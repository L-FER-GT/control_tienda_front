package com.lfergt.controltienda.data.supabase

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lfergt.controltienda.data.di.AppScope
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.port.SyncMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class Source { CACHE }
object FieldValue { fun serverTimestamp(): Map<String, Boolean> = mapOf("\$serverTime" to true) }
fun Long.toTimestamp(): Long = this

/** Confirmed server data and durable outbox are separate: rejected writes can be rolled back. */
internal class LocalDatabase(context: Context) : SQLiteOpenHelper(context, "supabase_offline.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE documents (uid TEXT NOT NULL, path TEXT NOT NULL, data TEXT NOT NULL, PRIMARY KEY(uid,path))")
        db.execSQL("CREATE TABLE outbox (seq INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL, id TEXT NOT NULL UNIQUE, operations TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX outbox_user ON outbox(uid,seq)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

@Singleton
class DocumentStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: SupabaseAuth,
    private val sync: SyncMonitor,
    @param:AppScope private val scope: CoroutineScope,
) {
    private val local = LocalDatabase(context)
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    private val refreshMutex = Mutex()
    private val pollCache = mutableMapOf<String, Pair<String, String>>()
    private fun uid() = auth.currentUser?.uid ?: throw DomainError.Auth("Debes iniciar sesión")

    fun collection(path: String) = CollectionReference(this, path)
    fun collectionGroup(group: String) = Query(this, mapOf("group" to group))
    fun document(path: String) = DocumentReference(this, path)
    fun batch() = WriteBatch(this)

    suspend fun enqueue(operations: List<Map<String, Any?>>) = withContext(Dispatchers.IO) {
        val user = uid()
        local.writableDatabase.insertOrThrow("outbox", null, ContentValues().apply {
            put("uid", user); put("id", UUID.randomUUID().toString())
            put("operations", jsonValue(operations).toString()); put("created", System.currentTimeMillis())
        })
        revision.value++
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("supabase-sync-$user", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        scope.launch { runCatching { flush() } }
    }

    /** Returns false on transient errors. Permanent rejections roll back only their batch. */
    suspend fun flush(): Boolean = mutex.withLock { withContext(Dispatchers.IO) {
        val user = auth.currentUser?.uid ?: return@withContext true
        while (auth.currentUser?.uid == user) {
            val row = local.readableDatabase.rawQuery("SELECT id,operations FROM outbox WHERE uid=? ORDER BY seq LIMIT 1", arrayOf(user)).use {
                if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
            } ?: return@withContext true
            try {
                val operations = JSONArray(row.second)
                auth.request("/rest/v1/rpc/ct_commit", data = mapOf("operation_id" to row.first, "operations" to operations), expectedUid=user)
                if (auth.currentUser?.uid != user) return@withContext false
                val confirmed = mutableListOf<Pair<String, String?>>()
                for (i in 0 until operations.length()) {
                    val path = operations.getJSONObject(i).getString("path")
                    val data = JSONArray(auth.request("/rest/v1/rpc/ct_query", data = mapOf("query" to mapOf("document" to path)),expectedUid=user))
                    confirmed += path to if (data.length() == 0) null else data.getJSONObject(0).getJSONObject("data").toString()
                }
                val db = local.writableDatabase
                db.beginTransaction()
                try {
                    confirmed.forEach { (path, data) ->
                        db.delete("documents", "uid=? AND path=?", arrayOf(user, path))
                        if (data != null) put(db, user, path, data)
                    }
                    db.delete("outbox", "id=?", arrayOf(row.first)); db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                revision.value++
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (e !is ApiException || e.status !in listOf(400, 403, 404, 409, 422)) return@withContext false
                local.writableDatabase.delete("outbox", "id=?", arrayOf(row.first))
                sync.report(e.toDomainError()); revision.value++
            }
        }
        false
    } }

    private fun put(db: SQLiteDatabase, user: String, path: String, data: String) {
        db.insertWithOnConflict("documents", null, ContentValues().apply { put("uid",user); put("path",path); put("data",data) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    internal suspend fun cached(query: Query): QuerySnapshot = withContext(Dispatchers.IO) {
        val user = uid()
        val documents = linkedMapOf<String, DocumentSnapshot>()
        local.readableDatabase.rawQuery("SELECT path,data FROM documents WHERE uid=?", arrayOf(user)).use { c ->
            while (c.moveToNext()) {
                val path=c.getString(0)
                if(query.inScope(path)) documents[path]=DocumentSnapshot(document(path), parse(c.getString(1)), false)
            }
        }
        local.readableDatabase.rawQuery("SELECT operations,created FROM outbox WHERE uid=? ORDER BY seq", arrayOf(user)).use { c ->
            while (c.moveToNext()) {
                val ops=JSONArray(c.getString(0)); val time=c.getLong(1)
                for (i in 0 until ops.length()) {
                    val op=ops.getJSONObject(i); val path=op.getString("path")
                    if(!query.inScope(path)) continue
                    if (op.getString("kind")=="delete") documents.remove(path)
                    else {
                        val patch=parse(op.getJSONObject("data").toString()).mapValues { (_,v) -> if (v is Map<*,*> && v["\$serverTime"]==true) time else v }
                        val data=if (op.getString("kind")=="update") (documents[path]?.data ?: emptyMap())+patch else patch
                        documents[path]=DocumentSnapshot(document(path),data,true)
                    }
                }
            }
        }
        QuerySnapshot(query.select(documents.values.toList()))
    }

    internal suspend fun refresh(query: Query) = refreshMutex.withLock { withContext(Dispatchers.IO) {
        val user=uid(); var offset=0; val rows=mutableListOf<JSONObject>()
        val max=(query.spec["limit"] as? Number)?.toInt() ?: Int.MAX_VALUE
        while (offset < max) {
            val limit=minOf(500,max-offset)
            val page=query.spec+mapOf("limit" to limit,"offset" to offset)
            val cacheKey=user+":"+jsonValue(page).toString()
            val previous=pollCache[cacheKey]
            val response=JSONObject(auth.request("/rest/v1/rpc/ct_poll",data=mapOf("query" to page,"etag" to previous?.first),expectedUid=user))
            val result=if(response.isNull("rows")) JSONArray(previous?.second ?: "[]") else response.getJSONArray("rows")
            pollCache[cacheKey]=response.getString("etag") to result.toString()
            if(pollCache.size>128) pollCache.remove(pollCache.keys.first())
            for (i in 0 until result.length()) rows+=result.getJSONObject(i)
            offset+=result.length()
            if (result.length()<limit) break
        }
        if (auth.currentUser?.uid != user) return@withContext
        val db=local.writableDatabase
        db.beginTransaction()
        try {
            // Remove cached rows from this scope, including data whose permission was revoked.
            db.rawQuery("SELECT path,data FROM documents WHERE uid=?",arrayOf(user)).use { c ->
                val remove=mutableListOf<String>()
                while(c.moveToNext()) if(query.inScope(c.getString(0))) remove+=c.getString(0)
                remove.forEach { db.delete("documents","uid=? AND path=?",arrayOf(user,it)) }
            }
            rows.forEach { put(db,user,it.getString("path"),it.getJSONObject("data").toString()) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.value++
    } }

    internal fun observe(query: Query): Flow<QuerySnapshot> = channelFlow {
        launch { combine(revision, auth.session) { _, session -> session }.collect { session ->
            send(if(session==null) QuerySnapshot(emptyList()) else cached(query))
        } }
        while (currentCoroutineContext().isActive) {
            try { if(auth.currentUser!=null) { flush(); refresh(query) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e !is IOException && !(e is ApiException && e.retryable)) sync.report(e.toDomainError())
            }
            delay(15_000)
        }
    }.distinctUntilChanged()

    internal suspend fun read(query: Query, source: Source? = null): QuerySnapshot {
        if(source != Source.CACHE) {
            try { flush(); refresh(query) }
            catch (e: IOException) { /* available cached data remains usable offline */ }
        }
        return cached(query)
    }
    @Suppress("UNCHECKED_CAST") private fun parse(text: String) = unpack(JSONObject(text)) as Map<String,Any?>
}

open class Query(internal val store: DocumentStore, internal val spec: Map<String, Any?>) {
    enum class Direction { ASCENDING, DESCENDING }
    private fun filter(field:String,op:String,value:Any?): Query {
        val filters=(spec["filters"] as? List<*>).orEmpty()+mapOf("field" to field,"op" to op,"value" to value)
        return Query(store,spec+("filters" to filters))
    }
    fun whereEqualTo(field:String,value:Any?)=filter(field,"eq",value)
    fun whereGreaterThanOrEqualTo(field:String,value:Any?)=filter(field,"gte",value)
    fun whereLessThan(field:String,value:Any?)=filter(field,"lt",value)
    fun orderBy(field:String,direction:Direction=Direction.ASCENDING)=Query(store,spec+mapOf("order" to field,"descending" to (direction==Direction.DESCENDING)))
    fun limit(n:Long)=Query(store,spec+("limit" to n))
    fun startAt(value:String)=Query(store,spec+("start" to value))
    fun endAt(value:String)=Query(store,spec+("end" to value))
    fun get()=ReadTask { store.read(this) }
    internal fun inScope(path:String):Boolean = when {
        spec["document"]!=null -> path==spec["document"]
        spec["group"]!=null -> path.substringBeforeLast('/').substringAfterLast('/')==spec["group"]
        else -> path.substringBeforeLast('/')==spec["path"]
    }
    internal fun select(rows:List<DocumentSnapshot>):List<DocumentSnapshot> {
        val filters=(spec["filters"] as? List<*>).orEmpty().filterIsInstance<Map<*,*>>()
        var result=rows.filter { row -> inScope(row.reference.path) && filters.all { f ->
            val a=row.get(f["field"].toString()); val b=f["value"]
            when(f["op"]) { "eq" -> a==b || a is Number && b is Number && a.toDouble()==b.toDouble(); "gte" -> compare(a,b)>=0; "lt" -> compare(a,b)<0; else -> false }
        } }
        val order=spec["order"] as? String
        if(order!=null) {
            result=result.filter { (spec["start"]==null || compare(it.get(order),spec["start"])>=0) && (spec["end"]==null || compare(it.get(order),spec["end"])<=0) }
                .sortedWith { a,b -> compare(a.get(order),b.get(order)) }
            if(spec["descending"]==true) result=result.reversed()
        }
        return result.take((spec["limit"] as? Number)?.toInt() ?: Int.MAX_VALUE)
    }
    private fun compare(a:Any?,b:Any?):Int = if(a is Number && b is Number) a.toDouble().compareTo(b.toDouble()) else (a?.toString() ?: "").compareTo(b?.toString() ?: "")
}
class CollectionReference(store:DocumentStore,val path:String):Query(store,mapOf("path" to path)) {
    val parent:DocumentReference? get()=path.substringBeforeLast('/',"").takeIf { it.isNotEmpty() }?.let(store::document)
    fun document(id:String=UUID.randomUUID().toString())=store.document("$path/$id")
}
data class DocumentReference(internal val store:DocumentStore,val path:String) {
    val id get()=path.substringAfterLast('/')
    val parent get()=store.collection(path.substringBeforeLast('/'))
    fun collection(name:String)=store.collection("$path/$name")
    fun get(source:Source?=null)=ReadTask { store.read(Query(store,mapOf("document" to path)),source).documents.firstOrNull() ?: DocumentSnapshot(this,null,false) }
    fun set(data:Map<String,Any?>)=PendingWrite(store,listOf(operation("set",data)))
    fun update(data:Map<String,Any?>)=PendingWrite(store,listOf(operation("update",data)))
    fun update(field:String,value:Any?)=update(mapOf(field to value))
    fun delete()=PendingWrite(store,listOf(operation("delete",null)))
    internal fun operation(kind:String,data:Map<String,Any?>?)=mapOf("path" to path,"kind" to kind,"data" to data)
}
class WriteBatch(private val store:DocumentStore) {
    private val ops=mutableListOf<Map<String,Any?>>()
    fun set(ref:DocumentReference,data:Map<String,Any?>)=apply { ops+=ref.operation("set",data) }
    fun update(ref:DocumentReference,data:Map<String,Any?>)=apply { ops+=ref.operation("update",data) }
    fun update(ref:DocumentReference,field:String,value:Any?)=update(ref,mapOf(field to value))
    fun delete(ref:DocumentReference)=apply { ops+=ref.operation("delete",null) }
    fun commit()=PendingWrite(store,ops.toList())
}
class PendingWrite(internal val store:DocumentStore,internal val ops:List<Map<String,Any?>>)
suspend fun PendingWrite.commitOffline(sync:SyncMonitor) { if(ops.isNotEmpty()) store.enqueue(ops) }
fun Query.observe(includeMetadata:Boolean=false):Flow<QuerySnapshot> = store.observe(this)
fun DocumentReference.observe(includeMetadata:Boolean=false):Flow<DocumentSnapshot> = store.observe(Query(store,mapOf("document" to path)))
    .map { it.documents.firstOrNull() ?: DocumentSnapshot(this,null,false) }
data class SnapshotMetadata(private val pending:Boolean) { fun hasPendingWrites()=pending }
data class DocumentSnapshot(val reference:DocumentReference,internal val data:Map<String,Any?>?,private val pending:Boolean) {
    val id get()=reference.id
    val metadata get()=SnapshotMetadata(pending)
    fun exists()=data!=null
    fun get(key:String)=data?.get(key)
    fun getString(key:String)=get(key) as? String
    fun getBoolean(key:String)=get(key) as? Boolean
    fun getLong(key:String)=(get(key) as? Number)?.toLong()
    fun getDouble(key:String)=(get(key) as? Number)?.toDouble()
}
data class QuerySnapshot(val documents:List<DocumentSnapshot>) {
    val isEmpty get()=documents.isEmpty()
    fun size()=documents.size
}

@HiltWorker
class SyncWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val store:DocumentStore):CoroutineWorker(context,params) {
    override suspend fun doWork():Result = if(store.flush()) Result.success() else Result.retry()
}

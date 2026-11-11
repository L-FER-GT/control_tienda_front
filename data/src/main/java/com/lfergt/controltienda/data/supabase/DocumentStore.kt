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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class Source { CACHE }
object FieldValue { fun serverTimestamp(): Map<String, Boolean> = mapOf("\$serverTime" to true) }
fun Long.toTimestamp(): Long = this

/** Confirmed server data and durable outbox are separate: rejected writes can be rolled back. */
internal class LocalDatabase(context: Context) : SQLiteOpenHelper(context, "supabase_offline.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE documents (uid TEXT NOT NULL, path TEXT NOT NULL, data TEXT NOT NULL, PRIMARY KEY(uid,path))")
        db.execSQL("CREATE TABLE outbox (seq INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL, id TEXT NOT NULL UNIQUE, operations TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX outbox_user ON outbox(uid,seq)")
        db.execSQL(SYNCED_TABLE)
        db.execSQL(POLL_TABLE)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(SYNCED_TABLE)
        if (oldVersion < 3) db.execSQL(POLL_TABLE)
    }
    private companion object {
        /** Consultas que ya se sincronizaron alguna vez: su caché local, aunque esté vacía, es fiable. */
        const val SYNCED_TABLE = "CREATE TABLE IF NOT EXISTS synced_queries (uid TEXT NOT NULL, key TEXT NOT NULL, PRIMARY KEY(uid,key))"
        /** Último etag aplicado de cada página: al reabrir la app no se vuelve a descargar lo que no cambió. */
        const val POLL_TABLE = "CREATE TABLE IF NOT EXISTS poll_state (uid TEXT NOT NULL, key TEXT NOT NULL, etag TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(uid,key))"
    }
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
    /** Qué rutas cambió cada revisión: un observador solo relee la base si le tocó un cambio. */
    private val changeLog = ArrayDeque<Pair<Long, Collection<String>?>>()
    /** Varias consultas se actualizan en paralelo para que la pantalla visible no espere a las demás. */
    private val refreshSlots = Semaphore(3)
    private val refreshing = ConcurrentHashMap<String, Mutex>()
    private val pollCache = mutableMapOf<String, Pair<String, String>>()
    /** Una sola lectura y un solo sondeo por consulta, aunque varias pantallas la observen. */
    private val observers = ConcurrentHashMap<String, Flow<QuerySnapshot>>()
    private companion object {
        const val FIRST_SYNC_GRACE_MS = 3_000L
        const val POLL_INTERVAL_MS = 15_000L
        const val CHANGE_LOG_SIZE = 256
    }
    private fun uid() = auth.currentUser?.uid ?: throw DomainError.Auth("Debes iniciar sesión")

    private fun pendingCount(user: String): Int = local.readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox WHERE uid=?", arrayOf(user)).use { it.moveToFirst(); it.getInt(0) }
    init {
        scope.launch(Dispatchers.IO) {
            var previousUser: String? = null
            combine(revision, auth.session) { _, session -> session?.uid }.collect { user ->
                // Otra cuenta: ningún observador nuevo debe recibir el último dato de la anterior.
                if (user != previousUser) { sync.reset(); observers.clear(); previousUser = user }
                sync.queueChanged(user?.let(::pendingCount) ?: 0)
            }
        }
    }

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
        notifyChanged(operations.mapNotNull { it["path"] as? String })
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("supabase-sync-$user", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        scope.launch { runCatching { flush() } }
    }

    /** Returns false on transient errors. Permanent rejections roll back only their batch. */
    suspend fun flush(): Boolean = mutex.withLock { withContext(Dispatchers.IO) {
        val user = auth.currentUser?.uid ?: return@withContext true
        sync.syncing(pendingCount(user) > 0)
        try {
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
                sync.completed(System.currentTimeMillis())
                sync.queueChanged(pendingCount(user))
                notifyChanged(confirmed.map { it.first })
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (e !is ApiException || e.status !in listOf(400, 403, 404, 409, 422)) return@withContext false
                local.writableDatabase.delete("outbox", "id=?", arrayOf(row.first))
                val rejected = runCatching { JSONArray(row.second).let { ops -> (0 until ops.length()).map { ops.getJSONObject(it).getString("path") } } }.getOrNull()
                sync.report(e.toDomainError()); notifyChanged(rejected)
            }
        }
        false
        } finally {
            if (auth.currentUser?.uid == user) sync.queueChanged(pendingCount(user))
            sync.syncing(false)
        }
    } }

    private fun put(db: SQLiteDatabase, user: String, path: String, data: String) {
        db.insertWithOnConflict("documents", null, ContentValues().apply { put("uid",user); put("path",path); put("data",data) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Registra qué rutas cambiaron; `null` significa que no se sabe y todos los observadores releen. */
    private fun notifyChanged(paths: Collection<String>?) {
        if (paths != null && paths.isEmpty()) return
        synchronized(changeLog) {
            val next = revision.value + 1
            changeLog.addLast(next to paths)
            while (changeLog.size > CHANGE_LOG_SIZE) changeLog.removeFirst()
            revision.value = next
        }
    }

    /** ¿Algún cambio de las revisiones (since, until] cae dentro de la consulta? */
    private fun touches(query: Query, since: Long, until: Long): Boolean {
        if (until <= since) return false
        val entries = synchronized(changeLog) { changeLog.filter { it.first > since && it.first <= until } }
        // El registro ya no llega tan atrás: no se puede descartar nada.
        if (entries.size < until - since) return true
        return entries.any { (_, paths) -> paths == null || paths.any(query::inScope) }
    }

    /** Solo las filas de la consulta (usa la clave uid+path), no todos los documentos del usuario. */
    private fun scopedRows(db: SQLiteDatabase, user: String, query: Query): List<Pair<String, String>> {
        val document = query.spec["document"] as? String
        val group = query.spec["group"] as? String
        val path = query.spec["path"] as? String
        val cursor = when {
            document != null -> db.rawQuery("SELECT path,data FROM documents WHERE uid=? AND path=?", arrayOf(user, document))
            group != null -> db.rawQuery("SELECT path,data FROM documents WHERE uid=? AND path LIKE ? ESCAPE '\\'",
                arrayOf(user, "%/" + group.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"))
            path != null -> {
                // Rango "path/" ≤ x < "path0" ('0' sigue a '/'): documentos directos, sin subcolecciones.
                val prefix = "$path/"
                db.rawQuery("SELECT path,data FROM documents WHERE uid=? AND path>=? AND path<? AND instr(substr(path,${prefix.length + 1}),'/')=0",
                    arrayOf(user, prefix, "${path}0"))
            }
            else -> db.rawQuery("SELECT path,data FROM documents WHERE uid=?", arrayOf(user))
        }
        return cursor.use { c -> buildList { while (c.moveToNext()) { val p = c.getString(0); if (query.inScope(p)) add(p to c.getString(1)) } } }
    }

    private fun queryKey(query: Query) = jsonValue(query.spec).toString()

    private fun syncedBefore(user: String, query: Query): Boolean =
        local.readableDatabase.rawQuery("SELECT 1 FROM synced_queries WHERE uid=? AND key=?", arrayOf(user, queryKey(query))).use { it.moveToFirst() }

    private fun markSynced(user: String, query: Query) {
        local.writableDatabase.insertWithOnConflict("synced_queries", null,
            ContentValues().apply { put("uid", user); put("key", queryKey(query)) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun storedPoll(user: String, key: String): Pair<String, Int>? =
        local.readableDatabase.rawQuery("SELECT etag,count FROM poll_state WHERE uid=? AND key=?", arrayOf(user, key)).use {
            if (it.moveToFirst()) it.getString(0) to it.getInt(1) else null
        }

    internal suspend fun cached(query: Query): QuerySnapshot = withContext(Dispatchers.IO) {
        val user = uid()
        val documents = linkedMapOf<String, DocumentSnapshot>()
        scopedRows(local.readableDatabase, user, query).forEach { (path, data) -> documents[path] = DocumentSnapshot(document(path), parse(data), false) }
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

    /** Una misma consulta no se refresca dos veces a la vez; consultas distintas sí, hasta 3 en paralelo. */
    internal suspend fun refresh(query: Query) = refreshing.getOrPut(queryKey(query)) { Mutex() }.withLock {
        refreshSlots.withPermit { withContext(Dispatchers.IO) { refreshNow(query, reuseStored = true) } }
    }

    private suspend fun refreshNow(query: Query, reuseStored: Boolean) {
        val user=uid(); var offset=0; val rows=mutableListOf<JSONObject>()
        val max=(query.spec["limit"] as? Number)?.toInt() ?: Int.MAX_VALUE
        var changed=false
        var skipped=false
        val pages=mutableListOf<Triple<String, String, Int>>()
        while (offset < max) {
            val limit=minOf(500,max-offset)
            val page=query.spec+mapOf("limit" to limit,"offset" to offset)
            val cacheKey=user+":"+jsonValue(page).toString()
            val previous=synchronized(pollCache) { pollCache[cacheKey] }
            val stored=if (previous == null && reuseStored) storedPoll(user, cacheKey) else null
            val response=JSONObject(auth.request("/rest/v1/rpc/ct_poll",data=mapOf("query" to page,"etag" to (previous?.first ?: stored?.first)),expectedUid=user))
            val etag=response.getString("etag")
            if (response.isNull("rows") && previous == null && stored != null) {
                // Igual a lo último que se aplicó en la base local (etag guardado): no hace falta descargarla.
                skipped=true; pages+=Triple(cacheKey, etag, stored.second); offset+=stored.second
                if (stored.second < limit) break else continue
            }
            if (!response.isNull("rows") || previous == null) changed=true
            val result=if(response.isNull("rows")) JSONArray(previous?.second ?: "[]") else response.getJSONArray("rows")
            synchronized(pollCache) {
                pollCache[cacheKey]=etag to result.toString()
                if(pollCache.size>128) pollCache.remove(pollCache.keys.first())
            }
            pages+=Triple(cacheKey, etag, result.length())
            for (i in 0 until result.length()) rows+=result.getJSONObject(i)
            offset+=result.length()
            if (result.length()<limit) break
        }
        if (skipped) {
            // Una página sin cambios y otra con cambios: se necesitan todas las filas para conciliar.
            if (changed) return refreshNow(query, reuseStored = false)
            if (auth.currentUser?.uid == user && !syncedBefore(user, query)) markSynced(user, query)
            return
        }
        if (auth.currentUser?.uid != user) return
        val db=local.writableDatabase
        val cachedRows = scopedRows(db, user, query)
        val returned = rows.map { it.getString("path") }.toSet()
        val constrained = query.spec.keys.any { it in setOf("filters", "limit", "start", "end") }
        val candidates = if (constrained) query.select(cachedRows.map { (path, data) -> DocumentSnapshot(document(path), parse(data), false) }).map { it.reference.path }
            else cachedRows.map { it.first }
        val remove = mutableListOf<String>()
        for (path in candidates.filter { it !in returned }) {
            if (constrained) {
                // Absence from a filtered/limited result does not mean deletion: the row may
                // have changed category or moved beyond the limit. Revalidate it individually.
                val current = JSONArray(auth.request("/rest/v1/rpc/ct_query",
                    data = mapOf("query" to mapOf("document" to path)), expectedUid = user))
                if (current.length() > 0) { rows += current.getJSONObject(0); continue }
            }
            remove += path
        }
        if (auth.currentUser?.uid != user) return
        // Solo se escriben (y se avisan) las filas que de verdad cambiaron.
        val existing = cachedRows.toMap()
        val writes = rows.map { it.getString("path") to it.getJSONObject("data").toString() }.filter { (path, data) -> existing[path] != data }
        db.beginTransaction()
        try {
            remove.forEach { db.delete("documents","uid=? AND path=?",arrayOf(user,it)) }
            writes.forEach { (path, data) -> put(db,user,path,data) }
            pages.forEach { (key, etag, count) ->
                db.insertWithOnConflict("poll_state", null, ContentValues().apply {
                    put("uid", user); put("key", key); put("etag", etag); put("count", count)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        if (!syncedBefore(user, query)) markSynced(user, query)
        notifyChanged(remove + writes.map { it.first })
    }

    /**
     * Las pantallas que observan la misma consulta comparten una sola lectura y un solo sondeo.
     * Al volver a una pantalla en menos de un minuto se muestra al instante el último dato mientras se relee.
     */
    internal fun observe(query: Query): Flow<QuerySnapshot> = observers.getOrPut("${auth.currentUser?.uid}:${queryKey(query)}") {
        observeShared(query).shareIn(scope, SharingStarted.WhileSubscribed(5_000, 60_000), replay = 1)
    }

    private fun observeShared(query: Query): Flow<QuerySnapshot> = channelFlow {
        val knownUser = auth.currentUser?.uid
        // Si esta consulta ya se sincronizó antes, la caché local (incluso vacía) se muestra al instante.
        val attempted = MutableStateFlow(knownUser != null &&
            withContext(Dispatchers.IO) { runCatching { syncedBefore(knownUser, query) }.getOrDefault(false) })
        // Primera sincronización de una consulta sin datos locales: la red no puede bloquear la pantalla
        // más de unos segundos; se muestra lo que haya en la base local y se completa al llegar la nube.
        if (!attempted.value) launch { delay(FIRST_SYNC_GRACE_MS); attempted.value = true }
        launch {
            var seen = -1L
            var seenUser: String? = null
            var last: QuerySnapshot? = null
            combine(revision, auth.session, attempted) { rev, session, ready -> Triple(rev, session?.uid, ready) }.collect { (rev, user, ready) ->
                if (user == null) { last = null; seenUser = null; send(QuerySnapshot(emptyList())); return@collect }
                // Solo se relee SQLite si cambió el usuario o algún documento de esta consulta.
                val snapshot = if (last == null || user != seenUser || touches(query, seen, rev)) {
                    try { cached(query) } catch (e: CancellationException) { throw e } catch (e: Exception) { return@collect }
                } else last!!
                seen = rev; seenUser = user; last = snapshot
                if (ready || snapshot.documents.isNotEmpty()) send(snapshot)
            }
        }
        while (currentCoroutineContext().isActive) {
            try { if(auth.currentUser!=null) { flush(); refresh(query) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e !is IOException && !(e is ApiException && e.retryable)) sync.report(e.toDomainError())
            }
            attempted.value = true
            delay(POLL_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    internal suspend fun read(query: Query, source: Source? = null): QuerySnapshot {
        if(source != Source.CACHE) {
            try { flush(); refresh(query) }
            catch (e: IOException) { /* available cached data remains usable offline */ }
            catch (e: ApiException) { if (!e.retryable) throw e }
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
                .sortedWith { a,b ->
                    val compared = compare(a.get(order),b.get(order))
                    if (compared != 0) {
                        if (spec["descending"] == true) -compared else compared
                    } else a.reference.path.compareTo(b.reference.path)
                }
        } else result = result.sortedBy { it.reference.path }
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

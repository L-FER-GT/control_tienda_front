package com.lfergt.controltienda.data.supabase

import android.content.ContentValues
import android.content.Context
import com.lfergt.controltienda.data.di.DataConfig
import com.lfergt.controltienda.domain.port.SyncMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], manifest=Config.NONE)
class OfflineStoreTest {
    @Test fun `filtered and limited queries preserve other cached rows and reconcile moved or revoked rows`() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE).edit().putString("uid", "filter-user")
            .putString("access", "access").putString("refresh", "refresh").putLong("expires", Long.MAX_VALUE).commit()
        val server = MockWebServer().apply { start() }
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
        val sync = object : SyncMonitor {
            override val failures: Flow<String> = emptyFlow()
            override fun report(error: Throwable) = Unit
        }
        try {
            val store = DocumentStore(context, SupabaseAuth(context, DataConfig(server.url("/").toString(), "public", appVersion = "test")), sync, scope)
            val all = store.collection("stores/shop/products")
            server.enqueue(MockResponse().setBody("""{"etag":"v1","rows":[
                {"path":"stores/shop/products/a","data":{"name":"Arroz","category":"food"}},
                {"path":"stores/shop/products/b","data":{"name":"Jabón","category":"cleaning"}}
            ]}"""))
            store.refresh(all)
            server.enqueue(MockResponse().setBody("""{"etag":"filtered","rows":[{"path":"stores/shop/products/a","data":{"name":"Arroz","category":"food"}}]}"""))
            store.refresh(all.whereEqualTo("category", "food"))
            assertEquals(2, store.cached(all).size())
            server.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"temporarily unavailable\"}"))
            assertEquals(2, all.get().await().size())
            // A row no longer in the filter still exists in a different category.
            server.enqueue(MockResponse().setBody("""{"etag":"moved","rows":[]}"""))
            server.enqueue(MockResponse().setBody("""[{"path":"stores/shop/products/a","data":{"name":"Arroz","category":"other"}}]"""))
            store.refresh(all.whereEqualTo("category", "food"))
            assertTrue(store.cached(all.whereEqualTo("category", "food")).isEmpty)
            assertEquals(2, store.cached(all).size())
            server.enqueue(MockResponse().setBody("""{"etag":"limited","rows":[{"path":"stores/shop/products/a","data":{"name":"Arroz","category":"other"}}]}"""))
            store.refresh(all.orderBy("name").limit(1))
            assertEquals(2, store.cached(all).size())
            // RLS hides a previously visible row: do not retain it offline.
            server.enqueue(MockResponse().setBody("""{"etag":"revoked","rows":[]}"""))
            server.enqueue(MockResponse().setBody("[]"))
            store.refresh(all.whereEqualTo("category", "cleaning"))
            assertEquals(listOf("a"), store.cached(all).documents.map { it.id })
        } finally { scope.cancel(); server.shutdown() }
    }

    @Test fun `durable outbox preserves offline writes retries and rolls back rejected batches per user`() = runBlocking {
        val context=RuntimeEnvironment.getApplication() as Context
        val server=MockWebServer();server.start()
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher())
        val errors=mutableListOf<Throwable>()
        val sync=object:SyncMonitor { override val failures:Flow<String> = emptyFlow();override fun report(error:Throwable) { errors+=error } }
        val config=DataConfig(server.url("/").toString(),"public-test",appVersion="test")
        fun login(uid:String) {
            context.getSharedPreferences("supabase_session",Context.MODE_PRIVATE).edit()
                .putString("uid",uid).putString("access","test-token").putString("refresh","test-refresh")
                .putLong("expires",System.currentTimeMillis()+3_600_000).commit()
        }
        login("user-a")
        val auth=SupabaseAuth(context,config)
        val db=LocalDatabase(context)
        db.writableDatabase.insertOrThrow("documents",null,ContentValues().apply {
            put("uid","user-a");put("path","stores/shop/products/p1");put("data","""{"name":"Arroz","stock":20}""")
        })
        val operations="""[{"path":"stores/shop/products/p1","kind":"update","data":{"name":"Nuevo"}}]"""
        db.writableDatabase.insertOrThrow("outbox",null,ContentValues().apply {
            put("uid","user-a");put("id","11111111-1111-4111-8111-111111111111");put("operations",operations);put("created",1)
        })
        db.close()
        // New store instance reads the persisted queue, including optimistic data.
        val store=DocumentStore(context,auth,sync,scope)
        val pending=store.document("stores/shop/products/p1").get(Source.CACHE).await()
        assertEquals("Nuevo",pending.getString("name"));assertTrue(pending.metadata.hasPendingWrites())
        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"temporarily unavailable\"}"))
        assertFalse(store.flush());assertTrue(store.document(pending.reference.path).get(Source.CACHE).await().metadata.hasPendingWrites())
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"message\":\"permission revoked\"}"))
        assertTrue(store.flush());val restored=store.document(pending.reference.path).get(Source.CACHE).await()
        assertEquals("Arroz",restored.getString("name"));assertFalse(restored.metadata.hasPendingWrites());assertEquals(1,errors.size)
        server.takeRequest();server.takeRequest()
        // Commit may succeed remotely while the acknowledgement read fails.
        // Retry must reuse the SAME operation id and payload, then confirm locally.
        val queue=LocalDatabase(context)
        queue.writableDatabase.insertOrThrow("outbox",null,ContentValues().apply {
            put("uid","user-a");put("id","22222222-2222-4222-8222-222222222222")
            put("operations","""[{"path":"stores/shop/orders/order1","kind":"set","data":{"number":null}}]""");put("created",2)
        });queue.close()
        server.enqueue(MockResponse().setBody("{\"ok\":true}"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"retry\"}"))
        assertFalse(store.flush())
        val original=server.takeRequest().body.readUtf8();server.takeRequest()
        server.enqueue(MockResponse().setBody("{\"ok\":true,\"replayed\":true}"))
        server.enqueue(MockResponse().setBody("""[{"path":"stores/shop/orders/order1","data":{"number":1}}]"""))
        assertTrue(store.flush())
        assertEquals(original,server.takeRequest().body.readUtf8());server.takeRequest()
        val confirmed=store.document("stores/shop/orders/order1").get(Source.CACHE).await()
        assertEquals(1L,confirmed.getLong("number"));assertFalse(confirmed.metadata.hasPendingWrites())
        // A different account must not see cached data from the prior session.
        login("user-b")
        val other=DocumentStore(context,SupabaseAuth(context,config),sync,scope)
        assertFalse(other.document(pending.reference.path).get(Source.CACHE).await().exists())
        scope.cancel();server.shutdown()
    }

    @Test fun `conditional polling sends etag and reuses unchanged rows`() = runBlocking {
        val context=RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("supabase_session",Context.MODE_PRIVATE).edit().putString("uid","poll-user")
            .putString("access","access").putString("refresh","refresh").putLong("expires",Long.MAX_VALUE).commit()
        val server=MockWebServer();server.start()
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher())
        val sync=object:SyncMonitor { override val failures:Flow<String> = emptyFlow();override fun report(error:Throwable)=Unit }
        val store=DocumentStore(context,SupabaseAuth(context,DataConfig(server.url("/").toString(),"public",appVersion="test")),sync,scope)
        server.enqueue(MockResponse().setBody("""{"etag":"v1","rows":[{"path":"stores/shop/products/p1","data":{"name":"Arroz","stock":20}}]}"""))
        assertEquals("Arroz",store.collection("stores/shop/products").get().await().documents.single().getString("name"))
        server.enqueue(MockResponse().setBody("""{"etag":"v1","rows":null}"""))
        assertEquals(20,store.collection("stores/shop/products").get().await().documents.single().getLong("stock")!!.toInt())
        server.takeRequest();val request=server.takeRequest()
        assertTrue(request.body.readUtf8().contains("\"etag\":\"v1\""));assertEquals("Bearer access",request.getHeader("Authorization"))
        scope.cancel();server.shutdown()
    }
}

package com.lfergt.controltienda.data.media

import android.content.Context
import com.lfergt.controltienda.data.di.DataConfig
import com.lfergt.controltienda.data.supabase.SupabaseAuth
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ImageHttpClientTest {
    @Test fun `una sesion cerrada falla la imagen sin excepcion fatal en el dispatcher`() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE).edit().clear().commit()
        val server = MockWebServer().apply { start() }
        val auth = SupabaseAuth(context, DataConfig(server.url("/").toString(), "public", appVersion = "test"))
        val client = ImageHttpClientFactory(auth).create()
        val done = CountDownLatch(1)
        val failure = AtomicReference<IOException?>()
        try {
            client.newCall(Request.Builder().url(server.url("/storage/v1/object/authenticated/media/image.jpg")).build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { failure.set(e); done.countDown() }
                    override fun onResponse(call: Call, response: Response) { response.close(); done.countDown() }
                })
            assertTrue("La petición debe terminar sin quedar bloqueada", done.await(5, TimeUnit.SECONDS))
            assertEquals("No se pudo autenticar la imagen", failure.get()?.message)
            assertEquals(0, server.requestCount)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }
}

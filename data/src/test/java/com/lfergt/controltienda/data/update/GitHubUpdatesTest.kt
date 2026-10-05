package com.lfergt.controltienda.data.update

import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.AppUpdate
import com.lfergt.controltienda.domain.model.AppVersion
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.MessageDigest

class GitHubUpdatesTest {

    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun updates(installedCode: Int = AppVersion(1, 0, 0).code, repo: String = "L-FER-GT/control_tienda_front"): GitHubUpdates {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(http)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
        return GitHubUpdates(api, http, repo, installedCode, folder.root, { "content://test/${it.name}" })
    }

    private fun release(tag: String, assets: String = """[{"name":"control-tienda-$tag.apk","browser_download_url":"https://github.com/x.apk","size":3,"digest":"sha256:abc"}]""") =
        MockResponse().setBody("""{"tag_name":"$tag","body":"Novedades","draft":false,"assets":$assets}""")

    @Test
    fun `ofrece el release si es más nuevo que la versión instalada`() = runTest {
        server.enqueue(release("v1.2.0"))
        val update = updates().findNewer()!!
        assertEquals(AppVersion(1, 2, 0), update.version)
        assertEquals("Novedades", update.notes)
        assertEquals("abc", update.sha256)
        assertEquals("/repos/L-FER-GT/control_tienda_front/releases/latest", server.takeRequest().path)
    }

    @Test
    fun `no ofrece nada si ya está al día o el release no trae APK`() = runTest {
        server.enqueue(release("v1.0.0"))
        assertNull(updates().findNewer())
        server.enqueue(release("v2.0.0", assets = "[]"))
        assertNull(updates().findNewer())
    }

    @Test
    fun `sin releases publicados no es un error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(updates().findNewer())
    }

    @Test
    fun `un fallo del servidor se informa como problema de conexión`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            updates().findNewer()
            fail("Debía fallar")
        } catch (e: DomainError.Network) { /* esperado */ }
    }

    @Test
    fun `sin repositorio configurado queda desactivado y no consulta`() = runTest {
        val disabled = updates(repo = "")
        assertFalse(disabled.enabled)
        assertNull(disabled.findNewer())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `descarga verificando el SHA-256 y reemplaza el instalador anterior`() = runTest {
        val content = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))
        folder.newFile("control-tienda-1.1.0.apk")
        val progress = mutableListOf<Float>()

        val file = updates().download(update(sha256(content))) { progress += it }

        assertEquals("content://test/control-tienda-1.2.0.apk", file.uri)
        assertEquals("application/vnd.android.package-archive", file.mimeType)
        assertEquals(listOf("control-tienda-1.2.0.apk"), folder.root.list()!!.toList())
        assertEquals(1f, progress.last())
    }

    @Test
    fun `descarta un archivo que no coincide con el SHA-256 publicado`() = runTest {
        server.enqueue(MockResponse().setBody("alterado"))
        try {
            updates().download(update("00".repeat(32))) {}
            fail("Debía rechazar el archivo")
        } catch (e: DomainError.Validation) {
            assertTrue(folder.root.list()!!.isEmpty())
        }
    }

    private fun update(sha256: String) = AppUpdate(AppVersion(1, 2, 0), "", server.url("/x.apk").toString(), 0, sha256)

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

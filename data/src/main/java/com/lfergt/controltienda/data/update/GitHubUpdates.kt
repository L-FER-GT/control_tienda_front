package com.lfergt.controltienda.data.update

import android.content.Context
import androidx.core.content.FileProvider
import com.lfergt.controltienda.data.di.DataConfig
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.AppUpdate
import com.lfergt.controltienda.domain.model.AppVersion
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.port.AppUpdates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Cliente HTTP para la API pública de GitHub y la descarga de los APK publicados. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubHttp

/** API pública de GitHub (sin token: el repositorio es público). */
interface GitHubApi {
    /** Último release publicado; excluye borradores y pre-releases. 404 si aún no hay ninguno. */
    @Headers("Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(@Path("owner") owner: String, @Path("repo") repo: String): GitHubRelease
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    /** "sha256:<hex>" calculado por GitHub al subir el archivo. */
    val digest: String? = null,
)

/**
 * Actualizaciones desde GitHub Releases: el workflow de release publica el APK firmado al subir un
 * tag vX.Y.Z. Solo se conserva el último instalador descargado, en la caché de la app.
 */
@Singleton
class GitHubUpdates internal constructor(
    private val api: GitHubApi,
    private val http: OkHttpClient,
    private val repository: String,
    private val installedCode: Int,
    private val directory: File,
    private val shareable: (File) -> String,
) : AppUpdates {

    @Inject constructor(
        api: GitHubApi,
        @GitHubHttp http: OkHttpClient,
        config: DataConfig,
        @ApplicationContext context: Context,
    ) : this(
        api, http, config.updateRepo, config.appVersionCode, File(context.cacheDir, "updates"),
        { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it).toString() },
    )

    override val enabled: Boolean = repository.count { it == '/' } == 1

    override suspend fun findNewer(): AppUpdate? {
        if (!enabled) return null
        val (owner, repo) = repository.split('/')
        val release = try {
            api.latestRelease(owner, repo)
        } catch (e: HttpException) {
            if (e.code() == 404) return null
            throw DomainError.Network(CHECK_FAILED, e)
        } catch (e: IOException) {
            throw DomainError.Network(CHECK_FAILED, e)
        }
        val version = AppVersion.parse(release.tagName) ?: return null
        if (version.code <= installedCode) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return AppUpdate(
            version = version,
            notes = release.body.orEmpty().trim(),
            downloadUrl = apk.downloadUrl,
            sizeBytes = apk.size,
            sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
        )
    }

    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): LocalFile = withContext(Dispatchers.IO) {
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "control-tienda-${update.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            http.newCall(Request.Builder().url(update.downloadUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val total = response.body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
                response.body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw DomainError.Network("No se pudo descargar la actualización. Revisa tu conexión.", e)
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (update.sha256 != null && !hash.equals(update.sha256, ignoreCase = true)) {
            file.delete()
            throw DomainError.Validation(null, "La descarga llegó dañada. Vuelve a intentarlo.")
        }
        LocalFile(shareable(file), APK_MIME)
    }

    private companion object {
        const val CHECK_FAILED = "No se pudo consultar si hay una versión nueva."
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

package com.lfergt.controltienda.data.media

import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.lfergt.controltienda.data.di.DataConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La base de datos solo guarda la ruta del archivo (p. ej. "stores/abc/products/uuid.jpg").
 * Esta clase la convierte en algo que el cargador de imágenes puede mostrar:
 *  - la copia local si el archivo aún no se subió (creado sin conexión), o
 *  - la URL del servidor, que luego queda en la caché de disco para verse sin conexión.
 */
@Singleton
class StorageImageResolver @Inject constructor(
    private val uploader: MediaUploader,
    private val storage: FirebaseStorage,
    private val config: DataConfig,
) {
    private val bucket: String get() = storage.reference.bucket

    private val baseUrl: String
        get() = if (config.useEmulators) "http://${config.emulatorHost}:9199" else "https://firebasestorage.googleapis.com"

    fun resolve(path: String): Any {
        val pending = uploader.pendingFileFor(path)
        if (pending.exists()) return pending
        return "$baseUrl/v0/b/$bucket/o/${Uri.encode(path)}?alt=media"
    }

    fun pendingFile(path: String): File? = uploader.pendingFileFor(path).takeIf { it.exists() }

    fun isStorageUrl(host: String): Boolean =
        host == "firebasestorage.googleapis.com" || (config.useEmulators && host == config.emulatorHost)
}

/** Agrega el token de Firebase a las descargas: las reglas de Storage exigen usuario autenticado. */
class FirebaseStorageAuthInterceptor(
    private val auth: FirebaseAuth,
    private val resolver: StorageImageResolver,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val user = auth.currentUser
        if (user == null || !resolver.isStorageUrl(request.url.host)) return chain.proceed(request)
        val token = runCatching { Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS).token }.getOrNull()
            ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Firebase $token").build())
    }
}

@Singleton
class ImageHttpClientFactory @Inject constructor(
    private val auth: FirebaseAuth,
    private val resolver: StorageImageResolver,
) {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(FirebaseStorageAuthInterceptor(auth, resolver))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

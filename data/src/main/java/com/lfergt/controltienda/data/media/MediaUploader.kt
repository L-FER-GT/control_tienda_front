package com.lfergt.controltienda.data.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lfergt.controltienda.domain.model.LocalFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cola de subida de archivos a la "carpeta del servidor" (Supabase Storage).
 *
 * La ruta definitiva se decide al instante y se guarda en PostgreSQL aunque no haya conexión;
 * el archivo queda en una carpeta local y WorkManager lo sube cuando vuelve internet.
 * Mientras tanto la app muestra la copia local (ver [StorageImageResolver]).
 */
@Singleton
class MediaUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val processor: ImageProcessor,
    private val auth: com.lfergt.controltienda.data.supabase.SupabaseAuth,
) {
    private val pendingDir: File get() = File(context.filesDir, "pending_uploads").apply { mkdirs() }

    fun pendingFileFor(remotePath: String): File = File(pendingDir, remotePath.replace('/', '_'))

    /**
     * @param folder carpeta remota, p. ej. "stores/{id}/products"
     * @return ruta remota del archivo (lo único que se guarda en la base de datos)
     */
    suspend fun enqueue(file: LocalFile, folder: String): String {
        val extension = when {
            file.mimeType.startsWith("image/") -> "jpg"
            file.mimeType == "application/pdf" -> "pdf"
            file.mimeType.contains("spreadsheetml") -> "xlsx"
            file.mimeType.contains("excel") -> "xls"
            else -> "bin"
        }
        val remotePath = "$folder/${UUID.randomUUID()}.$extension"
        val local = pendingFileFor(remotePath)
        val contentType = processor.prepare(file, local)

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_LOCAL to local.absolutePath,
                    UploadWorker.KEY_REMOTE to remotePath,
                    UploadWorker.KEY_CONTENT_TYPE to contentType,
                    UploadWorker.KEY_USER to (auth.currentUser?.uid ?: error("Debes iniciar sesión")),
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(UploadWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(remotePath, ExistingWorkPolicy.KEEP, request)
        return remotePath
    }
}

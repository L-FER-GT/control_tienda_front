package com.lfergt.controltienda.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.io.File

/** Sube un archivo pendiente a Cloud Storage. Reintenta con espera exponencial si falla. */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val storage: FirebaseStorage,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val localPath = inputData.getString(KEY_LOCAL) ?: return Result.failure()
        val remotePath = inputData.getString(KEY_REMOTE) ?: return Result.failure()
        val contentType = inputData.getString(KEY_CONTENT_TYPE) ?: "image/jpeg"
        val file = File(localPath)
        if (!file.exists()) return Result.success()

        return try {
            val metadata = StorageMetadata.Builder().setContentType(contentType).build()
            storage.reference.child(remotePath).putFile(Uri.fromFile(file), metadata).await()
            file.delete()
            Result.success()
        } catch (e: StorageException) {
            // NOT_AUTHORIZED puede ser temporal: la membresía o la tienda creadas sin conexión
            // aún no llegaron al servidor. Se reintenta un número limitado de veces.
            Log.w(TAG, "Fallo al subir $remotePath (intento $runAttemptCount)", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al subir $remotePath", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "uploads"
        const val KEY_LOCAL = "local"
        const val KEY_REMOTE = "remote"
        const val KEY_CONTENT_TYPE = "contentType"
        private const val MAX_ATTEMPTS = 10
    }
}

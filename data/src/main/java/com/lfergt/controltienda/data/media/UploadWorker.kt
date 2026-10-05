package com.lfergt.controltienda.data.media

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lfergt.controltienda.data.supabase.SupabaseAuth
import com.lfergt.controltienda.data.supabase.DocumentStore
import com.lfergt.controltienda.data.supabase.ApiException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

@HiltWorker
class UploadWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,
    private val auth:SupabaseAuth,private val store:DocumentStore):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val file=File(inputData.getString(KEY_LOCAL) ?: return Result.failure())
        val path=inputData.getString(KEY_REMOTE) ?: return Result.failure()
        val user=inputData.getString(KEY_USER) ?: return Result.failure()
        if(auth.currentUser?.uid!=user) return Result.retry()
        if(!file.exists()) return Result.success()
        if(file.length()>5*1024*1024) return Result.failure()
        return try {
            if(!store.flush()) return Result.retry()
            auth.request("/storage/v1/object/${auth.config.storageBucket}/${Uri.encode(path,"/")}",requestBody=file.asRequestBody((inputData.getString(KEY_CONTENT_TYPE) ?: "image/jpeg").toMediaType()))
            file.delete(); Result.success()
        } catch(e:CancellationException) { throw e
        } catch(e:ApiException) {
            if(e.status==409 || e.message?.contains("already exists",true)==true) { file.delete(); Result.success() }
            else if(e.retryable || e.status in listOf(401,403)) Result.retry() else Result.failure()
        } catch(e:java.io.IOException) { Result.retry() }
    }
    companion object {
        const val TAG="uploads"
        const val KEY_LOCAL="local"
        const val KEY_REMOTE="remote"
        const val KEY_CONTENT_TYPE="contentType"
        const val KEY_USER="user"
    }
}

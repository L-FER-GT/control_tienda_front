package com.lfergt.controltienda.data.media

import android.net.Uri
import com.lfergt.controltienda.data.di.DataConfig
import com.lfergt.controltienda.data.supabase.SupabaseAuth
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageImageResolver @Inject constructor(private val uploader: MediaUploader, private val config: DataConfig) {
    fun resolve(path: String): Any = pendingFile(path) ?: "${config.supabaseUrl.trimEnd('/')}/storage/v1/object/authenticated/${config.storageBucket}/${Uri.encode(path, "/") }"
    fun pendingFile(path: String): File? = uploader.pendingFileFor(path).takeIf { it.exists() }
}

@Singleton
class ImageHttpClientFactory @Inject constructor(private val auth: SupabaseAuth) {
    fun create(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request=chain.request(); val base=auth.config.supabaseUrl.toHttpUrlOrNull()
        if(base!=null && request.url.host==base.host && request.url.scheme==base.scheme && request.url.port==base.port && request.url.encodedPath.startsWith("/storage/v1/")) {
            val token=runBlocking { auth.accessToken() }
            chain.proceed(request.newBuilder().header("Authorization","Bearer $token").header("apikey",auth.config.supabaseAnonKey).build())
        } else chain.proceed(request)
    }.connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
}

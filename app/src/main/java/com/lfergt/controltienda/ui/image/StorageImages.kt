package com.lfergt.controltienda.ui.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.map.Mapper
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import coil3.request.crossfade
import com.lfergt.controltienda.data.media.ImageHttpClientFactory
import com.lfergt.controltienda.data.media.StorageImageResolver

/** Ruta de un archivo en la "carpeta del servidor", tal como está guardada en la base de datos. */
data class StoragePath(val path: String)

/** Convierte la ruta en la copia local pendiente de subir o en la URL de Cloud Storage. */
class StoragePathMapper(private val resolver: StorageImageResolver) : Mapper<StoragePath, Any> {
    override fun map(data: StoragePath, options: Options): Any = resolver.resolve(data.path)
}

/**
 * Cargador de imágenes con caché en disco de 250 MB: una foto descargada una vez se sigue
 * viendo sin conexión (los nombres de archivo nunca se reutilizan, así que la caché no queda obsoleta).
 */
fun buildImageLoader(context: Context, resolver: StorageImageResolver, clients: ImageHttpClientFactory): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(StoragePathMapper(resolver))
            add(OkHttpNetworkFetcherFactory(callFactory = { clients.create() }))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("images"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()

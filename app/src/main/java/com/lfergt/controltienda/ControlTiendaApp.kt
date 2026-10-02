package com.lfergt.controltienda

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.lfergt.controltienda.data.media.ImageHttpClientFactory
import com.lfergt.controltienda.data.media.StorageImageResolver
import com.lfergt.controltienda.notifications.NotificationChannels
import com.lfergt.controltienda.ui.image.buildImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ControlTiendaApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var imageResolver: StorageImageResolver
    @Inject lateinit var imageClients: ImageHttpClientFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        buildImageLoader(context, imageResolver, imageClients)
}

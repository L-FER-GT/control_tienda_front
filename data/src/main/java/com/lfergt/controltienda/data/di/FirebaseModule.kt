package com.lfergt.controltienda.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun auth(config: DataConfig): FirebaseAuth = FirebaseAuth.getInstance().apply {
        if (config.useEmulators) useEmulator(config.emulatorHost, 9099)
        setLanguageCode("es")
    }

    /**
     * Firestore con caché persistente ilimitada: es la "base de datos local".
     * Las lecturas funcionan sin conexión y las escrituras se encolan y se envían al reconectar.
     */
    @Provides
    @Singleton
    fun firestore(config: DataConfig): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        if (config.useEmulators) useEmulator(config.emulatorHost, 8080)
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build(),
            )
            .build()
    }

    @Provides
    @Singleton
    fun storage(config: DataConfig): FirebaseStorage = FirebaseStorage.getInstance().apply {
        if (config.useEmulators) useEmulator(config.emulatorHost, 9199)
        maxUploadRetryTimeMillis = 60_000
    }

    @Provides
    @Singleton
    fun functions(config: DataConfig): FirebaseFunctions =
        FirebaseFunctions.getInstance(config.functionsRegion).apply {
            if (config.useEmulators) useEmulator(config.emulatorHost, 5001)
        }

    @Provides
    @Singleton
    fun messaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

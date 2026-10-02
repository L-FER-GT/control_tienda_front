package com.lfergt.controltienda.di

import com.lfergt.controltienda.BuildConfig
import com.lfergt.controltienda.data.di.DataConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun dataConfig(): DataConfig = DataConfig(
        useEmulators = BuildConfig.USE_EMULATORS,
        emulatorHost = BuildConfig.EMULATOR_HOST,
        functionsRegion = BuildConfig.FUNCTIONS_REGION,
        appVersion = BuildConfig.VERSION_NAME,
    )
}

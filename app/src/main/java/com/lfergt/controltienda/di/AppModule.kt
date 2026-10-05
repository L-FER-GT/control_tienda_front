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
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        storageBucket = BuildConfig.SUPABASE_STORAGE_BUCKET,
        appVersion = BuildConfig.VERSION_NAME,
    )
}

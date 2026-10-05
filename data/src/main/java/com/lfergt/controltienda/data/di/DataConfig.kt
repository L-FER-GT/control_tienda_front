package com.lfergt.controltienda.data.di

data class DataConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val storageBucket: String = "media",
    val appVersion: String,
)

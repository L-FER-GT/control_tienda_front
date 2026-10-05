package com.lfergt.controltienda.data.di

data class DataConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val storageBucket: String = "media",
    val appVersion: String,
    val appVersionCode: Int = 0,
    /** "propietario/repositorio" de GitHub con los releases; vacío desactiva las actualizaciones. */
    val updateRepo: String = "",
)

package com.lfergt.controltienda.data.di

/** Configuración que el módulo :app entrega a la capa de datos (sale de BuildConfig). */
data class DataConfig(
    val useEmulators: Boolean,
    val emulatorHost: String,
    val functionsRegion: String,
    val appVersion: String,
)

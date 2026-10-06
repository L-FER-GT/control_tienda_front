package com.lfergt.controltienda.domain.port

import com.lfergt.controltienda.domain.model.AppUpdate
import com.lfergt.controltienda.domain.model.ExportFormat
import com.lfergt.controltienda.domain.model.ExportedFile
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.ReportTable
import kotlinx.coroutines.flow.Flow

/** Sugiere el nombre de un producto a partir de su código de barras (Open Food Facts). */
interface ProductInfoLookup {
    suspend fun findProductName(barcode: String): String?
}

/** Genera archivos PDF / Excel de un reporte para compartirlos. */
interface ReportExporter {
    suspend fun export(table: ReportTable, format: ExportFormat): ExportedFile
}

interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>
}

/**
 * Los cambios hechos sin conexión se confirman más tarde. Si el servidor los rechaza
 * (p. ej. el usuario perdió un permiso), el mensaje llega por aquí para avisarle.
 */
data class SyncStatus(
    val pending: Int = 0,
    val syncing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val lastFailure: String? = null,
)

interface SyncMonitor {
    val failures: Flow<String>
    val status: Flow<SyncStatus> get() = kotlinx.coroutines.flow.flowOf(SyncStatus())
    fun report(error: Throwable)
    fun queueChanged(pending: Int) {}
    fun syncing(active: Boolean) {}
    fun completed(at: Long) {}
    fun reset() {}
    fun acknowledgeFailure() {}
}

/** Tienda que cada usuario dejó abierta, para volver a ella al iniciar la app. */
interface LastStorePreference {
    fun get(uid: String): String?
    /** null olvida la tienda: el usuario volvió a la lista. */
    fun set(uid: String, storeId: String?)
}

/** Versiones de la app publicadas fuera de una tienda de apps (Releases de GitHub). */
interface AppUpdates {
    /** false en compilaciones sin canal de publicación (debug o locales). */
    val enabled: Boolean

    /** La última versión publicada si es posterior a la instalada; null si ya está al día. */
    suspend fun findNewer(): AppUpdate?

    /** Descarga y verifica el instalador; el archivo queda listo para el instalador del sistema. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): LocalFile
}

/** Reloj inyectable para poder probar la lógica que depende de la hora. */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

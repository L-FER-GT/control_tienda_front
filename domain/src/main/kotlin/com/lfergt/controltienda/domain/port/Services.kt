package com.lfergt.controltienda.domain.port

import com.lfergt.controltienda.domain.model.ExportFormat
import com.lfergt.controltienda.domain.model.ExportedFile
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
interface SyncMonitor {
    val failures: Flow<String>
    fun report(error: Throwable)
}

/** Reloj inyectable para poder probar la lógica que depende de la hora. */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

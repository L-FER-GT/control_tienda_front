package com.lfergt.controltienda.ui.components

import com.lfergt.controltienda.domain.port.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncIndicatorTest {

    @Test
    fun `sin pendientes, con conexión y sin errores no se muestra nada`() {
        assertNull(syncIndicator(SyncStatus(lastSyncedAt = 1L), offline = false))
    }

    @Test
    fun `el error tiene prioridad sobre la falta de conexión y los pendientes`() {
        assertEquals(SyncIndicator.ERROR, syncIndicator(SyncStatus(pending = 2, lastFailure = "Sin permiso"), offline = true))
        assertEquals(SyncIndicator.OFFLINE, syncIndicator(SyncStatus(pending = 2), offline = true))
        assertEquals(SyncIndicator.OFFLINE, syncIndicator(SyncStatus(), offline = true))
        assertEquals(SyncIndicator.PENDING, syncIndicator(SyncStatus(pending = 1), offline = false))
        assertEquals(SyncIndicator.PENDING, syncIndicator(SyncStatus(syncing = true), offline = false))
    }

    @Test
    fun `las etiquetas usan singular y plural`() {
        assertEquals("1 cambio pendiente", syncLabel(SyncIndicator.PENDING, 1))
        assertEquals("Sin conexión · 3 cambios pendientes", syncLabel(SyncIndicator.OFFLINE, 3))
        assertEquals("Sin conexión", syncLabel(SyncIndicator.OFFLINE, 0))
        assertEquals("Enviando cambios", syncLabel(SyncIndicator.PENDING, 0))
    }
}

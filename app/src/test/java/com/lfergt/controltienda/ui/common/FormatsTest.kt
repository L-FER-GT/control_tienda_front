package com.lfergt.controltienda.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FormatsTest {

    private val lima = ZoneId.of("America/Lima")
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(lima).toInstant().toEpochMilli()

    private val now = at(2026, 10, 6, 16, 0)

    @Test
    fun `la hora se muestra en formato de 12 horas`() {
        assertEquals("2:32 p. m.", formatTime(at(2026, 10, 6, 14, 32), lima))
        assertEquals("12:05 a. m.", formatTime(at(2026, 10, 6, 0, 5), lima))
        assertEquals("12:00 p. m.", formatTime(at(2026, 10, 6, 12, 0), lima))
        assertEquals("9:07 a. m.", formatTime(at(2026, 10, 6, 9, 7), lima))
    }

    @Test
    fun `los días cercanos se nombran y los demás llevan día y mes`() {
        assertEquals("Hoy, 2:32 p. m.", formatWhen(at(2026, 10, 6, 14, 32), now, lima))
        assertEquals("Ayer, 11:59 p. m.", formatWhen(at(2026, 10, 5, 23, 59), now, lima))
        assertEquals("jue 1 oct", formatDay(at(2026, 10, 1, 8, 0), now, lima))
        assertEquals("mar 15 set", formatDay(at(2026, 9, 15, 8, 0), now, lima))
        assertEquals("31 dic 2025", formatDay(at(2025, 12, 31, 8, 0), now, lima))
    }

    @Test
    fun `la fecha corta incluye el año`() {
        assertEquals("6 oct 2026", formatShortDate(now, lima))
    }

    @Test
    fun `los plurales concuerdan con la cantidad`() {
        assertEquals("1 producto", plural(1, "producto", "productos"))
        assertEquals("0 productos", plural(0, "producto", "productos"))
        assertEquals("3 productos", plural(3, "producto", "productos"))
    }
}

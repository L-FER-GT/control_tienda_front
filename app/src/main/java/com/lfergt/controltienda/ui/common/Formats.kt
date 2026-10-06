package com.lfergt.controltienda.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/*
 * Fechas y cantidades como se leen en Perú: hora de 12 horas con "a. m."/"p. m.", días cercanos por su
 * nombre ("Hoy", "Ayer") y meses abreviados ("set" para setiembre). Los nombres son fijos para que no
 * dependan de la versión de Android ni de la configuración regional del celular.
 */

private val weekDays = arrayOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val months = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic")

private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** 14:32 -> "2:32 p. m."; 00:05 -> "12:05 a. m.". */
fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    val hour = (time.hour % 12).let { if (it == 0) 12 else it }
    return String.format(Locale.ROOT, "%d:%02d %s", hour, time.minute, if (time.hour < 12) "a. m." else "p. m.")
}

/** "5 oct 2026". */
fun formatShortDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = millis.toLocalDate(zone)
    return "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}"
}

/** "Hoy", "Ayer", "lun 5 oct" en el año en curso y "5 oct 2025" en otro año. */
fun formatDay(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val date = millis.toLocalDate(zone)
    val today = now.toLocalDate(zone)
    return when {
        date == today -> "Hoy"
        date == today.minusDays(1) -> "Ayer"
        date.year == today.year -> "${weekDays[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${months[date.monthValue - 1]}"
        else -> formatShortDate(millis, zone)
    }
}

/** "Hoy, 2:32 p. m.", "Ayer, 9:05 a. m." o "lun 5 oct, 6:10 p. m.". */
fun formatWhen(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String =
    "${formatDay(millis, now, zone)}, ${formatTime(millis, zone)}"

/** "1 producto", "3 productos". */
fun plural(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"

package com.lfergt.controltienda.domain.model

import kotlin.math.roundToLong

/**
 * Los montos se guardan en céntimos (Long) para evitar errores de redondeo con Double.
 * Los precios ya incluyen IGV: no se desglosa ningún impuesto.
 */
object Money {
    fun lineTotal(unitPriceCents: Long, quantity: Double): Long = (unitPriceCents * quantity).roundToLong()

    /** Convierte un texto como "12.5" o "12,50" a céntimos. Devuelve null si no es válido. */
    fun parseToCents(text: String): Long? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** Formato sin símbolo: 1234567 -> "12,345.67" */
    fun formatPlain(cents: Long): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val units = abs / 100
        val decimals = (abs % 100).toString().padStart(2, '0')
        val grouped = units.toString().reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "-" else "") + grouped + "." + decimals
    }

    fun format(cents: Long, currency: String): String = "${currencySymbol(currency)} ${formatPlain(cents)}"

    /** Texto editable (sin separador de miles): 1250 -> "12.50" */
    fun toInput(cents: Long?): String = cents?.let {
        val abs = kotlin.math.abs(it)
        (if (it < 0) "-" else "") + "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    } ?: ""

    fun currencySymbol(code: String): String = SUPPORTED_CURRENCIES[code] ?: code

    val SUPPORTED_CURRENCIES: Map<String, String> = linkedMapOf(
        "PEN" to "S/",
        "USD" to "$",
        "EUR" to "€",
        "CLP" to "CLP$",
        "COP" to "COL$",
        "MXN" to "MX$",
        "ARS" to "AR$",
        "BOB" to "Bs",
    )

    const val DEFAULT_CURRENCY = "PEN"
}

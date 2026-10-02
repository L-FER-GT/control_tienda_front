package com.lfergt.controltienda.domain.model

data class Category(
    val id: String,
    val name: String,
    val photoPath: String?,
    val updatedAt: Long = 0,
) {
    companion object {
        /** Categoría virtual "Todos": siempre existe y muestra todos los productos sin excepción. */
        const val ALL_ID = "__all__"
    }
}

enum class MeasureUnit(val key: String, val symbol: String, val label: String, val allowsDecimals: Boolean) {
    UNIT("unit", "und", "Unidades", false),
    KG("kg", "kg", "Kilos", true),
    GRAM("g", "g", "Gramos", true),
    LITER("l", "L", "Litros", true),
    MILLILITER("ml", "ml", "Mililitros", true),
    METER("m", "m", "Metros", true),
    PACK("pack", "paq", "Paquetes", false),
    BOX("box", "caja", "Cajas", false),
    DOZEN("dozen", "doc", "Docenas", false);

    /** 2.0 -> "2", 1.25 -> "1.25" */
    fun formatQuantity(quantity: Double): String {
        val rounded = kotlin.math.round(quantity * 1000) / 1000
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    companion object {
        fun fromKey(key: String?): MeasureUnit = entries.firstOrNull { it.key == key } ?: UNIT
    }
}

data class Product(
    val id: String,
    val name: String,
    val categoryId: String?,
    /** Precio de venta (obligatorio), IGV incluido. */
    val salePriceCents: Long,
    /** Costo de compra (opcional). */
    val purchaseCostCents: Long?,
    val unit: MeasureUnit,
    /** null = stock ilimitado (no se controla). Puede ser negativo: las ventas no se bloquean. */
    val stock: Double?,
    /** Nivel mínimo a partir del cual se alerta (> 0). null = solo se alerta en 0 o negativo. */
    val stockAlert: Double?,
    val barcode: String?,
    val qrCode: String?,
    val photoPath: String?,
    val updatedAt: Long = 0,
) {
    val tracksStock: Boolean get() = stock != null

    fun matchesCode(code: String): Boolean = code.isNotBlank() && (barcode == code || qrCode == code)
}

data class ProductDraft(
    val id: String?,
    val name: String,
    val categoryId: String?,
    val salePriceCents: Long,
    val purchaseCostCents: Long?,
    val unit: MeasureUnit,
    val stock: Double?,
    val stockAlert: Double?,
    val barcode: String?,
    val qrCode: String?,
    /** Estado anterior, para registrar cambios de precio en el historial. */
    val previous: Product? = null,
)

enum class PriceChangeSource(val key: String) {
    CREATED("created"),
    MANUAL("manual"),
    RECEPTION("reception");

    companion object {
        fun fromKey(key: String?): PriceChangeSource = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

/** Historial: el mismo producto puede comprarse y venderse a precios distintos en la misma semana. */
data class PriceHistoryEntry(
    val id: String,
    val salePriceCents: Long?,
    val purchaseCostCents: Long?,
    val source: PriceChangeSource,
    val refId: String?,
    val changedByName: String,
    val at: Long,
)

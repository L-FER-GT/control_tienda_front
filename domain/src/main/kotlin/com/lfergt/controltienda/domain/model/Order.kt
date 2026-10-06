package com.lfergt.controltienda.domain.model

/** Método de pago: solo informativo (no hay pasarela, anulaciones ni descuentos). */
enum class PaymentMethod(val key: String, val label: String) {
    CASH("cash", "Efectivo"),
    CARD("card", "Tarjeta"),
    YAPE("yape", "Yape"),
    PLIN("plin", "Plin"),
    TRANSFER("transfer", "Transferencia"),
    OTHER("other", "Otro");

    companion object {
        fun fromKey(key: String?): PaymentMethod = entries.firstOrNull { it.key == key } ?: CASH
    }
}

data class OrderItem(
    /** null en ítems manuales: no están ligados a un producto y no modifican inventario. */
    val productId: String?,
    val description: String,
    val categoryId: String?,
    val categoryName: String?,
    val quantity: Double,
    val unit: MeasureUnit,
    val unitPriceCents: Long,
    /** Costo de compra al momento de la venta (para el reporte de ganancias). */
    val unitCostCents: Long?,
    val manual: Boolean,
) : java.io.Serializable {
    val subtotalCents: Long get() = Money.lineTotal(unitPriceCents, quantity)
}

data class Order(
    val id: String,
    /** Número correlativo por tienda. Lo asigna el servidor al sincronizar; null mientras tanto. */
    val number: Long?,
    val storeId: String,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Long,
    val paymentMethod: PaymentMethod,
    val currency: String,
    val items: List<OrderItem>,
    val totalCents: Long,
    /** true si aún no llegó al servidor o el servidor no le asignó número. */
    val pendingSync: Boolean,
) {
    val displayNumber: String get() = number?.let { formatNumber(it) } ?: "Pendiente"

    companion object {
        fun formatNumber(number: Long): String = "N° " + number.toString().padStart(6, '0')
    }
}

data class OrderDraft(
    val items: List<OrderItem>,
    val paymentMethod: PaymentMethod,
    val currency: String,
    val createdAt: Long,
) {
    val totalCents: Long get() = items.sumOf { it.subtotalCents }
}

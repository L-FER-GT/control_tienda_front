package com.lfergt.controltienda.domain.model

data class Supplier(
    val id: String,
    val companyName: String,
    val ruc: String?,
    val phone: String?,
    val contactName: String?,
    val email: String?,
    val address: String?,
    val notes: String?,
) {
    val isOthers: Boolean get() = id == OTHERS_ID

    companion object {
        /** Proveedor "Otros": siempre existe, no se guarda en la base de datos ni se puede borrar. */
        const val OTHERS_ID = "otros"
        val OTHERS = Supplier(OTHERS_ID, "Otros", null, null, null, null, null, null)

        /** RUC peruano: 11 dígitos, empieza en 10, 15, 16, 17 o 20. */
        fun isValidRuc(ruc: String): Boolean =
            ruc.length == 11 && ruc.all { it.isDigit() } && ruc.take(2) in setOf("10", "15", "16", "17", "20")
    }
}

data class ReceptionLine(
    val productId: String,
    val productName: String,
    val quantity: Double,
    val unit: MeasureUnit,
    /** Costo unitario de esta compra (opcional). Actualiza el costo del producto y su historial. */
    val unitCostCents: Long?,
) {
    val subtotalCents: Long? get() = unitCostCents?.let { Money.lineTotal(it, quantity) }
}

/** Recepción de mercadería: suma stock y registra el costo de compra. Se puede editar. */
data class Reception(
    val id: String,
    val supplierId: String,
    val supplierName: String,
    val lines: List<ReceptionLine>,
    val invoiceTotalCents: Long?,
    /** Rutas en el servidor de las fotos de las facturas. */
    val invoicePhotos: List<String>,
    val notes: String?,
    val receivedAt: Long,
    val createdBy: String,
    val createdByName: String,
    val updatedAt: Long,
    val pendingSync: Boolean = false,
)

data class ReceptionDraft(
    val id: String?,
    val supplierId: String,
    val supplierName: String,
    val lines: List<ReceptionLine>,
    val invoiceTotalCents: Long?,
    /** Fotos que ya estaban guardadas y se conservan. */
    val keptPhotos: List<String>,
    val newPhotos: List<LocalFile>,
    val notes: String?,
    val receivedAt: Long,
)

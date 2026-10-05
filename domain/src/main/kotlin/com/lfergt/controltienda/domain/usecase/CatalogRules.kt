package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.ProductDraft

data class StockAlertResult(
    /** Stock en 0 o negativo. */
    val outOfStock: List<Product>,
    /** Stock positivo pero en o por debajo de su alertaStock. */
    val belowMinimum: List<Product>,
) {
    val total: Int get() = outOfStock.size + belowMinimum.size
}

/**
 * Alertas de stock mínimo. Solo aplica a productos con stock marcado: si el campo está vacío
 * el stock es ilimitado. El stock puede ser negativo porque las ventas no se bloquean.
 */
object StockAlerts {
    fun compute(products: List<Product>): StockAlertResult {
        val tracked = products.filter { it.stock != null }
        val out = tracked.filter { it.stock!! <= 0.0 }.sortedBy { it.stock }
        val low = tracked
            .filter { p -> p.stock!! > 0.0 && p.stockAlert != null && p.stockAlert > 0.0 && p.stock <= p.stockAlert }
            .sortedBy { it.stock!! / it.stockAlert!! }
        return StockAlertResult(out, low)
    }
}

object ProductValidator {
    const val FIELD_NAME = "name"
    const val FIELD_SALE_PRICE = "salePrice"
    const val FIELD_PURCHASE_COST = "purchaseCost"
    const val FIELD_STOCK_ALERT = "stockAlert"
    const val FIELD_BARCODE = "barcode"
    const val FIELD_QR = "qr"

    /** Devuelve campo -> mensaje de error. Vacío si el borrador es válido. */
    fun validate(draft: ProductDraft, existing: List<Product>): Map<String, String> = buildMap {
        if (draft.name.isBlank()) put(FIELD_NAME, "El nombre es obligatorio")
        if (draft.salePriceCents < 0) put(FIELD_SALE_PRICE, "El precio de venta no puede ser negativo")
        if ((draft.purchaseCostCents ?: 0) < 0) put(FIELD_PURCHASE_COST, "El costo no puede ser negativo")
        if (draft.stockAlert != null && draft.stockAlert <= 0) put(FIELD_STOCK_ALERT, "La alerta debe ser mayor a 0")
        if (draft.stockAlert != null && draft.stock == null) {
            put(FIELD_STOCK_ALERT, "Para usar la alerta, el producto debe tener stock")
        }
        val others = existing.filter { it.id != draft.id }
        draft.barcode?.trim()?.takeIf { it.isNotBlank() }?.let { code ->
            others.firstOrNull { it.matchesCode(code) }?.let { put(FIELD_BARCODE, "El código ya pertenece a \"${it.name}\"") }
        }
        draft.qrCode?.trim()?.takeIf { it.isNotBlank() }?.let { code ->
            others.firstOrNull { it.matchesCode(code) }?.let { put(FIELD_QR, "El QR ya pertenece a \"${it.name}\"") }
        }
    }
}

object CategoryRules {
    /** Productos seleccionados que ya pertenecen a otra categoría y requieren confirmación para moverlos. */
    fun conflicts(selected: List<Product>, targetCategoryId: String): List<Product> =
        selected.filter { it.categoryId != null && it.categoryId != targetCategoryId }

    fun productsOf(categoryId: String, products: List<Product>): List<Product> =
        if (categoryId == Category.ALL_ID) products else products.filter { it.categoryId == categoryId }

    fun uncategorized(products: List<Product>, categories: List<Category>): List<Product> {
        val ids = categories.map { it.id }.toSet()
        return products.filter { it.categoryId == null || it.categoryId !in ids }
    }

    fun nameOf(categoryId: String?, categories: List<Category>): String? =
        categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
}

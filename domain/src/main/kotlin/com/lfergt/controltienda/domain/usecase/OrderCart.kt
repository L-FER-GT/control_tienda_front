package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.OrderDraft
import com.lfergt.controltienda.domain.model.OrderItem
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.Product

data class CartLine(val key: String, val item: OrderItem) : java.io.Serializable

/** Carrito inmutable de "Crear orden". */
data class OrderCart(
    val lines: List<CartLine> = emptyList(),
    private val manualSeq: Int = 0,
) : java.io.Serializable {
    val totalCents: Long get() = lines.sumOf { it.item.subtotalCents }
    val isEmpty: Boolean get() = lines.isEmpty()
    val itemCount: Int get() = lines.size

    /** Agrega un producto registrado; si ya está en el carrito, suma la cantidad. */
    fun addProduct(product: Product, categoryName: String?, quantity: Double = 1.0): OrderCart {
        val key = "p:${product.id}"
        val existing = lines.firstOrNull { it.key == key }
        return if (existing != null) {
            setQuantity(key, existing.item.quantity + quantity)
        } else {
            copy(
                lines = lines + CartLine(
                    key,
                    OrderItem(
                        productId = product.id,
                        description = product.name,
                        categoryId = product.categoryId,
                        categoryName = categoryName,
                        quantity = quantity,
                        unit = product.unit,
                        unitPriceCents = product.salePriceCents,
                        unitCostCents = product.purchaseCostCents,
                        manual = false,
                    ),
                ),
            )
        }
    }

    /** Ítem manual: solo detalle, categoría, precio y cantidad. No modifica inventario. */
    fun addManual(
        description: String,
        categoryId: String?,
        categoryName: String?,
        unitPriceCents: Long,
        quantity: Double,
        unit: MeasureUnit,
    ): OrderCart {
        val seq = manualSeq + 1
        return copy(
            manualSeq = seq,
            lines = lines + CartLine(
                "m:$seq",
                OrderItem(
                    productId = null,
                    description = description.trim(),
                    categoryId = categoryId,
                    categoryName = categoryName,
                    quantity = quantity,
                    unit = unit,
                    unitPriceCents = unitPriceCents,
                    unitCostCents = null,
                    manual = true,
                ),
            ),
        )
    }

    /** Cantidad <= 0 elimina la línea. */
    fun setQuantity(key: String, quantity: Double): OrderCart =
        if (quantity <= 0) remove(key)
        else copy(lines = lines.map { if (it.key == key) it.copy(item = it.item.copy(quantity = quantity)) else it })

    fun remove(key: String): OrderCart = copy(lines = lines.filterNot { it.key == key })

    fun clear(): OrderCart = OrderCart()

    fun toDraft(paymentMethod: PaymentMethod, currency: String, now: Long): OrderDraft =
        OrderDraft(items = lines.map { it.item }, paymentMethod = paymentMethod, currency = currency, createdAt = now)
}

/**
 * Evita registrar dos veces una lectura, ya que la cámara analiza varios cuadros por segundo:
 * - Después de cada lectura aceptada se ignora todo durante [pauseMs], sea el código que sea.
 * - Un código aceptado que sigue a la vista (visto hace menos de [visibleMs]) no se vuelve a sumar,
 *   aunque entre medio la cámara haya leído otro (una lectura errónea o un código vecino).
 *   Para agregar otra unidad hay que retirar el producto del cuadro y volver a enfocarlo.
 */
class ScanDebouncer(private val pauseMs: Long = 1_000, private val visibleMs: Long = 1_500) {
    private var lastAcceptedAt: Long? = null
    private val inView = HashMap<String, Long>()

    fun accept(code: String, now: Long): Boolean {
        inView.entries.removeAll { now - it.value >= visibleMs }
        if (code in inView) {
            inView[code] = now
            return false
        }
        val accepted = lastAcceptedAt
        if (accepted != null && now - accepted < pauseMs) return false
        inView[code] = now
        lastAcceptedAt = now
        return true
    }

    fun reset() {
        lastAcceptedAt = null
        inView.clear()
    }
}

package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderCartTest {

    @Test
    fun `escanear el mismo producto suma la cantidad en la misma línea`() {
        val p = product("a", price = 350, cost = 200)
        val cart = OrderCart().addProduct(p, "Bebidas").addProduct(p, "Bebidas")

        assertEquals(1, cart.itemCount)
        assertEquals(2.0, cart.lines.single().item.quantity, 0.0)
        assertEquals(700, cart.totalCents)
        assertEquals(200L, cart.lines.single().item.unitCostCents)
    }

    @Test
    fun `los ítems manuales no se fusionan y no tienen producto`() {
        val cart = OrderCart()
            .addManual("Bolsa de pan", null, null, 50, 3.0, MeasureUnit.UNIT)
            .addManual("Bolsa de pan", null, null, 50, 1.0, MeasureUnit.UNIT)

        assertEquals(2, cart.itemCount)
        assertTrue(cart.lines.all { it.item.manual && it.item.productId == null })
        assertEquals(200, cart.totalCents)
    }

    @Test
    fun `cantidades decimales para productos por kilo`() {
        val queso = product("q", price = 3290, unit = MeasureUnit.KG)
        val cart = OrderCart().addProduct(queso, null, quantity = 0.75)
        assertEquals(2468, cart.totalCents) // 32.90 * 0.75 = 24.675 -> 24.68
    }

    @Test
    fun `cantidad cero elimina la línea`() {
        val cart = OrderCart().addProduct(product("a"), null)
        val key = cart.lines.single().key
        assertTrue(cart.setQuantity(key, 0.0).isEmpty)
    }

    @Test
    fun `el borrador conserva método de pago y total`() {
        val draft = OrderCart().addProduct(product("a", price = 1000), null)
            .toDraft(PaymentMethod.YAPE, "PEN", now = 123L)
        assertEquals(PaymentMethod.YAPE, draft.paymentMethod)
        assertEquals(1000, draft.totalCents)
        assertEquals(123L, draft.createdAt)
    }

    @Test
    fun `el debouncer ignora lecturas continuas del mismo código`() {
        val debouncer = ScanDebouncer(cooldownMs = 1000)
        assertTrue(debouncer.accept("775", now = 0))
        assertFalse(debouncer.accept("775", now = 300))
        assertFalse(debouncer.accept("775", now = 1200)) // sigue a la vista: la ventana se desliza
        assertTrue(debouncer.accept("775", now = 2500)) // se retiró y volvió a enfocar
        assertTrue(debouncer.accept("999", now = 2600))
    }

    @Test
    fun `ítem manual sin costo`() {
        val cart = OrderCart().addManual("Servicio", null, null, 500, 1.0, MeasureUnit.UNIT)
        assertNull(cart.lines.single().item.unitCostCents)
    }
}

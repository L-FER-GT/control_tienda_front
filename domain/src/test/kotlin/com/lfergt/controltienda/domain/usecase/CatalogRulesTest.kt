package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.ProductDraft
import com.lfergt.controltienda.domain.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRulesTest {

    @Test
    fun `stock vacío es ilimitado y no genera alerta`() {
        val result = StockAlerts.compute(listOf(product("a", stock = null, alert = 5.0)))
        assertEquals(0, result.total)
    }

    @Test
    fun `stock cero o negativo siempre alerta, y positivo solo si tiene alertaStock`() {
        val products = listOf(
            product("neg", stock = -2.0),
            product("cero", stock = 0.0),
            product("bajo", stock = 3.0, alert = 5.0),
            product("ok", stock = 10.0, alert = 5.0),
            product("sinAlerta", stock = 1.0),
        )
        val result = StockAlerts.compute(products)
        assertEquals(listOf("neg", "cero"), result.outOfStock.map { it.id })
        assertEquals(listOf("bajo"), result.belowMinimum.map { it.id })
    }

    private fun draft(id: String? = null, name: String = "Arroz", barcode: String? = null, qr: String? = null, stock: Double? = null, alert: Double? = null) =
        ProductDraft(id, name, null, 450, null, MeasureUnit.UNIT, stock, alert, barcode, qr)

    @Test
    fun `código de barras duplicado es inválido`() {
        val existing = listOf(product("a", name = "Leche", barcode = "7751271011324"))
        val errors = ProductValidator.validate(draft(barcode = "7751271011324"), existing)
        assertTrue(errors.containsKey(ProductValidator.FIELD_BARCODE))
        // Editar el mismo producto con su propio código sí es válido
        assertTrue(ProductValidator.validate(draft(id = "a", barcode = "7751271011324"), existing).isEmpty())
    }

    @Test
    fun `el mismo producto admite QR y barras con el mismo contenido`() {
        assertTrue(ProductValidator.validate(draft(barcode = "AB-123", qr = "AB-123"), emptyList()).isEmpty())
    }

    @Test
    fun `los espacios no permiten duplicar el codigo de otro producto`() {
        val existing = listOf(product("a", barcode = "AB-123"))
        assertTrue(ProductValidator.validate(draft(barcode = " AB-123 "), existing).containsKey(ProductValidator.FIELD_BARCODE))
        assertTrue(ProductValidator.validate(draft(qr = " AB-123 "), existing).containsKey(ProductValidator.FIELD_QR))
    }

    @Test
    fun `nombre obligatorio y alerta requiere stock`() {
        val errors = ProductValidator.validate(draft(name = " ", alert = 3.0), emptyList())
        assertTrue(errors.containsKey(ProductValidator.FIELD_NAME))
        assertTrue(errors.containsKey(ProductValidator.FIELD_STOCK_ALERT))
    }

    @Test
    fun `asignar categoría advierte solo por productos de otra categoría`() {
        val products = listOf(product("a", categoryId = "bebidas"), product("b"), product("c", categoryId = "snacks"))
        val conflicts = CategoryRules.conflicts(products, "snacks")
        assertEquals(listOf("a"), conflicts.map { it.id })
    }

    @Test
    fun `la categoría Todos muestra todo y sin categoría incluye categorías borradas`() {
        val products = listOf(product("a", categoryId = "x"), product("b"), product("c", categoryId = "borrada"))
        val categories = listOf(Category("x", "X", null))
        assertEquals(3, CategoryRules.productsOf(Category.ALL_ID, products).size)
        assertEquals(listOf("b", "c"), CategoryRules.uncategorized(products, categories).map { it.id })
    }

    @Test
    fun `parseo y formato de montos`() {
        assertEquals(1250L, Money.parseToCents("12.5"))
        assertEquals(1250L, Money.parseToCents("12,50"))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("-1"))
        assertEquals("1,234,567.89", Money.formatPlain(123456789))
        assertEquals("S/ 0.05", Money.format(5, "PEN"))
        assertEquals("12.50", Money.toInput(1250))
    }
}

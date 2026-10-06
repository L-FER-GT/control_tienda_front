package com.lfergt.controltienda.feature

import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.feature.catalog.StockFilter
import com.lfergt.controltienda.feature.purchasing.invoiceDifference
import com.lfergt.controltienda.testing.testProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas pequeñas de las pantallas: diferencia de factura, filtro de stock y número de venta. */
class SmallRulesTest {

    @Test
    fun `la diferencia de factura solo aparece si hay productos y no coincide`() {
        assertEquals(500L, invoiceDifference(12_000, hasLines = true, linesCents = 11_500))
        assertEquals(-200L, invoiceDifference(1_000, hasLines = true, linesCents = 1_200))
        assertNull(invoiceDifference(12_000, hasLines = true, linesCents = 12_000))
        assertNull(invoiceDifference(null, hasLines = true, linesCents = 1_000))
        assertNull(invoiceDifference(12_000, hasLines = false, linesCents = 0))
    }

    @Test
    fun `el filtro de stock separa agotados y stock bajo`() {
        val agotado = testProduct("a").copy(stock = 0.0)
        val negativo = testProduct("n").copy(stock = -2.0)
        val bajo = testProduct("b").copy(stock = 3.0, stockAlert = 5.0)
        val normal = testProduct("o").copy(stock = 20.0, stockAlert = 5.0)
        val sinControl = testProduct("s").copy(stock = null)
        assertTrue(StockFilter.OUT.matches(agotado) && StockFilter.OUT.matches(negativo))
        assertFalse(StockFilter.OUT.matches(bajo) || StockFilter.OUT.matches(sinControl))
        assertTrue(StockFilter.LOW.matches(bajo))
        assertFalse(StockFilter.LOW.matches(normal) || StockFilter.LOW.matches(agotado) || StockFilter.LOW.matches(sinControl))
        assertTrue(listOf(agotado, bajo, normal, sinControl).all { StockFilter.ALL.matches(it) })
    }

    @Test
    fun `el número de venta se muestra sin ceros a la izquierda`() {
        assertEquals("N° 123", Order.formatNumber(123))
        assertEquals("N° 1", Order.formatNumber(1))
    }
}

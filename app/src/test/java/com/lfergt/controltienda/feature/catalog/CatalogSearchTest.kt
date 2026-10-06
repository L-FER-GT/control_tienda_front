package com.lfergt.controltienda.feature.catalog

import com.lfergt.controltienda.testing.testProduct
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSearchTest {

    private val products = listOf(
        testProduct("cafe", "Café Altomayo"),
        testProduct("leche", "Leche Gloria", barcode = "7751271011324"),
        testProduct("pan", "Pan francés", qr = "QR-PAN"),
    )

    private fun ids(query: String) = products.search(query).map { it.id }

    @Test
    fun `los nombres se encuentran sin importar tildes ni mayúsculas`() {
        assertEquals(listOf("cafe"), ids("cafe"))
        assertEquals(listOf("cafe"), ids("CAFÉ"))
        assertEquals(listOf("pan"), ids("frances"))
    }

    @Test
    fun `varias palabras deben aparecer todas, en cualquier orden`() {
        assertEquals(listOf("leche"), ids("gloria leche"))
        assertEquals(emptyList<String>(), ids("leche altomayo"))
    }

    @Test
    fun `los códigos se buscan como identificadores`() {
        assertEquals(listOf("leche"), ids("7751271"))
        assertEquals(listOf("pan"), ids("qr-pan"))
    }

    @Test
    fun `una búsqueda vacía o con espacios restaura la lista completa`() {
        assertEquals(products.map { it.id }, ids("   "))
    }
}

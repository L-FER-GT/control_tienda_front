package com.lfergt.controltienda.data.openfoodfacts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsLookupTest {

    private class FakeApi(private val response: ProductResponse) : OpenFoodFactsApi {
        override suspend fun product(barcode: String, fields: String) = response
    }

    private fun lookup(product: OffProduct?, status: Int = 1) = OpenFoodFactsLookup(FakeApi(ProductResponse(status, product)))

    @Test
    fun `no repite la marca si el nombre ya la contiene`() = runTest {
        val name = lookup(OffProduct(name = "Coca-Cola Original", brands = "COCA-COLA SERVICES SA/NV", quantity = "330 ml"))
            .findProductName("5449000000996")
        assertEquals("Coca-Cola Original 330 ml", name)
    }

    @Test
    fun `prefiere el nombre en español y agrega marca y cantidad`() = runTest {
        val name = lookup(OffProduct(name = "Evaporated milk", nameEs = "Leche evaporada", brands = "Gloria, Leche Gloria", quantity = "400 g"))
            .findProductName("7751271011324")
        assertEquals("Leche evaporada Gloria 400 g", name)
    }

    @Test
    fun `sin producto o con código inválido no sugiere nada`() = runTest {
        assertNull(lookup(null, status = 0).findProductName("7751271011324"))
        assertNull(lookup(OffProduct(name = "X")).findProductName("12ab"))
    }
}

package com.lfergt.controltienda.data.openfoodfacts

import android.util.Log
import com.lfergt.controltienda.domain.port.ProductInfoLookup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

/** API pública de Open Food Facts: https://openfoodfacts.github.io/openfoodfacts-server/api/ */
interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,product_name_es,brands,quantity",
    ): ProductResponse
}

@Serializable
data class ProductResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val name: String? = null,
    @SerialName("product_name_es") val nameEs: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
)

/**
 * Sugiere el nombre al registrar un código nuevo: "Leche evaporada Gloria 400 g".
 * Es solo una ayuda: si no hay internet o el producto no existe, devuelve null.
 */
@Singleton
class OpenFoodFactsLookup @Inject constructor(
    private val api: OpenFoodFactsApi,
) : ProductInfoLookup {

    override suspend fun findProductName(barcode: String): String? {
        if (barcode.length < 8 || !barcode.all(Char::isDigit)) return null
        return try {
            val response = api.product(barcode)
            val product = response.product ?: return null
            if (response.status != 1) return null
            buildName(product)
        } catch (e: Exception) {
            Log.d("OpenFoodFacts", "Sin sugerencia para $barcode", e)
            null
        }
    }

    internal fun buildName(product: OffProduct): String? {
        val base = (product.nameEs?.takeIf { it.isNotBlank() } ?: product.name)?.trim()
        if (base.isNullOrEmpty()) return null
        val brand = product.brands?.split(',')?.firstOrNull()?.trim()?.takeIf {
            it.isNotEmpty() && !base.contains(it, ignoreCase = true)
        }
        val quantity = product.quantity?.trim()?.takeIf { it.isNotEmpty() && !base.contains(it, ignoreCase = true) }
        return listOfNotNull(base, brand, quantity).joinToString(" ").take(120)
    }
}

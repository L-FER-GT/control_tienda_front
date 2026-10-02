package com.lfergt.controltienda.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.usecase.CategoryRules
import com.lfergt.controltienda.ui.common.StoreContext
import com.lfergt.controltienda.ui.common.StoreHeader
import com.lfergt.controltienda.ui.components.StockLabel
import com.lfergt.controltienda.ui.components.StorageImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class CatalogData(
    val header: StoreHeader = StoreHeader(),
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val loaded: Boolean = false,
) {
    fun categoryName(id: String?): String? = CategoryRules.nameOf(id, categories)
    fun countIn(categoryId: String): Int = CategoryRules.productsOf(categoryId, products).size
}

/** Tienda + categorías + productos (desde la base local, actualizados en tiempo real). */
class CatalogSource @Inject constructor(
    private val context: StoreContext,
    private val catalog: CatalogRepository,
) {
    fun observe(storeId: String): Flow<CatalogData> = combine(
        context.header(storeId),
        catalog.observeCategories(storeId),
        catalog.observeProducts(storeId),
    ) { header, categories, products -> CatalogData(header, categories, products, header.loaded) }
}

fun List<Product>.search(query: String): List<Product> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    return filter { it.name.lowercase().contains(q) || it.barcode == q || it.qrCode == q }
}

/** Detalle rápido de un producto (al tocarlo en "Ver productos"). */
@Composable
fun ProductDetailDialog(product: Product, currency: String, categoryName: String?, showStock: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp),
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageImage(product.photoPath, product.name, Modifier.fillMaxWidth().aspectRatio(1.3f))
                Text(product.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    Money.format(product.salePriceCents, currency) + " / " + product.unit.symbol,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text("Categoría: ${categoryName ?: "Sin categoría"}", style = MaterialTheme.typography.bodyMedium)
                if (showStock) StockLabel(product)
                product.barcode?.let { Text("Código de barras: $it", style = MaterialTheme.typography.bodySmall) }
                product.qrCode?.let { Text("QR: $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

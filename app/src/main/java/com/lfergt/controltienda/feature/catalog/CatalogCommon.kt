package com.lfergt.controltienda.feature.catalog

import java.text.Normalizer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    val words = normalizedSearch(query).split(Regex("\\s+"))
    return filter { product -> words.all { normalizedSearch(product.name).contains(it) } || product.barcode?.contains(q, ignoreCase = true) == true || product.qrCode?.contains(q, ignoreCase = true) == true }
}

/** Filtro de categoría en un chip: "Categoría" o el nombre elegido; al tocarlo despliega las opciones. */
@Composable
fun CategoryFilterChip(categories: List<Category>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = categories.firstOrNull { it.id == selected }?.name
    Box {
        FilterChip(
            selected = selected != Category.ALL_ID,
            onClick = { open = true },
            label = { Text(name ?: "Categoría") },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Todas las categorías") }, onClick = { open = false; onSelect(Category.ALL_ID) })
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = { open = false; onSelect(category.id) })
            }
        }
    }
}

fun normalizedSearch(value: String): String = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Detalle rápido de un producto (al tocarlo en "Ver productos"). */
@Composable
fun ProductDetailDialog(
    product: Product,
    currency: String,
    categoryName: String?,
    showStock: Boolean,
    onDismiss: () -> Unit,
    action: Pair<String, () -> Unit>? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp),
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = action?.let { (label, run) -> { TextButton(onClick = run) { Text(label) } } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

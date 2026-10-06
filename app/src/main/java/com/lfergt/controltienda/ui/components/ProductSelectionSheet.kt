package com.lfergt.controltienda.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.feature.catalog.search

/** Selector común: buscar también permite introducir un código sin usar cámara. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSelectionSheet(
    products: List<Product>, currency: String, onDismiss: () -> Unit,
    onPick: (Product) -> Unit, quantities: Map<String, String> = emptyMap(),
    purchase: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Agregar productos", style = MaterialTheme.typography.titleLarge)
            SearchInput(query, { query = it }, "Buscar por nombre o código")
            val visible = products.search(query)
            Text("${visible.size} resultados", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                if (visible.isEmpty()) item {
                    Text("No hay coincidencias. Prueba otro nombre o código.", Modifier.padding(vertical = 24.dp))
                }
                items(visible, key = { it.id }) { product ->
                    ListItem(
                        modifier = Modifier.clickable(onClickLabel = "Agregar ${product.name}") { onPick(product) },
                        leadingContent = { StorageImage(product.photoPath, null, Modifier.size(44.dp)) },
                        headlineContent = { Text(product.name) },
                        supportingContent = {
                            val price = if (purchase) product.purchaseCostCents else product.salePriceCents
                            Text((if (purchase) "Costo: " else "") + (price?.let { Money.format(it, currency) } ?: "Sin costo") + " / ${product.unit.symbol}")
                        },
                        trailingContent = { Text(quantities[product.id]?.let { "$it ${product.unit.symbol}" } ?: "Agregar", color = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { Text("Listo") }
        }
    }
}

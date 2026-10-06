package com.lfergt.controltienda.feature.catalog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.feature.scanner.ScanMode
import com.lfergt.controltienda.feature.scanner.ScannerDialog
import com.lfergt.controltienda.navigation.ManageProductsRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.components.AdaptiveGrid
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.ProductTile
import com.lfergt.controltienda.ui.components.SearchInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ListItem
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.Money
import javax.inject.Inject
import com.lfergt.controltienda.ui.components.StockLabel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.ui.common.plural
import com.lfergt.controltienda.ui.components.AddActions

@HiltViewModel
class ManageProductsViewModel @Inject constructor(savedState: SavedStateHandle, source: CatalogSource) : BaseViewModel() {
    val storeId = savedState.toRoute<ManageProductsRoute>().storeId
    val state = source.observe(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())
}

/** Filtro de stock de la lista de productos. */
enum class StockFilter(val label: String) {
    ALL("Todos"), OUT("Agotados"), LOW("Stock bajo");

    fun matches(product: Product): Boolean {
        val stock = product.stock ?: return this == ALL
        return when (this) {
            ALL -> true
            OUT -> stock <= 0
            LOW -> stock > 0 && product.stockAlert != null && stock <= product.stockAlert!!
        }
    }
}

/**
 * Productos para crear y editar. Al escanear un código: si ya existe abre el producto,
 * si no, abre uno nuevo con el código cargado (y sugiere el nombre).
 */
@Composable
fun ManageProductsScreen(
    onBack: () -> Unit,
    onEdit: (productId: String?, code: String?) -> Unit,
    onCategories: () -> Unit,
    viewModel: ManageProductsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.ALL_ID) }
    var stockFilter by rememberSaveable { mutableStateOf(StockFilter.ALL) }
    var cards by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf<ScanMode?>(null) }
    val allowed = state.header.access.can(Permission.MANAGE_PRODUCTS)

    BackScaffold(
        title = "Productos", subtitle = state.header.name,
        onBack = onBack,
        actions = {
            if (allowed) {
                IconButton(onClick = { cards = !cards }) {
                    Icon(if (cards) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, if (cards) "Ver lista" else "Ver tarjetas")
                }
            }
            if (state.header.access.can(Permission.MANAGE_CATEGORIES)) {
                IconButton(onClick = onCategories) { Icon(Icons.Outlined.Category, "Categorías") }
            }
        },
        floatingActionButton = {
            if (allowed && state.loaded) {
                AddActions(
                    onBarcode = { scanning = ScanMode.BARCODE },
                    onQr = { scanning = ScanMode.QR },
                    onManual = { onEdit(null, null) },
                    manualLabel = "Nuevo producto",
                )
            }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            !allowed -> NoAccess(Modifier.padding(padding))
            else -> {
                val products = state.products.search(query)
                    .filter { (category == Category.ALL_ID || it.categoryId == category) && stockFilter.matches(it) }
                val filtering = query.isNotBlank() || category != Category.ALL_ID || stockFilter != StockFilter.ALL
                AdaptiveGrid(minCellSize = 150.dp, modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchInput(query, { query = it }, "Buscar por nombre o código")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CategoryFilterChip(state.categories, category) { category = it }
                                listOf(StockFilter.OUT, StockFilter.LOW).forEach { filter ->
                                    FilterChip(
                                        selected = stockFilter == filter,
                                        onClick = { stockFilter = if (stockFilter == filter) StockFilter.ALL else filter },
                                        label = { Text(filter.label) },
                                    )
                                }
                            }
                            if (filtering) Text(plural(products.size, "producto", "productos"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (products.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                Icons.Outlined.Inventory2,
                                if (filtering) "Sin resultados" else "Aún no hay productos",
                                if (filtering) null else "Agrega tu primer producto con los botones de abajo.",
                            )
                        }
                    }
                    items(products, key = { it.id }, span = { GridItemSpan(if (cards) 1 else maxLineSpan) }) { product ->
                        if (!cards) Card(onClick = { onEdit(product.id, null) }, modifier = Modifier.fillMaxWidth()) {
                            ListItem(headlineContent = { Text(product.name) }, supportingContent = { StockLabel(product) }, trailingContent = { Text(Money.format(product.salePriceCents, state.header.currency)) })
                        } else ProductTile(
                            product = product,
                            currency = state.header.currency,
                            showStock = true,
                            onClick = { onEdit(product.id, null) },
                        )
                    }
                }
            }
        }
    }

    scanning?.let { mode ->
        ScannerDialog(
            mode = mode,
            onDismiss = { scanning = null },
            onResult = { code ->
                scanning = null
                val existing = state.products.firstOrNull { it.matchesCode(code.value) }
                if (existing != null) onEdit(existing.id, null)
                else onEdit(null, (if (code.isQr) "qr:" else "bar:") + code.value)
            },
        )
    }
}

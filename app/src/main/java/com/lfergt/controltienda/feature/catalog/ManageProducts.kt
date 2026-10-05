package com.lfergt.controltienda.feature.catalog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ViewWeek
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
import com.lfergt.controltienda.ui.components.ExpandableFab
import com.lfergt.controltienda.ui.components.FabAction
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.ProductTile
import com.lfergt.controltienda.ui.components.SearchInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ManageProductsViewModel @Inject constructor(savedState: SavedStateHandle, source: CatalogSource) : BaseViewModel() {
    val storeId = savedState.toRoute<ManageProductsRoute>().storeId
    val state = source.observe(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())
}

/**
 * Lista de productos para crear y editar. Al escanear un código: si ya existe abre el producto,
 * si no, abre uno nuevo con el código cargado (y sugiere el nombre).
 */
@Composable
fun ManageProductsScreen(
    onBack: () -> Unit,
    onEdit: (productId: String?, code: String?) -> Unit,
    viewModel: ManageProductsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf<ScanMode?>(null) }
    val allowed = state.header.access.can(Permission.MANAGE_PRODUCTS)

    BackScaffold(
        title = state.header.name,
        onBack = onBack,
        floatingActionButton = {
            if (allowed) {
                ExpandableFab(
                    icon = Icons.Outlined.Add,
                    actions = listOf(
                        FabAction("Escanear código de barras", Icons.Outlined.ViewWeek) { scanning = ScanMode.BARCODE },
                        FabAction("Escanear QR", Icons.Outlined.QrCodeScanner) { scanning = ScanMode.QR },
                        FabAction("Nuevo producto", Icons.Outlined.Add) { onEdit(null, null) },
                    ),
                )
            }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            !allowed -> NoAccess(Modifier.padding(padding))
            else -> {
                val products = state.products.search(query)
                AdaptiveGrid(minCellSize = 150.dp, modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SearchInput(query, { query = it }, "Buscar por nombre o código") }
                    if (products.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                Icons.Outlined.Inventory2,
                                if (query.isBlank()) "Aún no hay productos" else "Sin resultados",
                                if (query.isBlank()) "Agrega tu primer producto con el botón +." else null,
                            )
                        }
                    }
                    items(products, key = { it.id }) { product ->
                        ProductTile(
                            product = product,
                            currency = state.header.currency,
                            showStock = true,
                            extra = state.categoryName(product.categoryId) ?: "Sin categoría",
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

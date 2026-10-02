package com.lfergt.controltienda.feature.catalog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.usecase.CategoryRules
import com.lfergt.controltienda.navigation.ViewCategoriesRoute
import com.lfergt.controltienda.navigation.ViewProductsRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.components.AdaptiveGrid
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.PhotoTile
import com.lfergt.controltienda.ui.components.ProductTile
import com.lfergt.controltienda.ui.components.SearchInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ------------------------------------------------------------------ categorías (primer nivel)

@HiltViewModel
class ViewCategoriesViewModel @Inject constructor(savedState: SavedStateHandle, source: CatalogSource) : BaseViewModel() {
    val storeId = savedState.toRoute<ViewCategoriesRoute>().storeId
    val state = source.observe(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())
}

/** "Ver productos": primero todas las categorías en cajas grandes (con foto si tienen). */
@Composable
fun ViewCategoriesScreen(onBack: () -> Unit, onCategory: (String) -> Unit, viewModel: ViewCategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackScaffold(title = state.header.name, onBack = onBack) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            !state.header.access.active -> NoAccess(Modifier.padding(padding))
            else -> CategoryGrid(state, Modifier.padding(padding), onCategory)
        }
    }
}

@Composable
fun CategoryGrid(state: CatalogData, modifier: Modifier, onCategory: (String) -> Unit) {
    AdaptiveGrid(minCellSize = 160.dp, modifier = modifier, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        item(key = Category.ALL_ID) {
            PhotoTile(
                title = "Todos",
                subtitle = "${state.products.size} productos",
                photoPath = null,
                placeholderIcon = Icons.Outlined.Apps,
                onClick = { onCategory(Category.ALL_ID) },
            )
        }
        items(state.categories, key = { it.id }) { category ->
            PhotoTile(
                title = category.name,
                subtitle = "${state.countIn(category.id)} productos",
                photoPath = category.photoPath,
                placeholderIcon = Icons.Outlined.Category,
                onClick = { onCategory(category.id) },
            )
        }
    }
}

// ------------------------------------------------------------------ productos de una categoría

@HiltViewModel
class ViewProductsViewModel @Inject constructor(savedState: SavedStateHandle, source: CatalogSource) : BaseViewModel() {
    private val route = savedState.toRoute<ViewProductsRoute>()
    val categoryId = route.categoryId
    val state = source.observe(route.storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())
}

/** Productos en cajas con foto, nombre y precio. El inventario solo con permiso (el dueño siempre). */
@Composable
fun ViewProductsScreen(onBack: () -> Unit, viewModel: ViewProductsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Product?>(null) }
    val categoryName = if (viewModel.categoryId == Category.ALL_ID) "Todos" else state.categoryName(viewModel.categoryId) ?: ""
    val showStock = state.header.access.can(Permission.VIEW_STOCK)

    BackScaffold(title = "Ver productos", subtitle = categoryName, onBack = onBack) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@BackScaffold
        }
        val products = CategoryRules.productsOf(viewModel.categoryId, state.products).search(query)
        AdaptiveGrid(minCellSize = 150.dp, modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { SearchInput(query, { query = it }, "Buscar producto") }
            if (products.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Outlined.Inventory2, "No hay productos", if (query.isBlank()) "Esta categoría aún no tiene productos." else "Prueba con otro nombre.")
                }
            }
            items(products, key = { it.id }) { product ->
                ProductTile(product, state.header.currency, showStock, onClick = { detail = product })
            }
        }
    }
    detail?.let {
        ProductDetailDialog(it, state.header.currency, state.categoryName(it.categoryId), showStock, onDismiss = { detail = null })
    }
}

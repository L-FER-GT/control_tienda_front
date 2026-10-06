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
import androidx.compose.material3.Text
import javax.inject.Inject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.lfergt.controltienda.ui.common.plural

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
    BackScaffold(title = "Categorías", subtitle = state.header.name, onBack = onBack) { padding ->
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
                subtitle = plural(state.products.size, "producto", "productos"),
                photoPath = null,
                placeholderIcon = Icons.Outlined.Apps,
                onClick = { onCategory(Category.ALL_ID) },
            )
        }
        items(state.categories, key = { it.id }) { category ->
            PhotoTile(
                title = category.name,
                subtitle = plural(state.countIn(category.id), "producto", "productos"),
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
fun ViewProductsScreen(onBack: () -> Unit, onCategories: () -> Unit, viewModel: ViewProductsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Product?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf(viewModel.categoryId) }
    val showStock = state.header.access.can(Permission.VIEW_STOCK)

    BackScaffold(
        title = "Productos", subtitle = state.header.name, onBack = onBack,
        actions = {
            if (state.header.access.can(Permission.MANAGE_CATEGORIES)) {
                IconButton(onClick = onCategories) { Icon(Icons.Outlined.Category, "Categorías") }
            }
        },
    ) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@BackScaffold
        }
        if (!state.header.access.active) { NoAccess(Modifier.padding(padding)); return@BackScaffold }
        val products = CategoryRules.productsOf(selectedCategory, state.products).search(query)
        AdaptiveGrid(minCellSize = 150.dp, modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchInput(query, { query = it }, "Buscar por nombre o código")
                    if (state.categories.isNotEmpty()) CategoryFilterChip(state.categories, selectedCategory) { selectedCategory = it }
                    if (query.isNotBlank() || selectedCategory != Category.ALL_ID) {
                        Text(plural(products.size, "producto", "productos"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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

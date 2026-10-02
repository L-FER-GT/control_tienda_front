package com.lfergt.controltienda.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.usecase.CategoryRules
import com.lfergt.controltienda.navigation.CategoriesRoute
import com.lfergt.controltienda.navigation.CategoryDetailRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.components.AdaptiveGrid
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.ConfirmDialog
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.ExpandableFab
import com.lfergt.controltienda.ui.components.FabAction
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.PhotoField
import com.lfergt.controltienda.ui.components.ProductTile
import com.lfergt.controltienda.ui.components.SearchInput
import com.lfergt.controltienda.ui.components.StorageImage
import com.lfergt.controltienda.ui.components.TextInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ------------------------------------------------------------------ lista de categorías

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    source: CatalogSource,
    private val catalog: CatalogRepository,
) : BaseViewModel() {
    val storeId = savedState.toRoute<CategoriesRoute>().storeId
    val state = source.observe(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())

    fun save(categoryId: String?, name: String, photo: String?) = launchSafe {
        if (name.isBlank()) throw com.lfergt.controltienda.domain.error.DomainError.Validation("name", "El nombre es obligatorio")
        catalog.saveCategory(storeId, categoryId, name, photo?.let { LocalFile(it) })
        message(if (categoryId == null) "Categoría creada" else "Categoría actualizada")
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, onCategory: (String) -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val allowed = state.header.access.can(Permission.MANAGE_CATEGORIES)
    CollectMessages(viewModel)

    BackScaffold(
        title = state.header.name,
        onBack = onBack,
        floatingActionButton = {
            if (allowed) FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, "Nueva categoría") }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            !allowed -> NoAccess(Modifier.padding(padding))
            else -> CategoryGrid(state, Modifier.padding(padding), onCategory)
        }
    }
    if (creating) {
        CategoryDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, photo -> creating = false; viewModel.save(null, name, photo) },
        )
    }
}

/** Crear o editar una categoría: nombre y foto. */
@Composable
fun CategoryDialog(initial: Category?, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp),
        title = { Text(if (initial == null) "Nueva categoría" else "Editar categoría") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(name, { name = it }, "Nombre")
                PhotoField(initial?.photoPath, photo, { photo = it.toString() }, label = "Foto (opcional)")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, photo) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ------------------------------------------------------------------ detalle de una categoría

enum class PickerSource { UNCATEGORIZED, ALL }

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    source: CatalogSource,
    private val catalog: CatalogRepository,
) : BaseViewModel() {
    private val route = savedState.toRoute<CategoryDetailRoute>()
    val storeId = route.storeId
    val categoryId = route.categoryId
    val isAll = categoryId == Category.ALL_ID
    val state = source.observe(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())

    private val closeChannel = Channel<Unit>(Channel.CONFLATED)
    val closed = closeChannel.receiveAsFlow()

    fun assign(products: List<Product>) = launchSafe {
        catalog.assignCategory(storeId, products.map { it.id }, categoryId)
        message(if (products.size == 1) "Producto asignado" else "${products.size} productos asignados")
    }

    fun removeFromCategory(product: Product) = launchSafe {
        catalog.assignCategory(storeId, listOf(product.id), null)
        message("\"${product.name}\" quedó sin categoría")
    }

    fun edit(name: String, photo: String?) = launchSafe {
        catalog.saveCategory(storeId, categoryId, name, photo?.let { LocalFile(it) })
        message("Categoría actualizada")
    }

    fun delete() = launchSafe {
        catalog.deleteCategory(storeId, categoryId)
        closeChannel.send(Unit)
    }
}

/**
 * Productos de una categoría. El botón + permite elegir entre "Productos sin categoría" o
 * "Todos los productos" para asignarlos; si alguno ya tiene otra categoría se pide confirmación.
 */
@Composable
fun CategoryDetailScreen(onBack: () -> Unit, viewModel: CategoryDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<PickerSource?>(null) }
    var pendingConflicts by remember { mutableStateOf<Pair<List<Product>, List<Product>>?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Product?>(null) }
    val category = state.categories.firstOrNull { it.id == viewModel.categoryId }
    val allowed = state.header.access.can(Permission.MANAGE_CATEGORIES)
    CollectMessages(viewModel)
    LaunchedEffect(Unit) { viewModel.closed.collect { onBack() } }

    BackScaffold(
        title = "Categorías",
        subtitle = if (viewModel.isAll) "Todos" else category?.name,
        onBack = onBack,
        actions = {
            if (allowed && !viewModel.isAll && category != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Editar categoría") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar categoría") }
            }
        },
        floatingActionButton = {
            if (allowed && !viewModel.isAll) {
                ExpandableFab(
                    icon = Icons.Outlined.Add,
                    actions = listOf(
                        FabAction("Todos los productos", Icons.Outlined.Apps) { picker = PickerSource.ALL },
                        FabAction("Productos sin categoría", Icons.AutoMirrored.Outlined.LabelOff) { picker = PickerSource.UNCATEGORIZED },
                    ),
                )
            }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            !allowed -> NoAccess(Modifier.padding(padding))
            !viewModel.isAll && category == null -> EmptyState(Icons.Outlined.Category, "La categoría ya no existe", modifier = Modifier.padding(padding))
            else -> {
                val products = CategoryRules.productsOf(viewModel.categoryId, state.products)
                AdaptiveGrid(minCellSize = 150.dp, modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp)) {
                    category?.photoPath?.let { path ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StorageImage(
                                path,
                                category.name,
                                Modifier.fillMaxWidth().aspectRatio(3f).clip(MaterialTheme.shapes.large),
                            )
                        }
                    }
                    if (products.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(Icons.Outlined.Inventory2, "Sin productos", "Usa el botón + para agregar productos a esta categoría.")
                        }
                    }
                    items(products, key = { it.id }) { product ->
                        ProductTile(
                            product = product,
                            currency = state.header.currency,
                            showStock = false,
                            extra = if (viewModel.isAll) state.categoryName(product.categoryId) ?: "Sin categoría" else null,
                            onClick = { if (!viewModel.isAll) removing = product },
                        )
                    }
                }
            }
        }
    }

    picker?.let { source ->
        val candidates = when (source) {
            PickerSource.UNCATEGORIZED -> CategoryRules.uncategorized(state.products, state.categories)
            PickerSource.ALL -> state.products.filter { it.categoryId != viewModel.categoryId }
        }
        ProductPickerSheet(
            title = if (source == PickerSource.ALL) "Todos los productos" else "Productos sin categoría",
            candidates = candidates,
            currency = state.header.currency,
            categoryName = { state.categoryName(it) },
            onDismiss = { picker = null },
            onConfirm = { selected ->
                picker = null
                val conflicts = CategoryRules.conflicts(selected, viewModel.categoryId)
                if (conflicts.isEmpty()) viewModel.assign(selected) else pendingConflicts = selected to conflicts
            },
        )
    }

    pendingConflicts?.let { (selected, conflicts) ->
        val message = if (conflicts.size == 1) {
            val p = conflicts.first()
            "\"${p.name}\" ya está en la categoría \"${state.categoryName(p.categoryId)}\". ¿Estás seguro que deseas cambiarlo?"
        } else {
            "${conflicts.size} productos ya están en otra categoría:\n" +
                conflicts.take(6).joinToString("\n") { "• ${it.name} (${state.categoryName(it.categoryId)})" } +
                (if (conflicts.size > 6) "\n…" else "") + "\n¿Estás seguro que deseas cambiarlos?"
        }
        ConfirmDialog(
            title = "Cambiar de categoría",
            message = message,
            confirmText = "Sí, cambiar",
            onConfirm = { viewModel.assign(selected) },
            onDismiss = { pendingConflicts = null },
        )
    }

    removing?.let { product ->
        ConfirmDialog(
            title = "Quitar de la categoría",
            message = "\"${product.name}\" quedará sin categoría (seguirá apareciendo en \"Todos\").",
            confirmText = "Quitar",
            onConfirm = { viewModel.removeFromCategory(product) },
            onDismiss = { removing = null },
        )
    }
    if (editing && category != null) {
        CategoryDialog(category, onDismiss = { editing = false }, onSave = { name, photo -> editing = false; viewModel.edit(name, photo) })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar categoría?",
            message = "Sus productos no se borran: quedarán sin categoría.",
            confirmText = "Eliminar",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPickerSheet(
    title: String,
    candidates: List<Product>,
    currency: String,
    categoryName: (String?) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (List<Product>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            SearchInput(query, { query = it }, "Buscar producto")
            val visible = candidates.search(query)
            Box(Modifier.heightIn(max = 420.dp)) {
                if (visible.isEmpty()) {
                    Text("No hay productos para mostrar.", modifier = Modifier.padding(16.dp))
                }
                LazyColumn {
                    items(visible, key = { it.id }) { product ->
                        val checked = product.id in selected
                        ListItem(
                            leadingContent = { StorageImage(product.photoPath, null, Modifier.size(44.dp)) },
                            headlineContent = { Text(product.name) },
                            supportingContent = {
                                Text(Money.format(product.salePriceCents, currency) + " · " + (categoryName(product.categoryId) ?: "Sin categoría"))
                            },
                            trailingContent = {
                                Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + product.id else selected - product.id })
                            },
                        )
                    }
                }
            }
            Button(
                onClick = { onConfirm(candidates.filter { it.id in selected }) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) { Text(if (selected.isEmpty()) "Selecciona productos" else "Asignar ${selected.size} a esta categoría") }
        }
    }
}

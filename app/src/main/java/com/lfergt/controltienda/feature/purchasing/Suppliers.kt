package com.lfergt.controltienda.feature.purchasing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.Supplier
import com.lfergt.controltienda.domain.port.SupplierRepository
import com.lfergt.controltienda.navigation.SupplierEditorRoute
import com.lfergt.controltienda.navigation.SuppliersRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.StoreContext
import com.lfergt.controltienda.ui.common.StoreHeader
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.ConfirmDialog
import com.lfergt.controltienda.ui.components.FormColumn
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.SearchInput
import com.lfergt.controltienda.ui.components.TextInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuppliersState(val header: StoreHeader = StoreHeader(), val suppliers: List<Supplier> = emptyList())

@HiltViewModel
class SuppliersViewModel @Inject constructor(
    savedState: SavedStateHandle,
    context: StoreContext,
    suppliers: SupplierRepository,
) : BaseViewModel() {
    val storeId = savedState.toRoute<SuppliersRoute>().storeId
    val state = combine(context.header(storeId), suppliers.observeSuppliers(storeId)) { h, list -> SuppliersState(h, list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuppliersState())
}

/** Lista de proveedores. "Otros" siempre existe para compras sin proveedor registrado. */
@Composable
fun SuppliersScreen(onBack: () -> Unit, onEdit: (String?) -> Unit, viewModel: SuppliersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val allowed = state.header.access.can(Permission.SUPPLIERS)
    BackScaffold(
        title = state.header.name,
        onBack = onBack,
        floatingActionButton = {
            if (allowed) ExtendedFloatingActionButton(onClick = { onEdit(null) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Nuevo proveedor") })
        },
    ) { padding ->
        when {
            !state.header.loaded -> LoadingBox(Modifier.padding(padding))
            !allowed -> NoAccess(Modifier.padding(padding))
            else -> {
                val q = query.trim().lowercase()
                val list = state.suppliers.filter {
                    q.isEmpty() || it.companyName.lowercase().contains(q) || it.ruc?.contains(q) == true || it.contactName?.lowercase()?.contains(q) == true
                }
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item { SearchInput(query, { query = it }, "Buscar por empresa, RUC o asesor", Modifier.widthIn(max = 720.dp)) }
                    items(list, key = { it.id }) { supplier ->
                        Card(
                            onClick = { if (!supplier.isOthers) onEdit(supplier.id) },
                            enabled = !supplier.isOthers,
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { Icon(Icons.Outlined.LocalShipping, null) },
                                headlineContent = { Text(supplier.companyName) },
                                supportingContent = {
                                    Text(
                                        if (supplier.isOthers) "Siempre disponible para compras sin proveedor registrado"
                                        else listOfNotNull(
                                            supplier.ruc?.let { "RUC $it" },
                                            supplier.contactName?.let { "Asesor: $it" },
                                            supplier.phone,
                                        ).joinToString(" · ").ifEmpty { "Sin datos adicionales" },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ editor

data class SupplierForm(
    val loaded: Boolean = false,
    val companyName: String = "",
    val ruc: String = "",
    val phone: String = "",
    val contactName: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val errors: Map<String, String> = emptyMap(),
)

@HiltViewModel
class SupplierEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    context: StoreContext,
    private val suppliers: SupplierRepository,
) : BaseViewModel() {
    private val route = savedState.toRoute<SupplierEditorRoute>()
    val storeId = route.storeId
    val supplierId = route.supplierId
    val header = context.header(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreHeader())
    private val _form = MutableStateFlow(SupplierForm(loaded = supplierId == null))
    val form = _form.asStateFlow()
    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done = doneChannel.receiveAsFlow()

    init {
        if (supplierId != null) viewModelScope.launch {
            val s = suppliers.observeSuppliers(storeId).first().firstOrNull { it.id == supplierId }
            _form.value = SupplierForm(
                loaded = true,
                companyName = s?.companyName ?: "",
                ruc = s?.ruc ?: "",
                phone = s?.phone ?: "",
                contactName = s?.contactName ?: "",
                email = s?.email ?: "",
                address = s?.address ?: "",
                notes = s?.notes ?: "",
            )
        }
    }

    fun update(block: (SupplierForm) -> SupplierForm) = _form.update { block(it).copy(errors = emptyMap()) }

    fun save() {
        val f = _form.value
        val errors = buildMap {
            if (f.companyName.isBlank()) put("company", "La empresa es obligatoria")
            if (f.ruc.isNotBlank() && !Supplier.isValidRuc(f.ruc)) put("ruc", "El RUC debe tener 11 dígitos (10, 15, 16, 17 o 20…)")
            if (f.email.isNotBlank() && !f.email.contains("@")) put("email", "Correo no válido")
        }
        if (errors.isNotEmpty()) {
            _form.update { it.copy(errors = errors) }
            return
        }
        launchSafe {
            suppliers.saveSupplier(
                storeId,
                Supplier(supplierId ?: "", f.companyName, f.ruc, f.phone, f.contactName, f.email, f.address, f.notes),
            )
            doneChannel.send(Unit)
        }
    }

    fun delete() = launchSafe {
        supplierId?.let { suppliers.deleteSupplier(storeId, it) }
        doneChannel.send(Unit)
    }
}

@Composable
fun SupplierEditorScreen(onBack: () -> Unit, viewModel: SupplierEditorViewModel = hiltViewModel()) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    CollectMessages(viewModel)
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }

    BackScaffold(
        title = "Proveedores",
        subtitle = if (viewModel.supplierId == null) "Nuevo proveedor" else form.companyName,
        onBack = onBack,
        actions = {
            if (viewModel.supplierId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar") }
        },
    ) { padding ->
        when {
            !form.loaded || !header.loaded -> LoadingBox(Modifier.padding(padding))
            !header.access.can(Permission.SUPPLIERS) -> NoAccess(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                FormColumn {
                    TextInput(form.companyName, { v -> viewModel.update { it.copy(companyName = v) } }, "Empresa / razón social *", error = form.errors["company"])
                    TextInput(
                        form.ruc, { v -> viewModel.update { it.copy(ruc = v.filter(Char::isDigit).take(11)) } }, "RUC",
                        error = form.errors["ruc"], keyboardType = KeyboardType.Number,
                    )
                    TextInput(form.contactName, { v -> viewModel.update { it.copy(contactName = v) } }, "Nombre del asesor / vendedor")
                    TextInput(form.phone, { v -> viewModel.update { it.copy(phone = v) } }, "Teléfono", keyboardType = KeyboardType.Phone)
                    TextInput(form.email, { v -> viewModel.update { it.copy(email = v) } }, "Correo", error = form.errors["email"], keyboardType = KeyboardType.Email)
                    TextInput(form.address, { v -> viewModel.update { it.copy(address = v) } }, "Dirección", singleLine = false)
                    TextInput(form.notes, { v -> viewModel.update { it.copy(notes = v) } }, "Notas (días de visita, condiciones, etc.)", singleLine = false)
                    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar proveedor?",
            message = "Las recepciones registradas con este proveedor se conservan.",
            confirmText = "Eliminar",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

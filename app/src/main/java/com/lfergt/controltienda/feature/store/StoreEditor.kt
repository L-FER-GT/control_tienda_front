package com.lfergt.controltienda.feature.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lfergt.controltienda.ui.components.rememberGuardedBack
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.StoreDraft
import com.lfergt.controltienda.domain.port.StoreRepository
import com.lfergt.controltienda.domain.usecase.SaveStoreUseCase
import com.lfergt.controltienda.navigation.StoreEditorRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.Dropdown
import com.lfergt.controltienda.ui.components.FormColumn
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.PhotoField
import com.lfergt.controltienda.ui.components.TextInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.lfergt.controltienda.ui.components.UnavailableState

data class StoreEditorState(
    val unavailable: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val name: String = "",
    val address: String = "",
    val isPublic: Boolean = false,
    val currency: String = Money.DEFAULT_CURRENCY,
    val photoPath: String? = null,
    val pickedPhoto: String? = null,
    val errors: Map<String, String> = emptyMap(),
) : java.io.Serializable

@HiltViewModel
class StoreEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val stores: StoreRepository,
    private val saveStore: SaveStoreUseCase,
) : BaseViewModel() {

    val storeId: String? = savedState.toRoute<StoreEditorRoute>().storeId
    private val _state = MutableStateFlow(savedState.get<StoreEditorState>("editorDraft")?.copy(saving = false) ?: StoreEditorState(loading = storeId != null))
    val state = _state.asStateFlow()
    private var baseline: StoreEditorState? = savedState["editorBaseline"]
    val dirty: Boolean get() = baseline?.let { _state.value.copy(saving = false, errors = emptyMap()) != it.copy(saving = false, errors = emptyMap()) } ?: false
    private fun rememberBaseline() {
        if (baseline == null) { baseline = _state.value; savedState["editorBaseline"] = baseline }
    }


    private val doneChannel = Channel<String>(Channel.CONFLATED)
    val done = doneChannel.receiveAsFlow()

    init {
        viewModelScope.launch { _state.collect { savedState["editorDraft"] = it.copy(saving = false) } }
        if (storeId != null && _state.value.loading) viewModelScope.launch {
            val store = stores.observeStore(storeId).first() ?: run {
                _state.update { it.copy(loading = false, unavailable = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    name = store.name,
                    address = store.address,
                    isPublic = store.isPublic,
                    currency = store.currency,
                    photoPath = store.photoPath,
                )
            }
            rememberBaseline()
        } else rememberBaseline()
    }

    fun onName(v: String) = _state.update { it.copy(name = v, errors = it.errors - "name") }
    fun onAddress(v: String) = _state.update { it.copy(address = v, errors = it.errors - "address") }
    fun onPublic(v: Boolean) = _state.update { it.copy(isPublic = v) }
    fun onCurrency(v: String) = _state.update { it.copy(currency = v) }
    fun onPhoto(uri: String) = _state.update { it.copy(pickedPhoto = uri) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id = saveStore(
                    storeId,
                    StoreDraft(s.name, s.address, s.isPublic, s.currency),
                    s.pickedPhoto?.let { LocalFile(it) },
                )
                doneChannel.send(id)
            } catch (e: DomainError.Validation) {
                _state.update { it.copy(errors = mapOf((e.field ?: "name") to e.userMessage())) }
            } catch (e: Exception) {
                message(e.userMessage())
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

@Composable
fun StoreEditorScreen(onBack: () -> Unit, onSaved: (String, Boolean) -> Unit, viewModel: StoreEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val creating = viewModel.storeId == null
    CollectMessages(viewModel)
    LaunchedEffect(Unit) { viewModel.done.collect { onSaved(it, creating) } }

    BackScaffold(title = if (creating) "Nueva tienda" else "Editar tienda", onBack = rememberGuardedBack(viewModel.dirty, state.saving, onBack)) { padding ->
        if (state.unavailable) {
            UnavailableState(onBack, Modifier.padding(padding))
            return@BackScaffold
        }
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@BackScaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            FormColumn {
                PhotoField(
                    currentPath = state.photoPath,
                    pickedUri = state.pickedPhoto,
                    onPicked = { viewModel.onPhoto(it.toString()) },
                    label = "Foto de la tienda",
                )
                TextInput(state.name, viewModel::onName, "Nombre de la tienda *", error = state.errors["name"])
                TextInput(
                    state.address,
                    viewModel::onAddress,
                    "Dirección *",
                    error = state.errors["address"],
                    singleLine = false,
                    supporting = "Ej.: Av. Los Olivos 123, San Martín de Porres, Lima",
                )
                Dropdown(
                    label = "Moneda",
                    options = Money.SUPPORTED_CURRENCIES.keys.toList(),
                    selected = state.currency,
                    optionLabel = { "${Money.currencySymbol(it)} ($it)" },
                    onSelected = viewModel::onCurrency,
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (state.isPublic) "Tienda pública" else "Tienda privada", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (state.isPublic) "Cualquier usuario puede verla en la lista y ver sus productos y precios."
                                else "Solo la ven las personas que invites con su código.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.isPublic, onCheckedChange = viewModel::onPublic, modifier = Modifier.semantics { contentDescription = "Tienda pública" })
                    }
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
                    else Text(if (creating) "Crear tienda" else "Guardar cambios")
                }
            }
        }
    }
}

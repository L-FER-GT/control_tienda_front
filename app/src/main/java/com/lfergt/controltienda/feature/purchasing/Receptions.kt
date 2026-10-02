package com.lfergt.controltienda.feature.purchasing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.Reception
import com.lfergt.controltienda.domain.port.ReceptionRepository
import com.lfergt.controltienda.navigation.ReceptionsRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.StoreContext
import com.lfergt.controltienda.ui.common.StoreHeader
import com.lfergt.controltienda.ui.common.formatDate
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.theme.StatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReceptionsState(val header: StoreHeader = StoreHeader(), val receptions: List<Reception> = emptyList())

@HiltViewModel
class ReceptionsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    context: StoreContext,
    receptions: ReceptionRepository,
) : BaseViewModel() {
    val storeId = savedState.toRoute<ReceptionsRoute>().storeId
    val state = combine(context.header(storeId), receptions.observeReceptions(storeId)) { h, list -> ReceptionsState(h, list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceptionsState())
}

/** Historial de recepciones de mercadería. Tocar una la abre para editarla. */
@Composable
fun ReceptionsScreen(onBack: () -> Unit, onEdit: (String?) -> Unit, viewModel: ReceptionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allowed = state.header.access.can(Permission.RECEPTIONS)
    BackScaffold(
        title = state.header.name,
        onBack = onBack,
        floatingActionButton = {
            if (allowed) ExtendedFloatingActionButton(onClick = { onEdit(null) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Registrar recepción") })
        },
    ) { padding ->
        when {
            !state.header.loaded -> LoadingBox(Modifier.padding(padding))
            !allowed -> NoAccess(Modifier.padding(padding))
            state.receptions.isEmpty() -> EmptyState(
                Icons.Outlined.MoveToInbox,
                "Sin recepciones",
                "Registra la mercadería que llega: suma stock y guarda el costo de compra.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(state.receptions, key = { it.id }) { r ->
                    Card(
                        onClick = { onEdit(r.id) },
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(r.supplierName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${formatDate(r.receivedAt)} · ${r.lines.size} productos · por ${r.createdByName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (r.invoicePhotos.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.outline)
                                        Text("${r.invoicePhotos.size} foto(s) de factura", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (r.pendingSync) Text("Pendiente de sincronizar", style = MaterialTheme.typography.labelSmall, color = StatusColors.warning)
                            }
                            r.invoiceTotalCents?.let {
                                Text(Money.format(it, state.header.currency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

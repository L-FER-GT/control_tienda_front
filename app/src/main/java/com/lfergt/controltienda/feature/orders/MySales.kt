package com.lfergt.controltienda.feature.orders

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
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.domain.port.OrderRepository
import com.lfergt.controltienda.navigation.MySalesRoute
import com.lfergt.controltienda.navigation.SaleDetailRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.StoreContext
import com.lfergt.controltienda.ui.common.StoreHeader
import com.lfergt.controltienda.ui.common.formatWhen
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.theme.StatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.lfergt.controltienda.ui.components.HistoryPeriod
import com.lfergt.controltienda.ui.components.HistoryFilters
import com.lfergt.controltienda.feature.catalog.normalizedSearch
import javax.inject.Inject
import com.lfergt.controltienda.ui.components.UnavailableState
import com.lfergt.controltienda.ui.common.plural

data class MySalesState(
    val header: StoreHeader = StoreHeader(),
    val orders: List<Order> = emptyList(),
    val loaded: Boolean = false,
)

/** "Venta N° 123", o "Venta por sincronizar" mientras el servidor no le asigna número. */
val Order.title: String get() = number?.let { "Venta " + Order.formatNumber(it) } ?: "Venta por sincronizar"

@HiltViewModel
class MySalesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    context: StoreContext,
    orders: OrderRepository,
) : BaseViewModel() {
    val storeId = savedState.toRoute<MySalesRoute>().storeId
    val state = combine(
        context.header(storeId),
        context.uid?.let { orders.observeMyOrders(storeId, it) } ?: flowOf(emptyList()),
    ) { header, list -> MySalesState(header, list, header.loaded) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MySalesState())
}

/** Ventas del usuario: ID de orden, fecha con hora, monto y botón para ver el detalle. */
@Composable
fun MySalesScreen(onBack: () -> Unit, onDetail: (String) -> Unit, viewModel: MySalesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.ALL) }
    val currency = state.header.currency
    val visible = state.orders.filter { order -> period.includes(order.createdAt) && normalizedSearch(order.title + " " + order.items.joinToString { it.description }).contains(normalizedSearch(query)) }
    BackScaffold(title = "Mis ventas", subtitle = state.header.name, onBack = onBack) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            !state.header.access.isStaff -> NoAccess(Modifier.padding(padding))
            state.orders.isEmpty() -> EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "Aún no tienes ventas", "Tus ventas aparecerán aquí.", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Card(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Vendido ${period.summary}", style = MaterialTheme.typography.labelLarge)
                                Text(plural(visible.size, "venta", "ventas"), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(Money.format(visible.sumOf { it.totalCents }, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { HistoryFilters(query, { query = it }, period, { period = it }, visible.size) }
                if (visible.isEmpty()) item { Text("Sin ventas para estos filtros.") }
                items(visible, key = { it.id }) { order ->
                    Card(
                        onClick = { onDetail(order.id) },
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(order.title, style = MaterialTheme.typography.titleSmall)
                                Text(formatWhen(order.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (order.pendingSync) PendingSyncLabel()
                            }
                            Text(Money.format(order.totalCents, order.currency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSyncLabel() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.CloudUpload, null, tint = StatusColors.warning, modifier = Modifier.padding(0.dp))
        Text("Pendiente de sincronizar", style = MaterialTheme.typography.labelSmall, color = StatusColors.warning)
    }
}

// ------------------------------------------------------------------ detalle

@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    orders: OrderRepository,
) : BaseViewModel() {
    private val route = savedState.toRoute<SaleDetailRoute>()
    val state = orders.observeOrder(route.storeId, route.orderId)
        .map { true to it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false to null)
}

/** Detalle: arriba "Mis ventas"; al centro el ID de la orden, la fecha y hora, el detalle y el total. */
@Composable
fun SaleDetailScreen(onBack: () -> Unit, viewModel: SaleDetailViewModel = hiltViewModel()) {
    val order by viewModel.state.collectAsStateWithLifecycle()
    BackScaffold(title = order.second?.title ?: "Venta", onBack = onBack) { padding ->
        val o = order.second
        if (!order.first) {
            LoadingBox(Modifier.padding(padding))
            return@BackScaffold
        }
        if (o == null) {
            UnavailableState(onBack, Modifier.padding(padding))
            return@BackScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(formatWhen(o.createdAt), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (o.pendingSync) PendingSyncLabel()
                }
            }
            item {
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Text("Cant.", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                    Text("Detalle", Modifier.weight(2.4f), style = MaterialTheme.typography.labelLarge)
                    Text("P. unit.", Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
                    Text("Total", Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
                }
                HorizontalDivider(Modifier.widthIn(max = 720.dp).padding(top = 6.dp))
            }
            items(o.items) { item ->
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${item.unit.formatQuantity(item.quantity)} ${item.unit.symbol}", Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.weight(2.4f)) {
                        Text(item.description, style = MaterialTheme.typography.bodyMedium)
                        if (item.manual) Text("Agregado a mano", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(item.unitPriceCents, o.currency), Modifier.weight(1.2f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(item.subtotalCents, o.currency), Modifier.weight(1.2f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                HorizontalDivider(Modifier.widthIn(max = 720.dp))
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pago: ${o.paymentMethod.label}", style = MaterialTheme.typography.bodyMedium)
                        Text("Vendedor: ${o.createdByName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total", style = MaterialTheme.typography.labelLarge)
                        Text(Money.format(o.totalCents, o.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

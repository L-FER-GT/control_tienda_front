package com.lfergt.controltienda.feature.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.usecase.StockAlerts
import com.lfergt.controltienda.feature.catalog.CatalogData
import com.lfergt.controltienda.feature.catalog.CatalogSource
import com.lfergt.controltienda.navigation.StockAlertsRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.StorageImage
import com.lfergt.controltienda.ui.theme.StatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StockAlertsViewModel @Inject constructor(savedState: SavedStateHandle, source: CatalogSource) : BaseViewModel() {
    val storeId = savedState.toRoute<StockAlertsRoute>().storeId
    val state = source.observe(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())
}

/**
 * Solo aparecen productos con stock marcado (vacío = ilimitado). Siempre se alertan los que
 * están en 0 o negativo; los positivos, solo si tienen "alerta de stock" y la alcanzaron.
 */
@Composable
fun StockAlertsScreen(onBack: () -> Unit, onProduct: (String) -> Unit, viewModel: StockAlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canEdit = state.header.access.can(Permission.MANAGE_PRODUCTS)
    BackScaffold(title = state.header.name, subtitle = "Alertas de stock mínimo", onBack = onBack) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            !state.header.access.can(Permission.STOCK_ALERTS) -> NoAccess(Modifier.padding(padding))
            else -> {
                val alerts = StockAlerts.compute(state.products)
                if (alerts.total == 0) {
                    EmptyState(Icons.Outlined.CheckCircle, "Todo en orden", "Ningún producto está sin stock ni por debajo de su mínimo.", Modifier.padding(padding))
                    return@BackScaffold
                }
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (alerts.outOfStock.isNotEmpty()) {
                        item { SectionTitle("Sin stock o negativo (${alerts.outOfStock.size})", StatusColors.danger) }
                        items(alerts.outOfStock, key = { "o" + it.id }) { AlertRow(it, StatusColors.danger, canEdit, onProduct) }
                    }
                    if (alerts.belowMinimum.isNotEmpty()) {
                        item { SectionTitle("Bajo el mínimo (${alerts.belowMinimum.size})", StatusColors.warning) }
                        items(alerts.belowMinimum, key = { "b" + it.id }) { AlertRow(it, StatusColors.warning, canEdit, onProduct) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 8.dp))
}

@Composable
private fun AlertRow(product: Product, color: Color, canEdit: Boolean, onProduct: (String) -> Unit) {
    Card(
        onClick = { if (canEdit) onProduct(product.id) },
        enabled = canEdit,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StorageImage(product.photoPath, null, Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall)
                product.stockAlert?.let {
                    Text("Mínimo: ${product.unit.formatQuantity(it)} ${product.unit.symbol}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(product.unit.formatQuantity(product.stock ?: 0.0), style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
                Text(product.unit.symbol, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

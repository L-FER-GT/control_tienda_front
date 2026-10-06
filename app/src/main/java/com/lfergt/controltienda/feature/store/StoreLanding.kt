package com.lfergt.controltienda.feature.store

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.StoreOption
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.usecase.StockAlerts
import com.lfergt.controltienda.navigation.StoreLandingRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.StoreContext
import com.lfergt.controltienda.ui.common.StoreHeader
import com.lfergt.controltienda.ui.common.icon
import com.lfergt.controltienda.ui.common.label
import com.lfergt.controltienda.ui.components.AdaptiveGrid
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.LocalOffline
import com.lfergt.controltienda.ui.components.LocalSnackbar
import com.lfergt.controltienda.ui.components.OptionTile
import com.lfergt.controltienda.ui.components.StorageImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.grid.GridItemSpan
import javax.inject.Inject
import com.lfergt.controltienda.ui.components.OfflineIcon

data class LandingState(
    val header: StoreHeader = StoreHeader(),
    val options: List<StoreOption> = emptyList(),
    val stockAlerts: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoreLandingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    context: StoreContext,
    catalog: CatalogRepository,
) : BaseViewModel() {

    val storeId = savedState.toRoute<StoreLandingRoute>().storeId
    private val header = context.header(storeId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val alertCount = header.map { it.access.can(Permission.STOCK_ALERTS) }.distinctUntilChanged().flatMapLatest { allowed ->
        if (!allowed) flowOf(0) else catalog.observeProducts(storeId).map { StockAlerts.compute(it).total }
    }

    val state = combine(header, alertCount) { h, alerts ->
        LandingState(h, h.access.availableOptions(), alerts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LandingState())

    init {
        // "Entrar a la sala" de la tienda: mientras el usuario esté dentro de la tienda, el catálogo
        // se mantiene sincronizado en tiempo real (y la base local se actualiza al entrar).
        viewModelScope.launch {
            header.map { it.access.active }.distinctUntilChanged().flatMapLatest { active ->
                if (!active) flowOf(Unit)
                else combine(catalog.observeProducts(storeId), catalog.observeCategories(storeId)) { _, _ -> }
            }.collect {}
        }
    }
}

@Composable
fun StoreLandingScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOption: (StoreOption) -> Unit,
    viewModel: StoreLandingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val header = state.header
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500

    Scaffold(snackbarHost = { SnackbarHost(LocalSnackbar.current, Modifier.imePadding()) }, contentWindowInsets = WindowInsets(0)) { padding ->
        if (!header.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val canEdit = header.access.can(Permission.EDIT_STORE)
        val body: @Composable (Modifier) -> Unit = { modifier ->
            when {
                header.store == null -> EmptyState(Icons.Outlined.Storefront, "Tienda no disponible", "Es posible que la hayan eliminado.", modifier)
                header.store.disabledBySystem -> EmptyState(
                    Icons.Outlined.Block,
                    "Tienda deshabilitada",
                    "El administrador del sistema deshabilitó esta tienda.",
                    modifier,
                )
                !header.access.active -> EmptyState(
                    Icons.Outlined.Lock,
                    "Sin acceso",
                    "Esta tienda es privada. Pide al administrador que te invite con tu código.",
                    modifier,
                )
                else -> AdaptiveGrid(
                    minCellSize = 150.dp,
                    modifier = modifier,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    if (header.access.isStaff) item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(onClick = { onOption(StoreOption.CREATE_ORDER) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Nueva venta", modifier = Modifier.padding(8.dp))
                        }
                    }
                    items(state.options.filter { !header.access.isStaff || it != StoreOption.CREATE_ORDER }) { option ->
                        OptionTile(
                            label = option.label,
                            icon = option.icon,
                            badgeCount = if (option == StoreOption.STOCK_ALERTS) state.stockAlerts else 0,
                            onClick = { onOption(option) },
                        )
                    }
                }
            }
        }

        if (landscape && compactHeight) {
            // Celular en horizontal: la cabecera queda anclada a la izquierda y las opciones a la derecha.
            Row(Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    StorePhotoHeader(header, onBack, if (canEdit) onEdit else null, Modifier.weight(1f).fillMaxWidth(), topInset = false)
                    StoreTitle(header)
                }
                body(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Foto, nombre y dirección anclados arriba; solo las opciones se desplazan.
                StorePhotoHeader(header, onBack, if (canEdit) onEdit else null, Modifier.fillMaxWidth().height(if (header.access.isStaff) 120.dp else if (landscape) 180.dp else 220.dp), topInset = true)
                StoreTitle(header)
                body(Modifier.weight(1f).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun StorePhotoHeader(header: StoreHeader, onBack: () -> Unit, onEdit: (() -> Unit)?, modifier: Modifier, topInset: Boolean) {
    Box(modifier) {
        StorageImage(
            header.store?.photoPath,
            contentDescription = header.name,
            modifier = Modifier.fillMaxSize(),
            placeholderIcon = Icons.Outlined.Storefront,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
        )
        val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.35f),
            contentColor = Color.White,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (topInset) Modifier.statusBarsPadding() else Modifier)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalIconButton(onClick = onBack, colors = buttonColors, shape = CircleShape) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Row {
                OfflineIcon()
                if (onEdit != null) {
                    FilledTonalIconButton(onClick = onEdit, colors = buttonColors, shape = CircleShape) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar tienda")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreTitle(header: StoreHeader) {
    val store = header.store ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(store.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(
                store.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (store.isPublic) Icons.Outlined.Public else Icons.Outlined.Lock,
                contentDescription = if (store.isPublic) "Pública" else "Privada",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

package com.lfergt.controltienda.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lfergt.controltienda.domain.model.StoreListFilter
import com.lfergt.controltienda.domain.model.UserCode
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.label
import com.lfergt.controltienda.ui.components.AdaptiveGrid
import com.lfergt.controltienda.ui.components.Avatar
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.LocalSnackbar
import com.lfergt.controltienda.ui.components.OfflineIcon
import com.lfergt.controltienda.ui.components.PhotoTile
import com.lfergt.controltienda.ui.components.SearchInput

/**
 * Lista de tiendas en cajas (la foto ocupa todo y el nombre va abajo).
 * Arriba a la derecha: notificaciones y avatar (solo en esta pantalla, no dentro de las tiendas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenStore: (String) -> Unit,
    onCreateStore: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onMaster: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    CollectMessages(viewModel)
    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiendas") },
                actions = {
                    OfflineIcon()
                    IconButton(onClick = onNotifications) {
                        BadgedBox(badge = { if (state.unread > 0) Badge { Text(if (state.unread > 99) "99+" else "${state.unread}") } }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Avatar(state.me?.displayName ?: "", state.me?.photoPath, size = 34.dp)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            state.me?.let { me ->
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(me.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text("Código: ${UserCode.pretty(me.code)}", style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text("Configuración") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menuOpen = false; onSettings() },
                            )
                            if (state.isSuperadmin) {
                                DropdownMenuItem(
                                    text = { Text("Opciones maestras") },
                                    leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, null) },
                                    onClick = { menuOpen = false; onMaster() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Cerrar sesión") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                onClick = { menuOpen = false; viewModel.signOut() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateStore,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Nueva tienda") },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        AdaptiveGrid(
            minCellSize = 165.dp,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchInput(state.query, viewModel::onQuery, "Buscar tienda o dirección")
                    if (state.filters.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.filters) { filter ->
                                FilterChip(
                                    selected = filter == state.selectedFilter,
                                    onClick = { viewModel.selectFilter(filter) },
                                    label = { Text(filter.label(state.ownedCount)) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Outlined.Storefront,
                        title = if (state.query.isNotBlank()) "Sin resultados" else "Aún no hay tiendas",
                        message = when {
                            state.query.isNotBlank() -> "Prueba con otro nombre."
                            state.selectedFilter == StoreListFilter.ALL ->
                                "Crea tu tienda con el botón \"Nueva tienda\" o comparte tu código para que te inviten."
                            else -> null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            items(state.items, key = { it.store.id }) { item ->
                PhotoTile(
                    title = item.store.name,
                    subtitle = item.store.address,
                    photoPath = item.store.photoPath,
                    tag = if (item.store.disabledBySystem) "Deshabilitada" else item.relation.label,
                    dimmed = item.store.disabledBySystem,
                    placeholderIcon = Icons.Outlined.Storefront,
                    onClick = { onOpenStore(item.store.id) },
                )
            }
        }
    }
}

/** Android 13+: pide permiso para mostrar las notificaciones push (invitaciones, alertas). */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

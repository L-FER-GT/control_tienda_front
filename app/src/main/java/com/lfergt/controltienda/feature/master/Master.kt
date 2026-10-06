package com.lfergt.controltienda.feature.master

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.UsageLevel
import com.lfergt.controltienda.domain.model.UsageMetric
import com.lfergt.controltienda.domain.model.UsagePeriod
import com.lfergt.controltienda.domain.model.UsageReport
import com.lfergt.controltienda.domain.model.UserCode
import com.lfergt.controltienda.domain.port.AdminRepository
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.formatDateTime
import com.lfergt.controltienda.ui.components.Avatar
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.SearchInput
import com.lfergt.controltienda.ui.theme.StatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.lfergt.controltienda.domain.error.userMessage
import javax.inject.Inject

data class MasterState(
    val usage: UsageReport? = null,
    val loadingUsage: Boolean = false,
    val userSearching: Boolean = false,
    val storeSearching: Boolean = false,
    val userQuery: String = "",
    val users: List<PublicProfile> = emptyList(),
    val storeQuery: String = "",
    val stores: List<Store> = emptyList(),
)

@HiltViewModel
class MasterViewModel @Inject constructor(
    private val admin: AdminRepository,
    auth: AuthRepository,
) : BaseViewModel() {

    val allowed = auth.currentSession()?.isSuperadmin == true
    private val _state = MutableStateFlow(MasterState())
    val state = _state.asStateFlow()

    init {
        if (allowed) refreshUsage()
    }

    fun refreshUsage() {
        _state.update { it.copy(loadingUsage = true) }
        launchSafe(onError = { _state.update { it.copy(loadingUsage = false) } }) {
            val report = admin.getUsage()
            _state.update { it.copy(usage = report, loadingUsage = false) }
        }
    }

    private var userSearch: Job? = null
    private var storeSearch: Job? = null
    fun onUserQuery(q: String) {
        userSearch?.cancel()
        _state.update { it.copy(userQuery = q, users = emptyList(), userSearching = q.trim().length >= 2) }
        if (q.trim().length < 2) return
        userSearch = viewModelScope.launch {
            try { delay(350); val result = admin.searchUsers(q); if (_state.value.userQuery == q) _state.update { it.copy(users = result) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.userMessage()) }
            finally { if (_state.value.userQuery == q) _state.update { it.copy(userSearching = false) } }
        }
    }
    fun onStoreQuery(q: String) {
        storeSearch?.cancel()
        _state.update { it.copy(storeQuery = q, stores = emptyList(), storeSearching = q.isNotBlank()) }
        if (q.isBlank()) return
        storeSearch = viewModelScope.launch {
            try { delay(350); val result = admin.searchStores(q); if (_state.value.storeQuery == q) _state.update { it.copy(stores = result) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.userMessage()) }
            finally { if (_state.value.storeQuery == q) _state.update { it.copy(storeSearching = false) } }
        }
    }

    fun setUserDisabled(user: PublicProfile, disabled: Boolean) = launchSafe {
        admin.setUserDisabled(user.uid, disabled)
        _state.update { s -> s.copy(users = s.users.map { if (it.uid == user.uid) it.copy(disabled = disabled) else it }) }
        message(if (disabled) "${user.displayName} fue deshabilitado" else "${user.displayName} fue habilitado")
    }

    fun setStoreDisabled(store: Store, disabled: Boolean) = launchSafe {
        admin.setStoreDisabled(store.id, disabled)
        _state.update { s -> s.copy(stores = s.stores.map { if (it.id == store.id) it.copy(disabledBySystem = disabled) else it }) }
        message(if (disabled) "Tienda deshabilitada" else "Tienda habilitada")
    }
}

/** Opciones maestras: solo para el creador de la app (superadmin). */
@Composable
fun MasterScreen(onBack: () -> Unit, viewModel: MasterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    CollectMessages(viewModel)

    BackScaffold(
        title = "Opciones maestras",
        onBack = onBack,
        actions = { if (tab == 0) IconButton(onClick = viewModel::refreshUsage) { Icon(Icons.Outlined.Refresh, "Actualizar") } },
    ) { padding ->
        if (!viewModel.allowed) {
            EmptyState(Icons.Outlined.Info, "Solo para el superadministrador", modifier = Modifier.padding(padding))
            return@BackScaffold
        }
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Consumo") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Usuarios") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Tiendas") })
            }
            when (tab) {
                0 -> UsageTab(state)
                1 -> UsersTab(state, viewModel::onUserQuery, viewModel::setUserDisabled)
                else -> StoresTab(state, viewModel::onStoreQuery, viewModel::setStoreDisabled)
            }
        }
    }
}

@Composable
private fun UsageTab(state: MasterState) {
    val report = state.usage
    if (report == null) {
        if (state.loadingUsage) LoadingBox() else EmptyState(Icons.Outlined.Info, "Sin datos de consumo", "Toca actualizar para consultar.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Almacenamiento de Supabase Free", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Estimación de base de datos y archivos. Consulta tráfico y demás cuotas en el panel de Supabase. Actualizado: ${formatDateTime(report.generatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.source == "emulator") {
                    Text(
                        "Datos de ejemplo: estás usando el emulador de Supabase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusColors.warning,
                    )
                }
            }
        }
        items(report.metrics, key = { it.key }) { UsageCard(it) }
    }
}

private fun formatAmount(value: Double, unit: String): String =
    if (unit == "bytes") {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = value
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        String.format(Locale.US, if (i == 0) "%.0f %s" else "%.2f %s", v, units[i])
    } else {
        String.format(Locale.US, "%,.0f %s", value, unit)
    }

@Composable
private fun UsageCard(metric: UsageMetric) {
    val color = when (metric.level) {
        UsageLevel.OK -> StatusColors.ok
        UsageLevel.WARNING -> StatusColors.warning
        UsageLevel.EXCEEDED -> StatusColors.danger
    }
    val period = when (metric.period) {
        UsagePeriod.DAY -> "Hoy (se reinicia a medianoche del Pacífico)"
        UsagePeriod.MONTH -> "Este mes"
        UsagePeriod.TOTAL -> "Acumulado"
    }
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(metric.label, style = MaterialTheme.typography.titleSmall)
                    Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${(metric.ratio * 100).toInt()} %", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { metric.ratio.toFloat().coerceIn(0f, 1f) },
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
            Text(
                "${formatAmount(metric.used, metric.unit)} de ${formatAmount(metric.limit, metric.unit)} · alerta en ${formatAmount(metric.limit * UsageMetric.WARNING_RATIO, metric.unit)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UsersTab(state: MasterState, onQuery: (String) -> Unit, onDisable: (PublicProfile, Boolean) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SearchInput(state.userQuery, onQuery, "Buscar por nombre o código") }
        if (state.userSearching) item { Text("Buscando…") }
        else if (state.users.isEmpty()) item { Text(if (state.userQuery.length < 2) "Escribe al menos dos caracteres" else "Sin resultados") }
        items(state.users, key = { it.uid }) { user ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Avatar(user.displayName, user.photoPath) },
                headlineContent = { Text(user.displayName) },
                supportingContent = { Text(UserCode.pretty(user.code) + if (user.disabled) " · Deshabilitado" else "") },
                trailingContent = { Switch(modifier = Modifier.semantics { contentDescription = "Habilitar ${user.displayName}" }, checked = !user.disabled, onCheckedChange = { enabled -> onDisable(user, !enabled) }) },
            )
        }
    }
}

@Composable
private fun StoresTab(state: MasterState, onQuery: (String) -> Unit, onDisable: (Store, Boolean) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SearchInput(state.storeQuery, onQuery, "Buscar tienda por nombre") }
        if (state.storeSearching) item { Text("Buscando…") }
        else if (state.stores.isEmpty()) item { Text(if (state.storeQuery.isBlank()) "Escribe el nombre de la tienda" else "Sin resultados") }
        items(state.stores, key = { it.id }) { store ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(store.name) },
                supportingContent = {
                    Text("${store.address} · Dueño: ${store.ownerName}" + if (store.disabledBySystem) " · Deshabilitada" else "")
                },
                trailingContent = { Switch(modifier = Modifier.semantics { contentDescription = "Habilitar tienda ${store.name}" }, checked = !store.disabledBySystem, onCheckedChange = { enabled -> onDisable(store, !enabled) }) },
            )
        }
    }
}

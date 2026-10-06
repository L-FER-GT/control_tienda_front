package com.lfergt.controltienda.feature.reports

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.domain.model.ColumnKind
import com.lfergt.controltienda.domain.model.ExportFormat
import com.lfergt.controltienda.domain.model.ExportedFile
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.PeriodGrouping
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.ReportCell
import com.lfergt.controltienda.domain.model.ReportFilter
import com.lfergt.controltienda.domain.model.ReportTable
import com.lfergt.controltienda.domain.model.ReportType
import com.lfergt.controltienda.domain.port.ReportExporter
import com.lfergt.controltienda.domain.usecase.GenerateReportUseCase
import com.lfergt.controltienda.feature.catalog.CatalogData
import com.lfergt.controltienda.feature.catalog.CatalogSource
import com.lfergt.controltienda.navigation.ReportDetailRoute
import com.lfergt.controltienda.navigation.ReportsRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.StoreContext
import com.lfergt.controltienda.ui.common.StoreHeader
import com.lfergt.controltienda.ui.common.formatDate
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject

val ReportType.icon: ImageVector
    get() = when (this) {
        ReportType.SALES_BY_PERIOD -> Icons.Outlined.BarChart
        ReportType.SALES_BY_EMPLOYEE -> Icons.Outlined.People
        ReportType.SALES_BY_PAYMENT_METHOD -> Icons.Outlined.Payments
        ReportType.TOP_PRODUCTS -> Icons.Outlined.EmojiEvents
        ReportType.SALES_BY_CATEGORY -> Icons.Outlined.Category
        ReportType.PROFIT -> Icons.AutoMirrored.Outlined.TrendingUp
        ReportType.PURCHASES_BY_SUPPLIER -> Icons.Outlined.LocalShipping
        ReportType.INVENTORY_VALUE -> Icons.Outlined.Inventory
    }

// ------------------------------------------------------------------ lista de reportes

@HiltViewModel
class ReportsViewModel @Inject constructor(savedState: SavedStateHandle, context: StoreContext) : BaseViewModel() {
    val storeId = savedState.toRoute<ReportsRoute>().storeId
    val header = context.header(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreHeader())
}

@Composable
fun ReportsScreen(onBack: () -> Unit, onReport: (ReportType) -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    BackScaffold(title = "Reportes", subtitle = header.name, onBack = onBack) { padding ->
        when {
            !header.loaded -> LoadingBox(Modifier.padding(padding))
            !header.access.can(Permission.REPORTS) -> NoAccess(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(ReportType.entries) { type ->
                    Card(
                        onClick = { onReport(type) },
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(type.icon, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            },
                            headlineContent = { Text(type.title) },
                            supportingContent = { Text(type.description) },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ detalle

enum class RangePreset(val label: String) {
    TODAY("Hoy"), YESTERDAY("Ayer"), LAST_7("Últimos 7 días"), THIS_MONTH("Este mes"), LAST_MONTH("Mes anterior"), CUSTOM("Personalizado")
}

data class ReportUiState(
    val type: ReportType,
    val preset: RangePreset = RangePreset.THIS_MONTH,
    val from: LocalDate = LocalDate.now().withDayOfMonth(1),
    /** Inclusivo para la interfaz. */
    val to: LocalDate = LocalDate.now(),
    val grouping: PeriodGrouping = PeriodGrouping.DAY,
    val limit: Int = 20,
    val loading: Boolean = false,
    val table: ReportTable? = null,
    val exporting: Boolean = false,
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    source: CatalogSource,
    private val generate: GenerateReportUseCase,
    private val exporter: ReportExporter,
) : BaseViewModel() {
    private val route = savedState.toRoute<ReportDetailRoute>()
    val catalog = source.observe(route.storeId).stateIn(viewModelScope, SharingStarted.Eagerly, CatalogData())
    private val _state = MutableStateFlow(ReportUiState(ReportType.valueOf(route.type)))
    val state = _state.asStateFlow()
    private val shareChannel = Channel<ExportedFile>(Channel.CONFLATED)
    val share = shareChannel.receiveAsFlow()

    fun setPreset(preset: RangePreset) {
        if (_state.value.loading) return
        val today = LocalDate.now()
        val (from, to) = when (preset) {
            RangePreset.TODAY -> today to today
            RangePreset.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
            RangePreset.LAST_7 -> today.minusDays(6) to today
            RangePreset.THIS_MONTH -> today.withDayOfMonth(1) to today
            RangePreset.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1) to today.withDayOfMonth(1).minusDays(1)
            RangePreset.CUSTOM -> _state.value.from to _state.value.to
        }
        _state.update { it.copy(preset = preset, from = from, to = to, table = null) }
    }

    fun setCustomRange(from: LocalDate, to: LocalDate) = _state.update { if (it.loading) it else it.copy(preset = RangePreset.CUSTOM, from = from, to = to, table = null) }
    fun setGrouping(g: PeriodGrouping) = _state.update { if (it.loading) it else it.copy(grouping = g, table = null) }
    fun setLimit(limit: Int) = _state.update { if (it.loading) it else it.copy(limit = limit, table = null) }

    /** Aplicar filtros y generar el reporte (las ventas se consultan al servidor o a la caché local). */
    fun apply() {
        val s = _state.value
        if (s.loading) return
        val data = catalog.value
        val store = data.header.store ?: return
        val zone = ZoneId.systemDefault()
        val filter = ReportFilter(
            type = s.type,
            fromMillis = s.from.atStartOfDay(zone).toInstant().toEpochMilli(),
            toMillis = s.to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            grouping = s.grouping,
            limit = s.limit,
        )
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val table = generate(store, filter, data.products, data.categories)
                _state.update { it.copy(table = table, loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false) }
                message(e.userMessage())
            }
        }
    }

    fun export(format: ExportFormat) {
        val table = _state.value.table ?: return
        _state.update { it.copy(exporting = true) }
        launchSafe(onError = { _state.update { it.copy(exporting = false) } }) {
            val file = exporter.export(table, format)
            _state.update { it.copy(exporting = false) }
            shareChannel.send(file)
        }
    }
}

/** Arriba "Reportes" con la flecha atrás; filtros, botón "Ver reporte" y el resultado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(onBack: () -> Unit, viewModel: ReportDetailViewModel = hiltViewModel()) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var rangePicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    CollectMessages(viewModel)
    LaunchedEffect(Unit) {
        viewModel.share.collect { file ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType
                putExtra(Intent.EXTRA_STREAM, Uri.parse(file.uri))
                putExtra(Intent.EXTRA_SUBJECT, file.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Compartir reporte"))
        }
    }

    BackScaffold(title = "Reportes", subtitle = state.type.title, onBack = onBack) { padding ->
        when {
            !catalog.loaded -> LoadingBox(Modifier.padding(padding))
            !catalog.header.access.can(Permission.REPORTS) -> NoAccess(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Filters(state, viewModel, onCustom = { rangePicker = true }) }
                state.table?.let { table ->
                    item { ExportButtons(state.exporting, viewModel::export) }
                    item { ReportTableView(table) }
                }
            }
        }
    }

    if (rangePicker) {
        val zone = ZoneOffset.UTC
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.from.atStartOfDay(zone).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = state.to.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { rangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis ?: start
                        if (start != null && end != null) {
                            viewModel.setCustomRange(
                                Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),
                                Instant.ofEpochMilli(end).atZone(zone).toLocalDate(),
                            )
                        }
                        rangePicker = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { rangePicker = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(pickerState, modifier = Modifier.height(480.dp), title = { Text("Rango de fechas", Modifier.padding(16.dp)) })
        }
    }
}

@Composable
private fun Filters(state: ReportUiState, viewModel: ReportDetailViewModel, onCustom: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.type.needsDateRange) {
            Text("Periodo", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RangePreset.entries) { preset ->
                    FilterChip(
                        enabled = !state.loading,
                        selected = state.preset == preset,
                        onClick = { if (preset == RangePreset.CUSTOM) onCustom() else viewModel.setPreset(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }
            Text(
                if (state.from == state.to) formatDate(state.from.toMillis()) else "${formatDate(state.from.toMillis())} al ${formatDate(state.to.toMillis())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Muestra el inventario actual de los productos con stock controlado.", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.type.needsGrouping) {
            Text("Agrupar por", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PeriodGrouping.DAY to "Día", PeriodGrouping.WEEK to "Semana", PeriodGrouping.MONTH to "Mes").forEach { (g, label) ->
                    FilterChip(enabled = !state.loading, selected = state.grouping == g, onClick = { viewModel.setGrouping(g) }, label = { Text(label) })
                }
            }
        }
        if (state.type.needsLimit) {
            Text("Cantidad de productos", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 50, 100).forEach { n ->
                    FilterChip(enabled = !state.loading, selected = state.limit == n, onClick = { viewModel.setLimit(n) }, label = { Text("Top $n") })
                }
            }
        }
        Button(onClick = viewModel::apply, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Ver reporte")
        }
    }
}

private fun LocalDate.toMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun ExportButtons(exporting: Boolean, onExport: (ExportFormat) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onExport(ExportFormat.PDF) }, enabled = !exporting) {
            Icon(Icons.Outlined.PictureAsPdf, null); Text("  PDF")
        }
        OutlinedButton(onClick = { onExport(ExportFormat.XLSX) }, enabled = !exporting) {
            Icon(Icons.Outlined.TableView, null); Text("  Excel")
        }
        if (exporting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

private fun cellText(cell: ReportCell, currency: String): String = when (cell) {
    is ReportCell.Text -> cell.value
    is ReportCell.Amount -> Money.format(cell.cents, currency)
    is ReportCell.Number -> if (cell.decimals == 0) String.format(Locale.US, "%,.0f", cell.value)
    else String.format(Locale.US, "%,.${cell.decimals}f", cell.value).trimEnd('0').trimEnd('.')
}

/** Tabla con desplazamiento horizontal compartido para que se lea bien en celulares. */
@Composable
private fun ReportTableView(table: ReportTable) {
    val scroll = rememberScrollState()
    var page by remember(table) { mutableIntStateOf(0) }
    val pageSize = 40
    val pages = ((table.rows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val widths: List<Dp> = table.columns.mapIndexed { i, c -> if (i == 0 && c.kind == ColumnKind.TEXT) 180.dp else 120.dp }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(table.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            table.totals?.let { totals ->
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Resumen del periodo", style = MaterialTheme.typography.titleMedium)
                    totals.forEachIndexed { i, cell -> if (cell !is ReportCell.Text) Text("${table.columns[i].title}: ${cellText(cell, table.currency)}", style = MaterialTheme.typography.titleSmall) }
                }
            }
            Text("${table.rows.size} filas · Desliza la tabla para ver todas las columnas", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            TableRow(table.columns.map { it.title }, table, widths, scroll, header = true)
            HorizontalDivider()
            if (table.rows.isEmpty()) {
                Text("Sin datos para los filtros seleccionados.", modifier = Modifier.padding(16.dp))
            }
            table.rows.drop(page * pageSize).take(pageSize).forEachIndexed { index, row ->
                TableRow(row.map { cellText(it, table.currency) }, table, widths, scroll, zebra = index % 2 == 1)
            }
            if (pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text("Anterior") }
                Text("${page + 1} / $pages")
                TextButton(onClick = { page++ }, enabled = page + 1 < pages) { Text("Siguiente") }
            }
            table.totals?.let {
                HorizontalDivider()
                TableRow(it.map { cell -> cellText(cell, table.currency) }, table, widths, scroll, bold = true)
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    table: ReportTable,
    widths: List<Dp>,
    scroll: androidx.compose.foundation.ScrollState,
    header: Boolean = false,
    bold: Boolean = false,
    zebra: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (zebra) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                modifier = Modifier.width(widths[i]).padding(end = 8.dp),
                textAlign = if (table.columns[i].kind == ColumnKind.TEXT) TextAlign.Start else TextAlign.End,
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold || header) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

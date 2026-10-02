package com.lfergt.controltienda.feature.purchasing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.ProductDraft
import com.lfergt.controltienda.domain.model.ReceptionDraft
import com.lfergt.controltienda.domain.model.ReceptionLine
import com.lfergt.controltienda.domain.model.Supplier
import com.lfergt.controltienda.domain.port.Clock
import com.lfergt.controltienda.domain.port.ReceptionRepository
import com.lfergt.controltienda.domain.port.SupplierRepository
import com.lfergt.controltienda.domain.usecase.SaveProductUseCase
import com.lfergt.controltienda.domain.usecase.SaveReceptionUseCase
import com.lfergt.controltienda.feature.catalog.CatalogData
import com.lfergt.controltienda.feature.catalog.CatalogSource
import com.lfergt.controltienda.feature.catalog.search
import com.lfergt.controltienda.feature.scanner.ScanMode
import com.lfergt.controltienda.feature.scanner.ScannerDialog
import com.lfergt.controltienda.navigation.ReceptionEditorRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.formatDate
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.DecimalInput
import com.lfergt.controltienda.ui.components.Dropdown
import com.lfergt.controltienda.ui.components.FormColumn
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.MoneyInput
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.PhotoSourceSheet
import com.lfergt.controltienda.ui.components.SearchInput
import com.lfergt.controltienda.ui.components.StorageImage
import com.lfergt.controltienda.ui.components.TextInput
import com.lfergt.controltienda.ui.components.rememberPhotoPicker
import com.lfergt.controltienda.ui.components.toDecimalOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Línea editable: cantidad y costo como texto mientras se escriben. */
data class LineForm(val productId: String, val productName: String, val unit: MeasureUnit, val quantity: String, val unitCost: String)

data class ReceptionForm(
    val loaded: Boolean = false,
    val supplier: Supplier = Supplier.OTHERS,
    val receivedAt: Long = 0,
    val lines: List<LineForm> = emptyList(),
    val invoiceTotal: String = "",
    val notes: String = "",
    val keptPhotos: List<String> = emptyList(),
    val newPhotos: List<String> = emptyList(),
    val saving: Boolean = false,
    val unknownCode: String? = null,
)

@HiltViewModel
class ReceptionEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    source: CatalogSource,
    private val supplierRepository: SupplierRepository,
    private val receptions: ReceptionRepository,
    private val saveReception: SaveReceptionUseCase,
    private val saveProduct: SaveProductUseCase,
    private val clock: Clock,
) : BaseViewModel() {

    private val route = savedState.toRoute<ReceptionEditorRoute>()
    val storeId = route.storeId
    val receptionId = route.receptionId
    val catalog = source.observe(storeId).stateIn(viewModelScope, SharingStarted.Eagerly, CatalogData())
    val suppliers = supplierRepository.observeSuppliers(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(Supplier.OTHERS))

    private val _form = MutableStateFlow(ReceptionForm())
    val form = _form.asStateFlow()
    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done = doneChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (receptionId == null) {
                _form.value = ReceptionForm(loaded = true, receivedAt = clock.now())
            } else {
                val r = receptions.observeReception(storeId, receptionId).filterNotNull().first()
                // Si el proveedor fue eliminado, se conserva el nombre guardado en la recepción.
                val supplierList = supplierRepository.observeSuppliers(storeId).first()
                _form.value = ReceptionForm(
                    loaded = true,
                    supplier = supplierList.firstOrNull { it.id == r.supplierId } ?: Supplier.OTHERS.copy(id = r.supplierId, companyName = r.supplierName),
                    receivedAt = r.receivedAt,
                    lines = r.lines.map { LineForm(it.productId, it.productName, it.unit, it.unit.formatQuantity(it.quantity), Money.toInput(it.unitCostCents)) },
                    invoiceTotal = Money.toInput(r.invoiceTotalCents),
                    notes = r.notes ?: "",
                    keptPhotos = r.invoicePhotos,
                )
            }
        }
    }

    fun setSupplier(s: Supplier) = _form.update { it.copy(supplier = s) }
    fun setDate(millis: Long) = _form.update { it.copy(receivedAt = millis) }
    fun setInvoiceTotal(v: String) = _form.update { it.copy(invoiceTotal = v) }
    fun setNotes(v: String) = _form.update { it.copy(notes = v) }
    fun addPhoto(uri: String) = _form.update { if (it.keptPhotos.size + it.newPhotos.size >= 10) it else it.copy(newPhotos = it.newPhotos + uri) }
    fun removeKept(path: String) = _form.update { it.copy(keptPhotos = it.keptPhotos - path) }
    fun removeNew(uri: String) = _form.update { it.copy(newPhotos = it.newPhotos - uri) }

    fun addProduct(product: Product) = _form.update { f ->
        val existing = f.lines.indexOfFirst { it.productId == product.id }
        if (existing >= 0) {
            val line = f.lines[existing]
            val qty = (line.quantity.toDecimalOrNull() ?: 0.0) + 1
            f.copy(lines = f.lines.toMutableList().also { it[existing] = line.copy(quantity = product.unit.formatQuantity(qty)) })
        } else {
            f.copy(lines = f.lines + LineForm(product.id, product.name, product.unit, "1", Money.toInput(product.purchaseCostCents)))
        }
    }

    fun updateLine(index: Int, line: LineForm) = _form.update { f -> f.copy(lines = f.lines.toMutableList().also { it[index] = line }) }
    fun removeLine(index: Int) = _form.update { f -> f.copy(lines = f.lines.toMutableList().also { it.removeAt(index) }) }

    fun onScanned(code: String) {
        val product = catalog.value.products.firstOrNull { it.matchesCode(code) }
        if (product != null) addProduct(product) else _form.update { it.copy(unknownCode = code) }
    }

    fun dismissUnknown() = _form.update { it.copy(unknownCode = null) }

    /** Crea el producto que llegó y aún no estaba registrado (con stock 0: la recepción lo suma). */
    fun quickCreate(name: String, salePriceCents: Long, costCents: Long?, barcode: String?) = launchSafe {
        val products = catalog.value.products
        val draft = ProductDraft(
            id = null, name = name, categoryId = null, salePriceCents = salePriceCents, purchaseCostCents = costCents,
            unit = MeasureUnit.UNIT, stock = 0.0, stockAlert = null, barcode = barcode, qrCode = null,
        )
        val id = saveProduct(storeId, draft, products, null)
        _form.update {
            it.copy(
                unknownCode = null,
                lines = it.lines + LineForm(id, name.trim(), MeasureUnit.UNIT, "1", Money.toInput(costCents)),
            )
        }
        message("Producto \"$name\" creado")
    }

    val linesTotalCents: Long
        get() = _form.value.lines.sumOf { l ->
            val cost = Money.parseToCents(l.unitCost) ?: 0
            Money.lineTotal(cost, l.quantity.toDecimalOrNull() ?: 0.0)
        }

    fun save() {
        val f = _form.value
        if (f.saving) return
        val lines = f.lines.map { l ->
            ReceptionLine(l.productId, l.productName, l.quantity.toDecimalOrNull() ?: 0.0, l.unit, Money.parseToCents(l.unitCost))
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                saveReception(
                    storeId,
                    ReceptionDraft(
                        id = receptionId,
                        supplierId = f.supplier.id,
                        supplierName = f.supplier.companyName,
                        lines = lines,
                        invoiceTotalCents = Money.parseToCents(f.invoiceTotal),
                        keptPhotos = f.keptPhotos,
                        newPhotos = f.newPhotos.map { LocalFile(it) },
                        notes = f.notes,
                        receivedAt = f.receivedAt,
                    ),
                )
                message(if (receptionId == null) "Recepción registrada: el stock se actualizará al sincronizar" else "Recepción actualizada")
                doneChannel.send(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.userMessage())
            } finally {
                _form.update { it.copy(saving = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceptionEditorScreen(onBack: () -> Unit, viewModel: ReceptionEditorViewModel = hiltViewModel()) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var photoSheet by rememberSaveable { mutableStateOf(false) }
    val photoPicker = rememberPhotoPicker { viewModel.addPhoto(it.toString()) }
    val currency = catalog.header.currency
    CollectMessages(viewModel)
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }

    BackScaffold(
        title = "Recepción de mercadería",
        subtitle = if (viewModel.receptionId == null) "Nueva recepción" else "Editar recepción",
        onBack = onBack,
    ) { padding ->
        when {
            !catalog.loaded || !form.loaded -> LoadingBox(Modifier.padding(padding))
            !catalog.header.access.can(Permission.RECEPTIONS) -> NoAccess(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                FormColumn {
                    Dropdown(
                        label = "Proveedor",
                        options = suppliers,
                        selected = form.supplier,
                        optionLabel = { it.companyName },
                        onSelected = viewModel::setSupplier,
                    )
                    OutlinedButton(onClick = { datePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CalendarMonth, null)
                        Text("  Fecha de recepción: ${formatDate(form.receivedAt)}")
                    }

                    Text("Productos recibidos", style = MaterialTheme.typography.titleMedium)
                    form.lines.forEachIndexed { index, line -> LineEditor(line, currency, { viewModel.updateLine(index, it) }, { viewModel.removeLine(index) }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.AddBox, null); Text("  Agregar")
                        }
                        OutlinedButton(onClick = { scanning = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.QrCodeScanner, null); Text("  Escanear")
                        }
                    }
                    if (form.lines.isNotEmpty()) {
                        Text(
                            "Suma de productos: ${Money.format(viewModel.linesTotalCents, currency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    MoneyInput(form.invoiceTotal, viewModel::setInvoiceTotal, "Total de la factura", currency, supporting = "Precio total de la factura o boleta")
                    TextInput(form.notes, viewModel::setNotes, "Notas (N° de factura, observaciones)", singleLine = false)

                    Text("Fotos de la factura (${form.keptPhotos.size + form.newPhotos.size}/10)", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(form.keptPhotos) { path ->
                            PhotoThumb(onRemove = { viewModel.removeKept(path) }) { StorageImage(path, null, Modifier.size(96.dp)) }
                        }
                        items(form.newPhotos) { uri ->
                            PhotoThumb(onRemove = { viewModel.removeNew(uri) }) {
                                AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
                            }
                        }
                        item {
                            Surface(onClick = { photoSheet = true }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AddAPhoto, "Agregar foto") }
                            }
                        }
                    }
                    Text(
                        "Puedes registrar solo la foto de la factura y completar los productos después.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = viewModel::save, enabled = !form.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (viewModel.receptionId == null) "Registrar recepción" else "Guardar cambios")
                    }
                }
            }
        }
    }

    if (picking) {
        ProductPicker(catalog.products, currency, onDismiss = { picking = false }, onPick = { viewModel.addProduct(it) })
    }
    if (scanning) {
        ScannerDialog(ScanMode.ANY, onResult = { scanning = false; viewModel.onScanned(it.value) }, onDismiss = { scanning = false })
    }
    form.unknownCode?.let { code ->
        QuickProductDialog(code, currency, onDismiss = viewModel::dismissUnknown, onCreate = viewModel::quickCreate)
    }
    if (photoSheet) PhotoSourceSheet(onDismiss = { photoSheet = false }, picker = photoPicker)
    if (datePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = form.receivedAt)
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utcMidnight ->
                        // El DatePicker devuelve medianoche UTC: se conserva el día elegido a las 12:00 locales.
                        val date = java.time.Instant.ofEpochMilli(utcMidnight).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        viewModel.setDate(date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }
                    datePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(pickerState) }
    }
}

@Composable
private fun LineEditor(line: LineForm, currency: String, onChange: (LineForm) -> Unit, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.productName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Quitar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalInput(line.quantity, { onChange(line.copy(quantity = it)) }, "Cantidad (${line.unit.symbol})", Modifier.weight(1f))
                MoneyInput(line.unitCost, { onChange(line.copy(unitCost = it)) }, "Costo unitario", currency, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhotoThumb(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box {
        Surface(shape = MaterialTheme.shapes.medium) { content() }
        FilledTonalIconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
            Icon(Icons.Outlined.Close, "Quitar foto", Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPicker(products: List<Product>, currency: String, onDismiss: () -> Unit, onPick: (Product) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Agregar producto", style = MaterialTheme.typography.titleLarge)
            SearchInput(query, { query = it }, "Buscar producto")
            LazyColumn(Modifier.heightIn(max = 460.dp).padding(bottom = 24.dp)) {
                items(products.search(query), key = { it.id }) { product ->
                    ListItem(
                        leadingContent = { StorageImage(product.photoPath, null, Modifier.size(44.dp)) },
                        headlineContent = { Text(product.name) },
                        supportingContent = {
                            Text("Costo actual: " + (product.purchaseCostCents?.let { Money.format(it, currency) } ?: "—"))
                        },
                        modifier = Modifier.padding(0.dp),
                        trailingContent = { TextButton(onClick = { onPick(product) }) { Text("Agregar") } },
                    )
                }
            }
        }
    }
}

/** Producto que llegó y no estaba registrado: se crea aquí mismo (nombre, precio de venta y costo). */
@Composable
private fun QuickProductDialog(code: String, currency: String, onDismiss: () -> Unit, onCreate: (String, Long, Long?, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    val priceCents = Money.parseToCents(price)
    val isBarcode = code.all(Char::isDigit)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp),
        title = { Text("Producto no registrado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Código: $code", style = MaterialTheme.typography.bodySmall)
                TextInput(name, { name = it }, "Nombre *")
                MoneyInput(price, { price = it }, "Precio de venta *", currency)
                MoneyInput(cost, { cost = it }, "Costo de compra", currency)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, priceCents!!, Money.parseToCents(cost), if (isBarcode) code else null) },
                enabled = name.isNotBlank() && priceCents != null,
            ) { Text("Crear y agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

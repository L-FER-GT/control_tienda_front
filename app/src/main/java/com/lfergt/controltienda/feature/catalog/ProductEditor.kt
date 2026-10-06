package com.lfergt.controltienda.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lfergt.controltienda.ui.components.rememberGuardedBack
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.PriceChangeSource
import com.lfergt.controltienda.domain.model.PriceHistoryEntry
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.ProductDraft
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.port.ProductInfoLookup
import com.lfergt.controltienda.domain.usecase.ProductValidator
import com.lfergt.controltienda.domain.usecase.SaveProductUseCase
import com.lfergt.controltienda.feature.scanner.ScanMode
import com.lfergt.controltienda.feature.scanner.ScannerDialog
import com.lfergt.controltienda.navigation.ProductEditorRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.formatDateTime
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.ConfirmDialog
import com.lfergt.controltienda.ui.components.DecimalInput
import com.lfergt.controltienda.ui.components.Dropdown
import com.lfergt.controltienda.ui.components.FormColumn
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.MoneyInput
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.PhotoField
import com.lfergt.controltienda.ui.components.TextInput
import com.lfergt.controltienda.ui.components.toDecimalOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import javax.inject.Inject
import com.lfergt.controltienda.ui.components.UnavailableState

data class ProductForm(
    val unavailable: Boolean = false,
    val loaded: Boolean = false,
    val name: String = "",
    val salePrice: String = "",
    val purchaseCost: String = "",
    val categoryId: String? = null,
    val unit: MeasureUnit = MeasureUnit.UNIT,
    val trackStock: Boolean = false,
    val stock: String = "",
    val stockAlert: String = "",
    val barcode: String = "",
    val qrCode: String = "",
    val photoPath: String? = null,
    val pickedPhoto: String? = null,
    val suggestion: String? = null,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
) : java.io.Serializable

@HiltViewModel
class ProductEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    source: CatalogSource,
    private val catalog: CatalogRepository,
    private val saveProduct: SaveProductUseCase,
    private val productInfo: ProductInfoLookup,
) : BaseViewModel() {

    private val route = savedState.toRoute<ProductEditorRoute>()
    val storeId = route.storeId
    val productId = route.productId
    val data = source.observe(storeId).stateIn(viewModelScope, SharingStarted.Eagerly, CatalogData())

    private val _form = MutableStateFlow(savedState.get<ProductForm>("editorDraft")?.copy(saving = false) ?: ProductForm())
    val form = _form.asStateFlow()
    private var baseline: ProductForm? = savedState["editorBaseline"]
    val dirty: Boolean get() = baseline?.let { _form.value.copy(saving = false, errors = emptyMap(), suggestion = null) != it.copy(saving = false, errors = emptyMap(), suggestion = null) } ?: false
    private fun rememberBaseline() {
        if (baseline == null) { baseline = _form.value; savedState["editorBaseline"] = baseline }
    }


    val history = (if (productId != null) catalog.observePriceHistory(storeId, productId) else flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<PriceHistoryEntry>())

    private var original: Product? = null
    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done = doneChannel.receiveAsFlow()

    init {
        viewModelScope.launch { _form.collect { savedState["editorDraft"] = it.copy(saving = false) } }
        viewModelScope.launch {
            val loaded = data.first { it.loaded }
            val product = productId?.let { id -> loaded.products.firstOrNull { it.id == id } }
            if (productId != null && product == null) {
                _form.update { it.copy(loaded = true, unavailable = true) }
                return@launch
            }
            original = product
            if (!_form.value.loaded) _form.value = if (product != null) {
                ProductForm(
                    loaded = true,
                    name = product.name,
                    salePrice = Money.toInput(product.salePriceCents),
                    purchaseCost = Money.toInput(product.purchaseCostCents),
                    categoryId = product.categoryId,
                    unit = product.unit,
                    trackStock = product.stock != null,
                    stock = product.stock?.let(product.unit::formatQuantity) ?: "",
                    stockAlert = product.stockAlert?.let(product.unit::formatQuantity) ?: "",
                    barcode = product.barcode ?: "",
                    qrCode = product.qrCode ?: "",
                    photoPath = product.photoPath,
                )
            } else {
                val code = route.code
                ProductForm(
                    loaded = true,
                    barcode = code?.takeIf { it.startsWith("bar:") }?.removePrefix("bar:") ?: "",
                    qrCode = code?.takeIf { it.startsWith("qr:") }?.removePrefix("qr:") ?: "",
                )
            }
            rememberBaseline()
        }
        // Al registrar un código nuevo, sugiere el nombre con Open Food Facts.
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            _form.map { it.barcode }.distinctUntilChanged().debounce(500).collect { code ->
                if (code.length >= 8 && code.all(Char::isDigit) && original?.barcode != code) {
                    val name = productInfo.findProductName(code)
                    if (name != null && _form.value.name.isBlank()) _form.update { it.copy(suggestion = name) }
                }
            }
        }
    }

    private fun edit(field: String, block: (ProductForm) -> ProductForm) = _form.update { block(it).copy(errors = it.errors - field) }

    fun onName(v: String) = edit(ProductValidator.FIELD_NAME) { it.copy(name = v) }
    fun onSalePrice(v: String) = edit(ProductValidator.FIELD_SALE_PRICE) { it.copy(salePrice = v) }
    fun onPurchaseCost(v: String) = edit(ProductValidator.FIELD_PURCHASE_COST) { it.copy(purchaseCost = v) }
    fun onCategory(id: String?) = _form.update { it.copy(categoryId = id) }
    fun onUnit(u: MeasureUnit) = _form.update { it.copy(unit = u) }
    fun onTrackStock(v: Boolean) = _form.update { it.copy(trackStock = v, errors = emptyMap()) }
    fun onStock(v: String) = edit(ProductValidator.FIELD_STOCK_ALERT) { it.copy(stock = v) }
    fun onStockAlert(v: String) = edit(ProductValidator.FIELD_STOCK_ALERT) { it.copy(stockAlert = v) }
    fun onBarcode(v: String) = edit(ProductValidator.FIELD_BARCODE) { it.copy(barcode = v.trim()) }
    fun onQr(v: String) = edit(ProductValidator.FIELD_QR) { it.copy(qrCode = v.trim()) }
    fun onPhoto(uri: String) = _form.update { it.copy(pickedPhoto = uri) }
    fun useSuggestion() = _form.update { it.copy(name = it.suggestion ?: it.name, suggestion = null) }
    fun dismissSuggestion() = _form.update { it.copy(suggestion = null) }

    fun save() {
        val f = _form.value
        if (f.saving) return
        val salePrice = Money.parseToCents(f.salePrice)
        if (salePrice == null) {
            _form.update { it.copy(errors = it.errors + (ProductValidator.FIELD_SALE_PRICE to "El precio de venta es obligatorio")) }
            return
        }
        if (f.trackStock && f.stock.toDecimalOrNull() == null) {
            _form.update { it.copy(errors = it.errors + (ProductValidator.FIELD_STOCK_ALERT to "Ingresa el stock actual")) }
            return
        }
        val draft = ProductDraft(
            id = productId,
            name = f.name,
            categoryId = f.categoryId,
            salePriceCents = salePrice,
            purchaseCostCents = Money.parseToCents(f.purchaseCost),
            unit = f.unit,
            stock = if (f.trackStock) f.stock.toDecimalOrNull() else null,
            stockAlert = if (f.trackStock) f.stockAlert.toDecimalOrNull() else null,
            barcode = f.barcode.ifBlank { null },
            qrCode = f.qrCode.ifBlank { null },
            previous = original,
        )
        val errors = ProductValidator.validate(draft, data.value.products)
        if (errors.isNotEmpty()) {
            _form.update { it.copy(errors = errors) }
            return
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                saveProduct(storeId, draft, data.value.products, f.pickedPhoto?.let { LocalFile(it) })
                doneChannel.send(Unit)
            } catch (e: DomainError.Validation) {
                _form.update { it.copy(errors = mapOf((e.field ?: ProductValidator.FIELD_NAME) to e.userMessage())) }
            } catch (e: Exception) {
                message(e.userMessage())
            } finally {
                _form.update { it.copy(saving = false) }
            }
        }
    }

    fun delete() {
        val id = productId ?: return
        launchSafe {
            catalog.deleteProduct(storeId, id)
            doneChannel.send(Unit)
        }
    }
}

@Composable
fun ProductEditorScreen(onBack: () -> Unit, viewModel: ProductEditorViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var scanFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val currency = data.header.currency
    val formScroll = rememberScrollState()
    LaunchedEffect(form.errors) { if (form.errors.isNotEmpty()) formScroll.animateScrollTo(0) }
    CollectMessages(viewModel)
    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }

    BackScaffold(
        title = "Gestionar productos",
        subtitle = if (viewModel.productId == null) "Nuevo producto" else form.name,
        onBack = rememberGuardedBack(viewModel.dirty, form.saving, onBack),
        bottomBar = {
            if (data.loaded && form.loaded && !form.unavailable && data.header.access.can(Permission.MANAGE_PRODUCTS)) {
                Button(onClick = viewModel::save, enabled = !form.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    Text(if (form.saving) "Guardando…" else if (viewModel.productId == null) "Crear producto" else "Guardar cambios")
                }
            }
        },
        actions = {
            if (viewModel.productId != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar producto") }
            }
        },
    ) { padding ->
        when {
            form.unavailable -> UnavailableState(onBack, Modifier.padding(padding))
            !data.loaded || !form.loaded -> LoadingBox(Modifier.padding(padding))
            !data.header.access.can(Permission.MANAGE_PRODUCTS) -> NoAccess(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).verticalScroll(formScroll)) {
                FormColumn {
                    if (form.errors.isNotEmpty()) Text(form.errors.values.distinct().joinToString("\n"), color = MaterialTheme.colorScheme.error)
                    TextInput(form.name, viewModel::onName, "Nombre *", error = form.errors[ProductValidator.FIELD_NAME])
                    form.suggestion?.let { suggestion ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, null)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text("Sugerencia (Open Food Facts)", style = MaterialTheme.typography.labelMedium)
                                    Text(suggestion, style = MaterialTheme.typography.bodyLarge)
                                }
                                AssistChip(onClick = viewModel::useSuggestion, label = { Text("Usar") })
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MoneyInput(form.salePrice, viewModel::onSalePrice, "Precio de venta *", currency, Modifier.fillMaxWidth(), error = form.errors[ProductValidator.FIELD_SALE_PRICE])
                        MoneyInput(form.purchaseCost, viewModel::onPurchaseCost, "Costo de compra", currency, Modifier.fillMaxWidth(), error = form.errors[ProductValidator.FIELD_PURCHASE_COST])
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dropdown(
                            label = "Categoría",
                            options = listOf<Category?>(null) + data.categories,
                            selected = data.categories.firstOrNull { it.id == form.categoryId },
                            optionLabel = { it?.name ?: "Sin categoría" },
                            onSelected = { viewModel.onCategory(it?.id) },
                            placeholder = "Sin categoría",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Dropdown(
                            label = "Unidad",
                            options = MeasureUnit.entries,
                            selected = form.unit,
                            optionLabel = { it.label },
                            onSelected = viewModel::onUnit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Controlar inventario", Modifier.weight(1f))
                        Switch(form.trackStock, viewModel::onTrackStock, Modifier.semantics { contentDescription = "Controlar inventario" })
                    }
                    if (form.trackStock) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DecimalInput(
                            form.stock, viewModel::onStock, "Stock actual *", Modifier.fillMaxWidth(), allowNegative = true,
                            supporting = "Cantidad disponible; puede ser negativa",
                            error = form.errors[ProductValidator.FIELD_STOCK_ALERT],
                        )
                        DecimalInput(
                            form.stockAlert, viewModel::onStockAlert, "Alerta de stock", Modifier.fillMaxWidth(),
                            error = form.errors[ProductValidator.FIELD_STOCK_ALERT],
                            supporting = "Avisa al llegar a este mínimo",
                        )
                    }
                    TextInput(
                        form.barcode, viewModel::onBarcode, "Código de barras",
                        error = form.errors[ProductValidator.FIELD_BARCODE],
                        trailing = { IconButton(onClick = { scanFor = "bar" }) { Icon(Icons.Outlined.QrCodeScanner, "Escanear código de barras") } },
                    )
                    TextInput(
                        form.qrCode, viewModel::onQr, "Código QR",
                        error = form.errors[ProductValidator.FIELD_QR],
                        trailing = { IconButton(onClick = { scanFor = "qr" }) { Icon(Icons.Outlined.QrCode2, "Escanear QR") } },
                    )
                    PhotoField(form.photoPath, form.pickedPhoto, { viewModel.onPhoto(it.toString()) }, label = "Foto del producto (opcional)", aspectRatio = 3f)
                    if (history.isNotEmpty()) PriceHistory(history, currency)
                }
            }
        }
    }

    scanFor?.let { target ->
        ScannerDialog(
            mode = if (target == "qr") ScanMode.QR else ScanMode.BARCODE,
            onDismiss = { scanFor = null },
            onResult = { code ->
                scanFor = null
                if (target == "qr") viewModel.onQr(code.value) else viewModel.onBarcode(code.value)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar producto?",
            message = "Se eliminará \"${form.name}\". Las ventas registradas no se modifican.",
            confirmText = "Eliminar",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/** El mismo producto puede comprarse y venderse a distintos precios: aquí queda el registro. */
@Composable
private fun PriceHistory(history: List<PriceHistoryEntry>, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Historial de precios", style = MaterialTheme.typography.titleMedium)
        history.forEach { entry ->
            val source = when (entry.source) {
                PriceChangeSource.CREATED -> "Creación"
                PriceChangeSource.MANUAL -> "Edición"
                PriceChangeSource.RECEPTION -> "Recepción"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${formatDateTime(entry.at)} · $source", style = MaterialTheme.typography.bodySmall)
                    Text(entry.changedByName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    entry.salePriceCents?.let { Text("Venta: ${Money.format(it, currency)}", style = MaterialTheme.typography.bodyMedium) }
                    entry.purchaseCostCents?.let { Text("Costo: ${Money.format(it, currency)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

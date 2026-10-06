package com.lfergt.controltienda.feature.orders

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.outlined.Search
import com.lfergt.controltienda.ui.components.LocalSnackbar
import com.lfergt.controltienda.ui.components.ProductSelectionSheet
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.usecase.CartLine
import com.lfergt.controltienda.feature.scanner.CodeScanner
import com.lfergt.controltienda.feature.scanner.ScanMode
import com.lfergt.controltienda.feature.scanner.ScanSounds
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.ConfirmDialog
import com.lfergt.controltienda.ui.components.DecimalInput
import com.lfergt.controltienda.ui.components.Dropdown
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.ExpandableFab
import com.lfergt.controltienda.ui.components.FabAction
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.MoneyInput
import com.lfergt.controltienda.ui.components.NoAccess
import com.lfergt.controltienda.ui.components.StorageImage
import com.lfergt.controltienda.ui.components.TextInput
import com.lfergt.controltienda.ui.components.toDecimalOrNull

@Composable
fun CreateOrderScreen(
    onBack: () -> Unit,
    onRegisterProduct: (code: String) -> Unit,
    viewModel: CreateOrderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var manualPrefill by rememberSaveable { mutableStateOf("") }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var editingQuantity by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val currency = state.catalog.header.currency
    fun removeLine(line: CartLine) {
        viewModel.remove(line.key)
        scope.launch {
            if (snackbar.showSnackbar("Producto quitado", actionLabel = "Deshacer") == SnackbarResult.ActionPerformed) viewModel.restoreLine(line)
        }
    }
    CollectMessages(viewModel)

    BackHandler(enabled = (!state.cart.isEmpty || state.saving) && state.scanMode == null) { if (!state.saving) confirmExit = true }
    val back = { if (!state.saving) { if (state.cart.isEmpty) onBack() else confirmExit = true } }

    BackScaffold(
        title = "Nueva venta",
        subtitle = state.catalog.header.name,
        onBack = back,
        floatingActionButton = {
            if (state.catalog.header.access.isStaff && !state.saving) {
                ExpandableFab(
                    icon = Icons.Outlined.Add,
                    actions = listOf(
                        FabAction("Buscar producto o código", Icons.Outlined.Search) { picking = true },
                        FabAction("Escanear código de barras", Icons.Outlined.ViewWeek) { viewModel.openScanner(ScanMode.BARCODE) },
                        FabAction("Escanear QR", Icons.Outlined.QrCode2) { viewModel.openScanner(ScanMode.QR) },
                        FabAction("Introducción manual", Icons.Outlined.EditNote) { manualPrefill = ""; manualOpen = true },
                    ),
                )
            }
        },
        bottomBar = {
            if (!state.cart.isEmpty) {
                CheckoutBar(
                    totalCents = state.cart.totalCents,
                    currency = currency,
                    payment = state.payment,
                    saving = state.saving,
                    onPayment = viewModel::setPayment,
                    onSubmit = viewModel::submit,
                )
            }
        },
    ) { padding ->
        when {
            !state.catalog.loaded -> LoadingBox(Modifier.padding(padding))
            !state.catalog.header.access.isStaff -> NoAccess(Modifier.padding(padding))
            state.cart.isEmpty -> EmptyState(
                Icons.Outlined.ShoppingCart,
                "Empieza una venta",
                "Busca un producto por nombre o código, escanéalo o agrega un ítem manual con el botón +.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Resumen (${state.cart.itemCount} ítems)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmClear = true }, enabled = !state.saving) { Text("Vaciar") }
                    }
                }
                items(state.cart.lines, key = { it.key }) { line ->
                    CartLineRow(
                        line = line,
                        currency = currency,
                        photoPath = line.item.productId?.let { id -> state.catalog.products.firstOrNull { it.id == id }?.photoPath },
                        enabled = !state.saving,
                        onEditQuantity = { editingQuantity = line.key },
                        onQuantity = { if (it <= 0) removeLine(line) else viewModel.setQuantity(line.key, it) },
                        onRemove = { removeLine(line) },
                    )
                }
            }
        }
    }

    if (picking && !state.saving) ProductSelectionSheet(
        state.catalog.products, currency, onDismiss = { picking = false }, onPick = viewModel::addProduct,
        quantities = state.cart.lines.mapNotNull { line -> line.item.productId?.let { it to line.item.unit.formatQuantity(line.item.quantity) } }.toMap(),
    )
    if (confirmClear) ConfirmDialog("¿Vaciar la venta?", "Se quitarán todos los productos del resumen.", "Vaciar", viewModel::clear, { confirmClear = false })
    editingQuantity?.let { key ->
        state.cart.lines.firstOrNull { it.key == key }?.let { line ->
            QuantityDialog(line, onDismiss = { editingQuantity = null }, onSave = { viewModel.setQuantity(key, it); editingQuantity = null })
        }
    }
    state.scanMode?.let { mode ->
        ScanOverlay(
            mode = mode,
            state = state,
            canRegister = state.catalog.header.access.can(Permission.MANAGE_PRODUCTS),
            onScanned = viewModel::onScanned,
            onClose = viewModel::closeScanner,
            onManual = { code -> manualPrefill = code; manualOpen = true },
            onRegister = { code -> viewModel.closeScanner(); onRegisterProduct(code) },
        )
    }
    if (manualOpen) {
        ManualItemDialog(
            currency = currency,
            categories = state.catalog.categories,
            initialDetail = manualPrefill,
            onDismiss = { manualOpen = false },
            onAdd = { detail, categoryId, price, qty, unit ->
                manualOpen = false
                viewModel.addManual(detail, categoryId, price, qty, unit)
            },
        )
    }
    if (confirmExit) {
        ConfirmDialog(
            title = "¿Salir sin registrar?",
            message = "La orden tiene productos que se perderán.",
            confirmText = "Salir",
            onConfirm = onBack,
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun CartLineRow(line: CartLine, currency: String, photoPath: String?, enabled: Boolean, onEditQuantity: () -> Unit, onQuantity: (Double) -> Unit, onRemove: () -> Unit) {
    val item = line.item
    val step = if (item.unit.allowsDecimals) 0.25 else 1.0
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            StorageImage(photoPath, null, Modifier.size(52.dp), placeholderIcon = if (item.manual) Icons.Outlined.EditNote else Icons.Outlined.ShoppingCart)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(item.description, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Money.format(item.unitPriceCents, currency)} x ${item.unit.formatQuantity(item.quantity)} ${item.unit.symbol}" +
                        if (item.manual) " · manual" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Money.format(item.subtotalCents, currency), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = enabled, onClick = { onQuantity(item.quantity - step) }) { Icon(Icons.Outlined.Remove, "Reducir cantidad de ${item.description}") }
            TextButton(onClick = onEditQuantity, enabled = enabled) { Text("${item.unit.formatQuantity(item.quantity)} ${item.unit.symbol}") }
            IconButton(enabled = enabled, onClick = { onQuantity(item.quantity + step) }) { Icon(Icons.Outlined.Add, "Más") }
            IconButton(enabled = enabled, onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Quitar ${item.description}") }
          }
        }
    }
}

/** Total + método de pago (solo informativo) + registrar. */
@Composable
private fun CheckoutBar(
    totalCents: Long,
    currency: String,
    payment: PaymentMethod,
    saving: Boolean,
    onPayment: (PaymentMethod) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PaymentMethod.entries) { method ->
                    FilterChip(enabled = !saving, selected = method == payment, onClick = { onPayment(method) }, label = { Text(method.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total", style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(totalCents, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onSubmit, enabled = !saving, modifier = Modifier.height(52.dp)) {
                    if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Registrar venta")
                }
            }
        }
    }
}

/**
 * Cámara con el cuadro de enfoque. Debajo del cuadro, con un margen, aparece un recuadro blanco
 * con letras negras: nombre y precio del producto leído.
 */
@Composable
private fun ScanOverlay(
    mode: ScanMode,
    state: CreateOrderState,
    canRegister: Boolean,
    onScanned: (com.lfergt.controltienda.feature.scanner.ScannedCode) -> Unit,
    onClose: () -> Unit,
    onManual: (String) -> Unit,
    onRegister: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.feedback?.seq) {
        when (state.feedback) {
            is ScanFeedback.Added -> {
                ScanSounds.ok()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            is ScanFeedback.Unknown -> ScanSounds.unknown()
            null -> Unit
        }
    }
    val currency = state.catalog.header.currency

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CodeScanner(
                mode = mode,
                onDetected = onScanned,
                belowFrame = {
                    when (val fb = state.feedback) {
                        is ScanFeedback.Added -> Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(fb.product.name, color = Color.Black, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    Money.format(fb.product.salePriceCents, currency),
                                    color = Color.Black,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("En la orden: ${fb.product.unit.formatQuantity(fb.quantityInCart)} ${fb.product.unit.symbol}", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ScanFeedback.Unknown -> Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Código no registrado", color = Color.Black, style = MaterialTheme.typography.titleMedium)
                                Text(fb.code, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onManual("") }) { Text("Ingresar manual") }
                                    if (canRegister) {
                                        FilledTonalButton(onClick = { onRegister((if (fb.isQr) "qr:" else "bar:") + fb.code) }) { Text("Registrar") }
                                    }
                                }
                            }
                        }
                        null -> Unit
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar", tint = Color.White) }
                Spacer(Modifier.width(4.dp))
                Text(
                    "${state.cart.itemCount} ítems · ${Money.format(state.cart.totalCents, currency)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Button(
                onClick = onClose,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp).fillMaxWidth().height(52.dp),
            ) { Text("Listo, ver resumen") }
        }
    }
}

/** Introducción manual: sin nombre de producto, solo detalle, categoría, precio y cantidad. */
@Composable
private fun ManualItemDialog(
    currency: String,
    categories: List<Category>,
    initialDetail: String,
    onDismiss: () -> Unit,
    onAdd: (String, String?, Long, Double, MeasureUnit) -> Unit,
) {
    var detail by rememberSaveable { mutableStateOf(initialDetail) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var price by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(MeasureUnit.UNIT) }
    val priceCents = Money.parseToCents(price)
    val qty = quantity.toDecimalOrNull()
    val valid = detail.isNotBlank() && priceCents != null && qty != null && qty > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text("Ítem manual") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(detail, { detail = it }, "Detalle *", singleLine = false)
                Dropdown(
                    label = "Categoría",
                    options = listOf<Category?>(null) + categories,
                    selected = categories.firstOrNull { it.id == categoryId },
                    optionLabel = { it?.name ?: "Sin categoría" },
                    onSelected = { categoryId = it?.id },
                    placeholder = "Sin categoría",
                )
                MoneyInput(price, { price = it }, "Precio unitario *", currency)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DecimalInput(quantity, { quantity = it }, "Cantidad *", Modifier.weight(1f))
                    Dropdown(
                        label = "Unidad",
                        options = MeasureUnit.entries,
                        selected = unit,
                        optionLabel = { it.label },
                        onSelected = { unit = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (priceCents != null && qty != null) {
                    HorizontalDivider()
                    Text("Subtotal: ${Money.format(Money.lineTotal(priceCents, qty), currency)}", style = MaterialTheme.typography.titleSmall)
                }
                Text("Los ítems manuales no modifican el inventario.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(detail.trim(), categoryId, priceCents!!, qty!!, unit) }, enabled = valid) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun QuantityDialog(line: CartLine, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by rememberSaveable(line.key) { mutableStateOf(line.item.unit.formatQuantity(line.item.quantity)) }
    val quantity = value.toDecimalOrNull()
    val valid = quantity != null && quantity.isFinite() && quantity > 0 && (line.item.unit.allowsDecimals || quantity % 1.0 == 0.0)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(line.item.description) },
        text = { DecimalInput(value, { value = it }, "Cantidad (${line.item.unit.symbol})", error = if (!valid) "Ingresa una cantidad positiva${if (line.item.unit.allowsDecimals) "" else " sin decimales"}" else null) },
        confirmButton = { TextButton(onClick = { quantity?.let(onSave) }, enabled = valid) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

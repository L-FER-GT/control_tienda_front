package com.lfergt.controltienda.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.port.Clock
import com.lfergt.controltienda.domain.usecase.CreateOrderUseCase
import com.lfergt.controltienda.domain.usecase.OrderCart
import com.lfergt.controltienda.domain.usecase.ScanDebouncer
import com.lfergt.controltienda.feature.catalog.CatalogData
import com.lfergt.controltienda.feature.catalog.CatalogSource
import com.lfergt.controltienda.feature.scanner.ScanMode
import com.lfergt.controltienda.feature.scanner.ScannedCode
import com.lfergt.controltienda.navigation.CreateOrderRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.lfergt.controltienda.domain.usecase.CartLine

/** Resultado del último escaneo, que se muestra debajo del cuadro. */
sealed interface ScanFeedback {
    val seq: Long
    data class Added(val product: Product, val quantityInCart: Double, override val seq: Long) : ScanFeedback
    data class Unknown(val code: String, val isQr: Boolean, override val seq: Long) : ScanFeedback
}

data class CreateOrderState(
    val catalog: CatalogData = CatalogData(),
    val cart: OrderCart = OrderCart(),
    val payment: PaymentMethod = PaymentMethod.CASH,
    val scanMode: ScanMode? = null,
    val feedback: ScanFeedback? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class CreateOrderViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    source: CatalogSource,
    private val createOrder: CreateOrderUseCase,
    private val clock: Clock,
) : BaseViewModel() {

    val storeId = savedState.toRoute<CreateOrderRoute>().storeId

    private val cart = MutableStateFlow(savedState.get<OrderCart>("cartDraft") ?: OrderCart())
    private val payment = MutableStateFlow(savedState.get<PaymentMethod>("paymentDraft") ?: PaymentMethod.CASH)
    private val scanMode = MutableStateFlow<ScanMode?>(null)
    private val feedback = MutableStateFlow<ScanFeedback?>(null)
    private val saving = MutableStateFlow(false)
    /** Un segundo entre dos lecturas; un producto que sigue a la vista no se vuelve a sumar. */
    private val debouncer = ScanDebouncer(pauseMs = 1_000, visibleMs = 1_500)
    private var seq = 0L

    private val catalog = source.observe(storeId).stateIn(viewModelScope, SharingStarted.Eagerly, CatalogData())

    val state: StateFlow<CreateOrderState> = combine(
        catalog, cart, payment, combine(scanMode, feedback) { mode, fb -> mode to fb }, saving,
    ) { c, items, pay, (mode, fb), busy ->
        CreateOrderState(c, items, pay, mode, fb, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateOrderState())

    init {
        viewModelScope.launch { cart.collect { savedState["cartDraft"] = it } }
        viewModelScope.launch { payment.collect { savedState["paymentDraft"] = it } }
    }

    fun openScanner(mode: ScanMode) {
        if (saving.value) return
        debouncer.reset()
        feedback.value = null
        scanMode.value = mode
    }

    fun closeScanner() {
        scanMode.value = null
    }

    /** Lectura continua: el producto se busca en la base local, así funciona sin conexión. */
    fun onScanned(code: ScannedCode) {
        if (saving.value) return
        if (!debouncer.accept(code.value, clock.now())) return
        val data = catalog.value
        val product = data.products.firstOrNull { it.matchesCode(code.value) }
        seq++
        if (product == null) {
            feedback.value = ScanFeedback.Unknown(code.value, code.isQr, seq)
            return
        }
        cart.update { it.addProduct(product, data.categoryName(product.categoryId)) }
        val qty = cart.value.lines.first { it.item.productId == product.id }.item.quantity
        feedback.value = ScanFeedback.Added(product, qty, seq)
    }

    fun addProduct(product: Product) {
        if (saving.value) return
        cart.update { it.addProduct(product, catalog.value.categoryName(product.categoryId)) }
    }

    /** Ítem manual: detalle, categoría, precio y cantidad. No toca el inventario. */
    fun addManual(description: String, categoryId: String?, unitPriceCents: Long, quantity: Double, unit: MeasureUnit) {
        if (saving.value) return
        cart.update {
            it.addManual(description, categoryId, catalog.value.categoryName(categoryId), unitPriceCents, quantity, unit)
        }
        if (scanMode.value != null) feedback.value = null
    }

    fun setQuantity(key: String, quantity: Double) {
        if (saving.value || !quantity.isFinite() || quantity <= 0) return
        val line = cart.value.lines.firstOrNull { it.key == key } ?: return
        if (!line.item.unit.allowsDecimals && quantity % 1.0 != 0.0) return
        cart.update { it.setQuantity(key, quantity) }
    }
    fun remove(key: String) { if (!saving.value) cart.update { it.remove(key) } }
    fun restoreLine(line: CartLine) {
        if (!saving.value) cart.update { current ->
            if (current.lines.any { it.key == line.key }) current else current.copy(lines = current.lines + line)
        }
    }
    fun setPayment(method: PaymentMethod) {
        if (saving.value) return
        payment.value = method
    }

    fun clear() {
        if (saving.value) return
        cart.value = OrderCart()
        feedback.value = null
    }

    fun submit() {
        if (saving.value || cart.value.isEmpty) return
        val submittedCart = cart.value
        val submittedPayment = payment.value
        saving.value = true
        viewModelScope.launch {
            try {
                createOrder(storeId, submittedCart, submittedPayment, catalog.value.header.currency)
                cart.value = OrderCart()
                payment.value = PaymentMethod.CASH
                feedback.value = null
                message("Venta registrada. El número de orden se asigna al sincronizar.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.userMessage())
            } finally {
                saving.value = false
            }
        }
    }
}

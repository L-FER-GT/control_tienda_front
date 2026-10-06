package com.lfergt.controltienda.feature.orders

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.port.Clock
import com.lfergt.controltienda.domain.usecase.CreateOrderUseCase
import com.lfergt.controltienda.feature.catalog.CatalogSource
import com.lfergt.controltienda.feature.scanner.ScanMode
import com.lfergt.controltienda.feature.scanner.ScannedCode
import com.lfergt.controltienda.testing.FakeAuthRepository
import com.lfergt.controltienda.testing.FakeCatalogRepository
import com.lfergt.controltienda.testing.FakeOrderRepository
import com.lfergt.controltienda.testing.FakeStoreRepository
import com.lfergt.controltienda.testing.MainDispatcherRule
import com.lfergt.controltienda.testing.testProduct
import com.lfergt.controltienda.domain.usecase.OrderCart
import com.lfergt.controltienda.ui.common.StoreContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Rule
import org.junit.Test

class CreateOrderViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private var now = 0L
    private val orders = FakeOrderRepository()
    private val catalog = FakeCatalogRepository(
        products = listOf(
            testProduct("leche", "Leche Gloria", price = 450, barcode = "7751271011324", cost = 380),
            testProduct("pan", "Pan francés", price = 30, qr = "QR-PAN"),
            testProduct("queso", "Queso fresco", price = 2_400).copy(unit = MeasureUnit.KG),
        ),
    )

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle(mapOf("storeId" to "s1"))): CreateOrderViewModel {
        val stores = FakeStoreRepository(access = StoreAccess(StoreRole.EMPLOYEE, emptySet(), active = true))
        val context = StoreContext(stores, FakeAuthRepository())
        return CreateOrderViewModel(
            savedState,
            CatalogSource(context, catalog),
            CreateOrderUseCase(orders, Clock { now }),
            Clock { now },
        )
    }

    @Test
    fun `escanear un código registrado agrega el producto y muestra nombre y precio`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.openScanner(ScanMode.BARCODE)
            vm.onScanned(ScannedCode("7751271011324", isQr = false))
            val state = expectMostRecentItem()
            assertEquals(1, state.cart.itemCount)
            val feedback = state.feedback as ScanFeedback.Added
            assertEquals("Leche Gloria", feedback.product.name)
            assertEquals(450L, state.cart.totalCents)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la lectura continua no duplica, pero volver a enfocar suma una unidad`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.openScanner(ScanMode.QR)
            now = 0; vm.onScanned(ScannedCode("QR-PAN", isQr = true))
            now = 400; vm.onScanned(ScannedCode("QR-PAN", isQr = true))
            now = 3_000; vm.onScanned(ScannedCode("QR-PAN", isQr = true))
            val state = expectMostRecentItem()
            assertEquals(2.0, state.cart.lines.single().item.quantity, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entre dos lecturas hay al menos un segundo, aunque sean productos distintos`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.openScanner(ScanMode.ANY)
            now = 0; vm.onScanned(ScannedCode("7751271011324", isQr = false))
            now = 500; vm.onScanned(ScannedCode("QR-PAN", isQr = true))
            assertEquals(1, expectMostRecentItem().cart.itemCount)
            now = 1_000; vm.onScanned(ScannedCode("QR-PAN", isQr = true))
            val state = expectMostRecentItem()
            assertEquals(2, state.cart.itemCount)
            assertEquals(1.0, state.cart.lines.first { it.item.productId == "leche" }.item.quantity, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un código desconocido no modifica la orden`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.openScanner(ScanMode.BARCODE)
            vm.onScanned(ScannedCode("0000000000000", isQr = false))
            val state = expectMostRecentItem()
            assertTrue(state.cart.isEmpty)
            assertTrue(state.feedback is ScanFeedback.Unknown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `registrar la venta guarda la orden con ítems manuales y limpia el carrito`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.openScanner(ScanMode.BARCODE)
            vm.onScanned(ScannedCode("7751271011324", isQr = false))
            vm.addManual("Bolsa de hielo", null, 250, 2.0, MeasureUnit.UNIT)
            vm.setPayment(PaymentMethod.YAPE)
            vm.submit()
            val state = expectMostRecentItem()
            assertTrue(state.cart.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
        val draft = orders.created.single()
        assertEquals(950L, draft.totalCents)
        assertEquals(PaymentMethod.YAPE, draft.paymentMethod)
        assertEquals("PEN", draft.currency)
        assertTrue(draft.items.last().manual)
        assertEquals(380L, draft.items.first().unitCostCents)
        vm.messages.test {
            assertTrue(awaitItem().startsWith("Venta registrada"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `se vende un producto sin código buscándolo por nombre y conserva su identidad`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.addProduct(catalog.products.value.first { it.id == "queso" })
            val line = expectMostRecentItem().cart.lines.single()
            assertEquals("queso", line.item.productId)
            assertFalse(line.item.manual)
            assertEquals(2_400L, line.item.unitPriceCents)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la cantidad exacta acepta decimales solo si la unidad lo permite`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.addProduct(catalog.products.value.first { it.id == "queso" })
            vm.addProduct(catalog.products.value.first { it.id == "pan" })
            val lines = expectMostRecentItem().cart.lines
            val queso = lines.first { it.item.productId == "queso" }.key
            val pan = lines.first { it.item.productId == "pan" }.key
            vm.setQuantity(queso, 0.375)
            vm.setQuantity(pan, 36.0)
            vm.setQuantity(pan, 2.5)
            vm.setQuantity(pan, 0.0)
            val state = expectMostRecentItem()
            assertEquals(0.375, state.cart.lines.first { it.key == queso }.item.quantity, 0.0)
            assertEquals(36.0, state.cart.lines.first { it.key == pan }.item.quantity, 0.0)
            assertEquals(900L + 1_080L, state.cart.totalCents)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deshacer devuelve la línea quitada con su cantidad`() = runTest {
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.addProduct(catalog.products.value.first { it.id == "pan" })
            val line = expectMostRecentItem().cart.lines.single()
            vm.setQuantity(line.key, 12.0)
            val removed = expectMostRecentItem().cart.lines.single()
            vm.remove(removed.key)
            assertTrue(expectMostRecentItem().cart.isEmpty)
            vm.restoreLine(removed)
            vm.restoreLine(removed)
            val restored = expectMostRecentItem().cart.lines.single()
            assertEquals(12.0, restored.item.quantity, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mientras se registra la venta el carrito no cambia y lo guardado coincide con lo confirmado`() = runTest {
        val gate = CompletableDeferred<Unit>()
        orders.gate = gate
        val vm = viewModel()
        vm.state.test {
            skipItems(1)
            vm.addProduct(catalog.products.value.first { it.id == "leche" })
            vm.setPayment(PaymentMethod.YAPE)
            vm.submit()
            assertTrue(expectMostRecentItem().saving)
            val key = vm.state.value.cart.lines.single().key
            vm.addProduct(catalog.products.value.first { it.id == "pan" })
            vm.setQuantity(key, 5.0)
            vm.remove(key)
            vm.clear()
            vm.setPayment(PaymentMethod.CASH)
            vm.submit()
            val during = vm.state.value
            assertEquals(1, during.cart.itemCount)
            assertEquals(1.0, during.cart.lines.single().item.quantity, 0.0)
            assertEquals(PaymentMethod.YAPE, during.payment)
            gate.complete(Unit)
            val after = expectMostRecentItem()
            assertFalse(after.saving)
            assertTrue(after.cart.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
        val draft = orders.created.single()
        assertEquals(listOf("leche"), draft.items.map { it.productId })
        assertEquals(PaymentMethod.YAPE, draft.paymentMethod)
    }

    @Test
    fun `el carrito y el pago se restauran tras recrear el proceso`() = runTest {
        val saved = SavedStateHandle(mapOf("storeId" to "s1"))
        val vm = viewModel(saved)
        vm.state.test {
            skipItems(1)
            vm.addProduct(catalog.products.value.first { it.id == "queso" })
            vm.setQuantity(expectMostRecentItem().cart.lines.single().key, 0.375)
            vm.addManual("Bolsa de hielo", null, 250, 2.0, MeasureUnit.UNIT)
            vm.setPayment(PaymentMethod.CARD)
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        // Simula el Bundle: los valores guardados deben sobrevivir a la serialización.
        fun roundTrip(value: Any?): Any? {
            val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray()
            return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        }
        val restoredHandle = SavedStateHandle(
            mapOf(
                "storeId" to "s1",
                "cartDraft" to roundTrip(saved.get<OrderCart>("cartDraft")),
                "paymentDraft" to roundTrip(saved.get<PaymentMethod>("paymentDraft")),
            ),
        )
        val restored = viewModel(restoredHandle)
        restored.state.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.cart.itemCount)
            assertEquals(0.375, state.cart.lines.first { it.item.productId == "queso" }.item.quantity, 0.0)
            assertTrue(state.cart.lines.last().item.manual)
            assertEquals(PaymentMethod.CARD, state.payment)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una venta inexistente pasa de cargando a no disponible sin quedarse esperando`() = runTest {
        val vm = SaleDetailViewModel(SavedStateHandle(mapOf("storeId" to "s1", "orderId" to "o404")), orders)
        assertEquals(false to null, vm.state.value)
        vm.state.test {
            assertEquals(true to null, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

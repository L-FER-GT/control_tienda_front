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
import com.lfergt.controltienda.ui.common.StoreContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        ),
    )

    private fun viewModel(): CreateOrderViewModel {
        val stores = FakeStoreRepository(access = StoreAccess(StoreRole.EMPLOYEE, emptySet(), active = true))
        val context = StoreContext(stores, FakeAuthRepository())
        return CreateOrderViewModel(
            SavedStateHandle(mapOf("storeId" to "s1")),
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
}

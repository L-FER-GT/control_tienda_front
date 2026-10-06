package com.lfergt.controltienda.feature.purchasing

import androidx.lifecycle.SavedStateHandle
import com.lfergt.controltienda.domain.model.*
import com.lfergt.controltienda.domain.port.*
import com.lfergt.controltienda.domain.usecase.SaveProductUseCase
import com.lfergt.controltienda.domain.usecase.SaveReceptionUseCase
import com.lfergt.controltienda.feature.catalog.CatalogSource
import com.lfergt.controltienda.feature.scanner.ScannedCode
import com.lfergt.controltienda.testing.*
import com.lfergt.controltienda.ui.common.StoreContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReceptionEditorViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()
    private val catalog = FakeCatalogRepository()
    private val receptions = object : ReceptionRepository {
        override fun observeReceptions(storeId: String) = flowOf(emptyList<Reception>())
        override fun observeReception(storeId: String, receptionId: String) = flowOf<Reception?>(null)
        override suspend fun saveReception(storeId: String, draft: ReceptionDraft) = "r1"
        override suspend fun getReceptions(storeId: String, fromMillis: Long, toMillis: Long) = emptyList<Reception>()
    }
    private val suppliers = object : SupplierRepository {
        override fun observeSuppliers(storeId: String) = flowOf(listOf(Supplier.OTHERS))
        override suspend fun saveSupplier(storeId: String, supplier: Supplier) = "s1"
        override suspend fun deleteSupplier(storeId: String, supplierId: String) = Unit
    }
    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle(mapOf("storeId" to "s1", "receptionId" to null))) = ReceptionEditorViewModel(
        savedState,
        CatalogSource(StoreContext(FakeStoreRepository(access = StoreAccess(StoreRole.OWNER, emptySet(), true)), FakeAuthRepository()), catalog),
        suppliers, receptions, SaveReceptionUseCase(receptions), SaveProductUseCase(catalog), Clock { 1000L },
    )

    @Test fun `un QR numerico se conserva como QR al crear desde recepcion`() = runTest {
        val vm = viewModel()
        val code = ScannedCode("77512345", isQr = true)
        vm.onScanned(code)
        assertEquals(code, vm.form.value.unknownCode)
        vm.quickCreate("Arroz", 250, 100, code)
        val saved = catalog.savedProducts.single()
        assertEquals(code.value, saved.qrCode)
        assertNull(saved.barcode)
        assertEquals("p", vm.form.value.lines.single().productId)
        assertNull(vm.form.value.unknownCode)
    }

    @Test fun `un codigo de barras alfanumerico no se pierde al crear desde recepcion`() = runTest {
        val vm = viewModel()
        val code = ScannedCode("AB-123", isQr = false)
        vm.onScanned(code)
        vm.quickCreate("Caja", 250, null, code)
        val saved = catalog.savedProducts.single()
        assertEquals(code.value, saved.barcode)
        assertNull(saved.qrCode)
    }

    @Test fun `doble toque en crear y agregar produce un solo producto`() = runTest {
        val gate = CompletableDeferred<Unit>()
        catalog.saveGate = gate
        val vm = viewModel()
        val code = ScannedCode("7750000000001", isQr = false)
        vm.onScanned(code)
        vm.quickCreate("Arroz", 250, 100, code)
        assertTrue(vm.form.value.creatingProduct)
        vm.quickCreate("Arroz", 250, 100, code)
        gate.complete(Unit)
        assertEquals(1, catalog.savedProducts.size)
        assertEquals(1, vm.form.value.lines.size)
        assertFalse(vm.form.value.creatingProduct)
    }

    @Test fun `no se aceptan mas de diez fotos de factura`() = runTest {
        val vm = viewModel()
        repeat(12) { vm.addPhoto("content://foto/$it") }
        assertEquals(10, vm.form.value.newPhotos.size)
    }

    @Test fun `el borrador se restaura tras recrear el proceso y sigue marcado como modificado`() = runTest {
        catalog.products.value = listOf(testProduct("arroz", "Arroz"))
        val saved = SavedStateHandle(mapOf("storeId" to "s1", "receptionId" to null))
        val vm = viewModel(saved)
        assertFalse(vm.dirty)
        vm.addProduct(catalog.products.value.single())
        vm.addPhoto("content://foto/1")
        assertTrue(vm.dirty)
        val restoredHandle = SavedStateHandle(
            mapOf(
                "storeId" to "s1", "receptionId" to null,
                "editorDraft" to roundTrip(saved.get<ReceptionForm>("editorDraft")),
                "editorBaseline" to roundTrip(saved.get<ReceptionForm>("editorBaseline")),
            ),
        )
        val restored = viewModel(restoredHandle)
        assertEquals("arroz", restored.form.value.lines.single().productId)
        assertEquals(listOf("content://foto/1"), restored.form.value.newPhotos)
        assertTrue(restored.dirty)
    }

    private fun roundTrip(value: Any?): Any? {
        val bytes = java.io.ByteArrayOutputStream().also { java.io.ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray()
        return java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes)).use { it.readObject() }
    }
}

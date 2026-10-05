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
    private fun viewModel() = ReceptionEditorViewModel(
        SavedStateHandle(mapOf("storeId" to "s1", "receptionId" to null)),
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
}

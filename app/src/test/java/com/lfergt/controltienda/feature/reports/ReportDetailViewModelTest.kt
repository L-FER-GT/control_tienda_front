package com.lfergt.controltienda.feature.reports

import androidx.lifecycle.SavedStateHandle
import com.lfergt.controltienda.domain.model.ExportFormat
import com.lfergt.controltienda.domain.model.ExportedFile
import com.lfergt.controltienda.domain.model.PeriodGrouping
import com.lfergt.controltienda.domain.model.Reception
import com.lfergt.controltienda.domain.model.ReceptionDraft
import com.lfergt.controltienda.domain.model.ReportTable
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.port.Clock
import com.lfergt.controltienda.domain.port.ReceptionRepository
import com.lfergt.controltienda.domain.port.ReportExporter
import com.lfergt.controltienda.domain.usecase.GenerateReportUseCase
import com.lfergt.controltienda.feature.catalog.CatalogSource
import com.lfergt.controltienda.testing.FakeAuthRepository
import com.lfergt.controltienda.testing.FakeCatalogRepository
import com.lfergt.controltienda.testing.FakeOrderRepository
import com.lfergt.controltienda.testing.FakeStoreRepository
import com.lfergt.controltienda.testing.MainDispatcherRule
import com.lfergt.controltienda.ui.common.StoreContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ReportDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val receptions = object : ReceptionRepository {
        override fun observeReceptions(storeId: String) = flowOf(emptyList<Reception>())
        override fun observeReception(storeId: String, receptionId: String) = flowOf<Reception?>(null)
        override suspend fun saveReception(storeId: String, draft: ReceptionDraft) = "r1"
        override suspend fun getReceptions(storeId: String, fromMillis: Long, toMillis: Long) = emptyList<Reception>()
    }
    private val exporter = object : ReportExporter {
        override suspend fun export(table: ReportTable, format: ExportFormat) = ExportedFile("file://r", format.mimeType, "r")
    }

    // En JVM toRoute no decodifica el tipo (Bundle simulado): el VM usa "Ventas por periodo".
    private fun viewModel() = ReportDetailViewModel(
        SavedStateHandle(mapOf("storeId" to "s1", "type" to "SALES_BY_PERIOD")),
        CatalogSource(StoreContext(FakeStoreRepository(access = StoreAccess(StoreRole.OWNER, emptySet(), true)), FakeAuthRepository()), FakeCatalogRepository()),
        GenerateReportUseCase(FakeOrderRepository(), receptions, Clock { 0L }),
        exporter,
    )

    @Test
    fun `el reporte se genera solo al abrir`() = runTest {
        val vm = viewModel()
        assertNotNull(vm.state.value.table)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `cambiar un filtro vuelve a generar el reporte con el nuevo periodo`() = runTest {
        val vm = viewModel()
        vm.setPreset(RangePreset.TODAY)
        assertEquals(RangePreset.TODAY, vm.state.value.preset)
        assertNotNull(vm.state.value.table)
        vm.setGrouping(PeriodGrouping.MONTH)
        assertEquals(PeriodGrouping.MONTH, vm.state.value.grouping)
        assertNotNull(vm.state.value.table)
    }
}

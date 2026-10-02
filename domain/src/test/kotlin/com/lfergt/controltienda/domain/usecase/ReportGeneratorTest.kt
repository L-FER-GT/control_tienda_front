package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.item
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.PeriodGrouping
import com.lfergt.controltienda.domain.model.ReportCell
import com.lfergt.controltienda.domain.model.ReportFilter
import com.lfergt.controltienda.domain.model.ReportType
import com.lfergt.controltienda.domain.order
import com.lfergt.controltienda.domain.product
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportGeneratorTest {

    private val zone = ZoneId.of("America/Lima")
    private val generator = ReportGenerator(zone)

    private fun millis(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDate.of(y, m, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private val from = millis(2026, 9, 1, 0)
    private val to = millis(2026, 10, 1, 0)

    private val orders = listOf(
        order("1", millis(2026, 9, 1), listOf(item("a", 2.0, 500, cost = 300)), by = "u1", byName = "Ana"),
        order("2", millis(2026, 9, 1), listOf(item("b", 1.0, 1000)), by = "u2", byName = "Luis", payment = PaymentMethod.YAPE),
        order("3", millis(2026, 9, 9), listOf(item("a", 1.0, 600, cost = 350), item(null, 1.0, 200)), by = "u1", byName = "Ana"),
        order("fuera", millis(2026, 10, 2), listOf(item("a", 50.0, 500))),
    )

    private fun run(type: ReportType, grouping: PeriodGrouping = PeriodGrouping.DAY) =
        generator.generate(ReportFilter(type, from, to, grouping), ReportData("Bodega", "PEN", orders), now = 0)

    @Test
    fun `ventas por día agrupa y excluye órdenes fuera del rango`() {
        val table = run(ReportType.SALES_BY_PERIOD)
        assertEquals(2, table.rows.size)
        assertEquals(ReportCell.Text("01/09/2026"), table.rows[0][0])
        assertEquals(ReportCell.Amount(2000), table.rows[0][2])
        assertEquals(ReportCell.Amount(2800), table.totals!![2])
    }

    @Test
    fun `ventas por mes usa el nombre del mes`() {
        val table = run(ReportType.SALES_BY_PERIOD, PeriodGrouping.MONTH)
        assertEquals(ReportCell.Text("Septiembre 2026"), table.rows.single()[0])
    }

    @Test
    fun `ventas por empleado ordena de mayor a menor`() {
        val table = run(ReportType.SALES_BY_EMPLOYEE)
        assertEquals(ReportCell.Text("Ana"), table.rows[0][0])
        assertEquals(ReportCell.Amount(1800), table.rows[0][2])
    }

    @Test
    fun `productos más vendidos por cantidad`() {
        val table = run(ReportType.TOP_PRODUCTS)
        assertEquals(ReportCell.Text("a"), table.rows[0][1])
        assertEquals(ReportCell.Number(3.0, 2), table.rows[0][2])
    }

    @Test
    fun `ganancia usa el costo guardado en cada venta y separa ítems sin costo`() {
        val table = run(ReportType.PROFIT)
        // a: ventas 1000 + 600 = 1600; costo 600 + 350 = 950; ganancia 650
        assertEquals(ReportCell.Amount(650), table.rows[0][4])
        assertEquals(ReportCell.Text("Ítems sin costo registrado"), table.rows.last()[0])
        assertEquals(ReportCell.Amount(650), table.totals!![4])
    }

    @Test
    fun `inventario valorizado ignora stock ilimitado y no valora stock negativo`() {
        val products = listOf(
            product("a", price = 500, cost = 300, stock = 10.0),
            product("b", price = 100, stock = -3.0),
            product("c", price = 100, stock = null),
        )
        val table = generator.generate(
            ReportFilter(ReportType.INVENTORY_VALUE, 0, 1),
            ReportData("Bodega", "PEN", products = products),
            now = 0,
        )
        assertEquals(2, table.rows.size)
        assertEquals(ReportCell.Amount(3000), table.totals!![3])
        assertEquals(ReportCell.Amount(5000), table.totals[5])
    }
}

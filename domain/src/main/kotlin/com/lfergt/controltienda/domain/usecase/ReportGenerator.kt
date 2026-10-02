package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.ColumnKind
import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.domain.model.OrderItem
import com.lfergt.controltienda.domain.model.PeriodGrouping
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.Reception
import com.lfergt.controltienda.domain.model.ReportCell
import com.lfergt.controltienda.domain.model.ReportColumn
import com.lfergt.controltienda.domain.model.ReportFilter
import com.lfergt.controltienda.domain.model.ReportTable
import com.lfergt.controltienda.domain.model.ReportType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.roundToLong

data class ReportData(
    val storeName: String,
    val currency: String,
    val orders: List<Order> = emptyList(),
    val receptions: List<Reception> = emptyList(),
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
)

/** Construye las tablas de los reportes a partir de los datos crudos. Lógica pura y testeable. */
class ReportGenerator(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val months = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
    )

    fun generate(filter: ReportFilter, data: ReportData, now: Long): ReportTable {
        val orders = data.orders.filter { it.createdAt >= filter.fromMillis && it.createdAt < filter.toMillis }
        val receptions = data.receptions.filter { it.receivedAt >= filter.fromMillis && it.receivedAt < filter.toMillis }
        val subtitle = if (filter.type.needsDateRange) "${data.storeName} · ${rangeLabel(filter)}" else data.storeName
        val (columns, rows, totals) = when (filter.type) {
            ReportType.SALES_BY_PERIOD -> salesByPeriod(orders, filter.grouping)
            ReportType.SALES_BY_EMPLOYEE -> salesByEmployee(orders)
            ReportType.SALES_BY_PAYMENT_METHOD -> salesByPayment(orders)
            ReportType.TOP_PRODUCTS -> topProducts(orders, filter.limit)
            ReportType.SALES_BY_CATEGORY -> salesByCategory(orders, data.categories)
            ReportType.PROFIT -> profit(orders)
            ReportType.PURCHASES_BY_SUPPLIER -> purchasesBySupplier(receptions)
            ReportType.INVENTORY_VALUE -> inventoryValue(data.products)
        }
        return ReportTable(
            title = filter.type.title,
            subtitle = subtitle,
            currency = data.currency,
            columns = columns,
            rows = rows,
            totals = totals,
            generatedAt = now,
        )
    }

    private data class Built(
        val columns: List<ReportColumn>,
        val rows: List<List<ReportCell>>,
        val totals: List<ReportCell>?,
    )

    // ---------------------------------------------------------------- ventas

    private fun salesByPeriod(orders: List<Order>, grouping: PeriodGrouping): Built {
        val groups = orders.groupBy { periodStart(it.createdAt, grouping) }.toSortedMap()
        val rows = groups.map { (start, list) ->
            val total = list.sumOf { it.totalCents }
            listOf(
                ReportCell.Text(periodLabel(start, grouping)),
                ReportCell.Number(list.size.toDouble()),
                ReportCell.Amount(total),
                ReportCell.Amount(average(total, list.size)),
            )
        }
        val total = orders.sumOf { it.totalCents }
        return Built(
            columns = listOf(
                ReportColumn("Periodo", ColumnKind.TEXT),
                ReportColumn("Órdenes", ColumnKind.NUMBER),
                ReportColumn("Total", ColumnKind.MONEY),
                ReportColumn("Ticket promedio", ColumnKind.MONEY),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Total"),
                ReportCell.Number(orders.size.toDouble()),
                ReportCell.Amount(total),
                ReportCell.Amount(average(total, orders.size)),
            ),
        )
    }

    private fun salesByEmployee(orders: List<Order>): Built {
        val rows = orders.groupBy { it.createdBy }
            .map { (_, list) -> Triple(list.first().createdByName, list.size, list.sumOf { it.totalCents }) }
            .sortedByDescending { it.third }
            .map { (name, count, total) ->
                listOf(
                    ReportCell.Text(name),
                    ReportCell.Number(count.toDouble()),
                    ReportCell.Amount(total),
                    ReportCell.Amount(average(total, count)),
                )
            }
        val total = orders.sumOf { it.totalCents }
        return Built(
            columns = listOf(
                ReportColumn("Empleado", ColumnKind.TEXT),
                ReportColumn("Órdenes", ColumnKind.NUMBER),
                ReportColumn("Total", ColumnKind.MONEY),
                ReportColumn("Ticket promedio", ColumnKind.MONEY),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Total"),
                ReportCell.Number(orders.size.toDouble()),
                ReportCell.Amount(total),
                ReportCell.Amount(average(total, orders.size)),
            ),
        )
    }

    private fun salesByPayment(orders: List<Order>): Built {
        val grand = orders.sumOf { it.totalCents }
        val rows = orders.groupBy { it.paymentMethod }
            .map { (method, list) -> Triple(method.label, list.size, list.sumOf { it.totalCents }) }
            .sortedByDescending { it.third }
            .map { (label, count, total) ->
                listOf(
                    ReportCell.Text(label),
                    ReportCell.Number(count.toDouble()),
                    ReportCell.Amount(total),
                    ReportCell.Number(percent(total, grand), 1),
                )
            }
        return Built(
            columns = listOf(
                ReportColumn("Método de pago", ColumnKind.TEXT),
                ReportColumn("Órdenes", ColumnKind.NUMBER),
                ReportColumn("Total", ColumnKind.MONEY),
                ReportColumn("% del total", ColumnKind.NUMBER),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Total"),
                ReportCell.Number(orders.size.toDouble()),
                ReportCell.Amount(grand),
                ReportCell.Number(if (grand > 0) 100.0 else 0.0, 1),
            ),
        )
    }

    private data class ItemAgg(val name: String, val quantity: Double, val totalCents: Long)

    private fun itemKey(item: OrderItem) = item.productId ?: "manual:${item.description.lowercase()}"

    private fun itemName(item: OrderItem) = if (item.manual) "${item.description} (manual)" else item.description

    private fun topProducts(orders: List<Order>, limit: Int): Built {
        val aggregated = orders.flatMap { it.items }
            .groupBy(::itemKey)
            .map { (_, items) ->
                ItemAgg(itemName(items.last()), items.sumOf { it.quantity }, items.sumOf { it.subtotalCents })
            }
            .sortedWith(compareByDescending<ItemAgg> { it.quantity }.thenByDescending { it.totalCents })
            .take(limit.coerceAtLeast(1))
        val rows = aggregated.mapIndexed { index, agg ->
            listOf(
                ReportCell.Number((index + 1).toDouble()),
                ReportCell.Text(agg.name),
                ReportCell.Number(agg.quantity, 2),
                ReportCell.Amount(agg.totalCents),
            )
        }
        return Built(
            columns = listOf(
                ReportColumn("#", ColumnKind.NUMBER),
                ReportColumn("Producto", ColumnKind.TEXT),
                ReportColumn("Cantidad", ColumnKind.NUMBER),
                ReportColumn("Total", ColumnKind.MONEY),
            ),
            rows = rows,
            totals = null,
        )
    }

    private fun salesByCategory(orders: List<Order>, categories: List<Category>): Built {
        val items = orders.flatMap { it.items }
        val grand = items.sumOf { it.subtotalCents }
        val rows = items.groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val name = CategoryRules.nameOf(categoryId, categories)
                    ?: list.firstNotNullOfOrNull { it.categoryName }
                    ?: "Sin categoría"
                Triple(name, list.sumOf { it.quantity }, list.sumOf { it.subtotalCents })
            }
            .sortedByDescending { it.third }
            .map { (name, qty, total) ->
                listOf(
                    ReportCell.Text(name),
                    ReportCell.Number(qty, 2),
                    ReportCell.Amount(total),
                    ReportCell.Number(percent(total, grand), 1),
                )
            }
        return Built(
            columns = listOf(
                ReportColumn("Categoría", ColumnKind.TEXT),
                ReportColumn("Cantidad", ColumnKind.NUMBER),
                ReportColumn("Total", ColumnKind.MONEY),
                ReportColumn("% del total", ColumnKind.NUMBER),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Total"),
                ReportCell.Number(items.sumOf { it.quantity }, 2),
                ReportCell.Amount(grand),
                ReportCell.Number(if (grand > 0) 100.0 else 0.0, 1),
            ),
        )
    }

    /** Usa el costo guardado en cada venta (el costo vigente al momento de vender). */
    private fun profit(orders: List<Order>): Built {
        val items = orders.flatMap { it.items }
        val withCost = items.filter { it.unitCostCents != null }
        val withoutCost = items.filter { it.unitCostCents == null }

        val rows = withCost.groupBy(::itemKey).map { (_, list) ->
            val revenue = list.sumOf { it.subtotalCents }
            val cost = list.sumOf { (it.unitCostCents!! * it.quantity).roundToLong() }
            val gain = revenue - cost
            listOf(
                ReportCell.Text(itemName(list.last())),
                ReportCell.Number(list.sumOf { it.quantity }, 2),
                ReportCell.Amount(revenue),
                ReportCell.Amount(cost),
                ReportCell.Amount(gain),
                ReportCell.Number(percent(gain, revenue), 1),
            ) to gain
        }.sortedByDescending { it.second }.map { it.first }.toMutableList()

        val noCostRevenue = withoutCost.sumOf { it.subtotalCents }
        if (withoutCost.isNotEmpty()) {
            rows += listOf(
                ReportCell.Text("Ítems sin costo registrado"),
                ReportCell.Number(withoutCost.sumOf { it.quantity }, 2),
                ReportCell.Amount(noCostRevenue),
                ReportCell.Text("—"),
                ReportCell.Text("—"),
                ReportCell.Text("—"),
            )
        }
        val revenueKnown = withCost.sumOf { it.subtotalCents }
        val costKnown = withCost.sumOf { (it.unitCostCents!! * it.quantity).roundToLong() }
        val gainKnown = revenueKnown - costKnown
        return Built(
            columns = listOf(
                ReportColumn("Producto", ColumnKind.TEXT),
                ReportColumn("Cantidad", ColumnKind.NUMBER),
                ReportColumn("Ventas", ColumnKind.MONEY),
                ReportColumn("Costo", ColumnKind.MONEY),
                ReportColumn("Ganancia", ColumnKind.MONEY),
                ReportColumn("Margen %", ColumnKind.NUMBER),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Total (con costo)"),
                ReportCell.Number(withCost.sumOf { it.quantity }, 2),
                ReportCell.Amount(revenueKnown),
                ReportCell.Amount(costKnown),
                ReportCell.Amount(gainKnown),
                ReportCell.Number(percent(gainKnown, revenueKnown), 1),
            ),
        )
    }

    // ---------------------------------------------------------------- compras e inventario

    private fun purchasesBySupplier(receptions: List<Reception>): Built {
        fun amount(r: Reception) = r.invoiceTotalCents ?: r.lines.sumOf { it.subtotalCents ?: 0 }
        val rows = receptions.groupBy { it.supplierId }
            .map { (_, list) -> Triple(list.first().supplierName, list.size, list.sumOf(::amount)) }
            .sortedByDescending { it.third }
            .map { (name, count, total) ->
                listOf(ReportCell.Text(name), ReportCell.Number(count.toDouble()), ReportCell.Amount(total))
            }
        return Built(
            columns = listOf(
                ReportColumn("Proveedor", ColumnKind.TEXT),
                ReportColumn("Recepciones", ColumnKind.NUMBER),
                ReportColumn("Total comprado", ColumnKind.MONEY),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Total"),
                ReportCell.Number(receptions.size.toDouble()),
                ReportCell.Amount(receptions.sumOf(::amount)),
            ),
        )
    }

    private fun inventoryValue(products: List<Product>): Built {
        val tracked = products.filter { it.stock != null }.sortedBy { it.name.lowercase() }
        var totalCost = 0L
        var totalSale = 0L
        val rows = tracked.map { p ->
            val qty = max(p.stock!!, 0.0)
            val costValue = p.purchaseCostCents?.let { (it * qty).roundToLong() }
            val saleValue = (p.salePriceCents * qty).roundToLong()
            totalCost += costValue ?: 0
            totalSale += saleValue
            listOf(
                ReportCell.Text(p.name),
                ReportCell.Number(p.stock, 2),
                p.purchaseCostCents?.let { ReportCell.Amount(it) } ?: ReportCell.Text("—"),
                costValue?.let { ReportCell.Amount(it) } ?: ReportCell.Text("—"),
                ReportCell.Amount(p.salePriceCents),
                ReportCell.Amount(saleValue),
            )
        }
        return Built(
            columns = listOf(
                ReportColumn("Producto", ColumnKind.TEXT),
                ReportColumn("Stock", ColumnKind.NUMBER),
                ReportColumn("Costo unit.", ColumnKind.MONEY),
                ReportColumn("Valor al costo", ColumnKind.MONEY),
                ReportColumn("Precio venta", ColumnKind.MONEY),
                ReportColumn("Valor a precio de venta", ColumnKind.MONEY),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Total"),
                ReportCell.Text(""),
                ReportCell.Text(""),
                ReportCell.Amount(totalCost),
                ReportCell.Text(""),
                ReportCell.Amount(totalSale),
            ),
        )
    }

    // ---------------------------------------------------------------- utilidades

    private fun average(total: Long, count: Int): Long = if (count == 0) 0 else total / count

    private fun percent(part: Long, whole: Long): Double = if (whole == 0L) 0.0 else part * 100.0 / whole

    private fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    internal fun periodStart(millis: Long, grouping: PeriodGrouping): LocalDate {
        val date = localDate(millis)
        return when (grouping) {
            PeriodGrouping.DAY -> date
            PeriodGrouping.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            PeriodGrouping.MONTH -> date.withDayOfMonth(1)
        }
    }

    private fun periodLabel(start: LocalDate, grouping: PeriodGrouping): String = when (grouping) {
        PeriodGrouping.DAY -> start.format(dayFormat)
        PeriodGrouping.WEEK -> "Semana del ${start.format(dayFormat)}"
        PeriodGrouping.MONTH -> "${months[start.monthValue - 1]} ${start.year}"
    }

    private fun rangeLabel(filter: ReportFilter): String {
        val from = localDate(filter.fromMillis)
        val to = localDate(filter.toMillis - 1)
        return if (from == to) from.format(dayFormat) else "${from.format(dayFormat)} - ${to.format(dayFormat)}"
    }
}

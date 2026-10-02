package com.lfergt.controltienda.domain.model

enum class ReportType(
    val title: String,
    val description: String,
    val needsDateRange: Boolean,
    val needsGrouping: Boolean = false,
    val needsLimit: Boolean = false,
) {
    SALES_BY_PERIOD("Ventas por periodo", "Total vendido por día, semana o mes", needsDateRange = true, needsGrouping = true),
    SALES_BY_EMPLOYEE("Ventas por empleado", "Órdenes y montos de cada vendedor", needsDateRange = true),
    SALES_BY_PAYMENT_METHOD("Ventas por método de pago", "Efectivo, tarjeta, Yape, Plin…", needsDateRange = true),
    TOP_PRODUCTS("Productos más vendidos", "Ranking por cantidad vendida", needsDateRange = true, needsLimit = true),
    SALES_BY_CATEGORY("Ventas por categoría", "Participación de cada categoría", needsDateRange = true),
    PROFIT("Ganancias", "Ventas menos costo de compra por producto", needsDateRange = true),
    PURCHASES_BY_SUPPLIER("Compras por proveedor", "Recepciones de mercadería por proveedor", needsDateRange = true),
    INVENTORY_VALUE("Inventario valorizado", "Stock actual valorizado al costo y al precio de venta", needsDateRange = false),
}

enum class PeriodGrouping { DAY, WEEK, MONTH }

data class ReportFilter(
    val type: ReportType,
    val fromMillis: Long,
    /** Exclusivo. */
    val toMillis: Long,
    val grouping: PeriodGrouping = PeriodGrouping.DAY,
    val limit: Int = 20,
)

enum class ColumnKind { TEXT, MONEY, NUMBER }

data class ReportColumn(val title: String, val kind: ColumnKind)

sealed interface ReportCell {
    data class Text(val value: String) : ReportCell
    data class Amount(val cents: Long) : ReportCell
    data class Number(val value: Double, val decimals: Int = 0) : ReportCell
}

data class ReportTable(
    val title: String,
    val subtitle: String,
    val currency: String,
    val columns: List<ReportColumn>,
    val rows: List<List<ReportCell>>,
    val totals: List<ReportCell>?,
    val generatedAt: Long,
)

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

data class ExportedFile(val uri: String, val mimeType: String, val fileName: String)

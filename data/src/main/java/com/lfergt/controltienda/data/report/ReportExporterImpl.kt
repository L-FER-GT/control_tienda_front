package com.lfergt.controltienda.data.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.lfergt.controltienda.domain.model.ColumnKind
import com.lfergt.controltienda.domain.model.ExportFormat
import com.lfergt.controltienda.domain.model.ExportedFile
import com.lfergt.controltienda.domain.model.Money
import com.lfergt.controltienda.domain.model.ReportCell
import com.lfergt.controltienda.domain.model.ReportTable
import com.lfergt.controltienda.domain.port.ReportExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Exporta un reporte a PDF o Excel para compartirlo (WhatsApp, correo, etc.). */
@Singleton
class ReportExporterImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReportExporter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE"))

    override suspend fun export(table: ReportTable, format: ExportFormat): ExportedFile = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val slug = table.title.lowercase(Locale.ROOT)
            .replace(Regex("[áàä]"), "a").replace(Regex("[éèë]"), "e").replace(Regex("[íìï]"), "i")
            .replace(Regex("[óòö]"), "o").replace(Regex("[úùü]"), "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(table.generatedAt))
        val file = File(dir, "$slug-$stamp.${format.extension}")
        when (format) {
            ExportFormat.PDF -> writePdf(table, file)
            ExportFormat.XLSX -> writeXlsx(table, file)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        ExportedFile(uri.toString(), format.mimeType, file.name)
    }

    fun cellText(cell: ReportCell, currency: String): String = when (cell) {
        is ReportCell.Text -> cell.value
        is ReportCell.Amount -> Money.format(cell.cents, currency)
        is ReportCell.Number -> if (cell.decimals == 0) {
            String.format(Locale.US, "%,.0f", cell.value)
        } else {
            String.format(Locale.US, "%,.${cell.decimals}f", cell.value).trimEnd('0').trimEnd('.')
        }
    }

    // ------------------------------------------------------------------ PDF

    private fun writePdf(table: ReportTable, file: File) {
        val landscape = table.columns.size > 4
        val pageWidth = if (landscape) 842 else 595
        val pageHeight = if (landscape) 595 else 842
        val margin = 36f
        val rowHeight = 20f
        val usable = pageWidth - margin * 2

        val title = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(15, 110, 86) }
        val subtitle = Paint().apply { textSize = 10f; color = Color.DKGRAY }
        val header = Paint().apply { textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val body = Paint().apply { textSize = 9.5f; color = Color.BLACK }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val fill = Paint().apply { color = Color.rgb(15, 110, 86) }
        val zebra = Paint().apply { color = Color.rgb(242, 246, 244) }
        val line = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 0.6f }

        // El ancho de cada columna es proporcional al texto más largo (la primera tiene prioridad).
        val weights = table.columns.mapIndexed { index, column ->
            val longest = (table.rows.map { cellText(it[index], table.currency) } + column.title).maxOf { it.length }
            longest.coerceIn(4, if (index == 0) 40 else 18).toFloat()
        }
        val widths = weights.map { it / weights.sum() * usable }

        val document = PdfDocument()
        var pageNumber = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        fun drawRow(cells: List<String>, paint: Paint, background: Paint?) {
            background?.let { page.canvas.drawRect(margin, y - rowHeight + 5, pageWidth - margin, y + 5, it) }
            var x = margin
            cells.forEachIndexed { i, text ->
                val alignRight = table.columns[i].kind != ColumnKind.TEXT
                val clipped = ellipsize(text, paint, widths[i] - 8)
                val tx = if (alignRight) x + widths[i] - 4 - paint.measureText(clipped) else x + 4
                page.canvas.drawText(clipped, tx, y, paint)
                x += widths[i]
            }
            page.canvas.drawLine(margin, y + 5, pageWidth - margin, y + 5, line)
            y += rowHeight
        }

        fun newPage() {
            if (pageNumber > 0) document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin + 18
            if (pageNumber == 1) {
                page.canvas.drawText(table.title, margin, y, title)
                y += 18
                page.canvas.drawText(table.subtitle, margin, y, subtitle)
                y += 14
                page.canvas.drawText("Generado: ${dateFormat.format(Date(table.generatedAt))}", margin, y, subtitle)
                y += 24
            }
            drawRow(table.columns.map { it.title }, header, fill)
            page.canvas.drawText("Página $pageNumber", pageWidth - margin - 50, pageHeight - 18f, subtitle)
        }

        newPage()
        if (table.rows.isEmpty()) {
            page.canvas.drawText("Sin datos para los filtros seleccionados.", margin + 4, y, body)
            y += rowHeight
        }
        table.rows.forEachIndexed { index, row ->
            if (y > pageHeight - margin - rowHeight) newPage()
            drawRow(row.map { cellText(it, table.currency) }, body, if (index % 2 == 1) zebra else null)
        }
        table.totals?.let { totals ->
            if (y > pageHeight - margin - rowHeight) newPage()
            drawRow(totals.map { cellText(it, table.currency) }, bold, zebra)
        }
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    // ------------------------------------------------------------------ Excel (.xlsx mínimo, sin librerías)

    private fun writeXlsx(table: ReportTable, file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""",
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Reporte" sheetId="1" r:id="rId1"/></sheets>
</workbook>""",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""",
            )
            // Estilos: 0 normal, 1 negrita, 2 moneda, 3 moneda negrita, 4 número, 5 número negrita
            put(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="6">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="4" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
</styleSheet>""",
            )
            put("xl/worksheets/sheet1.xml", sheetXml(table))
        }
    }

    private fun xml(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun sheetXml(table: ReportTable): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols>""")
        table.columns.forEachIndexed { i, _ -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="${if (i == 0) 34 else 16}" customWidth="1"/>""") }
        sb.append("</cols><sheetData>")
        var rowIndex = 0

        fun textCell(ref: String, value: String, bold: Boolean) =
            """<c r="$ref" t="inlineStr" s="${if (bold) 1 else 0}"><is><t xml:space="preserve">${xml(value)}</t></is></c>"""

        fun row(cells: List<String>) {
            rowIndex++
            sb.append("""<row r="$rowIndex">""").append(cells.joinToString("")).append("</row>")
        }

        row(listOf(textCell("A1", table.title, true)))
        row(listOf(textCell("A2", table.subtitle, false)))
        row(listOf(textCell("A3", "Moneda: ${Money.currencySymbol(table.currency)} (${table.currency}) · Generado: ${dateFormat.format(Date(table.generatedAt))}", false)))
        rowIndex++ // fila en blanco
        row(table.columns.mapIndexed { i, c -> textCell("${columnName(i)}${rowIndex + 1}", c.title, true) })

        fun dataRow(cells: List<ReportCell>, bold: Boolean) {
            val r = rowIndex + 1
            row(
                cells.mapIndexed { i, cell ->
                    val ref = "${columnName(i)}$r"
                    when (cell) {
                        is ReportCell.Text -> textCell(ref, cell.value, bold)
                        is ReportCell.Amount -> """<c r="$ref" s="${if (bold) 3 else 2}"><v>${cell.cents / 100.0}</v></c>"""
                        is ReportCell.Number -> """<c r="$ref" s="${if (bold) 5 else 4}"><v>${cell.value}</v></c>"""
                    }
                },
            )
        }
        table.rows.forEach { dataRow(it, bold = false) }
        table.totals?.let { dataRow(it, bold = true) }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }
}

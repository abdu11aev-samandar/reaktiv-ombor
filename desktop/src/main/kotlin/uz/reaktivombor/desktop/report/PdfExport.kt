package uz.reaktivombor.desktop.report

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfPageEventHelper
import com.lowagie.text.pdf.PdfTemplate
import com.lowagie.text.pdf.PdfWriter
import uz.reaktivombor.desktop.core.ExpiryState
import java.awt.Color
import java.io.OutputStream
import java.nio.file.Path

/**
 * Hisobot → .pdf (OpenPDF): A4 albom, DejaVu Sans shrifti faylga joylanadi (Identity-H) — o‘ g‘ ’ ʻ to‘g‘ri chiqadi.
 * Sarlavha, tashkilot, yaratilgan vaqt va filtrlar; jadval sarlavhasi har sahifada takrorlanadi; holat katagi o‘z
 * rangida, qator och tusda (holatsiz qatorlar — navbatma-navbat fon); pastda "Reaktiv Control · Sahifa N / M".
 */
object PdfExport {
    private class Fonts(val regular: BaseFont, val bold: BaseFont)

    private val fonts: Fonts by lazy { Fonts(loadFont("DejaVuSans.ttf"), loadFont("DejaVuSans-Bold.ttf")) }

    private val NAVY = color(ReportColors.NAVY)
    private val TEXT = color(ReportColors.TEXT)
    private val MUTED = color(ReportColors.MUTED)
    private val BORDER = color(ReportColors.BORDER)

    fun write(data: ReportData, target: Path): Path = ReportFiles.writeAtomically(target) { out -> write(data, out) }

    fun write(data: ReportData, out: OutputStream) {
        val f = fonts
        val document = Document(PageSize.A4.rotate(), 28f, 28f, 26f, 40f)
        val writer = PdfWriter.getInstance(document, out)
        writer.pageEvent = PageFooter(f.regular)
        document.addTitle(data.title)
        document.addCreator("Reaktiv Control")
        if (data.orgName.isNotBlank()) document.addAuthor(data.orgName)
        document.open()

        document.add(Paragraph(data.title, Font(f.bold, 15f, Font.NORMAL, NAVY)).apply { spacingAfter = 2f })
        if (data.orgName.isNotBlank()) document.add(Paragraph(data.orgName, Font(f.bold, 10.5f, Font.NORMAL, TEXT)))
        val info = Font(f.regular, 8.5f, Font.NORMAL, MUTED)
        document.add(Paragraph(data.generatedLine, info))
        data.filterLines.forEach { document.add(Paragraph(it, info)) }

        val size = when {
            data.columns.size <= 8 -> 8.5f
            data.columns.size <= 11 -> 8f
            data.columns.size <= 13 -> 7.5f
            else -> 7f
        }
        document.add(table(data, f, size).apply { setSpacingBefore(8f) })

        if (data.footerLines.isNotEmpty()) {
            val footer = Font(f.bold, 8.5f, Font.NORMAL, TEXT)
            data.footerLines.forEachIndexed { index, line ->
                document.add(Paragraph(line, footer).apply { if (index == 0) spacingBefore = 8f })
            }
        }
        document.close()
    }

    private fun table(data: ReportData, f: Fonts, size: Float): PdfPTable {
        val columns = data.columns.ifEmpty { listOf(ReportColumn("")) }
        val table = PdfPTable(columns.size)
        table.widthPercentage = 100f
        table.setWidths(fitWidths(data, columns, f, size))
        table.headerRows = 1
        val headerFont = Font(f.bold, size, Font.NORMAL, Color.WHITE)
        columns.forEach { column ->
            table.addCell(
                cell(column.title, headerFont).apply {
                    backgroundColor = NAVY
                    horizontalAlignment = Element.ALIGN_CENTER
                    verticalAlignment = Element.ALIGN_MIDDLE
                    setPaddingTop(4f)
                    setPaddingBottom(5f)
                },
            )
        }
        val regular = Font(f.regular, size, Font.NORMAL, TEXT)
        val bold = Font(f.bold, size, Font.NORMAL, TEXT)
        var zebra = 0
        data.rows.forEach { row ->
            val background = when {
                row.summary -> color(ReportColors.SUMMARY)
                row.state != null -> color(ReportColors.tint(row.state))
                else -> if (zebra++ % 2 == 1) color(ReportColors.ZEBRA) else null
            }
            columns.forEachIndexed { c, column ->
                val value = row.cells.getOrNull(c)
                val state = value as? ExpiryState
                val text = data.text(row, c)
                val cell = if (column.type == ReportColumnType.STATUS && state != null) {
                    cell(text, Font(f.bold, size, Font.NORMAL, color(ReportColors.onFill(state)))).apply {
                        backgroundColor = color(ReportColors.fill(state))
                    }
                } else {
                    cell(text, if (row.summary) bold else regular).apply { if (background != null) backgroundColor = background }
                }
                cell.horizontalAlignment = when (column.type) {
                    ReportColumnType.QTY, ReportColumnType.INT -> Element.ALIGN_RIGHT
                    ReportColumnType.DATE, ReportColumnType.STATUS -> Element.ALIGN_CENTER
                    ReportColumnType.TEXT -> Element.ALIGN_LEFT
                }
                table.addCell(cell)
            }
        }
        if (data.rows.isEmpty()) {
            table.addCell(
                cell("Ma’lumot topilmadi.", Font(f.regular, size, Font.ITALIC, MUTED)).apply {
                    colspan = columns.size
                    horizontalAlignment = Element.ALIGN_CENTER
                },
            )
        }
        return table
    }

    /**
     * Ustun kengliklari (nuqtada): ustun tavsifidagi nisbat bo‘yicha, lekin sarlavha yoki katakdagi eng uzun so‘z
     * bo‘linmaydigan darajada keng — so‘z o‘rtasidan uzilib qolmasin. Qolgan ustunlar shunga mos toraytiriladi.
     */
    private fun fitWidths(data: ReportData, columns: List<ReportColumn>, f: Fonts, size: Float): FloatArray {
        val total = PageSize.A4.rotate().width - 56f
        val padding = 7f
        fun longestWord(text: String, font: BaseFont): Float =
            text.split(' ', '\n').maxOfOrNull { font.getWidthPoint(it, size) } ?: 0f
        val min = columns.mapIndexed { c, column ->
            val header = longestWord(column.title, f.bold)
            val cells = data.rows.maxOfOrNull { row ->
                val font = if (row.summary || column.type == ReportColumnType.STATUS) f.bold else f.regular
                longestWord(data.text(row, c), font)
            } ?: 0f
            minOf(maxOf(header, cells) + padding, total / 3)
        }
        val weights = columns.map { it.width.coerceAtLeast(1).toFloat() }
        val widths = weights.map { it / weights.sum() * total }.toMutableList()
        // Tor ustunlar kerakli kenglikkacha kengaytiriladi, qolganlari mutanosib toraytiriladi.
        repeat(columns.size) {
            val fixed = widths.indices.filter { widths[it] < min[it] - 0.01f }
            if (fixed.isEmpty()) return@repeat
            fixed.forEach { widths[it] = min[it] }
            val flexible = widths.indices.filter { widths[it] > min[it] + 0.01f }
            val excess = widths.sum() - total
            val room = flexible.sumOf { (widths[it] - min[it]).toDouble() }.toFloat()
            if (room <= 0f) return@repeat
            val k = minOf(1f, excess / room)
            flexible.forEach { widths[it] -= (widths[it] - min[it]) * k }
        }
        return widths.toFloatArray()
    }

    private fun cell(text: String, font: Font): PdfPCell = PdfPCell(Phrase(text, font)).apply {
        verticalAlignment = Element.ALIGN_MIDDLE
        borderColor = BORDER
        borderWidth = 0.5f
        setPaddingLeft(3f)
        setPaddingRight(3f)
        setPaddingTop(2.5f)
        setPaddingBottom(3.5f)
    }

    /** Har sahifa pastida: "Reaktiv Control · Sahifa N / M" (M — hujjat yopilganda yoziladi). */
    private class PageFooter(private val font: BaseFont) : PdfPageEventHelper() {
        private val size = 8f
        private lateinit var total: PdfTemplate

        override fun onOpenDocument(writer: PdfWriter, document: Document) {
            total = writer.directContent.createTemplate(30f, 12f)
        }

        override fun onEndPage(writer: PdfWriter, document: Document) {
            val text = "Reaktiv Control · Sahifa ${writer.pageNumber} / "
            val width = font.getWidthPoint(text, size) + font.getWidthPoint("00", size)
            val x = (document.left() + document.right()) / 2 - width / 2
            val y = document.bottom() - 20f
            val cb = writer.directContent
            cb.saveState()
            cb.beginText()
            cb.setFontAndSize(font, size)
            cb.setColorFill(MUTED)
            cb.setTextMatrix(x, y)
            cb.showText(text)
            cb.endText()
            cb.addTemplate(total, x + font.getWidthPoint(text, size), y)
            cb.restoreState()
        }

        override fun onCloseDocument(writer: PdfWriter, document: Document) {
            total.beginText()
            total.setFontAndSize(font, size)
            total.setColorFill(MUTED)
            total.setTextMatrix(0f, 0f)
            total.showText((writer.pageNumber - 1).coerceAtLeast(1).toString())
            total.endText()
        }
    }

    private fun loadFont(file: String): BaseFont {
        val bytes = PdfExport::class.java.getResourceAsStream("/fonts/$file")?.use { it.readBytes() }
            ?: throw IllegalStateException("Shrift topilmadi: fonts/$file")
        return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, bytes, null)
    }

    private fun color(hex: String): Color = Color(hex.toInt(16))
}

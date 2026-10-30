package uz.reaktivombor.desktop.report

import org.dhatim.fastexcel.BorderStyle
import org.dhatim.fastexcel.PaperSize
import org.dhatim.fastexcel.Position
import org.dhatim.fastexcel.StyleSetter
import org.dhatim.fastexcel.Worksheet
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.Xlsx
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.file.Path
import java.time.LocalDate

/**
 * Hisobot → .xlsx (fastexcel). Tepada sarlavha, tashkilot, yaratilgan vaqt va filtrlar; so‘ng jadval: sarlavha qatori
 * (qalin, to‘q ko‘k fonda oq), haqiqiy sana kataklari (dd.mm.yyyy), son kataklari, holat katagi o‘z rangida,
 * qator holat rangida och tusda, matn kataklari ustun ichida qatorlarga bo‘linadi (kesilmaydi). Sarlavha qatori qotirilgan,
 * avtofiltr yoqilgan, chop etish — A4 albom, eniga sig‘adi.
 */
object ExcelExport {
    /** Excel varaq nomi chegarasi. */
    const val SHEET_NAME_MAX = 31

    private const val DATE_FORMAT = "dd.mm.yyyy"
    private const val INT_FORMAT = "#,##0"
    private val SHEET_FORBIDDEN = Regex("""[\[\]:*?/\\\p{Cntrl}]""")

    fun write(data: ReportData, target: Path): Path = ReportFiles.writeAtomically(target) { out -> write(data, out) }

    fun write(data: ReportData, out: OutputStream) = Xlsx.write(out) { workbook ->
        val ws = workbook.newWorksheet(sheetName(data.title))
        val lastCol = (data.columns.size - 1).coerceAtLeast(0)
        var r = 0

        ws.value(r, 0, data.title)
        ws.style(r, 0).bold().fontSize(14).fontColor(ReportColors.NAVY).set()
        ws.rowHeight(r, 21.0)
        r++
        if (data.orgName.isNotBlank()) {
            ws.value(r, 0, data.orgName)
            ws.style(r, 0).bold().set()
            r++
        }
        ws.value(r, 0, data.generatedLine)
        ws.style(r, 0).fontColor(ReportColors.MUTED).set()
        r++
        data.filterLines.forEach { line ->
            ws.value(r, 0, line)
            ws.style(r, 0).fontColor(ReportColors.MUTED).set()
            r++
        }
        r++

        // Sarlavha qatori.
        val header = r
        data.columns.forEachIndexed { c, column ->
            ws.value(header, c, column.title)
            ws.style(header, c).bold().fontColor(ReportColors.WHITE).fillColor(ReportColors.NAVY).wrapText(true)
                .horizontalAlignment("center").verticalAlignment("center")
                .borderStyle(BorderStyle.THIN).borderColor(ReportColors.BORDER).set()
            ws.width(c, column.width.toDouble().coerceIn(3.0, 80.0))
        }
        ws.rowHeight(header, headerHeight(data.columns))
        r++

        data.rows.forEach { row ->
            writeRow(ws, r, data.columns, row)
            r++
        }
        val lastData = r - 1
        if (data.rows.isEmpty()) {
            ws.value(r, 0, "Ma’lumot topilmadi.")
            ws.style(r, 0).italic().fontColor(ReportColors.MUTED).set()
            r++
        }

        ws.freezePane(0, header + 1)
        ws.setAutoFilter(header, 0, lastData.coerceAtLeast(header), lastCol)

        r++
        data.footerLines.forEach { line ->
            ws.value(r, 0, line)
            ws.style(r, 0).bold().set()
            r++
        }

        // Chop etish: A4 albom, eniga bitta sahifa, sarlavha qatori har sahifada.
        ws.pageOrientation("landscape")
        ws.paperSize(PaperSize.A4_PAPER)
        ws.setFitToPage(true)
        ws.fitToWidth(1.toShort())
        ws.fitToHeight(0.toShort())
        ws.repeatRows(header)
        ws.footer("Reaktiv Control · Sahifa &P / &N", Position.CENTER)
    }

    private fun writeRow(ws: Worksheet, r: Int, columns: List<ReportColumn>, row: ReportRow) {
        val tint = when {
            row.summary -> ReportColors.SUMMARY
            row.state != null -> ReportColors.tint(row.state)
            else -> null
        }
        columns.forEachIndexed { c, column ->
            val value = row.cells.getOrNull(c)
            var style: StyleSetter = ws.style(r, c).borderStyle(BorderStyle.THIN).borderColor(ReportColors.BORDER).verticalAlignment("top")
            when (column.type) {
                ReportColumnType.TEXT -> {
                    val text = value?.toString().orEmpty()
                    if (text.isNotEmpty()) ws.value(r, c, text)
                    // Uzun matn (nom, tashkilot, izoh) qo‘shni katak bilan kesilmasin: ustun ichida qatorlarga bo‘linadi
                    // (qator balandligi berilmagan — Excel/LibreOffice o‘zi moslaydi).
                    style = style.wrapText(true)
                }
                ReportColumnType.DATE -> {
                    (value as? LocalDate)?.let { ws.value(r, c, it) }
                    style = style.format(DATE_FORMAT).horizontalAlignment("center")
                }
                ReportColumnType.QTY -> {
                    val number = (value as? BigDecimal)?.let { if (it.scale() < 0) it.setScale(0) else it }
                    if (number != null) {
                        ws.value(r, c, number)
                        style = style.format(qtyFormat(number))
                    }
                    style = style.horizontalAlignment("right")
                }
                ReportColumnType.INT -> {
                    (value as? Number)?.let { ws.value(r, c, it.toLong()) }
                    style = style.format(INT_FORMAT).horizontalAlignment("right")
                }
                ReportColumnType.STATUS -> {
                    val state = value as? ExpiryState
                    if (state != null) {
                        ws.value(r, c, state.label)
                        style = style.fillColor(ReportColors.fill(state)).fontColor(ReportColors.onFill(state)).bold()
                    }
                    style = style.horizontalAlignment("center")
                }
            }
            val ownFill = column.type == ReportColumnType.STATUS && value is ExpiryState
            if (tint != null && !ownFill) style = style.fillColor(tint)
            if (row.summary) style = style.bold()
            style.set()
        }
    }

    /** Sarlavha qatori balandligi: eng ko‘p qatorga bo‘linadigan sarlavha sig‘adigan qilib (11 pt shrift ≈ 15 pt qator). */
    internal fun headerHeight(columns: List<ReportColumn>): Double {
        val lines = columns.maxOfOrNull { column -> wrappedLines(column.title, (column.width - 1).coerceAtLeast(3)) } ?: 1
        return (lines * 15.0 + 4.0).coerceAtLeast(20.0)
    }

    /** So‘zlarni bo‘lmasdan [perLine] belgili qatorlarga joylaganda nechta qator bo‘ladi (taxminan, bold uchun 10% zaxira). */
    private fun wrappedLines(text: String, perLine: Int): Int {
        val limit = perLine / 1.1
        var lines = 1
        var current = 0.0
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val length = word.length.toDouble()
            current = when {
                current == 0.0 -> length
                current + 1 + length <= limit -> current + 1 + length
                else -> {
                    lines++
                    length
                }
            }
            while (current > limit) {
                lines++
                current -= limit
            }
        }
        return lines
    }

    /** Aniq ko‘rinish: 5 → "5", 1,5 → "1,5", 0,25 → "0,25" (keraksiz nollar va oxirida vergul bo‘lmaydi). */
    internal fun qtyFormat(value: BigDecimal): String {
        val scale = value.stripTrailingZeros().scale().coerceAtLeast(0)
        return if (scale == 0) INT_FORMAT else INT_FORMAT + "." + "0".repeat(scale.coerceAtMost(9))
    }

    /** Excel varaq nomi: 31 belgigacha, [ ] : * ? / \ siz, chetida ' yo‘q, bo‘sh emas. */
    fun sheetName(title: String): String {
        val cleaned = title.replace(SHEET_FORBIDDEN, " ").replace(Regex("\\s+"), " ").trim().trim('\'').trim()
        val cut = if (cleaned.codePointCount(0, cleaned.length) > SHEET_NAME_MAX) {
            cleaned.substring(0, cleaned.offsetByCodePoints(0, SHEET_NAME_MAX)).trim().trimEnd('\'')
        } else {
            cleaned
        }
        return cut.ifEmpty { "Hisobot" }
    }
}

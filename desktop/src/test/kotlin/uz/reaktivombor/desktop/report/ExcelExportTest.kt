package uz.reaktivombor.desktop.report

import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import org.dhatim.fastexcel.reader.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.ExpiryState
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

class ExcelExportTest : ReportTestBase() {

    private fun export(kind: ReportKind, filter: ReportFilter = ReportFilter()): Pair<ReportData, Path> {
        val data = ReportBuilder(services).build(kind, filter, today)
        val file = ExcelExport.write(data, tmp.root.toPath().resolve("hisobot/${kind.name}.xlsx"))
        return data to file
    }

    private fun rows(file: Path): List<Row> =
        ReadableWorkbook(file.toFile(), ReadingOptions(true, false)).use { it.firstSheet.read() }

    private fun Row.texts(): List<String> = (0 until cellCount).map { getCellText(it).orEmpty() }.dropLastWhile { it.isEmpty() }

    @Test
    fun writesTitleHeaderDatesNumbersAndUzbekText() {
        seed()
        services.settings.orgName = "Sud-tibbiy ekspertiza markazi"
        val (data, file) = export(ReportKind.ALL_REAGENTS)
        ReadableWorkbook(file.toFile()).use { wb -> assertEquals("Barcha reaktivlar ro‘yxati", wb.firstSheet.name) }
        val rows = rows(file)
        assertEquals("Barcha reaktivlar ro‘yxati", rows[0].getCellText(0))
        assertEquals("Sud-tibbiy ekspertiza markazi", rows[1].getCellText(0))
        assertEquals("Yaratilgan: 29.10.2026 09:00", rows[2].getCellText(0))
        assertEquals("Filtr: yo‘q", rows[3].getCellText(0))
        val header = rows.first { it.texts() == data.columns.map { c -> c.title } }
        assertEquals(6, header.rowNum) // sarlavha, tashkilot, vaqt, filtr, bo‘sh qator, jadval sarlavhasi
        assertEquals("Bo‘lim / saqlash joyi", header.getCellText(3))
        assertEquals("Hisobdan chiqarilgan", header.getCellText(11))
        assertEquals("Mas’ul xodim", header.getCellText(15))

        val aseton = rows.first { it.getCellText(1) == "Aseton" }
        // Haqiqiy sana katagi, dd.mm.yyyy ko‘rinishida.
        val received = aseton.getCell(5)
        assertEquals(CellType.NUMBER, received.type)
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0), received.asDate())
        assertEquals("dd.mm.yyyy", received.dataFormatString)
        // Son katagi (miqdor) va butun son (qolgan kunlar).
        val remainder = aseton.getCell(12)
        assertEquals(CellType.NUMBER, remainder.type)
        assertEquals(0, BigDecimal("2").compareTo(remainder.asNumber()))
        assertEquals("#,##0", remainder.dataFormatString)
        assertEquals(0, BigDecimal("10").compareTo(aseton.getCell(7).asNumber()))
        assertEquals("1 oy yoki kam qoldi", aseton.getCellText(8))
        val natriy = rows.first { it.getCellText(1) == "Natriy xlorid" }
        assertEquals(0, BigDecimal("0.75").compareTo(natriy.getCell(12).asNumber()))
        assertEquals("#,##0.00", natriy.getCell(12).dataFormatString)
        assertEquals("kg", natriy.getCellText(13))
        val xloroform = rows.first { it.getCellText(1) == "Xloroform" }
        assertEquals("Muddati o‘tgan", xloroform.getCellText(8))
        assertEquals(0, BigDecimal("400").compareTo(xloroform.getCell(12).asNumber()))
        assertEquals(0, BigDecimal("100").compareTo(xloroform.getCell(11).asNumber())) // hisobdan chiqarilgan
        // Jadvaldan keyin — jami qatorlari.
        assertTrue(rows.any { it.getCellText(0) == "Jami qoldiq: 16,4 l · 750 g" })
    }

    @Test
    fun headerIsFrozenFilteredStyledAndTheZipIsStandard() {
        seed()
        val (data, file) = export(ReportKind.EXPIRED)
        val entries = LinkedHashMap<String, String>()
        // Oddiy zip (LibreOffice ham ochadi): java.util.zip oqimi bilan o‘qiladi.
        ZipInputStream(Files.newInputStream(file)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        // 1-qator — sarlavha, 2 — vaqt, 3 — filtr, 4 — bo‘sh, 5 — jadval sarlavhasi (tashkilot nomi yo‘q).
        assertTrue(sheet, sheet.contains("""ySplit="5""""))
        assertTrue(sheet, sheet.contains("""state="frozen""""))
        val lastCol = ('A' + data.columns.size - 1)
        assertTrue(sheet, sheet.contains("""<autoFilter ref="A5:${lastCol}6""""))
        assertTrue(sheet, sheet.contains("""orientation="landscape""""))
        val styles = entries.getValue("xl/styles.xml")
        assertTrue(styles, styles.contains("\"0B2545\"")) // sarlavha foni
        assertTrue(styles, styles.contains("\"7A1212\"")) // «Muddati o‘tgan» katagi
        assertTrue(styles, styles.contains("\"F4D3D3\"")) // qatorning och tusi
        assertTrue(styles, styles.contains("dd.mm.yyyy"))
    }

    @Test
    fun textCellsWrapInsideTheirColumn() {
        seed()
        val (data, file) = export(ReportKind.DISTRICTS)
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(Files.newInputStream(file)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        fun parse(name: String) = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(entries.getValue(name)))
        val cells = parse("xl/worksheets/sheet1.xml").getElementsByTagName("c")
        fun styleOf(ref: String): Int = (0 until cells.length).map { cells.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("r") == ref }.getAttribute("s").toInt()
        val xfs = (parse("xl/styles.xml").getElementsByTagName("cellXfs").item(0) as org.w3c.dom.Element).getElementsByTagName("xf")
        fun wraps(style: Int): Boolean {
            val alignment = (xfs.item(style) as org.w3c.dom.Element).getElementsByTagName("alignment")
            return alignment.length > 0 && (alignment.item(0) as org.w3c.dom.Element).getAttribute("wrapText").let { it == "1" || it == "true" }
        }
        // 1 — sarlavha, 2 — vaqt, 3 — filtr, 4 — bo‘sh, 5 — jadval sarlavhasi, 6 — birinchi qator.
        val organization = 'A' + data.columns.indexOfFirst { it.title == "Tashkilot" }
        val name = 'A' + data.columns.indexOfFirst { it.title == "Reaktiv nomi" }
        val qty = 'A' + data.columns.indexOfFirst { it.title == "Berilgan" }
        assertTrue(wraps(styleOf("${organization}6")))
        assertTrue(wraps(styleOf("${name}6")))
        assertTrue(!wraps(styleOf("${qty}6"))) // son kataklari bitta qatorda
    }

    @Test
    fun emptyReportAndRewritingAnExistingFile() {
        val (_, file) = export(ReportKind.EXPIRED)
        val rows = rows(file)
        assertTrue(rows.any { it.getCellText(0) == "Ma’lumot topilmadi." })
        seed()
        val (_, again) = export(ReportKind.EXPIRED)
        assertEquals(file, again)
        assertTrue(rows(again).any { it.getCellText(1) == "Xloroform" })
        // Vaqtinchalik fayllar qolmaydi.
        assertEquals(listOf("EXPIRED.xlsx"), Files.list(file.parent).use { s -> s.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun syntheticDataWithEveryColumnType() {
        val data = ReportData(
            kind = ReportKind.BATCHES,
            title = "Sinov: [maxsus] belgilar / nomi juda uzun bo‘lgan hisobot",
            orgName = "",
            generatedAt = LocalDateTime.of(2026, 10, 29, 14, 5),
            filterLines = listOf("Reaktiv: O‘simlik moyi ʻ’"),
            columns = listOf(
                ReportColumn("Matn"), ReportColumn("Sana", ReportColumnType.DATE), ReportColumn("Miqdor", ReportColumnType.QTY),
                ReportColumn("Son", ReportColumnType.INT), ReportColumn("Holati", ReportColumnType.STATUS),
            ),
            rows = listOf(
                ReportRow(listOf("G‘alla", LocalDate.of(2026, 1, 31), BigDecimal("1250.125"), -3L, ExpiryState.YELLOW), ExpiryState.YELLOW),
                ReportRow(listOf("Jami", null, BigDecimal("0.000001"), null, null), summary = true),
            ),
            footerLines = emptyList(),
        )
        val out = ByteArrayOutputStream()
        ExcelExport.write(data, out)
        val file = tmp.root.toPath().resolve("sinov.xlsx")
        Files.write(file, out.toByteArray())
        ReadableWorkbook(file.toFile()).use { wb ->
            val name = wb.firstSheet.name
            assertEquals(ExcelExport.SHEET_NAME_MAX, name.codePointCount(0, name.length))
            assertTrue(name, name.none { it in "[]:*?/\\" })
        }
        val rows = rows(file)
        assertEquals("Reaktiv: O‘simlik moyi ʻ’", rows[2].getCellText(0))
        val first = rows.first { it.getCellText(0) == "G‘alla" }
        assertEquals(LocalDateTime.of(2026, 1, 31, 0, 0), first.getCell(1).asDate())
        assertEquals(0, BigDecimal("1250.125").compareTo(first.getCell(2).asNumber()))
        assertEquals("#,##0.000", first.getCell(2).dataFormatString)
        assertEquals(0, BigDecimal("-3").compareTo(first.getCell(3).asNumber()))
        assertEquals("1–3 oy qoldi", first.getCellText(4))
        val total = rows.first { it.getCellText(0) == "Jami" }
        assertEquals(0, BigDecimal("0.000001").compareTo(total.getCell(2).asNumber()))
        assertNotNull(total)
    }

    @Test
    fun sheetNamesAreSanitised() {
        assertEquals("Muddati o‘tgan reaktivlar", ExcelExport.sheetName("Muddati o‘tgan reaktivlar"))
        assertEquals("Hisobot a b c", ExcelExport.sheetName("'Hisobot: a/b*c?'"))
        assertEquals("Reaktivlarning partiyalar bo‘yi", ExcelExport.sheetName("Reaktivlarning partiyalar bo‘yicha ro‘yxati"))
        assertEquals("Hisobot", ExcelExport.sheetName(" [] "))
        assertEquals("#,##0", ExcelExport.qtyFormat(BigDecimal("4E+2")))
        assertEquals("#,##0.0", ExcelExport.qtyFormat(BigDecimal("1.50")))
    }
}

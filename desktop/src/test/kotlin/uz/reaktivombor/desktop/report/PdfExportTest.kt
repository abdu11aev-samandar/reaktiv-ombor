package uz.reaktivombor.desktop.report

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.ExpiryState
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime

class PdfExportTest : ReportTestBase() {

    /** Har bir sahifa matni (bo‘shliqlar bittaga keltirilgan). */
    private fun pages(bytes: ByteArray): List<String> {
        val reader = PdfReader(bytes)
        try {
            val extractor = PdfTextExtractor(reader)
            return (1..reader.numberOfPages).map { extractor.getTextFromPage(it).replace(Regex("\\s+"), " ") }
        } finally {
            reader.close()
        }
    }

    private fun pdf(data: ReportData): ByteArray = ByteArrayOutputStream().also { PdfExport.write(data, it) }.toByteArray()

    @Test
    fun titleOrganisationFiltersAndUzbekLettersAreInTheText() {
        seed()
        services.settings.orgName = "Sud-tibbiy ekspertiza markazi"
        val data = ReportBuilder(services).build(ReportKind.EXPIRED, ReportFilter(departmentId = null, series = "X-"), today)
        val file = PdfExport.write(data, tmp.root.toPath().resolve("pdf/EXPIRED.pdf"))
        val bytes = Files.readAllBytes(file)
        assertTrue(String(bytes, 0, 5, Charsets.ISO_8859_1) == "%PDF-")
        val text = pages(bytes)
        assertEquals(1, text.size)
        val page = text.single()
        assertTrue(page, page.contains("Muddati o‘tgan reaktivlar"))
        assertTrue(page, page.contains("Sud-tibbiy ekspertiza markazi"))
        assertTrue(page, page.contains("Yaratilgan: 29.10.2026 09:00"))
        assertTrue(page, page.contains("Seriya: X-"))
        assertTrue(page, page.contains("O‘tgan kunlar"))
        assertTrue(page, page.contains("Xloroform"))
        assertTrue(page, page.contains("Reaktiv Control · Sahifa 1 / 1"))
        // Shrift faylga joylangan, Unicode (Identity-H) kodlashda.
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertTrue(raw.contains("/Identity-H"))
        assertTrue(raw.contains("DejaVuSans"))
        assertTrue(raw.contains("/FontFile2"))
    }

    @Test
    fun everyUzbekLetterFormRendersAndIsExtractable() {
        val data = ReportData(
            kind = ReportKind.ALL_REAGENTS,
            title = "O‘zbekcha harflar: o‘ g‘ O‘ G‘ ma’lumot ʻ",
            orgName = "",
            generatedAt = LocalDateTime.of(2026, 10, 29, 14, 5),
            filterLines = listOf("Filtr: yo‘q"),
            columns = listOf(ReportColumn("Bo‘lim"), ReportColumn("Holati", ReportColumnType.STATUS)),
            rows = listOf(ReportRow(listOf("G‘ijduvon", ExpiryState.EXPIRED), ExpiryState.EXPIRED)),
            footerLines = listOf("Jami: 1 ta"),
        )
        val page = pages(pdf(data)).single()
        listOf("o‘", "g‘", "O‘", "G‘", "ma’lumot", "ʻ", "G‘ijduvon", "Muddati o‘tgan", "Yaratilgan: 29.10.2026 14:05", "Jami: 1 ta").forEach {
            assertTrue("«$it» yo‘q: $page", page.contains(it))
        }
    }

    @Test
    fun multiPageTableRepeatsTheHeaderAndNumbersPages() {
        val columns = listOf(
            ReportColumn("№", ReportColumnType.INT, 5), ReportColumn("Reaktiv nomi", ReportColumnType.TEXT, 28),
            ReportColumn("Yaroqlilik muddati", ReportColumnType.DATE, 12), ReportColumn("Qoldiq", ReportColumnType.QTY, 11),
            ReportColumn("Birlik", ReportColumnType.TEXT, 7), ReportColumn("Holati", ReportColumnType.STATUS, 17),
        )
        val states = ExpiryState.entries
        val rows = (1..160).map { i ->
            val state = states[i % states.size]
            ReportRow(
                listOf(i.toLong(), "Reaktiv № $i — o‘lchovli", LocalDate.of(2027, 1, 1).plusDays(i.toLong()), BigDecimal(i).divide(BigDecimal(4)), "l", state),
                state = if (i % 3 == 0) null else state,
            )
        }
        val data = ReportData(
            ReportKind.BATCHES, "Ko‘p sahifali hisobot", "Laboratoriya", LocalDateTime.of(2026, 10, 29, 9, 0),
            listOf("Filtr: yo‘q"), columns, rows, listOf("Jami: 160 ta"),
        )
        val pages = pages(pdf(data))
        assertTrue("sahifalar: ${pages.size}", pages.size >= 3)
        pages.forEachIndexed { index, page ->
            assertTrue("${index + 1}-sahifa: $page", page.contains("Reaktiv nomi"))
            assertTrue("${index + 1}-sahifa: $page", page.contains("Yaroqlilik muddati"))
            assertTrue("${index + 1}-sahifa: $page", page.contains("Reaktiv Control · Sahifa ${index + 1} / ${pages.size}"))
        }
        assertTrue(pages.first().contains("Ko‘p sahifali hisobot"))
        assertTrue(pages.last().contains("Reaktiv № 160 — o‘lchovli"))
        assertTrue(pages.last().contains("Jami: 160 ta"))
        assertTrue(pages.joinToString(" ").contains("40")) // 160 / 4
    }

    @Test
    fun emptyReportSaysThereIsNoData() {
        val data = ReportBuilder(services).build(ReportKind.BELOW_MIN, ReportFilter(), today)
        val page = pages(pdf(data)).single()
        assertTrue(page, page.contains("Ma’lumot topilmadi."))
        assertTrue(page, page.contains("Minimal qoldiqdan kamaygan reaktivlar"))
    }
}

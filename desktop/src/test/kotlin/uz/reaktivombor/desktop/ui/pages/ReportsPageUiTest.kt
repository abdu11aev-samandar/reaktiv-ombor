package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import org.dhatim.fastexcel.reader.Row
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.report.ReportBuilder
import uz.reaktivombor.desktop.report.ReportColumnType
import uz.reaktivombor.desktop.report.ReportData
import uz.reaktivombor.desktop.report.ReportFilter
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import uz.reaktivombor.desktop.ui.theme.Severity
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** «Hisobotlar» sahifasi haqiqiy baza (DemoData) bilan: 12 tur, filtrlar, xatolar, Excel va PDF fayllar. */
@OptIn(ExperimentalTestApi::class)
class ReportsPageUiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids
    private lateinit var dialogs: FakeFileDialogs
    private lateinit var out: Path
    private val today = DemoData.TODAY

    @Before
    fun setUp() {
        val (s, i) = DemoData.seeded(tmp.newFolder("data").toPath())
        services = s
        ids = i
        dialogs = FakeFileDialogs()
        out = tmp.newFolder("out").toPath()
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.app(): AppState {
        val state = AppState(services, dialogs, Page.REPORTS)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun SkikoComposeUiTest.pick(kind: ReportKind) {
        onNodeWithTag("report-kind-${kind.name}").performScrollTo().performClick()
        waitForIdle()
    }

    private fun build(kind: ReportKind, filter: ReportFilter = ReportFilter()): ReportData = ReportBuilder(services).build(kind, filter, today)

    private fun rows(file: Path): List<Row> = ReadableWorkbook(file.toFile(), ReadingOptions(true, false)).use { it.firstSheet.read() }

    private fun Row.text(i: Int): String = if (i < cellCount) getCellText(i).orEmpty() else ""

    /** Jadval sarlavhasidan keyingi, [column] ustuni to‘ldirilgan qatorlar (jami va pastki qatorlarsiz). */
    private fun dataRows(rows: List<Row>, column: Int): List<Row> {
        val header = rows.indexOfFirst { it.text(0) == "№" }
        assertTrue("jadval sarlavhasi", header >= 0)
        return rows.drop(header + 1).filter { it.text(column).isNotEmpty() }
    }

    private fun pdfText(file: Path): String {
        val reader = PdfReader(Files.readAllBytes(file))
        try {
            val extractor = PdfTextExtractor(reader)
            return (1..reader.numberOfPages).joinToString("\n") { extractor.getTextFromPage(it) }.replace(Regex("\\s+"), " ")
        } finally {
            reader.close()
        }
    }

    @Test
    fun everyKindPreviewsWithoutErrors() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        // Ro‘yxatda 12 tur, sahifa ochilganda «Hisobot hali tuzilmagan».
        ReportKind.entries.forEach { onNodeWithTag("report-kind-${it.name}").assertExists() }
        onNodeWithTag("report-empty").assertExists()
        ReportKind.entries.forEach { kind ->
            pick(kind)
            onNodeWithTag("report-title").assert(hasText(kind.title))
            if (kind.needsName) onNodeWithTag("report-name").performTextInput("Etil spirti 96%")
            onNodeWithTag("report-show").performClick()
            waitForIdle()
            val expected = build(kind, ReportFilter(name = if (kind.needsName) "Etil spirti 96%" else null))
            onNodeWithTag("report-row-count").assert(hasText("${expected.reportsRowCount} ta qator", substring = true))
            onNodeWithTag("report-filter-lines").assert(hasText(expected.filterLines.joinToString(" · ")))
            if (expected.rows.isEmpty()) {
                onNodeWithTag("report-table-empty").assertExists()
            } else {
                onNodeWithTag("report-table-row-0").assertExists()
                // Birinchi qatorning kataklari jadvalda xuddi eksportdagidek (ko‘rinib turganlari).
                val first = expected.rows.first()
                val cell = expected.columns.indices
                    .filter { expected.columns[it].type == ReportColumnType.TEXT }
                    .map { expected.text(first, it) }
                    .first { it.isNotEmpty() }
                assertTrue(
                    "${kind.title}: «$cell»",
                    onAllNodes(hasText(cell) and hasAnyAncestor(hasTestTag("report-table-row-0"))).fetchSemanticsNodes().isNotEmpty(),
                )
            }
            expected.footerLines.forEach { line -> onNode(hasText(line) and hasAnyAncestor(hasTestTag("report-footer"))).assertExists() }
            assertNull("${kind.title}: kutilmagan xato", state.unexpectedError)
            assertTrue("${kind.title}: xato xabari", state.messages.none { it.severity == Severity.ERROR })
        }
        assertTrue("ko‘rish faylga yozmaydi", dialogs.calls.isEmpty())
    }

    @Test
    fun batchesPreviewHasSummaryRowsAndStatusChips() = runDesktopComposeUiTest(1366, 768) {
        app()
        pick(ReportKind.BATCHES)
        onNodeWithTag("report-show").performClick()
        val data = build(ReportKind.BATCHES)
        val summary = data.rows.indexOfFirst { it.summary && it.cells[1] == "Jami: Etil spirti 96% (3 ta partiya)" }
        assertTrue(summary > 0)
        onNode(hasText("Jami: Etil spirti 96% (3 ta partiya)") and hasAnyAncestor(hasTestTag("report-table-row-$summary"))).assertExists()
        // Holat katagi — rangli belgi matni bilan.
        onNode(hasText("1–3 oy qoldi") and hasAnyAncestor(hasTestTag("report-table-row-0"))).assertExists()
        onNodeWithTag("report-row-count").assert(hasText("10 ta qator + 7 ta jami"))
    }

    @Test
    fun historyWithoutNameShowsTheErrorNextToTheField() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        pick(ReportKind.HISTORY)
        onNodeWithTag("report-show").performClick()
        // Faqat maydon ostida (pastki xabar sifatida takrorlanmaydi).
        onNodeWithText("Tarix hisoboti uchun reaktiv nomini kiriting.").assertExists()
        assertTrue(state.messages.none { it.severity == Severity.ERROR })
        onNodeWithTag("report-table").assertDoesNotExist()
        // Saqlash ham avval tuzadi — xato bo‘lsa saqlash oynasi ochilmaydi.
        onNodeWithTag("report-save-excel").performClick()
        assertTrue(dialogs.calls.isEmpty())
        onNodeWithTag("report-name").performTextInput("Mavjud emas")
        onNodeWithText("Tarix hisoboti uchun reaktiv nomini kiriting.").assertDoesNotExist()
        onNodeWithTag("report-show").performClick()
        onNodeWithText("«Mavjud emas» nomli reaktiv topilmadi.").assertExists()
        assertNull(state.unexpectedError)
    }

    @Test
    fun excelSaveWithoutPreviewBuildsAndWritesAReadableFile() = runDesktopComposeUiTest(1366, 768) {
        val target = out.resolve("kirim.xlsx")
        dialogs.saveAnswer = target
        app()
        pick(ReportKind.RECEIPTS)
        onNodeWithTag("report-period-from").performTextInput("01.01.2026")
        onNodeWithTag("report-period-to").performTextInput("31.10.2026")
        mainClock.autoAdvance = false
        onNodeWithTag("report-save-excel").performClick()
        mainClock.advanceTimeBy(300)
        assertEquals(listOf("save:Kirim-operatsiyalari-2026-10-29.xlsx"), dialogs.calls)
        assertTrue(Files.isRegularFile(target))

        val expected = build(ReportKind.RECEIPTS, ReportFilter(from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 10, 31)))
        assertEquals(7, expected.reportsRowCount)
        val rows = rows(target)
        assertEquals("Kirim operatsiyalari", rows[0].text(0))
        assertTrue(rows.any { it.text(0) == "Davr: 01.01.2026 — 31.10.2026" })
        val data = dataRows(rows, 2)
        assertEquals(expected.rows.map { it.cells[2] }, data.map { it.text(2) })
        assertTrue(data.none { it.text(3) == "XL-117" }) // 2025-yilgi kirim davrga kirmaydi

        // Saqlashdan keyin ko‘rib chiqish ham shu hisobot; xabarda fayl nomi va «Papkani ochish».
        onNodeWithTag("report-table").assertExists()
        onNodeWithTag("report-row-count").assert(hasText("7 ta qator"))
        onNode(hasText("«Kirim operatsiyalari» Excel fayliga saqlandi (7 ta qator): kirim.xlsx") and hasAnyAncestor(hasTestTag("message-success"))).assertExists()
        onNodeWithText("Papkani ochish").performClick()
        mainClock.advanceTimeBy(100)
        assertEquals("folder:${target.toAbsolutePath().parent}", dialogs.calls.last())
    }

    @Test
    fun pdfSaveWritesAReadablePdfWithTheOrganisation() = runDesktopComposeUiTest(1366, 768) {
        services.settings.orgName = "Sud-tibbiy ekspertiza markazi"
        val target = out.resolve("muddati.pdf")
        dialogs.saveAnswer = target
        app()
        pick(ReportKind.EXPIRED)
        onNodeWithTag("report-show").performClick()
        onNodeWithTag("report-header-line").assert(hasText("Sud-tibbiy ekspertiza markazi · Yaratilgan: 29.10.2026 09:00"))
        onNodeWithTag("report-org-settings").assertDoesNotExist()
        onNodeWithTag("report-save-pdf").performClick()
        waitForIdle()
        assertEquals(listOf("save:Muddati-o‘tgan-reaktivlar-2026-10-29.pdf"), dialogs.calls)
        val text = pdfText(target)
        assertTrue(text, text.contains("Muddati o‘tgan reaktivlar"))
        assertTrue(text, text.contains("Sud-tibbiy ekspertiza markazi"))
        assertTrue(text, text.contains("Xloroform"))
        assertTrue(text, text.contains("XL-117"))
    }

    @Test
    fun cancellingTheSaveDialogWritesNothing() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        pick(ReportKind.ALL_REAGENTS)
        onNodeWithTag("report-save-excel").performClick()
        waitForIdle()
        assertEquals(listOf("save:Barcha-reaktivlar-ro‘yxati-2026-10-29.xlsx"), dialogs.calls)
        assertEquals(0L, Files.list(out).use { it.count() })
        assertTrue(state.messages.isEmpty())
        // Hisobot baribir tuzilgan — ko‘rib chiqishda.
        onNodeWithTag("report-table").assertExists()
    }

    @Test
    fun unwritableTargetShowsAnErrorMessageNotACrash() = runDesktopComposeUiTest(1366, 768) {
        // Tanlangan «papka» aslida fayl — yozib bo‘lmaydi: tushunarli qizil xabar, «Kutilmagan xato» emas.
        val blocker = Files.createFile(out.resolve("fayl"))
        dialogs.saveAnswer = blocker.resolve("hisobot.xlsx")
        val state = app()
        onNodeWithTag("report-save-excel").performClick()
        waitForIdle()
        assertNull(state.unexpectedError)
        assertTrue(state.messages.any { it.severity == Severity.ERROR && it.text.contains("yozib bo‘lmadi") })
        assertTrue(state.messages.none { it.severity == Severity.SUCCESS })
    }

    @Test
    fun departmentAndDistrictFiltersAndStalePreview() = runDesktopComposeUiTest(1366, 768) {
        app()
        pick(ReportKind.BY_DEPARTMENT)
        onNodeWithTag("report-department").performClick()
        // 0 — «Hammasi», 1 — Gistologiya.
        onNodeWithTag("report-department-option-1").performClick()
        onNodeWithTag("report-show").performClick()
        val gistologiya = services.directory.departments().first { it.name == "Gistologiya" }.id
        val expected = build(ReportKind.BY_DEPARTMENT, ReportFilter(departmentId = gistologiya))
        onNodeWithTag("report-filter-lines").assert(hasText("Bo‘lim: Gistologiya"))
        onNodeWithTag("report-row-count").assert(hasText("${expected.reportsRowCount} ta qator"))
        onNodeWithText("Sud-kimyo").assertDoesNotExist()

        // Filtr o‘zgardi — ko‘rinish eskirgani belgilanadi, «Yangilash» qayta tuzadi.
        onNodeWithTag("report-series").performTextInput("ES")
        onNodeWithTag("report-stale").assertExists()
        onNodeWithText("Yangilash").performClick()
        onNodeWithTag("report-stale").assertDoesNotExist()
        onNodeWithTag("report-filter-lines").assert(hasText("Seriya: ES · Bo‘lim: Gistologiya"))

        // Sarf hisobotida tuman ham bor: Chilonzor — ikkita berish. Bo‘lim filtri shu turda ham qo‘llanadi, tozalaymiz.
        pick(ReportKind.ISSUES)
        onNodeWithText("Filtrlarni tozalash (2)").performClick()
        onNodeWithTag("report-district").performClick()
        val districts = services.directory.districts(includeInactive = true)
        val chilonzor = districts.indexOfFirst { it.name == "Chilonzor" }
        onNodeWithTag("report-district-option-${chilonzor + 1}").performClick()
        onNodeWithTag("report-show").performClick()
        onNodeWithTag("report-filter-lines").assert(hasText("Tuman: Chilonzor"))
        onNodeWithTag("report-row-count").assert(hasText("2 ta qator"))
    }

    @Test
    fun periodStartAfterEndIsRefusedAtTheField() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        pick(ReportKind.RECEIPTS)
        onNodeWithTag("report-period-from").performTextInput("10.10.2026")
        onNodeWithTag("report-period-to").performTextInput("01.10.2026")
        onNodeWithTag("report-show").performClick()
        onNodeWithText("Boshlanish sanasi tugash sanasidan keyin bo‘lmasligi kerak.").assertExists()
        onNodeWithTag("report-table").assertDoesNotExist()
        assertTrue(state.messages.none { it.severity == Severity.ERROR })
    }
}

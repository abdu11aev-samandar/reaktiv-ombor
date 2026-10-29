package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import org.dhatim.fastexcel.reader.Row
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.nio.file.Files
import java.nio.file.Path

/** «Barcha reaktivlar» → «Eksport»: sahifa filtrlari bilan «partiyalar bo‘yicha ro‘yxat» Excel/PDF fayli. */
@OptIn(ExperimentalTestApi::class)
class ReagentsExportUiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids
    private lateinit var dialogs: FakeFileDialogs
    private lateinit var out: Path

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

    private fun SkikoComposeUiTest.app(intent: PageIntent? = null): AppState {
        val state = AppState(services, dialogs, Page.REAGENTS)
        if (intent != null) state.navigate(Page.REAGENTS, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun Row.text(i: Int): String = if (i < cellCount) getCellText(i).orEmpty() else ""

    @Test
    fun excelExportUsesThePageFiltersAndSaysWhichApplied() = runDesktopComposeUiTest(1366, 768) {
        val target = out.resolve("partiyalar.xlsx")
        dialogs.saveAnswer = target
        app()
        onNodeWithTag("filter-department").performClick()
        // 0 — «Hammasi», 1 — Gistologiya.
        onNodeWithTag("filter-department-option-1").performClick()
        onNodeWithTag("filter-series").performTextInput("ES")
        // Holat filtri hisobotda yo‘q — xabarda aytiladi.
        onNodeWithTag("state-chip-YELLOW").performClick()
        mainClock.autoAdvance = false
        onNodeWithTag("reagents-export-excel").performClick()
        mainClock.advanceTimeBy(300)
        assertEquals(listOf("save:Reaktivlarning-partiyalar-bo‘yicha-ro‘yxati-2026-10-29.xlsx"), dialogs.calls)

        val rows = ReadableWorkbook(target.toFile(), ReadingOptions(true, false)).use { it.firstSheet.read() }
        assertEquals("Reaktivlarning partiyalar bo‘yicha ro‘yxati", rows[0].text(0))
        assertTrue(rows.any { it.text(0) == "Seriya: ES" })
        assertTrue(rows.any { it.text(0) == "Bo‘lim: Gistologiya" })
        val header = rows.indexOfFirst { it.text(0) == "№" }
        // Seriya ustuni to‘ldirilgan qatorlar — partiyalar (jami qatorlarisiz).
        val series = rows.drop(header + 1).map { it.text(2) }.filter { it.isNotEmpty() }
        assertEquals(listOf("ES-003", "ES-001"), series)

        onNode(hasText("Excel fayliga saqlandi (2 ta qator): partiyalar.xlsx.", substring = true) and hasAnyAncestor(hasTestTag("message-success"))).assertExists()
        onNode(hasText("Qo‘llangan filtrlar: Seriya: ES; Bo‘lim: Gistologiya.", substring = true) and hasAnyAncestor(hasTestTag("message-success"))).assertExists()
        onNode(hasText("Hisobotga o‘tmagan filtrlar: holat.", substring = true) and hasAnyAncestor(hasTestTag("message-success"))).assertExists()
    }

    @Test
    fun pdfExportWithoutFiltersListsEveryBatch() = runDesktopComposeUiTest(1366, 768) {
        val target = out.resolve("partiyalar.pdf")
        dialogs.saveAnswer = target
        app()
        mainClock.autoAdvance = false
        onNodeWithTag("reagents-export-pdf").performClick()
        mainClock.advanceTimeBy(300)
        assertEquals(listOf("save:Reaktivlarning-partiyalar-bo‘yicha-ro‘yxati-2026-10-29.pdf"), dialogs.calls)
        val reader = PdfReader(Files.readAllBytes(target))
        val text = try {
            val extractor = PdfTextExtractor(reader)
            (1..reader.numberOfPages).joinToString(" ") { extractor.getTextFromPage(it) }.replace(Regex("\\s+"), " ")
        } finally {
            reader.close()
        }
        assertTrue(text, text.contains("Filtr: yo‘q"))
        listOf("ES-001", "ES-002", "ES-003", "XL-117", "XL-131", "KN-09", "NX-12", "PL-3", "FR-201", "EO-77").forEach {
            assertTrue("$it PDF da", text.contains(it))
        }
        onNode(hasText("Filtrsiz: barcha arxivlanmagan partiyalar.", substring = true) and hasAnyAncestor(hasTestTag("message-success"))).assertExists()
    }

    @Test
    fun selectedReagentBecomesTheNameFilter() = runDesktopComposeUiTest(1366, 768) {
        val target = out.resolve("etil.xlsx")
        dialogs.saveAnswer = target
        app(PageIntent.ReagentsFiltered(reagentId = ids.etilReagent))
        onNodeWithTag("reagents-export-excel").performClick()
        waitForIdle()
        val rows = ReadableWorkbook(target.toFile(), ReadingOptions(true, false)).use { it.firstSheet.read() }
        assertTrue(rows.any { it.text(0) == "Reaktiv: Etil spirti 96%" })
        val header = rows.indexOfFirst { it.text(0) == "№" }
        assertEquals(listOf("ES-003", "ES-002", "ES-001"), rows.drop(header + 1).map { it.text(2) }.filter { it.isNotEmpty() })
        // Jadval filtrlari o‘zgarmagan.
        onNodeWithTag("reagents-row-${ids.es001}").assertExists()
        onNodeWithTag("reagents-row-${ids.eo77}").assertDoesNotExist()
    }
}

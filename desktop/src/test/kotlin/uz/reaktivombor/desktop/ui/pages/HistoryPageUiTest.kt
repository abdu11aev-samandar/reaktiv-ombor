package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.service.AuditAction
import uz.reaktivombor.desktop.service.AuditFilter
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** «Operatsiyalar tarixi» haqiqiy baza (DemoData) bilan: amallar jurnali filtrlari, reaktiv tarixi va uning eksporti. */
@OptIn(ExperimentalTestApi::class)
class HistoryPageUiTest {
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

    private fun SkikoComposeUiTest.app(page: Page = Page.HISTORY, intent: PageIntent? = null): AppState {
        val state = AppState(services, dialogs, page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun ledgerRow(movementId: Long) = "ledger-row-$movementId"

    private fun Row.text(i: Int): String = if (i < cellCount) getCellText(i).orEmpty() else ""

    // ---------------------------------------------------------------- amallar jurnali

    @Test
    fun auditLogFiltersByActionTypeAndText() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        val all = services.queries.auditLogs()
        onNodeWithTag("audit-row-${all.first().id}").assertExists()
        onNodeWithTag("audit-count").assert(hasText("Ko‘rsatilmoqda: ${all.size} ta yozuv", substring = true))

        onNodeWithTag("audit-action").performClick()
        // 0 — «Hammasi», keyin AuditAction tartibida.
        onNodeWithTag("audit-action-option-${AuditAction.ISSUE_DISTRICT.ordinal + 1}").performClick()
        onNodeWithTag("audit-action").assert(hasText("Tumanga berildi"))
        val districtIssues = services.queries.auditLogs(AuditFilter(action = AuditAction.ISSUE_DISTRICT))
        assertEquals(3, districtIssues.size)
        districtIssues.forEach { onNode(hasText("Tumanga berildi") and hasAnyAncestor(hasTestTag("audit-row-${it.id}"))).assertExists() }
        all.filterNot { e -> districtIssues.any { it.id == e.id } }.forEach { onNodeWithTag("audit-row-${it.id}").assertDoesNotExist() }
        // Yangisi birinchi.
        val top = onNodeWithTag("audit-row-${districtIssues[0].id}").fetchSemanticsNode().positionInRoot.y
        val next = onNodeWithTag("audit-row-${districtIssues[1].id}").fetchSemanticsNode().positionInRoot.y
        assertTrue(top < next)
        onNodeWithTag("audit-count").assert(hasText("Ko‘rsatilmoqda: 3 ta yozuv", substring = true))

        onNodeWithTag("audit-text").performTextInput("Yunusobod")
        val yunusobod = districtIssues.single { it.details.contains("Yunusobod") }
        onNodeWithTag("audit-row-${yunusobod.id}").assertExists()
        onNodeWithTag("audit-count").assert(hasText("Ko‘rsatilmoqda: 1 ta yozuv", substring = true))
        onNode(hasText("Kumush nitrat · KN-09") and hasAnyAncestor(hasTestTag("audit-row-${yunusobod.id}"))).assertExists()
        onNode(hasText("8 g") and hasAnyAncestor(hasTestTag("audit-row-${yunusobod.id}"))).assertExists()

        onNodeWithText("Filtrlarni tozalash (2)").performClick()
        onNodeWithTag("audit-count").assert(hasText("Ko‘rsatilmoqda: ${all.size} ta yozuv", substring = true))
        assertNull(state.unexpectedError)
    }

    @Test
    fun moreButtonLoadsOlderEntries() = runDesktopComposeUiTest(1366, 768) {
        repeat(120) { services.settings.orgName = "Markaz $it" }
        val total = services.queries.auditLogs(limit = 10_000).size
        assertTrue(total in (HISTORY_AUDIT_PAGE + 1)..(2 * HISTORY_AUDIT_PAGE))
        app()
        onNodeWithTag("audit-count").assert(hasText("Ko‘rsatilmoqda: $HISTORY_AUDIT_PAGE ta yozuv", substring = true))
        onNodeWithTag("audit-more").performClick()
        onNodeWithTag("audit-count").assert(hasText("Ko‘rsatilmoqda: $total ta yozuv", substring = true))
        onNodeWithTag("audit-more").assertDoesNotExist()
    }

    @Test
    fun auditEntryDialogOpensTheBatchLedger() = runDesktopComposeUiTest(1366, 768) {
        app()
        val entry = services.queries.auditLogs(AuditFilter(action = AuditAction.RETURN)).single()
        onNodeWithTag("audit-row-${entry.id}").performMouseInput { doubleClick() }
        onNodeWithTag("audit-dialog").assertExists()
        onNodeWithTag("audit-dialog-details").assert(hasText(entry.details))
        onNodeWithTag("audit-dialog-ledger").performClick()
        onNodeWithTag("audit-dialog").assertDoesNotExist()
        onNodeWithTag("ledger-reagent").assert(hasText("Etil spirti 96%"))
        onNodeWithTag("ledger-batch").assert(hasText("ES-001", substring = true))
        services.queries.ledger(ids.es001).forEach { onNodeWithTag(ledgerRow(it.movementId)).assertExists() }
        services.queries.ledger(ids.es002).forEach { onNodeWithTag(ledgerRow(it.movementId)).assertDoesNotExist() }
    }

    // ---------------------------------------------------------------- reaktiv tarixi

    @Test
    fun ledgerRunningBalanceMatchesTheRemainder() = runDesktopComposeUiTest(1366, 768) {
        val biologiya = services.directory.departments().first { it.name == "Biologiya" }.id
        services.stock.issue(
            IssueInput(
                date = today, docNo = "T-60", issuedBy = "Karimova D.",
                lines = listOf(IssueLine(ids.es002, "2", MeasureUnit.L, RecipientType.DEPARTMENT, departmentId = biologiya, receiver = "Rahimov A.")),
            ),
        )
        services.stock.issue(
            IssueInput(
                date = today, docNo = "T-61",
                lines = listOf(IssueLine(ids.es002, "500", MeasureUnit.ML, RecipientType.DISTRICT, districtName = "Chilonzor", receiver = "Aliyev S.")),
            ),
        )
        val wrong = services.queries.issueItems(OpFilter(batchId = ids.es002)).single { it.docNo == "T-61" }
        services.stock.cancelIssueItem(wrong.id, "Xato kiritilgan")

        app(intent = PageIntent.HistoryOf(batchId = ids.es002, reagentId = ids.etilReagent))
        onNodeWithTag("history-tab-ledger").assertExists()
        onNodeWithTag("ledger-reagent").assert(hasText("Etil spirti 96%"))
        onNodeWithTag("ledger-batch").assert(hasText("ES-002", substring = true))

        val ledger = services.queries.ledger(ids.es002)
        assertEquals(4, ledger.size)
        val remainder = services.stock.remainder(ids.es002)
        assertEquals(3_000_000L, remainder)
        assertEquals(remainder, ledger.last().balance)
        // Har bir qatorda shu harakatdan keyingi qoldiq. Bekor qilingan berish va uning bekor qilish qatori qoldiqni
        // o‘zgartirmaydi (tarixda ko‘rinadi, lekin yig‘indiga kirmaydi): 5 l → 3 l → 3 l → 3 l.
        val expected = listOf("5 l", "3 l", "3 l", "3 l")
        ledger.zip(expected).forEach { (row, balance) ->
            assertEquals(balance, qtyText(row.balance, row.unit))
            onNode(hasText(balance) and hasAnyAncestor(hasTestTag(ledgerRow(row.movementId)))).assertExists()
        }
        onNodeWithTag("ledger-group-${ids.es002}").assert(hasText("Joriy qoldiq: ${qtyText(remainder, MeasureUnit.L)}", substring = true))
        onNode(hasText("3 l") and hasAnyAncestor(hasTestTag("ledger-summary"))).assertExists()

        // Bekor qilingan berish belgilangan, bekor qilish qatori sababi bilan.
        val cancelled = ledger.single { it.issueItemId == wrong.id && it.kind.name == "ISSUE" }
        onNode(hasText("bekor qilingan") and hasAnyAncestor(hasTestTag(ledgerRow(cancelled.movementId)))).assertExists()
        onNode(hasText("−0,5 l") and hasAnyAncestor(hasTestTag(ledgerRow(cancelled.movementId)))).assertExists()
        val reversal = ledger.last()
        onNode(hasText("Bekor qilish: berish") and hasAnyAncestor(hasTestTag(ledgerRow(reversal.movementId)))).assertExists()
        onNode(hasText("Sabab: Xato kiritilgan") and hasAnyAncestor(hasTestTag(ledgerRow(reversal.movementId)))).assertExists()
        onNode(hasText("+0,5 l") and hasAnyAncestor(hasTestTag(ledgerRow(reversal.movementId)))).assertExists()
        // Berish qatori: qabul qiluvchi va hujjat.
        val toBio = ledger[1]
        onNode(hasText("Bo‘limga berildi") and hasAnyAncestor(hasTestTag(ledgerRow(toBio.movementId)))).assertExists()
        onNode(hasText("Biologiya bo‘limi — Rahimov A.") and hasAnyAncestor(hasTestTag(ledgerRow(toBio.movementId)))).assertExists()
        onNode(hasText("T-60") and hasAnyAncestor(hasTestTag(ledgerRow(toBio.movementId)))).assertExists()
    }

    @Test
    fun pickingAReagentAndABatchFiltersTheLedger() = runDesktopComposeUiTest(1366, 768) {
        app()
        onNodeWithTag("history-tab-ledger").performClick()
        onNodeWithTag("ledger-empty").assertExists()
        onNodeWithTag("ledger-reagent").performTextInput("Kumush")
        onNodeWithTag("ledger-reagent-item-0").performClick()
        onNodeWithTag("ledger-reagent").assert(hasText("Kumush nitrat"))
        val kn = services.queries.ledger(ids.kn09)
        assertEquals(3, kn.size)
        kn.forEach { onNodeWithTag(ledgerRow(it.movementId)).assertExists() }
        onNode(hasText("12 g") and hasAnyAncestor(hasTestTag(ledgerRow(kn.last().movementId)))).assertExists()

        onNodeWithTag("ledger-reagent").performTextReplacement("Etil spirti 96%")
        services.queries.reagentLedger(ids.etilReagent).forEach { onNodeWithTag(ledgerRow(it.movementId)).assertExists() }
        onNodeWithTag("ledger-batch").performClick()
        val order = services.queries.batchRows(BatchFilter(reagentId = ids.etilReagent, includeArchived = true), today).map { it.batchId }
        onNodeWithTag("ledger-batch-option-${order.indexOf(ids.es001) + 1}").performClick()
        services.queries.ledger(ids.es001).forEach { onNodeWithTag(ledgerRow(it.movementId)).assertExists() }
        services.queries.ledger(ids.es003).forEach { onNodeWithTag(ledgerRow(it.movementId)).assertDoesNotExist() }
        onNode(hasText("6 l") and hasAnyAncestor(hasTestTag("ledger-summary"))).assertExists()
    }

    @Test
    fun ledgerExportsTheHistoryReportToExcelAndPdf() = runDesktopComposeUiTest(1366, 768) {
        val xlsx = out.resolve("tarix.xlsx")
        dialogs.saveAnswer = xlsx
        app(intent = PageIntent.HistoryOf(reagentId = ids.etilReagent))
        onNodeWithTag("history-save-excel").performClick()
        waitForIdle()
        assertEquals(listOf("save:Etil-spirti-96%-operatsiyalar-tarixi-2026-10-29.xlsx"), dialogs.calls)
        val rows = ReadableWorkbook(xlsx.toFile(), ReadingOptions(true, false)).use { it.firstSheet.read() }
        assertEquals("Reaktivning to‘liq operatsiyalar tarixi", rows[0].text(0))
        assertTrue(rows.any { it.text(0) == "Reaktiv: Etil spirti 96%" })
        val header = rows.indexOfFirst { it.text(0) == "№" }
        val data = rows.drop(header + 1).filter { it.text(2).isNotEmpty() }
        assertEquals(services.queries.reagentLedger(ids.etilReagent).size, data.size)

        // Partiya tanlansa — faqat uning seriyasi.
        val pdf = out.resolve("tarix.pdf")
        dialogs.saveAnswer = pdf
        onNodeWithTag("ledger-batch").performClick()
        val order = services.queries.batchRows(BatchFilter(reagentId = ids.etilReagent, includeArchived = true), today).map { it.batchId }
        onNodeWithTag("ledger-batch-option-${order.indexOf(ids.es002) + 1}").performClick()
        onNodeWithTag("history-save-pdf").performClick()
        waitForIdle()
        val reader = PdfReader(Files.readAllBytes(pdf))
        val text = try {
            PdfTextExtractor(reader).getTextFromPage(1).replace(Regex("\\s+"), " ")
        } finally {
            reader.close()
        }
        assertTrue(text, text.contains("Reaktivning to‘liq operatsiyalar tarixi"))
        assertTrue(text, text.contains("Seriya: ES-002"))
        assertTrue(text, !text.contains("ES-001"))
    }

    @Test
    fun batchExportSaysWhenTheSeriesFilterAlsoMatchesAnotherBatch() = runDesktopComposeUiTest(1366, 768) {
        // Hisobotning seriya filtri qism-satr bo‘yicha: ES-001 tanlansa ES-0010 ham kiradi — xabar buni yashirmaydi.
        services.reagents.createBatch(
            NewBatch(
                name = "Etil spirti 96%", series = "ES-0010", receivedOn = today, initialQtyText = "1", unit = MeasureUnit.L,
                expiresOn = LocalDate.of(2028, 1, 1),
            ),
        )
        val xlsx = out.resolve("es001.xlsx")
        dialogs.saveAnswer = xlsx
        app(intent = PageIntent.HistoryOf(batchId = ids.es001))
        mainClock.autoAdvance = false
        onNodeWithTag("history-save-excel").performClick()
        mainClock.advanceTimeBy(300)
        assertTrue(Files.isRegularFile(xlsx))
        onNode(
            hasText("partiya ES-001; seriyasida «ES-001» bor ES-0010 partiyasi ham kirdi", substring = true) and
                hasAnyAncestor(hasTestTag("message-success")),
        ).assertExists()
        val rows = ReadableWorkbook(xlsx.toFile(), ReadingOptions(true, false)).use { it.firstSheet.read() }
        val header = rows.indexOfFirst { it.text(0) == "№" }
        val series = rows.drop(header + 1).map { it.text(2) }.filter { it.isNotEmpty() }.toSet()
        assertEquals(setOf("ES-001", "ES-0010"), series)
    }

    @Test
    fun ledgerExportWithoutReagentShowsTheFieldError() = runDesktopComposeUiTest(1366, 768) {
        app()
        onNodeWithTag("history-tab-ledger").performClick()
        onNodeWithTag("history-save-excel").performClick()
        onNodeWithText("Tarix hisoboti uchun reaktiv nomini kiriting.").assertExists()
        assertTrue(dialogs.calls.isEmpty())
    }

    @Test
    fun batchHistoryDialogOpensThisPage() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.REAGENTS)
        onNodeWithTag("reagents-row-${ids.kn09}").performClick()
        onNode(hasText("Tarix") and hasAnyAncestor(hasTestTag("reagent-actions"))).performClick()
        onNodeWithText("Operatsiyalar tarixida ochish").performClick()
        waitForIdle()
        assertEquals(Page.HISTORY, state.page)
        onNodeWithTag("ledger-batch").assert(hasText("KN-09", substring = true))
        services.queries.ledger(ids.kn09).forEach { onNodeWithTag(ledgerRow(it.movementId)).assertExists() }
    }
}

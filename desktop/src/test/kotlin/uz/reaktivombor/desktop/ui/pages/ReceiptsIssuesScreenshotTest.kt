package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.ReceiptInput
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.io.File
import javax.imageio.ImageIO

/**
 * «Kirim operatsiyalari» va «Sarf va berish» 1366×768 da (haqiqiy dastur + namuna baza): skrinshotlar
 * desktop/build/ui-screens/ops ga yoziladi va ko‘z bilan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class ReceiptsIssuesScreenshotTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids

    @Before
    fun setUp() {
        val (s, i) = DemoData.seeded(tmp.newFolder("data").toPath())
        services = s
        ids = i
        services.settings.actorName = "Karimova D."
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.save(name: String) {
        waitForIdle()
        val dir = File("build/ui-screens/ops").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    /** Bosish: avval ko‘rinadigan joyga aylantiriladi (sahifa uzun — pastdagi maydonlar oynadan tashqarida bo‘ladi). */
    private fun SkikoComposeUiTest.tap(tag: String) {
        val node = onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.app(page: Page, intent: PageIntent? = null): AppState {
        val state = AppState(services, FakeFileDialogs(), page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    // ---------------------------------------------------------------- kirim

    @Test
    fun receipts() = runDesktopComposeUiTest(1366, 768) {
        app(Page.RECEIPTS)
        save("01-receipts-empty")
        onNodeWithTag("receipt-batch").performTextInput("spirt")
        save("02-receipts-picker")
        onNodeWithTag("receipt-batch-option-${ids.es002}").performClick()
        onNodeWithTag("receipt-qty").performTextInput("2")
        onNodeWithTag("receipt-doc").performTextInput("YX-118")
        save("03-receipts-filled")
        onNodeWithTag("receipt-save").performClick()
        save("04-receipts-saved")
        onNodeWithTag("receipts").performScrollTo()
        save("05-receipts-table")
    }

    @Test
    fun receiptsErrorsAndDialogs() = runDesktopComposeUiTest(1366, 768) {
        app(Page.RECEIPTS, PageIntent.ReceiptFor(ids.es001))
        save("06-receipts-intent")
        onNodeWithTag("receipt-save").performClick()
        save("07-receipts-errors")
        onNodeWithTag("receipts").performScrollTo()
        val initial = services.queries.receipts().first { it.batchId == ids.es001 }
        onNodeWithTag("receipts-row-${initial.id}").performClick()
        onNode(hasText("Bekor qilish") and hasAnyAncestor(hasTestTag("receipt-actions"))).performClick()
        save("08-receipts-cancel-refused")
        onNodeWithText("Yopish").performClick()
        val eozin = services.queries.receipts().first { it.batchId == ids.eo77 }
        onNodeWithTag("receipts-row-${eozin.id}").performClick()
        onNode(hasText("Bekor qilish") and hasAnyAncestor(hasTestTag("receipt-actions"))).performClick()
        save("09-receipts-cancel-reason")
        onNodeWithText("Orqaga").performClick()
        onNode(hasText("Tuzatish") and hasAnyAncestor(hasTestTag("receipt-actions"))).performClick()
        save("10-receipts-correct-dialog")
    }

    @Test
    fun receiptsStatusColumns() = runDesktopComposeUiTest(1366, 768) {
        val extra = services.stock.addReceipt(ids.es002, ReceiptInput(DemoData.TODAY, "2", MeasureUnit.L, docNo = "YX-7"))
        services.stock.correctReceipt(extra, ReceiptInput(DemoData.TODAY, "3", MeasureUnit.L, docNo = "YX-7"), "Yuk xatida 3 l")
        val eozin = services.queries.receipts().first { it.batchId == ids.eo77 }
        services.stock.cancelReceipt(eozin.id, "Noto‘g‘ri partiyaga yozilgan")
        app(Page.RECEIPTS)
        tap("receipts-filter-cancelled")
        onNodeWithTag("receipts").performScrollTo()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyAncestor(hasTestTag("receipts")))
            .onFirst()
            .performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(1200f, 0f) }
        save("05b-receipts-status")
    }

    // ---------------------------------------------------------------- berish

    @Test
    fun issuesEmpty() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ISSUES)
        save("11-issues-empty")
        tap("issue-goto-history")
        save("12-issues-history")
        tap("tab-returns")
        save("13-issues-returns")
    }

    @Test
    fun issuesMultiLineWithWarnings() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ISSUES, PageIntent.IssueFrom(ids.es001))
        onNodeWithTag("issue-doc").performTextInput("T-55")
        onNodeWithTag("issue-line-1-qty").performTextInput("200")
        tap("issue-line-1-qty-unit")
        tap("issue-line-1-qty-unit-1")
        tap("issue-line-1-department")
        tap("issue-line-1-department-option-0")
        tap("issue-add-line")
        onNodeWithTag("issue-line-2-reagent").performTextInput("Xloroform")
        tap("issue-line-2-batch")
        save("14-issues-batch-select")
        tap("issue-line-2-batch-option-${ids.xl117}")
        onNodeWithTag("issue-line-2-qty").performTextInput("50")
        tap("issue-line-2-type-1")
        onNodeWithTag("issue-line-2-district").performTextInput("Olmazor")
        tap("issue-add-line")
        onNodeWithTag("issue-line-3-reagent").performTextInput("Kumush nitrat")
        onNodeWithTag("issue-line-3-qty").performTextInput("15")
        tap("issue-line-3-type-2")
        save("15-issues-filled-warnings")
        onNodeWithTag("issue-save").performScrollTo()
        save("16-issues-filled-bottom")
    }

    @Test
    fun issuesDialogs() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ISSUES)
        tap("issue-fefo-split")
        onNodeWithTag("split-reagent").performTextInput("Etil spirti 96%")
        onNodeWithTag("split-qty").performTextInput("4,5")
        save("17-issues-fefo-split")
        tap("split-apply")
        save("18-issues-after-split")
        tap("issue-line-1-department")
        tap("issue-line-1-department-option-0")
        tap("issue-line-2-department")
        tap("issue-line-2-department-option-0")
        tap("issue-save")
        save("19-issues-success")
        // Yangi qatorlar jadval boshida — kerakli partiya qidiruv bilan topiladi.
        onNodeWithTag("issues-filter-text").performScrollTo()
        onNodeWithTag("issues-filter-text").performTextInput("ES-001")
        onNodeWithTag("issue-items").performScrollTo()
        tap("issue-items-row-${ids.gistologiyaIssueItem}")
        onNode(hasText("Qaytarish") and hasAnyAncestor(hasTestTag("issue-item-actions"))).performClick()
        onNodeWithTag("ret-qty").performTextInput("3")
        save("20-issues-return-dialog")
        onNode(hasText("Bekor qilish") and hasAnyAncestor(isDialog())).performClick()
        onNode(hasText("Bekor qilish") and hasAnyAncestor(hasTestTag("issue-item-actions"))).performClick()
        save("21-issues-cancel-refused")
        onNodeWithText("Yopish").performClick()
        val biologiya = services.queries.issueItems().first { it.batchId == ids.es001 && it.recipientName == "Biologiya" }
        tap("issue-items-row-${biologiya.id}")
        onNode(hasText("Tuzatish") and hasAnyAncestor(hasTestTag("issue-item-actions"))).performClick()
        save("22-issues-correct-dialog")
    }

    @Test
    fun issuesExpiredConfirmation() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ISSUES, PageIntent.IssueFrom(ids.xl117))
        onNodeWithTag("issue-line-1-qty").performTextInput("100")
        tap("issue-line-1-department")
        tap("issue-line-1-department-option-2")
        tap("issue-save")
        save("23-issues-expired-confirm")
        onNode(hasText("Baribir berish") and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        // Tarixning o‘ng ustunlari: belgilar (FEFO / muddati o‘tgan), holat (bekor qilingan + sabab, tuzatish), izoh.
        val biologiya = services.queries.issueItems().first { it.batchId == ids.es001 && it.recipientName == "Biologiya" }
        services.stock.correctIssueItem(
            biologiya.id,
            IssueLine(ids.es001, "800", MeasureUnit.ML, RecipientType.DEPARTMENT, departmentId = biologiya.departmentId, receiver = "Rahimov A."),
            biologiya.issuedOn,
            "Talabnomada 800 ml",
        )
        val chilonzor = services.queries.issueItems().first { it.batchId == ids.es001 && it.recipientName == "Chilonzor" }
        services.stock.cancelIssueItem(chilonzor.id, "Boshqa hujjat bilan qayta yozildi")
        tap("issues-filter-cancelled")
        onNodeWithTag("issue-items").performScrollTo()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyAncestor(hasTestTag("issue-items")))
            .onFirst()
            .performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(1200f, 0f) }
        save("24-issues-history-status")
    }
}

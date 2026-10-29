package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
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
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * «Hisobotlar», «Operatsiyalar tarixi» va «Barcha reaktivlar» eksporti 1366×768 da (haqiqiy dastur + namuna baza):
 * skrinshotlar desktop/build/ui-screens/reportsui ga yoziladi va ko‘z bilan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class ReportsScreenshotTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids
    private lateinit var dialogs: FakeFileDialogs

    @Before
    fun setUp() {
        val (s, i) = DemoData.seeded(tmp.newFolder("data").toPath())
        services = s
        ids = i
        dialogs = FakeFileDialogs(saveAnswer = tmp.newFolder("out").toPath().resolve("hisobot.xlsx"))
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.save(name: String) {
        waitForIdle()
        val dir = File("build/ui-screens/reportsui").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    /** Soat to‘xtatilgan holatda (xabar yopilmasin) — waitForIdle siz. */
    private fun SkikoComposeUiTest.saveNow(name: String) {
        val dir = File("build/ui-screens/reportsui").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SkikoComposeUiTest.app(page: Page, intent: PageIntent? = null): AppState {
        val state = AppState(services, dialogs, page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    @Test
    fun reports() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REPORTS)
        save("01-reports-start")
        onNodeWithTag("report-kind-BATCHES").performClick()
        onNodeWithTag("report-show").performClick()
        save("02-reports-batches-preview")
        onNodeWithTag("report-kind-ISSUES").performClick()
        onNodeWithTag("report-show").performClick()
        save("03-reports-issues-preview")
        onNodeWithTag("report-kind-HISTORY").performScrollTo().performClick()
        onNodeWithTag("report-show").performClick()
        save("04-reports-history-error")
        onNodeWithTag("report-name").performTextInput("Etil spirti 96%")
        onNodeWithTag("report-period-from").performTextInput("01.04.2026")
        onNodeWithTag("report-show").performClick()
        save("05-reports-history-preview")
        mainClock.autoAdvance = false
        onNodeWithTag("report-save-excel").performClick()
        mainClock.advanceTimeBy(300)
        saveNow("06-reports-saved-message")
    }

    @Test
    fun reportsDepartmentAndExpired() = runDesktopComposeUiTest(1366, 768) {
        services.settings.orgName = "Respublika sud-tibbiy ekspertiza ilmiy-amaliy markazi"
        app(Page.REPORTS)
        onNodeWithTag("report-kind-BY_DEPARTMENT").performClick()
        onNodeWithTag("report-show").performClick()
        save("07-reports-by-department")
        onNodeWithTag("report-kind-EXPIRING").performClick()
        onNodeWithTag("report-show").performClick()
        save("08-reports-expiring")
        onNodeWithTag("report-kind-MOVEMENT").performScrollTo().performClick()
        onNodeWithTag("report-period-from").performTextInput("01.06.2026")
        onNodeWithTag("report-period-to").performTextInput("30.09.2026")
        onNodeWithTag("report-show").performClick()
        save("09-reports-movement")
    }

    @Test
    fun auditLog() = runDesktopComposeUiTest(1366, 768) {
        app(Page.HISTORY)
        save("10-history-audit")
        onNodeWithTag("audit-action").performClick()
        save("11-history-audit-action-menu")
    }

    @Test
    fun auditDialog() = runDesktopComposeUiTest(1366, 768) {
        app(Page.HISTORY)
        val entry = services.queries.auditLogs().first { it.batchId == ids.es001 }
        onNodeWithTag("audit-row-${entry.id}").performMouseInput { doubleClick() }
        save("12-history-audit-dialog")
    }

    @Test
    fun ledger() = runDesktopComposeUiTest(1366, 768) {
        app(Page.HISTORY, PageIntent.HistoryOf(reagentId = ids.etilReagent))
        save("13-history-ledger-reagent")
        onNodeWithTag("ledger-batch").performClick()
        save("14-history-ledger-batch-menu")
    }

    @Test
    fun ledgerWithCancelAndCorrection() = runDesktopComposeUiTest(1366, 768) {
        val items = services.queries.issueItems(OpFilter(batchId = ids.es001))
        services.stock.cancelIssueItem(items.single { it.docNo == "T-30" }.id, "Hujjat bekor qilindi")
        val bio = items.single { it.docNo == "T-24" }
        val biologiya = services.directory.departments().first { it.name == "Biologiya" }.id
        services.stock.correctIssueItem(
            bio.id,
            IssueLine(ids.es001, "0,8", MeasureUnit.L, RecipientType.DEPARTMENT, departmentId = biologiya, receiver = "Rahimov A."),
            LocalDate.of(2026, 5, 20),
            "Miqdor xato yozilgan",
        )
        app(Page.HISTORY, PageIntent.HistoryOf(batchId = ids.es001))
        save("18-history-ledger-cancelled")
        onNodeWithTag("history-tab-audit").performClick()
        save("19-history-audit-after-cancel")
    }

    @Test
    fun ledgerEmpty() = runDesktopComposeUiTest(1366, 768) {
        app(Page.HISTORY)
        onNodeWithTag("history-tab-ledger").performClick()
        save("15-history-ledger-empty")
    }

    @Test
    fun reagentsExport() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        save("16-reagents-export-buttons")
        onNodeWithTag("filter-series").performTextInput("ES")
        onNodeWithTag("state-chip-GREEN").performClick()
        mainClock.autoAdvance = false
        onNodeWithTag("reagents-export-excel").performClick()
        mainClock.advanceTimeBy(300)
        saveNow("17-reagents-export-message")
    }
}

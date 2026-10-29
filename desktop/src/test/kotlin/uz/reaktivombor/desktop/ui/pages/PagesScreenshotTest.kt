package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.io.File
import javax.imageio.ImageIO

/**
 * Haqiqiy dastur (ReaktivControlApp + namuna baza) 1366×768 da: skrinshotlar desktop/build/ui-screens/pages ga
 * yoziladi va ko‘z bilan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class PagesScreenshotTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids

    @Before
    fun setUp() {
        val (s, i) = DemoData.seeded(tmp.newFolder("data").toPath())
        services = s
        ids = i
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.save(name: String) {
        waitForIdle()
        val dir = File("build/ui-screens/pages").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SkikoComposeUiTest.app(page: Page): AppState {
        val state = AppState(services, FakeFileDialogs(), page)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    @Test
    fun dashboard() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DASHBOARD)
        save("01-dashboard")
        onNodeWithTag("recent-issue-5").performScrollTo()
        save("02-dashboard-bottom")
        onNodeWithTag("notifications").performClick()
        save("03-notifications")
    }

    @Test
    fun reagents() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        save("04-reagents")
        onNodeWithTag("reagents-columns").performClick()
        save("05-reagents-column-menu")
    }

    @Test
    fun reagentsFiltered() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        onNodeWithTag("state-chip-EXPIRED").performClick()
        onNodeWithTag("state-chip-RED").performClick()
        onNodeWithText("Kengaytirilgan filtr").performClick()
        save("06-reagents-filtered")
    }

    @Test
    fun reagentsEditAndHistoryDialogs() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        onNodeWithTag("reagents-row-${ids.es001}").performClick()
        save("07-reagents-selected")
        onNode(hasText("Tahrirlash") and hasAnyAncestor(hasTestTag("reagent-actions"))).performClick()
        save("08-edit-dialog")
        onNodeWithTag("edit-dialog-close").performClick()
        onNode(hasText("Tarix") and hasAnyAncestor(hasTestTag("reagent-actions"))).performClick()
        save("09-history-dialog")
    }

    @Test
    fun addReagentDuplicateAndSuccess() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Etil spirti 96%")
        onNodeWithTag("add-series").performTextInput("ES-001")
        onNodeWithTag("add-expires").performTextInput("01.09.2027")
        onNodeWithTag("add-qty").performTextInput("2")
        onNodeWithTag("add-qty-unit").performClick()
        onNodeWithTag("add-qty-unit-0").performClick()
        onNodeWithTag("add-department").performClick()
        save("12-add-department-select")
        onNodeWithTag("add-department-option-1").performClick()
        onNodeWithTag("add-save").performClick()
        save("13-add-duplicate-dialog")
        onNodeWithText("Orqaga").performClick()
        onNodeWithTag("add-series").performTextReplacement("ES-004")
        onNodeWithTag("add-save").performClick()
        save("14-add-success")
    }

    @Test
    fun addReagent() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ADD_REAGENT)
        save("10-add-reagent")
        onNodeWithTag("add-save").performClick()
        save("11-add-reagent-errors")
    }
}

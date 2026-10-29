package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.Dimension
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
 * Bo‘limlar, tumanlar, yaroqlilik muddati va umumiy zaxiralar sahifalari (va oynalari) 1366×768 da: skrinshotlar
 * desktop/build/ui-screens/places ga yoziladi va ko‘z bilan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class PlacesScreenshotTest {
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
        val dir = File("build/ui-screens/places").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SkikoComposeUiTest.app(page: Page, intent: PageIntent? = null): AppState {
        val state = AppState(services, FakeFileDialogs(), page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun department(name: String) = services.directory.departments().single { it.name == name }.id

    private fun district(name: String) = services.directory.districts().single { it.name == name }.id

    @Test
    fun departments() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DEPARTMENTS)
        save("01-departments")
        onNodeWithTag("department-tab-1").performClick()
        save("02-departments-issued")
        onNodeWithTag("department-tab-2").performClick()
        save("03-departments-history")
        onNodeWithTag("departments-add").performClick()
        save("04-departments-add-dialog")
    }

    @Test
    fun departmentsSudKimyoAndDialogs() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DEPARTMENTS, PageIntent.DepartmentOf(department("Sud-kimyo")))
        save("05-departments-sud-kimyo")
        onNodeWithTag("departments-rename").performClick()
        onNodeWithTag("name-dialog-field").performTextClearance()
        onNodeWithTag("name-dialog-field").performTextInput("Gistologiya")
        onNode(hasText("Saqlash") and hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        save("06-departments-rename-conflict")
        onNode(hasText("Bekor qilish") and hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        onNodeWithTag("departments-toggle").performClick()
        save("07-departments-deactivate-confirm")
    }

    @Test
    fun districts() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DISTRICTS)
        save("08-districts-all")
        onNodeWithTag("district-tab-1").performClick()
        save("09-districts-per-reagent")
    }

    @Test
    fun districtChilonzorAndReturn() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DISTRICTS, PageIntent.DistrictOf(district("Chilonzor")))
        save("10-districts-chilonzor")
        val item = services.queries.issueItems().first { it.districtName == "Chilonzor" }
        onNodeWithTag("district-items-row-${item.id}").performClick()
        save("11-districts-selected")
        onNode(hasText("Qaytarish") and hasAnyAncestor(hasTestTag("district-actions"))).performClick()
        save("12-districts-return-dialog")
        onNodeWithTag("return-qty").performTextClearance()
        onNodeWithTag("return-qty").performTextInput("5")
        onNodeWithTag("return-save").performClick()
        save("24-districts-return-error")
    }

    @Test
    fun inactiveDepartmentInTheList() = runDesktopComposeUiTest(1366, 768) {
        val id = department("Morfologiya")
        services.directory.setActive(uz.reaktivombor.desktop.service.PlaceKind.DEPARTMENT, id, false)
        app(Page.DEPARTMENTS, PageIntent.DepartmentOf(id))
        save("25-departments-inactive")
    }

    @Test
    fun expiry() = runDesktopComposeUiTest(1366, 768) {
        app(Page.EXPIRY)
        save("13-expiry")
        onNodeWithTag("expiry-row-${ids.xl117}").performClick()
        onNode(hasText("Hisobdan chiqarish") and hasAnyAncestor(hasTestTag("expiry-actions"))).performClick()
        save("14-expiry-writeoff-dialog")
        onNodeWithTag("writeoff-save").performClick()
        save("15-expiry-writeoff-errors")
        onNodeWithTag("writeoff-dialog-close").performClick()
        onNode(hasText("Arxivlash") and hasAnyAncestor(hasTestTag("expiry-actions"))).performClick()
        save("23-expiry-archive-confirm")
    }

    @Test
    fun expiryAllAndIntent() = runDesktopComposeUiTest(1366, 768) {
        app(Page.EXPIRY)
        onNodeWithTag("state-chip-ALL").performClick()
        save("16-expiry-all")
    }

    @Test
    fun expiryFromNotification() = runDesktopComposeUiTest(1366, 768) {
        app(Page.EXPIRY, PageIntent.FilterExpiry(setOf(uz.reaktivombor.desktop.core.ExpiryState.RED), ids.kn09))
        save("17-expiry-intent")
    }

    @Test
    fun stock() = runDesktopComposeUiTest(1366, 768) {
        app(Page.STOCK)
        save("18-stock")
        onNodeWithTag("stock-row-${ids.etilReagent}-${Dimension.VOLUME.name}").performClick()
        onNode(hasText("Partiyalar") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        save("19-stock-batches-dialog")
        onNodeWithTag("stock-batches-dialog-close").performClick()
        onNodeWithTag("stock-row-${ids.xloroformReagent}-${Dimension.VOLUME.name}").performClick()
        onNode(hasText("Partiyalar") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        save("22-stock-batches-expired-inside")
    }

    @Test
    fun stockMinAndFilter() = runDesktopComposeUiTest(1366, 768) {
        app(Page.STOCK)
        onNodeWithTag("stock-row-${ids.etilReagent}-${Dimension.VOLUME.name}").performClick()
        onNode(hasText("Minimal qoldiqni belgilash") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        save("20-stock-min-dialog")
        onNodeWithTag("stock-min-dialog-close").performClick()
        onNodeWithTag("stock-below-min").performClick()
        save("21-stock-below-min")
    }
}

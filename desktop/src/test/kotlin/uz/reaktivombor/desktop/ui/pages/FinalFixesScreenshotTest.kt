package uz.reaktivombor.desktop.ui.pages

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
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
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppShell
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import uz.reaktivombor.desktop.ui.ShellNotification
import uz.reaktivombor.desktop.ui.theme.AppTheme
import uz.reaktivombor.desktop.ui.theme.Severity
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/** Yakuniy tuzatishlar skrinshotlari (1366×768 va 1280×720): desktop/build/ui-screens/fixui1 — ko‘z bilan tekshiriladi. */
@OptIn(ExperimentalTestApi::class)
class FinalFixesScreenshotTest {
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
        val dir = File("build/ui-screens/fixui1").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SkikoComposeUiTest.app(page: Page, intent: PageIntent? = null): AppState {
        val state = AppState(services, FakeFileDialogs(), page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun dept(name: String) = services.directory.departments(includeInactive = true).first { it.name == name }.id

    private fun addBatch(name: String, series: String, expires: String, qty: String, department: Long = dept("Gistologiya"), minStock: String? = null): Long =
        services.reagents.createBatch(
            NewBatch(
                name = name, series = series, receivedOn = LocalDate.parse("2026-01-05"), initialQtyText = qty, unit = MeasureUnit.L,
                expiresOn = LocalDate.parse(expires), departmentId = department, storageLocation = "Ombor", responsible = "Karimova D.",
                minStockText = minStock, minStockUnit = if (minStock != null) MeasureUnit.L else null, receivedBy = "Karimova D.",
            ),
        )

    private fun seedLongNames(): String {
        val long = "Natriy gidroksid 0,1 N standart-titr eritmasi (fiksanal)"
        addBatch(long, "N-1", "2026-10-01", "1", minStock = "100")
        addBatch(long, "N-2", "2026-11-15", "1")
        addBatch(long, "N-3", "2026-12-20", "1")
        addBatch(long, "N-4", "2028-01-01", "1")
        val longer = "Etilendiamintetrasirka kislotaning dinatriyli tuzi dihidrati, analitik sinf, qayta kristallangan, fiksanal ampulada"
        addBatch(longer, "ED-1", "2026-10-01", "2")
        addBatch(longer, "ED-2", "2026-11-10", "2")
        return long
    }

    private fun collapsed(w: Int, h: Int, tag: String) = runDesktopComposeUiTest(w, h) {
        seedLongNames()
        app(Page.REAGENTS)
        onNodeWithText("Hammasini yopish").performClick()
        save("reagents-collapsed-$tag")
    }

    @Test
    fun collapsedGroups1366() = collapsed(1366, 768, "1366")

    @Test
    fun collapsedGroups1280() = collapsed(1280, 720, "1280")

    private fun dashboard(w: Int, h: Int, tag: String) = runDesktopComposeUiTest(w, h) {
        services.directory.addDepartment("Bo‘sh bo‘lim")
        repeat(10) { i ->
            val name = "Qo‘shimcha bo‘lim ${i + 1}"
            services.directory.addDepartment(name)
            addBatch("Metanol", "MT-$i", "2028-01-01", "${i + 1}", department = dept(name))
        }
        app(Page.DASHBOARD)
        save("dashboard-$tag")
    }

    @Test
    fun dashboard1366() = dashboard(1366, 768, "1366")

    @Test
    fun dashboard1280() = dashboard(1280, 720, "1280")

    @Test
    fun inactiveDepartmentInTheFilter() = runDesktopComposeUiTest(1366, 768) {
        services.directory.setActive(PlaceKind.DEPARTMENT, dept("Biologiya"), false)
        app(Page.REAGENTS)
        onNodeWithTag("filter-department").performClick()
        save("reagents-department-filter-inactive")
    }

    @Test
    fun unsavedChangesDialog() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Metanol")
        onNodeWithTag("nav-STOCK").performClick()
        save("unsaved-dialog")
    }

    @Test
    fun longComboSuggestion() = runDesktopComposeUiTest(1366, 768) {
        addBatch("Etilendiamintetrasirka kislotaning dinatriyli tuzi dihidrati, analitik sinf, qayta kristallangan, ampulada", "ED-1", "2028-01-01", "1")
        addBatch("Etilen glikol", "EG-1", "2028-01-01", "1")
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Etilen")
        save("combo-long-suggestion")
    }

    @Test
    fun ledgerWithALongName() = runDesktopComposeUiTest(1366, 768) {
        val long = "Natriy gidroksid 0,1 N standart-titr eritmasi (fiksanal) ampulada, 10 ta komplekt, analitik sinf, qayta kristallangan"
        val batch = addBatch(long, "N-9", "2028-01-01", "5")
        services.stock.issue(
            IssueInput(
                date = DemoData.TODAY, docNo = "T-90", issuedBy = "Karimova D.",
                lines = listOf(IssueLine(batch, "1", MeasureUnit.L, RecipientType.DEPARTMENT, departmentId = dept("Biologiya"), receiver = "Rahimov A.")),
            ),
        )
        val reagentId = services.reagents.list().first { it.name == long }.id
        app(Page.HISTORY, PageIntent.HistoryOf(batchId = batch, reagentId = reagentId))
        save("ledger-long-name")
    }

    @Test
    fun bellWithManyNotifications() = runDesktopComposeUiTest(1366, 768) {
        val items = List(45) { ShellNotification("Bildirishnoma $it: partiyaning muddati o‘tgan", Severity.WARNING, if (it % 3 == 0) Page.STOCK else Page.EXPIRY) }
        setContent { AppTheme { AppShell(Page.DASHBOARD, {}, items, today = DemoData.TODAY) { Text("sahifa") } } }
        onNodeWithTag("notifications").performClick()
        save("bell-capped")
    }

    @Test
    fun reportsInvalidPeriodDate() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REPORTS)
        onNodeWithTag("report-kind-RECEIPTS").performScrollTo().performClick()
        onNodeWithTag("report-period-from").performTextInput("31.02.2026")
        onNodeWithTag("report-show").performClick()
        save("reports-invalid-period")
    }

    @Test
    fun reagentsQuantityTooltipHeader() = runDesktopComposeUiTest(1366, 768) {
        addBatch("Suv", "SU-1", "2028-01-01", "123456789")
        app(Page.REAGENTS)
        save("reagents-quantities")
    }
}

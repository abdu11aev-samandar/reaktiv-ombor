package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
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
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.PlaceKind
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
 * Yakuniy ko‘rik tuzatishlari ekranda (haqiqiy dastur + namuna baza, 1366×768 va 1280×720): bekor qilingan/tuzatilgan
 * yozuvlar belgisi, kirimlarda yaroqlilik muddati, nofaol tuman, «Sof berilgan», katta miqdorlar, takroriy qaytarish.
 * Skrinshotlar desktop/build/ui-screens/fixui2 ga yoziladi va ko‘z bilan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class OpsStatusAndPlacesScreenshotTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids
    private val today = DemoData.TODAY

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
        val dir = File("build/ui-screens/fixui2").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

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

    /** Bekor qilingan Eozin kirimi, 2 l → 3 l tuzatilgan ES-002 kirimi; bekor qilingan T-30 (Chilonzor) qatori va qaytarish. */
    private fun cancelAndCorrect() {
        val eozin = services.queries.receipts().single { it.batchId == ids.eo77 }
        services.stock.cancelReceipt(eozin.id, "Noto‘g‘ri partiyaga yozilgan")
        val extra = services.stock.addReceipt(ids.es002, ReceiptInput(today, "2", MeasureUnit.L, docNo = "YX-7"))
        services.stock.correctReceipt(extra, ReceiptInput(today, "3", MeasureUnit.L, docNo = "YX-7"), "Yuk xatida 3 l")
        val chilonzor = services.queries.issueItems().single { it.batchId == ids.es001 && it.recipientName == "Chilonzor" }
        services.stock.cancelIssueItem(chilonzor.id, "Boshqa hujjat bilan qayta yozildi")
        val biologiya = services.queries.issueItems().single { it.batchId == ids.es001 && it.recipientName == "Biologiya" }
        services.stock.correctIssueItem(
            biologiya.id,
            IssueLine(ids.es001, "800", MeasureUnit.ML, RecipientType.DEPARTMENT, departmentId = biologiya.departmentId, receiver = "Rahimov A."),
            biologiya.issuedOn,
            "Talabnomada 800 ml",
        )
        val ret = services.queries.returns().single { it.issueItemId == ids.gistologiyaIssueItem }
        services.stock.cancelReturn(ret.id, "Xato kiritilgan")
    }

    private fun receiptsCancelled(prefix: String) = runDesktopComposeUiTest(if (prefix == "a") 1366 else 1280, if (prefix == "a") 768 else 720) {
        cancelAndCorrect()
        app(Page.RECEIPTS)
        tap("receipts-filter-cancelled")
        onNodeWithTag("receipts").performScrollTo()
        save("$prefix-01-receipts-cancelled-default-scroll")
    }

    private fun issuesCancelled(prefix: String) = runDesktopComposeUiTest(if (prefix == "a") 1366 else 1280, if (prefix == "a") 768 else 720) {
        cancelAndCorrect()
        app(Page.ISSUES)
        tap("issues-filter-cancelled")
        onNodeWithTag("issue-items").performScrollTo()
        save("$prefix-02-issues-cancelled-default-scroll")
        tap("tab-returns")
        onNodeWithTag("returns").performScrollTo()
        save("$prefix-03-returns-cancelled")
    }

    @Test
    fun receiptsCancelled1366() = receiptsCancelled("a")

    @Test
    fun receiptsCancelled1280() = receiptsCancelled("b")

    @Test
    fun issuesCancelled1366() = issuesCancelled("a")

    @Test
    fun issuesCancelled1280() = issuesCancelled("b")

    @Test
    fun inactiveDistrictAndDepartmentFilter() = runDesktopComposeUiTest(1366, 768) {
        val yunusobod = services.directory.districts().single { it.name == "Yunusobod" }
        services.directory.setActive(PlaceKind.DISTRICT, yunusobod.id, false)
        val biologiya = services.directory.departments().single { it.name == "Biologiya" }
        services.directory.setActive(PlaceKind.DEPARTMENT, biologiya.id, false)
        app(Page.ISSUES)
        onNodeWithTag("issue-line-1-reagent").performTextInput("Natriy xlorid")
        onNodeWithTag("issue-line-1-qty").performTextInput("100")
        tap("issue-line-1-qty-unit")
        tap("issue-line-1-qty-unit-1") // g
        tap("issue-line-1-type-1")
        onNodeWithTag("issue-line-1-district").performTextInput("yunusobod")
        save("a-04-issue-inactive-district")
        tap("issue-save")
        save("a-05-issue-inactive-district-save")
        onNodeWithTag("issue-line-1-district").performTextReplacement("Olmazor")
        save("a-06-issue-new-district")
        tap("issues-filter-department")
        save("a-07-issues-department-filter-inactive")
    }

    @Test
    fun placesLabelsAndLargeQuantities() = runDesktopComposeUiTest(1366, 768) {
        val gistologiya = services.directory.departments().single { it.name == "Gistologiya" }
        fun batch(name: String, series: String, qty: String, unit: MeasureUnit) = services.reagents.createBatch(
            NewBatch(
                name = name, series = series, receivedOn = today.minusDays(3), initialQtyText = qty, unit = unit,
                expiresOn = today.plusYears(2), departmentId = gistologiya.id, storageLocation = "Ombor", receivedBy = "Karimova D.",
            ),
        )
        batch("Glitserin", "GL-1", "125000,25", MeasureUnit.ML)
        batch("Glitserin", "GL-2", "12345,678", MeasureUnit.G)
        batch("Glitserin", "GL-3", "10500,125", MeasureUnit.L)
        val state = app(Page.STOCK)
        save("a-08-stock-sof-berilgan-large")
        state.navigate(Page.DEPARTMENTS, PageIntent.DepartmentOf(gistologiya.id))
        waitForIdle()
        save("a-09-departments-batches-large")
        tap("department-tab-1")
        save("a-10-departments-issued")
        state.navigate(Page.STOCK)
        waitForIdle()
        val etil = services.reagents.batch(ids.es001)!!.reagentId
        tap("stock-row-$etil-VOLUME")
        onNode(hasText("Partiyalar") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        save("a-11-stock-batches-dialog")
    }

    @Test
    fun duplicateReturnConfirmations() = runDesktopComposeUiTest(1366, 768) {
        val chilonzor = services.directory.districts().single { it.name == "Chilonzor" }
        val item = services.queries.issueItems(OpFilter(batchId = ids.fr201, recipientType = RecipientType.DISTRICT)).single()
        val state = app(Page.DISTRICTS, PageIntent.DistrictOf(chilonzor.id))
        onNodeWithTag("district-items-row-${item.id}").performClick()
        repeat(2) {
            onNode(hasText("Qaytarish") and hasAnyAncestor(hasTestTag("district-actions"))).performClick()
            waitForIdle()
            onNodeWithTag("return-qty").performTextReplacement("0,5")
            onNodeWithTag("return-save").performClick()
            waitForIdle()
        }
        save("a-12-districts-duplicate-return")

        val biologiya = services.queries.issueItems().single { it.batchId == ids.es001 && it.recipientName == "Biologiya" }
        state.navigate(Page.ISSUES)
        waitForIdle()
        onNodeWithTag("issue-items").performScrollTo()
        tap("issue-items-row-${biologiya.id}")
        repeat(2) {
            onNode(hasText("Qaytarish") and hasAnyAncestor(hasTestTag("issue-item-actions"))).performClick()
            waitForIdle()
            onNodeWithTag("ret-qty").performTextInput("0,5")
            onNodeWithTag("ret-save").performClick()
            waitForIdle()
        }
        save("a-13-issues-duplicate-return")
    }
}

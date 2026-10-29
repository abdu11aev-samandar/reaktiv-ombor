package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueItemRow
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

/**
 * «Sarf va berish» haqiqiy baza (DemoData) bilan: ko‘p qatorli berish bitta operatsiyada, qoldiq tekshiruvi, yangi tuman,
 * FEFO tavsiyasi va ogohlantirishi, muddati o‘tgan partiya tasdig‘i, taqsimlash, qaytarish, bekor qilish, tuzatish —
 * natija bazadan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class IssuesPageUiTest {
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

    private fun SkikoComposeUiTest.app(intent: PageIntent? = null): AppState {
        val state = AppState(services, FakeFileDialogs(), Page.ISSUES)
        if (intent != null) state.navigate(Page.ISSUES, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    /** Sahifa uzun — avval ko‘rinadigan joyga aylantirib, keyin bosiladi (ochilgan ro‘yxat bandlari aylantirilmaydi). */
    private fun SkikoComposeUiTest.tap(tag: String) {
        val node = onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.type(tag: String, text: String) {
        val node = onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performTextInput(text)
        waitForIdle()
    }

    /** Qatorni to‘ldirish: reaktiv nomi (FEFO partiya o‘zi tanlanadi), miqdor, kerak bo‘lsa birlik va bo‘lim (tartib raqami). */
    private fun SkikoComposeUiTest.fillLine(n: Int, reagent: String, qty: String, unitIndex: Int? = null, departmentIndex: Int? = null) {
        type("issue-line-$n-reagent", reagent)
        type("issue-line-$n-qty", qty)
        if (unitIndex != null) {
            tap("issue-line-$n-qty-unit")
            tap("issue-line-$n-qty-unit-$unitIndex")
        }
        if (departmentIndex != null) {
            tap("issue-line-$n-department")
            tap("issue-line-$n-department-option-$departmentIndex")
        }
    }

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun SkikoComposeUiTest.selectItem(id: Long) {
        onNodeWithTag("issue-items").performScrollTo()
        tap("issue-items-row-$id")
    }

    private fun SkikoComposeUiTest.itemAction(text: String) {
        onNode(hasText(text) and hasAnyAncestor(hasTestTag("issue-item-actions"))).performClick()
        waitForIdle()
    }

    private fun remainder(batchId: Long) = services.stock.remainder(batchId)

    private fun allItems(): List<IssueItemRow> = services.queries.issueItems(OpFilter(includeCancelled = true))

    private fun dept(name: String) = services.directory.departments().single { it.name == name }.id

    @Test
    fun fourLinesFromALitreBatchAreSavedAsOneOperation() = runDesktopComposeUiTest(1366, 768) {
        app()
        val itemsBefore = allItems()
        type("issue-doc", "T-60")
        onNodeWithTag("issue-issuer").assert(hasText("Karimova D.")) // beruvchi — sozlamalardagi xodim
        // Bo‘limlar tartibi: Gistologiya, Biologiya, Sud-kimyo, Morfologiya. Birlik: l, ml → ml.
        fillLine(1, "Formalin 10%", "200", unitIndex = 1, departmentIndex = 0)
        tap("issue-add-line")
        fillLine(2, "Formalin 10%", "300", unitIndex = 1, departmentIndex = 1)
        tap("issue-add-line")
        fillLine(3, "Formalin 10%", "150", unitIndex = 1, departmentIndex = 2)
        tap("issue-add-line")
        fillLine(4, "Formalin 10%", "100", unitIndex = 1, departmentIndex = 3)
        // FEFO partiya o‘zi tanlandi (Formalinning yagona partiyasi) va jami bir o‘lchov turida ko‘rsatiladi.
        onNodeWithTag("issue-line-4-batch").assert(hasText("FR-201", substring = true))
        onNodeWithTag("issue-totals").assert(hasText("Jami: Formalin 10% — 750 ml"))
        tap("issue-save")

        assertEquals(17_250_000L, remainder(ids.fr201)) // 18 l − 0,75 l
        val created = allItems().filter { old -> itemsBefore.none { it.id == old.id } }
        assertEquals(4, created.size)
        assertEquals(1, created.map { it.issueId }.distinct().size) // bitta hujjat (issues) — to‘rt qator
        val byDepartment = created.associate { it.departmentName to it.qty }
        assertEquals(mapOf("Gistologiya" to 200_000L, "Biologiya" to 300_000L, "Sud-kimyo" to 150_000L, "Morfologiya" to 100_000L), byDepartment)
        created.forEach {
            assertEquals("T-60", it.docNo)
            assertEquals("Karimova D.", it.issuedBy)
            assertEquals(MeasureUnit.ML, it.enteredUnit)
            assertEquals(today, it.issuedOn)
            assertFalse(it.fefoOverride)
        }
        onNode(hasText("Berish №${created.first().issueId} saqlandi") and hasAnyAncestor(hasTestTag("issue-success"))).assertExists()
        // Shakl keyingi hujjat uchun tozalandi: bitta bo‘sh qator, sana va beruvchi qoladi.
        onNodeWithTag("issue-line-2").assertDoesNotExist()
        onNodeWithTag("issue-doc").assert(hasText("Masalan: T-55"))
        onNodeWithTag("issue-issuer").assert(hasText("Karimova D."))
        onNodeWithTag("issue-date").assert(hasText("29.10.2026"))
        onNodeWithTag("issue-items").performScrollTo()
        created.forEach { onNodeWithTag("issue-items-row-${it.id}").assertExists() }
    }

    @Test
    fun insufficientStockOnOneLineSavesNothing() = runDesktopComposeUiTest(1366, 768) {
        app()
        val itemsBefore = allItems().size
        fillLine(1, "Formalin 10%", "1", departmentIndex = 0)
        tap("issue-add-line")
        fillLine(2, "Kumush nitrat", "15", departmentIndex = 2)
        // Jonli tekshiruv: KN-09 da 12 g bor — qator xato, saqlash tugmasi o‘chiq.
        onNodeWithText("Yetarli emas: mavjud 12 g").assertExists()
        onNodeWithTag("issue-error-summary").assert(hasText("2-qatorda", substring = true))
        onNodeWithTag("issue-save").assertIsNotEnabled()

        // Miqdor to‘g‘rilandi, lekin shu orada qoldiq boshqa joyda kamaydi — servis rad etadi, hech narsa saqlanmaydi.
        onNodeWithTag("issue-line-2-qty").performTextReplacement("10")
        waitForIdle()
        onNodeWithTag("issue-save").assertIsEnabled()
        services.stock.issue(
            IssueInput(today, lines = listOf(IssueLine(ids.kn09, "5", MeasureUnit.G, RecipientType.DEPARTMENT, departmentId = dept("Sud-kimyo")))),
        )
        val afterOther = allItems().size
        tap("issue-save")
        onNode(hasText("qoldiq yetarli emas", substring = true) and hasAnyAncestor(hasTestTag("issue-line-2"))).assertExists()
        onNodeWithTag("issue-line-1-message").assertDoesNotExist()
        assertEquals(itemsBefore + 1, afterOther)
        assertEquals(afterOther, allItems().size)
        assertEquals(18_000_000L, remainder(ids.fr201))
        assertEquals(7_000L, remainder(ids.kn09))
        // Jonli tekshiruv yangilandi: endi 7 g.
        onNodeWithText("Yetarli emas: mavjud 7 g").assertExists()
    }

    @Test
    fun linesOnTheSameBatchAreSummedInTheLiveCheck() = runDesktopComposeUiTest(1366, 768) {
        app()
        fillLine(1, "Kumush nitrat", "8", departmentIndex = 0)
        tap("issue-add-line")
        fillLine(2, "Kumush nitrat", "5", departmentIndex = 1)
        // Ikkala qatorda ham — ikkalasi bir partiyadan.
        onAllNodesWithText("Yetarli emas: mavjud 12 g (bu partiyadan jami 13 g so‘ralgan)").assertCountEquals(2)
        onNodeWithTag("issue-save").assertIsNotEnabled()
        onNodeWithTag("issue-line-2-qty").performTextReplacement("4")
        waitForIdle()
        tap("issue-save")
        assertEquals(0L, remainder(ids.kn09))
    }

    @Test
    fun aNewDistrictTypedInALineIsCreated() = runDesktopComposeUiTest(1366, 768) {
        app()
        assertTrue(services.directory.districts().none { it.name == "Olmazor" })
        fillLine(1, "Natriy xlorid", "100", unitIndex = 1) // kg, g → g
        tap("issue-line-1-type-1") // Tuman
        type("issue-line-1-district", "Olmazor")
        onNodeWithText("Yangi tuman qo‘shiladi").assertExists()
        type("issue-line-1-org", "Olmazor tuman bo‘limi")
        type("issue-line-1-receiver", "Nazarov O.")
        tap("issue-save")

        val district = services.directory.districts().single { it.name == "Olmazor" }
        val item = allItems().single { it.districtId == district.id }
        assertEquals(RecipientType.DISTRICT, item.recipientType)
        assertEquals(100_000L, item.qty)
        assertEquals("Olmazor tuman bo‘limi", item.organization)
        assertEquals("Nazarov O.", item.receiver)
        assertEquals(1_650_000L, remainder(ids.nx12))
    }

    @Test
    fun fefoRecommendationIsPreselectedAndAnOverrideIsWarnedAndFlagged() = runDesktopComposeUiTest(1366, 768) {
        app()
        fillLine(1, "Etil spirti 96%", "1", departmentIndex = 0)
        // Muddati eng yaqin, o‘tmagan partiya — ES-003 — o‘zi tanlanadi va belgilanadi.
        onNodeWithTag("issue-line-1-batch").assert(hasText("ES-003", substring = true))
        onNodeWithTag("issue-line-1-batch").assert(hasText("FEFO tavsiyasi", substring = true))
        onNodeWithTag("issue-line-1-fefo").assertDoesNotExist()

        tap("issue-line-1-batch")
        tap("issue-line-1-batch-option-${ids.es001}")
        onNode(hasText("ES-003 partiyasi", substring = true) and hasAnyAncestor(hasTestTag("issue-line-1-fefo"))).assertExists()
        tap("issue-save")

        val item = allItems().single { it.batchId == ids.es001 && it.issuedOn == today }
        assertTrue(item.fefoOverride)
        assertEquals(5_000_000L, remainder(ids.es001))
        assertEquals(3_000_000L, remainder(ids.es003))
    }

    @Test
    fun anExpiredBatchNeedsAnExplicitConfirmation() = runDesktopComposeUiTest(1366, 768) {
        app(PageIntent.IssueFrom(ids.xl117))
        onNodeWithTag("issue-line-1-batch").assert(hasText("XL-117", substring = true))
        onNodeWithTag("issue-line-1-expired").assertExists()
        type("issue-line-1-qty", "100")
        tap("issue-line-1-department")
        tap("issue-line-1-department-option-2")
        tap("issue-save")
        onNodeWithText("Muddati o‘tgan partiyadan berish").assertExists()
        assertEquals(320_000L, remainder(ids.xl117))

        dialogButton("Orqaga").performClick()
        waitForIdle()
        assertEquals(320_000L, remainder(ids.xl117))
        tap("issue-save")
        dialogButton("Baribir berish").performClick()
        waitForIdle()
        assertEquals(220_000L, remainder(ids.xl117))
        val item = allItems().single { it.batchId == ids.xl117 && it.issuedOn == today }
        assertTrue(item.expiredOverride)
        assertEquals("Sud-kimyo", item.departmentName)
    }

    @Test
    fun writeOffNeedsAReason() = runDesktopComposeUiTest(1366, 768) {
        app(PageIntent.IssueFrom(ids.xl117))
        type("issue-line-1-qty", "320")
        tap("issue-line-1-type-2") // Hisobdan chiqarish
        // Hisobdan chiqarishda muddati o‘tgan partiya tasdig‘i kerak emas.
        onNodeWithTag("issue-line-1-expired").assertDoesNotExist()
        tap("issue-save")
        onNodeWithText("Hisobdan chiqarish sababini yozing.").assertExists()
        assertEquals(320_000L, remainder(ids.xl117))
        type("issue-line-1-note", "Muddati o‘tgan")
        tap("issue-save")
        assertEquals(0L, remainder(ids.xl117))
        val item = allItems().single { it.batchId == ids.xl117 && it.issuedOn == today }
        assertEquals(RecipientType.WRITE_OFF, item.recipientType)
        assertEquals("Muddati o‘tgan", item.note)
        assertFalse(item.expiredOverride)
    }

    @Test
    fun duplicateIssueNeedsConfirmation() = runDesktopComposeUiTest(1366, 768) {
        app()
        fillLine(1, "Petri likopchasi", "5", departmentIndex = 1)
        tap("issue-save")
        assertEquals(75_000L, remainder(ids.pl3))
        fillLine(1, "Petri likopchasi", "5", departmentIndex = 1)
        tap("issue-save")
        onNodeWithText("Takroriy berish").assertExists()
        assertEquals(75_000L, remainder(ids.pl3))
        dialogButton("Baribir saqlash").performClick()
        waitForIdle()
        assertEquals(70_000L, remainder(ids.pl3))
    }

    @Test
    fun fefoSplitReplacesTheLinesAndSaves() = runDesktopComposeUiTest(1366, 768) {
        app()
        tap("issue-fefo-split")
        type("split-reagent", "Etil spirti 96%")
        type("split-qty", "4,5")
        onNode(hasText("→ 3 l") and hasAnyAncestor(hasTestTag("split-part-0"))).assertExists()
        onNode(hasText("→ 1,5 l") and hasAnyAncestor(hasTestTag("split-part-1"))).assertExists()
        tap("split-department")
        tap("split-department-option-0")
        tap("split-apply")
        onNodeWithTag("split-dialog").assertDoesNotExist()
        onNodeWithTag("issue-line-1-batch").assert(hasText("ES-003", substring = true))
        onNodeWithTag("issue-line-2-batch").assert(hasText("ES-002", substring = true))
        tap("issue-save")

        assertEquals(0L, remainder(ids.es003))
        assertEquals(3_500_000L, remainder(ids.es002))
        val created = allItems().filter { it.issuedOn == today }
        assertEquals(1, created.map { it.issueId }.distinct().size)
        assertTrue(created.all { it.departmentName == "Gistologiya" && !it.fefoOverride })
    }

    @Test
    fun aMissingStockIntentIsExplained() = runDesktopComposeUiTest(1366, 768) {
        services.stock.writeOff(ids.eo77, today, "250", MeasureUnit.ML, "Idish singan")
        app(PageIntent.IssueFrom(ids.eo77))
        onNodeWithTag("issue-line-1-message").assertExists()
    }

    @Test
    fun returnDialogIncreasesTheRemainder() = runDesktopComposeUiTest(1366, 768) {
        app()
        selectItem(ids.gistologiyaIssueItem)
        itemAction("Qaytarish")
        onNodeWithTag("return-dialog").assertExists()
        type("ret-qty", "3")
        onNodeWithText("Ko‘pi bilan 2,5 l qaytarish mumkin.").assertExists()
        onNodeWithTag("ret-save").assertIsNotEnabled()
        onNodeWithTag("ret-qty").performTextReplacement("1")
        type("ret-reason", "Ishlatilmadi")
        tap("ret-save")

        onNodeWithTag("return-dialog").assertDoesNotExist()
        assertEquals(7_000_000L, remainder(ids.es001))
        val item = allItems().single { it.id == ids.gistologiyaIssueItem }
        assertEquals(1_500_000L, item.returnedQty)
        val ret = services.queries.returns(OpFilter()).single { it.issueItemId == item.id && it.reason == "Ishlatilmadi" }
        assertEquals(1_000_000L, ret.qty)
        assertEquals("Karimova D.", ret.receivedBy)
    }

    @Test
    fun cancellingAnItemWithReturnsIsRefusedWithAMessage() = runDesktopComposeUiTest(1366, 768) {
        app()
        selectItem(ids.gistologiyaIssueItem)
        itemAction("Bekor qilish")
        onNodeWithText("Bekor qilib bo‘lmaydi").assertExists()
        onNodeWithText("qaytarilgan", substring = true).assertExists()
        onNodeWithTag("reason").assertDoesNotExist()
        dialogButton("Qaytarishlarni ko‘rsatish").performClick()
        waitForIdle()
        val ret = services.queries.returns(OpFilter()).single { it.issueItemId == ids.gistologiyaIssueItem }
        onNodeWithTag("returns-row-${ret.id}").assertExists()
        assertFalse(allItems().single { it.id == ids.gistologiyaIssueItem }.cancelled)
        assertEquals(6_000_000L, remainder(ids.es001))
    }

    @Test
    fun cancelAnIssueItemAndThenAReturn() = runDesktopComposeUiTest(1366, 768) {
        app()
        val biologiya = allItems().single { it.batchId == ids.es001 && it.departmentName == "Biologiya" }
        selectItem(biologiya.id)
        itemAction("Bekor qilish")
        onNodeWithTag("reason").performTextInput("Boshqa hujjatga yozilgan")
        dialogButton("Bekor qilish").performClick()
        waitForIdle()
        val cancelled = allItems().single { it.id == biologiya.id }
        assertTrue(cancelled.cancelled)
        assertEquals("Boshqa hujjatga yozilgan", cancelled.cancelReason)
        assertEquals(7_000_000L, remainder(ids.es001))
        onNodeWithTag("issue-items-row-${biologiya.id}").assertDoesNotExist()

        // Qaytarishlar: Gistologiyadan qaytgan 0,5 l bekor qilinadi — qoldiqdan yana ayiriladi.
        tap("tab-returns")
        val ret = services.queries.returns(OpFilter()).single { it.issueItemId == ids.gistologiyaIssueItem }
        tap("returns-row-${ret.id}")
        onNode(hasText("Bekor qilish") and hasAnyAncestor(hasTestTag("return-actions"))).performClick()
        waitForIdle()
        onNodeWithTag("reason").performTextInput("Xato kiritilgan")
        dialogButton("Bekor qilish").performClick()
        waitForIdle()
        assertTrue(services.queries.returns(OpFilter(includeCancelled = true)).single { it.id == ret.id }.cancelled)
        assertEquals(6_500_000L, remainder(ids.es001))
    }

    @Test
    fun correctAnIssueItemReplacesIt() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        val biologiya = allItems().single { it.batchId == ids.es001 && it.departmentName == "Biologiya" }
        selectItem(biologiya.id)
        itemAction("Tuzatish")
        onNodeWithTag("issue-correct-dialog").assertExists()
        onNodeWithTag("ic-receiver").assert(hasText("Rahimov A."))
        onNodeWithTag("ic-qty").performTextReplacement("800")
        tap("ic-qty-unit")
        tap("ic-qty-unit-1") // ml
        tap("ic-save")
        onNodeWithText("Tuzatish sababi kiritilishi shart.").assertExists()
        assertEquals(6_000_000L, remainder(ids.es001))

        type("ic-reason", "Talabnomada 800 ml")
        tap("ic-save")
        onNodeWithTag("issue-correct-dialog").assertDoesNotExist()
        assertEquals(6_200_000L, remainder(ids.es001))
        val all = allItems()
        assertTrue(all.single { it.id == biologiya.id }.cancelled)
        val replacement = all.single { it.correctsId == biologiya.id }
        assertEquals(800_000L, replacement.qty)
        assertEquals(MeasureUnit.ML, replacement.enteredUnit)
        assertEquals("Biologiya", replacement.departmentName)
        assertEquals(biologiya.docNo, replacement.docNo)
        onNode(hasText("Faol (tuzatish)") and hasAnyAncestor(hasTestTag("issue-items-row-${replacement.id}"))).assertExists()
        assertNull(state.unexpectedError)
    }
}

package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.ReceiptInput
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp

/** «Kirim operatsiyalari» haqiqiy baza (DemoData) bilan: qo‘shimcha kirim, bekor qilish, tuzatish — bazadagi qoldiq tekshiriladi. */
@OptIn(ExperimentalTestApi::class)
class ReceiptsPageUiTest {
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
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.app(page: Page = Page.RECEIPTS, intent: PageIntent? = null): AppState {
        val state = AppState(services, FakeFileDialogs(), page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    /** Sahifa uzun — avval ko‘rinadigan joyga aylantirib, keyin bosiladi. */
    private fun SkikoComposeUiTest.tap(tag: String) {
        val node = onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    /** Jadval qatorini tanlash: avval jadval sahifada ko‘rinadigan joyga, keyin qator jadval ichida aylantiriladi. */
    private fun SkikoComposeUiTest.selectRow(receiptId: Long) {
        onNodeWithTag("receipts").performScrollTo()
        tap("receipts-row-$receiptId")
    }

    private fun SkikoComposeUiTest.rowAction(text: String) {
        onNode(hasText(text) and hasAnyAncestor(hasTestTag("receipt-actions"))).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun remainder(batchId: Long) = services.stock.remainder(batchId)

    private fun receiptsOf(batchId: Long) = services.queries.receipts(OpFilter(batchId = batchId, includeCancelled = true))

    @Test
    fun extraReceiptForAnExistingBatchAddsToItsRemainder() = runDesktopComposeUiTest(1366, 768) {
        app()
        onNodeWithTag("receipt-batch").performTextInput("es-002")
        tap("receipt-batch-option-${ids.es002}")
        onNode(hasText("Biologiya / Sovutgich") and hasAnyAncestor(hasTestTag("receipt-batch-summary"))).assertExists()
        onNodeWithTag("receipt-qty").performTextInput("2,5")
        onNodeWithTag("receipt-doc").performTextInput("YX-118")
        onNodeWithTag("receipt-receiver").performTextInput("Rahimov A.")
        tap("receipt-save")

        onNode(hasText("Partiyaning yangi qoldig‘i: 7,5 l.", substring = true) and hasAnyAncestor(hasTestTag("receipt-success"))).assertExists()
        assertEquals(7_500_000L, remainder(ids.es002))
        val saved = receiptsOf(ids.es002).first { !it.isInitial }
        assertEquals("YX-118", saved.docNo)
        assertEquals("Rahimov A.", saved.receivedBy)
        assertEquals("Sovutgich", saved.storageLocation) // partiyaning saqlash joyi taklif qilinadi
        assertEquals(today, saved.receivedOn)
        assertEquals("2,5", saved.enteredQty)
        onNodeWithTag("receipts-row-${saved.id}").assertExists()
        // Shakl keyingi kirim uchun tozalandi (sana qoladi).
        onNodeWithTag("receipt-batch-summary").assertDoesNotExist()
        onNodeWithTag("receipt-date").assert(hasText("29.10.2026"))
    }

    @Test
    fun receiptForIntentPreselectsTheBatchAndAcceptsMillilitres() = runDesktopComposeUiTest(1366, 768) {
        app(intent = PageIntent.ReceiptFor(ids.es001))
        onNodeWithTag("receipt-batch").assert(hasText("Etil spirti 96% · ES-001 · 01.09.2027"))
        onNodeWithTag("receipt-qty").performTextInput("500")
        tap("receipt-qty-unit")
        tap("receipt-qty-unit-1") // l, ml → ml
        tap("receipt-save")
        assertEquals(6_500_000L, remainder(ids.es001))
        val saved = receiptsOf(ids.es001).first { !it.isInitial }
        assertEquals(MeasureUnit.ML, saved.enteredUnit)
        assertEquals("500", saved.enteredQty)
        assertEquals(500_000L, saved.qty)
    }

    @Test
    fun missingBatchAndQuantityAreShownNextToTheFields() = runDesktopComposeUiTest(1366, 768) {
        app()
        val before = services.queries.receipts(OpFilter(includeCancelled = true)).size
        tap("receipt-save")
        onNodeWithText("Partiyani tanlang", substring = true).assertExists()
        onNodeWithTag("receipt-batch").performTextInput("NX-12")
        tap("receipt-batch-option-${ids.nx12}")
        tap("receipt-save")
        onNodeWithText("Kirim miqdori kiritilishi shart.").assertExists()
        onNodeWithTag("receipt-error-summary").assertExists()
        assertEquals(before, services.queries.receipts(OpFilter(includeCancelled = true)).size)
        assertEquals(1_750_000L, remainder(ids.nx12))
    }

    @Test
    fun typingAnotherTextDropsTheChosenBatch() = runDesktopComposeUiTest(1366, 768) {
        app(intent = PageIntent.ReceiptFor(ids.es001))
        onNodeWithTag("receipt-batch-summary").assertExists()
        onNodeWithTag("receipt-batch").performTextReplacement("xlor")
        onNodeWithTag("receipt-batch-summary").assertDoesNotExist()
        onNodeWithTag("receipt-batch-option-${ids.xl131}").assertExists()
        onNodeWithTag("receipt-batch-option-${ids.es001}").assertDoesNotExist()
    }

    @Test
    fun duplicateReceiptNeedsConfirmation() = runDesktopComposeUiTest(1366, 768) {
        app(intent = PageIntent.ReceiptFor(ids.es002))
        onNodeWithTag("receipt-qty").performTextInput("1")
        tap("receipt-save")
        assertEquals(6_000_000L, remainder(ids.es002))

        onNodeWithTag("receipt-batch").performTextInput("ES-002")
        tap("receipt-batch-option-${ids.es002}")
        onNodeWithTag("receipt-qty").performTextInput("1")
        tap("receipt-save")
        onNodeWithText("Takroriy kirim").assertExists()
        assertEquals(6_000_000L, remainder(ids.es002))
        dialogButton("Baribir saqlash").performClick()
        waitForIdle()
        assertEquals(7_000_000L, remainder(ids.es002))
        assertEquals(2, receiptsOf(ids.es002).count { !it.isInitial })
    }

    @Test
    fun cancelAReceiptWithAReason() = runDesktopComposeUiTest(1366, 768) {
        val id = services.stock.addReceipt(ids.es002, ReceiptInput(today, "2", MeasureUnit.L, docNo = "YX-1"))
        app()
        selectRow(id)
        rowAction("Bekor qilish")
        onNodeWithText("Kirimni bekor qilish").assertExists()
        onNodeWithTag("reason").performTextInput("Xato kiritilgan")
        dialogButton("Bekor qilish").performClick()
        waitForIdle()

        val receipt = receiptsOf(ids.es002).single { it.id == id }
        assertTrue(receipt.cancelled)
        assertEquals("Xato kiritilgan", receipt.cancelReason)
        assertEquals(5_000_000L, remainder(ids.es002))
        onNodeWithTag("receipts-row-$id").assertDoesNotExist()
        tap("receipts-filter-cancelled")
        onNode(hasText("Bekor qilingan") and hasAnyAncestor(hasTestTag("receipts-row-$id"))).assertExists()
    }

    @Test
    fun cancellingAReceiptWhoseStockWasIssuedIsRefusedWithAnExplanation() = runDesktopComposeUiTest(1366, 768) {
        app()
        val initial = receiptsOf(ids.es001).single()
        selectRow(initial.id)
        rowAction("Bekor qilish")
        onNodeWithText("Kirimni bekor qilib bo‘lmaydi").assertExists()
        onNodeWithText("partiyadan reaktiv berilgan", substring = true).assertExists()
        // Sabab so‘ralmaydi — rad etish oldindan tushuntiriladi.
        onNodeWithTag("reason").assertDoesNotExist()
        dialogButton("Partiya tarixini ko‘rish").performClick()
        waitForIdle()
        onNodeWithTag("history-dialog").assertExists()
        assertFalse(receiptsOf(ids.es001).single().cancelled)
        assertEquals(6_000_000L, remainder(ids.es001))
    }

    @Test
    fun correctAReceiptReplacesItWithANewOne() = runDesktopComposeUiTest(1366, 768) {
        val id = services.stock.addReceipt(ids.es002, ReceiptInput(today, "2", MeasureUnit.L, docNo = "YX-7"))
        app()
        selectRow(id)
        rowAction("Tuzatish")
        onNodeWithTag("receipt-correct-dialog").assertExists()
        onNodeWithTag("rc-doc").assert(hasText("YX-7"))
        onNodeWithTag("rc-qty").performTextReplacement("3")
        tap("rc-save")
        onNodeWithText("Tuzatish sababi kiritilishi shart.").assertExists()
        assertEquals(7_000_000L, remainder(ids.es002))

        onNodeWithTag("rc-reason").performTextInput("Yuk xatida 3 l")
        tap("rc-save")
        onNodeWithTag("receipt-correct-dialog").assertDoesNotExist()
        assertEquals(8_000_000L, remainder(ids.es002))
        val all = receiptsOf(ids.es002)
        assertTrue(all.single { it.id == id }.cancelled)
        val replacement = all.single { it.correctsId == id }
        assertFalse(replacement.cancelled)
        assertEquals(3_000_000L, replacement.qty)
        assertEquals("YX-7", replacement.docNo)
        onNode(hasText("Faol (tuzatish)") and hasAnyAncestor(hasTestTag("receipts-row-${replacement.id}"))).assertExists()
        tap("receipts-filter-cancelled")
        onNode(hasText("Tuzatilgan") and hasAnyAncestor(hasTestTag("receipts-row-$id"))).assertExists()
    }

    @Test
    fun filtersAndRowActionsNavigate() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        val xl = receiptsOf(ids.xl117).single()
        onNodeWithTag("receipts-filter-text").performTextInput("xloroform")
        onNodeWithTag("receipts-row-${xl.id}").assertExists()
        onNodeWithTag("receipts-row-${receiptsOf(ids.es001).single().id}").assertDoesNotExist()
        selectRow(xl.id)
        rowAction("Tarix")
        onNodeWithTag("history-dialog").assertExists()
        onNodeWithTag("history-dialog-close").performClick()
        waitForIdle()
        tap("receipt-new-batch")
        assertEquals(Page.ADD_REAGENT, state.page)
        assertNull(state.unexpectedError)
    }
}

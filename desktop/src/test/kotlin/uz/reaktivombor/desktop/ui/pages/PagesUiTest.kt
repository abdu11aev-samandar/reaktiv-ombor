package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp

/** Haqiqiy baza (DemoData) bilan butun dastur: navigatsiya, bosh sahifa, «Barcha reaktivlar», «Reaktiv qo‘shish». */
@OptIn(ExperimentalTestApi::class)
class PagesUiTest {
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

    private fun SkikoComposeUiTest.app(page: Page = Page.DASHBOARD, intent: PageIntent? = null): AppState {
        val state = AppState(services, FakeFileDialogs(), page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun row(batchId: Long) = "reagents-row-$batchId"

    private fun SkikoComposeUiTest.selectAndAct(batchId: Long, action: String) {
        onNodeWithTag(row(batchId)).performClick()
        onNode(hasText(action) and hasAnyAncestor(hasTestTag("reagent-actions"))).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    // ---------------------------------------------------------------- navigatsiya

    @Test
    fun everyNavigationItemOpensItsPage() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        Page.entries.forEach { page ->
            onNodeWithTag("nav-${page.name}").performClick()
            waitForIdle()
            assertEquals(page, state.page)
            assertTrue("${page.title}: sarlavha", onAllNodesWithText(page.title).fetchSemanticsNodes().size >= 2)
            assertNull("${page.title}: kutilmagan xato", state.unexpectedError)
        }
        // Keyingi bosqich sahifalari hozircha vaqtinchalik.
        onNodeWithTag("nav-RECEIPTS").performClick()
        onNodeWithText("Bu sahifa keyingi bosqichda to‘ldiriladi").assertExists()
    }

    @Test
    fun notificationOpensTheExpiryPageWithItsBatch() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        onNodeWithTag("notifications").performClick()
        onNodeWithTag("notification-badge", useUnmergedTree = true).assert(hasText("3"))
        onNodeWithTag("notification-0").performClick()
        waitForIdle()
        assertEquals(Page.EXPIRY, state.page)
        assertEquals(PageIntent.FilterExpiry(setOf(ExpiryState.EXPIRED), ids.xl117), state.intent)
    }

    // ---------------------------------------------------------------- bosh sahifa

    @Test
    fun dashboardNumbersComeFromTheQuery() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        val d = services.queries.dashboard(today)
        onNodeWithTag("card-types").assert(hasText(d.reagentTypes.toString()))
        onNodeWithTag("card-active").assert(hasText(d.activeBatches.toString()))
        onNodeWithTag("card-expiring").assert(hasText(d.expiringBatches.toString()))
        onNodeWithTag("card-expired").assert(hasText(d.expiredBatches.toString()))
        onNodeWithTag("card-below-min").assert(hasText(d.belowMinReagents.toString()))
        onNodeWithTag("card-expiring").assert(hasText("1 ta qizil, 3 ta sariq"))
        assertEquals(listOf(7, 10, 4, 1, 1), listOf(d.reagentTypes, d.activeBatches, d.expiringBatches, d.expiredBatches, d.belowMinReagents))
        onNodeWithTag("expired-banner").assertExists()
        // Bo‘limlar grafigi: hajm bo‘yicha qoldiq (litr bilan gramm qo‘shilmaydi).
        onNodeWithText("9,25 l").assertExists()
        onNodeWithText("1,32 l").assertExists()
        onNodeWithTag("card-active").performClick()
        waitForIdle()
        assertEquals(Page.REAGENTS, state.page)
        assertEquals(PageIntent.ReagentsFiltered(withStockOnly = true), state.intent)
    }

    // ---------------------------------------------------------------- barcha reaktivlar

    @Test
    fun sameNamesAreAdjacentInFefoOrderWithGroupTotal() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        onNodeWithTag("reagents-group-${ids.etilReagent}").assert(hasText("Umumiy qoldiq: 14 l", substring = true))
        onNodeWithTag("reagents-group-${ids.etilReagent}").assert(hasText("3 ta partiya"))
        val lines = onAllNodes(
            SemanticsMatcher("jadval qatorlari") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("reagents-row-") || it.startsWith("reagents-group-") } == true
            },
        ).fetchSemanticsNodes().sortedBy { it.positionInRoot.y }.map { it.config[SemanticsProperties.TestTag] }
        val start = lines.indexOf("reagents-group-${ids.etilReagent}")
        assertTrue(lines.toString(), start >= 0)
        assertEquals(listOf(row(ids.es003), row(ids.es002), row(ids.es001)), lines.subList(start + 1, start + 4))
        assertTrue("guruhdan keyin boshqa guruh", lines.getOrNull(start + 4)?.startsWith("reagents-group-") ?: true)
    }

    @Test
    fun statusFilterShowsOnlyMatchingBatches() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        onNodeWithTag("state-chip-EXPIRED").performClick()
        onNodeWithTag(row(ids.xl117)).assertIsDisplayed()
        onNodeWithTag(row(ids.es001)).assertDoesNotExist()
        onNodeWithTag(row(ids.xl131)).assertDoesNotExist()
        onNodeWithText("1 ta reaktiv, 1 ta partiya — filtr bo‘yicha").assertExists()
        onNodeWithTag("state-chip-RED").performClick()
        onNodeWithTag(row(ids.kn09)).assertIsDisplayed()
        onNodeWithTag("state-chip-EXPIRED").performClick()
        onNodeWithTag("state-chip-RED").performClick()
        onNodeWithTag(row(ids.es001)).assertExists()
    }

    @Test
    fun textAndDepartmentFiltersAndClearing() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        onNodeWithTag("filter-text").performTextInput("xlorofo")
        onNodeWithTag(row(ids.xl117)).assertExists()
        onNodeWithTag(row(ids.es001)).assertDoesNotExist()
        onNodeWithText("Filtrlarni tozalash (1)").performClick()
        onNodeWithTag(row(ids.es001)).assertExists()
        onNodeWithTag("filter-department").performClick()
        // 0 — «Hammasi», 1 — Gistologiya.
        onNodeWithTag("filter-department-option-1").performClick()
        onNodeWithTag(row(ids.es001)).assertExists()
        onNodeWithTag(row(ids.es002)).assertDoesNotExist()
        onNodeWithTag(row(ids.eo77)).assertExists()
    }

    @Test
    fun reagentIntentFiltersToOneName() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS, PageIntent.ReagentsFiltered(reagentId = ids.etilReagent))
        onNodeWithTag(row(ids.es001)).assertExists()
        onNodeWithTag(row(ids.eo77)).assertDoesNotExist()
        onNodeWithText("Reaktiv: Etil spirti 96%").performClick()
        onNodeWithTag(row(ids.eo77)).assertExists()
    }

    @Test
    fun editDialogValidatesAndSaves() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        selectAndAct(ids.es002, "Tahrirlash")
        onNodeWithTag("edit-dialog").assertExists()
        onNodeWithTag("edit-series").performTextClearance()
        onNodeWithTag("edit-save").performClick()
        onNodeWithText("Seriya raqami kiritilishi shart.").assertExists()
        onNodeWithTag("edit-dialog").assertExists()
        assertEquals("ES-002", services.reagents.batch(ids.es002)!!.series)

        onNodeWithTag("edit-series").performTextInput("ES-002A")
        onNodeWithTag("edit-storage").performTextReplacement("Sovutgich 2")
        onNodeWithTag("edit-formula").performTextReplacement("C2H5OH")
        onNodeWithTag("edit-save").performClick()
        waitForIdle()
        onNodeWithTag("edit-dialog").assertDoesNotExist()
        val batch = services.reagents.batch(ids.es002)!!
        assertEquals("ES-002A", batch.series)
        assertEquals("Sovutgich 2", batch.storageLocation)
        assertEquals("C2H5OH", services.reagents.get(ids.etilReagent)!!.formula)
        onNode(hasText("ES-002A") and hasAnyAncestor(hasTestTag(row(ids.es002)))).assertExists()
    }

    @Test
    fun archiveHidesTheBatchAndArchivedFilterShowsIt() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        selectAndAct(ids.eo77, "Arxivlash")
        dialogButton("Arxivlash").performClick()
        waitForIdle()
        assertTrue(services.reagents.batch(ids.eo77)!!.archived)
        onNodeWithTag(row(ids.eo77)).assertDoesNotExist()
        onNodeWithTag("filter-archived").performClick()
        onNode(hasText("Arxiv") and hasAnyAncestor(hasTestTag(row(ids.eo77)))).assertExists()
        selectAndAct(ids.eo77, "Arxivdan chiqarish")
        assertFalse(services.reagents.batch(ids.eo77)!!.archived)
    }

    @Test
    fun deleteFollowsTheHistoryRule() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        // ES-001 da berish va qaytarish bor — o‘chirilmaydi, arxivlash taklif qilinadi.
        selectAndAct(ids.es001, "O‘chirish")
        onNodeWithText("O‘chirib bo‘lmaydi").assertExists()
        dialogButton("Bekor qilish").performClick()
        assertTrue(services.reagents.batch(ids.es001) != null)
        // EO-77 — faqat boshlang‘ich kirim: o‘chiriladi.
        selectAndAct(ids.eo77, "O‘chirish")
        onNodeWithText("Partiyani o‘chirish").assertExists()
        dialogButton("O‘chirish").performClick()
        waitForIdle()
        assertNull(services.reagents.batch(ids.eo77))
        onNodeWithTag(row(ids.eo77)).assertDoesNotExist()
    }

    @Test
    fun rowActionsNavigateWithIntents() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.REAGENTS)
        selectAndAct(ids.es001, "Kirim")
        assertEquals(Page.RECEIPTS, state.page)
        assertEquals(PageIntent.ReceiptFor(ids.es001), state.intent)
        state.navigate(Page.REAGENTS)
        waitForIdle()
        selectAndAct(ids.es003, "Berish")
        assertEquals(Page.ISSUES, state.page)
        assertEquals(PageIntent.IssueFrom(ids.es003), state.intent)
    }

    @Test
    fun historyDialogShowsRunningBalance() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        selectAndAct(ids.es001, "Tarix")
        onNodeWithTag("history-dialog").assertExists()
        val ledger = services.queries.ledger(ids.es001)
        assertEquals(5, ledger.size)
        ledger.forEach { onNodeWithTag("ledger-row-${it.movementId}").assertExists() }
        onNode(hasText("6") and hasAnyAncestor(hasTestTag("ledger-row-${ledger.last().movementId}"))).assertExists()
        onNodeWithText("Operatsiyalar tarixida ochish").assertExists()
    }

    // ---------------------------------------------------------------- reaktiv qo‘shish

    @Test
    fun addReagentValidatesThenSaves() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.ADD_REAGENT)
        val before = services.queries.batchRows(BatchFilter(includeArchived = true), today).size
        onNodeWithTag("add-save").performClick()
        onNodeWithText("Reaktiv nomi kiritilishi shart.").assertExists()
        onNodeWithText("Seriya raqami kiritilishi shart.").assertExists()
        onNodeWithText("Yaroqlilik muddati kiritilishi shart.").assertExists()
        onNodeWithTag("add-error-summary").assertExists()
        assertEquals(before, services.queries.batchRows(BatchFilter(includeArchived = true), today).size)

        onNodeWithTag("add-name").performTextInput("Metanol")
        onNodeWithTag("add-series").performTextInput("MT-5")
        onNodeWithTag("add-expires").performTextInput("01.06.2028")
        onNodeWithTag("add-qty").performTextInput("2,5")
        onNodeWithTag("add-qty-unit").performClick()
        onNodeWithTag("add-qty-unit-0").performClick()
        onNodeWithTag("add-save").performClick()
        waitForIdle()
        onNodeWithTag("add-success").assertExists()
        val saved = services.queries.batchRows(BatchFilter(text = "Metanol"), today).single()
        assertEquals("MT-5", saved.series)
        assertEquals(2_500_000L, saved.remainder)
        assertEquals(MeasureUnit.L, saved.unit)
        assertEquals(today, saved.receivedOn)
        onNodeWithTag("add-error-summary").assertDoesNotExist()

        onNodeWithText("Ro‘yxatda ko‘rish").performClick()
        waitForIdle()
        assertEquals(Page.REAGENTS, state.page)
        assertEquals(PageIntent.ReagentsFiltered(reagentId = saved.reagentId), state.intent)
        onNodeWithTag(row(saved.batchId)).assertExists()
    }

    @Test
    fun exactDuplicateOffersAnExtraReceipt() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Etil spirti 96%")
        onNodeWithTag("add-series").performTextInput("es-001")
        onNodeWithTag("add-expires").performTextInput("01.09.2027")
        onNodeWithTag("add-qty").performTextInput("2")
        onNodeWithTag("add-qty-unit").performClick()
        onNodeWithTag("add-qty-unit-0").performClick()
        onNodeWithTag("add-save").performClick()
        onNodeWithText("Bu partiya allaqachon bor").assertExists()
        dialogButton("Mavjud partiyaga qo‘shimcha kirim").performClick()
        waitForIdle()
        assertEquals(Page.RECEIPTS, state.page)
        assertEquals(PageIntent.ReceiptFor(ids.es001), state.intent)
    }

    @Test
    fun sameSeriesOtherExpiryNeedsConfirmation() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Etil spirti 96%")
        onNodeWithTag("add-series").performTextInput("ES-001")
        onNodeWithTag("add-expires").performTextInput("01.10.2027")
        onNodeWithTag("add-qty").performTextInput("1")
        onNodeWithTag("add-qty-unit").performClick()
        onNodeWithTag("add-qty-unit-0").performClick()
        onNodeWithTag("add-save").performClick()
        onNodeWithText("Shu seriyali partiya bor").assertExists()
        dialogButton("Alohida partiya sifatida saqlash").performClick()
        waitForIdle()
        onNodeWithTag("add-success").assertExists()
        assertEquals(4, services.queries.batchRows(BatchFilter(reagentId = ids.etilReagent), today).size)
    }

    @Test
    fun newDepartmentCanBeAddedFromTheForm() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-department").performClick()
        // 0 — «Tanlanmagan», 1..4 — bo‘limlar, 5 — «+ Yangi bo‘lim qo‘shish…».
        onNodeWithTag("add-department-option-5").performClick()
        onNodeWithTag("name-dialog-field").performTextInput("Toksikologiya")
        dialogButton("Qo‘shish").performClick()
        waitForIdle()
        val added = services.directory.departments().single { it.name == "Toksikologiya" }
        onNodeWithTag("add-department").assert(hasText("Toksikologiya"))
        onNodeWithTag("add-name").performTextInput("Metanol")
        onNodeWithTag("add-series").performTextInput("MT-1")
        onNodeWithTag("add-expires").performTextInput("01.06.2028")
        onNodeWithTag("add-qty").performTextInput("1")
        onNodeWithTag("add-qty-unit").performClick()
        onNodeWithTag("add-qty-unit-0").performClick()
        onNodeWithTag("add-save").performClick()
        waitForIdle()
        assertEquals(added.id, services.queries.batchRows(BatchFilter(text = "Metanol"), today).single().departmentId)
    }

    // ---------------------------------------------------------------- yozish himoyasi

    @Test
    fun secondWriteInTheSameFrameIsIgnored() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.DASHBOARD)
        var calls = 0
        runOnIdle {
            assertTrue(state.run({ calls++ }))
            // Navbatda turgan ikkinchi bosish: band holati keyingi kadrgacha saqlanadi.
            assertFalse(state.run({ calls++ }))
            assertTrue(state.busy)
        }
        waitForIdle()
        assertFalse(state.busy)
        runOnIdle { assertTrue(state.run({ calls++ })) }
        assertEquals(2, calls)
    }
}

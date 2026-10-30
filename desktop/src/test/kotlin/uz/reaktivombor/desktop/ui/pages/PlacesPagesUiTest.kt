package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.nio.file.Files
import java.time.LocalDate

/**
 * Haqiqiy baza (DemoData) bilan: «Bo‘limlar bo‘yicha hisob», «Tumanlarga berilgan reaktivlar», «Yaroqlilik muddati
 * nazorati» va «Umumiy zaxiralar» — har bir amal oxirigacha, natija bazadan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class PlacesPagesUiTest {
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

    private fun SkikoComposeUiTest.app(page: Page, intent: PageIntent? = null, dialogs: FakeFileDialogs = FakeFileDialogs()): AppState {
        val state = AppState(services, dialogs, page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    /** [tag] ichida [text] matnli tugun bor. */
    private fun SkikoComposeUiTest.assertIn(tag: String, text: String, substring: Boolean = false) {
        onNode(hasText(text, substring = substring) and hasAnyAncestor(hasTestTag(tag))).assertExists()
    }

    /** [tag] ichida [text] matnli kamida bitta tugun bor (bir xil qiymat bir nechta ustunda bo‘lishi mumkin). */
    private fun SkikoComposeUiTest.assertAnyIn(tag: String, text: String) {
        val found = onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(tag))).fetchSemanticsNodes()
        assertTrue("«$text» $tag ichida topilmadi", found.isNotEmpty())
    }

    /** Jadval qatorlari tepadan pastga tartibda ([prefix] bilan boshlanadigan test belgilari). */
    private fun SkikoComposeUiTest.rowTags(prefix: String): List<String> = onAllNodes(
        SemanticsMatcher("qatorlar") { node -> node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true },
    ).fetchSemanticsNodes().sortedBy { it.positionInRoot.y }.map { it.config[SemanticsProperties.TestTag] }

    private fun department(name: String): Place = services.directory.departments(includeInactive = true).single { it.name == name }

    private fun district(name: String): Place = services.directory.districts(includeInactive = true).single { it.name == name }

    private fun place(kind: PlaceKind, id: Long): Place = services.directory.get(kind, id)!!

    // ---------------------------------------------------------------- bo‘limlar

    @Test
    fun departmentCanBeAddedRenamedDeactivatedAndActivated() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DEPARTMENTS)
        onNodeWithTag("departments-add").performClick()
        onNodeWithTag("name-dialog-field").performTextInput("gistologiya")
        dialogButton("Qo‘shish").performClick()
        onNodeWithText("«Gistologiya» nomli bo‘lim allaqachon bor.").assertExists()
        assertEquals(4, services.directory.departments(includeInactive = true).size)

        onNodeWithTag("name-dialog-field").performTextReplacement("Toksikologiya")
        dialogButton("Qo‘shish").performClick()
        waitForIdle()
        val added = department("Toksikologiya")
        assertTrue(added.active)
        // Yangi bo‘lim tanlangan: sarlavhada uning nomi, partiyasi yo‘q.
        assertIn("department-header", "Toksikologiya")
        onNodeWithTag("department-batches-empty").assertExists()

        onNodeWithTag("departments-rename").performClick()
        onNodeWithTag("name-dialog-field").performTextReplacement("Biologiya")
        dialogButton("Saqlash").performClick()
        onNodeWithText("«Biologiya» nomli bo‘lim allaqachon bor.").assertExists()
        assertEquals("Toksikologiya", place(PlaceKind.DEPARTMENT, added.id).name)
        onNodeWithTag("name-dialog-field").performTextReplacement("Toksikologiya laboratoriyasi")
        dialogButton("Saqlash").performClick()
        waitForIdle()
        assertEquals("Toksikologiya laboratoriyasi", place(PlaceKind.DEPARTMENT, added.id).name)
        assertIn("department-header", "Toksikologiya laboratoriyasi")

        onNodeWithTag("departments-toggle").performClick()
        onNodeWithText("Bo‘limni nofaol qilish").assertExists()
        dialogButton("Nofaol qilish").performClick()
        waitForIdle()
        assertFalse(place(PlaceKind.DEPARTMENT, added.id).active)
        onNodeWithTag("departments-item-${added.id}").assert(hasText("nofaol"))
        // Nofaol bo‘lim yangi partiyalar uchun tanlanmaydi (Reaktiv qo‘shish shaklida yo‘q).
        assertFalse(services.directory.departments().any { it.id == added.id })

        onNodeWithTag("departments-toggle").performClick()
        dialogButton("Faollashtirish").performClick()
        waitForIdle()
        assertTrue(place(PlaceKind.DEPARTMENT, added.id).active)
    }

    @Test
    fun departmentSummaryMatchesTheDemoData() = runDesktopComposeUiTest(1366, 768) {
        val gistologiya = department("Gistologiya")
        app(Page.DEPARTMENTS, PageIntent.DepartmentOf(gistologiya.id))
        val summary = services.queries.departmentSummary(gistologiya.id, today = today)
        assertEquals(setOf(ids.es001, ids.es003, ids.eo77), summary.ownedBatches.map { it.batchId }.toSet())

        // ES-001 10 l + ES-003 3 l + EO-77 250 ml; berilgan 4 l (3 + 1 + 0,5 − 0,5); qoldiq 9,25 l; bo‘limga sof 2,5 l.
        assertIn("department-card-batches", "3 ta")
        assertIn("department-card-received", "13,25 l")
        assertIn("department-card-issued", "4 l")
        assertIn("department-card-remainder", "9,25 l")
        assertIn("department-card-issued-to", "2,5 l")
        assertIn("department-card-issued-to", "sof · 1 ta berish")
        assertEquals("9,25 l", Qty.formatAuto(summary.ownedBatches.sumOf { it.remainder }, Dimension.VOLUME))
        listOf(ids.es001, ids.es003, ids.eo77).forEach { onNodeWithTag("department-batches-row-$it").assertExists() }
        onNodeWithTag("department-batches-row-${ids.es002}").assertDoesNotExist()
        assertIn("department-batches-row-${ids.es003}", "1–3 oy qoldi")

        // Bo‘limga berilgan: reaktiv bo‘yicha jami va sanali qator.
        onNodeWithTag("department-tab-1").performClick()
        onNodeWithTag("department-issued-group-${ids.etilReagent}-VOLUME").assert(hasText("Sof berilgan: 2,5 l"))
        onNodeWithTag("department-issued-group-${ids.etilReagent}-VOLUME").assert(hasText("Qaytarilgan: 0,5 l"))
        assertIn("department-issued-row-${ids.gistologiyaIssueItem}", "10.03.2026")

        // Sarf tarixi: berish va qaytarish (yangisi birinchi).
        onNodeWithTag("department-tab-2").performClick()
        val returnId = services.queries.returns().single().id
        assertEquals(listOf("department-history-row-r$returnId", "department-history-row-i${ids.gistologiyaIssueItem}"), rowTags("department-history-row-"))
        assertIn("department-history-row-r$returnId", "Qaytarildi")

        // Davr: 01.04.2026 dan — 10.03 dagi berish kirmaydi (bo‘limdagi partiyalar — bugungi holat — o‘zgarmaydi), lekin davrdagi
        // qaytarish (0,5 l) «Davr uchun harakat» hisobotidagi kabi sof berilganni kamaytiradi.
        onNodeWithTag("department-period-from").performTextInput("01.04.2026")
        waitForIdle()
        assertIn("department-card-issued-to", "sof · 0 ta berish")
        assertIn("department-card-remainder", "9,25 l")
        val inPeriod = services.queries.departmentSummary(gistologiya.id, LocalDate.of(2026, 4, 1), null, today)
        assertEquals(listOf(0), inPeriod.issuedTo.map { it.items })
        assertEquals(listOf(-500_000L), inPeriod.issuedTo.map { it.net })
        assertEquals(1, inPeriod.earlierReturns.size)
        assertEquals(listOf("department-history-row-r$returnId"), rowTags("department-history-row-"))

        // Boshqa bo‘lim: hajm va massa alohida qatorlarda, qo‘shilmaydi.
        onNodeWithTag("departments-item-${department("Sud-kimyo").id}").performClick()
        waitForIdle()
        assertIn("department-card-remainder", "1,32 l")
        assertIn("department-card-remainder", "12 g")
        assertIn("department-card-batches", "1 tasi muddati o‘tgan")
    }

    @Test
    fun departmentReagentFilterNarrowsEverything() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DEPARTMENTS, PageIntent.DepartmentOf(department("Gistologiya").id))
        onNodeWithTag("department-reagent").performTextInput("eozin")
        waitForIdle()
        onNodeWithTag("department-batches-row-${ids.eo77}").assertExists()
        onNodeWithTag("department-batches-row-${ids.es001}").assertDoesNotExist()
        assertIn("department-card-remainder", "250 ml")
        assertIn("department-card-issued-to", "—")
    }

    @Test
    fun departmentReportIsSavedAsExcelAndPdf() = runDesktopComposeUiTest(1366, 768) {
        val xlsx = tmp.root.toPath().resolve("hisobot/bolim.xlsx")
        Files.createDirectories(xlsx.parent)
        val dialogs = FakeFileDialogs(saveAnswer = xlsx)
        app(Page.DEPARTMENTS, PageIntent.DepartmentOf(department("Sud-kimyo").id), dialogs)
        // Muvaffaqiyat xabari 6 soniyada yopiladi — soat to‘xtatiladi, «Papkani ochish» bosiladi.
        mainClock.autoAdvance = false
        onNodeWithTag("department-report-excel").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(Files.size(xlsx) > 0)
        assertTrue(dialogs.calls.toString(), dialogs.calls.first().startsWith("save:Bo‘limlar bo‘yicha reaktivlar qoldig‘i"))
        val texts = ReadableWorkbook(xlsx.toFile()).use { wb ->
            wb.firstSheet.read().flatMap { row -> (0 until row.cellCount).mapNotNull { row.getCellText(it) } }
        }
        assertTrue("Bo‘lim: Sud-kimyo" in texts)
        assertTrue(texts.contains("Kumush nitrat") && texts.contains("Xloroform"))
        assertFalse("bo‘lim filtri hisobotga o‘tgan", texts.contains("Gistologiya"))
        onNodeWithText("Papkani ochish").performClick()
        assertTrue(dialogs.calls.toString(), dialogs.calls.contains("folder:${xlsx.toAbsolutePath().parent}"))
        mainClock.autoAdvance = true
        waitForIdle()

        val pdf = tmp.root.toPath().resolve("hisobot/bolim.pdf")
        dialogs.saveAnswer = pdf
        onNodeWithTag("department-report-pdf").performClick()
        waitForIdle()
        assertEquals("%PDF", String(Files.readAllBytes(pdf).copyOfRange(0, 4)))

        // Bekor qilingan saqlash oynasi — hech narsa yozilmaydi, xato yo‘q.
        dialogs.saveAnswer = null
        val before = Files.list(xlsx.parent).use { it.count() }
        onNodeWithTag("department-report-excel").performClick()
        waitForIdle()
        assertEquals(before, Files.list(xlsx.parent).use { it.count() })
    }

    // ---------------------------------------------------------------- tumanlar

    @Test
    fun districtShowsEveryIssueSeparatelyAndPerReagentTotals() = runDesktopComposeUiTest(1366, 768) {
        val chilonzor = district("Chilonzor")
        // Shu tumanga xuddi shu reaktiv yana boshqa kuni berildi.
        services.stock.issue(
            IssueInput(
                date = LocalDate.of(2026, 10, 25), docNo = "T-60",
                lines = listOf(IssueLine(ids.es001, "0,3", MeasureUnit.L, RecipientType.DISTRICT, districtId = chilonzor.id, receiver = "Aliyev S.")),
            ),
        )
        app(Page.DISTRICTS, PageIntent.DistrictOf(chilonzor.id))
        val items = services.queries.issueItems(OpFilter(districtId = chilonzor.id, recipientType = RecipientType.DISTRICT))
        assertEquals(3, items.size)
        val etilItems = items.filter { it.reagentId == ids.etilReagent }.sortedBy { it.issuedOn }
        assertEquals(listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 10, 25)), etilItems.map { it.issuedOn })
        // Har bir berish — alohida qator, o‘z sanasi bilan; yangisi tepada.
        assertEquals(items.sortedByDescending { it.issuedOn }.map { "district-items-row-${it.id}" }, rowTags("district-items-row-"))
        assertIn("district-items-row-${etilItems[0].id}", "15.06.2026")
        assertIn("district-items-row-${etilItems[1].id}", "25.10.2026")
        assertIn("district-items-row-${etilItems[1].id}", "0,3")
        assertIn("district-card-count", "3 ta")
        assertIn("district-card-net", "2,8 l")

        // Reaktiv bo‘yicha jami: 0,5 + 0,3 = 0,8 l, 2 marta, birinchi va oxirgi sana.
        onNodeWithTag("district-tab-1").performClick()
        val row = "district-reagents-row-${chilonzor.id}-${ids.etilReagent}-VOLUME"
        assertAnyIn(row, "0,8")
        assertIn(row, "2")
        assertIn(row, "15.06.2026")
        assertIn(row, "25.10.2026")

        // Reaktiv filtri: faqat formalin.
        onNodeWithTag("district-tab-0").performClick()
        onNodeWithTag("district-reagent").performTextInput("formalin")
        waitForIdle()
        assertEquals(1, rowTags("district-items-row-").size)
        assertIn("district-card-net", "2 l")

        // Barcha tumanlar: har bir tuman kartasi.
        onNodeWithTag("district-reagent").performTextClearance()
        onNodeWithTag("districts-all").performClick()
        waitForIdle()
        // Bosiladigan karta — matnlari kartaning o‘zida birlashgan.
        onNodeWithTag("district-card-${chilonzor.id}").assert(hasText("2,8 l"))
        onNodeWithTag("district-card-${district("Yunusobod").id}").assert(hasText("8 g"))
        assertEquals(4, rowTags("district-items-row-").size)
        onNodeWithTag("district-card-${district("Yunusobod").id}").performClick()
        waitForIdle()
        assertIn("district-header", "Yunusobod")
        assertEquals(1, rowTags("district-items-row-").size)
    }

    @Test
    fun districtCanBeAddedRenamedAndDeactivated() = runDesktopComposeUiTest(1366, 768) {
        app(Page.DISTRICTS)
        onNodeWithTag("districts-add").performClick()
        onNodeWithTag("name-dialog-field").performTextInput("Mirzo Ulug‘bek")
        dialogButton("Qo‘shish").performClick()
        waitForIdle()
        val added = district("Mirzo Ulug‘bek")
        assertIn("district-header", "Mirzo Ulug‘bek")
        onNodeWithTag("district-items-empty").assertExists()

        onNodeWithTag("districts-rename").performClick()
        onNodeWithTag("name-dialog-field").performTextReplacement("chilonzor")
        dialogButton("Saqlash").performClick()
        onNodeWithText("«Chilonzor» nomli tuman allaqachon bor.").assertExists()
        onNodeWithTag("name-dialog-field").performTextReplacement("Mirobod")
        dialogButton("Saqlash").performClick()
        waitForIdle()
        assertEquals("Mirobod", place(PlaceKind.DISTRICT, added.id).name)

        onNodeWithTag("districts-toggle").performClick()
        dialogButton("Nofaol qilish").performClick()
        waitForIdle()
        assertFalse(place(PlaceKind.DISTRICT, added.id).active)
        onNodeWithTag("districts-item-${added.id}").assert(hasText("nofaol"))
    }

    @Test
    fun districtReportUsesTheCurrentFilters() = runDesktopComposeUiTest(1366, 768) {
        val xlsx = tmp.root.toPath().resolve("hisobot/tuman.xlsx")
        Files.createDirectories(xlsx.parent)
        val dialogs = FakeFileDialogs(saveAnswer = xlsx)
        app(Page.DISTRICTS, PageIntent.DistrictOf(district("Chilonzor").id), dialogs)
        onNodeWithTag("district-reagent").performTextInput("formalin")
        waitForIdle()
        onNodeWithTag("district-report-excel").performClick()
        waitForIdle()
        assertTrue(dialogs.calls.toString(), dialogs.calls.first().startsWith("save:Tumanlarga berilgan reaktivlar hisoboti"))
        val texts = ReadableWorkbook(xlsx.toFile()).use { wb ->
            wb.firstSheet.read().flatMap { row -> (0 until row.cellCount).mapNotNull { row.getCellText(it) } }
        }
        assertTrue(texts.toString(), "Tuman: Chilonzor" in texts)
        assertTrue(texts.toString(), "Reaktiv: formalin" in texts)
        assertTrue(texts.contains("Formalin 10%"))
        assertFalse("reaktiv filtri hisobotga o‘tmagan", texts.contains("Etil spirti 96%"))
        assertFalse("tuman filtri hisobotga o‘tmagan", texts.contains("Kumush nitrat"))

        val pdf = tmp.root.toPath().resolve("hisobot/tuman.pdf")
        dialogs.saveAnswer = pdf
        onNodeWithTag("district-report-pdf").performClick()
        waitForIdle()
        assertEquals("%PDF", String(Files.readAllBytes(pdf).copyOfRange(0, 4)))
    }

    @Test
    fun districtReturnValidatesAndSaves() = runDesktopComposeUiTest(1366, 768) {
        val chilonzor = district("Chilonzor")
        app(Page.DISTRICTS, PageIntent.DistrictOf(chilonzor.id))
        val item = services.queries.issueItems(OpFilter(batchId = ids.fr201, recipientType = RecipientType.DISTRICT)).single()
        onNodeWithTag("district-items-row-${item.id}").performClick()
        onNode(hasText("Qaytarish") and hasAnyAncestor(hasTestTag("district-actions"))).performClick()
        onNodeWithTag("return-dialog").assertExists()
        onNodeWithTag("return-qty").performTextReplacement("5")
        onNodeWithTag("return-save").performClick()
        onNodeWithText("Qaytariladigan miqdor berilganidan ko‘p", substring = true).assertExists()
        assertTrue(services.queries.returns(OpFilter(batchId = ids.fr201)).isEmpty())

        onNodeWithTag("return-qty").performTextReplacement("0,5")
        onNodeWithTag("return-reason").performTextInput("Ishlatilmadi")
        onNodeWithTag("return-save").performClick()
        waitForIdle()
        onNodeWithTag("return-dialog").assertDoesNotExist()
        val saved = services.queries.returns(OpFilter(batchId = ids.fr201)).single()
        assertEquals(500_000L, saved.qty)
        assertEquals("Ishlatilmadi", saved.reason)
        assertEquals(18_500_000L, services.stock.remainder(ids.fr201))
        assertIn("district-items-row-${item.id}", "0,5 l")
        assertIn("district-card-net", "2 l")
    }

    // ---------------------------------------------------------------- yaroqlilik muddati

    @Test
    fun expiryChipsShowCountsAndDefaultFilter() = runDesktopComposeUiTest(1366, 768) {
        app(Page.EXPIRY)
        mapOf("EXPIRED" to "1", "RED" to "1", "YELLOW" to "3", "GREEN" to "5", "UNKNOWN" to "0", "ALL" to "10").forEach { (state, count) ->
            onNodeWithTag("state-chip-$state").assert(hasText(count))
        }
        onNodeWithTag("expiry-banner").assertExists()
        // Standart: muddati o‘tgan + qizil + sariq, qolgan kunlar bo‘yicha o‘sish tartibida.
        assertEquals(
            listOf(ids.xl117, ids.kn09, ids.es003, ids.fr201, ids.eo77).map { "expiry-row-$it" },
            rowTags("expiry-row-"),
        )
        onNodeWithTag("expiry-row-${ids.es001}").assertDoesNotExist()
        assertIn("expiry-row-${ids.xl117}", "28 kun o‘tgan")

        onNodeWithTag("state-chip-ALL").performClick()
        assertEquals(10, rowTags("expiry-row-").size)
        onNodeWithTag("state-chip-GREEN").performClick()
        assertEquals(
            listOf(ids.es002, ids.es001, ids.xl131, ids.nx12, ids.pl3).map { "expiry-row-$it" },
            rowTags("expiry-row-"),
        )
        onNodeWithTag("state-chip-EXPIRED").performClick()
        assertEquals(6, rowTags("expiry-row-").size)
    }

    @Test
    fun writeOffNeedsAReasonAndReducesTheRemainder() = runDesktopComposeUiTest(1366, 768) {
        app(Page.EXPIRY)
        onNodeWithTag("expiry-row-${ids.xl117}").performClick()
        onNode(hasText("Hisobdan chiqarish") and hasAnyAncestor(hasTestTag("expiry-actions"))).performClick()
        onNodeWithTag("writeoff-dialog").assertExists()
        // Standart miqdor — butun qoldiq (320 ml).
        onNode(hasText("320") and hasTestTag("writeoff-qty")).assertExists()
        onNodeWithTag("writeoff-qty").performTextReplacement("100")
        onNodeWithTag("writeoff-save").performClick()
        onNodeWithText("Hisobdan chiqarish sababini yozing.").assertExists()
        assertEquals(320_000L, services.stock.remainder(ids.xl117))

        onNodeWithTag("writeoff-reason").performTextInput("Yaroqlilik muddati o‘tgan")
        onNodeWithTag("writeoff-save").performClick()
        waitForIdle()
        onNodeWithTag("writeoff-dialog").assertDoesNotExist()
        assertEquals(220_000L, services.stock.remainder(ids.xl117))
        val writeOff = services.queries.issueItems(OpFilter(batchId = ids.xl117, recipientType = RecipientType.WRITE_OFF)).single()
        assertEquals(100_000L, writeOff.qty)
        assertEquals("Yaroqlilik muddati o‘tgan", writeOff.note)
        assertIn("expiry-row-${ids.xl117}", "220")

        // Qolganini to‘liq hisobdan chiqarish: partiya ro‘yxatdan (faqat qoldig‘i borlar) va ogohlantirish yo‘qoladi.
        onNode(hasText("Hisobdan chiqarish") and hasAnyAncestor(hasTestTag("expiry-actions"))).performClick()
        onNodeWithTag("writeoff-reason").performTextInput("Yaroqlilik muddati o‘tgan")
        onNodeWithTag("writeoff-save").performClick()
        waitForIdle()
        assertEquals(0L, services.stock.remainder(ids.xl117))
        onNodeWithTag("expiry-row-${ids.xl117}").assertDoesNotExist()
        onNodeWithTag("expiry-banner").assertDoesNotExist()
        onNodeWithTag("expiry-empty").performClick()
        onNodeWithTag("expiry-row-${ids.xl117}").assertExists()
        val row = services.queries.batchRows(BatchFilter(reagentId = ids.xloroformReagent), today).single { it.batchId == ids.xl117 }
        assertEquals(320_000L, row.writtenOffQty)
    }

    @Test
    fun notificationIntentPreselectsTheStateAndHighlightsTheBatch() = runDesktopComposeUiTest(1366, 768) {
        app(Page.EXPIRY, PageIntent.FilterExpiry(setOf(ExpiryState.RED), ids.kn09))
        assertEquals(listOf("expiry-row-${ids.kn09}"), rowTags("expiry-row-"))
        assertIn("expiry-actions", "Kumush nitrat · KN-09 · 20.11.2026", substring = true)
        onNode(hasText("Hisobdan chiqarish") and hasAnyAncestor(hasTestTag("expiry-actions"))).assertExists()
    }

    @Test
    fun archiveFromTheExpiryPageNeedsConfirmation() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.EXPIRY)
        // Qoldig‘i bor partiya arxivlanmaydi: tasdiq o‘rniga nima qilish kerakligi aytiladi, hech narsa o‘zgarmaydi.
        onNodeWithTag("expiry-row-${ids.eo77}").performClick()
        onNode(hasText("Arxivlash") and hasAnyAncestor(hasTestTag("expiry-actions"))).performClick()
        onNodeWithText("Arxivlab bo‘lmaydi").assertExists()
        onNodeWithText("Partiyani arxivlash").assertDoesNotExist()
        assertFalse(services.reagents.batch(ids.eo77)!!.archived)
        // Qoldiq hisobdan chiqarilgach (nol qoldiq) arxivlash tasdiq bilan bajariladi.
        services.stock.writeOff(ids.eo77, today, "250", MeasureUnit.ML, "Idish singan")
        state.refresh()
        waitForIdle()
        onNodeWithTag("expiry-empty").performClick()
        onNodeWithTag("expiry-row-${ids.eo77}").performClick()
        onNode(hasText("Arxivlash") and hasAnyAncestor(hasTestTag("expiry-actions"))).performClick()
        onNodeWithText("Partiyani arxivlash").assertExists()
        dialogButton("Bekor qilish").performClick()
        assertFalse(services.reagents.batch(ids.eo77)!!.archived)
        onNode(hasText("Arxivlash") and hasAnyAncestor(hasTestTag("expiry-actions"))).performClick()
        dialogButton("Arxivlash").performClick()
        waitForIdle()
        assertTrue(services.reagents.batch(ids.eo77)!!.archived)
        onNodeWithTag("expiry-row-${ids.eo77}").assertDoesNotExist()
        onNodeWithTag("expiry-archived").performClick()
        assertIn("expiry-row-${ids.eo77}", "Arxiv")
    }

    // ---------------------------------------------------------------- umumiy zaxiralar

    private fun stockRow(reagentId: Long, dimension: Dimension = Dimension.VOLUME) = "stock-row-$reagentId-${dimension.name}"

    @Test
    fun stockTotalsAndBelowMinFilter() = runDesktopComposeUiTest(1366, 768) {
        app(Page.STOCK)
        val totals = services.queries.stockTotals(BatchFilter(), today)
        assertEquals(totals.map { stockRow(it.reagentId, it.dimension) }, rowTags("stock-row-"))
        val etil = totals.single { it.reagentId == ids.etilReagent }
        assertEquals(14_000_000L, etil.remainder)
        assertEquals(MeasureUnit.L, etil.unit)
        val etilRow = stockRow(ids.etilReagent)
        listOf("14", "l", "3 / 3", "18", "4", "5 l", "Yetarli", "15.12.2026", "1–3 oy qoldi").forEach { assertIn(etilRow, it) }
        val kumush = services.reagents.batch(ids.kn09)!!.reagentId
        val kumushRow = stockRow(kumush, Dimension.MASS)
        listOf("12", "g", "20 g", "Minimaldan kam", "1 oy yoki kam qoldi").forEach { assertIn(kumushRow, it) }
        // Xloroform: eng yaqin muddat — muddati o‘tgan partiya (yashil partiya bilan birga ham yashirinmaydi).
        assertIn(stockRow(ids.xloroformReagent), "Muddati o‘tgan")

        onNodeWithTag("stock-below-min").performClick()
        assertEquals(listOf(kumushRow), rowTags("stock-row-"))
        onNodeWithTag("stock-text").performTextInput("etil")
        onNodeWithTag("stock-empty").assertExists()
        onNodeWithText("Filtrlarni tozalash (2)").performClick()
        assertEquals(totals.size, rowTags("stock-row-").size)
    }

    @Test
    fun minimumStockDialogSavesOnlyTheMinimum() = runDesktopComposeUiTest(1366, 768) {
        app(Page.STOCK)
        val before = services.reagents.get(ids.etilReagent)!!
        onNodeWithTag(stockRow(ids.etilReagent)).performClick()
        onNode(hasText("Minimal qoldiqni belgilash") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        onNodeWithTag("stock-min-dialog").assertExists()
        onNode(hasText("5") and hasTestTag("stock-min")).assertExists()
        onNodeWithTag("stock-min").performTextReplacement("1,2345")
        onNodeWithTag("stock-min-save").performClick()
        onNodeWithTag("stock-min-dialog").assertExists()
        assertEquals(5_000_000L, services.reagents.get(ids.etilReagent)!!.minStock)

        onNodeWithTag("stock-min").performTextReplacement("20")
        onNodeWithTag("stock-min-save").performClick()
        waitForIdle()
        onNodeWithTag("stock-min-dialog").assertDoesNotExist()
        val after = services.reagents.get(ids.etilReagent)!!
        assertEquals(20_000_000L, after.minStock)
        assertEquals(MeasureUnit.L, after.minStockUnit)
        assertEquals(before.copy(minStock = 20_000_000L, updatedAt = after.updatedAt), after)
        assertIn(stockRow(ids.etilReagent), "Minimaldan kam")
        assertIn(stockRow(ids.etilReagent), "20 l")

        // Bo‘sh — minimal qoldiq olib tashlanadi.
        onNode(hasText("Minimal qoldiqni belgilash") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        onNodeWithTag("stock-min").performTextClearance()
        onNodeWithTag("stock-min-save").performClick()
        waitForIdle()
        assertNull(services.reagents.get(ids.etilReagent)!!.minStock)
        assertIn(stockRow(ids.etilReagent), "Belgilanmagan")
    }

    @Test
    fun batchesDialogShowsEveryBatchWithItsOwnState() = runDesktopComposeUiTest(1366, 768) {
        app(Page.STOCK)
        onNodeWithTag(stockRow(ids.etilReagent)).performClick()
        onNode(hasText("Partiyalar") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        onNodeWithTag("stock-batches-dialog").assertExists()
        listOf(ids.es001, ids.es002, ids.es003).forEach { onNodeWithTag("stock-batches-row-$it").assertExists() }
        assertIn("stock-batches-row-${ids.es003}", "1–3 oy qoldi")
        assertIn("stock-batches-row-${ids.es001}", "Yaroqli")
        assertIn("stock-batches-state-YELLOW", "1–3 oy qoldi: 1")
        assertIn("stock-batches-state-GREEN", "Yaroqli: 2")
        onNodeWithTag("stock-batches-state-EXPIRED").assertDoesNotExist()

        // Xloroform: yashil partiya bilan birga muddati o‘tgan partiya ham o‘z rangida ko‘rinadi.
        onNodeWithTag("stock-batches-dialog-close").performClick()
        onNodeWithTag("stock-row-${ids.xloroformReagent}-VOLUME").performClick()
        onNode(hasText("Partiyalar") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        assertIn("stock-batches-row-${ids.xl117}", "Muddati o‘tgan")
        assertIn("stock-batches-row-${ids.xl131}", "Yaroqli")
        assertIn("stock-batches-state-EXPIRED", "Muddati o‘tgan: 1")
    }

    @Test
    fun stockIntentFiltersToOneReagent() = runDesktopComposeUiTest(1366, 768) {
        val kumush = services.reagents.batch(ids.kn09)!!.reagentId
        val state = app(Page.STOCK, PageIntent.StockFiltered(reagentId = kumush, belowMinOnly = true))
        assertEquals(listOf(stockRow(kumush, Dimension.MASS)), rowTags("stock-row-"))
        assertIn("stock-actions", "Kumush nitrat", substring = true)
        onNodeWithTag("stock-reagent-chip").performClick()
        onNodeWithTag("stock-below-min").performClick()
        assertEquals(7, rowTags("stock-row-").size)
        onNode(hasText("Barcha reaktivlarda") and hasAnyAncestor(hasTestTag("stock-actions"))).performClick()
        waitForIdle()
        assertEquals(Page.REAGENTS, state.page)
        assertEquals(PageIntent.ReagentsFiltered(reagentId = kumush), state.intent)
    }
}

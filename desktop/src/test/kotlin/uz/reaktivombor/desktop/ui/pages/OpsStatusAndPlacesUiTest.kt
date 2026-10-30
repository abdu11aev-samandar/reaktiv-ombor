package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Yakuniy ko‘rik topilmalari bo‘yicha (DemoData bilan): nofaol tuman berish qatorida «yangi» deb va’da qilinmaydi,
 * berish tarixi filtrida nofaol bo‘lim bor, kirimlarda yaroqlilik muddati ustuni, bekor qilingan/tuzatilgan yozuvlar
 * ko‘rinadigan ustunlarda belgilanadi, «Sof berilgan» yorliqlari, miqdor kataklari bo‘linmaydi, ikkala qaytarish
 * oynasida takroriy qaytarish tasdiqlanadi.
 */
@OptIn(ExperimentalTestApi::class)
class OpsStatusAndPlacesUiTest {
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

    private fun SkikoComposeUiTest.app(page: Page, intent: PageIntent? = null): AppState {
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

    private fun SkikoComposeUiTest.type(tag: String, text: String) {
        val node = onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performTextInput(text)
        waitForIdle()
    }

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun SkikoComposeUiTest.assertIn(tag: String, text: String) {
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
    }

    private fun SkikoComposeUiTest.assertNotIn(tag: String, text: String) {
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertDoesNotExist()
    }

    /** [tag] ichidagi aynan [text] matnli Text ning joylashuvi (uslubi, qatorlari). */
    private fun SkikoComposeUiTest.layoutOf(tag: String, text: String): TextLayoutResult {
        val node = onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    /** Miqdor katagi: bitta qatorda (minglar bo‘shlig‘ida bo‘linmaydi), sig‘masa «…». */
    private fun assertSingleLine(layout: TextLayoutResult) {
        assertFalse("softWrap o‘chirilmagan: ${layout.layoutInput.text}", layout.layoutInput.softWrap)
        assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
        assertEquals(1, layout.lineCount)
    }

    // ---------------------------------------------------------------- #4/#35 nofaol tuman

    @Test
    fun anInactiveDistrictIsNotPromisedAsNewAndIsNotSaved() = runDesktopComposeUiTest(1366, 768) {
        val yunusobod = services.directory.districts().single { it.name == "Yunusobod" }
        services.directory.setActive(PlaceKind.DISTRICT, yunusobod.id, false)
        val before = services.stock.remainder(ids.nx12)
        app(Page.ISSUES)
        type("issue-line-1-reagent", "Natriy xlorid")
        type("issue-line-1-qty", "100")
        tap("issue-line-1-qty-unit")
        tap("issue-line-1-qty-unit-1") // g
        tap("issue-line-1-type-1") // Tuman
        type("issue-line-1-district", "yunusobod")
        onNodeWithText(INACTIVE_DISTRICT_ERROR).assertExists()
        onNodeWithText("Yangi tuman qo‘shiladi").assertDoesNotExist()
        onNodeWithText("+ «yunusobod» — yangi tuman").assertDoesNotExist()

        // Saqlash: qator xatosi, hech narsa yozilmaydi va tuman nofaolligicha qoladi.
        type("issue-line-1-org", "Yunusobod tuman bo‘limi")
        tap("issue-save")
        onNodeWithText(INACTIVE_DISTRICT_ERROR).assertExists()
        assertEquals(before, services.stock.remainder(ids.nx12))
        assertFalse(services.directory.get(PlaceKind.DISTRICT, yunusobod.id)!!.active)
        assertTrue(services.queries.issueItems(OpFilter(districtId = yunusobod.id)).none { it.issuedOn == today })

        // Haqiqatan yangi nom — avvalgidek «Yangi tuman qo‘shiladi».
        onNodeWithTag("issue-line-1-district").performTextReplacement("Olmazor")
        waitForIdle()
        onNodeWithText("Yangi tuman qo‘shiladi").assertExists()
        onNodeWithText(INACTIVE_DISTRICT_ERROR).assertDoesNotExist()
    }

    @Test
    fun inactiveDistrictCheckUsesTheServiceNameKey() {
        val inactive = listOf("Mirzo Ulug‘bek")
        assertTrue(issuesIsInactiveDistrict("  mirzo  ulug'bek ", inactive))
        assertFalse(issuesIsInactiveDistrict("Mirzo", inactive))
        assertFalse(issuesIsInactiveDistrict("  ", inactive))
    }

    // ---------------------------------------------------------------- #1/#11 berish tarixi filtri

    @Test
    fun issueHistoryDepartmentFilterListsInactiveDepartments() = runDesktopComposeUiTest(1366, 768) {
        val biologiya = services.directory.departments().single { it.name == "Biologiya" }
        services.directory.setActive(PlaceKind.DEPARTMENT, biologiya.id, false)
        app(Page.ISSUES)
        val all = services.directory.departments(includeInactive = true)
        val index = all.indexOfFirst { it.id == biologiya.id } + 1 // 0 — «Hammasi»
        tap("issues-filter-department")
        onNodeWithTag("issues-filter-department-option-$index").assert(hasText("Biologiya (nofaol)"))
        tap("issues-filter-department-option-$index")

        val expected = services.queries.issueItems(OpFilter(departmentId = biologiya.id))
        assertTrue(expected.isNotEmpty())
        onNodeWithTag("issue-items").performScrollTo()
        expected.forEach { onNodeWithTag("issue-items-row-${it.id}").assertExists() }
        onNodeWithTag("issue-items-row-${ids.gistologiyaIssueItem}").assertDoesNotExist()
        onNodeWithTag("issues-filter-department").assert(hasText("Biologiya (nofaol)"))
    }

    // ---------------------------------------------------------------- #7 va #24 kirimlar

    @Test
    fun receiptsShowTheExpiryAndMarkCancelledAndCorrectedRows() = runDesktopComposeUiTest(1366, 768) {
        val eozin = services.queries.receipts().single { it.batchId == ids.eo77 }
        services.stock.cancelReceipt(eozin.id, "Xato kiritilgan")
        val extra = services.stock.addReceipt(ids.es002, ReceiptInput(today, "2", MeasureUnit.L, docNo = "YX-7"))
        val replacement = services.stock.correctReceipt(extra, ReceiptInput(today, "3", MeasureUnit.L, docNo = "YX-7"), "3 l edi")
        app(Page.RECEIPTS)
        tap("receipts-filter-cancelled")
        onNodeWithTag("receipts").performScrollTo()

        // Yaroqlilik muddati ustuni (EO-77: 10.01.2027).
        onNodeWithTag("receipts-th-expires").assert(hasText("Yaroqlilik muddati"))
        assertIn("receipts-row-${eozin.id}", "10.01.2027")

        // Bekor qilingan va tuzatilgan: sana oldida belgi, miqdor ustidan chizilgan; faol tuzatish — oddiy.
        assertIn("receipts-row-${eozin.id}", "bekor")
        assertIn("receipts-row-$extra", "tuzatilgan")
        assertNotIn("receipts-row-$replacement", "tuzatilgan")
        assertNotIn("receipts-row-$replacement", "bekor")
        assertEquals(TextDecoration.LineThrough, layoutOf("receipts-row-${eozin.id}", "250").layoutInput.style.textDecoration)
        assertEquals(TextDecoration.LineThrough, layoutOf("receipts-row-$extra", "2").layoutInput.style.textDecoration)
        val active = layoutOf("receipts-row-$replacement", "3").layoutInput.style.textDecoration
        assertTrue("faol kirim chizilmasin: $active", active == null || active == TextDecoration.None)
    }

    // ---------------------------------------------------------------- #24 berish tarixi va qaytarishlar

    @Test
    fun cancelledIssueItemsAndReturnsAreMarkedInTheVisibleColumns() = runDesktopComposeUiTest(1366, 768) {
        val biologiya = services.queries.issueItems().single { it.batchId == ids.es001 && it.departmentName == "Biologiya" }
        services.stock.cancelIssueItem(biologiya.id, "Boshqa hujjatga yozilgan")
        val ret = services.queries.returns().single { it.issueItemId == ids.gistologiyaIssueItem }
        services.stock.cancelReturn(ret.id, "Xato kiritilgan")
        app(Page.ISSUES)
        tap("issues-filter-cancelled")
        onNodeWithTag("issue-items").performScrollTo()
        assertIn("issue-items-row-${biologiya.id}", "bekor")
        assertEquals(TextDecoration.LineThrough, layoutOf("issue-items-row-${biologiya.id}", "1").layoutInput.style.textDecoration)
        assertNotIn("issue-items-row-${ids.gistologiyaIssueItem}", "bekor")

        tap("tab-returns")
        assertIn("returns-row-${ret.id}", "bekor")
        assertEquals(TextDecoration.LineThrough, layoutOf("returns-row-${ret.id}", "0,5").layoutInput.style.textDecoration)
    }

    // ---------------------------------------------------------------- #25 «Sof berilgan»

    @Test
    fun netIssuedValuesAreLabelledSofBerilgan() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.STOCK)
        onNodeWithTag("stock-th-issued").assert(hasText("Sof berilgan"))

        val gistologiya = services.directory.departments().single { it.name == "Gistologiya" }
        state.navigate(Page.DEPARTMENTS, PageIntent.DepartmentOf(gistologiya.id))
        waitForIdle()
        // Bo‘lim partiyalari: 3 + 1 + 0,5 − 0,5 = 4 l — sof.
        assertIn("department-card-issued", "Sof berilgan")
        assertIn("department-card-issued", "4 l")
        onNodeWithTag("department-batches-th-issued").assert(hasText("Sof berilgan"))
        // Bo‘limga berilganlar: yalpi «Berilgan» alohida «Qaytarilgan» va «Sof berilgan» bilan.
        tap("department-tab-1")
        onNodeWithTag("department-issued-th-qty").assert(hasText("Berilgan"))
        onNodeWithTag("department-issued-th-returned").assert(hasText("Qaytarilgan"))
        onNodeWithTag("department-issued-th-net").assert(hasText("Sof berilgan"))
    }

    // ---------------------------------------------------------------- #29 miqdor kataklari

    @Test
    fun largeQuantitiesStayOnOneLine() = runDesktopComposeUiTest(1366, 768) {
        val gistologiya = services.directory.departments().single { it.name == "Gistologiya" }
        val big = services.reagents.createBatch(
            NewBatch(
                name = "Glitserin", series = "GL-1", receivedOn = today.minusDays(3), initialQtyText = "130000,75", unit = MeasureUnit.ML,
                expiresOn = today.plusYears(2), departmentId = gistologiya.id, storageLocation = "Ombor", receivedBy = "Karimova D.",
            ),
        )
        // Qoldiq kelgandan farq qilsin (kataklar matni bilan topiladi): 130 000,75 − 4 999,5 = 125 001,25 ml.
        services.stock.writeOff(big, today, "4999,5", MeasureUnit.ML, "Idish singan")
        val reagentId = services.reagents.batch(big)!!.reagentId
        val state = app(Page.DEPARTMENTS, PageIntent.DepartmentOf(gistologiya.id))
        assertSingleLine(layoutOf("department-batches-row-$big", "125 001,25"))

        state.navigate(Page.STOCK)
        waitForIdle()
        assertSingleLine(layoutOf("stock-row-$reagentId-VOLUME", "125 001,25"))

        val chilonzor = services.directory.districts().single { it.name == "Chilonzor" }
        val fr = services.queries.issueItems(OpFilter(batchId = ids.fr201, recipientType = RecipientType.DISTRICT)).single()
        state.navigate(Page.DISTRICTS, PageIntent.DistrictOf(chilonzor.id))
        waitForIdle()
        assertSingleLine(layoutOf("district-items-row-${fr.id}", "2"))
    }

    // ---------------------------------------------------------------- #31 takroriy qaytarish

    @Test
    fun aRepeatedDistrictReturnAsksForConfirmationAndThenSaves() = runDesktopComposeUiTest(1366, 768) {
        val chilonzor = services.directory.districts().single { it.name == "Chilonzor" }
        val item = services.queries.issueItems(OpFilter(batchId = ids.fr201, recipientType = RecipientType.DISTRICT)).single()
        val before = services.stock.remainder(ids.fr201)
        app(Page.DISTRICTS, PageIntent.DistrictOf(chilonzor.id))
        onNodeWithTag("district-items-row-${item.id}").performClick()
        repeat(2) {
            onNode(hasText("Qaytarish") and hasAnyAncestor(hasTestTag("district-actions"))).performClick()
            waitForIdle()
            onNodeWithTag("return-qty").performTextReplacement("0,5")
            onNodeWithTag("return-save").performClick()
            waitForIdle()
        }
        // Ikkinchisi — 2 daqiqa ichida xuddi shunday: tasdiq so‘raladi, hali saqlanmagan.
        onNodeWithText("Takroriy qaytarish").assertExists()
        assertEquals(1, services.queries.returns(OpFilter(batchId = ids.fr201)).size)
        dialogButton("Baribir saqlash").performClick()
        waitForIdle()
        onNodeWithTag("return-dialog").assertDoesNotExist()
        assertEquals(2, services.queries.returns(OpFilter(batchId = ids.fr201)).size)
        assertEquals(before + 1_000_000L, services.stock.remainder(ids.fr201))
    }

    @Test
    fun aRepeatedIssueHistoryReturnAsksForConfirmationAndThenSaves() = runDesktopComposeUiTest(1366, 768) {
        val item = services.queries.issueItems().single { it.batchId == ids.es001 && it.departmentName == "Biologiya" }
        val before = services.stock.remainder(ids.es001)
        val state = app(Page.ISSUES)
        onNodeWithTag("issue-items").performScrollTo()
        tap("issue-items-row-${item.id}")
        repeat(2) {
            onNode(hasText("Qaytarish") and hasAnyAncestor(hasTestTag("issue-item-actions"))).performClick()
            waitForIdle()
            onNodeWithTag("ret-qty").performTextInput("0,5")
            onNodeWithTag("ret-save").performClick()
            waitForIdle()
        }
        onNodeWithText("Takroriy qaytarish").assertExists()
        assertEquals(1, services.queries.returns().count { it.issueItemId == item.id })
        // «Orqaga» — saqlanmaydi, oyna ochiq qoladi; qayta saqlab tasdiqlansa — ikkinchisi yoziladi.
        dialogButton("Orqaga").performClick()
        waitForIdle()
        onNodeWithTag("return-dialog").assertExists()
        assertEquals(1, services.queries.returns().count { it.issueItemId == item.id })
        onNodeWithTag("ret-save").performClick()
        waitForIdle()
        dialogButton("Baribir saqlash").performClick()
        waitForIdle()
        onNodeWithTag("return-dialog").assertDoesNotExist()
        assertEquals(2, services.queries.returns().count { it.issueItemId == item.id })
        assertEquals(before + 1_000_000L, services.stock.remainder(ids.es001))
        assertNull(state.unexpectedError)
    }
}

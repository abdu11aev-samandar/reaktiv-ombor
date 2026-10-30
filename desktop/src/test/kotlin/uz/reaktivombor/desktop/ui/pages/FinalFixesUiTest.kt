package uz.reaktivombor.desktop.ui.pages

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import org.dhatim.fastexcel.reader.Row
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
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppShell
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.BELL_MAX_ITEMS
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import uz.reaktivombor.desktop.ui.ShellNotification
import uz.reaktivombor.desktop.ui.theme.AppTheme
import uz.reaktivombor.desktop.ui.theme.Severity
import java.nio.file.Path
import java.time.LocalDate

/**
 * Yakuniy tekshiruvdagi interfeys nuqsonlari uchun regressiya testlari: guruh sarlavhasi, nofaol bo‘limlar filtrda,
 * saqlanmagan shakl, eksport, davr maydoni, grafiklar, takliflar ro‘yxati, qo‘ng‘iroqcha, qidiruv kechikishi va h.k.
 */
@OptIn(ExperimentalTestApi::class)
class FinalFixesUiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids
    private lateinit var dialogs: FakeFileDialogs
    private lateinit var out: Path

    @Before
    fun setUp() {
        val (s, i) = DemoData.seeded(tmp.newFolder("data").toPath())
        services = s
        ids = i
        dialogs = FakeFileDialogs()
        out = tmp.newFolder("out").toPath()
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.app(page: Page, intent: PageIntent? = null): AppState {
        val state = AppState(services, dialogs, page)
        if (intent != null) state.navigate(page, intent)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun dept(name: String) = services.directory.departments(includeInactive = true).first { it.name == name }.id

    private fun addBatch(
        name: String,
        series: String,
        expires: String,
        qty: String,
        unit: MeasureUnit = MeasureUnit.L,
        department: Long = dept("Gistologiya"),
        minStock: String? = null,
        minUnit: MeasureUnit? = null,
    ): Long = services.reagents.createBatch(
        NewBatch(
            name = name, series = series, receivedOn = LocalDate.parse("2026-01-05"), initialQtyText = qty, unit = unit,
            expiresOn = LocalDate.parse(expires), departmentId = department, storageLocation = "Ombor", responsible = "Karimova D.",
            minStockText = minStock, minStockUnit = minUnit, receivedBy = "Karimova D.",
        ),
    )

    private fun Row.text(i: Int): String = if (i < cellCount) getCellText(i).orEmpty() else ""

    private fun rowsOf(file: Path): List<Row> = ReadableWorkbook(file.toFile(), ReadingOptions(true, false)).use { it.firstSheet.read() }

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    // ---------------------------------------------------------------- #0 guruh sarlavhasi

    private fun collapsedHeaderKeepsEveryChip(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val long = "Natriy gidroksid 0,1 N standart-titr eritmasi (fiksanal) ampulada, 10 ta komplekt"
        // Muddati o‘tgan, qizil, sariq, yashil partiyalar; jami minimal qoldiqdan kam.
        addBatch(long, "N-1", "2026-10-01", "1", minStock = "100", minUnit = MeasureUnit.L)
        addBatch(long, "N-2", "2026-11-15", "1")
        addBatch(long, "N-3", "2026-12-20", "1")
        addBatch(long, "N-4", "2028-01-01", "1")
        val reagentId = services.reagents.list().first { it.name == long }.id
        app(Page.REAGENTS)
        onNodeWithText("Hammasini yopish").performClick()
        waitForIdle()
        val group = hasAnyAncestor(hasTestTag("reagents-group-$reagentId"))
        listOf("Umumiy qoldiq:", "Muddati o‘tgan: 1", "1 oy yoki kam qoldi: 1", "1–3 oy qoldi: 1", "Yaroqli: 1", "Minimal qoldiqdan kam").forEach { text ->
            val node = onNode(hasText(text, substring = true) and group, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
            // Kesilgan belgi nuqtagacha qisqaradi; to‘liq belgi har harfga kamida ~4 px oladi (Windows shrifti Linux’dagidan ingichka).
            assertTrue("$text: eni ${node.size.width}", node.size.width >= text.length * 4)
            assertTrue("$text o‘ngdan chiqib ketgan", node.boundsInRoot.right <= width)
        }
    }

    @Test
    fun collapsedGroupHeaderKeepsEveryChipAt1366() = collapsedHeaderKeepsEveryChip(1366, 768)

    @Test
    fun collapsedGroupHeaderKeepsEveryChipAt1280() = collapsedHeaderKeepsEveryChip(1280, 720)

    // ---------------------------------------------------------------- #1 / #11 nofaol bo‘lim

    @Test
    fun inactiveDepartmentsAreListedInFiltersButNotInForms() = runDesktopComposeUiTest(1366, 768) {
        val biologiya = dept("Biologiya")
        services.directory.setActive(PlaceKind.DEPARTMENT, biologiya, false)

        // «Barcha reaktivlar»: filtrda nofaol bo‘lim ko‘rinadi va tanlansa uning partiyalari chiqadi.
        val state = app(Page.REAGENTS)
        onNodeWithTag("filter-department").performClick()
        onNodeWithText("Biologiya (nofaol)").assertExists()
        onNodeWithText("Biologiya (nofaol)").performClick()
        waitForIdle()
        onNodeWithTag("reagents-row-${ids.es002}").assertExists()
        onNodeWithTag("reagents-row-${ids.pl3}").assertExists()
        onNodeWithTag("reagents-row-${ids.es001}").assertDoesNotExist()

        // Muddat nazorati va Hisobotlar filtrlari ham.
        state.navigate(Page.EXPIRY)
        waitForIdle()
        onNodeWithTag("expiry-department").performClick()
        onNodeWithText("Biologiya (nofaol)").assertExists()
        state.navigate(Page.REPORTS)
        waitForIdle()
        onNodeWithTag("report-kind-RECEIPTS").performScrollTo().performClick()
        onNodeWithTag("report-department").performClick()
        onNodeWithText("Biologiya (nofaol)").assertExists()

        // Shaklda (partiya biriktirish) — faqat faol bo‘limlar.
        state.navigate(Page.ADD_REAGENT)
        waitForIdle()
        onNodeWithTag("add-department").performScrollTo().performClick()
        onNodeWithText("Gistologiya").assertExists()
        onNodeWithText("Biologiya (nofaol)").assertDoesNotExist()
    }

    // ---------------------------------------------------------------- #8 minimal qoldiq

    @Test
    fun changingTheBatchUnitDimensionDoesNotOverwriteTheMinimum() = runDesktopComposeUiTest(1366, 768) {
        val before = services.reagents.get(ids.etilReagent)!!
        assertEquals(MeasureUnit.L, before.minStockUnit)
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Etil")
        onNodeWithTag("add-name-item-0").performClick()
        onNodeWithTag("add-series").performTextInput("ES-900")
        onNodeWithTag("add-expires").performTextInput("01.06.2028")
        onNodeWithTag("add-qty").performTextInput("1")
        // 0 — l, 1 — ml, 2 — kg: boshqa o‘lchov turi.
        onNodeWithTag("add-qty-unit").performClick()
        onNodeWithTag("add-qty-unit-2").performClick()
        onNodeWithTag("add-save").performClick()
        waitForIdle()
        onNodeWithTag("add-success").assertExists()
        val after = services.reagents.get(ids.etilReagent)!!
        assertEquals(before.minStock, after.minStock)
        assertEquals(MeasureUnit.L, after.minStockUnit)
    }

    @Test
    fun aMinimumTheUserTypesIsStillSaved() = runDesktopComposeUiTest(1366, 768) {
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Etil")
        onNodeWithTag("add-name-item-0").performClick()
        onNodeWithTag("add-series").performTextInput("ES-901")
        onNodeWithTag("add-expires").performTextInput("01.06.2028")
        onNodeWithTag("add-qty").performTextInput("1")
        onNodeWithTag("add-qty-unit").performClick()
        onNodeWithTag("add-qty-unit-0").performClick()
        onNodeWithTag("add-min").performTextReplacement("7")
        onNodeWithTag("add-save").performClick()
        waitForIdle()
        onNodeWithTag("add-success").assertExists()
        val after = services.reagents.get(ids.etilReagent)!!
        assertEquals(MeasureUnit.L, after.minStockUnit)
        assertEquals(7_000_000L, after.minStock)
    }

    // ---------------------------------------------------------------- #9 saqlanmagan shakl

    @Test
    fun unsavedAddReagentFormAsksBeforeLeavingAndTheCurrentItemDoesNothing() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.ADD_REAGENT)
        // Toza shakl — so‘ralmaydi.
        onNodeWithTag("nav-REAGENTS").performClick()
        waitForIdle()
        assertEquals(Page.REAGENTS, state.page)
        onNodeWithTag("nav-ADD_REAGENT").performClick()
        waitForIdle()
        onNodeWithTag("add-name").performTextInput("Metanol")
        val serial = state.navigation.serial
        // Ochiq sahifaning bandi shaklni tozalamaydi.
        onNodeWithTag("nav-ADD_REAGENT").performClick()
        waitForIdle()
        assertEquals(serial, state.navigation.serial)
        onNodeWithTag("add-name").assert(hasText("Metanol"))
        onNodeWithText("Saqlanmagan o‘zgarishlar bor").assertDoesNotExist()

        onNodeWithTag("nav-STOCK").performClick()
        waitForIdle()
        onNodeWithText("Saqlanmagan o‘zgarishlar bor").assertExists()
        assertEquals(Page.ADD_REAGENT, state.page)
        dialogButton("Sahifada qolish").performClick()
        waitForIdle()
        onNodeWithText("Saqlanmagan o‘zgarishlar bor").assertDoesNotExist()
        onNodeWithTag("add-name").assert(hasText("Metanol"))

        onNodeWithTag("nav-STOCK").performClick()
        waitForIdle()
        dialogButton("Saqlamasdan chiqish").performClick()
        waitForIdle()
        assertEquals(Page.STOCK, state.page)
        assertNull(state.pendingNavigation)
    }

    @Test
    fun theBellAndTheOtherFormsAreGuardedToo() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.ISSUES)
        onNodeWithTag("issue-doc").performTextInput("T-77")
        onNodeWithTag("notifications").performClick()
        onNodeWithTag("notification-0").performClick()
        waitForIdle()
        assertEquals(Page.ISSUES, state.page)
        dialogButton("Saqlamasdan chiqish").performClick()
        waitForIdle()
        assertEquals(Page.EXPIRY, state.page)

        // Kirim sahifasi: hujjat raqami yozilgan.
        state.navigate(Page.RECEIPTS)
        waitForIdle()
        onNodeWithTag("receipt-doc").performTextInput("F-1")
        state.navigate(Page.DASHBOARD)
        waitForIdle()
        assertEquals(Page.RECEIPTS, state.page)
        assertEquals(Page.DASHBOARD, state.pendingNavigation?.page)
        dialogButton("Sahifada qolish").performClick()
        waitForIdle()
        assertNull(state.pendingNavigation)
        onNodeWithTag("receipt-doc").assert(hasText("F-1"))
    }

    // ---------------------------------------------------------------- #10 eksport

    @Test
    fun searchTextExportsAsASearchLineAndWritesTheRowsOnScreen() = runDesktopComposeUiTest(1366, 768) {
        val target = out.resolve("seyf.xlsx")
        dialogs.saveAnswer = target
        app(Page.REAGENTS)
        // «Seyf» — saqlash joyi (reaktiv nomi emas): ekranda faqat KN-09.
        onNodeWithTag("filter-text").performTextInput("Seyf")
        waitForIdle()
        onNodeWithTag("reagents-row-${ids.kn09}").assertExists()
        onNodeWithTag("reagents-row-${ids.es001}").assertDoesNotExist()
        mainClock.autoAdvance = false
        onNodeWithTag("reagents-export-excel").performClick()
        mainClock.advanceTimeBy(300)
        val rows = rowsOf(target)
        assertTrue(rows.any { it.text(0) == "Qidiruv: Seyf" })
        assertFalse(rows.any { it.text(0).startsWith("Reaktiv: ") })
        val header = rows.indexOfFirst { it.text(0) == "№" }
        assertEquals(listOf("KN-09"), rows.drop(header + 1).map { it.text(2) }.filter { it.isNotEmpty() })
        assertTrue(rows.none { it.text(0) == "Ma’lumot topilmadi." })
    }

    @Test
    fun everyOtherPageFilterIsDescribedInTheExport() = runDesktopComposeUiTest(1366, 768) {
        val target = out.resolve("filtrlar.xlsx")
        dialogs.saveAnswer = target
        app(Page.REAGENTS)
        onNodeWithTag("filter-empty").performClick() // «Qoldig‘i yo‘qlar ham» o‘chadi
        onNodeWithTag("filter-archived").performClick()
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag("reagents-export-excel").performClick()
        mainClock.advanceTimeBy(300)
        val rows = rowsOf(target)
        assertTrue(rows.any { it.text(0) == "Arxivdagilar ham" })
        assertTrue(rows.any { it.text(0) == "Qoldig‘i yo‘qlar ko‘rsatilmagan" })
    }

    // ---------------------------------------------------------------- #19 / #20 davr va tozalash

    @Test
    fun anInvalidTypedPeriodDateBlocksThePreviewAndTheSave() = runDesktopComposeUiTest(1366, 768) {
        val target = out.resolve("davr.xlsx")
        dialogs.saveAnswer = target
        val state = app(Page.REPORTS)
        onNodeWithTag("report-kind-RECEIPTS").performScrollTo().performClick()
        onNodeWithTag("report-period-from").performTextInput("31.02.2026")
        onNodeWithTag("report-show").performClick()
        waitForIdle()
        onNodeWithTag("report-table").assertDoesNotExist()
        onNodeWithText("Davr sanasini to‘g‘rilang", substring = true).assertExists()
        onNodeWithTag("report-save-excel").performClick()
        waitForIdle()
        assertEquals(emptyList<String>(), dialogs.calls)
        assertFalse(java.nio.file.Files.exists(target))
        assertNull(state.unexpectedError)

        // Sana to‘g‘rilandi — hisobot tuziladi.
        onNodeWithTag("report-period-from").performTextInput("")
        onNodeWithText("Filtrlarni tozalash (1)").performClick()
        waitForIdle()
        onNodeWithTag("report-period-from").performTextInput("01.01.2026")
        onNodeWithTag("report-show").performClick()
        waitForIdle()
        onNodeWithTag("report-table").assertExists()
    }

    @Test
    fun clearFiltersAlsoClearsInvalidTypedDatesAndCountsThemAsActive() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        onNodeWithText("Filtrlarni tozalash").assert(isEnabled().not())
        onNodeWithText("Kengaytirilgan filtr").performClick()
        // Chala va noto‘g‘ri sana filtrga o‘tmaydi, lekin faol filtr sifatida sanaladi.
        onNodeWithTag("filter-expires-from").performTextInput("31.02.2026")
        waitForIdle()
        onNodeWithText("Filtrlarni tozalash (1)").assertExists()
        onNodeWithText("Filtrlarni tozalash (1)").performClick()
        waitForIdle()
        onNodeWithTag("filter-expires-from").assert(hasText("31.02.2026").not())
        onNodeWithText("Filtrlarni tozalash").assert(isEnabled().not())
    }

    // ---------------------------------------------------------------- #22 / #23 bosh sahifa

    @Test
    fun departmentChartIsSortedWithoutZeroRowsAndLinksToTheRest() = runDesktopComposeUiTest(1366, 768) {
        services.directory.addDepartment("Bo‘sh bo‘lim")
        services.directory.addDepartment("Katta bo‘lim")
        addBatch("Aseton", "AS-1", "2028-01-01", "900", department = dept("Katta bo‘lim"))
        repeat(14) { i ->
            val name = "Qo‘shimcha ${i + 1}"
            services.directory.addDepartment(name)
            addBatch("Metanol", "MT-$i", "2028-01-01", "${i + 1}", department = dept(name))
        }
        val state = app(Page.DASHBOARD)
        onNodeWithText("Bo‘sh bo‘lim").assertDoesNotExist()
        // Eng katta qoldiq birinchi.
        val big = onNodeWithText("Katta bo‘lim").assertIsDisplayed().fetchSemanticsNode().positionInRoot.y
        listOf("Gistologiya", "Biologiya", "Sud-kimyo", "Morfologiya", "Qo‘shimcha 14").forEach { name ->
            onAllNodes(hasText(name) and hasAnyAncestor(hasTestTag("dept-chart"))).fetchSemanticsNodes().forEach {
                assertTrue("$name «Katta bo‘lim» dan yuqorida", big < it.positionInRoot.y)
            }
        }
        onNodeWithTag("dept-chart-more").assertIsDisplayed().assert(hasText("— hammasi", substring = true))
        onNodeWithTag("dept-chart-more").performClick()
        waitForIdle()
        assertEquals(Page.DEPARTMENTS, state.page)
    }

    private fun donutLegendStaysBesideTheRing(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        app(Page.DASHBOARD)
        val donut = onNodeWithTag("state-donut").performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        listOf("Yaroqli", "1–3 oy qoldi", "1 oy yoki kam qoldi", "Muddati o‘tgan", "Muddati kiritilmagan").forEach { label ->
            val node = onNode(hasText(label) and hasAnyAncestor(hasTestTag("state-donut"))).assertIsDisplayed().fetchSemanticsNode()
            // Izoh halqa yonida (ostida emas) va to‘liq (qisqartirilmagan) ko‘rinadi.
            assertTrue("$label: izoh halqa ostiga tushgan", node.positionInRoot.x >= donut.positionInRoot.x + 100f)
            assertTrue("$label: eni ${node.size.width}", node.size.width >= label.length * 5)
        }
    }

    @Test
    fun donutLegendKeepsAllFiveRowsBesideTheRingAt1366() = donutLegendStaysBesideTheRing(1366, 768)

    @Test
    fun donutLegendKeepsAllFiveRowsBesideTheRingAt1280() = donutLegendStaysBesideTheRing(1280, 720)

    // ---------------------------------------------------------------- #25 / #27 yozuvlar

    @Test
    fun issuedColumnExplainsItIsNetOfReturnsAndTheEmptyCheckboxesShareOneName() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.REAGENTS)
        onNodeWithTag("reagents-th-issued").assert(hasText("Jami berilgan miqdor"))
        onNode(hasContentDescription("qaytarilganlar ayirilgan", substring = true)).assertExists()
        onNodeWithText("Qoldig‘i yo‘qlar ham").assertExists()
        onNodeWithText("Tugagan partiyalar").assertDoesNotExist()
        state.navigate(Page.EXPIRY)
        waitForIdle()
        onNodeWithText("Qoldig‘i yo‘qlar ham").assertExists()
        onNodeWithText("Qoldig‘i yo‘qlarni ham ko‘rsatish").assertDoesNotExist()
    }

    // ---------------------------------------------------------------- #28 takliflar ro‘yxati

    @Test
    fun comboSuggestionsAreWiderThanTheFieldAndLongNamesStayReadable() = runDesktopComposeUiTest(1366, 768) {
        val long = "Etilendiamintetrasirka kislotaning dinatriyli tuzi dihidrati, analitik sinf, qayta kristallangan, ampulada"
        addBatch(long, "ED-1", "2028-01-01", "1")
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-name").performTextInput("Etilendiamin")
        val field = onNodeWithTag("add-name").fetchSemanticsNode().size.width
        val item = onNodeWithTag("add-name-item-0").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("ro‘yxat ${item.size.width} px, maydon $field px", item.size.width > field)
        // Uzun nom 2 qatorga o‘raladi (bir qatorli band ~34 px).
        assertTrue("balandligi ${item.size.height}", item.size.height > 40)
        onNodeWithTag("add-name-item-0").assert(hasText(long.take(40), substring = true))
    }

    // ---------------------------------------------------------------- #29 miqdor ustunlari

    @Test
    fun aNarrowQuantityColumnCutsTheNumberWithAnEllipsisInsteadOfWrappingAtTheThousandsSpace() = runDesktopComposeUiTest(1366, 768) {
        val id = addBatch("Aaa suv", "SU-1", "2028-01-01", "99999999,999", unit = MeasureUnit.ML)
        val remainder = services.queries.batchRows(uz.reaktivombor.desktop.service.BatchFilter(), DemoData.TODAY).first { it.batchId == id }.remainder
        app(Page.REAGENTS)
        // Foydalanuvchi ustunni toraytirdi (sichqoncha bilan chegarasini suridi).
        onNodeWithTag("reagents-th-remainder").performScrollTo()
        waitForIdle()
        onNodeWithTag("reagents-resize-remainder", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(-25f, 0f))
            moveBy(Offset(-25f, 0f))
            release()
        }
        waitForIdle()
        val text = qtyNumber(remainder, MeasureUnit.ML)
        // Kelgan miqdor bilan joriy qoldiq bir xil matn: «Joriy qoldiq» ustuni — keyingisi.
        val cell = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().lastOrNull { n ->
            generateSequence(n) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == "reagents-row-$id" }
        }
        assertTrue("raqam yacheykasi topilmadi", cell != null)
        val layouts = mutableListOf<TextLayoutResult>()
        cell!!.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        // Bir qator, bo‘linmaydi va sig‘masa oxirida «…» chiziladi (ilgari minglik bo‘shlig‘ida bo‘linib,
        // qolgani «…»siz jimgina tushib qolardi: «99»).
        assertEquals(1, layout.lineCount)
        assertFalse("minglik bo‘shlig‘ida o‘raladi", layout.layoutInput.softWrap)
        assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
    }

    // ---------------------------------------------------------------- #33 qo‘ng‘iroqcha va qidiruv

    @Test
    fun theBellCapsItsListAndPointsToTheRest() = runDesktopComposeUiTest(1366, 768) {
        var opened: Page? = null
        val items = List(45) {
            ShellNotification("Bildirishnoma $it", Severity.WARNING, if (it % 3 == 0) Page.STOCK else Page.EXPIRY)
        }
        setContent {
            AppTheme { AppShell(Page.DASHBOARD, { opened = it }, items, today = DemoData.TODAY) { Text("sahifa") } }
        }
        onNodeWithTag("notifications").performClick()
        onNodeWithTag("notification-${BELL_MAX_ITEMS - 1}").assertExists()
        onNodeWithTag("notification-$BELL_MAX_ITEMS").assertDoesNotExist()
        onNodeWithTag("notification-more").assert(hasText("Yana 15 ta", substring = true))
        onNodeWithTag("notification-more").performScrollTo().performClick()
        waitForIdle()
        assertEquals(Page.EXPIRY, opened)
    }

    @Test
    fun theSearchFieldWaitsForTheTypingToStopBeforeQuerying() = runDesktopComposeUiTest(1366, 768) {
        app(Page.REAGENTS)
        mainClock.autoAdvance = false
        onNodeWithTag("filter-text").performTextInput("Xlor")
        mainClock.advanceTimeBy(60)
        // Matn ko‘rinadi, jadval hali o‘zgarmagan.
        onNodeWithTag("filter-text").assert(hasText("Xlor"))
        onNodeWithTag("reagents-row-${ids.es001}").assertExists()
        mainClock.advanceTimeBy(400)
        onNodeWithTag("reagents-row-${ids.es001}").assertDoesNotExist()
        onNodeWithTag("reagents-row-${ids.xl131}").assertExists()
    }

    // ---------------------------------------------------------------- #34 reaktiv tarixi sarlavhasi

    @Test
    fun aLongReagentNameDoesNotSqueezeTheLedgerSummary() = runDesktopComposeUiTest(1366, 768) {
        val long = "Natriy gidroksid 0,1 N standart-titr eritmasi (fiksanal) ampulada, 10 ta komplekt, analitik sinf"
        val batch = addBatch(long, "N-9", "2028-01-01", "5")
        services.stock.issue(
            IssueInput(
                date = DemoData.TODAY, docNo = "T-90", issuedBy = "Karimova D.",
                lines = listOf(IssueLine(batch, "1", MeasureUnit.L, RecipientType.DEPARTMENT, departmentId = dept("Biologiya"), receiver = "Rahimov A.")),
            ),
        )
        val reagentId = services.reagents.list().first { it.name == long }.id
        app(Page.HISTORY, PageIntent.HistoryOf(batchId = batch, reagentId = reagentId))
        val summary = hasAnyAncestor(hasTestTag("ledger-summary"))
        listOf("Harakatlar", "Joriy qoldiq", "4 l").forEach { text ->
            val node = onNode(hasText(text, substring = true) and summary).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$text: eni ${node.size.width}", node.size.width >= text.length * 4)
        }
    }
}

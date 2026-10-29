package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Xlsx
import uz.reaktivombor.desktop.service.AuditAction
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.ExcelImport
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** «Reaktiv qo‘shish» → «Exceldan import»: shablon → to‘ldirilgan fayl → ko‘rib chiqish → import (natija bazada tekshiriladi). */
@OptIn(ExperimentalTestApi::class)
class AddReagentImportUiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private val today = DemoData.TODAY
    private val dialogs = FakeFileDialogs()

    @Before
    fun setUp() {
        services = DemoData.seeded(tmp.newFolder("data").toPath()).first
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.app(): AppState {
        val state = AppState(services, dialogs, Page.ADD_REAGENT)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun SkikoComposeUiTest.inside(tag: String, text: String) = onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag)))

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun batches(name: String) = services.queries.batchRows(BatchFilter(text = name), today)

    companion object {
        /**
         * Shablon ustunlari tartibida to‘ldirilgan fayl: 2-qator — to‘g‘ri, 3-qator — seriyasiz (xato), 4-qator — yangi bo‘lim
         * (ogohlantirish), 5-qator — tushunarsiz birlik (xato).
         */
        fun filledFile(path: Path, today: LocalDate): Path {
            val h = ExcelImport.HEADERS
            fun col(c: ExcelImport.Column) = h.indexOf(c.header)
            val rows: List<Map<ExcelImport.Column, Any>> = listOf(
                mapOf(
                    ExcelImport.Column.NAME to "Metanol", ExcelImport.Column.SERIES to "MT-7", ExcelImport.Column.RECEIVED to today.minusDays(3),
                    ExcelImport.Column.EXPIRES to today.plusDays(400), ExcelImport.Column.QTY to 2.5, ExcelImport.Column.UNIT to "litr",
                    ExcelImport.Column.DEPARTMENT to "Biologiya", ExcelImport.Column.RECEIVED_BY to "Karimova D.",
                ),
                mapOf(
                    ExcelImport.Column.NAME to "Aseton", ExcelImport.Column.RECEIVED to "05.10.2026", ExcelImport.Column.EXPIRES to "05.10.2027",
                    ExcelImport.Column.QTY to "1 250,5", ExcelImport.Column.UNIT to "ml",
                ),
                mapOf(
                    ExcelImport.Column.NAME to "Xloroform", ExcelImport.Column.SERIES to "XL-200", ExcelImport.Column.RECEIVED to today.minusDays(1),
                    ExcelImport.Column.EXPIRES to today.plusDays(60), ExcelImport.Column.QTY to 500, ExcelImport.Column.UNIT to "ml",
                    ExcelImport.Column.DEPARTMENT to "Toksikologiya",
                ),
                mapOf(
                    ExcelImport.Column.NAME to "Ammiak", ExcelImport.Column.SERIES to "AM-1", ExcelImport.Column.RECEIVED to today.minusDays(1),
                    ExcelImport.Column.EXPIRES to today.plusDays(100), ExcelImport.Column.QTY to 1, ExcelImport.Column.UNIT to "xyz",
                ),
            )
            Files.newOutputStream(path).use { out ->
                Xlsx.write(out) { wb ->
                    val ws = wb.newWorksheet(ExcelImport.SHEET)
                    h.forEachIndexed { c, title -> ws.value(0, c, title) }
                    rows.forEachIndexed { r, values ->
                        values.forEach { (column, v) ->
                            when (v) {
                                is LocalDate -> ws.value(r + 1, col(column), v)
                                is Number -> ws.value(r + 1, col(column), v)
                                else -> ws.value(r + 1, col(column), v.toString())
                            }
                        }
                    }
                }
            }
            return path
        }
    }

    @Test
    fun templateThenFilledFileThenPreviewThenImport() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        // 1. Shablon.
        val template = tmp.root.toPath().resolve("shablon.xlsx")
        dialogs.saveAnswer = template
        onNodeWithTag("import-template").performScrollTo().performClick()
        waitForIdle()
        assertEquals("save:reaktivlar-import-shabloni.xlsx", dialogs.calls.last())
        ReadableWorkbook(template.toFile()).use { wb ->
            val header = wb.firstSheet.read()[0]
            assertEquals(ExcelImport.HEADERS, (0 until header.cellCount).map { header.getCellText(it) })
        }

        // 2. To‘ldirilgan fayl → ko‘rib chiqish (bazaga hech narsa yozilmaydi).
        val before = services.queries.batchRows(BatchFilter(includeArchived = true), today).size
        dialogs.openAnswer = filledFile(tmp.root.toPath().resolve("partiyalar.xlsx"), today)
        onNodeWithTag("import-pick").performScrollTo().performClick()
        waitForIdle()
        assertEquals("open:xlsx", dialogs.calls.last())
        onNodeWithTag("import-counts").assert(hasText("4 ta qator: 2 ta to‘g‘ri, 2 ta xato · 1 ta ogohlantirish"))
        inside("import-preview-row-2", "Metanol").assertExists()
        inside("import-preview-row-2", "2,5").assertExists()
        inside("import-preview-row-2", "To‘g‘ri").assertExists()
        inside("import-preview-row-3", "Seriya raqami kiritilishi shart.").assertExists()
        inside("import-preview-row-4", "Toksikologiya (yangi)").assertExists()
        inside("import-preview-row-4", "Yangi bo‘lim qo‘shiladi").assertExists()
        inside("import-preview-row-5", "«xyz» noma’lum").assertExists()
        assertEquals(before, services.queries.batchRows(BatchFilter(includeArchived = true), today).size)
        assertTrue(services.directory.departments().none { it.name == "Toksikologiya" })

        // Qatorni tanlash — to‘liq xatolar ro‘yxati.
        onNodeWithTag("import-preview-row-5").performScrollTo().performClick()
        inside("import-details", "5-qator: Ammiak").assertExists()
        // «Faqat xatoli qatorlar».
        onNodeWithTag("import-only-errors").performScrollTo().performClick()
        onNodeWithTag("import-preview-row-2").assertDoesNotExist()
        onNodeWithTag("import-preview-row-3").assertExists()
        onNodeWithTag("import-only-errors").performScrollTo().performClick()

        // 3. Import: xatoli qatorlar bor — avval tasdiq.
        onNodeWithTag("import-run").performScrollTo().assert(hasText("Import qilish (2 ta)")).performClick()
        dialogButton("2 ta qatorni import qilish").performClick()
        waitForIdle()
        assertEquals(before + 2, services.queries.batchRows(BatchFilter(includeArchived = true), today).size)
        val metanol = batches("Metanol").single()
        assertEquals("MT-7", metanol.series)
        assertEquals(2_500_000L, metanol.remainder)
        assertEquals(MeasureUnit.L, metanol.unit)
        assertEquals("Biologiya", metanol.departmentName)
        val xl = batches("Xloroform").single { it.series == "XL-200" }
        assertEquals(500_000L, xl.remainder)
        assertEquals("Toksikologiya", xl.departmentName)
        assertTrue(batches("Aseton").isEmpty())
        assertTrue(services.queries.auditLogs().any { it.action == AuditAction.EXCEL_IMPORTED && it.details.contains("partiyalar.xlsx") })
        inside("import-done", "2 ta partiya qo‘shildi, 2 ta qator o‘tkazib yuborildi").assertExists()
        onNodeWithTag("import-preview").assertDoesNotExist()

        // 4. Xuddi shu fayl qayta: qo‘shilganlari endi takror — import qilinadigan qator yo‘q.
        onNodeWithTag("import-pick").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("import-counts").assert(hasText("4 ta qator: 0 ta to‘g‘ri, 4 ta xato"))
        inside("import-preview-row-2", "bazada allaqachon bor").assertExists()
        onNodeWithTag("import-run").assertIsNotEnabled()
        assertEquals(before + 2, services.queries.batchRows(BatchFilter(includeArchived = true), today).size)
        assertEquals(Page.ADD_REAGENT, state.page)
    }

    @Test
    fun unchangedTemplateShowsOnlyTheExampleRowAndImportsNothing() = runDesktopComposeUiTest(1366, 768) {
        app()
        val template = tmp.root.toPath().resolve("shablon.xlsx")
        dialogs.saveAnswer = template
        onNodeWithTag("import-template").performScrollTo().performClick()
        waitForIdle()
        val before = services.queries.batchRows(BatchFilter(includeArchived = true), today).size
        dialogs.openAnswer = template
        onNodeWithTag("import-pick").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("import-counts").assert(hasText("1 ta qator: 0 ta to‘g‘ri, 0 ta xato · 1 ta namuna qator (import qilinmaydi)"))
        inside("import-preview-row-2", "Namuna qator").assertExists()
        inside("import-preview-row-2", "namuna qator — o‘chiring").assertExists()
        onNodeWithTag("import-run").assertIsNotEnabled()
        assertEquals(before, services.queries.batchRows(BatchFilter(includeArchived = true), today).size)
    }

    @Test
    fun cleanFileImportsWithoutConfirmationAndShowsTheResult() = runDesktopComposeUiTest(1366, 768) {
        val state = app()
        val path = tmp.root.toPath().resolve("toza.xlsx")
        Files.newOutputStream(path).use { out ->
            Xlsx.write(out) { wb ->
                val ws = wb.newWorksheet("Reaktivlar")
                listOf("Reaktiv nomi", "Seriya", "Kelgan sana", "Yaroqlilik muddati", "Miqdor", "Birlik").forEachIndexed { c, t -> ws.value(0, c, t) }
                listOf("Eozin", "EO-90", today.minusDays(2), today.plusDays(500), 250, "ml").forEachIndexed { c, v ->
                    when (v) {
                        is LocalDate -> ws.value(1, c, v)
                        is Number -> ws.value(1, c, v)
                        else -> ws.value(1, c, v.toString())
                    }
                }
            }
        }
        dialogs.openAnswer = path
        onNodeWithTag("import-pick").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("import-counts").assert(hasText("1 ta qator: 1 ta to‘g‘ri, 0 ta xato"))
        onNodeWithTag("import-run").performScrollTo().performClick()
        waitForIdle()
        assertEquals(250_000L, batches("Eozin").single { it.series == "EO-90" }.remainder)
        onNode(hasText("Ro‘yxatda ko‘rish") and hasAnyAncestor(hasTestTag("import-done"))).performClick()
        waitForIdle()
        assertEquals(Page.REAGENTS, state.page)
    }

    @Test
    fun unreadableFileShowsAnErrorAndNoPreview() = runDesktopComposeUiTest(1366, 768) {
        app()
        dialogs.openAnswer = tmp.root.toPath().resolve("buzuq.xlsx").also { Files.writeString(it, "bu excel emas") }
        onNodeWithTag("import-pick").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("import-preview").assertDoesNotExist()
        inside("message-error", "Bazaga hech narsa yozilmadi.").assertExists()
        onNodeWithTag("import-steps").assertExists()
    }

    @Test
    fun headerShortcutScrollsToTheImportSection() = runDesktopComposeUiTest(1366, 768) {
        app()
        fun span(): Pair<Float, Float> = onNodeWithTag("excel-import").fetchSemanticsNode().let { it.positionInRoot.y to it.positionInRoot.y + it.size.height }
        val (_, bottom) = span()
        assertTrue("bo‘lim dastlab to‘liq ko‘rinmaydi: $bottom", bottom > 768f)
        onNodeWithTag("add-goto-import").performClick()
        waitForIdle()
        val (top, end) = span()
        assertTrue("bo‘lim to‘liq ko‘rinadi: $top..$end", top >= 0f && end <= 768f)
    }
}

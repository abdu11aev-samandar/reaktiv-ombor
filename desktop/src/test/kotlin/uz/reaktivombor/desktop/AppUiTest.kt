package uz.reaktivombor.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class AppUiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val today = LocalDate.parse("2026-10-21")

    private val noDialogs = object : FileDialogs {
        override fun openJson(): Path? = null
        override fun saveFile(title: String, defaultName: String): Path? = null
        override fun openFolder(dir: Path) {}
    }

    private fun ComposeUiTest.screenshot(name: String) {
        waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SemanticsNodeInteraction.key(k: Key) = performKeyInput { pressKey(k) }

    @Test
    fun typingARowLikeInExcel() = runComposeUiTest {
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        setContent { ReaktivlarApp(state, store, noDialogs, todayProvider = { today }, autoSave = false, refreshToday = false) }

        onNodeWithText("Xloroform").assertExists()
        screenshot("01-jadval")

        // Yangi qator: nom → Tab → sana → Tab → miqdor → Tab → birlik → Tab → muddat → Tab → qoldiq → Enter
        onNodeWithText("Yangi qator").performClick()
        val cell = onNodeWithTag("cell-editor")
        cell.performTextInput("Ammiak")
        cell.key(Key.Tab)
        onNodeWithTag("cell-editor").performTextInput("30.09.2026")
        onNodeWithTag("cell-editor").key(Key.Tab)
        onNodeWithTag("cell-editor").performTextInput("2,5")
        onNodeWithTag("cell-editor").key(Key.Tab)
        onNodeWithTag("cell-editor").performTextInput("m")
        onNodeWithTag("cell-editor").key(Key.Tab)
        onNodeWithTag("cell-editor").performTextInput("1.11.2026")
        onNodeWithTag("cell-editor").key(Key.Tab)
        onNodeWithTag("cell-editor").performTextInput("1 200")
        onNodeWithTag("cell-editor").key(Key.Enter)
        waitForIdle()

        val added = state.rows.last()
        assertEquals("Ammiak", added.name)
        assertEquals("ml", added.unit)
        assertTrue(added.check().complete)
        onNodeWithText("11").assertExists() // «Qolgan muddat (kun)»
        screenshot("02-yangi-qator")

        // Ctrl+Z — oxirgi katak bekor qilinadi, qator tugallanmagan bo‘lib qoladi.
        onNodeWithTag("cell-editor").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        assertEquals("", state.rows.last().stockAmount)
        assertEquals(setOf(Col.STOCK), state.rows.last().check().errors.keys)
        screenshot("03-tugallanmagan")
    }

    @Test
    fun filterAndSearch() = runComposeUiTest {
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        setContent { ReaktivlarApp(state, store, noDialogs, todayProvider = { today }, autoSave = false, refreshToday = false) }

        onNodeWithText("Ko‘rsatish").performClick() // qizillarni ko‘rsatish
        assertTrue(onAllNodesWithText("Xloroform").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("Naftol-2").fetchSemanticsNodes().isNotEmpty())
        onAllNodesWithText("Etil spirti").assertCountEquals(0)
        screenshot("04-qizil-filtr")
        onNodeWithText("Hammasi  5").performClick()
        onNodeWithTag("search").performTextInput("SHAVEL")
        assertTrue(onAllNodesWithText("Shavel kislota").fetchSemanticsNodes().isNotEmpty())
        onAllNodesWithText("Etil spirti").assertCountEquals(0)
    }

    private fun ComposeUiTest.app(state: SheetState) {
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        setContent { ReaktivlarApp(state, store, noDialogs, todayProvider = { today }, autoSave = false, refreshToday = false) }
    }

    private fun ComposeUiTest.type(text: String, then: Key? = null) {
        onNodeWithTag("cell-editor").performTextInput(text)
        then?.let { onNodeWithTag("cell-editor").key(it) }
    }

    private fun SheetState.byName(name: String) = rows.single { it.name == name }

    @Test
    fun closingTheWindowKeepsTheCellBeingTyped() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Yangi qator").performClick()
        type("Ammiak", Key.Tab)
        type("30.09.2026") // Enter bosilmadi — oyna yopilyapti
        waitForIdle()
        runOnIdle { state.flushPendingEdit() }
        assertEquals("30.09.2026", state.byName("Ammiak").producedAt)
    }

    @Test
    fun editingARowOutOfTheFilterKeepsTabAndEnterOnTheRightRows() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Ko‘rsatish").performClick() // qizillar: Xloroform, Naftol-2
        onNodeWithText("05.11.2026").performClick() // Xloroform muddati
        type("05.11.2027", Key.Tab) // endi yashil — lekin qator joyida qoladi
        type("300", Key.Enter)
        type("10.10.2027", Key.Enter) // Enter → keyingi qizil qator (Naftol-2) muddati
        waitForIdle()
        assertEquals("300", state.byName("Xloroform").stockAmount)
        assertEquals("05.11.2027", state.byName("Xloroform").expiresAt)
        assertEquals("40", state.byName("Naftol-2").stockAmount)
        assertEquals("10.10.2027", state.byName("Naftol-2").expiresAt)
        assertEquals(5, state.rows.size)
        assertTrue(onAllNodesWithText("Xloroform").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun enterAfterTabsGoesToTheFirstColumnOfTheNextRow() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Yangi qator").performClick()
        type("Ammiak", Key.Tab)
        type("30.09.2026", Key.Tab)
        type("2,5", Key.Tab)
        type("ml", Key.Tab)
        type("01.11.2027", Key.Tab)
        type("1200", Key.Enter)
        type("Kumush nitrat", Key.Enter)
        waitForIdle()
        assertTrue(state.byName("Ammiak").check().complete)
        assertEquals("", state.byName("Kumush nitrat").stockAmount)
        assertEquals(7, state.rows.size)
    }

    @Test
    fun arrowsLeaveATypedCellAndEnterMovesDownWithoutEditing() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithTag("cell-3-F").performClick() // Shavel kislota — ombordagi miqdor
        type("35", Key.DirectionRight)
        waitForIdle()
        assertEquals("35", state.byName("Shavel kislota").stockAmount)
        onNodeWithText("G3").assertExists() // tanlov o‘ngdagi “Holat” katagiga o‘tdi

        onNodeWithTag("cell-1-F").performClick() // Etil spirti — ombordagi miqdor
        onNodeWithTag("cell-editor").key(Key.Enter) // tahrirlamaydi, pastga tushadi
        type("1", Key.Enter)
        waitForIdle()
        assertEquals("750", state.byName("Etil spirti").stockAmount)
        assertEquals("1", state.byName("Xloroform").stockAmount)
    }

    @Test
    fun doubleClickEditsTheCellKeepingItsText() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Naftol-2").performTouchInput { doubleClick() }
        waitForIdle()
        type("-b", Key.Enter)
        waitForIdle()
        assertEquals(listOf("Naftol-2-b"), state.rows.filter { it.name.startsWith("Naftol") }.map { it.name })
    }

    @Test
    fun unitListShowsEveryUnitWhenEditingTheCurrentOne() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onAllNodesWithText("ml")[0].performClick()
        onNodeWithTag("cell-editor").key(Key.F2)
        waitForIdle()
        onNodeWithText("kg").assertExists()
        onNodeWithText("dona").assertExists()
        onNodeWithText("kg").performClick()
        waitForIdle()
        assertEquals("kg", state.byName("Etil spirti").unit)
    }

    @Test
    fun searchKeepsTheKeyboardWhileTyping() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithTag("search").performClick()
        // "n" tanlangan Etil spirti’ni yashiradi — yangi tanlangan katak fokusni tortib olmasligi kerak
        // (aks holda keyingi harflar reaktiv nomiga yozilardi).
        onNodeWithTag("search").performTextInput("n")
        waitForIdle()
        onNodeWithTag("search").assertIsFocused()
        onNodeWithTag("search").performTextInput("af")
        waitForIdle()
        onNodeWithTag("search").assertIsFocused()
        onNodeWithTag("search").assertTextEquals("naf")
    }

    @Test
    fun switchingToAFilterWithTheSameCountMovesTheSelectionToAVisibleRow() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("3 oydan ko‘p  1").performClick() // faqat Etil spirti
        onNodeWithTag("cell-1-F").performClick()
        onNodeWithText("1 – 3 oy  1").performClick() // faqat Shavel kislota
        waitForIdle()
        type("999", Key.Enter)
        waitForIdle()
        assertEquals("750", state.byName("Etil spirti").stockAmount)
        assertEquals("999", state.byName("Shavel kislota").stockAmount)
    }

    @Test
    fun undoKeepsTheSelectionOnTheUndoneCell() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Ko‘rsatish").performClick() // qizillar
        onNodeWithText("05.11.2026").performClick()
        type("05.11.2027", Key.Tab) // Xloroform qoldig‘i tanlandi
        onNodeWithTag("cell-editor").key(Key.Delete) // tasodifan o‘chirildi
        onNodeWithTag("cell-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft) }
        waitForIdle()
        type("300", Key.Enter)
        waitForIdle()
        assertEquals("300", state.byName("Xloroform").stockAmount)
        assertEquals("40", state.byName("Naftol-2").stockAmount)
    }

    @Test
    fun undoingANewRowDoesNotSendTheNextValueToRowOne() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Yangi qator").performClick()
        type("Amiak", Key.Tab)
        onNodeWithTag("cell-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft) }
        waitForIdle()
        type("30.09.2026", Key.Enter)
        waitForIdle()
        assertEquals("01.09.2026", state.byName("Etil spirti").producedAt)
        assertTrue(state.rows.none { it.name == "Amiak" })
    }

    @Test
    fun f2WhileTypingLetsTheArrowsMoveInsideTheText() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithTag("cell-3-F").performClick()
        type("35")
        onNodeWithTag("cell-editor").key(Key.F2)
        onNodeWithTag("cell-editor").key(Key.DirectionLeft)
        type("0", Key.Enter)
        waitForIdle()
        assertEquals("305", state.byName("Shavel kislota").stockAmount)
        assertEquals("20.12.2026", state.byName("Shavel kislota").expiresAt)
    }

    @Test
    fun clickingAnotherCellOfTheNewRowKeepsTypingInThatRow() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Yangi qator").performClick()
        type("Natriy")
        onNodeWithTag("cell-new-B").performClick() // xuddi shu qatorning “Kelgan vaqt” katagi
        waitForIdle()
        type("01.10.2026", Key.Enter)
        waitForIdle()
        assertEquals("01.10.2026", state.byName("Natriy").producedAt)
        assertEquals(6, state.rows.size)
    }

    @Test
    fun undoingANewRowAfterTabReturnsToItsFirstColumn() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithText("Yangi qator").performClick()
        type("Amiak", Key.Tab)
        onNodeWithTag("cell-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft) }
        waitForIdle()
        type("Ammiak", Key.Enter)
        waitForIdle()
        assertEquals("", state.byName("Ammiak").producedAt)
        assertEquals(6, state.rows.size)
    }

    @Test
    fun undoingAMultiRowPasteShowsEveryRestoredRowInTheFilter() = runComposeUiTest {
        val reds = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"Benzol","receivedAt":"2026-01-01","givenAmount":"1","unit":"ml","expiresAt":"2026-11-05","stockAmount":"10","createdAt":1},
              {"name":"Toluol","receivedAt":"2026-01-01","givenAmount":"1","unit":"ml","expiresAt":"2026-10-10","stockAmount":"20","createdAt":2},
              {"name":"Fenol","receivedAt":"2026-01-01","givenAmount":"1","unit":"ml","expiresAt":"2026-10-30","stockAmount":"30","createdAt":3}
            ]}
        """.trimIndent()
        val state = SheetState(SheetStore.fromJson(reds))
        val clipboard = object : ClipboardManager {
            var content: AnnotatedString? = null
            override fun getText(): AnnotatedString? = content
            override fun setText(annotatedString: AnnotatedString) { content = annotatedString }
        }
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                ReaktivlarApp(state, store, noDialogs, todayProvider = { today }, autoSave = false, refreshToday = false)
            }
        }
        onNodeWithText("05.11.2026").performClick() // Benzol muddati
        clipboard.content = AnnotatedString("05.11.2027\r\n10.10.2027\r\n")
        onNodeWithTag("cell-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.V); keyUp(Key.CtrlLeft) }
        waitForIdle()
        onNodeWithText("1 oydan kam  1").performClick() // faqat Fenol
        onNodeWithTag("cell-3-F").performClick() // Fenol — ombordagi miqdor
        type("70", Key.Enter) // ko‘rinish “muzlaydi”
        repeat(2) {
            onNodeWithTag("cell-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft) }
            waitForIdle()
        }
        assertEquals("05.11.2026", state.byName("Benzol").expiresAt)
        listOf("Benzol", "Toluol", "Fenol").forEach { name ->
            assertTrue(name, onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun givingToADepartmentFillsTheRemainder() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        onNodeWithTag("cell-1-G").performClick() // Etil spirti → Bo‘lim
        type("b", Key.Tab) // birinchi harf yetadi
        type("50", Key.Enter)
        waitForIdle()
        val etil = state.byName("Etil spirti")
        assertEquals("Biologiya", etil.department)
        assertEquals("50", etil.issuedAmount)
        assertTrue(etil.check().complete)
        onNodeWithText("700").assertExists() // Qoldiq = 750 − 50
        screenshot("05-qoldiq")
    }

    @Test
    fun theComputedColumnsCannotBeTypedInto() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        app(state)
        val before = state.rows
        onNodeWithTag("cell-1-A").performClick()
        onNodeWithTag("cell-editor").key(Key.MoveEnd) // J1 — Qolgan muddat
        onNodeWithText("J1").assertExists()
        onNodeWithTag("cell-editor").key(Key.Delete)
        onNodeWithTag("cell-editor").key(Key.DirectionLeft) // I1 — Qoldiq
        onNodeWithText("I1").assertExists()
        onNodeWithTag("cell-editor").key(Key.Delete)
        waitForIdle()
        assertEquals(before, state.rows)
    }

    @Test
    fun copyingAComputedCellCopiesItsNumber() = runComposeUiTest {
        val state = SheetState(SheetStore.fromJson(SAMPLE))
        val clipboard = object : ClipboardManager {
            var content: AnnotatedString? = null
            override fun getText(): AnnotatedString? = content
            override fun setText(annotatedString: AnnotatedString) { content = annotatedString }
        }
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                ReaktivlarApp(state, store, noDialogs, todayProvider = { today }, autoSave = false, refreshToday = false)
            }
        }
        onNodeWithTag("cell-1-A").performClick()
        onNodeWithTag("cell-editor").key(Key.MoveEnd) // J1 — Qolgan muddat
        onNodeWithTag("cell-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.C); keyUp(Key.CtrlLeft) }
        assertEquals("315", clipboard.content?.text)
        onNodeWithTag("cell-editor").key(Key.DirectionLeft) // I1 — Qoldiq
        onNodeWithTag("cell-editor").performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.X); keyUp(Key.CtrlLeft) }
        assertEquals("750", clipboard.content?.text)
        assertEquals("750", state.byName("Etil spirti").stockAmount) // Ctrl+X hisoblangan katakni o‘chirmaydi
    }

    @Test
    fun descendingSortKeepsEmptyCellsLast() {
        val rows = listOf(
            SheetRow(1, "A", createdAt = 1),
            SheetRow(2, "B", issuedAmount = "5", createdAt = 2),
            SheetRow(3, "C", issuedAmount = "25", createdAt = 3),
        )
        val checks = rows.associate { it.id to it.check(today) }
        val desc = visibleRows(rows, checks, "", Filter.ALL, Col.ISSUED to false, emptySet(), today)
        assertEquals(listOf("C", "B", "A"), desc.map { it.name })
        val asc = visibleRows(rows, checks, "", Filter.ALL, Col.ISSUED to true, emptySet(), today)
        assertEquals(listOf("B", "C", "A"), asc.map { it.name })
    }

    companion object {
        val SAMPLE = """
            {"format":"reaktiv-ombor","version":2,"rows":[
              {"name":"Etil spirti","producedAt":"2026-09-01","receivedAmount":"1000","unit":"ml","expiresAt":"2027-09-01","stockAmount":"750","createdAt":1},
              {"name":"Xloroform","producedAt":"2026-08-10","receivedAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"320","createdAt":2},
              {"name":"Shavel kislota","producedAt":"2026-07-15","receivedAmount":"250","unit":"g","expiresAt":"2026-12-20","stockAmount":"180.5","createdAt":3},
              {"name":"Naftol-2","producedAt":"2026-06-01","receivedAmount":"100","unit":"g","expiresAt":"2026-10-10","stockAmount":"40","createdAt":4},
              {"name":"Jelatin","producedAt":"20.09.2026","receivedAmount":"","unit":"g","expiresAt":"","stockAmount":"","createdAt":5}
            ]}
        """.trimIndent()
    }
}

package uz.reaktivombor.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
        onNodeWithText("11 kun qoldi").assertExists()
        screenshot("02-yangi-qator")

        // Ctrl+Z — oxirgi katak bekor qilinadi, qator tugallanmagan bo‘lib qoladi.
        onNodeWithTag("cell-editor").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        assertEquals("", state.rows.last().stockAmount)
        onNodeWithText("Tugallanmagan: 1 ta maydon").assertExists()
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
        assertEquals("30.09.2026", state.byName("Ammiak").receivedAt)
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
        onNodeWithText("180,5").performClick() // Shavel kislota qoldig‘i
        type("35", Key.DirectionRight)
        waitForIdle()
        assertEquals("35", state.byName("Shavel kislota").stockAmount)
        onNodeWithText("G3").assertExists() // tanlov o‘ngdagi “Holat” katagiga o‘tdi

        onNodeWithText("750").performClick() // Etil spirti qoldig‘i
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

    companion object {
        val SAMPLE = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"Etil spirti","receivedAt":"2026-09-01","givenAmount":"1000","unit":"ml","expiresAt":"2027-09-01","stockAmount":"750","createdAt":1},
              {"name":"Xloroform","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"320","createdAt":2},
              {"name":"Shavel kislota","receivedAt":"2026-07-15","givenAmount":"250","unit":"gr","expiresAt":"2026-12-20","stockAmount":"180.5","createdAt":3},
              {"name":"Naftol-2","receivedAt":"2026-06-01","givenAmount":"100","unit":"gr","expiresAt":"2026-10-10","stockAmount":"40","createdAt":4}
            ],"drafts":[
              {"name":"Jelatin","receivedAt":"20.09.2026","givenAmount":"","unit":"gr","expiresAt":"","stockAmount":"","createdAt":5}
            ]}
        """.trimIndent()
    }
}

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

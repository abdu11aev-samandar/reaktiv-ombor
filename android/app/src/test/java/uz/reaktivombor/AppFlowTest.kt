package uz.reaktivombor

import android.Manifest
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.reaktivombor.data.AppDatabase
import uz.reaktivombor.data.Reagent
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private fun hasClickLabel(label: String) =
    SemanticsMatcher("onClickLabel = $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

/** Testda WorkManager ishga tushirilmaydi. */
class TestApp : Application()

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w400dp-h860dp-xhdpi")
class AppFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao = AppDatabase.get(context).reagentDao()
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { dao.deleteAll() }
    }

    private fun reagent(name: String, expiresAt: LocalDate, created: Long) = Reagent(
        name = name,
        receivedAt = today.minusMonths(2),
        givenAmount = BigDecimal("1250.5"),
        unit = "gr",
        expiresAt = expiresAt,
        stockAmount = BigDecimal("800"),
        createdAt = created,
    )

    private fun click(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).performClick()

    /** Ekran rasmi build/screenshots/ ga (Robolectric’ning haqiqiy grafikasi bilan chiziladi). */
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun listShowsColouredNamesAndFormValidates() {
        runBlocking {
            dao.insertAll(
                listOf(
                    reagent("Etil spirti", today.plusMonths(8), 1),
                    reagent("Xloroform", today.plusDays(10), 2),
                    reagent("Shavel kislota", today.plusMonths(2), 3),
                    reagent("Naftol-2", today.minusDays(3), 4),
                ),
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Xloroform").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Etil spirti, 3 oydan ko‘p").assertExists()
        compose.onNodeWithContentDescription("Shavel kislota, 1 – 3 oy").assertExists()
        compose.onNodeWithContentDescription("Xloroform, 1 oydan kam").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Naftol-2, 1 oydan kam"))
        compose.onNodeWithContentDescription("Naftol-2, 1 oydan kam").assertExists()
        compose.onNodeWithText("10 kun qoldi").assertExists()
        compose.onNodeWithText("Muddati 3 kun oldin o‘tgan").assertExists()
        assertEquals(4, compose.onAllNodesWithText("1\u00A0250,5\u00A0gr", substring = true).fetchSemanticsNodes().size)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("1 oydan kam  2", useUnmergedTree = true).assertExists()
        screenshot("01-royxat")

        // Rang bo‘yicha filtr
        click("1 oydan kam  2")
        compose.onAllNodesWithText("Etil spirti").assertCountEquals(0)
        compose.onNodeWithText("Xloroform").assertExists()
        click("Hammasi  4")

        // Qidiruv: o' va o‘ bir xil
        compose.onNodeWithText("Nomi bo‘yicha qidirish…").performTextInput("NAFTOL")
        compose.onAllNodesWithText("Xloroform").assertCountEquals(0)
        compose.onNodeWithText("Naftol-2").assertExists()
        compose.onNodeWithContentDescription("Qidiruvni tozalash").performClick()

        // Forma: hammasi majburiy
        compose.onNodeWithContentDescription("Reaktiv qo‘shish").assertExists()
        click("Reaktiv qo‘shish")
        click("Saqlash")
        compose.onNodeWithText("Reaktiv nomi kiritilishi shart.").assertExists()
        compose.onNodeWithContentDescription("Reaktiv qo‘shish").assertDoesNotExist()
        compose.onNode(hasContentDescription("Kelgan vaqt kiritilishi shart.", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Yaroqlilik muddati kiritilishi shart.", substring = true)).assertExists()
        // Xatolar yozilgan sari yangilanadi.
        compose.onNodeWithText("Reaktiv nomi *").performTextInput("Formalin")
        compose.onAllNodesWithText("Reaktiv nomi kiritilishi shart.").assertCountEquals(0)
        compose.onNodeWithText("Berilgan miqdor kiritilishi shart.").assertExists()
        compose.onNodeWithText("Ombordagi miqdor kiritilishi shart.").assertExists()
        screenshot("02-forma-xatolar")
    }

    @Test
    fun newReagentCanBeSavedWithDatePicker() {
        compose.onNodeWithText("Hozircha reaktiv kiritilmagan.").assertExists()
        screenshot("00-bosh-royxat")

        click("Reaktiv qo‘shish")
        compose.onNodeWithText("Reaktiv nomi *").performTextInput("Ammiak")

        // Ikkala sana uchun ham kalendarda joriy oyning 1-kunini tanlaymiz.
        val firstOfMonth = "${today.month.getDisplayName(TextStyle.FULL, Locale.US)} 1, ${today.year}"
        compose.onAllNodes(hasClickLabel("Sanani tanlash"))[0].performClick()
        compose.onNode(hasText(firstOfMonth, substring = true) and hasClickAction()).performClick()
        click("Tanlash")

        compose.onAllNodes(hasClickLabel("Sanani tanlash"))[1].performClick()
        compose.onNode(hasText(firstOfMonth, substring = true) and hasClickAction()).performClick()
        click("Tanlash")

        compose.onNodeWithText("Berilgan miqdor *").performTextInput("2,5")
        compose.onNodeWithText("Ombordagi miqdor *").performTextInput("1 200")
        click("ml")
        screenshot("03-forma-toldirilgan")
        click("Saqlash")

        compose.waitUntil(5_000) { runBlocking { dao.getAll() }.isNotEmpty() }
        val saved = runBlocking { dao.getAll() }.single()
        assertEquals("Ammiak", saved.name)
        assertEquals(today.withDayOfMonth(1), saved.receivedAt)
        assertEquals(today.withDayOfMonth(1), saved.expiresAt)
        assertEquals(BigDecimal("2.500"), saved.givenAmount)
        assertEquals(BigDecimal("1200.000"), saved.stockAmount)
        assertEquals("ml", saved.unit)

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ammiak").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Ammiak, 1 oydan kam").assertExists()
        screenshot("04-saqlangan")
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-night-xhdpi")
    fun darkThemeList() {
        runBlocking {
            dao.insertAll(
                listOf(
                    reagent("Etil spirti", today.plusMonths(8), 1),
                    reagent("Xloroform", today.plusDays(10), 2),
                    reagent("Shavel kislota", today.plusMonths(2), 3),
                ),
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Xloroform").fetchSemanticsNodes().isNotEmpty() }
        screenshot("05-tungi-rejim")
    }
}

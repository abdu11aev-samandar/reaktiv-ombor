package uz.reaktivombor.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.ui.components.ErrorDialog
import uz.reaktivombor.desktop.ui.components.ReasonDialog
import uz.reaktivombor.desktop.ui.theme.AppTheme
import uz.reaktivombor.desktop.ui.theme.Severity
import java.io.File
import javax.imageio.ImageIO

/**
 * Ko‘rinish skrinshotlari (1366×768 — eng kichik ishchi ekran) desktop/build/ui-screens papkasiga yoziladi.
 * Ular ko‘z bilan tekshiriladi; testning o‘zi faqat hech narsa kesilmaganini tekshiradi.
 */
@OptIn(ExperimentalTestApi::class)
class UiScreenshotTest {
    private val notifications = listOf(
        ShellNotification("2 ta partiyaning yaroqlilik muddati o‘tgan (Xloroform XL-117, Formalin 10% FR-201)", Severity.ERROR, Page.EXPIRY),
        ShellNotification("4 ta partiyaning muddati 1 oy yoki kam qoldi", Severity.ERROR, Page.EXPIRY),
        ShellNotification("3 ta partiyaning muddati 1–3 oy qoldi", Severity.WARNING, Page.EXPIRY),
        ShellNotification("Kumush nitrat: qoldiq minimal miqdordan kam (12 g < 20 g)", Severity.WARNING, Page.STOCK),
    )

    private fun SkikoComposeUiTest.save(name: String) {
        waitForIdle()
        val dir = File("build/ui-screens").apply { mkdirs() }
        // Butun oyna — ochilgan menyu va oynalar bilan birga.
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test
    fun shellWithGroupedTableAt1366x768() = runDesktopComposeUiTest(width = 1366, height = 768) {
        setContent {
            AppTheme {
                AppShell(Page.REAGENTS, {}, notifications, today = DEMO_TODAY) { DemoReagentsPage() }
            }
        }
        save("01-shell-reagents")
        // 13-band (Sozlamalar) oynaga to‘liq sig‘adi — aylantirmasdan.
        val last = onNodeWithTag("nav-SETTINGS").fetchSemanticsNode()
        assertTrue("Sozlamalar: ${last.positionInRoot}", last.positionInRoot.y + last.size.height <= 768f)

        onNodeWithTag("table-row-2").performClick()
        save("10-selected-row")

        onNodeWithTag("table-columns").performClick()
        save("05-column-menu")
    }

    @Test
    fun dashboardAt1366x768() = runDesktopComposeUiTest(width = 1366, height = 768) {
        setContent {
            AppTheme {
                AppShell(Page.DASHBOARD, {}, notifications, today = DEMO_TODAY) { DemoDashboard() }
            }
        }
        save("02-dashboard")
        onNodeWithTag("notifications").performClick()
        save("04-notifications")
    }

    @Test
    fun formFieldsAndCalendar() = runDesktopComposeUiTest(width = 1366, height = 768) {
        setContent {
            AppTheme {
                AppShell(Page.ADD_REAGENT, {}, emptyList(), today = DEMO_TODAY) { DemoForm() }
            }
        }
        save("03-form")
        onNodeWithTag("demo-expires-calendar").performClick()
        save("06-calendar")
    }

    @Test
    fun comboSuggestionsOpen() = runDesktopComposeUiTest(width = 1366, height = 768) {
        setContent {
            AppTheme {
                AppShell(Page.ADD_REAGENT, {}, emptyList(), today = DEMO_TODAY) { DemoForm() }
            }
        }
        onNodeWithTag("demo-name").performTextInput(" spi")
        onNodeWithTag("demo-name").performKeyInput { pressKey(Key.DirectionDown) }
        save("11-combo")
    }

    @Test
    fun errorDialogWithDetails() = runDesktopComposeUiTest(width = 1366, height = 768) {
        setContent {
            AppTheme {
                AppShell(Page.BACKUP, {}, emptyList(), today = DEMO_TODAY) { DemoDashboard() }
                ErrorDialog(
                    message = "Zaxira nusxadan tiklab bo‘lmadi: fayl buzilgan yoki boshqa dasturga tegishli. Joriy ma’lumotlar o‘zgarmadi.",
                    details = "java.sql.SQLException: file is not a database\n\tat org.sqlite.core.DB.newSQLException(DB.java:1179)",
                    onDismiss = {},
                )
            }
        }
        onNodeWithText("Batafsil").performClick()
        save("12-error-dialog")
    }

    @Test
    fun comboSuggestionsAndDialog() = runDesktopComposeUiTest(width = 1366, height = 768) {
        setContent {
            AppTheme {
                var dialog by remember { mutableStateOf(true) }
                AppShell(Page.ADD_REAGENT, {}, emptyList(), today = DEMO_TODAY) { DemoForm() }
                if (dialog) {
                    ReasonDialog(
                        title = "Kirimni bekor qilish",
                        message = "Etil spirti 96%, ES-2401 — 20 l kirim bekor qilinadi. Ombordagi qoldiq kamayadi.",
                        onConfirm = { dialog = false },
                        onDismiss = { dialog = false },
                        confirmLabel = "Bekor qilish",
                        dismissLabel = "Orqaga",
                        danger = true,
                    )
                }
            }
        }
        save("07-reason-dialog")
    }

    @Test
    fun railScrollsOnALowWindow() = runDesktopComposeUiTest(width = 1280, height = 560) {
        setContent {
            AppTheme {
                AppShell(Page.SETTINGS, {}, notifications, today = DEMO_TODAY) { DemoDashboard() }
            }
        }
        save("08-low-window")
    }

    @Test
    fun icons() = runDesktopComposeUiTest(width = 1366, height = 560) {
        setContent { AppTheme { DemoIcons() } }
        save("09-icons")
    }
}

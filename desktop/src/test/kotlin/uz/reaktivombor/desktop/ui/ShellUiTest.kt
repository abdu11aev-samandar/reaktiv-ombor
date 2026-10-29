package uz.reaktivombor.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.ui.theme.AppTheme
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import uz.reaktivombor.desktop.ui.theme.statusStyle

@OptIn(ExperimentalTestApi::class)
class ShellUiTest {
    /** DESIGN.md §6 dagi tartib va nomlar. */
    private val expectedTitles = listOf(
        "Bosh sahifa", "Barcha reaktivlar", "Reaktiv qo‘shish", "Kirim operatsiyalari", "Sarf va berish",
        "Bo‘limlar bo‘yicha hisob", "Tumanlarga berilgan reaktivlar", "Yaroqlilik muddati nazorati", "Umumiy zaxiralar",
        "Hisobotlar", "Operatsiyalar tarixi", "Ma’lumotlarni zaxiralash", "Sozlamalar",
    )

    @Test
    fun thirteenPagesInTheSpecifiedOrder() {
        assertEquals(expectedTitles, Page.entries.map { it.title })
    }

    @Test
    fun railShowsEveryPageAndSwitches() = runDesktopComposeUiTest(1366, 768) {
        var current by mutableStateOf(Page.DASHBOARD)
        setContent {
            AppTheme {
                AppShell(current, { current = it }, emptyList(), today = DEMO_TODAY) { Text("Sahifa: ${current.title}") }
            }
        }
        onNodeWithText("Reaktiv Control").assertExists()
        onNodeWithText("Forensic Laboratory Management System").assertExists()
        var previousBottom = 0f
        Page.entries.forEach { page ->
            val item = onNodeWithTag("nav-${page.name}")
            item.assertIsDisplayed()
            onNodeWithText(page.title).assertExists()
            // Kesilmagan joyi (getBoundsInRoot ota-ona chegarasida kesadi).
            val node = item.fetchSemanticsNode()
            val top = node.positionInRoot.y
            val bottom = top + node.size.height
            assertTrue("${page.title} tartibi", top >= previousBottom)
            // 1366×768 da hammasi aylantirmasdan ko‘rinadi.
            assertTrue("${page.title}: $bottom", bottom <= 768f)
            previousBottom = bottom
        }
        onNodeWithTag("nav-DASHBOARD").assertIsSelected()

        onNodeWithTag("nav-STOCK").performClick()
        waitForIdle()
        assertEquals(Page.STOCK, current)
        onNodeWithText("Sahifa: Umumiy zaxiralar").assertExists()
        onNodeWithTag("nav-STOCK").assertIsSelected()

        // Klaviatura: fokusdagi bandda Enter.
        onNodeWithTag("nav-HISTORY").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("nav-HISTORY").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(Page.HISTORY, current)
    }

    @Test
    fun railScrollsInsteadOfClippingOnALowWindow() = runDesktopComposeUiTest(1280, 480) {
        var current by mutableStateOf(Page.DASHBOARD)
        setContent {
            AppTheme { AppShell(current, { current = it }, emptyList(), today = DEMO_TODAY) { Text(current.title) } }
        }
        onNodeWithTag("nav-SETTINGS").assertIsNotDisplayed()
        onNodeWithTag("nav-SETTINGS").performScrollTo().assertIsDisplayed()
        onNodeWithTag("nav-SETTINGS").performClick()
        waitForIdle()
        assertEquals(Page.SETTINGS, current)
    }

    @Test
    fun todayIsShownWithItsUzbekWeekday() = runDesktopComposeUiTest(1366, 768) {
        setContent { AppTheme { AppShell(Page.DASHBOARD, {}, emptyList(), today = DEMO_TODAY) {} } }
        onNodeWithText("29.10.2026").assertExists()
        onNodeWithText("Payshanba").assertExists()
    }

    @Test
    fun notificationBadgeCountsAndAnItemNavigates() = runDesktopComposeUiTest(1366, 768) {
        var current by mutableStateOf(Page.DASHBOARD)
        val items = listOf(
            ShellNotification("2 ta partiyaning muddati o‘tgan", Severity.ERROR, Page.EXPIRY),
            ShellNotification("Kumush nitrat: qoldiq minimaldan kam", Severity.WARNING, Page.STOCK),
            ShellNotification("4 ta partiyaning muddati 1 oy yoki kam qoldi", Severity.ERROR, Page.EXPIRY),
        )
        var notifications by mutableStateOf(items)
        setContent {
            AppTheme { AppShell(current, { current = it }, notifications, today = DEMO_TODAY) { Text(current.title) } }
        }
        onNodeWithTag("notification-badge", useUnmergedTree = true).assertTextEquals("3")
        onNodeWithTag("notifications").performClick()
        onNodeWithText("Bildirishnomalar").assertExists()
        onNodeWithText("3 ta").assertExists()
        onNodeWithTag("notification-1").performClick()
        waitForIdle()
        assertEquals(Page.STOCK, current)
        onNodeWithTag("notification-1").assertDoesNotExist() // ro‘yxat yopildi

        notifications = List(120) { items[it % 3] }
        waitForIdle()
        onNodeWithTag("notification-badge", useUnmergedTree = true).assertTextEquals("99+")

        notifications = emptyList()
        waitForIdle()
        onNodeWithTag("notification-badge", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("notifications").performClick()
        onNodeWithText("Hozircha bildirishnoma yo‘q.").assertExists()
    }

    @Test
    fun customNotificationHandler() = runDesktopComposeUiTest(1366, 768) {
        val clicked = mutableListOf<ShellNotification>()
        val item = ShellNotification("Zaxira nusxa 10 kundan beri olinmagan", Severity.INFO, Page.BACKUP)
        setContent {
            AppTheme { AppShell(Page.DASHBOARD, {}, listOf(item), onNotificationClick = { clicked += it }, today = DEMO_TODAY) {} }
        }
        onNodeWithTag("notifications").performClick()
        onNodeWithText("Zaxira nusxa 10 kundan beri olinmagan").performClick()
        waitForIdle()
        assertEquals(listOf(item), clicked)
    }

    @Test
    fun statusStylesMapTheFiveExpiryStates() {
        val labels = mapOf(
            "GREEN" to "Yaroqli",
            "YELLOW" to "1–3 oy qoldi",
            "RED" to "1 oy yoki kam qoldi",
            "EXPIRED" to "Muddati o‘tgan",
            "UNKNOWN" to "Muddati kiritilmagan",
        )
        labels.forEach { (name, label) -> assertEquals(label, statusStyle(name).label) }
        assertTrue(statusStyle("EXPIRED").bold)
        assertEquals(StatusStyles.RED, statusStyle("red"))
        assertEquals(StatusStyles.UNKNOWN, statusStyle("nimadir"))
        assertEquals(StatusStyles.UNKNOWN, statusStyle(null))
        // DESIGN.md §6 ranglari.
        assertEquals(0xFFE6F4EA.toInt(), statusStyle("GREEN").background.toArgb())
        assertEquals(0xFF1E7B34.toInt(), statusStyle("GREEN").foreground.toArgb())
        assertEquals(0xFFFFF4CC.toInt(), statusStyle("YELLOW").background.toArgb())
        assertEquals(0xFF8A6100.toInt(), statusStyle("YELLOW").foreground.toArgb())
        assertEquals(0xFFFDE2E1.toInt(), statusStyle("RED").background.toArgb())
        assertEquals(0xFFB42318.toInt(), statusStyle("RED").foreground.toArgb())
        assertEquals(0xFFF4D3D3.toInt(), statusStyle("EXPIRED").background.toArgb())
        assertEquals(0xFF7A1212.toInt(), statusStyle("EXPIRED").foreground.toArgb())
    }
}

package uz.reaktivombor.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.GroupExpansion
import uz.reaktivombor.desktop.ui.components.TableLayout
import uz.reaktivombor.desktop.ui.components.TableLayoutState
import uz.reaktivombor.desktop.ui.components.TableSelection
import uz.reaktivombor.desktop.ui.components.rememberTableLayoutState
import uz.reaktivombor.desktop.ui.theme.AppTheme
import uz.reaktivombor.desktop.ui.theme.statusStyle
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
class DataTableUiTest {
    private class Probe {
        lateinit var layout: TableLayoutState
        val saved = mutableListOf<String>()
        val clicked = mutableListOf<Long>()
        val opened = mutableListOf<Long>()
        val actions = mutableListOf<String>()
        val selection = TableSelection()
        val expansion = GroupExpansion()
    }

    /** Jadval (1366×768 oynada): [grouped] — reaktiv nomi bo‘yicha guruhlar. */
    private fun ComposeUiTest.table(grouped: Boolean = false, saved: String? = null): Probe {
        val probe = Probe()
        setContent {
            AppTheme {
                val columns = demoColumns { action, row -> probe.actions += "$action:${row.id}" }
                probe.layout = rememberTableLayoutState(columns, load = { saved }, save = { probe.saved += it })
                Column(Modifier.fillMaxSize()) {
                    if (grouped) {
                        DemoReagentsTable(probe.layout, Modifier.fillMaxSize(), probe.expansion) { action, row -> probe.actions += "$action:${row.id}" }
                    } else {
                        DataTable(
                            rows = DEMO_BATCHES,
                            columns = columns,
                            rowKey = { it.id },
                            layout = probe.layout,
                            modifier = Modifier.fillMaxSize(),
                            rowBackground = { statusStyle(it.state).background },
                            selection = probe.selection,
                            onRowClick = { probe.clicked += it.id },
                            onRowDoubleClick = { probe.opened += it.id },
                        )
                    }
                }
            }
        }
        return probe
    }

    /** Ko‘rinib turgan qatorlar tartibi (ekrandagi joyi bo‘yicha). */
    private fun ComposeUiTest.visibleOrder(ids: List<Long> = DEMO_BATCHES.map { it.id }): List<Long> =
        ids.mapNotNull { id ->
            val nodes = onAllNodes(hasTestTag("table-row-$id")).fetchSemanticsNodes()
            nodes.firstOrNull()?.let { id to it.boundsInRoot.top }
        }.sortedBy { it.second }.map { it.first }

    private fun ComposeUiTest.left(id: String): Dp = onNodeWithTag("table-th-$id").getBoundsInRoot().left

    private fun ComposeUiTest.width(id: String): Dp = onNodeWithTag("table-th-$id").getBoundsInRoot().let { it.right - it.left }

    @Test
    fun headerClickSortsAscendingDescendingAndBack() = runDesktopComposeUiTest(1366, 768) {
        val probe = table()
        val original = visibleOrder()
        assertEquals(DEMO_BATCHES.take(original.size).map { it.id }, original)

        onNodeWithTag("table-th-receivedQty").performClick()
        waitForIdle()
        onNodeWithContentDescription("O‘sish bo‘yicha saralangan", useUnmergedTree = true).assertExists()
        val asc = visibleOrder()
        val ascValues = asc.map { id -> DEMO_BATCHES.single { it.id == id }.receivedQty }
        assertEquals(ascValues.sorted(), ascValues)
        assertEquals(DEMO_BATCHES.minBy { it.receivedQty }.id, asc.first())

        onNodeWithTag("table-th-receivedQty").performClick()
        waitForIdle()
        onNodeWithContentDescription("Kamayish bo‘yicha saralangan", useUnmergedTree = true).assertExists()
        val descValues = visibleOrder().map { id -> DEMO_BATCHES.single { it.id == id }.receivedQty }
        assertEquals(descValues.sortedDescending(), descValues)

        onNodeWithTag("table-th-receivedQty").performClick() // uchinchi bosish — saralash olib tashlanadi
        waitForIdle()
        assertEquals(original, visibleOrder())
        assertNull(probe.layout.sort)
        // Har bir o‘zgarish saqlandi.
        assertEquals(3, probe.saved.size)
        assertEquals("receivedQty", TableLayout.fromJson(probe.saved[0])?.sort?.columnId)
        assertEquals(true, TableLayout.fromJson(probe.saved[1])?.sort?.descending)
    }

    @Test
    fun emptyValuesStayLastInBothDirections() = runDesktopComposeUiTest(1366, 768) {
        table()
        // Naftol-2 (id 6) muddati yo‘q — o‘sishda ham, kamayishda ham oxirida.
        onNodeWithTag("table-th-days").performClick()
        waitForIdle()
        val asc = DEMO_BATCHES.sortedWith(compareBy(nullsLast()) { it.daysLeft }).map { it.id }
        assertEquals(6L, asc.last())
        assertEquals(asc.take(visibleOrder().size), visibleOrder())
        onNodeWithTag("table-th-days").performClick()
        waitForIdle()
        assertEquals(probeDescFirst(), visibleOrder().first())
    }

    private fun probeDescFirst(): Long = DEMO_BATCHES.filter { it.daysLeft != null }.maxBy { it.daysLeft!! }.id

    @Test
    fun columnMenuHidesMovesAndResets() = runDesktopComposeUiTest(1366, 768) {
        val probe = table()
        onNodeWithTag("table-columns").performClick()
        onNodeWithTag("table-col-series").performClick() // yashirish
        waitForIdle()
        onNodeWithTag("table-th-series").assertDoesNotExist()
        assertEquals(setOf("series"), probe.layout.hidden)

        // «Reaktiv nomi» o‘ngga: endi ko‘rinib turgan qo‘shnisi — «Bo‘lim / saqlash joyi».
        onNodeWithTag("table-col-right-name").performClick()
        waitForIdle()
        assertTrue(left("place") < left("name"))
        assertEquals(listOf("no", "place", "series", "name"), probe.layout.order.take(4))
        val json = TableLayout.fromJson(probe.saved.last())!!
        assertEquals(setOf("series"), json.hidden)
        assertEquals(listOf("no", "place", "series", "name"), json.order.take(4))

        onNodeWithTag("table-col-reset").performClick() // «Standart holatga qaytarish»
        waitForIdle()
        onNodeWithTag("table-th-series").assertExists()
        assertTrue(left("name") < left("place"))
        assertEquals(probe.layout.defaults, probe.layout.layout)
    }

    @Test
    fun rightClickOnAHeaderOpensItsMenuWithoutSorting() = runDesktopComposeUiTest(1366, 768) {
        val probe = table()
        onNodeWithTag("table-th-place").performMouseInput { rightClick() }
        waitForIdle()
        assertNull(probe.layout.sort)
        onNodeWithText("Kamayish bo‘yicha saralash").performClick()
        waitForIdle()
        assertEquals("place", probe.layout.sort?.columnId)
        assertEquals(true, probe.layout.sort?.descending)

        onNodeWithTag("table-th-place").performMouseInput { rightClick() }
        onNodeWithTag("table-hide-place").performClick()
        waitForIdle()
        onNodeWithTag("table-th-place").assertDoesNotExist()
        assertNull("yashirilgan ustun bo‘yicha saralanmaydi", probe.layout.sort)
    }

    @Test
    fun draggingTheBorderResizesAndDoubleClickRestores() = runDesktopComposeUiTest(1366, 768) {
        val probe = table()
        val before = width("name")
        assertEquals(190.dp, before)
        onNodeWithTag("table-resize-name", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(30f, 0f))
            moveBy(Offset(30f, 0f))
            release()
        }
        waitForIdle()
        val after = width("name")
        assertTrue("kenglik: $before → $after", abs((after - before).value - 60f) <= 2f)
        // Qo‘shni ustun ham siljidi (sarlavha va qatorlar bir xil).
        val cell = onNode(hasText("ES-2401") and hasAnyAncestor(hasTestTag("table-row-1"))).getBoundsInRoot()
        assertTrue(abs((cell.left - left("series")).value - 8f) <= 1f)
        val saved = TableLayout.fromJson(probe.saved.last())!!
        assertEquals(after.value, saved.widths.getValue("name"), 1f)

        // Juda tor qilib bo‘lmaydi (eng kichik kenglik).
        onNodeWithTag("table-resize-name", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(-600f, 0f))
            release()
        }
        waitForIdle()
        assertEquals(48.dp, width("name"))

        onNodeWithTag("table-resize-name", useUnmergedTree = true).performMouseInput { doubleClick() }
        waitForIdle()
        assertEquals(190.dp, width("name"))
    }

    @Test
    fun savedLayoutIsAppliedOnOpen() = runDesktopComposeUiTest(1366, 768) {
        val saved = TableLayout(
            order = listOf("name", "no", "series"),
            hidden = setOf("place"),
            widths = mapOf("name" to 260f),
            sort = uz.reaktivombor.desktop.ui.components.TableSort("expires", descending = true),
        ).toJson()
        val probe = table(saved = saved)
        assertTrue(left("name") < left("no"))
        onNodeWithTag("table-th-place").assertDoesNotExist()
        assertEquals(260.dp, width("name"))
        assertEquals("expires", probe.layout.sort?.columnId)
        // Saqlanmagan ustunlar standart joyida.
        assertEquals(listOf("name", "no", "series", "place", "produced"), probe.layout.order.take(5))
        assertTrue(probe.saved.isEmpty())
    }

    @Test
    fun groupsExpandCollapseAndKeepTheirNumbers() = runDesktopComposeUiTest(1366, 768) {
        val probe = table(grouped = true)
        onNodeWithTag("table-row-1").assertExists()
        onNodeWithTag("table-group-Etil spirti 96%").performClick()
        waitForIdle()
        listOf(1L, 2L, 3L).forEach { onNodeWithTag("table-row-$it").assertDoesNotExist() }
        onNodeWithTag("table-group-Etil spirti 96%").assertExists()
        // Yopiq guruhdan keyingi qatorlarning № o‘zgarmaydi.
        onNode(hasText("4") and hasAnyAncestor(hasTestTag("table-row-4"))).assertExists()

        onNodeWithTag("table-group-Etil spirti 96%").performClick()
        waitForIdle()
        onNodeWithTag("table-row-1").assertExists()

        runOnIdle { probe.expansion.collapseAll() }
        waitForIdle()
        assertTrue(visibleOrder().isEmpty())
        onNodeWithTag("table-group-Xloroform").assertExists()
        onNodeWithTag("table-group-Xloroform").performClick() // faqat shu guruh ochiladi
        waitForIdle()
        assertEquals(listOf(4L, 5L), visibleOrder())

        runOnIdle { probe.expansion.expandAll() }
        waitForIdle()
        assertEquals(DEMO_BATCHES.take(visibleOrder().size).map { it.id }, visibleOrder())
    }

    @Test
    fun sortingKeepsGroupsTogether() = runDesktopComposeUiTest(1366, 768) {
        table(grouped = true)
        onNodeWithTag("table-th-expires").performClick()
        waitForIdle()
        val order = visibleOrder()
        val names = order.map { id -> DEMO_BATCHES.single { it.id == id }.name }
        // Bir nomdagilar yonma-yon, guruh ichida muddat bo‘yicha.
        assertEquals(names.distinct().size, names.zipWithNext().count { (a, b) -> a != b } + 1)
        // Eng birinchi — eng erta muddatli partiyaning guruhi (Formalin, 30.09.2026).
        assertEquals("Formalin 10%", names.first())
        val etil = order.filter { id -> DEMO_BATCHES.single { it.id == id }.name.startsWith("Etil") }
        assertEquals(listOf(3L, 2L, 1L), etil)
    }

    @Test
    fun clickSelectsDoubleClickOpensAndKeysMove() = runDesktopComposeUiTest(1366, 768) {
        val probe = table()
        onNodeWithTag("table-row-2").performClick()
        waitForIdle()
        assertEquals(2L, probe.selection.key)
        assertEquals(listOf(2L), probe.clicked)
        assertTrue(probe.opened.isEmpty())

        onNodeWithTag("table-row-3").performMouseInput { doubleClick() }
        waitForIdle()
        assertEquals(listOf(3L), probe.opened)
        assertEquals(3L, probe.selection.key)

        onNodeWithTag("table-body").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(4L, probe.selection.key)
        onNodeWithTag("table-body").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf(3L, 4L), probe.opened)
    }

    @Test
    fun actionButtonsAndOverflowMenu() = runDesktopComposeUiTest(1366, 768) {
        val probe = table()
        // Amallar ustuni o‘ngda — ko‘rinishi uchun «№» dan keyinga suramiz.
        runOnIdle { repeat(13) { probe.layout.moveLeft("actions") } }
        waitForIdle()
        assertEquals(1, probe.layout.order.indexOf("actions"))
        onAllNodesWithContentDescription("Tahrirlash")[0].performClick()
        // 6 ta amal, 4 tasi ko‘rinadi: 3 ta tugma + «⋮».
        onAllNodesWithContentDescription("Arxivlash").assertCountEquals(0)
        onAllNodesWithContentDescription("Boshqa amallar")[1].performClick()
        onNodeWithText("O‘chirish").performClick()
        waitForIdle()
        assertEquals(listOf("edit:1", "delete:2"), probe.actions)
    }

    @Test
    fun emptyTableShowsTheMessage() = runDesktopComposeUiTest(1000, 500) {
        setContent {
            AppTheme {
                val columns = demoColumns()
                DataTable(
                    rows = emptyList(),
                    columns = columns,
                    rowKey = { it.id },
                    layout = rememberTableLayoutState(columns),
                    emptyText = "Hech narsa topilmadi",
                    emptyHint = "Filtrlarni o‘zgartirib ko‘ring.",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        onNodeWithText("Hech narsa topilmadi").assertExists()
        onNodeWithTag("table-th-name").assertExists()
    }
}

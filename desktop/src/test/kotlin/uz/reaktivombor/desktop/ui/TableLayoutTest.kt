package uz.reaktivombor.desktop.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.ui.components.ColumnSpec
import uz.reaktivombor.desktop.ui.components.TableLayout
import uz.reaktivombor.desktop.ui.components.TableLayoutState
import uz.reaktivombor.desktop.ui.components.TableSort
import uz.reaktivombor.desktop.ui.components.compareSortValues
import uz.reaktivombor.desktop.ui.components.naturalCompare

/** Jadval ko‘rinishi (tartib, yashirish, kenglik, saralash) va uning JSON’i — Compose’siz. */
class TableLayoutTest {
    private val specs = listOf(
        ColumnSpec("no", 48.dp, 36.dp, sortable = false, hideable = false, visibleByDefault = true),
        ColumnSpec("name", 200.dp, 80.dp, sortable = true, hideable = true, visibleByDefault = true),
        ColumnSpec("series", 110.dp, 48.dp, sortable = true, hideable = true, visibleByDefault = true),
        ColumnSpec("formula", 120.dp, 48.dp, sortable = true, hideable = true, visibleByDefault = false),
        ColumnSpec("qty", 100.dp, 48.dp, sortable = true, hideable = true, visibleByDefault = true),
    )

    private fun state(saves: MutableList<String> = mutableListOf()) = TableLayoutState(specs, TableSort("name")) { saves += it }

    @Test
    fun defaultsFollowTheColumns() {
        val s = state()
        assertEquals(listOf("no", "name", "series", "formula", "qty"), s.order)
        assertEquals(setOf("formula"), s.hidden)
        assertEquals(listOf("no", "name", "series", "qty"), s.visibleIds)
        assertEquals(TableSort("name"), s.sort)
        assertEquals(200.dp, s.widthOf("name"))
    }

    @Test
    fun jsonRoundTripKeepsEverything() {
        val saves = mutableListOf<String>()
        val s = state(saves)
        s.setVisible("formula", true)
        s.setVisible("series", false)
        s.moveRight("name") // name ↔ formula (series yashirin — ustidan sakraydi)
        s.setWidth("qty", 150.dp)
        s.setSort(TableSort("qty", descending = true))
        assertEquals(5, saves.size)

        val restored = state()
        restored.applyJson(saves.last())
        assertEquals(s.layout, restored.layout)
        assertEquals(listOf("no", "formula", "series", "name", "qty"), restored.order)
        assertEquals(150.dp, restored.widthOf("qty"))
        assertEquals(TableSort("qty", descending = true), restored.sort)
        assertEquals(s.toJson(), TableLayout.fromJson(s.toJson())!!.toJson())
    }

    @Test
    fun unknownColumnsAreDroppedAndNewOnesTakeTheirDefaultPlace() {
        val saved = TableLayout(
            order = listOf("qty", "removed", "name", "no"),
            hidden = setOf("removed", "no"),
            widths = mapOf("removed" to 300f, "name" to 10f),
            sort = TableSort("removed"),
        ).toJson()
        val s = state()
        s.applyJson(saved)
        // series va formula saqlanmagan: standart tartibdagi oldingi qo‘shnisidan (name) keyin.
        assertEquals(listOf("qty", "name", "series", "formula", "no"), s.order)
        // «№» yashirilmaydi; yangi formula ustuni standart bo‘yicha yashirin.
        assertEquals(setOf("formula"), s.hidden)
        assertEquals(80.dp, s.widthOf("name")) // eng kichik kenglikdan tor emas
        assertEquals(TableSort("name"), s.sort) // noma’lum ustun → standart saralash
    }

    @Test
    fun brokenJsonIsIgnored() {
        listOf("", "{", "[]", "null", """{"version":99,"order":["qty"]}""", """{"order":"x"}""").forEach { text ->
            val s = state()
            s.applyJson(text)
            assertEquals(text, s.defaults, s.layout)
        }
        assertNull(TableLayout.fromJson("not json"))
    }

    @Test
    fun theLastVisibleColumnCannotBeHidden() {
        val s = state()
        listOf("name", "series", "qty").forEach { s.setVisible(it, false) }
        assertEquals(listOf("no"), s.visibleIds)
        s.setVisible("no", false) // hideable = false
        assertEquals(listOf("no"), s.visibleIds)
        assertFalse(s.canHide("no"))
    }

    @Test
    fun sortCyclesAscendingDescendingNone() {
        val s = state()
        s.setSort(null)
        s.toggleSort("series")
        assertEquals(TableSort("series", false), s.sort)
        s.toggleSort("series")
        assertEquals(TableSort("series", true), s.sort)
        s.toggleSort("series")
        assertNull(s.sort)
        s.toggleSort("no") // saralanmaydi
        assertNull(s.sort)
    }

    @Test
    fun resetRestoresTheDefaultsAndSaves() {
        val saves = mutableListOf<String>()
        val s = state(saves)
        s.moveLeft("qty")
        s.setWidth("name", 300.dp, persist = false)
        assertEquals(1, saves.size) // sudrash paytida saqlanmaydi
        s.persist()
        s.reset()
        assertEquals(s.defaults, s.layout)
        assertEquals(s.defaults, TableLayout.fromJson(saves.last()))
    }

    @Test
    fun naturalOrderComparesNumbersAsNumbers() {
        val sorted = listOf("naftol-10", "naftol-2", "naftol-1", "etil", "naftol-02b").sortedWith { a, b -> naturalCompare(a, b) }
        assertEquals(listOf("etil", "naftol-1", "naftol-2", "naftol-02b", "naftol-10"), sorted)
        assertTrue(compareSortValues(9L, 10L) < 0)
        assertTrue(compareSortValues(2.5, 10L) < 0)
        assertTrue(compareSortValues(java.time.LocalDate.parse("2026-01-02"), java.time.LocalDate.parse("2025-12-31")) > 0)
    }
}

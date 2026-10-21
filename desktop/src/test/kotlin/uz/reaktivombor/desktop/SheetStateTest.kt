package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SheetStateTest {
    private var clock = 100L
    private fun state(rows: List<SheetRow> = emptyList()) = SheetState(rows) { clock++ }

    @Test
    fun typingIntoTheNewRowCreatesARow() {
        val s = state()
        val id = s.setCell(null, Col.NAME, "Ammiak")
        assertEquals(listOf("Ammiak"), s.rows.map { it.name })
        assertEquals(id, s.rows.single().id)
        assertEquals(null, s.setCell(null, Col.NAME, "   "))
        assertEquals(1, s.rows.size)
    }

    @Test
    fun undoAndRedo() {
        val s = state()
        val id = s.setCell(null, Col.NAME, "Ammiak")
        s.setCell(id, Col.UNIT, "ml")
        assertTrue(s.undo())
        assertEquals("gr", s.rows.single().unit)
        assertTrue(s.undo())
        assertTrue(s.rows.isEmpty())
        assertFalse(s.undo())
        assertTrue(s.redo())
        assertTrue(s.redo())
        assertEquals("ml", s.rows.single().unit)
    }

    @Test
    fun pastingFromExcelFillsAndAddsRows() {
        val s = state(listOf(SheetRow(1, "Eski", createdAt = 1)))
        val block = "Etil spirti\t01.09.2026\t1000\tml\t01.09.2027\t750\r\nXloroform\t10.08.2026\t500\tML\t05.11.2026\t320\r\n"
        val count = s.paste(block, targetIds = listOf(1L), startIndex = 0, startCol = Col.NAME)
        assertEquals(2, count)
        assertEquals(listOf("Etil spirti", "Xloroform"), s.rows.map { it.name })
        assertTrue(s.rows.all { it.check().complete })
        assertEquals("ml", s.rows[1].unit)
        assertTrue(s.undo())
        assertEquals(listOf("Eski"), s.rows.map { it.name })
    }

    @Test
    fun pastingIntoAColumnOnlyChangesThatColumnAndTheOnesToTheRight() {
        val s = state(listOf(SheetRow(1, "A", "01.01.2026", createdAt = 1), SheetRow(2, "B", createdAt = 2)))
        s.paste("02.02.2026\n03.03.2026", targetIds = listOf(1L, 2L), startIndex = 0, startCol = Col.RECEIVED)
        assertEquals(listOf("A", "B"), s.rows.map { it.name })
        assertEquals(listOf("02.02.2026", "03.03.2026"), s.rows.map { it.receivedAt })
    }

    @Test
    fun statusColumnIsReadOnly() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        s.setCell(1, Col.STATUS, "x")
        assertFalse(s.canUndo)
    }

    @Test
    fun excelQuotedMultilineCellStaysInOneRow() {
        val other = SheetRow(2, "Eski 2", "01.01.2026", "5", "gr", "01.01.2028", "5", createdAt = 2)
        val s = state(listOf(SheetRow(1, "Eski 1", createdAt = 1), other))
        val count = s.paste("\"Natriy xlorid\n(osh tuzi)\"\t01.09.2026\t1000\tgr\t01.09.2027\t750\r\n", listOf(1L, 2L), 0, Col.NAME)
        assertEquals(1, count)
        assertEquals("Natriy xlorid (osh tuzi)", s.rows[0].name)
        assertTrue(s.rows[0].check().complete)
        assertEquals(other, s.rows[1])
    }

    @Test
    fun clipboardQuotesFollowExcelRules() {
        assertEquals(listOf(listOf("\"Merck\" brand", "x")), parseClipboard("\"Merck\" brand\tx"))
        assertEquals(listOf(listOf("a \"b\"", ""), listOf("c")), parseClipboard("\"a \"\"b\"\"\"\t\nc"))
        assertEquals(listOf(listOf("a", "")), parseClipboard("a\t"))
    }

    @Test
    fun pasteCountsOnlyRowsThatChanged() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        assertEquals(0, s.paste("ml\nkg\nl", listOf(1L), 1, Col.UNIT))
        assertEquals(0, s.paste("x\ty\nz\tw", listOf(1L), 0, Col.STATUS))
        assertEquals(0, s.paste("A\n", listOf(1L), 0, Col.NAME))
        assertFalse(s.canUndo)
        assertEquals(1, s.rows.size)
    }

    @Test
    fun usDatesCopiedFromExcelAreReadMonthFirst() {
        val s = state()
        s.paste("Xloroform\t8/10/2026\t500\tml\t11/5/2026\t320\nAmmiak\t9/30/2026\t1\tl\t3/1/2027\t1", emptyList(), 0, Col.NAME)
        assertEquals(listOf(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-30")), s.rows.map { DateText.parse(it.receivedAt) })
        assertEquals(listOf(LocalDate.parse("2026-11-05"), LocalDate.parse("2027-03-01")), s.rows.map { DateText.parse(it.expiresAt) })
    }

    @Test
    fun rowOrderSurvivesSaveAndLoad() {
        val s = SheetState(emptyList()) { 1000L } // hamma qator bir millisekundda
        s.paste("A\t01.01.2026\t1\tgr\t01.01.2028\t1\nB\nC\t01.01.2026\t1\tgr\t01.01.2028\t1", emptyList(), 0, Col.NAME)
        s.appendAll(listOf(SheetRow(0, "Telefon", "01.01.2026", "1", "gr", "01.01.2028", "1", createdAt = 1)))
        assertEquals(listOf("A", "B", "C", "Telefon"), s.rows.map { it.name })
        val reloaded = SheetStore.parse(SheetStore.toJson(s.rows, "2026-10-21T09:00:00")).rows
        assertEquals(listOf("A", "B", "C", "Telefon"), reloaded.map { it.name })
        val next = SheetState(reloaded) { 1000L }
        next.setCell(null, Col.NAME, "D")
        assertEquals(listOf("A", "B", "C", "Telefon", "D"), SheetStore.parse(SheetStore.toJson(next.rows, "x")).rows.map { it.name })
    }
}

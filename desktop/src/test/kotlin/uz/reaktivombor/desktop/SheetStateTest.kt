package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

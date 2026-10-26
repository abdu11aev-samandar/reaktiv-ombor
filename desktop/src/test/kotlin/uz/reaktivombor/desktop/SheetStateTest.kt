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
        assertEquals("", s.rows.single().unit) // birlik so‘ralmaguncha bo‘sh
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
        s.paste("02.02.2026\n03.03.2026", targetIds = listOf(1L, 2L), startIndex = 0, startCol = Col.PRODUCED)
        assertEquals(listOf("A", "B"), s.rows.map { it.name })
        assertEquals(listOf("02.02.2026", "03.03.2026"), s.rows.map { it.producedAt })
    }

    @Test
    fun statusColumnIsReadOnly() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        s.setCell(1, Col.REMAINDER, "x")
        assertFalse(s.canUndo)
    }

    @Test
    fun excelQuotedMultilineCellStaysInOneRow() {
        val other = SheetRow(2, "Eski 2", "01.01.2026", "5", "g", "01.01.2028", "5", createdAt = 2)
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
        assertEquals(0, s.paste("x\ty\nz\tw", listOf(1L), 0, Col.REMAINDER))
        assertEquals(0, s.paste("A\n", listOf(1L), 0, Col.NAME))
        assertFalse(s.canUndo)
        assertEquals(1, s.rows.size)
    }

    @Test
    fun usDatesCopiedFromExcelAreReadMonthFirst() {
        val s = state()
        s.paste("Xloroform\t8/10/2026\t500\tml\t11/5/2026\t320\nAmmiak\t9/30/2026\t1\tl\t3/1/2027\t1", emptyList(), 0, Col.NAME, pcMonthFirst = true)
        assertEquals(listOf(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-30")), s.rows.map { DateText.parse(it.producedAt) })
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

    @Test
    fun oneTypoDoesNotFlipADayFirstBlock() {
        val s = state()
        val block = "Xloroform\t01/09/2026\t500\tml\t05/11/2026\t320\nNaftol-2\t02/06/2026\t100\tgr\t5/13/2027\t40"
        s.paste(block, emptyList(), 0, Col.NAME, pcMonthFirst = false)
        assertEquals(LocalDate.parse("2026-09-01"), DateText.parse(s.rows[0].producedAt))
        assertEquals(LocalDate.parse("2026-11-05"), DateText.parse(s.rows[0].expiresAt))
        assertEquals(LocalDate.parse("2026-06-02"), DateText.parse(s.rows[1].producedAt))
    }

    @Test
    fun typingANewDepartmentAddsItToTheListAndUndoTakesBoth() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        s.setCell(1, Col.DEPARTMENT, "  kimyo ")
        assertEquals("Kimyo", s.rows.single().department)
        assertEquals(DEFAULT_DEPARTMENTS + "Kimyo", s.departments)
        assertTrue(s.undo())
        assertEquals("", s.rows.single().department)
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
        assertTrue(s.redo())
        assertEquals(DEFAULT_DEPARTMENTS + "Kimyo", s.departments)
    }

    @Test
    fun aKnownDepartmentIsNotAddedTwice() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        s.setCell(1, Col.DEPARTMENT, "BIOLOGIYA")
        assertEquals("Biologiya", s.rows.single().department)
        s.setCell(1, Col.DEPARTMENT, "Морфология")
        assertEquals("Morfologiya", s.rows.single().department)
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
    }

    @Test
    fun aNumberInTheDepartmentColumnIsNotADepartment() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        s.setCell(1, Col.DEPARTMENT, "250")
        assertEquals("250", s.rows.single().department) // yozilgani yo‘qolmaydi — katakda xato bo‘lib turadi
        assertEquals(DEPARTMENT_NAME_ERROR, s.rows.single().check().errors[Col.DEPARTMENT])
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
    }

    @Test
    fun aNewRowStartedInTheDepartmentColumnAddsTheDepartment() {
        val s = state()
        val id = s.setCell(null, Col.DEPARTMENT, "Kimyo")
        assertEquals("Kimyo", s.rows.single { it.id == id }.department)
        assertTrue("Kimyo" in s.departments)
    }

    @Test
    fun pastingAddsNewDepartmentsOnceAndOneUndoTakesThemBack() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1), SheetRow(2, "B", createdAt = 2), SheetRow(3, "C", createdAt = 3)))
        val count = s.paste("kimyo\t5\nBIOLOGIYA\t1\nKimyo\t2", listOf(1L, 2L, 3L), 0, Col.DEPARTMENT)
        assertEquals(3, count)
        assertEquals(listOf("Kimyo", "Biologiya", "Kimyo"), s.rows.map { it.department })
        assertEquals(DEFAULT_DEPARTMENTS + "Kimyo", s.departments)
        assertTrue(s.undo())
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
        assertEquals(listOf("", "", ""), s.rows.map { it.department })
    }

    @Test
    fun renamingADepartmentRenamesItInTheRowsToo() {
        val s = state(listOf(SheetRow(1, "A", department = "Biologiya", createdAt = 1), SheetRow(2, "B", department = "Gistologiya", createdAt = 2)))
        val change = s.renameDepartment("Biologiya", " mikrobiologiya ")
        assertTrue(change.message, change.ok)
        assertEquals(listOf("Gistologiya", "Mikrobiologiya", "Morfologiya"), s.departments) // o‘rni o‘zgarmaydi
        assertEquals(listOf("Mikrobiologiya", "Gistologiya"), s.rows.map { it.department })
        assertTrue(s.undo())
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
        assertEquals(listOf("Biologiya", "Gistologiya"), s.rows.map { it.department })
    }

    @Test
    fun renamingToAnotherDepartmentMergesThem() {
        val s = state(listOf(SheetRow(1, "A", department = "Biologya", createdAt = 1), SheetRow(2, "B", department = "Biologiya", createdAt = 2)))
        assertEquals(DEFAULT_DEPARTMENTS + "Biologya", s.departments)
        val change = s.renameDepartment("Biologya", "biologiya")
        assertTrue(change.message, change.ok)
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
        assertEquals(listOf("Biologiya", "Biologiya"), s.rows.map { it.department })
        assertFalse(s.renameDepartment("Gistologiya", "  ").ok)
        assertFalse(s.renameDepartment("Gistologiya", "123").ok)
        assertFalse(s.renameDepartment("Yo‘q", "Kimyo").ok)
    }

    @Test
    fun onlyAnUnusedDepartmentCanBeRemoved() {
        val s = state(listOf(SheetRow(1, "A", department = "Biologiya", issuedAmount = "1", createdAt = 1)))
        val refused = s.removeDepartment("Biologiya")
        assertFalse(refused.ok)
        assertTrue(refused.message, refused.message.contains("1 ta qatorda"))
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
        assertTrue(s.removeDepartment("Morfologiya").ok)
        assertEquals(listOf("Gistologiya", "Biologiya"), s.departments)
        assertTrue(s.undo())
        assertEquals(DEFAULT_DEPARTMENTS, s.departments)
    }

    @Test
    fun addingADepartmentChecksTheName() {
        val s = state()
        val revision = s.revision
        assertTrue(s.addDepartment("  klinik  laboratoriya ").ok)
        assertEquals(DEFAULT_DEPARTMENTS + "Klinik laboratoriya", s.departments)
        assertTrue(s.revision > revision) // avtomatik saqlash ro‘yxat o‘zgarishini ham ko‘radi
        assertFalse(s.addDepartment("BIOLOGIYA").ok)
        assertFalse(s.addDepartment("биология").ok)
        assertFalse(s.addDepartment("12.10.2026").ok)
        assertFalse(s.addDepartment("").ok)
        assertEquals(DEFAULT_DEPARTMENTS + "Klinik laboratoriya", s.departments)
    }

    @Test
    fun openingAFileTakesItsDepartmentsAndAppendingMergesThem() {
        val s = state(listOf(SheetRow(1, "A", department = "Kimyo", createdAt = 1)))
        s.appendAll(listOf(SheetRow(0, "B", department = "fizika", createdAt = 2), SheetRow(0, "C", department = "kimyo", createdAt = 3)), listOf("Genetika"))
        assertEquals(DEFAULT_DEPARTMENTS + listOf("Kimyo", "Genetika", "Fizika"), s.departments)
        assertEquals(listOf("Kimyo", "Fizika", "Kimyo"), s.rows.map { it.department })
        s.replaceAll(listOf(SheetRow(0, "D", department = "Botanika", createdAt = 4)), listOf("Zoologiya"))
        assertEquals(listOf("Zoologiya", "Botanika"), s.departments)
        assertTrue(s.undo())
        assertEquals(DEFAULT_DEPARTMENTS + listOf("Kimyo", "Genetika", "Fizika"), s.departments)
    }

    @Test
    fun rowsAreMatchedToTheListWhenTheTableOpens() {
        val s = SheetState(listOf(SheetRow(1, "A", department = "biologiya", createdAt = 1)), listOf("Biologiya"))
        assertEquals("Biologiya", s.rows.single().department)
        assertEquals(listOf("Biologiya"), s.departments)
        assertFalse(s.canUndo)
    }

    @Test
    fun aRenamedOrRemovedDefaultDoesNotComeBackThroughItsRussianName() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        assertTrue(s.renameDepartment("Gistologiya", "Gistologiya bo‘limi").ok)
        s.setCell(1, Col.DEPARTMENT, "гистология")
        assertFalse("Gistologiya" in s.departments)
        assertTrue(s.removeDepartment("Morfologiya").ok)
        assertTrue(s.addDepartment("morphology").ok)
        assertEquals("Morphology", s.departments.last())
    }

    @Test
    fun appendedRowsWithTwoSpellingsBecomeOneDepartment() {
        val s = state(listOf(SheetRow(1, "A", createdAt = 1)))
        s.appendAll(listOf(SheetRow(0, "B", department = "O'simliklar", createdAt = 2), SheetRow(0, "C", department = "o‘simliklar", createdAt = 3)))
        assertEquals(listOf("", "O'simliklar", "O'simliklar"), s.rows.map { it.department })
        assertEquals(DEFAULT_DEPARTMENTS + "O'simliklar", s.departments)
        s.replaceAll(listOf(SheetRow(0, "D", department = "KIMYO", createdAt = 4), SheetRow(0, "E", department = "Kimyo", createdAt = 5)))
        assertEquals(listOf("KIMYO", "KIMYO"), s.rows.map { it.department })
    }

    @Test
    fun changedSpotPointsAtTheUndoneCell() {
        val a = SheetRow(1, "A", "01.01.2026", createdAt = 1)
        val b = SheetRow(2, "B", createdAt = 2)
        assertEquals(2L to Col.STOCK, changedSpot(listOf(a, b), listOf(a, b.copy(stockAmount = "5"))))
        assertEquals(2L to null, changedSpot(listOf(a), listOf(a, b))) // qaytgan (o‘chirilgan) qator
        assertEquals(2L to null, changedSpot(listOf(a, b), listOf(a))) // bekor qilingan yangi qator
        assertEquals(1L to null, changedSpot(listOf(a), listOf(a.copy(name = "X", producedAt = ""))))
        assertEquals(null, changedSpot(listOf(a), listOf(a)))
    }
}

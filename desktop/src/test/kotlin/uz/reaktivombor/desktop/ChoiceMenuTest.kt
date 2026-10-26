package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ochiladigan ro‘yxat: tepadagi band doim Enter yozadigan qiymat. */
class ChoiceMenuTest {
    private val list = DEFAULT_DEPARTMENTS + listOf("Klinik laboratoriya", "Biology club", "Mikrobiologiya")

    /** Katakda Enter yozadigan qiymat (App.commitEdit). */
    private fun enter(typed: String) = matchDepartment(typed, list, prefix = true)

    @Test
    fun theTopItemIsWhatEnterWrites() {
        listOf("b", "bio", "biology", "биология", "m", "mi", "Mor", "klinik  lab", "klinik\u00A0lab", "kimyo", "  gist ", "o'quv").forEach { typed ->
            val menu = choiceMenu(Col.DEPARTMENT, typed, current = "", departments = list)
            val top = if (menu.newFirst) menu.newDepartment else menu.items.first()
            assertEquals(typed, enter(typed), top)
        }
    }

    @Test
    fun aNameThatStartsLikeAnExistingOneCanStillBeAdded() {
        val menu = choiceMenu(Col.DEPARTMENT, "klinik  lab", current = "", departments = list)
        assertEquals("Klinik laboratoriya", menu.items.first())
        assertEquals("Klinik lab", menu.newDepartment) // pastda: aynan shu nom bilan yangi bo‘lim
        assertFalse(menu.newFirst)
        val alias = choiceMenu(Col.DEPARTMENT, "biology", current = "", departments = list)
        assertEquals(listOf("Biologiya", "Biology club"), alias.items)
        assertNull(alias.newDepartment)
    }

    @Test
    fun anUnknownNameIsOfferedAsANewDepartment() {
        val menu = choiceMenu(Col.DEPARTMENT, "kimyo", current = "", departments = list)
        assertTrue(menu.items.isEmpty())
        assertEquals("Kimyo", menu.newDepartment)
        assertTrue(menu.newFirst)
        // Son bo‘lim bo‘lmaydi — butun ro‘yxat tanlash uchun.
        val number = choiceMenu(Col.DEPARTMENT, "250", current = "", departments = list)
        assertEquals(list, number.items)
        assertNull(number.newDepartment)
    }

    @Test
    fun theWholeListShowsWhenNothingIsTypedOrTheValueIsUnchanged() {
        assertEquals(list, choiceMenu(Col.DEPARTMENT, "", current = "Biologiya", departments = list).items)
        assertEquals(list, choiceMenu(Col.DEPARTMENT, "biologiya", current = "Biologiya", departments = list).items)
        assertEquals(UNITS, choiceMenu(Col.UNIT, "ml", current = "ml", departments = list).items)
    }

    @Test
    fun unitsFilterByTheirStart() {
        assertEquals(listOf("ml"), choiceMenu(Col.UNIT, "m", current = "", departments = list).items)
        assertEquals(UNITS, choiceMenu(Col.UNIT, "x", current = "", departments = list).items)
        assertNull(choiceMenu(Col.UNIT, "x", current = "", departments = list).newDepartment)
    }
}

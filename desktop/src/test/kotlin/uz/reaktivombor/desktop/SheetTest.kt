package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.domain.ExpiryStatus
import uz.reaktivombor.domain.Field
import java.time.LocalDate

class SheetTest {
    private val today = LocalDate.parse("2026-10-21")

    @Test
    fun parsesTypicalDateSpellings() {
        val expected = LocalDate.parse("2026-10-17")
        listOf("17.10.2026", "17/10/2026", "17-10-2026", "17.10.26", "2026-10-17", " 17.10.2026 ").forEach {
            assertEquals(it, expected, DateText.parse(it))
        }
        assertEquals(LocalDate.parse("2027-01-07"), DateText.parse("7.1.2027"))
        assertNull(DateText.parse("31.02.2026"))
        assertNull(DateText.parse("ertaga"))
        assertEquals("07.01.2027", DateText.format(LocalDate.parse("2027-01-07")))
    }

    @Test
    fun completeRowGetsColour() {
        val row = SheetRow(1, "Xloroform", "10.08.2026", "500", "ml", "05.11.2026", "320", 1)
        val check = row.check()
        assertTrue(check.complete)
        assertEquals(ExpiryStatus.RED, check.status(today))
    }

    @Test
    fun draftRowListsMissingAndWrongFields() {
        val check = SheetRow(1, name = "Ammiak", receivedAt = "bugun", givenAmount = "ko‘p", createdAt = 1).check()
        assertEquals(null, check.values)
        assertEquals("Kelgan vaqt to‘g‘ri sana bo‘lishi kerak (masalan: 17.10.2026).", check.errors[Field.RECEIVED_AT])
        assertEquals("Berilgan miqdor son bo‘lishi kerak.", check.errors[Field.GIVEN_AMOUNT])
        assertEquals("Yaroqlilik muddati kiritilishi shart.", check.errors[Field.EXPIRES_AT])
        assertEquals("Ombordagi miqdor kiritilishi shart.", check.errors[Field.STOCK_AMOUNT])
        assertNull(check.errors[Field.NAME])
    }

    @Test
    fun expiryBeforeArrivalIsAnError() {
        val check = SheetRow(1, "Jelatin", "10.10.2026", "1", "gr", "01.10.2026", "1", 1).check()
        assertEquals("Yaroqlilik muddati kelgan vaqtdan oldin bo‘lishi mumkin emas.", check.errors[Field.EXPIRES_AT])
    }

    @Test
    fun unitsAreNormalised() {
        assertEquals("ml", normalizeUnit(" ML "))
        assertEquals("gr", normalizeUnit("г"))
        assertEquals("dona", normalizeUnit("ta"))
        assertEquals("tonna", normalizeUnit("tonna"))
        assertEquals("ml", SheetRow(1, createdAt = 1).with(Col.UNIT, "Мл").unit)
    }

    @Test
    fun amountsDisplayLikeThePhone() {
        assertEquals("1 250,5", displayAmount("1250,5"))
        assertEquals("ko‘p", displayAmount("ko‘p"))
        assertEquals("17.10.2026", displayDate("2026-10-17"))
    }

    @Test
    fun slashDatesThatCanOnlyBeMonthFirstAreAccepted() {
        assertEquals(LocalDate.parse("2026-10-25"), DateText.parse("10/25/2026"))
        assertNull(DateText.parse("10.25.2026"))
        // Ikki xil o‘qilishi mumkin — odatdagidek kun/oy.
        assertEquals(LocalDate.parse("2026-10-03"), DateText.parse("3/10/2026"))
    }

    @Test
    fun monthFirstIsDetectedFromTheWholeBlock() {
        assertTrue(DateText.isMonthFirst(listOf("10/25/2026", "3/10/2027", "")))
        assertFalse(DateText.isMonthFirst(listOf("25/10/2026", "3/10/2027")))
        assertFalse(DateText.isMonthFirst(listOf("3/10/2027")))
        assertFalse(DateText.isMonthFirst(listOf("10/25/2026", "25/10/2026")))
        assertFalse(DateText.isMonthFirst(listOf("10.25.2026")))
        assertEquals("10.3.2027", DateText.fromMonthFirst("3/10/2027"))
        assertEquals("17.10.2026", DateText.fromMonthFirst("17.10.2026"))
    }
}

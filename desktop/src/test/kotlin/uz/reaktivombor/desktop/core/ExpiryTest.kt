package uz.reaktivombor.desktop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpiryTest {
    private val today = LocalDate.of(2026, 10, 29)

    private fun state(days: Long) = expiryState(today.plusDays(days), today)

    @Test
    fun colourThresholdsAreExactDays() {
        assertEquals(ExpiryState.GREEN, state(91))
        assertEquals(ExpiryState.YELLOW, state(90))
        assertEquals(ExpiryState.YELLOW, state(31))
        assertEquals(ExpiryState.RED, state(30))
        assertEquals(ExpiryState.RED, state(0))
        assertEquals(ExpiryState.EXPIRED, state(-1))
        assertEquals(ExpiryState.GREEN, state(400))
        assertEquals(ExpiryState.EXPIRED, state(-400))
    }

    @Test
    fun expiredDetectionAndDaysLeft() {
        assertEquals(-1L, daysLeft(today.minusDays(1), today))
        assertEquals(0L, daysLeft(today, today))
        assertEquals(91L, daysLeft(today.plusDays(91), today))
        // Yil chegarasi va kabisa yili ham aniq kun bilan.
        assertEquals(366L, daysLeft(LocalDate.of(2028, 3, 1), LocalDate.of(2027, 3, 1)))
        assertEquals(ExpiryState.EXPIRED, expiryState(LocalDate.of(2026, 10, 28), today))
        assertEquals(ExpiryState.RED, expiryState(LocalDate.of(2026, 10, 29), today))
        assertNull(daysLeft(null, today))
        assertEquals(ExpiryState.UNKNOWN, expiryState(null, today))
    }

    @Test
    fun statusTextsAreUzbek() {
        assertEquals("Yaroqli", ExpiryState.GREEN.label)
        assertEquals("1–3 oy qoldi", ExpiryState.YELLOW.label)
        assertEquals("1 oy yoki kam qoldi", ExpiryState.RED.label)
        assertEquals("Muddati o‘tgan", ExpiryState.EXPIRED.label)
        assertEquals("Muddati kiritilmagan", ExpiryState.UNKNOWN.label)
        assertEquals("Bugun tugaydi", daysLeftLabel(0))
        assertEquals("12 kun o‘tgan", daysLeftLabel(-12))
        assertEquals("45", daysLeftLabel(45))
    }
}

package uz.reaktivombor.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Laravel’dagi tests/Unit/ExpiryStatusTest.php bilan bir xil chegaralar. */
class ExpiryStatusTest {
    private fun status(today: String, expires: String) = ExpiryStatus.of(LocalDate.parse(expires), LocalDate.parse(today))

    @Test
    fun boundaries() {
        val cases = listOf(
            Triple("2026-10-16", "2026-10-15", ExpiryStatus.RED), // muddati o‘tgan
            Triple("2026-10-16", "2026-10-16", ExpiryStatus.RED), // bugun tugaydi
            Triple("2026-10-16", "2026-11-15", ExpiryStatus.RED), // 1 oydan 1 kun kam
            Triple("2026-10-16", "2026-11-16", ExpiryStatus.YELLOW), // roppa-rosa 1 oy
            Triple("2026-10-16", "2026-12-16", ExpiryStatus.YELLOW),
            Triple("2026-10-16", "2027-01-15", ExpiryStatus.YELLOW),
            Triple("2026-10-16", "2027-01-16", ExpiryStatus.YELLOW), // roppa-rosa 3 oy
            Triple("2026-10-16", "2027-01-17", ExpiryStatus.GREEN), // 3 oydan 1 kun ko‘p
            Triple("2026-10-16", "2027-10-16", ExpiryStatus.GREEN),
            // Oy oxiri: 30-noyabr + 3 oy = 28-fevral.
            Triple("2026-11-30", "2027-02-28", ExpiryStatus.YELLOW),
            Triple("2026-11-30", "2027-03-01", ExpiryStatus.GREEN),
            Triple("2027-01-31", "2027-02-27", ExpiryStatus.RED),
            Triple("2027-01-31", "2027-02-28", ExpiryStatus.YELLOW),
        )
        cases.forEach { (today, expires, expected) ->
            assertEquals("$today → $expires", expected, status(today, expires))
        }
    }

    @Test
    fun alertStages() {
        val today = LocalDate.parse("2026-10-16")
        assertEquals(AlertStage.EXPIRED, AlertStage.of(LocalDate.parse("2026-10-15"), today))
        assertEquals(AlertStage.RED, AlertStage.of(today, today))
        assertEquals(AlertStage.YELLOW, AlertStage.of(LocalDate.parse("2026-12-01"), today))
        assertEquals(AlertStage.NONE, AlertStage.of(LocalDate.parse("2027-06-01"), today))
    }

    @Test
    fun hints() {
        val today = LocalDate.parse("2026-10-16")
        assertEquals("20 kun qoldi", expiryHint(daysUntil(LocalDate.parse("2026-11-05"), today)))
        assertEquals("Bugun tugaydi", expiryHint(daysUntil(today, today)))
        assertEquals("Muddati 6 kun oldin o‘tgan", expiryHint(daysUntil(LocalDate.parse("2026-10-10"), today)))
    }
}

package uz.reaktivombor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import uz.reaktivombor.data.Reagent
import java.math.BigDecimal
import java.time.LocalDate

class BackupTest {
    private val today = LocalDate.parse("2026-10-16")

    private val reagents = listOf(
        Reagent(1, "Etil spirti", LocalDate.parse("2026-09-01"), BigDecimal("1000.000"), "ml", LocalDate.parse("2027-09-01"), BigDecimal("750.000"), 100),
        Reagent(2, "Xloroform", LocalDate.parse("2026-08-10"), BigDecimal("500"), "ml", LocalDate.parse("2026-11-05"), BigDecimal("320.5"), 200),
    )

    @Test
    fun roundTrip() {
        val parsed = Backup.fromJson(Backup.toJson(reagents, "2026-10-16T10:00:00"), today)
        assertEquals(0, parsed.skipped)
        assertEquals(listOf("Etil spirti", "Xloroform"), parsed.reagents.map { it.name })
        assertEquals(BigDecimal("320.500"), parsed.reagents[1].stockAmount)
        assertEquals(LocalDate.parse("2026-11-05"), parsed.reagents[1].expiresAt)
        assertEquals(200L, parsed.reagents[1].createdAt)
        // Tiklangan qatorlar uchun eski eslatma qayta kelmaydi.
        assertEquals(AlertStage.RED.ordinal, parsed.reagents[1].notifiedStage)
    }

    @Test
    fun skipsBrokenRows() {
        val json = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"Ammiak","receivedAt":"2026-09-30","givenAmount":"1000","unit":"ml","expiresAt":"2027-03-30","stockAmount":"1000"},
              {"name":"","receivedAt":"2026-09-30","givenAmount":"1","unit":"ml","expiresAt":"2027-03-30","stockAmount":"1"},
              {"name":"Jelatin","receivedAt":"bugun","givenAmount":"1","unit":"gr","expiresAt":"2027-03-30","stockAmount":"1"},
              "matn"
            ]}
        """.trimIndent()
        val parsed = Backup.fromJson(json, today)
        assertEquals(listOf("Ammiak"), parsed.reagents.map { it.name })
        assertEquals(3, parsed.skipped)
    }

    @Test
    fun rejectsForeignFiles() {
        assertThrows(BackupException::class.java) { Backup.fromJson("salom", today) }
        assertThrows(BackupException::class.java) { Backup.fromJson("""{"format":"boshqa"}""", today) }
        assertThrows(BackupException::class.java) { Backup.fromJson("""{"format":"reaktiv-ombor","version":99,"reagents":[]}""", today) }
    }
}

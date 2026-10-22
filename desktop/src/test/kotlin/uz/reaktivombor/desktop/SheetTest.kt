package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.domain.ExpiryStatus
import java.math.BigDecimal
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

    private val xloroform = SheetRow(
        1, "Xloroform", producedAt = "10.08.2026", receivedAmount = "500", unit = "ml",
        expiresAt = "05.11.2026", stockAmount = "320", createdAt = 1,
    )

    @Test
    fun completeRowGetsColourAndComputedColumns() {
        val check = xloroform.copy(department = "Biologiya", issuedAmount = "20").check()
        assertTrue(check.errors.toString(), check.complete)
        assertEquals(ExpiryStatus.RED, check.status(today))
        assertEquals(BigDecimal("300.000"), check.remainder)
        assertEquals(15L, check.daysLeft(today))
        assertEquals("300", cellText(xloroform.copy(department = "Biologiya", issuedAmount = "20"), Col.REMAINDER, check, today))
        assertEquals("15", cellText(xloroform, Col.DAYS_LEFT, check, today))
    }

    @Test
    fun issuingIsOptionalButDepartmentAndAmountGoTogether() {
        val plain = xloroform.check()
        assertTrue(plain.complete)
        assertEquals(BigDecimal("320.000"), plain.remainder) // hech narsa berilmagan — qoldiq = ombordagi
        assertEquals("Bo‘limga berilgan miqdorni kiriting.", xloroform.copy(department = "Gistologiya").check().errors[Col.ISSUED])
        assertEquals("Qaysi bo‘limga berilganini tanlang.", xloroform.copy(issuedAmount = "5").check().errors[Col.DEPARTMENT])
        val tooMuch = xloroform.copy(department = "Morfologiya", issuedAmount = "321").check()
        assertEquals("Berilgan miqdor ombordagi miqdordan ko‘p bo‘lishi mumkin emas.", tooMuch.errors[Col.ISSUED])
        assertNull(tooMuch.remainder)
        assertEquals("Bo‘lim: Gistologiya, Biologiya yoki Morfologiya.", xloroform.copy(department = "Kimyo", issuedAmount = "1").check().errors[Col.DEPARTMENT])
    }

    @Test
    fun draftRowListsMissingAndWrongFields() {
        val check = SheetRow(1, name = "Ammiak", producedAt = "bugun", receivedAmount = "ko‘p", createdAt = 1).check()
        assertFalse(check.complete)
        assertEquals("Ishlab chiqarilgan vaqti to‘g‘ri sana bo‘lishi kerak (masalan: 17.10.2026).", check.errors[Col.PRODUCED])
        assertEquals("Kelgan miqdor son bo‘lishi kerak.", check.errors[Col.RECEIVED_AMOUNT])
        assertEquals("Yaroqlilik muddati kiritilishi shart.", check.errors[Col.EXPIRES])
        assertEquals("Ombordagi miqdor kiritilishi shart.", check.errors[Col.STOCK])
        assertNull(check.errors[Col.NAME])
        assertNull(check.errors[Col.DEPARTMENT])
        assertNull(check.errors[Col.ISSUED])
    }

    @Test
    fun expiryBeforeProductionIsAnError() {
        val check = SheetRow(1, "Jelatin", "10.10.2026", "1", "g", "01.10.2026", "1", createdAt = 1).check()
        assertEquals("Yaroqlilik muddati ishlab chiqarilgan vaqtdan oldin bo‘lishi mumkin emas.", check.errors[Col.EXPIRES])
    }

    @Test
    fun colourComesFromTheExpiryDateEvenForAnUnfinishedRow() {
        val check = SheetRow(1, "Fenol", expiresAt = "01.11.2026", createdAt = 1).check()
        assertFalse(check.complete)
        assertEquals(ExpiryStatus.RED, check.status(today))
        assertEquals(11L, check.daysLeft(today))
    }

    @Test
    fun theOldArrivalDateIsShownWhereTheProductionDateIsMissing() {
        val check = xloroform.copy(producedAt = "", legacyReceivedAt = "2026-08-10").check()
        assertEquals("Ishlab chiqarilgan vaqtini kiriting (eski jadvaldagi «Kelgan vaqt»: 10.08.2026).", check.errors[Col.PRODUCED])
    }

    @Test
    fun unitsAndDepartmentsAreNormalised() {
        assertEquals("ml", normalizeUnit(" ML "))
        assertEquals("g", normalizeUnit("gr"))
        assertEquals("g", normalizeUnit("г"))
        assertEquals("dona", normalizeUnit("ta"))
        assertEquals("tonna", normalizeUnit("tonna"))
        assertEquals("ml", SheetRow(1, createdAt = 1).with(Col.UNIT, "Мл").unit)
        assertEquals("Biologiya", normalizeDepartment("b"))
        assertEquals("Gistologiya", normalizeDepartment(" GIST "))
        assertEquals("Morfologiya", normalizeDepartment("Морфология"))
        assertEquals("Kimyo", normalizeDepartment("Kimyo"))
        assertEquals("Gistologiya", SheetRow(1, createdAt = 1).with(Col.DEPARTMENT, "g").department)
    }

    @Test
    fun daysLeftText() {
        assertEquals("39", daysLeftText(39))
        assertEquals("0 (bugun)", daysLeftText(0))
        assertEquals("-12 (o‘tgan)", daysLeftText(-12))
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
    fun monthFirstFollowsTheComputersRegionalFormat() {
        // AQSh formatidagi kompyuter: Excel oy/kun beradi — kun/oy ekani aniq bo‘lmasa, oy/kun.
        assertTrue(DateText.isMonthFirst(listOf("10/25/2026", "3/10/2027", ""), pcMonthFirst = true))
        assertTrue(DateText.isMonthFirst(listOf("3/10/2027"), pcMonthFirst = true))
        assertFalse(DateText.isMonthFirst(listOf("25/10/2026", "3/10/2027"), pcMonthFirst = true))
        assertFalse(DateText.isMonthFirst(listOf("10.25.2026"), pcMonthFirst = true))
        // Boshqa kompyuter: hech qachon butun blok aylantirilmaydi (bitta xato qiymat qolganlarini buzmasin).
        assertFalse(DateText.isMonthFirst(listOf("05/11/2026", "5/13/2027"), pcMonthFirst = false))
        assertEquals("10.3.2027", DateText.fromMonthFirst("3/10/2027"))
        assertEquals("17.10.2026", DateText.fromMonthFirst("17.10.2026"))
    }

    @Test
    fun ambiguousSlashDates() {
        assertTrue(DateText.isAmbiguous("11/12/2026"))
        assertTrue(DateText.isAmbiguous("3/4/26"))
        assertFalse(DateText.isAmbiguous("10/25/2026"))
        assertFalse(DateText.isAmbiguous("5/5/2026")) // ikki xil o‘qilishi bir xil
        assertFalse(DateText.isAmbiguous("11.12.2026"))
    }
}

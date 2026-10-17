package uz.reaktivombor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AmountsTest {
    private val nbsp = ' '

    @Test
    fun parsesCommaAndSpaces() {
        assertEquals(BigDecimal("2.500"), Amounts.parse("2,5"))
        assertEquals(BigDecimal("1200.750"), Amounts.parse(" 1 200,75 "))
        assertEquals(BigDecimal("0.500"), Amounts.parse(".5"))
        assertEquals(BigDecimal("2.556"), Amounts.parse("2,5555"))
        assertNull(Amounts.parse("ko‘p"))
        assertNull(Amounts.parse("1,5,6"))
        assertNull(Amounts.parse(""))
    }

    @Test
    fun formatsLikeTheWebApp() {
        assertEquals("1${nbsp}250,5${nbsp}gr", Amounts.format(BigDecimal("1250.500"), "gr"))
        assertEquals("800${nbsp}ml", Amounts.format(BigDecimal("800.000"), "ml"))
        assertEquals("0${nbsp}gr", Amounts.format(BigDecimal("0.000"), "gr"))
        assertEquals("999${nbsp}999${nbsp}999${nbsp}kg", Amounts.format(BigDecimal("999999999"), "kg"))
    }

    @Test
    fun formatsForInput() {
        assertEquals("500", Amounts.forInput(BigDecimal("500.000")))
        assertEquals("2,5", Amounts.forInput(BigDecimal("2.500")))
        assertEquals("0", Amounts.forInput(BigDecimal("0.000")))
    }
}

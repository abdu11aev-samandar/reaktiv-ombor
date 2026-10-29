package uz.reaktivombor.desktop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class UnitsTest {
    @Test
    fun quantitiesAreStoredAsExactBaseUnits() {
        assertEquals(1_000_000L, Qty.parse("1", MeasureUnit.L))
        assertEquals(200_000L, Qty.parse("200", MeasureUnit.ML))
        assertEquals(1_250_500L, Qty.parse("1 250,5", MeasureUnit.ML))
        assertEquals(1_250_500L, Qty.parse("1250.5", MeasureUnit.ML))
        assertEquals(1_250_500L, Qty.parse("1 250,5", MeasureUnit.ML)) // bo‘linmaydigan bo‘shliq
        assertEquals(1_250_500L, Qty.parse("1 250,5", MeasureUnit.ML)) // ingichka bo‘shliq
        assertEquals(15_000L, Qty.parse("0,015", MeasureUnit.KG)) // 15 g
        assertEquals(500L, Qty.parse(",5", MeasureUnit.G))
        assertEquals(3_000L, Qty.parse("3", MeasureUnit.PIECE))
        assertEquals(999_999_999L * 1_000_000L, Qty.parse("999 999 999", MeasureUnit.L))
        assertEquals(0L, Qty.parse("0", MeasureUnit.ML, allowZero = true))
        // Oxiridagi nollar kasr raqami hisoblanmaydi.
        assertEquals(1_250_000L, Qty.parse("1,25000", MeasureUnit.L))
    }

    @Test
    fun invalidQuantitiesGiveUzbekMessages() {
        fun error(text: String?, unit: MeasureUnit = MeasureUnit.ML) = assertThrows(UserError::class.java) { Qty.parse(text, unit) }.message
        assertEquals("Miqdor kiritilishi shart.", error(""))
        assertEquals("Miqdor kiritilishi shart.", error("   "))
        assertEquals("Miqdor kiritilishi shart.", error(null))
        assertTrue(error("abc").contains("son bo‘lishi kerak"))
        assertTrue(error("1e3").contains("son bo‘lishi kerak")) // daraja ko‘rinishi
        assertTrue(error("1,250.5").contains("son bo‘lishi kerak"))
        assertEquals("Miqdor manfiy bo‘lishi mumkin emas.", error("-5"))
        assertEquals("Miqdor noldan katta bo‘lishi kerak.", error("0"))
        assertEquals("Miqdor noldan katta bo‘lishi kerak.", error("0,000"))
        assertTrue(error("1,2345").contains("ko‘pi bilan 3 ta raqam"))
        assertTrue(error("1000000000").contains("juda katta"))
        assertTrue(error("99999999999999999999999").contains("juda katta"))
        val withField = assertThrows(UserError::class.java) { Qty.parse("x", MeasureUnit.L, "Kelgan miqdor", field = "qty") }
        assertEquals(mapOf("qty" to withField.message), withField.fields)
        assertTrue(withField.message.startsWith("Kelgan miqdor"))
    }

    @Test
    fun formatGroupsThousandsAndUsesDecimalComma() {
        assertEquals("1 250,5", Qty.format(1_250_500, MeasureUnit.ML))
        assertEquals("1 250,5 ml", Qty.formatWithUnit(1_250_500, MeasureUnit.ML))
        assertEquals("0,75", Qty.format(750_000, MeasureUnit.L))
        assertEquals("14", Qty.format(14_000_000, MeasureUnit.L))
        assertEquals("1 000 000", Qty.format(1_000_000_000, MeasureUnit.ML))
        assertEquals("0", Qty.format(0, MeasureUnit.KG))
        assertEquals("-0,3", Qty.format(-300_000, MeasureUnit.L))
        // Aniq: yaxlitlanmaydi.
        assertEquals("0,000001", Qty.format(1, MeasureUnit.L))
        assertEquals("750 ml", Qty.formatAuto(750_000, Dimension.VOLUME))
        assertEquals("5,5 l", Qty.formatAuto(5_500_000, Dimension.VOLUME))
        assertEquals(BigDecimal("1250.5"), Qty.toDecimal(1_250_500, MeasureUnit.ML))
        assertEquals(1_250_500L, Qty.fromDecimal(BigDecimal("1.2505"), MeasureUnit.L))
    }

    @Test
    fun conversionOnlyInsideOneDimension() {
        // 200 ml litrli partiyadan: asosiy birlik umumiy.
        val litre = Qty.parse("1", MeasureUnit.L)
        val portion = Qty.parse("200", MeasureUnit.ML)
        assertEquals("0,8 l", Qty.formatWithUnit(litre - portion, MeasureUnit.L))
        assertEquals("800 ml", Qty.formatWithUnit(litre - portion, MeasureUnit.ML))
        assertEquals(listOf(MeasureUnit.L, MeasureUnit.ML), MeasureUnit.L.siblings)
        assertEquals(listOf(MeasureUnit.KG, MeasureUnit.G), MeasureUnit.G.siblings)
        assertEquals(listOf(MeasureUnit.PIECE), MeasureUnit.PIECE.siblings)
        // Guruh birligi — eng kattasi: 5 l + 500 ml = 5,5 l.
        assertEquals(MeasureUnit.L, MeasureUnit.largest(listOf(MeasureUnit.ML, MeasureUnit.L)))
        assertEquals("5,5", Qty.format(Qty.parse("5", MeasureUnit.L) + Qty.parse("500", MeasureUnit.ML), MeasureUnit.L))
        // Turli o‘lchov turlari bir-biriga aylantirilmaydi.
        assertTrue(MeasureUnit.L.dimension != MeasureUnit.KG.dimension)
        // Jamilar o‘lchov turi bo‘yicha alohida (hajm, massa, dona tartibida), hech qachon qo‘shilmaydi.
        assertEquals("2 l · 3 g", Qty.formatTotals(mapOf(Dimension.MASS to 3_000L, Dimension.VOLUME to 2_000_000L)))
    }

    @Test
    fun unitAliasesAreRecognised() {
        assertEquals(MeasureUnit.L, MeasureUnit.of("litr"))
        assertEquals(MeasureUnit.L, MeasureUnit.of(" L "))
        assertEquals(MeasureUnit.ML, MeasureUnit.of("мл"))
        assertEquals(MeasureUnit.G, MeasureUnit.of("gr"))
        assertEquals(MeasureUnit.G, MeasureUnit.of("гр"))
        assertEquals(MeasureUnit.KG, MeasureUnit.of("kg"))
        assertEquals(MeasureUnit.PIECE, MeasureUnit.of("шт"))
        assertEquals(MeasureUnit.PIECE, MeasureUnit.of("ta"))
        assertEquals(MeasureUnit.PIECE, MeasureUnit.of("dona"))
        assertNull(MeasureUnit.of("xyz"))
        assertNull(MeasureUnit.of(""))
    }
}

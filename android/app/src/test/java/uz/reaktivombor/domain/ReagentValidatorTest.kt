package uz.reaktivombor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReagentValidatorTest {
    private val valid = ReagentInput(
        name = "  Etil spirti ",
        receivedAt = LocalDate.parse("2026-09-01"),
        expiresAt = LocalDate.parse("2027-09-01"),
        givenAmount = "1000",
        stockAmount = "750,5",
        unit = "ml",
    )

    @Test
    fun acceptsValidInput() {
        val result = ReagentValidator.validate(valid)
        assertTrue(result is Validation.Valid)
        val values = (result as Validation.Valid).values
        assertEquals("Etil spirti", values.name)
        assertEquals(BigDecimal("750.500"), values.stockAmount)
    }

    @Test
    fun allFieldsAreRequired() {
        val result = ReagentValidator.validate(ReagentInput("", null, null, "", " ", "gr")) as Validation.Invalid
        assertEquals(
            mapOf(
                Field.NAME to "Reaktiv nomi kiritilishi shart.",
                Field.RECEIVED_AT to "Kelgan vaqt kiritilishi shart.",
                Field.EXPIRES_AT to "Yaroqlilik muddati kiritilishi shart.",
                Field.GIVEN_AMOUNT to "Berilgan miqdor kiritilishi shart.",
                Field.STOCK_AMOUNT to "Ombordagi miqdor kiritilishi shart.",
            ),
            result.errors,
        )
    }

    @Test
    fun messagesMatchTheWebApp() {
        val result = ReagentValidator.validate(
            valid.copy(
                givenAmount = "ko‘p",
                stockAmount = "-5",
                unit = "tonna",
                receivedAt = LocalDate.parse("2026-10-10"),
                expiresAt = LocalDate.parse("2026-10-01"),
            ),
        ) as Validation.Invalid
        assertEquals("Berilgan miqdor son bo‘lishi kerak.", result.errors[Field.GIVEN_AMOUNT])
        assertEquals("Ombordagi miqdor manfiy bo‘lishi mumkin emas.", result.errors[Field.STOCK_AMOUNT])
        assertEquals("O‘lchov birligi ro‘yxatdan tanlanishi kerak.", result.errors[Field.UNIT])
        assertEquals("Yaroqlilik muddati kelgan vaqtdan oldin bo‘lishi mumkin emas.", result.errors[Field.EXPIRES_AT])
    }

    @Test
    fun rejectsTooLargeAmountsAndLongNames() {
        val result = ReagentValidator.validate(valid.copy(name = "x".repeat(256), givenAmount = "1000000000")) as Validation.Invalid
        assertEquals("Reaktiv nomi 255 belgidan oshmasligi kerak.", result.errors[Field.NAME])
        assertEquals("Berilgan miqdor juda katta.", result.errors[Field.GIVEN_AMOUNT])
    }
}

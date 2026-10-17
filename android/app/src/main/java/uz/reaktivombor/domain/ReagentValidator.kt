package uz.reaktivombor.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Formaga kiritilgan xom qiymatlar. */
data class ReagentInput(
    val name: String,
    val receivedAt: LocalDate?,
    val expiresAt: LocalDate?,
    val givenAmount: String,
    val stockAmount: String,
    val unit: String,
)

/** Tekshiruvdan o‘tgan qiymatlar. */
data class ReagentValues(
    val name: String,
    val receivedAt: LocalDate,
    val expiresAt: LocalDate,
    val givenAmount: BigDecimal,
    val stockAmount: BigDecimal,
    val unit: String,
)

enum class Field { NAME, RECEIVED_AT, EXPIRES_AT, GIVEN_AMOUNT, STOCK_AMOUNT, UNIT }

sealed interface Validation {
    data class Valid(val values: ReagentValues) : Validation
    data class Invalid(val errors: Map<Field, String>) : Validation
}

/** Barcha maydonlar majburiy; xabarlar Laravel ilovadagi bilan bir xil. */
object ReagentValidator {
    const val NAME_MAX = 255

    fun validate(input: ReagentInput): Validation {
        val errors = linkedMapOf<Field, String>()

        val name = input.name.trim()
        when {
            name.isEmpty() -> errors[Field.NAME] = "Reaktiv nomi kiritilishi shart."
            name.codePointCount(0, name.length) > NAME_MAX ->
                errors[Field.NAME] = "Reaktiv nomi $NAME_MAX belgidan oshmasligi kerak."
        }

        if (input.receivedAt == null) errors[Field.RECEIVED_AT] = "Kelgan vaqt kiritilishi shart."

        when {
            input.expiresAt == null -> errors[Field.EXPIRES_AT] = "Yaroqlilik muddati kiritilishi shart."
            input.receivedAt != null && input.expiresAt.isBefore(input.receivedAt) ->
                errors[Field.EXPIRES_AT] = "Yaroqlilik muddati kelgan vaqtdan oldin bo‘lishi mumkin emas."
        }

        val given = amount(input.givenAmount, "Berilgan miqdor", Field.GIVEN_AMOUNT, errors)
        val stock = amount(input.stockAmount, "Ombordagi miqdor", Field.STOCK_AMOUNT, errors)

        if (input.unit !in Amounts.UNITS) errors[Field.UNIT] = "O‘lchov birligi ro‘yxatdan tanlanishi kerak."

        if (errors.isNotEmpty()) return Validation.Invalid(errors)

        return Validation.Valid(
            ReagentValues(
                name = name,
                receivedAt = input.receivedAt!!,
                expiresAt = input.expiresAt!!,
                givenAmount = given!!,
                stockAmount = stock!!,
                unit = input.unit,
            ),
        )
    }

    private fun amount(raw: String, label: String, field: Field, errors: MutableMap<Field, String>): BigDecimal? {
        if (Amounts.normalize(raw).isEmpty()) {
            errors[field] = "$label kiritilishi shart."
            return null
        }
        val value = Amounts.parse(raw)
        when {
            value == null -> errors[field] = "$label son bo‘lishi kerak."
            value.signum() < 0 -> errors[field] = "$label manfiy bo‘lishi mumkin emas."
            value > Amounts.MAX -> errors[field] = "$label juda katta."
            else -> return value
        }
        return null
    }
}

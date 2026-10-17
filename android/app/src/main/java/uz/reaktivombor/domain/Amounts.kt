package uz.reaktivombor.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Amounts {
    val MAX: BigDecimal = BigDecimal("999999999")

    val UNITS = listOf("gr", "ml", "kg", "l", "dona")

    private val NUMERIC = Regex("""[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?""")

    /** "1 200,5" → "1200.5" (bo‘shliq olib tashlanadi, vergul nuqtaga aylanadi). */
    fun normalize(raw: String): String =
        raw.trim().replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.')

    /** Son bo‘lmasa null. 3 xonagacha yaxlitlanadi (bazadagi decimal(12,3) kabi). */
    fun parse(raw: String): BigDecimal? {
        val normalized = normalize(raw)
        if (!NUMERIC.matches(normalized)) return null
        return normalized.toBigDecimalOrNull()?.setScale(3, RoundingMode.HALF_UP)
    }

    /** Ro‘yxat uchun: 1250.5 → "1 250,5 gr" (bo‘linmaydigan bo‘shliq bilan). */
    fun format(value: BigDecimal, unit: String): String {
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val formatter = DecimalFormat("#,##0.###", symbols).apply { roundingMode = RoundingMode.HALF_UP }
        return formatter.format(value) + " " + unit
    }

    /** Forma uchun: 500.000 → "500", 2.500 → "2,5". */
    fun forInput(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString().replace('.', ',')
}

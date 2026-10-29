package uz.reaktivombor.desktop.core

import uz.reaktivombor.desktop.normalizeUnit
import java.math.BigDecimal

/** O‘lchov turi. Turli turdagi miqdorlar (litr va gramm) hech qachon qo‘shilmaydi. */
enum class Dimension(val title: String) {
    VOLUME("hajm"),
    MASS("massa"),
    COUNT("dona"),
}

/**
 * O‘lchov birliklari. Bazada har bir miqdor asosiy birlikda butun son (Long): mikrolitr, milligramm, mingdan bir dona.
 * `asosiy = kiritilgan × factor` — hisob-kitobda kasr son yo‘q, yaxlitlash xatosi bo‘lmaydi.
 */
enum class MeasureUnit(val code: String, val title: String, val dimension: Dimension, val factor: Long) {
    L("l", "litr", Dimension.VOLUME, 1_000_000),
    ML("ml", "millilitr", Dimension.VOLUME, 1_000),
    KG("kg", "kilogramm", Dimension.MASS, 1_000_000),
    G("g", "gramm", Dimension.MASS, 1_000),
    PIECE("dona", "dona", Dimension.COUNT, 1_000),
    ;

    /** Shu o‘lchov turidagi birliklar (l → [l, ml]) — miqdorni ulardan istalganida kiritish mumkin. */
    val siblings: List<MeasureUnit> get() = entries.filter { it.dimension == dimension }

    companion object {
        /** "l", "litr", "ML", "мл", "gr", "гр.", "шт.", "ta" … → birlik; noma’lum bo‘lsa null. */
        fun of(code: String): MeasureUnit? {
            // Oxiridagi nuqta qisqartma belgisi ("gr.", "мл.") — avval olib tashlanadi, keyin qisqartma izlanadi.
            val text = normalizeUnit(code.trim().removeSuffix(".")).trim()
            if (text.isEmpty()) return null
            return entries.firstOrNull { it.code == text }
                ?: entries.firstOrNull { it.title == text }
                ?: EXTRA_ALIASES[text]
        }

        fun of(dimension: Dimension): List<MeasureUnit> = entries.filter { it.dimension == dimension }

        /** Guruh jami ko‘rsatiladigan birlik: guruhdagi eng katta birlik (5 l + 500 ml = 5,5 l). */
        fun largest(units: Collection<MeasureUnit>): MeasureUnit? = units.maxByOrNull { it.factor }

        /** O‘lchov turining katta birligi: l, kg, dona. */
        fun main(dimension: Dimension): MeasureUnit = when (dimension) {
            Dimension.VOLUME -> L
            Dimension.MASS -> KG
            Dimension.COUNT -> PIECE
        }

        private val EXTRA_ALIASES = mapOf(
            "lt" to L, "ltr" to L, "литр" to L, "liter" to L, "litre" to L,
            "миллилитр" to ML, "milliliter" to ML, "millilitre" to ML,
            "килограмм" to KG, "kilogram" to KG,
            "грамм" to G, "gram" to G,
            "pcs" to PIECE, "pc" to PIECE, "штук" to PIECE, "штука" to PIECE, "dona" to PIECE,
        )
    }
}

/**
 * Miqdorni o‘qish va ko‘rsatish. Kiritilgan matn → asosiy birlikdagi Long; Long → "1 250,5".
 * Birlik almashtirish faqat bir o‘lchov turi ichida (l ↔ ml, kg ↔ g): asosiy birlik umumiy bo‘lgani uchun alohida
 * hisob kerak emas — `format(asosiy, ml)` va `format(asosiy, l)` bitta miqdorni ikki xil ko‘rsatadi.
 */
object Qty {
    /** Kiritilgan birlikda eng katta qiymat. */
    const val MAX_ENTERED = 999_999_999L

    /** Kiritilgan birlikda verguldan keyingi eng ko‘p raqamlar. */
    const val MAX_DECIMALS = 3

    private val NUMBER = Regex("""\d+(\.\d*)?|\.\d+""")

    /**
     * "1 250,5", "1250.5", bo‘linmaydigan va ingichka bo‘shliqlar — qabul qilinadi. Bo‘sh, son emas, daraja ko‘rinishi
     * (1e3), manfiy, nol ([allowZero] bo‘lmasa), 3 tadan ko‘p kasr raqam va 999 999 999 dan katta qiymat — [UserError].
     */
    fun parse(
        text: String?,
        unit: MeasureUnit,
        label: String = "Miqdor",
        allowZero: Boolean = false,
        field: String? = null,
    ): Long {
        val normalized = normalize(text)
        if (normalized.isEmpty()) throw UserError("$label kiritilishi shart.", field)
        if (normalized.startsWith("-") || normalized.startsWith("−")) {
            throw UserError("$label manfiy bo‘lishi mumkin emas.", field)
        }
        if (!NUMBER.matches(normalized)) {
            throw UserError("$label son bo‘lishi kerak (masalan: 250 yoki 1,5).", field)
        }
        val value = BigDecimal(normalized).stripTrailingZeros()
        if (value.scale() > MAX_DECIMALS) {
            throw UserError("$label: verguldan keyin ko‘pi bilan $MAX_DECIMALS ta raqam bo‘lishi mumkin.", field)
        }
        if (value > BigDecimal.valueOf(MAX_ENTERED)) {
            throw UserError("$label juda katta (ko‘pi bilan ${format(MAX_ENTERED * unit.factor, unit)} ${unit.code}).", field)
        }
        if (value.signum() == 0 && !allowZero) throw UserError("$label noldan katta bo‘lishi kerak.", field)
        return value.multiply(BigDecimal.valueOf(unit.factor)).longValueExact()
    }

    /** Matnni son ko‘rinishiga keltiradi: bo‘shliqlar olib tashlanadi, vergul — nuqta. */
    fun normalize(text: String?): String =
        (text ?: "").filterNot { it.isWhitespace() || Character.getType(it) == Character.SPACE_SEPARATOR.toInt() }
            .replace(',', '.')

    /** 1 250 500 000 µl, ml → "1 250 500"; 1 250 500 µl, l → "1,2505". Kasr qism aniq (yaxlitlanmaydi), oxirgi nollarsiz. */
    fun format(base: Long, unit: MeasureUnit): String {
        val negative = base < 0
        val abs = if (negative) -base else base
        val whole = abs / unit.factor
        val fraction = abs % unit.factor
        val digits = unit.factor.toString().length - 1
        val fractionText = if (fraction == 0L) "" else fraction.toString().padStart(digits, '0').trimEnd('0')
        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append(group(whole))
        if (fractionText.isNotEmpty()) sb.append(',').append(fractionText)
        return sb.toString()
    }

    /** "1 250,5 ml". */
    fun formatWithUnit(base: Long, unit: MeasureUnit): String = format(base, unit) + " " + unit.code

    /** O‘lchov turi bo‘yicha jami: 1 l va undan ko‘p — l (kg), aks holda ml (g): "0,75 l" emas, "750 ml". */
    fun formatAuto(base: Long, dimension: Dimension): String {
        val big = MeasureUnit.main(dimension)
        val small = MeasureUnit.of(dimension).minByOrNull { it.factor } ?: big
        val unit = if (base == 0L || kotlin.math.abs(base) >= big.factor) big else small
        return formatWithUnit(base, unit)
    }

    /** Bir nechta o‘lchov turidagi jamilar: "5,5 l · 200 g". Turlar qo‘shilmaydi. */
    fun formatTotals(totals: Map<Dimension, Long>): String =
        Dimension.entries.filter { totals.containsKey(it) }.joinToString(" · ") { formatAuto(totals.getValue(it), it) }

    /** Excel/PDF uchun aniq o‘nli son. */
    fun toDecimal(base: Long, unit: MeasureUnit): BigDecimal =
        BigDecimal.valueOf(base).divide(BigDecimal.valueOf(unit.factor)).stripTrailingZeros()

    /** O‘nli son → asosiy birlik (aniq bo‘linmasa — ArithmeticException). */
    fun fromDecimal(value: BigDecimal, unit: MeasureUnit): Long =
        value.multiply(BigDecimal.valueOf(unit.factor)).stripTrailingZeros().longValueExact()

    private fun group(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { index, c ->
            if (index > 0 && (digits.length - index) % 3 == 0) sb.append(' ')
            sb.append(c)
        }
        return sb.toString()
    }
}

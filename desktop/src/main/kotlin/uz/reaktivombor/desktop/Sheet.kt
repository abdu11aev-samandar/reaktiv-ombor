package uz.reaktivombor.desktop

import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.ExpiryStatus
import uz.reaktivombor.domain.Field
import uz.reaktivombor.domain.ReagentInput
import uz.reaktivombor.domain.ReagentValidator
import uz.reaktivombor.domain.ReagentValues
import uz.reaktivombor.domain.Validation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Sanani yozish va o‘qish: 17.10.2026, 17/10/2026, 17-10-2026, 7.1.26 yoki 2026-10-17. */
object DateText {
    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val DAY_FIRST = Regex("""(\d{1,2})[./\-](\d{1,2})[./\-](\d{4}|\d{2})""")
    private val ISO = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")

    fun format(date: LocalDate): String = date.format(DISPLAY)

    fun parse(raw: String): LocalDate? {
        val text = raw.trim()
        DAY_FIRST.matchEntire(text)?.let { m ->
            val (day, month, year) = m.destructured
            val fullYear = if (year.length == 2) 2000 + year.toInt() else year.toInt()
            // 10/25/2026 kun/oy bo‘la olmaydi — demak AQSh tartibi (oy/kun/yil).
            return of(fullYear, month.toInt(), day.toInt())
                ?: if ('/' in text && month.toInt() > 12) of(fullYear, day.toInt(), month.toInt()) else null
        }
        ISO.matchEntire(text)?.let { m ->
            val (year, month, day) = m.destructured
            return of(year.toInt(), month.toInt(), day.toInt())
        }
        return null
    }

    private fun of(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private val SLASH = Regex("""(\d{1,2})/(\d{1,2})/(\d{4}|\d{2})""")

    /**
     * Kompyuterning mintaqaviy formati “English (United States)” — Excel sanalarni oy/kun/yil (10/25/2026) qilib nusxalaydi.
     * Dastur bilan CLDR mahalliy ma’lumotlari kelmaydi (shablon noto‘g‘ri chiqadi), shuning uchun mamlakatga qaraymiz.
     */
    val pcMonthFirst: Boolean by lazy { Locale.getDefault(Locale.Category.FORMAT).country == "US" }

    /**
     * Nusxadagi “/” li sanalar oy/kun/yil tartibidami. Faqat AQSh formatidagi kompyuterda — boshqa kompyuterda Excel
     * kun/oy beradi va bitta xato qiymat qolgan sanalarni o‘zgartirib yubormasligi kerak. Birinchi qismi 12 dan katta
     * sana bo‘lsa (25/10/2026), blok baribir kun/oy.
     */
    fun isMonthFirst(values: List<String>, pcMonthFirst: Boolean = this.pcMonthFirst): Boolean {
        if (!pcMonthFirst) return false
        var sawSlash = false
        for (value in values) {
            val (first) = SLASH.matchEntire(value.trim())?.destructured ?: continue
            if (first.toInt() > 12) return false
            sawSlash = true
        }
        return sawSlash
    }

    /** "3/10/2027" — kun/oy ham, oy/kun ham bo‘la oladi (ikkala qismi 12 dan oshmaydi va har xil). */
    fun isAmbiguous(raw: String): Boolean = SLASH.matchEntire(raw.trim())?.destructured?.let { (first, second) ->
        first.toInt() in 1..12 && second.toInt() in 1..12 && first.toInt() != second.toInt()
    } ?: false

    /** "3/10/2027" (oy/kun/yil) → "10.3.2027". */
    fun fromMonthFirst(raw: String): String =
        SLASH.matchEntire(raw.trim())?.destructured?.let { (month, day, year) -> "$day.$month.$year" } ?: raw
}

/** Birlikni tozalaydi: "ML" → "ml", "г" → "gr". Noma’lum bo‘lsa — o‘zgarishsiz (keyin xato ko‘rsatiladi). */
fun normalizeUnit(raw: String): String {
    val text = raw.trim().lowercase()
    return UNIT_ALIASES[text] ?: text
}

private val UNIT_ALIASES = mapOf(
    "g" to "gr", "gram" to "gr", "gramm" to "gr", "г" to "gr", "гр" to "gr",
    "мл" to "ml", "кг" to "kg", "л" to "l", "litr" to "l", "дона" to "dona", "ta" to "dona", "шт" to "dona",
)

/** Jadval ustunlari (Excel’dagi A, B, C … kabi). */
enum class Col(val title: String, val widthDp: Int, val field: Field?) {
    NAME("Reaktiv nomi", 250, Field.NAME),
    RECEIVED("Kelgan vaqt", 120, Field.RECEIVED_AT),
    GIVEN("Berilgan miqdor", 135, Field.GIVEN_AMOUNT),
    UNIT("Birlik", 75, Field.UNIT),
    EXPIRES("Yaroqlilik muddati", 145, Field.EXPIRES_AT),
    STOCK("Ombordagi miqdor", 145, Field.STOCK_AMOUNT),
    STATUS("Holat", 250, null),
    ;

    val editable: Boolean get() = this.field != null
    val letter: Char get() = 'A' + ordinal
}

/**
 * Jadvaldagi bitta qator — foydalanuvchi yozgan matn ko‘rinishida.
 * Hamma maydon to‘g‘ri to‘ldirilmaguncha qator “tugallanmagan” hisoblanadi (Excel’dagi kabi bemalol yoziladi).
 */
data class SheetRow(
    val id: Long,
    val name: String = "",
    val receivedAt: String = "",
    val givenAmount: String = "",
    val unit: String = "gr",
    val expiresAt: String = "",
    val stockAmount: String = "",
    val createdAt: Long,
) {
    operator fun get(col: Col): String = when (col) {
        Col.NAME -> name
        Col.RECEIVED -> receivedAt
        Col.GIVEN -> givenAmount
        Col.UNIT -> unit
        Col.EXPIRES -> expiresAt
        Col.STOCK -> stockAmount
        Col.STATUS -> ""
    }

    fun with(col: Col, value: String): SheetRow = when (col) {
        Col.NAME -> copy(name = value)
        Col.RECEIVED -> copy(receivedAt = value)
        Col.GIVEN -> copy(givenAmount = value)
        Col.UNIT -> copy(unit = normalizeUnit(value))
        Col.EXPIRES -> copy(expiresAt = value)
        Col.STOCK -> copy(stockAmount = value)
        Col.STATUS -> this
    }

    val isBlank: Boolean
        get() = name.isBlank() && receivedAt.isBlank() && givenAmount.isBlank() && expiresAt.isBlank() && stockAmount.isBlank()

    fun check(): RowCheck {
        val received = DateText.parse(receivedAt)
        val expires = DateText.parse(expiresAt)
        val result = ReagentValidator.validate(ReagentInput(name, received, expires, givenAmount, stockAmount, unit))
        val errors = ((result as? Validation.Invalid)?.errors ?: emptyMap()).toMutableMap()
        if (receivedAt.isNotBlank() && received == null) {
            errors[Field.RECEIVED_AT] = "Kelgan vaqt to‘g‘ri sana bo‘lishi kerak (masalan: 17.10.2026)."
        }
        if (expiresAt.isNotBlank() && expires == null) {
            errors[Field.EXPIRES_AT] = "Yaroqlilik muddati to‘g‘ri sana bo‘lishi kerak (masalan: 17.10.2027)."
        }
        return RowCheck((result as? Validation.Valid)?.values, errors)
    }
}

data class RowCheck(val values: ReagentValues?, val errors: Map<Field, String>) {
    val complete: Boolean get() = values != null

    fun status(today: LocalDate): ExpiryStatus? = values?.let { ExpiryStatus.of(it.expiresAt, today) }

    fun error(col: Col): String? = col.field?.let(errors::get)
}

/** Ko‘rinishdagi miqdor: "1 250,5" (birlik alohida ustunda). */
fun displayAmount(raw: String): String =
    Amounts.parse(raw)?.let { Amounts.format(it, "").trimEnd(' ', ' ') } ?: raw

fun displayDate(raw: String): String = DateText.parse(raw)?.let(DateText::format) ?: raw

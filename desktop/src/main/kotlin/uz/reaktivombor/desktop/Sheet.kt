package uz.reaktivombor.desktop

import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.ExpiryStatus
import uz.reaktivombor.domain.daysUntil
import java.math.BigDecimal
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

/** O‘lchov birliklari — qog‘ozdagi jadval tartibida. */
val UNITS = listOf("l", "ml", "kg", "g", "dona")

/** Bo‘limlar: faqat shular yoziladi (hisobot va filtr to‘g‘ri chiqishi uchun). */
val DEPARTMENTS = listOf("Gistologiya", "Biologiya", "Morfologiya")

/** Birlikni tozalaydi: "ML" → "ml", "gr"/"г" → "g". Noma’lum bo‘lsa — o‘zgarishsiz (keyin xato ko‘rsatiladi). */
fun normalizeUnit(raw: String): String {
    val text = raw.trim().lowercase()
    return UNIT_ALIASES[text] ?: text
}

private val UNIT_ALIASES = mapOf(
    "gr" to "g", "gram" to "g", "gramm" to "g", "г" to "g", "гр" to "g",
    "мл" to "ml", "кг" to "kg", "л" to "l", "litr" to "l", "дона" to "dona", "ta" to "dona", "шт" to "dona",
)

/** "b", "BIO", "Биология" → "Biologiya". Mos kelmasa — o‘zgarishsiz (keyin xato ko‘rsatiladi). */
fun normalizeDepartment(raw: String): String {
    val text = raw.trim()
    val key = text.lowercase()
    if (key.isEmpty()) return ""
    return DEPARTMENTS.firstOrNull { it.lowercase().startsWith(key) } ?: DEPARTMENT_ALIASES[key] ?: text
}

private val DEPARTMENT_ALIASES = mapOf(
    "гистология" to "Gistologiya", "биология" to "Biologiya", "морфология" to "Morfologiya",
    "histologiya" to "Gistologiya", "histology" to "Gistologiya", "biology" to "Biologiya", "morphology" to "Morfologiya",
)

/** Jadval ustunlari (Excel’dagi A, B, C … kabi) — qog‘ozdagi jadval tartibida. */
enum class Col(val title: String, val widthDp: Int, val kind: Kind) {
    NAME("Reaktiv nomi", 210, Kind.TEXT),
    PRODUCED("Ishlab chiqarilgan vaqti", 125, Kind.DATE),
    RECEIVED_AMOUNT("Kelgan miqdor", 105, Kind.AMOUNT),
    UNIT("O‘lchov birligi", 85, Kind.CHOICE),
    EXPIRES("Yaroqlilik muddati", 120, Kind.DATE),
    STOCK("Ombordagi miqdor", 110, Kind.AMOUNT),
    DEPARTMENT("Bo‘lim", 120, Kind.CHOICE),
    ISSUED("Berilgan miqdor", 105, Kind.AMOUNT),
    REMAINDER("Qoldiq", 100, Kind.COMPUTED),
    DAYS_LEFT("Qolgan muddat (kun)", 120, Kind.COMPUTED),
    ;

    enum class Kind { TEXT, DATE, AMOUNT, CHOICE, COMPUTED }

    /** Qoldiq va qolgan muddatni dastur o‘zi hisoblaydi — ularga yozilmaydi. */
    val editable: Boolean get() = kind != Kind.COMPUTED
    val isDate: Boolean get() = kind == Kind.DATE
    val alignEnd: Boolean get() = kind == Kind.DATE || kind == Kind.AMOUNT || kind == Kind.COMPUTED
    val letter: Char get() = 'A' + ordinal

    /** Ro‘yxatdan tanlanadigan qiymatlar (birlik, bo‘lim). */
    val choices: List<String>
        get() = when (this) {
            UNIT -> UNITS
            DEPARTMENT -> DEPARTMENTS
            else -> emptyList()
        }
}

/**
 * Jadvaldagi bitta qator — foydalanuvchi yozgan matn ko‘rinishida.
 * Majburiy maydonlar to‘g‘ri to‘ldirilmaguncha qator “tugallanmagan” hisoblanadi (Excel’dagi kabi bemalol yoziladi).
 */
data class SheetRow(
    val id: Long,
    val name: String = "",
    val producedAt: String = "",
    val receivedAmount: String = "",
    val unit: String = "g",
    val expiresAt: String = "",
    val stockAmount: String = "",
    val department: String = "",
    val issuedAmount: String = "",
    val createdAt: Long,
    /** Eski jadvaldagi «Kelgan vaqt» — ustuni olib tashlangan, lekin yo‘qolmasin deb faylda saqlanadi. */
    val legacyReceivedAt: String = "",
) {
    operator fun get(col: Col): String = when (col) {
        Col.NAME -> name
        Col.PRODUCED -> producedAt
        Col.RECEIVED_AMOUNT -> receivedAmount
        Col.UNIT -> unit
        Col.EXPIRES -> expiresAt
        Col.STOCK -> stockAmount
        Col.DEPARTMENT -> department
        Col.ISSUED -> issuedAmount
        Col.REMAINDER, Col.DAYS_LEFT -> ""
    }

    fun with(col: Col, value: String): SheetRow = when (col) {
        Col.NAME -> copy(name = value)
        Col.PRODUCED -> copy(producedAt = value)
        Col.RECEIVED_AMOUNT -> copy(receivedAmount = value)
        Col.UNIT -> copy(unit = normalizeUnit(value))
        Col.EXPIRES -> copy(expiresAt = value)
        Col.STOCK -> copy(stockAmount = value)
        Col.DEPARTMENT -> copy(department = normalizeDepartment(value))
        Col.ISSUED -> copy(issuedAmount = value)
        Col.REMAINDER, Col.DAYS_LEFT -> this
    }

    val isBlank: Boolean
        get() = Col.entries.all { !it.editable || it == Col.UNIT || this[it].isBlank() }

    fun check(): RowCheck {
        val errors = linkedMapOf<Col, String>()

        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> errors[Col.NAME] = "Reaktiv nomi kiritilishi shart."
            trimmed.codePointCount(0, trimmed.length) > NAME_MAX -> errors[Col.NAME] = "Reaktiv nomi $NAME_MAX belgidan oshmasligi kerak."
        }

        val produced = date(producedAt, Col.PRODUCED, "Ishlab chiqarilgan vaqti", "17.10.2026", errors)
        if (producedAt.isBlank() && legacyReceivedAt.isNotBlank()) {
            errors[Col.PRODUCED] = "Ishlab chiqarilgan vaqtini kiriting (eski jadvaldagi «Kelgan vaqt»: ${displayDate(legacyReceivedAt)})."
        }
        val expires = date(expiresAt, Col.EXPIRES, "Yaroqlilik muddati", "17.10.2027", errors)
        if (produced != null && expires != null && expires.isBefore(produced)) {
            errors[Col.EXPIRES] = "Yaroqlilik muddati ishlab chiqarilgan vaqtdan oldin bo‘lishi mumkin emas."
        }

        amount(receivedAmount, Col.RECEIVED_AMOUNT, "Kelgan miqdor", required = true, errors)
        if (unit !in UNITS) errors[Col.UNIT] = "O‘lchov birligi: l, ml, kg, g yoki dona."
        val stock = amount(stockAmount, Col.STOCK, "Ombordagi miqdor", required = true, errors)

        // Bo‘lim va berilgan miqdor ixtiyoriy, lekin birga yoziladi.
        val dept = department.trim()
        val issued = amount(issuedAmount, Col.ISSUED, "Berilgan miqdor", required = false, errors)
        when {
            dept.isNotEmpty() && dept !in DEPARTMENTS -> errors[Col.DEPARTMENT] = "Bo‘lim: Gistologiya, Biologiya yoki Morfologiya."
            dept.isEmpty() && issuedAmount.isNotBlank() -> errors[Col.DEPARTMENT] = "Qaysi bo‘limga berilganini tanlang."
        }
        if (dept.isNotEmpty() && issuedAmount.isBlank()) errors[Col.ISSUED] = "Bo‘limga berilgan miqdorni kiriting."
        if (issued != null && stock != null && issued > stock) {
            errors[Col.ISSUED] = "Berilgan miqdor ombordagi miqdordan ko‘p bo‘lishi mumkin emas."
        }

        val remainder = if (stock != null && Col.ISSUED !in errors) stock - (issued ?: BigDecimal.ZERO) else null
        return RowCheck(errors, expires, remainder)
    }

    private companion object {
        const val NAME_MAX = 255

        fun date(raw: String, col: Col, label: String, example: String, errors: MutableMap<Col, String>): LocalDate? {
            if (raw.isBlank()) {
                errors[col] = "$label kiritilishi shart."
                return null
            }
            return DateText.parse(raw) ?: run {
                errors[col] = "$label to‘g‘ri sana bo‘lishi kerak (masalan: $example)."
                null
            }
        }

        fun amount(raw: String, col: Col, label: String, required: Boolean, errors: MutableMap<Col, String>): BigDecimal? {
            if (Amounts.normalize(raw).isEmpty()) {
                if (required) errors[col] = "$label kiritilishi shart."
                return null
            }
            val value = Amounts.parse(raw)
            when {
                value == null -> errors[col] = "$label son bo‘lishi kerak."
                value.signum() < 0 -> errors[col] = "$label manfiy bo‘lishi mumkin emas."
                value > Amounts.MAX -> errors[col] = "$label juda katta."
                else -> return value
            }
            return null
        }
    }
}

/**
 * Qatorni tekshirish natijasi. Rang (va qolgan kunlar) faqat yaroqlilik muddatiga qarab chiqadi — qator hali
 * to‘liq to‘ldirilmagan bo‘lsa ham muddati yaqin reaktiv ko‘rinib tursin.
 */
data class RowCheck(val errors: Map<Col, String>, val expires: LocalDate?, val remainder: BigDecimal?) {
    val complete: Boolean get() = errors.isEmpty()

    fun status(today: LocalDate): ExpiryStatus? = expires?.let { ExpiryStatus.of(it, today) }

    fun daysLeft(today: LocalDate): Long? = expires?.let { daysUntil(it, today) }

    fun error(col: Col): String? = errors[col]
}

/** Ko‘rinishdagi miqdor: "1 250,5" (birlik alohida ustunda). */
fun displayAmount(raw: String): String =
    Amounts.parse(raw)?.let(::displayAmount) ?: raw

// Amounts.format birlik oldiga bo‘linmaydigan bo‘shliq qo‘yadi — trimEnd() uni ham olib tashlaydi.
fun displayAmount(value: BigDecimal): String = Amounts.format(value, "").trimEnd()

fun displayDate(raw: String): String = DateText.parse(raw)?.let(DateText::format) ?: raw

/** «Qolgan muddat (kun)» ustuni: 39, 0 (bugun), -12 (o‘tgan). */
fun daysLeftText(days: Long): String = when {
    days > 0 -> "$days"
    days == 0L -> "0 (bugun)"
    else -> "$days (o‘tgan)"
}

/** Katakda ko‘rinadigan matn (hisoblanadigan ustunlar ham). */
fun cellText(row: SheetRow, col: Col, check: RowCheck?, today: LocalDate): String = when (col.kind) {
    Col.Kind.DATE -> displayDate(row[col])
    Col.Kind.AMOUNT -> displayAmount(row[col])
    Col.Kind.COMPUTED -> when (col) {
        Col.REMAINDER -> check?.remainder?.let(::displayAmount) ?: ""
        else -> check?.daysLeft(today)?.let(::daysLeftText) ?: ""
    }
    else -> row[col]
}

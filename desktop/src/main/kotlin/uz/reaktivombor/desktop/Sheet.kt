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

/**
 * Yangi jadvaldagi bo‘limlar. Ro‘yxat jadval bilan birga faylda saqlanadi: katakka yangi nom yozilsa, unga qo‘shiladi;
 * «Bo‘limlar» oynasida nomi o‘zgartiriladi yoki olib tashlanadi.
 */
val DEFAULT_DEPARTMENTS = listOf("Gistologiya", "Biologiya", "Morfologiya")

const val DEPARTMENT_MAX = 60

/** Birlikni tozalaydi: "ML" → "ml", "gr"/"г" → "g". Noma’lum bo‘lsa — o‘zgarishsiz (keyin xato ko‘rsatiladi). */
fun normalizeUnit(raw: String): String {
    val text = raw.trim().lowercase()
    return UNIT_ALIASES[text] ?: text
}

private val UNIT_ALIASES = mapOf(
    "gr" to "g", "gram" to "g", "gramm" to "g", "г" to "g", "гр" to "g",
    "мл" to "ml", "кг" to "kg", "л" to "l", "litr" to "l", "дона" to "dona", "ta" to "dona", "шт" to "dona",
)

/** Qidiruv va bo‘lim nomlari: katta-kichik harf va o‘ / o' / o’ / o´ (tutuq belgisining hamma ko‘rinishi) farqlanmaydi. */
fun searchKey(value: String): String = value.replace(APOSTROPHES, "‘").lowercase()

private val APOSTROPHES = Regex("""['`´‘’‛ʹʻʼʽ′]""")

/** Nom chetidagi bo‘shliqlarsiz, ichidagi har qanday bo‘shliq (Word/Telegram’dagi bo‘linmaydigani ham) — bitta oddiy bo‘shliq. */
fun cleanName(raw: String): String = raw.replace(SPACES, " ").trim()

private val SPACES = Regex("""[\s\p{Z}]+""")

private fun departmentKey(name: String): String = searchKey(cleanName(name))

/** Bo‘lim nomi harf bilan yoziladi — adashib bu ustunga qo‘yilgan son yoki sana yangi bo‘lim bo‘lib qolmasin. */
fun isDepartmentName(name: String): Boolean {
    val text = cleanName(name)
    return text.any { it.isLetter() } && text.codePointCount(0, text.length) <= DEPARTMENT_MAX
}

/** Bitta bo‘limmi: katta-kichik harf, tutuq belgisi va bo‘shliqlar farqlanmaydi. */
fun sameDepartment(a: String, b: String): Boolean = departmentKey(a) == departmentKey(b)

/**
 * Yozilgan nomni ro‘yxatdagi bo‘limga keltiradi: "biologiya", "Биология" → "Biologiya". [prefix] bo‘lsa boshi ham
 * yetadi ("b" → "Biologiya"; bir nechtasiga mos kelsa — ro‘yxatda birinchisi, ochilgan ro‘yxatdagi kabi).
 * Ro‘yxatda yo‘q nom — yangi bo‘lim: bosh harfi katta, ortiqcha bo‘shliqlarsiz.
 */
fun matchDepartment(raw: String, departments: List<String>, prefix: Boolean = false): String {
    val text = cleanName(raw)
    if (text.isEmpty()) return ""
    val key = searchKey(text)
    departments.firstOrNull { departmentKey(it) == key }?.let { return it }
    // Ruscha/inglizcha nom — faqat o‘sha bo‘lim ro‘yxatda bo‘lsa: nomi o‘zgartirilgan yoki olib tashlangan bo‘lim qaytib kelmasin.
    DEPARTMENT_ALIASES[key]?.let { name -> departments.firstOrNull { sameDepartment(it, name) }?.let { return it } }
    if (prefix) departments.firstOrNull { departmentKey(it).startsWith(key) }?.let { return it }
    return text.replaceFirstChar { it.titlecase() }
}

/** [departments] (takrorlarsiz, faqat to‘g‘ri nomlar) va qatorlarda yozilgan, lekin ro‘yxatda yo‘q bo‘limlar — oxirida. */
fun withUsedDepartments(departments: List<String>, rows: List<SheetRow>): List<String> {
    val result = mutableListOf<String>()
    (departments + rows.map { it.department }).forEach { raw ->
        val name = cleanName(raw)
        if (isDepartmentName(name) && result.none { sameDepartment(it, name) }) result += name
    }
    return result
}

/**
 * Bo‘lim katagiga yoziladigan qiymat: ro‘yxatdagi nom ("biologiya" → "Biologiya"). Yangi nom [list] oxiriga
 * qo‘shiladi; harfsiz qiymat (adashib qo‘yilgan son) qo‘shilmaydi — katakda xato bo‘lib ko‘rinadi.
 */
fun resolveDepartment(raw: String, list: MutableList<String>): String {
    val name = matchDepartment(raw, list)
    if (isDepartmentName(name) && list.none { sameDepartment(it, name) }) list += name
    return name
}

/**
 * Fayldan yoki boshqa jadvaldan kelgan qatorlar: bo‘limi ro‘yxatdagi yozilishiga keltiriladi, yangi nom ro‘yxatga
 * qo‘shiladi va keyingi qatorlardagi xuddi shu nom unga keltiriladi ("Kimyo" va "KIMYO" — bitta bo‘lim).
 * Qatorlar va kengaygan ro‘yxat qaytadi.
 */
fun withDepartmentsInList(rows: List<SheetRow>, departments: List<String>): Pair<List<SheetRow>, List<String>> {
    val list = withUsedDepartments(departments, emptyList()).toMutableList()
    val result = rows.map { row ->
        val name = resolveDepartment(row.department, list)
        if (name == row.department) row else row.copy(department = name)
    }
    return result to list
}

const val DEPARTMENT_NAME_ERROR = "Bo‘lim nomi harf bilan yozilishi kerak ($DEPARTMENT_MAX belgigacha)."

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

    /** Ro‘yxatdan tanlanadigan o‘zgarmas qiymatlar (birlik). Bo‘limlar ro‘yxati jadval bilan saqlanadi — SheetState.departments. */
    val choices: List<String>
        get() = if (this == UNIT) UNITS else emptyList()
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
    // Birlik majburiy: yangi qatorda tanlanmaguncha bo‘sh (gramm deb jimgina yozilib qolmasin).
    val unit: String = "",
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
        // Ro‘yxatdagi nomga SheetState keltiradi (unga yangi bo‘lim ham o‘sha yerda qo‘shiladi).
        Col.DEPARTMENT -> copy(department = value.trim())
        Col.ISSUED -> copy(issuedAmount = value)
        Col.REMAINDER, Col.DAYS_LEFT -> this
    }

    /** Eski «Kelgan vaqt» ham ma’lumot: faqat shu sana yozilgan eski qator bo‘sh hisoblanmaydi (saqlanadi va ko‘rinadi). */
    val isBlank: Boolean
        get() = legacyReceivedAt.isBlank() && Col.entries.all { !it.editable || it == Col.UNIT || this[it].isBlank() }

    fun check(today: LocalDate = LocalDate.now()): RowCheck {
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
        // Kelajakdagi sana — xato (masalan, 2062 yoki "99" → 2099): xato aynan shu katakda ko‘rinsin.
        if (produced != null && produced.isAfter(today)) {
            errors[Col.PRODUCED] = "Ishlab chiqarilgan vaqti (${DateText.format(produced)}) bugundan keyin bo‘lishi mumkin emas."
        }
        val expires = date(expiresAt, Col.EXPIRES, "Yaroqlilik muddati", "17.10.2027", errors)
        if (produced != null && expires != null && expires.isBefore(produced) && Col.PRODUCED !in errors) {
            errors[Col.EXPIRES] = "Yaroqlilik muddati ishlab chiqarilgan vaqtdan oldin bo‘lishi mumkin emas."
        }

        amount(receivedAmount, Col.RECEIVED_AMOUNT, "Kelgan miqdor", required = true, errors)
        if (unit !in UNITS) errors[Col.UNIT] = "O‘lchov birligi: l, ml, kg, g yoki dona."
        val stock = amount(stockAmount, Col.STOCK, "Ombordagi miqdor", required = true, errors)

        // Bo‘lim va berilgan miqdor ixtiyoriy, lekin birga yoziladi.
        val dept = department.trim()
        val issued = amount(issuedAmount, Col.ISSUED, "Berilgan miqdor", required = false, errors)
        when {
            dept.isNotEmpty() && !isDepartmentName(dept) -> errors[Col.DEPARTMENT] = DEPARTMENT_NAME_ERROR
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

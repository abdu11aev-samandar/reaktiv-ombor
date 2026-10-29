package uz.reaktivombor.desktop.ui

import uz.reaktivombor.desktop.DateText
import java.time.LocalDate
import java.time.YearMonth

/** Sanalarni o‘zbekcha ko‘rsatish (CLDR’ga bog‘liq emas — dastur bilan o‘zbek tili ma’lumotlari kelmaydi). */
object UzDates {
    val MONTHS = listOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
        "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr",
    )

    /** Hafta dushanbadan boshlanadi. */
    val WEEKDAYS_SHORT = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")
    val WEEKDAYS = listOf("Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba", "Yakshanba")

    /** 17.10.2026 */
    fun format(date: LocalDate?): String = date?.let(DateText::format) ?: ""

    /** 17.10.2026, 7.1.26, 2026-10-17, 17/10/2026 … (eski jadvaldagi DateText.parse bilan bir xil). */
    fun parse(text: String): LocalDate? = DateText.parse(text)

    fun weekday(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    fun month(month: YearMonth): String = "${MONTHS[month.monthValue - 1]} ${month.year}"
}

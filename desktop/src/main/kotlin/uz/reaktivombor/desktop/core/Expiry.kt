package uz.reaktivombor.desktop.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Yaroqlilik holati — faqat yaroqlilik muddatidan, aniq kunlar bilan (texnik topshiriq, 9-bo‘lim):
 * 90 kundan ko‘p — yashil, 31–90 — sariq, 0–30 — qizil, o‘tgan — to‘q qizil, muddati yo‘q (faqat eski ma’lumot) — kulrang.
 * [severity] — qaysi biri xavfliroq (guruhdagi eng yomon holatni topish uchun).
 */
enum class ExpiryState(val label: String, val severity: Int) {
    GREEN("Yaroqli", 0),
    YELLOW("1–3 oy qoldi", 2),
    RED("1 oy yoki kam qoldi", 3),
    EXPIRED("Muddati o‘tgan", 4),
    UNKNOWN("Muddati kiritilmagan", 1),
}

/** Sariq chegarasi: shundan ko‘p kun qolgan bo‘lsa — yashil. */
const val GREEN_AFTER_DAYS = 90L

/** Qizil chegarasi: shuncha yoki kam kun qolgan bo‘lsa — qizil. */
const val RED_UNTIL_DAYS = 30L

/** Muddatgacha qolgan kunlar (bugun tugasa 0, o‘tgan bo‘lsa manfiy); muddat yo‘q — null. */
fun daysLeft(expiresOn: LocalDate?, today: LocalDate): Long? =
    expiresOn?.let { ChronoUnit.DAYS.between(today, it) }

/** Hamma joyda (jadval, bosh sahifa, eslatmalar, hisobotlar, Excel/PDF ranglari) — shu bitta funksiya. */
fun expiryState(expiresOn: LocalDate?, today: LocalDate): ExpiryState {
    val days = daysLeft(expiresOn, today) ?: return ExpiryState.UNKNOWN
    return when {
        days < 0 -> ExpiryState.EXPIRED
        days <= RED_UNTIL_DAYS -> ExpiryState.RED
        days <= GREEN_AFTER_DAYS -> ExpiryState.YELLOW
        else -> ExpiryState.GREEN
    }
}

/** «Qolgan kunlar» matni: "45", "Bugun tugaydi", "12 kun o‘tgan", muddat yo‘q — "—". */
fun daysLeftLabel(days: Long?): String = when {
    days == null -> "—"
    days > 0 -> "$days"
    days == 0L -> "Bugun tugaydi"
    else -> "${-days} kun o‘tgan"
}

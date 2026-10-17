package uz.reaktivombor.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Reaktivning yaroqlilik muddati bo‘yicha rangi (Laravel’dagi App\Enums\ExpiryStatus bilan bir xil).
 *
 *  - 3 oydan ko‘p qolgan                     → yashil
 *  - 1 oydan 3 oygacha (ikkalasi ham) qolgan → sariq
 *  - 1 oydan kam qolgan yoki o‘tgan          → qizil
 *
 * Oylar kalendar bo‘yicha: LocalDate.plusMonths oy oxiriga “qisadi” (30-noyabr + 3 oy = 28-fevral).
 */
enum class ExpiryStatus(val label: String) {
    RED("1 oydan kam"),
    YELLOW("1 – 3 oy"),
    GREEN("3 oydan ko‘p"),
    ;

    companion object {
        const val RED_BELOW_MONTHS = 1L
        const val GREEN_AFTER_MONTHS = 3L

        fun of(expiresAt: LocalDate, today: LocalDate): ExpiryStatus = when {
            expiresAt.isBefore(today.plusMonths(RED_BELOW_MONTHS)) -> RED
            !expiresAt.isAfter(today.plusMonths(GREEN_AFTER_MONTHS)) -> YELLOW
            else -> GREEN
        }
    }
}

/** Eslatma bosqichi: faqat yuqoriga o‘tganda (yashil → sariq → qizil → o‘tgan) xabar beriladi. */
enum class AlertStage {
    NONE,
    YELLOW,
    RED,
    EXPIRED,
    ;

    companion object {
        fun of(expiresAt: LocalDate, today: LocalDate): AlertStage = when {
            expiresAt.isBefore(today) -> EXPIRED
            else -> when (ExpiryStatus.of(expiresAt, today)) {
                ExpiryStatus.RED -> RED
                ExpiryStatus.YELLOW -> YELLOW
                ExpiryStatus.GREEN -> NONE
            }
        }
    }
}

fun daysUntil(expiresAt: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, expiresAt)

fun expiryHint(days: Long): String = when {
    days > 0 -> "$days kun qoldi"
    days == 0L -> "Bugun tugaydi"
    else -> "Muddati ${-days} kun oldin o‘tgan"
}

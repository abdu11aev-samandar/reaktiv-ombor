package uz.reaktivombor.desktop.core

import uz.reaktivombor.desktop.DateText
import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.searchKey
import java.time.LocalDate

// Nomlar eski jadval qoidalari bilan solishtiriladi (Sheet.kt): katta-kichik harf, tutuq belgisining
// ko‘rinishlari (o' o‘ o’ o`) va ortiqcha bo‘shliqlar farqlanmaydi.

/** Nom kaliti: "  Etil   SPIRTI " va "etil spirti" — bitta nom. Bazadagi `name_key` ustuni shu. */
fun nameKey(raw: String): String = searchKey(cleanName(raw))

/** Qidiruv: [query] bo‘sh bo‘lsa hamma narsa mos; aks holda maydonlardan birida (kalit bo‘yicha) uchrashi kerak. */
fun matchesQuery(query: String?, vararg values: String?): Boolean {
    val key = nameKey(query ?: "")
    if (key.isEmpty()) return true
    return values.any { it != null && nameKey(it).contains(key) }
}

/** Belgilar soni (emoji va boshqa ikki qismli belgilar bitta hisoblanadi). */
fun String.charCount(): Int = codePointCount(0, length)

/** Xabarlar uchun sana: 17.10.2026. */
fun LocalDate.display(): String = DateText.format(this)

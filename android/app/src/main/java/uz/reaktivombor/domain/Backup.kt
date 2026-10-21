package uz.reaktivombor.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

class BackupException(message: String) : Exception(message)

/**
 * Zaxiradagi bitta qator. Platformaga bog‘liq emas — Android va Windows ilovalari
 * shu bitta formatni o‘qiydi va yozadi, shuning uchun fayl ikkalasi orasida ko‘chadi.
 */
data class BackupItem(
    val name: String,
    val receivedAt: LocalDate,
    val givenAmount: BigDecimal,
    val unit: String,
    val expiresAt: LocalDate,
    val stockAmount: BigDecimal,
    val createdAt: Long,
)

/**
 * Zaxira fayli (JSON). Telefon almashtirilganda, ilova qayta o‘rnatilganda yoki
 * telefon ↔ kompyuter o‘rtasida ro‘yxatni ko‘chirish uchun.
 *
 * Bu papka (domain/) Android’ga bog‘liq bo‘lmasligi kerak: Windows ilovasi uni to‘g‘ridan-to‘g‘ri kompilyatsiya qiladi.
 */
object Backup {
    const val FORMAT = "reaktiv-ombor"
    const val VERSION = 1

    fun toJsonObject(items: List<BackupItem>, exportedAt: String): JSONObject {
        val array = JSONArray()
        items.forEach { r ->
            array.put(
                JSONObject()
                    .put("name", r.name)
                    .put("receivedAt", r.receivedAt.toString())
                    .put("givenAmount", r.givenAmount.toPlainString())
                    .put("unit", r.unit)
                    .put("expiresAt", r.expiresAt.toString())
                    .put("stockAmount", r.stockAmount.toPlainString())
                    .put("createdAt", r.createdAt),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("reagents", array)
    }

    fun toJson(items: List<BackupItem>, exportedAt: String): String = toJsonObject(items, exportedAt).toString(2)

    data class Parsed(val items: List<BackupItem>, val skipped: Int)

    /** Fayl matnini tekshiradi: bizning formatimizmi va versiya mosmi. */
    fun parseRoot(text: String): JSONObject {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException("Bu fayl zaxira nusxa emas.")
        }
        if (root.optString("format") != FORMAT) throw BackupException("Bu fayl «Reaktivlar» ilovasining zaxira nusxasi emas.")
        if (root.optInt("version", 0) > VERSION) throw BackupException("Zaxira ilovaning yangiroq versiyasida olingan. Ilovani yangilang.")
        return root
    }

    /**
     * Faylni o‘qiydi. Har bir qator ilovadagi forma qoidalari bilan tekshiriladi;
     * buzuq qatorlar tashlab ketiladi va soni qaytariladi.
     */
    fun fromJson(text: String): Parsed = fromJsonObject(parseRoot(text))

    fun fromJsonObject(root: JSONObject): Parsed {
        val array = root.optJSONArray("reagents") ?: throw BackupException("Zaxira faylida reaktivlar ro‘yxati yo‘q.")
        val items = mutableListOf<BackupItem>()
        var skipped = 0

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)?.let(::parseItem)
            if (item == null) skipped++ else items += item
        }
        return Parsed(items, skipped)
    }

    private fun parseItem(item: JSONObject): BackupItem? {
        val input = ReagentInput(
            name = item.optString("name"),
            receivedAt = date(item.optString("receivedAt")),
            expiresAt = date(item.optString("expiresAt")),
            givenAmount = item.optString("givenAmount"),
            stockAmount = item.optString("stockAmount"),
            unit = item.optString("unit"),
        )
        val values = (ReagentValidator.validate(input) as? Validation.Valid)?.values ?: return null
        return BackupItem(
            name = values.name,
            receivedAt = values.receivedAt,
            givenAmount = values.givenAmount,
            unit = values.unit,
            expiresAt = values.expiresAt,
            stockAmount = values.stockAmount,
            createdAt = item.optLong("createdAt", 0L),
        )
    }

    private fun date(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: Exception) {
        null
    }
}

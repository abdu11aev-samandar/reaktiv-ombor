package uz.reaktivombor.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uz.reaktivombor.data.Reagent
import java.time.LocalDate

class BackupException(message: String) : Exception(message)

/** Zaxira fayli (JSON). Telefon almashtirilganda yoki ilova qayta o‘rnatilganda ro‘yxatni tiklash uchun. */
object Backup {
    const val FORMAT = "reaktiv-ombor"
    const val VERSION = 1

    fun toJson(reagents: List<Reagent>, exportedAt: String): String {
        val items = JSONArray()
        reagents.forEach { r ->
            items.put(
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
            .put("reagents", items)
            .toString(2)
    }

    data class Parsed(val reagents: List<Reagent>, val skipped: Int)

    /**
     * Faylni o‘qiydi. Har bir qator ilovadagi forma qoidalari bilan tekshiriladi;
     * buzuq qatorlar tashlab ketiladi va soni qaytariladi.
     */
    fun fromJson(text: String, today: LocalDate): Parsed {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException("Bu fayl zaxira nusxa emas.")
        }
        if (root.optString("format") != FORMAT) throw BackupException("Bu fayl «Reaktivlar» ilovasining zaxira nusxasi emas.")
        if (root.optInt("version", 0) > VERSION) throw BackupException("Zaxira ilovaning yangiroq versiyasida olingan. Ilovani yangilang.")

        val items = root.optJSONArray("reagents") ?: throw BackupException("Zaxira faylida reaktivlar ro‘yxati yo‘q.")
        val reagents = mutableListOf<Reagent>()
        var skipped = 0

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i)
            val reagent = item?.let { parseItem(it, today) }
            if (reagent == null) skipped++ else reagents += reagent
        }
        return Parsed(reagents, skipped)
    }

    private fun parseItem(item: JSONObject, today: LocalDate): Reagent? {
        val input = ReagentInput(
            name = item.optString("name"),
            receivedAt = date(item.optString("receivedAt")),
            expiresAt = date(item.optString("expiresAt")),
            givenAmount = item.optString("givenAmount"),
            stockAmount = item.optString("stockAmount"),
            unit = item.optString("unit"),
        )
        val values = (ReagentValidator.validate(input) as? Validation.Valid)?.values ?: return null
        return Reagent(
            name = values.name,
            receivedAt = values.receivedAt,
            givenAmount = values.givenAmount,
            unit = values.unit,
            expiresAt = values.expiresAt,
            stockAmount = values.stockAmount,
            createdAt = item.optLong("createdAt", 0L),
            // Tiklangan ro‘yxat uchun eski eslatmalar qayta kelmasin.
            notifiedStage = AlertStage.of(values.expiresAt, today).ordinal,
        )
    }

    private fun date(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: Exception) {
        null
    }
}

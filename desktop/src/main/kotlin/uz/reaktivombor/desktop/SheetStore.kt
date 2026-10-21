package uz.reaktivombor.desktop

import org.json.JSONArray
import org.json.JSONObject
import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.Backup
import uz.reaktivombor.domain.BackupItem
import uz.reaktivombor.domain.daysUntil
import uz.reaktivombor.domain.expiryHint
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Ma’lumotlar fayli. Telefon ilovasining zaxira formatida yoziladi (“reagents”),
 * shuning uchun shu faylni telefonga ko‘chirib «Zaxiradan tiklash» qilish mumkin.
 * Hali to‘liq to‘ldirilmagan qatorlar “drafts” bo‘limida saqlanadi (telefon ularni e’tiborsiz qoldiradi).
 */
class SheetStore(val file: Path) {
    val backupFile: Path = file.resolveSibling(file.fileName.toString().removeSuffix(".json") + ".bak.json")

    data class Loaded(val rows: List<SheetRow>, val warning: String? = null)

    /** O‘qilgan fayl: qatorlar va tashlab ketilgan buzuq qatorlar soni. */
    data class Parsed(val rows: List<SheetRow>, val skipped: Int)

    fun load(): Loaded {
        if (!Files.exists(file)) return Loaded(emptyList())
        val main = runCatching { read(file) }
        if (main.isSuccess) return Loaded(main.getOrThrow())

        // Fayl buzilgan: uni o‘chirmaymiz, yoniga nusxa qilib qo‘yamiz va oldingi saqlangan holatni ochamiz.
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val broken = file.resolveSibling("reaktivlar-buzuq-$stamp.json")
        runCatching { Files.copy(file, broken, StandardCopyOption.REPLACE_EXISTING) }
        val fromBackup = if (Files.exists(backupFile)) runCatching { read(backupFile) }.getOrNull() else null
        return if (fromBackup != null) {
            Loaded(fromBackup, "Ma’lumotlar fayli buzilgan edi — oxirgi zaxira nusxadan tiklandi. Buzilgan fayl: ${broken.fileName}")
        } else {
            Loaded(emptyList(), "Ma’lumotlar faylini o‘qib bo‘lmadi. U ${broken.fileName} nomi bilan saqlab qo‘yildi.")
        }
    }

    /** Avtomatik saqlash (fon oqimi) va oyna yopilishidagi saqlash bir vaqtda kelishi mumkin — navbat bilan. */
    @Synchronized
    fun save(rows: List<SheetRow>) {
        Files.createDirectories(file.parent)
        val json = toJson(rows, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, json, StandardCharsets.UTF_8)
        // Oldingi holat nusxasi — imkon bo‘lsa. U yozilmasa ham (band, faqat o‘qish uchun) asosiy fayl saqlanadi.
        runCatching { if (Files.exists(file)) Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING) }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun read(path: Path): List<SheetRow> = fromJson(Files.readString(path, StandardCharsets.UTF_8))

    companion object {
        const val APP_NAME = "Reaktivlar"

        /** Windows: %APPDATA%\Reaktivlar, macOS: ~/Library/Application Support/Reaktivlar, Linux: ~/.local/share/reaktivlar. */
        fun defaultFile(): Path {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val dir = when {
                os.contains("win") -> Paths.get(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", APP_NAME)
                os.contains("mac") -> Paths.get(home, "Library", "Application Support", APP_NAME)
                else -> Paths.get(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "reaktivlar")
            }
            return dir.resolve("reaktivlar.json")
        }

        fun toJson(rows: List<SheetRow>, exportedAt: String): String {
            val complete = rows.mapNotNull { row -> row.toBackupItem()?.let { row.id to it } }
            val completeIds = complete.map { it.first }.toSet()
            val root = Backup.toJsonObject(complete.map { it.second }, exportedAt)
            val drafts = JSONArray()
            rows.filter { r -> !r.isBlank && r.id !in completeIds }.forEach { r ->
                drafts.put(
                    JSONObject()
                        .put("name", r.name)
                        .put("receivedAt", r.receivedAt)
                        .put("givenAmount", r.givenAmount)
                        .put("unit", r.unit)
                        .put("expiresAt", r.expiresAt)
                        .put("stockAmount", r.stockAmount)
                        .put("createdAt", r.createdAt),
                )
            }
            root.put("drafts", drafts)
            return root.toString(2)
        }

        fun fromJson(text: String): List<SheetRow> = parse(text).rows

        /** Ma’lumotlar faylini yoki telefon zaxirasini qatorlarga aylantiradi (yaratilgan tartibda). */
        fun parse(text: String): Parsed {
            val root = Backup.parseRoot(text)
            val parsed = Backup.fromJsonObject(root)
            val rows = mutableListOf<Pair<Long, SheetRow>>()
            parsed.items.forEach { rows += it.createdAt to it.toSheetRow(0) }
            root.optJSONArray("drafts")?.let { drafts ->
                for (i in 0 until drafts.length()) {
                    val d = drafts.optJSONObject(i) ?: continue
                    val createdAt = d.optLong("createdAt", 0L)
                    rows += createdAt to SheetRow(
                        id = 0,
                        name = d.optString("name"),
                        receivedAt = d.optString("receivedAt"),
                        givenAmount = d.optString("givenAmount"),
                        unit = d.optString("unit", "gr"),
                        expiresAt = d.optString("expiresAt"),
                        stockAmount = d.optString("stockAmount"),
                        createdAt = createdAt,
                    )
                }
            }
            // Bir xil vaqtli qatorlar keyingi saqlash–ochishda o‘rin almashmasin: vaqtlar qat’iy o‘sadigan qilinadi.
            var last = Long.MIN_VALUE
            val ordered = rows.withIndex()
                .sortedWith(compareBy({ it.value.first }, { it.index }))
                .mapIndexed { index, item ->
                    val createdAt = if (item.value.first > last) item.value.first else last + 1
                    last = createdAt
                    item.value.second.copy(id = index + 1L, createdAt = createdAt)
                }
            return Parsed(ordered, parsed.skipped)
        }

        /** Excel uchun CSV: nuqta-vergul bilan ajratilgan, UTF-8 BOM bilan (o‘zbekcha harflar to‘g‘ri chiqadi). */
        fun toCsv(rows: List<SheetRow>, today: LocalDate): String {
            val sb = StringBuilder("﻿")
            sb.append(listOf("№", "Reaktiv nomi", "Kelgan vaqt", "Berilgan miqdor", "Birlik", "Yaroqlilik muddati", "Ombordagi miqdor", "Holat", "Qoldi")
                .joinToString(";") { csv(it) }).append("\r\n")
            rows.forEachIndexed { index, row ->
                val check = row.check()
                val values = check.values
                val status = check.status(today)
                val cells = listOf(
                    (index + 1).toString(),
                    row.name.trim(),
                    displayDate(row.receivedAt),
                    plainAmount(row.givenAmount),
                    row.unit,
                    displayDate(row.expiresAt),
                    plainAmount(row.stockAmount),
                    status?.label ?: "Tugallanmagan",
                    values?.let { expiryHint(daysUntil(it.expiresAt, today)) } ?: "",
                )
                sb.append(cells.joinToString(";") { csv(it) }).append("\r\n")
            }
            return sb.toString()
        }

        /** Excel uchun son: guruhlash bo‘shliqsiz, o‘nli qism vergul bilan ("1250,5"). */
        private fun plainAmount(raw: String): String =
            Amounts.parse(raw)?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: raw

        private fun csv(value: String): String =
            if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}

fun SheetRow.toBackupItem(): BackupItem? {
    val values = check().values ?: return null
    return BackupItem(
        name = values.name,
        receivedAt = values.receivedAt,
        givenAmount = values.givenAmount,
        unit = values.unit,
        expiresAt = values.expiresAt,
        stockAmount = values.stockAmount,
        createdAt = createdAt,
    )
}

fun BackupItem.toSheetRow(id: Long) = SheetRow(
    id = id,
    name = name,
    receivedAt = DateText.format(receivedAt),
    givenAmount = Amounts.forInput(givenAmount),
    unit = unit,
    expiresAt = DateText.format(expiresAt),
    stockAmount = Amounts.forInput(stockAmount),
    createdAt = createdAt,
)

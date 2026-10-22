package uz.reaktivombor.desktop

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.Backup
import uz.reaktivombor.domain.BackupException
import uz.reaktivombor.domain.BackupItem
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Ma’lumotlar fayli (JSON, 2-versiya): har bir qator yozilganidek — to‘g‘ri sana va son bo‘lsa mashina
 * ko‘rinishida (2026-10-17, 1250.5), aks holda matn holida, shuning uchun tugallanmagan qatorlar ham yo‘qolmaydi.
 * 1-versiyadagi fayllar (oldingi jadval va telefon zaxirasi) o‘qilganda yangi jadvalga o‘tkaziladi.
 *
 * Yangi jadval alohida faylda ([FILE_NAME]); 1.0.5 gacha bo‘lgan dastur fayli ([LEGACY_FILE_NAME]) faqat o‘qiladi.
 * Shunda eski dastur yana ochilsa ham (masalan, portable nusxa) u yangi jadvalni buzuq deb bo‘shatib qo‘ya olmaydi.
 */
class SheetStore(val file: Path) {
    val backupFile: Path = file.resolveSibling(file.fileName.toString().removeSuffix(".json") + ".bak.json")
    val legacyFile: Path = file.resolveSibling(LEGACY_FILE_NAME)

    data class Loaded(val rows: List<SheetRow>, val warning: String? = null)

    /** O‘qilgan fayl: qatorlar, tashlab ketilgan buzuq qatorlar soni va eski (1-versiya) fayl ekani. */
    data class Parsed(val rows: List<SheetRow>, val skipped: Int, val migrated: Boolean = false)

    fun load(): Loaded {
        if (!Files.exists(file)) return loadLegacy()
        val main = runCatching { parse(Files.readString(file, StandardCharsets.UTF_8)) }
        main.getOrNull()?.let { parsed ->
            val notes = buildList {
                if (parsed.migrated) add(MIGRATED_NOTE)
                if (legacyFile != file && isNewer(legacyFile, file)) {
                    add(
                        "Eski dastur (1.0.5 gacha) ham ishlatilgan: undagi o‘zgarishlar ${legacyFile.fileName} faylida qoldi, bu jadvalga kirmagan. " +
                            "Kerak bo‘lsa, «Fayl» → «Zaxira faylini ochish…» bilan qo‘shib oling.",
                    )
                }
            }
            return Loaded(parsed.rows, notes.joinToString(" ").ifEmpty { null })
        }

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

    private var savedRevision = Int.MIN_VALUE

    /**
     * Avtomatik saqlash (fon oqimi) va oyna yopilishidagi saqlash bir vaqtda kelishi mumkin — navbat bilan.
     * [revision] — jadval holatining raqami (SheetState.revision): kechikib qolgan eskiroq holat yangisining ustidan yozilmaydi.
     */
    @Synchronized
    fun save(rows: List<SheetRow>, revision: Int? = null) {
        if (revision != null && revision <= savedRevision) return
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
        if (revision != null) savedRevision = revision
    }

    /** Yangi jadval hali yo‘q: eski dastur faylidan (yoki uning zaxirasidan) o‘tkazamiz. Eski fayl o‘zgartirilmaydi. */
    private fun loadLegacy(): Loaded {
        if (legacyFile == file) return Loaded(emptyList())
        val candidates = listOf(legacyFile, legacyFile.resolveSibling(LEGACY_BACKUP_NAME)).filter { Files.exists(it) }
        for (path in candidates) {
            val parsed = runCatching { parse(Files.readString(path, StandardCharsets.UTF_8)) }.getOrNull() ?: continue
            return Loaded(parsed.rows, "$MIGRATED_NOTE Eski fayl o‘zgarishsiz qoldi: ${path.fileName}.")
        }
        return if (candidates.isEmpty()) {
            Loaded(emptyList())
        } else {
            Loaded(emptyList(), "Eski ma’lumotlar faylini (${legacyFile.fileName}) o‘qib bo‘lmadi. U o‘zgartirilmadi.")
        }
    }

    private fun isNewer(a: Path, b: Path): Boolean =
        runCatching { Files.exists(a) && Files.getLastModifiedTime(a) > Files.getLastModifiedTime(b) }.getOrDefault(false)

    private fun read(path: Path): List<SheetRow> = fromJson(Files.readString(path, StandardCharsets.UTF_8))

    companion object {
        const val APP_NAME = "Reaktivlar"
        const val VERSION = 2
        const val FILE_NAME = "reaktivlar-jadval.json"
        const val LEGACY_FILE_NAME = "reaktivlar.json"
        private const val LEGACY_BACKUP_NAME = "reaktivlar.bak.json"

        private const val MIGRATED_NOTE = "Jadval yangi ko‘rinishga o‘tkazildi. «Ishlab chiqarilgan vaqti» ustunini to‘ldiring — " +
            "eski «Kelgan vaqt» o‘sha katak ustiga borilganda ko‘rinadi."

        /** Windows: %APPDATA%\Reaktivlar, macOS: ~/Library/Application Support/Reaktivlar, Linux: ~/.local/share/reaktivlar. */
        fun defaultFile(): Path {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val dir = when {
                os.contains("win") -> Paths.get(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", APP_NAME)
                os.contains("mac") -> Paths.get(home, "Library", "Application Support", APP_NAME)
                else -> Paths.get(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "reaktivlar")
            }
            return dir.resolve(FILE_NAME)
        }

        fun toJson(rows: List<SheetRow>, exportedAt: String): String {
            val array = JSONArray()
            rows.filterNot { it.isBlank }.forEach { row ->
                array.put(
                    JSONObject()
                        .put("name", row.name)
                        .put("producedAt", isoOrRaw(row.producedAt))
                        .put("receivedAmount", plainOrRaw(row.receivedAmount))
                        .put("unit", row.unit)
                        .put("expiresAt", isoOrRaw(row.expiresAt))
                        .put("stockAmount", plainOrRaw(row.stockAmount))
                        .put("department", row.department)
                        .put("issuedAmount", plainOrRaw(row.issuedAmount))
                        .put("createdAt", row.createdAt)
                        .apply { if (row.legacyReceivedAt.isNotBlank()) put("receivedAt", isoOrRaw(row.legacyReceivedAt)) },
                )
            }
            return JSONObject()
                .put("format", Backup.FORMAT)
                .put("version", VERSION)
                .put("exportedAt", exportedAt)
                .put("rows", array)
                .toString(2)
        }

        fun fromJson(text: String): List<SheetRow> = parse(text).rows

        /** Ma’lumotlar faylini yoki zaxira nusxani qatorlarga aylantiradi (yaratilgan tartibda). */
        fun parse(text: String): Parsed {
            val root = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw BackupException("Bu fayl zaxira nusxa emas.")
            }
            if (root.optString("format") != Backup.FORMAT) throw BackupException("Bu fayl «Reaktivlar» dasturining zaxira nusxasi emas.")
            val version = root.optInt("version", 0)
            if (version > VERSION) throw BackupException("Zaxira dasturning yangiroq versiyasida olingan. Dasturni yangilang.")

            val (rows, skipped) = if (version >= 2) readRows(root) else readVersion1(root)
            // Bir xil vaqtli qatorlar keyingi saqlash–ochishda o‘rin almashmasin: vaqtlar qat’iy o‘sadigan qilinadi.
            var last = Long.MIN_VALUE
            val ordered = rows.withIndex()
                .sortedWith(compareBy({ it.value.createdAt }, { it.index }))
                .mapIndexed { index, item ->
                    val createdAt = if (item.value.createdAt > last) item.value.createdAt else last + 1
                    last = createdAt
                    item.value.copy(id = index + 1L, createdAt = createdAt)
                }
            return Parsed(ordered, skipped, migrated = version < 2)
        }

        private fun readRows(root: JSONObject): Pair<List<SheetRow>, Int> {
            val array = root.optJSONArray("rows") ?: throw BackupException("Zaxira faylida jadval qatorlari yo‘q.")
            val rows = mutableListOf<SheetRow>()
            var skipped = 0
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item == null) {
                    skipped++
                    continue
                }
                rows += SheetRow(
                    id = 0,
                    name = item.optString("name"),
                    producedAt = displayDate(item.optString("producedAt")),
                    receivedAmount = inputAmount(item.optString("receivedAmount")),
                    unit = normalizeUnit(item.optString("unit", "g")),
                    expiresAt = displayDate(item.optString("expiresAt")),
                    stockAmount = inputAmount(item.optString("stockAmount")),
                    department = normalizeDepartment(item.optString("department")),
                    issuedAmount = inputAmount(item.optString("issuedAmount")),
                    createdAt = item.optLong("createdAt", 0L),
                    legacyReceivedAt = displayDate(item.optString("receivedAt")),
                )
            }
            return rows to skipped
        }

        /**
         * Oldingi jadval (va telefon zaxirasi): «Kelgan vaqt, Berilgan miqdor, Birlik, Yaroqlilik muddati, Ombordagi miqdor».
         * Eski «Berilgan miqdor» omborga kelgan miqdor edi — u «Kelgan miqdor» bo‘ladi. «Kelgan vaqt» ustuni yo‘q:
         * qiymati faylda saqlanadi va «Ishlab chiqarilgan vaqti» katagida eslatma sifatida ko‘rinadi.
         */
        private fun readVersion1(root: JSONObject): Pair<List<SheetRow>, Int> {
            val parsed = Backup.fromJsonObject(root)
            val rows = parsed.items.map { it.toSheetRow() }.toMutableList()
            root.optJSONArray("drafts")?.let { drafts ->
                for (i in 0 until drafts.length()) {
                    val d = drafts.optJSONObject(i) ?: continue
                    rows += SheetRow(
                        id = 0,
                        name = d.optString("name"),
                        receivedAmount = d.optString("givenAmount"),
                        unit = normalizeUnit(d.optString("unit", "gr")),
                        expiresAt = d.optString("expiresAt"),
                        stockAmount = d.optString("stockAmount"),
                        createdAt = d.optLong("createdAt", 0L),
                        legacyReceivedAt = d.optString("receivedAt"),
                    )
                }
            }
            return rows to parsed.skipped
        }

        private fun BackupItem.toSheetRow() = SheetRow(
            id = 0,
            name = name,
            receivedAmount = Amounts.forInput(givenAmount),
            unit = normalizeUnit(unit),
            expiresAt = DateText.format(expiresAt),
            stockAmount = Amounts.forInput(stockAmount),
            createdAt = createdAt,
            legacyReceivedAt = DateText.format(receivedAt),
        )

        /** Excel uchun CSV: nuqta-vergul bilan ajratilgan, UTF-8 BOM bilan (o‘zbekcha harflar to‘g‘ri chiqadi). */
        fun toCsv(rows: List<SheetRow>, today: LocalDate): String {
            val sb = StringBuilder("﻿")
            val header = listOf("№") + Col.entries.map { it.title } + "Holat"
            sb.append(header.joinToString(";") { csv(it) }).append("\r\n")
            rows.forEachIndexed { index, row ->
                val check = row.check(today)
                val cells = listOf((index + 1).toString()) + Col.entries.map { col ->
                    when (col) {
                        Col.NAME -> row.name.trim()
                        Col.REMAINDER -> check.remainder?.let { plainAmount(it.toPlainString()) } ?: ""
                        Col.DAYS_LEFT -> check.daysLeft(today)?.toString() ?: ""
                        else -> if (col.kind == Col.Kind.AMOUNT) plainAmount(row[col]) else cellText(row, col, check, today)
                    }
                } + holat(check, today)
                sb.append(cells.joinToString(";") { csv(it) }).append("\r\n")
            }
            return sb.toString()
        }

        /** «Holat»: rang ekrandagidek yaroqlilik muddatidan; qator to‘liq bo‘lmasa bu ham yoziladi. */
        private fun holat(check: RowCheck, today: LocalDate): String {
            val status = check.status(today)?.label
            return when {
                status == null -> "Tugallanmagan"
                check.complete -> status
                else -> "$status (tugallanmagan)"
            }
        }

        private fun isoOrRaw(raw: String): String = DateText.parse(raw)?.toString() ?: raw

        private fun plainOrRaw(raw: String): String = Amounts.parse(raw)?.stripTrailingZeros()?.toPlainString() ?: raw

        private fun inputAmount(raw: String): String = Amounts.parse(raw)?.let(Amounts::forInput) ?: raw

        /** Excel uchun son: guruhlash bo‘shliqsiz, o‘nli qism vergul bilan ("1250,5"). */
        private fun plainAmount(raw: String): String =
            Amounts.parse(raw)?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: raw

        private fun csv(value: String): String =
            if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}

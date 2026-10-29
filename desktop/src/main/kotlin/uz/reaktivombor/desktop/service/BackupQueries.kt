package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.db.Schema
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.db.queryOne
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime

/** Ishlab turgan baza haqida: fayl, hajmi, SQLite versiyasi va yozuvlar soni (zaxira va sozlamalar sahifalari uchun). */
data class DatabaseInfo(
    val path: Path,
    val size: Long,
    val sqliteVersion: String,
    val userVersion: Int,
    val reagents: Long,
    val batches: Long,
    val movements: Long,
)

/** Oxirgi eski-dasturdan ko‘chirish: qachon va qaysi fayldan (app_settings.legacy_import). */
data class LegacyImportInfo(val at: LocalDateTime?, val fileName: String)

/**
 * «Ma’lumotlarni zaxiralash» va «Sozlamalar» sahifalarining o‘qish so‘rovlari (faqat o‘qiydi, hech narsa yozmaydi).
 * Queries.kt ga tegmaslik uchun alohida: sahifa `BackupQueries(state.services)` ni o‘zi yaratadi.
 */
class BackupQueries(private val services: Services) {

    /** Joriy baza: yo‘li, fayl hajmi, SQLite va sxema versiyasi, reaktiv / partiya / harakat yozuvlari soni. */
    fun databaseInfo(): DatabaseInfo = services.db.read { conn ->
        val path = services.db.path
        DatabaseInfo(
            path = path,
            size = runCatching { Files.size(path) }.getOrDefault(0L),
            sqliteVersion = conn.queryOne("SELECT sqlite_version()") { it.getString(1) }.orEmpty(),
            userVersion = Schema.userVersion(conn),
            reagents = conn.queryLong("SELECT COUNT(*) FROM reagents"),
            batches = conn.queryLong("SELECT COUNT(*) FROM batches"),
            movements = conn.queryLong("SELECT COUNT(*) FROM stock_movements"),
        )
    }

    /** Nechta jadvalning ustunlar ko‘rinishi saqlangan (standart holatdan farq qiladi). */
    fun savedTableLayouts(): Int = services.db.read { conn ->
        conn.queryLong("SELECT COUNT(*) FROM app_settings WHERE key LIKE 'table\\_layout.%' ESCAPE '\\'").toInt()
    }

    /** Oxirgi ko‘chirish (bo‘lmagan bo‘lsa null). Qiymat: "<vaqt>|<fayl nomi>". */
    fun lastLegacyImport(): LegacyImportInfo? {
        val raw = services.settings.get(Settings.LEGACY_IMPORT)?.takeIf { it.isNotBlank() } ?: return null
        val time = raw.substringBefore('|')
        val file = if ('|' in raw) raw.substringAfter('|') else ""
        return LegacyImportInfo(runCatching { LocalDateTime.parse(time) }.getOrNull(), file)
    }

    /**
     * Shu fayl allaqachon ko‘chirilganmi: [LegacyImport] bilan bir xil — fayl matnining SHA-256 xeshi
     * `legacy_import_hashes` da bormi. Faylni o‘qib bo‘lmasa — false (ko‘chirishda xato aniq aytiladi).
     */
    fun legacyAlreadyImported(path: Path): Boolean {
        val text = runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrNull() ?: return false
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val done = services.settings.get(Settings.LEGACY_IMPORT_HASHES).orEmpty().split(',')
        return hash in done
    }
}

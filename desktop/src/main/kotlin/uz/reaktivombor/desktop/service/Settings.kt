package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.queryOne
import uz.reaktivombor.desktop.db.update
import java.sql.Connection
import java.time.LocalDate

/** app_settings: dastur sozlamalari va jadval ustunlari joylashuvi (har bir jadval uchun JSON). */
class Settings(private val db: Database, private val audit: Audit) {

    companion object {
        const val ORG_NAME = "org_name"
        const val ACTOR_NAME = "actor_name"
        const val AUTO_BACKUP_KEEP = "auto_backup_keep"
        const val LAST_AUTO_BACKUP = "last_auto_backup"
        const val LEGACY_IMPORT = "legacy_import"
        const val LEGACY_IMPORT_HASHES = "legacy_import_hashes"

        /** Oxirgi eski ma’lumotlar ko‘chirishining barcha izohlari (qatorma-qator). */
        const val LEGACY_IMPORT_NOTES = "legacy_import_notes"
        private const val TABLE_LAYOUT_PREFIX = "table_layout."

        const val DEFAULT_AUTO_BACKUP_KEEP = 14
        val AUTO_BACKUP_KEEP_RANGE = 1..365
    }

    fun get(key: String): String? = db.read { conn -> get(conn, key) }

    internal fun get(conn: Connection, key: String): String? =
        conn.queryOne("SELECT value FROM app_settings WHERE key = ?", key) { it.getString(1) }

    internal fun put(conn: Connection, key: String, value: String?) {
        if (value == null) {
            conn.update("DELETE FROM app_settings WHERE key = ?", key)
        } else {
            conn.update(
                "INSERT INTO app_settings(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                key, value,
            )
        }
    }

    /** Hisobotlar sarlavhasidagi tashkilot nomi. */
    var orgName: String
        get() = get(ORG_NAME).orEmpty()
        set(value) = change(ORG_NAME, shortText(value, "Tashkilot nomi", 200, "orgName"), "Tashkilot nomi")

    /** Formalarda taklif qilinadigan xodim ismi; audit jurnalida «kim» sifatida yoziladi. */
    var actorName: String
        get() = get(ACTOR_NAME).orEmpty()
        set(value) = change(ACTOR_NAME, shortText(value, "Xodim ismi", 100, "actorName"), "Xodim ismi")

    /** Nechta avtomatik zaxira nusxa saqlanadi (eng yangilari). */
    var autoBackupKeep: Int
        get() = get(AUTO_BACKUP_KEEP)?.toIntOrNull()?.takeIf { it in AUTO_BACKUP_KEEP_RANGE } ?: DEFAULT_AUTO_BACKUP_KEEP
        set(value) {
            if (value !in AUTO_BACKUP_KEEP_RANGE) {
                throw UserError(
                    "Avtomatik zaxiralar soni ${AUTO_BACKUP_KEEP_RANGE.first} dan ${AUTO_BACKUP_KEEP_RANGE.last} gacha bo‘lishi kerak.",
                    "autoBackupKeep",
                )
            }
            change(AUTO_BACKUP_KEEP, value.toString(), "Avtomatik zaxiralar soni")
        }

    /** Oxirgi avtomatik zaxira sanasi (kuniga bir marta). */
    var lastAutoBackup: LocalDate?
        get() = get(LAST_AUTO_BACKUP)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) {
            db.write { conn -> put(conn, LAST_AUTO_BACKUP, value?.toString()) }
        }

    fun tableLayout(id: String): String? = get(TABLE_LAYOUT_PREFIX + id)

    /** null — standart holatga qaytarish. */
    fun setTableLayout(id: String, json: String?) {
        db.write { conn -> put(conn, TABLE_LAYOUT_PREFIX + id, json) }
    }

    /** Hamma jadvallarning ustunlari standart holatga. */
    fun resetTableLayouts() {
        db.write { conn ->
            conn.update("DELETE FROM app_settings WHERE key LIKE ?", "$TABLE_LAYOUT_PREFIX%")
            audit.log(conn, AuditAction.SETTINGS_CHANGED, "settings", null, "Jadval ustunlari standart holatga qaytarildi")
        }
    }

    private fun change(key: String, value: String, label: String) {
        db.write { conn ->
            val old = get(conn, key)
            if (old != value) {
                put(conn, key, value)
                audit.log(conn, AuditAction.SETTINGS_CHANGED, "settings", null, "$label: «${old.orEmpty()}» → «$value»")
            }
        }
    }
}

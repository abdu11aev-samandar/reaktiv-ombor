package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.Schema
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.db.queryOne
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.name

enum class BackupKind(val label: String) {
    AUTO("Avtomatik"),
    PRE_RESTORE("Tiklashdan oldingi"),
    MANUAL("Qo‘lda olingan"),
}

data class BackupFile(val path: Path, val kind: BackupKind, val size: Long, val modifiedAt: LocalDateTime)

/** Tekshiruvdan o‘tgan zaxira fayli haqida. */
data class BackupCheck(val userVersion: Int, val reagents: Long, val batches: Long, val movements: Long)

data class RestoreResult(val preRestoreBackup: Path, val check: BackupCheck)

/**
 * Zaxira nusxalar. Nusxa `VACUUM INTO` bilan olinadi (ishlab turgan bazadan ham to‘liq, izchil nusxa), so‘ng tekshiriladi.
 * Tiklash: tekshirish → joriy bazaning avtomatik nusxasi → fayl almashtiriladi → qayta ochiladi. Xato bo‘lsa — eski fayl
 * joyiga qaytariladi: dastur hech qachon bazasiz qolmaydi.
 */
class Backup(
    private val db: Database,
    private val audit: Audit,
    private val settings: Settings,
    /** `<ma’lumotlar papkasi>/backups`. */
    val dir: Path,
    private val clock: Clock,
) {
    companion object {
        private const val AUTO_PREFIX = "auto-"
        private const val PRE_RESTORE_PREFIX = "before-restore-"
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val log: Logger = Logger.getLogger("uz.reaktivombor.service.Backup")
    }

    /** Nusxa [target] ga (bor bo‘lsa — almashtiriladi). Avval vaqtinchalik faylga yoziladi va tekshiriladi. */
    fun create(target: Path): Path = createCopy(target.toAbsolutePath(), "Qo‘lda: ${target.name}")

    private fun createCopy(target: Path, auditText: String?): Path {
        if (sameFile(target, db.path)) throw UserError("Zaxira nusxani ishlab turgan baza fayli ustiga yozib bo‘lmaydi.")
        val parent = target.parent ?: throw UserError("Zaxira nusxa uchun papka tanlang.")
        try {
            Files.createDirectories(parent)
        } catch (e: IOException) {
            throw UserError("Papkani yaratib bo‘lmadi: $parent (${e.message}).")
        }
        val temp = parent.resolve(".${target.name}.${System.nanoTime()}.tmp")
        try {
            db.read { conn ->
                conn.prepareStatement("VACUUM INTO ?").use { st ->
                    st.setString(1, temp.toString())
                    st.execute()
                }
            }
            validate(temp)
            moveReplacing(temp, target)
        } catch (e: UserError) {
            Files.deleteIfExists(temp)
            throw e
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(temp) }
            log.log(Level.WARNING, "backup failed", e)
            throw UserError("Zaxira nusxani yozib bo‘lmadi: ${e.message ?: e.javaClass.simpleName}. Papka va bo‘sh joyni tekshiring.")
        }
        if (auditText != null) {
            db.write { conn -> audit.log(conn, AuditAction.BACKUP_CREATED, "backup", null, "$auditText (${target.toAbsolutePath()})") }
        }
        log.info("backup created")
        return target
    }

    /**
     * Kuniga bir marta `backups/auto-YYYY-MM-DD.db`; eng yangi [Settings.autoBackupKeep] tasi qoladi.
     * Nusxa olingan bo‘lsa — uning yo‘li, bugun allaqachon olingan bo‘lsa — null.
     */
    fun autoBackupIfDue(today: LocalDate): Path? {
        val target = dir.resolve("$AUTO_PREFIX$today.db")
        val created = if (settings.lastAutoBackup == today && Files.exists(target)) {
            null
        } else {
            if (!Files.exists(target)) createCopy(target, "Avtomatik zaxira") else null
        }
        settings.lastAutoBackup = today
        prune(settings.autoBackupKeep)
        return created
    }

    private fun prune(keep: Int) {
        val autos = list().filter { it.kind == BackupKind.AUTO }.sortedByDescending { it.path.name }
        autos.drop(keep).forEach { file ->
            runCatching { Files.deleteIfExists(file.path) }.onFailure { log.log(Level.WARNING, "old auto backup not deleted", it) }
        }
    }

    /** `backups/` papkasidagi nusxalar, yangisi birinchi. */
    fun list(): List<BackupFile> {
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.name.endsWith(".db") && !it.name.startsWith(".") }
                .map { path ->
                    val name = path.name
                    BackupFile(
                        path = path,
                        kind = when {
                            name.startsWith(AUTO_PREFIX) -> BackupKind.AUTO
                            name.startsWith(PRE_RESTORE_PREFIX) -> BackupKind.PRE_RESTORE
                            else -> BackupKind.MANUAL
                        },
                        size = Files.size(path),
                        modifiedAt = LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault()),
                    )
                }
                .toList()
        }.sortedWith(compareByDescending<BackupFile> { it.modifiedAt }.thenByDescending { it.path.name })
    }

    /**
     * Faylni faqat o‘qish uchun ochib tekshiradi: SQLite fayli, `integrity_check`, Reaktiv Control belgisi,
     * versiyasi dasturnikidan katta emas. Yaroqsiz bo‘lsa — [UserError].
     */
    fun validate(path: Path): BackupCheck {
        if (!Files.isRegularFile(path)) throw UserError("Fayl topilmadi: $path")
        val header = ByteArray(SQLITE_HEADER.size)
        val read = try {
            Files.newInputStream(path).use { it.readNBytes(header, 0, header.size) }
        } catch (e: IOException) {
            throw UserError("Faylni o‘qib bo‘lmadi: ${e.message}")
        }
        if (read < header.size || !header.contentEquals(SQLITE_HEADER)) {
            throw UserError("Bu fayl Reaktiv Control zaxira nusxasi emas.")
        }
        try {
            Database.connect(path, readOnly = true).use { conn ->
                val integrity = conn.queryOne("PRAGMA integrity_check") { it.getString(1) }
                if (integrity != "ok") throw UserError("Zaxira fayli buzilgan (tekshiruv: $integrity).")
                val version = Schema.userVersion(conn)
                if (version > Schema.VERSION) {
                    throw UserError("Zaxira dasturning yangiroq versiyasida olingan (versiya $version). Dasturni yangilang.")
                }
                val marker = runCatching {
                    conn.queryOne("SELECT value FROM app_settings WHERE key = 'app'") { it.getString(1) }
                }.getOrNull()
                if (version < 1 || marker != Schema.APP_MARKER) throw UserError("Bu fayl Reaktiv Control zaxira nusxasi emas.")
                return BackupCheck(
                    userVersion = version,
                    reagents = conn.queryLong("SELECT COUNT(*) FROM reagents"),
                    batches = conn.queryLong("SELECT COUNT(*) FROM batches"),
                    movements = conn.queryLong("SELECT COUNT(*) FROM stock_movements"),
                )
            }
        } catch (e: UserError) {
            throw e
        } catch (e: Exception) {
            throw UserError("Zaxira faylini ochib bo‘lmadi yoki u buzilgan: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Zaxiradan tiklash. Avval joriy baza `backups/before-restore-<vaqt>.db` ga saqlanadi. Xato bo‘lsa — oldingi baza
     * joyiga qaytariladi va xabar beriladi.
     */
    fun restore(source: Path): RestoreResult {
        val src = source.toAbsolutePath()
        if (sameFile(src, db.path)) throw UserError("Bu fayl — ishlab turgan bazaning o‘zi.")
        val check = validate(src)
        // Nomi band bo‘lsa (bir soniyada ikki tiklash) — yangi nom: mavjud nusxa, ayniqsa tiklanayotgan fayl, ustidan yozilmaydi.
        val stamp = LocalDateTime.now(clock).format(STAMP)
        val preTarget = generateSequence(0) { it + 1 }
            .map { n -> dir.resolve(PRE_RESTORE_PREFIX + stamp + (if (n == 0) "" else "-$n") + ".db") }
            .first { !Files.exists(it) && !sameFile(it, src) }
        val pre = createCopy(preTarget, "Tiklashdan oldingi avtomatik nusxa")
        var rollback: Path? = null
        try {
            db.offline { dbPath ->
                val folder = dbPath.parent
                rollback = Files.createTempFile(folder, ".rollback-", ".db").also { Files.copy(dbPath, it, StandardCopyOption.REPLACE_EXISTING) }
                val temp = Files.createTempFile(folder, ".restore-", ".db")
                Files.copy(src, temp, StandardCopyOption.REPLACE_EXISTING)
                Files.deleteIfExists(dbPath.resolveSibling(dbPath.name + "-journal"))
                moveReplacing(temp, dbPath)
            }
            // Qayta ochildi va sxema yangilandi; ishlashini tekshiramiz.
            db.read { conn -> conn.queryOne("PRAGMA quick_check") { it.getString(1) } }.let {
                if (it != "ok") throw UserError("Tiklangan baza tekshiruvdan o‘tmadi ($it).")
            }
        } catch (e: Throwable) {
            log.log(Level.SEVERE, "restore failed, rolling back", e)
            val back = rollback
            if (back != null) {
                runCatching { db.offline { dbPath -> moveReplacing(back, dbPath) } }
                    .onFailure { log.log(Level.SEVERE, "rollback failed", it) }
            }
            if (!db.isOpen) runCatching { db.reopen() }
            throw if (e is UserError) {
                UserError("Zaxiradan tiklab bo‘lmadi: ${e.message} Oldingi ma’lumotlar joyida qoldi.")
            } else {
                UserError("Zaxiradan tiklab bo‘lmadi (${e.message ?: e.javaClass.simpleName}). Oldingi ma’lumotlar joyida qoldi.")
            }
        } finally {
            rollback?.let { runCatching { Files.deleteIfExists(it) } }
        }
        db.write { conn ->
            audit.log(conn, AuditAction.BACKUP_RESTORED, "backup", null, "Tiklandi: $src. Oldingi holat: $pre")
        }
        log.info("restore done")
        return RestoreResult(pre, check)
    }

    private fun moveReplacing(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sameFile(a: Path, b: Path): Boolean =
        a.toAbsolutePath().normalize() == b.toAbsolutePath().normalize() ||
            (Files.exists(a) && Files.exists(b) && runCatching { Files.isSameFile(a, b) }.getOrDefault(false))
}

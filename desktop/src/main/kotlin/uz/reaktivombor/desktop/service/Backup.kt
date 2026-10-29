package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.DataDir
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
import java.nio.file.StandardOpenOption
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

/**
 * [preRestoreRaw] — joriy baza buzilgan bo‘lgani uchun tiklashdan oldingi nusxa tekshiruvsiz, fayl o‘zidan (bayt-bayt) olingan.
 */
data class RestoreResult(val preRestoreBackup: Path, val check: BackupCheck, val preRestoreRaw: Boolean = false)

/**
 * Dastur ochilmay qolganda buzilgan bazani zaxiradan tiklash natijasi: [backup] — qaysi nusxadan, [brokenCopy] — buzilgan
 * fayl qayerga surildi (o‘chirilmaydi; baza fayli bo‘lmagan bo‘lsa null).
 */
data class RecoveryResult(val backup: Path, val brokenCopy: Path?, val check: BackupCheck)

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
        private const val BROKEN_PREFIX = "reaktiv-control-buzuq-"
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val log: Logger = Logger.getLogger("uz.reaktivombor.service.Backup")

        /**
         * Faylni faqat o‘qish uchun ochib tekshiradi: SQLite fayli, `integrity_check`, Reaktiv Control belgisi,
         * versiyasi dasturnikidan katta emas. Yaroqsiz bo‘lsa — [UserError]. Ochiq bazasiz ham ishlaydi (dastur ochilmaganda).
         */
        fun validateFile(path: Path): BackupCheck {
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

        /** `<ma’lumotlar papkasi>/backups` dagi tekshiruvdan o‘tgan eng yangi nusxa (yo‘q bo‘lsa null). */
        fun latestValid(dataDir: Path): Path? {
            val folder = DataDir.backupsDir(dataDir)
            if (!Files.isDirectory(folder)) return null
            val files = Files.list(folder).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.name.endsWith(".db") && !it.name.startsWith(".") }.toList()
            }
            return files
                .sortedWith(
                    compareByDescending<Path> { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
                        .thenByDescending { it.name },
                )
                .firstOrNull { runCatching { validateFile(it) }.isSuccess }
        }

        /** Baza ochilmay qolganda: eng yangi yaroqli zaxiradan tiklash ([recoverFrom]). Zaxira yo‘q bo‘lsa — [UserError]. */
        fun recoverFromLatest(dataDir: Path, clock: Clock = Clock.systemDefaultZone()): RecoveryResult {
            val backup = latestValid(dataDir)
                ?: throw UserError("Yaroqli zaxira nusxa topilmadi (${DataDir.backupsDir(dataDir)}).")
            return recoverFrom(dataDir, backup, clock)
        }

        /**
         * Baza ochilmay qolganda (fayl buzilgan): buzilgan fayl `reaktiv-control-buzuq-<vaqt>.db` nomi bilan yoniga suriladi
         * (hech qachon o‘chirilmaydi), [backup] dan yangi, yoziladigan nusxa qo‘yiladi va ochib tekshiriladi (sxema yangilanadi,
         * quick_check, audit yozuvi). Ochilmasa — buzilgan fayl joyiga qaytariladi va [UserError].
         */
        fun recoverFrom(dataDir: Path, backup: Path, clock: Clock = Clock.systemDefaultZone()): RecoveryResult {
            val check = validateFile(backup)
            Files.createDirectories(dataDir)
            val dbFile = DataDir.dbFile(dataDir)
            val broken = uniquePath(dataDir, BROKEN_PREFIX + LocalDateTime.now(clock).format(STAMP), ".db")
            val companions = listOf("-journal", "-wal", "-shm")
            val temp = Files.createTempFile(dataDir, ".recover-", ".db")
            try {
                copyFresh(backup, temp)
            } catch (e: Exception) {
                runCatching { Files.deleteIfExists(temp) }
                throw UserError("Zaxira nusxani ko‘chirib bo‘lmadi: ${e.message ?: e.javaClass.simpleName}")
            }
            // Buzilgan fayl (va jurnali) o‘chirilmaydi — yoniga suriladi.
            val movedAside = Files.exists(dbFile)
            try {
                if (movedAside) Files.move(dbFile, broken)
                for (suffix in companions) {
                    val extra = dbFile.resolveSibling(dbFile.name + suffix)
                    if (Files.exists(extra)) Files.move(extra, broken.resolveSibling(broken.name + suffix))
                }
                moveReplacing(temp, dbFile)
                Database(dbFile).use { db ->
                    val quick = db.read { conn -> conn.queryOne("PRAGMA quick_check") { it.getString(1) } }
                    if (quick != "ok") throw UserError("Tiklangan baza tekshiruvdan o‘tmadi ($quick).")
                    db.write { conn ->
                        Audit(db, clock).log(
                            conn, AuditAction.BACKUP_RESTORED, "backup", null,
                            "Baza ochilmadi (buzilgan) — zaxiradan tiklandi: $backup" +
                                (if (movedAside) ". Buzilgan fayl saqlab qo‘yildi: $broken" else ""),
                        )
                    }
                }
            } catch (e: Throwable) {
                log.log(Level.SEVERE, "recovery failed", e)
                runCatching { Files.deleteIfExists(temp) }
                if (movedAside && Files.exists(broken)) {
                    runCatching { moveReplacing(broken, dbFile) }.onFailure { log.log(Level.SEVERE, "broken file not moved back", it) }
                }
                for (suffix in companions) {
                    val aside = broken.resolveSibling(broken.name + suffix)
                    if (Files.exists(aside)) runCatching { moveReplacing(aside, dbFile.resolveSibling(dbFile.name + suffix)) }
                }
                val reason = if (e is UserError) e.message else (e.message ?: e.javaClass.simpleName)
                throw UserError("Zaxiradan tiklab bo‘lmadi: $reason")
            }
            log.info("database recovered from backup")
            return RecoveryResult(backup, if (movedAside) broken else null, check)
        }

        /** Band bo‘lmagan nom: `<asos>.db`, `<asos>-1.db`, … ([avoid] fayli ham emas). */
        private fun uniquePath(folder: Path, base: String, extension: String, avoid: Path? = null): Path =
            generateSequence(0) { it + 1 }
                .map { n -> folder.resolve(base + (if (n == 0) "" else "-$n") + extension) }
                .first { !Files.exists(it) && (avoid == null || !sameFile(it, avoid)) }

        /**
         * Yangi faylga oqim bilan nusxa. Files.copy manba atributlarini (masalan, «faqat o‘qish uchun» belgisini) ham
         * ko‘chiradi — bu yerda natija har doim yoziladigan fayl.
         */
        private fun copyFresh(from: Path, to: Path) {
            Files.newInputStream(from).use { input ->
                Files.newOutputStream(to, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                    .use { input.copyTo(it) }
            }
            runCatching {
                if ("dos" in to.fileSystem.supportedFileAttributeViews()) Files.setAttribute(to, "dos:readonly", false)
            }
            runCatching { to.toFile().setWritable(true, true) }
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

    /** Tiklashdagi fayl almashtirish (testlarda xatoni sinash uchun almashtiriladi). */
    internal var mover: (from: Path, to: Path) -> Unit = { from, to -> moveReplacing(from, to) }

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
    fun validate(path: Path): BackupCheck = validateFile(path)

    /**
     * Zaxiradan tiklash. Avval joriy baza `backups/before-restore-<vaqt>.db` ga saqlanadi; baza buzilgan bo‘lsa (VACUUM INTO
     * ishlamaydi) — fayl o‘zi bayt-bayt, ulanish yopiq paytda (`…-raw.db`, tekshiruvsiz). Fayl yangi, yoziladigan nusxa bilan
     * almashtiriladi; qayta ochish, sxema yangilash, tekshiruv va audit yozuvi (sinov yozuvi) ham himoyalangan blokda —
     * birortasi o‘xshamasa, oldingi baza joyiga qaytariladi. Qaytarib bo‘lmasa — oldingi baza nusxasi o‘chirilmaydi va
     * xabarda uning joyi aytiladi.
     */
    fun restore(source: Path): RestoreResult {
        val src = source.toAbsolutePath()
        if (sameFile(src, db.path)) throw UserError("Bu fayl — ishlab turgan bazaning o‘zi.")
        val check = validate(src)
        // Nomi band bo‘lsa (bir soniyada ikki tiklash) — yangi nom: mavjud nusxa, ayniqsa tiklanayotgan fayl, ustidan yozilmaydi.
        val stamp = LocalDateTime.now(clock).format(STAMP)
        var pre: Path? = try {
            createCopy(uniquePath(dir, PRE_RESTORE_PREFIX + stamp, ".db", avoid = src), "Tiklashdan oldingi avtomatik nusxa")
        } catch (e: Exception) {
            // Joriy baza buzilgan bo‘lishi mumkin (VACUUM INTO hamma sahifani o‘qiydi) — tiklash aynan shunda kerak.
            log.log(Level.WARNING, "pre-restore copy failed, falling back to a raw file copy", e)
            null
        }
        val raw = pre == null
        var rollback: Path? = null
        var temp: Path? = null
        var swapped = false
        try {
            db.offline { dbPath ->
                val folder = dbPath.parent
                if (raw) {
                    val target = uniquePath(dir, PRE_RESTORE_PREFIX + stamp + "-raw", ".db", avoid = src)
                    try {
                        Files.createDirectories(dir)
                        copyFresh(dbPath, target)
                        val journal = dbPath.resolveSibling(dbPath.name + "-journal")
                        if (Files.exists(journal)) copyFresh(journal, target.resolveSibling(target.name + "-journal"))
                    } catch (e: Exception) {
                        throw UserError("Joriy bazaning nusxasini olib bo‘lmadi (${e.message ?: e.javaClass.simpleName}) — tiklash bekor qilindi.")
                    }
                    pre = target
                }
                rollback = Files.createTempFile(folder, ".rollback-", ".db").also { copyFresh(dbPath, it) }
                val fresh = Files.createTempFile(folder, ".restore-", ".db")
                temp = fresh
                copyFresh(src, fresh)
                Files.deleteIfExists(dbPath.resolveSibling(dbPath.name + "-journal"))
                mover(fresh, dbPath)
                swapped = true
            }
            // Qayta ochildi va sxema yangilandi; ishlashi va yozib bo‘lishi tekshiriladi (audit yozuvi — sinov yozuvi ham).
            db.read { conn -> conn.queryOne("PRAGMA quick_check") { it.getString(1) } }.let {
                if (it != "ok") throw UserError("Tiklangan baza tekshiruvdan o‘tmadi ($it).")
            }
            db.write { conn ->
                audit.log(conn, AuditAction.BACKUP_RESTORED, "backup", null, "Tiklandi: $src. Oldingi holat: $pre")
            }
        } catch (e: Throwable) {
            log.log(Level.SEVERE, "restore failed, rolling back", e)
            // Fayl almashtirilmagan bo‘lsa — oldingi baza joyida; almashtirilgan bo‘lsa — nusxasi joyiga qaytariladi.
            var oldInPlace = !swapped
            val back = rollback
            if (swapped && back != null) {
                runCatching {
                    db.offline { dbPath ->
                        mover(back, dbPath)
                        oldInPlace = true
                    }
                }.onFailure { log.log(Level.SEVERE, "rollback failed", it) }
            }
            if (!db.isOpen) runCatching { db.reopen() }.onFailure { log.log(Level.SEVERE, "reopen after failed restore failed", it) }
            temp?.let { runCatching { Files.deleteIfExists(it) } }
            val reason = if (e is UserError) {
                "Zaxiradan tiklab bo‘lmadi: ${e.message}"
            } else {
                "Zaxiradan tiklab bo‘lmadi (${e.message ?: e.javaClass.simpleName})."
            }
            if (oldInPlace) {
                back?.let { runCatching { Files.deleteIfExists(it) } }
                throw UserError("$reason Oldingi ma’lumotlar joyida qoldi.")
            }
            // Oldingi faylni joyiga qaytarib bo‘lmadi: uning nusxasi o‘chirilmaydi, qayerdaligi aytiladi.
            throw UserError(
                "$reason Oldingi ma’lumotlarni joyiga qaytarib bo‘lmadi — ular shu faylda saqlanib qoldi: $back" +
                    (pre?.let { " (tiklashdan oldingi nusxa: $it)" } ?: "") +
                    ". Dasturni qayta ochib, shu nusxadan «Zaxiradan tiklash» bilan tiklang.",
            )
        }
        rollback?.let { runCatching { Files.deleteIfExists(it) } }
        log.info("restore done")
        return RestoreResult(pre!!, check, preRestoreRaw = raw)
    }
}

package uz.reaktivombor.desktop.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.exec
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.db.queryOne
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

class BackupServiceTest : ServiceTestBase() {

    @Test
    fun createValidateAndRestoreWithAutomaticPreRestoreBackup() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val target = tmp.root.toPath().resolve("flesh/zaxira 29.10.2026.db") // bo‘shliqli yo‘l
        services.backup.create(target)
        val check = services.backup.validate(target)
        assertEquals(1L, check.batches)
        assertEquals(1L, check.movements)

        // Nusxadan keyin yangi ma’lumot.
        issueTo("Biologiya", batch, "2", L)
        newBatch("Aseton", "A-1", today.plusDays(100), "1")
        assertEquals(litres("3"), services.stock.remainder(batch))

        val result = services.backup.restore(target)
        // Baza o‘sha obyekt bilan qayta ochildi va nusxadagi holatda.
        assertEquals(litres("5"), services.stock.remainder(batch))
        assertEquals(listOf("Etil spirti"), services.reagents.list().map { it.name })
        assertTrue(services.queries.issueItems().isEmpty())
        // Tiklashdan oldingi holat avtomatik saqlangan va to‘liq.
        assertTrue(Files.exists(result.preRestoreBackup))
        assertTrue(result.preRestoreBackup.fileName.toString().startsWith("before-restore-"))
        assertEquals(2L, services.backup.validate(result.preRestoreBackup).batches)
        assertTrue(services.backup.list().any { it.path == result.preRestoreBackup && it.kind == BackupKind.PRE_RESTORE })
        // Ishlashda davom etadi.
        issueTo("Gistologiya", batch, "1", L)
        assertEquals(litres("4"), services.stock.remainder(batch))
        assertTrue(services.queries.auditLogs().any { it.action == AuditAction.BACKUP_RESTORED })

        // Oldingi holatga qaytish ham mumkin (o‘sha soniyada ham: yangi «tiklashdan oldingi» nusxa uning ustiga yozilmaydi).
        val second = services.backup.restore(result.preRestoreBackup)
        assertTrue(second.preRestoreBackup != result.preRestoreBackup)
        assertEquals(2, services.reagents.list().size)
        assertEquals(litres("3"), services.stock.remainder(batch))
        assertEquals(1L, services.backup.validate(second.preRestoreBackup).batches)
    }

    @Test
    fun badFilesAreRefusedAndTheDatabaseStaysIntact() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val text = tmp.newFile("eski.json").toPath().also { Files.writeString(it, """{"format":"reaktiv-ombor"}""") }
        assertTrue(assertThrows(UserError::class.java) { services.backup.restore(text) }.message.contains("zaxira nusxasi emas"))

        val missing = tmp.root.toPath().resolve("yoq.db")
        assertTrue(assertThrows(UserError::class.java) { services.backup.validate(missing) }.message.contains("topilmadi"))

        // Boshqa SQLite fayl (Reaktiv Control belgisi yo‘q).
        val foreign = tmp.root.toPath().resolve("boshqa.db")
        Database.connect(foreign).use { it.exec("CREATE TABLE t(x)") }
        assertTrue(assertThrows(UserError::class.java) { services.backup.restore(foreign) }.message.contains("zaxira nusxasi emas"))

        // Buzilgan nusxa.
        val good = tmp.root.toPath().resolve("good.db")
        services.backup.create(good)
        val broken = tmp.root.toPath().resolve("broken.db")
        val bytes = Files.readAllBytes(good)
        for (i in 4096 until minOf(bytes.size, 12288)) bytes[i] = 0x55
        Files.write(broken, bytes)
        assertThrows(UserError::class.java) { services.backup.restore(broken) }

        // Ishlab turgan bazaning o‘zi.
        assertThrows(UserError::class.java) { services.backup.restore(db.path) }

        assertEquals(litres("5"), services.stock.remainder(batch))
        issueTo("Biologiya", batch, "1", L)
        assertEquals(litres("4"), services.stock.remainder(batch))
        assertTrue(services.backup.list().none { it.kind == BackupKind.PRE_RESTORE })
    }

    @Test
    fun aBackupFromANewerVersionIsRefused() {
        val target = tmp.root.toPath().resolve("newer.db")
        services.backup.create(target)
        Database.connect(target).use { it.exec("PRAGMA user_version = 7") }
        val error = assertThrows(UserError::class.java) { services.backup.validate(target) }
        assertTrue(error.message.contains("yangiroq versiyasida"))
    }

    @Test
    fun autoBackupOncePerDayKeepsOnlyTheNewest() {
        services.settings.autoBackupKeep = 3
        val days = (0L until 5L).map { today.minusDays(4 - it) }
        days.forEach { day -> assertTrue(services.backup.autoBackupIfDue(day) != null) }
        // Shu kuni ikkinchi marta — olinmaydi.
        assertNull(services.backup.autoBackupIfDue(today))
        val autos = services.backup.list().filter { it.kind == BackupKind.AUTO }.map { it.path.fileName.toString() }.sorted()
        assertEquals(days.takeLast(3).map { "auto-$it.db" }, autos)
        assertEquals(today, services.settings.lastAutoBackup)
        autos.forEach { services.backup.validate(services.backup.dir.resolve(it)) }
        assertThrows(UserError::class.java) { services.settings.autoBackupKeep = 0 }
    }

    /** Baza faylidagi [table] jadvalining ildiz sahifasini buzadi (bazani yopib). Sahifa 1 (sxema) butun qoladi. */
    private fun corruptTablePage(file: Path, table: String) {
        val (root, pageSize) = Database.connect(file).use { conn ->
            conn.queryOne("SELECT rootpage FROM sqlite_master WHERE name = ?", table) { it.getInt(1) }!! to
                conn.queryOne("PRAGMA page_size") { it.getInt(1) }!!
        }
        val bytes = Files.readAllBytes(file)
        for (i in (root - 1) * pageSize until root * pageSize) bytes[i] = 0x55
        Files.write(file, bytes)
    }

    @Test
    fun restoreStillWorksWhenTheLiveDatabaseIsCorrupted() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val good = tmp.root.toPath().resolve("good.db")
        services.backup.create(good)
        repeat(20) { issueTo("Biologiya", batch, "${it + 1}", ML) }
        db.close()
        corruptTablePage(DataDir.dbFile(dataDir), "audit_logs")
        val broken = Files.readAllBytes(DataDir.dbFile(dataDir))

        val live = Database(DataDir.dbFile(dataDir)) // sxema sahifasi butun — baza ochiladi
        try {
            val again = Services(live, dataDir, clock)
            // VACUUM INTO buzilgan sahifani o‘qiy olmaydi — oldin tiklash rad etilardi.
            assertThrows(UserError::class.java) { again.backup.create(tmp.root.toPath().resolve("copy.db")) }
            val result = again.backup.restore(good)
            assertTrue(result.preRestoreRaw)
            assertTrue(result.preRestoreBackup.fileName.toString(), result.preRestoreBackup.fileName.toString().endsWith("-raw.db"))
            // Joriy holat baribir saqlandi: fayl o‘zi, bayt-bayt.
            assertTrue(broken.contentEquals(Files.readAllBytes(result.preRestoreBackup)))
            assertTrue(again.backup.list().any { it.path == result.preRestoreBackup && it.kind == BackupKind.PRE_RESTORE })
            assertEquals(litres("5"), again.stock.remainder(batch))
            again.stock.issue(IssueInput(today, lines = listOf(IssueLine(batch, "1", L, RecipientType.DEPARTMENT, departmentId = deptIdIn(again, "Gistologiya")))))
            assertEquals(litres("4"), again.stock.remainder(batch))
            assertTrue(again.queries.auditLogs().any { it.action == AuditAction.BACKUP_RESTORED })
        } finally {
            live.close()
        }
    }

    private fun deptIdIn(s: Services, name: String): Long = s.directory.departments().first { it.name == name }.id

    @Test
    fun startupRecoveryUsesTheNewestValidBackupAndKeepsTheBrokenFile() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val older = services.backup.autoBackupIfDue(today.minusDays(1))!!
        issueTo("Biologiya", batch, "1", L)
        val newer = services.backup.autoBackupIfDue(today)!!
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000_000_000_000L))
        Files.setLastModifiedTime(newer, FileTime.fromMillis(1_100_000_000_000L))
        // Eng yangi fayl — buzuq nusxa: tanlanmaydi.
        val junk = services.backup.dir.resolve("qolda-buzuq.db").also { Files.writeString(it, "buzuq") }
        Files.setLastModifiedTime(junk, FileTime.fromMillis(1_200_000_000_000L))
        issueTo("Biologiya", batch, "2", L) // zaxiradan keyingi operatsiya — yo‘qoladi
        db.close()

        // Baza fayli ochilmaydigan darajada buzilgan (sarlavha va sxema sahifasi).
        val dbFile = DataDir.dbFile(dataDir)
        val bytes = Files.readAllBytes(dbFile)
        for (i in 0 until 4096) bytes[i] = 0x33
        Files.write(dbFile, bytes)
        assertThrows(Exception::class.java) { Services.open(dataDir, clock).db.close() }

        assertEquals(newer, Backup.latestValid(dataDir))
        val result = Backup.recoverFromLatest(dataDir, clock)
        assertEquals(newer, result.backup)
        // Buzilgan fayl o‘chirilmadi — yoniga surildi.
        val brokenCopy = result.brokenCopy!!
        assertEquals(dataDir, brokenCopy.parent)
        assertTrue(brokenCopy.fileName.toString(), Regex("""reaktiv-control-buzuq-\d{8}-\d{6}\.db""").matches(brokenCopy.fileName.toString()))
        assertTrue(bytes.contentEquals(Files.readAllBytes(brokenCopy)))
        assertTrue(Files.isWritable(dbFile))

        val reopened = Services.open(dataDir, clock)
        try {
            assertEquals(litres("4"), reopened.stock.remainder(batch))
            assertTrue(reopened.queries.auditLogs().any { it.action == AuditAction.BACKUP_RESTORED && it.details.contains(brokenCopy.fileName.toString()) })
            reopened.stock.issue(IssueInput(today, lines = listOf(IssueLine(batch, "1", L, RecipientType.DEPARTMENT, departmentId = deptIdIn(reopened, "Gistologiya")))))
            assertEquals(litres("3"), reopened.stock.remainder(batch))
        } finally {
            reopened.db.close()
        }
    }

    @Test
    fun recoveryWithoutAValidBackupChangesNothing() {
        db.close()
        val dbFile = DataDir.dbFile(dataDir)
        val before = Files.readAllBytes(dbFile)
        val error = assertThrows(UserError::class.java) { Backup.recoverFromLatest(dataDir, clock) }
        assertTrue(error.message, error.message.contains("zaxira nusxa topilmadi"))
        assertTrue(before.contentEquals(Files.readAllBytes(dbFile)))
    }

    @Test
    fun restoringFromAReadOnlyBackupLeavesAWritableDatabase() {
        assumeTrue("POSIX huquqlari kerak", FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val target = tmp.root.toPath().resolve("cd/zaxira.db")
        services.backup.create(target)
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r--r--r--"))
        issueTo("Biologiya", batch, "1", L)

        services.backup.restore(target)
        // Files.copy manbaning «faqat o‘qish» huquqini ham ko‘chirardi — baza yozib bo‘lmaydigan bo‘lib qolardi.
        val permissions = Files.getPosixFilePermissions(db.path)
        assertTrue(permissions.toString(), PosixFilePermission.OWNER_WRITE in permissions)
        assertTrue(Files.isWritable(db.path))
        issueTo("Gistologiya", batch, "2", L)
        assertEquals(litres("3"), services.stock.remainder(batch))
        // Manba fayl o‘zgarmadi.
        assertEquals(PosixFilePermissions.fromString("r--r--r--"), Files.getPosixFilePermissions(target))
    }

    /** Tekshiruvdan o‘tadigan, lekin ochilganda sxemani yangilab bo‘lmaydigan nusxa (almashtirishdan keyingi xatoni sinash uchun). */
    private fun backupThatFailsAfterTheSwap(): Path {
        val target = tmp.root.toPath().resolve("yangilanmaydi.db")
        services.backup.create(target)
        Database.connect(target).use { it.exec("PRAGMA user_version = 1") } // legacy_rows jadvali bor — 2-qadam xato beradi
        services.backup.validate(target)
        return target
    }

    @Test
    fun aRestoreThatFailsAfterTheSwapRollsBackAndCleansUp() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val bad = backupThatFailsAfterTheSwap()
        issueTo("Biologiya", batch, "1", L)
        val error = assertThrows(UserError::class.java) { services.backup.restore(bad) }
        assertTrue(error.message, error.message.contains("Oldingi ma’lumotlar joyida qoldi"))
        assertTrue(db.isOpen)
        assertEquals(litres("4"), services.stock.remainder(batch))
        Files.list(dataDir).use { files ->
            assertTrue(files.noneMatch { it.fileName.toString().startsWith(".rollback-") || it.fileName.toString().startsWith(".restore-") })
        }
    }

    @Test
    fun whenTheRollbackItselfFailsTheOldDataIsKeptAndItsLocationIsReported() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val bad = backupThatFailsAfterTheSwap()
        issueTo("Biologiya", batch, "1", L)
        // Eski faylni joyiga qaytarish ham muvaffaqiyatsiz (masalan, antivirus faylni band qilgan).
        services.backup.mover = { from, to ->
            if (from.fileName.toString().startsWith(".rollback-")) throw AccessDeniedException(to.toString())
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
        val error = assertThrows(UserError::class.java) { services.backup.restore(bad) }
        assertFalse(error.message, error.message.contains("joyida qoldi"))
        val kept = Files.list(dataDir).use { files -> files.filter { it.fileName.toString().startsWith(".rollback-") }.toList() }.single()
        assertTrue(error.message, error.message.contains(kept.toString()))
        // Nusxada tiklashdan oldingi holat to‘liq: 1 l berilgan, qoldiq 4 l.
        Database.connect(kept, readOnly = true).use { conn ->
            assertEquals(litres("4"), conn.queryLong("SELECT SUM(qty) FROM stock_movements WHERE batch_id = ?", batch))
        }
        assertEquals(1L, Backup.validateFile(kept).batches)
    }
}

package uz.reaktivombor.desktop.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.exec
import java.nio.file.Files

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
}

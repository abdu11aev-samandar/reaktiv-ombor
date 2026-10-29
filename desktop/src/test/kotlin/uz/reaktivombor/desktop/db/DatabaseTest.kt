package uz.reaktivombor.desktop.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.service.IssueConfirm
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.ServiceTestBase
import uz.reaktivombor.desktop.service.Services
import java.sql.SQLException

class DatabaseTest : ServiceTestBase() {

    private fun sqlError(sql: String, vararg args: Any?): SQLException =
        assertThrows(SQLException::class.java) { db.write { it.update(sql, *args) } }

    @Test
    fun schemaVersionSeedsAndPragmas() {
        db.read { conn ->
            assertEquals(Schema.VERSION, Schema.userVersion(conn))
            assertEquals(1, conn.queryOne("PRAGMA foreign_keys") { it.getInt(1) })
            assertEquals("delete", conn.queryOne("PRAGMA journal_mode") { it.getString(1) })
            assertEquals(2, conn.queryOne("PRAGMA synchronous") { it.getInt(1) }) // FULL
            assertEquals(Schema.APP_MARKER, conn.queryOne("SELECT value FROM app_settings WHERE key = 'app'") { it.getString(1) })
        }
        assertEquals(14, services.settings.autoBackupKeep)
        assertEquals("", services.settings.orgName)
    }

    @Test
    fun dataSurvivesClosingAndReopening() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        issueTo("Biologiya", batch, "1,5", L)
        services.settings.orgName = "Respublika sud-tibbiy ekspertiza markazi"
        db.close()

        val reopened = Database(DataDir.dbFile(dataDir))
        try {
            val again = Services(reopened, dataDir, clock)
            assertEquals(litres("3,5"), again.stock.remainder(batch))
            assertEquals("Etil spirti", again.reagents.list().single().name)
            assertEquals(1, again.queries.issueItems().size)
            assertEquals("Respublika sud-tibbiy ekspertiza markazi", again.settings.orgName)
            // Ishlashda davom etadi.
            val gistologiya = again.directory.departments().first { it.name == "Gistologiya" }.id
            again.stock.issue(
                IssueInput(today, lines = listOf(IssueLine(batch, "500", ML, RecipientType.DEPARTMENT, departmentId = gistologiya))),
                IssueConfirm.none,
            )
            assertEquals(litres("3"), again.stock.remainder(batch))
        } finally {
            reopened.close()
        }
        // reopen() — o‘sha obyekt bilan.
        db.reopen()
        assertEquals(litres("3"), services.stock.remainder(batch))
    }

    @Test
    fun ledgerAndAuditAreAppendOnly() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        issueTo("Biologiya", batch, "100", ML)
        assertTrue(sqlError("UPDATE stock_movements SET qty = 5").message!!.contains("append-only"))
        assertTrue(sqlError("DELETE FROM stock_movements").message!!.contains("append-only"))
        assertTrue(sqlError("UPDATE audit_logs SET details = 'x'").message!!.contains("append-only"))
        assertTrue(sqlError("DELETE FROM audit_logs").message!!.contains("append-only"))
        // Operatsiya yozuvlari: miqdorni o‘zgartirib yoki o‘chirib bo‘lmaydi.
        assertTrue(sqlError("UPDATE receipts SET qty = 1").message!!.contains("append-only"))
        assertTrue(sqlError("DELETE FROM receipts").message!!.contains("append-only"))
        assertTrue(sqlError("UPDATE issue_items SET qty = 1").message!!.contains("append-only"))
        assertTrue(sqlError("DELETE FROM issue_items").message!!.contains("append-only"))
        assertTrue(sqlError("DELETE FROM issues").message!!.contains("append-only"))
        // Bekor qilingan operatsiyani «qaytarib» bo‘lmaydi.
        val item = services.queries.issueItems().single().id
        services.stock.cancelIssueItem(item, "Xato")
        assertTrue(sqlError("UPDATE issue_items SET cancelled_at = NULL, cancel_reason = NULL WHERE id = ?", item).message!!.contains("append-only"))
        assertEquals(litres("1"), services.stock.remainder(batch))
        assertEquals(3L, count("stock_movements"))
    }

    @Test
    fun databaseItselfRefusesNegativeStockAndDoubleCounting() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        val receipt = services.queries.receipts().single().id
        val now = "2026-10-29T09:00:00"
        // Qoldiqdan ko‘p chiqim — bazaning o‘zi rad etadi.
        assertTrue(
            sqlError(
                "INSERT INTO stock_movements(batch_id, kind, qty, moved_on, note, created_at) VALUES (?, 'ADJUSTMENT', ?, ?, '', ?)",
                batch, -litres("2"), today, now,
            ).message!!.contains("NEGATIVE_STOCK"),
        )
        // Bitta kirim ikki marta hisoblanmaydi.
        sqlError(
            "INSERT INTO stock_movements(batch_id, kind, qty, moved_on, receipt_id, created_at) VALUES (?, 'RECEIPT', ?, ?, ?, ?)",
            batch, litres("1"), today, receipt, now,
        )
        // Teskari yozuv faqat aniq teskari bo‘ladi va faqat bir marta.
        val movement = db.read { it.queryLong("SELECT id FROM stock_movements WHERE receipt_id = ?", receipt) }
        assertTrue(
            sqlError(
                "INSERT INTO stock_movements(batch_id, kind, qty, moved_on, reverses_id, created_at) VALUES (?, 'REVERSAL', ?, ?, ?, ?)",
                batch, -litres("0,5"), today, movement, now,
            ).message!!.contains("BAD_REVERSAL"),
        )
        db.write {
            it.update(
                "INSERT INTO stock_movements(batch_id, kind, qty, moved_on, reverses_id, created_at) VALUES (?, 'REVERSAL', ?, ?, ?, ?)",
                batch, -litres("1"), today, movement, now,
            )
        }
        sqlError(
            "INSERT INTO stock_movements(batch_id, kind, qty, moved_on, reverses_id, created_at) VALUES (?, 'REVERSAL', ?, ?, ?, ?)",
            batch, -litres("1"), today, movement, now,
        )
        assertEquals(0L, services.stock.remainder(batch))
    }

    @Test
    fun failedWriteRollsBackEverythingAndNestedWriteOnlyItsOwnPart() {
        val before = count("app_settings")
        assertThrows(IllegalStateException::class.java) {
            db.write { conn ->
                conn.update("INSERT INTO app_settings(key, value) VALUES ('a', '1')")
                throw IllegalStateException("xato")
            }
        }
        assertEquals(before, count("app_settings"))
        db.write { conn ->
            conn.update("INSERT INTO app_settings(key, value) VALUES ('outer', '1')")
            runCatching {
                db.write { inner ->
                    inner.update("INSERT INTO app_settings(key, value) VALUES ('inner', '1')")
                    throw IllegalStateException("ichki xato")
                }
            }
            conn.update("INSERT INTO app_settings(key, value) VALUES ('after', '1')")
        }
        assertEquals(before + 2, count("app_settings"))
        assertEquals(null, services.settings.get("inner"))
        assertEquals("1", services.settings.get("after"))
    }

    @Test
    fun aFailedFileOperationLeavesTheDatabaseOpenAndUsable() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        assertThrows(IllegalStateException::class.java) { db.offline { throw IllegalStateException("nusxa olinmadi") } }
        assertTrue(db.isOpen)
        assertEquals(litres("1"), services.stock.remainder(batch))
        issueTo("Biologiya", batch, "100", ML)
        // Tranzaksiya ichida faylni almashtirib bo‘lmaydi.
        assertThrows(IllegalStateException::class.java) { db.write { db.reopen() } }
        assertEquals(ml("900"), services.stock.remainder(batch))
    }

    @Test
    fun aDatabaseFromANewerVersionIsRefused() {
        db.write { it.exec("PRAGMA user_version = 99") }
        db.close()
        val error = assertThrows(UserError::class.java) { Database(DataDir.dbFile(dataDir)) }
        assertTrue(error.message.contains("yangiroq versiyasida"))
    }
}

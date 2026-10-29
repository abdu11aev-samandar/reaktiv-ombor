package uz.reaktivombor.desktop.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.SheetRow
import uz.reaktivombor.desktop.SheetStore
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.UserError
import java.nio.file.Files
import java.nio.file.Path

class LegacyImportTest : ServiceTestBase() {

    private val version2Rows = listOf(
        // Kelgan 1000 ml, omborda 750 ml, Biologiyaga 50 ml berilgan → qoldiq 700 ml.
        SheetRow(1, "Etil spirti", "01.09.2026", "1000", "ml", "01.09.2027", "750", "Biologiya", "50", createdAt = 10, legacyReceivedAt = "05.09.2026"),
        // Yangi bo‘lim nomi bilan.
        SheetRow(2, "Shavel kislota", "15.07.2026", "250", "g", "20.12.2026", "180,5", "Kimyo", "", createdAt = 20),
        SheetRow(3, "", "", "5", "l", "01.01.2027", "5", createdAt = 30), // nomsiz — o‘tkazib yuboriladi
        SheetRow(4, "Ammiak", "", "1", "xyz", "01.01.2027", "1", createdAt = 40), // noma’lum birlik
        SheetRow(5, "Xloroform", "", "", "ml", "", "", createdAt = 50), // tugallanmagan: miqdorsiz, muddatsiz
    )

    private fun writeJson(name: String, text: String): Path = tmp.root.toPath().resolve(name).also { Files.writeString(it, text) }

    @Test
    fun importsAVersion2SpreadsheetFile() {
        val json = SheetStore.toJson(version2Rows, "2026-10-20T10:00:00", departments = listOf("Gistologiya", "Kimyo", "Toksikologiya"))
        val file = writeJson("reaktivlar-jadval.json", json)
        val result = services.legacy.importFile(file)
        assertEquals(3, result.imported)
        assertEquals(2, result.skipped)
        assertTrue(result.notes.any { it.contains("nomi yo‘q") })
        assertTrue(result.notes.any { it.contains("«xyz»") })
        assertEquals(json, Files.readString(file)) // eski fayl o‘zgarmaydi

        val rows = services.queries.batchRows(today = today)
        val etil = rows.single { it.name == "Etil spirti" }
        assertEquals(ml("1000"), etil.receivedQty)
        assertEquals(ml("50"), etil.issuedQty)
        assertEquals(ml("700"), etil.remainder)
        assertEquals("", etil.series)
        assertEquals(java.time.LocalDate.of(2026, 9, 5), etil.receivedOn)
        assertEquals(java.time.LocalDate.of(2026, 9, 1), etil.producedOn)
        assertEquals(java.time.LocalDate.of(2027, 9, 1), etil.expiresOn)
        val item = services.queries.issueItems().single()
        assertEquals("Biologiya", item.recipientName)
        assertEquals(ml("50"), item.qty)
        // Ombordagi miqdorga moslash tarixda ko‘rinadi.
        val ledger = services.queries.ledger(etil.batchId)
        assertEquals(listOf(MovementKind.RECEIPT, MovementKind.ADJUSTMENT, MovementKind.ISSUE), ledger.map { it.kind })
        assertEquals("Eski jadvaldagi ombordagi miqdorga moslash", ledger[1].description)
        assertEquals(ml("700"), ledger.last().balance)

        val shavel = rows.single { it.name == "Shavel kislota" }
        assertEquals(180_500L, shavel.remainder) // 180,5 g mg da
        assertEquals("Kimyo", shavel.departmentName)
        assertEquals(ExpiryState.YELLOW, shavel.state)
        val xloroform = rows.single { it.name == "Xloroform" }
        assertEquals(0L, xloroform.remainder)
        assertEquals(ExpiryState.UNKNOWN, xloroform.state)
        val departments = services.directory.departments().map { it.name }
        assertTrue(departments.containsAll(listOf("Kimyo", "Toksikologiya", "Gistologiya", "Biologiya")))
        assertTrue(services.legacy.isDone())
        assertTrue(services.queries.auditLogs().any { it.action == AuditAction.LEGACY_IMPORTED })
    }

    @Test
    fun importsAVersion1PhoneBackup() {
        val phone = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"Xloroform","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"320.5","createdAt":5},
              {"name":"Jelatin","receivedAt":"2026-07-01","givenAmount":"1","unit":"kg","expiresAt":"2027-07-01","stockAmount":"1","createdAt":6},
              {"name":"","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"1","createdAt":7}
            ],"drafts":[
              {"name":"Ammiak","receivedAt":"20.09.2026","givenAmount":"","unit":"gr","expiresAt":"","stockAmount":"","createdAt":8}
            ]}
        """.trimIndent()
        val result = services.legacy.importFile(writeJson("reaktivlar.json", phone))
        assertEquals(3, result.imported) // Xloroform, Jelatin, Ammiak (qoralama)
        assertEquals(1, result.skipped) // nomsiz yozuv
        val rows = services.queries.batchRows(today = today)
        val xloroform = rows.single { it.name == "Xloroform" }
        assertEquals(ml("500"), xloroform.receivedQty)
        assertEquals(ml("320,5"), xloroform.remainder)
        assertEquals(java.time.LocalDate.of(2026, 8, 10), xloroform.receivedOn)
        assertEquals(ExpiryState.RED, xloroform.state)
        assertEquals(1_000_000L, rows.single { it.name == "Jelatin" }.remainder)
        val ammiak = rows.single { it.name == "Ammiak" }
        assertEquals(0L, ammiak.remainder)
        assertEquals(java.time.LocalDate.of(2026, 9, 20), ammiak.receivedOn)
        assertTrue(services.queries.issueItems().isEmpty())
    }

    @Test
    fun theSameFileIsNeverImportedTwice() {
        val file = writeJson("reaktivlar-jadval.json", SheetStore.toJson(version2Rows, "2026-10-20T10:00:00"))
        services.legacy.importFile(file)
        val batches = count("batches")
        val error = assertThrows(UserError::class.java) { services.legacy.importFile(file) }
        assertTrue(error.message.contains("allaqachon ko‘chirilgan"))
        assertEquals(batches, count("batches"))
        assertThrows(UserError::class.java) { services.legacy.importFile(writeJson("buzuq.json", "{buzuq")) }
    }

    @Test
    fun autoImportRunsOnlyOnAnEmptyDatabaseAndOnlyOnce() {
        val dir = tmp.newFolder("Reaktivlar").toPath()
        Files.writeString(dir.resolve(SheetStore.FILE_NAME), SheetStore.toJson(version2Rows, "2026-10-20T10:00:00"))
        val legacy = LegacyImport(db, services.audit, services.settings, services.directory, services.reagents, clock, dir.resolve(SheetStore.FILE_NAME))
        assertEquals(dir.resolve(SheetStore.FILE_NAME), legacy.findLegacyFile())
        val first = legacy.autoImportIfNeeded()
        assertEquals(3, first!!.imported)
        assertNull(legacy.autoImportIfNeeded())
        assertEquals(3L, count("batches"))
    }

    @Test
    fun autoImportIsSkippedWhenTheDatabaseAlreadyHasBatches() {
        newBatch("Aseton", "A-1", today.plusDays(100), "1")
        val dir = tmp.newFolder("Reaktivlar").toPath()
        // Faqat eski dastur fayli bor (1.0.5 gacha).
        Files.writeString(dir.resolve(SheetStore.LEGACY_FILE_NAME), """{"format":"reaktiv-ombor","version":1,"reagents":[]}""")
        val legacy = LegacyImport(db, services.audit, services.settings, services.directory, services.reagents, clock, dir.resolve(SheetStore.FILE_NAME))
        assertEquals(dir.resolve(SheetStore.LEGACY_FILE_NAME), legacy.findLegacyFile())
        assertNull(legacy.autoImportIfNeeded())
        assertFalse(legacy.isDone())
        assertEquals(1L, count("batches"))
    }
}

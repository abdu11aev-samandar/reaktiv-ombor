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

    @Test
    fun anOutOfRangeOldReceivedDateIsClampedLikeTheCreationDate() {
        val created = java.time.Instant.parse("2026-09-01T08:00:00Z").toEpochMilli()
        val phone = """
            {"format":"reaktiv-ombor","version":1,"reagents":[],"drafts":[
              {"name":"Aseton","receivedAt":"20.09.99","givenAmount":"500","unit":"ml","expiresAt":"01.06.2027","stockAmount":"400","createdAt":$created},
              {"name":"Fenol","receivedAt":"01.01.1980","givenAmount":"1","unit":"l","expiresAt":"01.06.2027","stockAmount":"1","createdAt":${created + 1}}
            ]}
        """.trimIndent()
        val result = services.legacy.importFile(writeJson("reaktivlar.json", phone))
        assertEquals(2, result.imported)
        val aseton = services.queries.batchRows(today = today).single { it.name == "Aseton" }
        // 2099 yildagi «kelgan sana» olinmadi: kirim yaratilgan kun bilan, partiyadan bugun berish mumkin.
        assertNull(aseton.receivedOn)
        assertEquals(listOf(java.time.LocalDate.of(2026, 9, 1)), services.queries.receipts(OpFilter(batchId = aseton.batchId)).map { it.receivedOn })
        assertEquals(ml("400"), services.stock.available(aseton.batchId, today))
        assertTrue(aseton.note, aseton.note.contains("20.09.99"))
        assertTrue(result.notes.toString(), result.notes.any { it.contains("«Aseton»") && it.contains("bugundan keyin") })
        issueTo("Biologiya", aseton.batchId, "10", ML)
        assertEquals(ml("390"), services.stock.remainder(aseton.batchId))
        val fenol = services.queries.batchRows(today = today).single { it.name == "Fenol" }
        assertNull(fenol.receivedOn)
        assertTrue(fenol.note, fenol.note.contains("01.01.1980"))
        assertTrue(result.notes.toString(), result.notes.any { it.contains("«Fenol»") && it.contains("01.01.1990 dan oldin") })
    }

    @Test
    fun theSameRowsFromAnotherFileAreNeverImportedTwice() {
        val dir = tmp.newFolder("Reaktivlar").toPath()
        // Eski dastur har saqlashda vaqtni yozadi va oldingi holatni .bak.json ga ko‘chiradi: matn har xil, qatorlar bir xil.
        val main = dir.resolve(SheetStore.FILE_NAME).also { Files.writeString(it, SheetStore.toJson(version2Rows, "2026-10-20T10:00:00")) }
        val bak = dir.resolve("reaktivlar-jadval.bak.json").also { Files.writeString(it, SheetStore.toJson(version2Rows, "2026-10-19T18:30:00")) }
        assertEquals(3, services.legacy.importFile(main).imported)
        val batches = count("batches")
        val totals = services.queries.stockTotals(today = today).map { it.remainder }
        val error = assertThrows(UserError::class.java) { services.legacy.importFile(bak) }
        assertTrue(error.message, error.message.contains("allaqachon ko‘chirilgan"))
        assertEquals(batches, count("batches"))
        assertEquals(totals, services.queries.stockTotals(today = today).map { it.remainder })
        assertEquals(3L, count("legacy_rows"))
    }

    @Test
    fun aVersion1FileAndTheVersion2FileMadeFromItShareRows() {
        val phone = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"Xloroform","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2027-11-05","stockAmount":"320.5","createdAt":5},
              {"name":"Jelatin","receivedAt":"2026-07-01","givenAmount":"1","unit":"kg","expiresAt":"2027-07-01","stockAmount":"1","createdAt":6}
            ]}
        """.trimIndent()
        // Yangi jadval eski fayldan shu tarzda yaratilgan (1.0.6+ dastur): qatorlar va ularning yaratilgan vaqti saqlanadi.
        val v2 = writeJson("reaktivlar-jadval.json", SheetStore.toJson(SheetStore.parse(phone).rows, "2026-10-01T09:00:00"))
        assertEquals(2, services.legacy.importFile(v2).imported)
        // Eski dastur keyin yana ishlatilgan: bitta yangi yozuv qo‘shilgan.
        val later = phone.replace(
            "\n]}",
            ",\n  {\"name\":\"Aseton\",\"receivedAt\":\"2026-10-10\",\"givenAmount\":\"2\",\"unit\":\"l\",\"expiresAt\":\"2027-10-10\",\"stockAmount\":\"2\",\"createdAt\":7}\n]}",
        )
        val result = services.legacy.importFile(writeJson("reaktivlar.json", later))
        assertEquals(1, result.imported)
        assertEquals(2, result.alreadyImported)
        assertTrue(result.notes.toString(), result.notes.count { it.contains("allaqachon ko‘chirilgan") } == 2)
        assertEquals(listOf("Aseton", "Jelatin", "Xloroform"), services.queries.batchRows(today = today).map { it.name }.sorted())
        assertEquals(ml("320,5"), services.queries.batchRows(today = today).single { it.name == "Xloroform" }.remainder)
    }

    @Test
    fun aNewerOldAppFileNextToTheSpreadsheetIsMentioned() {
        val dir = tmp.newFolder("Reaktivlar").toPath()
        val main = dir.resolve(SheetStore.FILE_NAME).also { Files.writeString(it, SheetStore.toJson(version2Rows.take(2), "2026-10-20T10:00:00")) }
        val old = dir.resolve(SheetStore.LEGACY_FILE_NAME)
        Files.writeString(old, """{"format":"reaktiv-ombor","version":1,"reagents":[]}""")
        Files.setLastModifiedTime(main, java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L))
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(2_000_000_000_000L))
        val legacy = LegacyImport(db, services.audit, services.settings, services.directory, services.reagents, clock, main)
        val result = legacy.autoImportIfNeeded()!!
        assertEquals(2, result.imported)
        assertTrue(result.notes.toString(), result.notes.first().contains(SheetStore.LEGACY_FILE_NAME))
        val audit = services.queries.auditLogs(AuditFilter(action = AuditAction.LEGACY_IMPORTED)).single { it.entity == "legacy" }
        assertTrue(audit.details, audit.details.contains(SheetStore.LEGACY_FILE_NAME))
        // Eski fayl jadvaldan eski bo‘lsa — eslatma yo‘q.
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(500_000_000_000L))
        val bak = dir.resolve("reaktivlar-jadval.bak.json").also { Files.writeString(it, SheetStore.toJson(version2Rows, "2026-10-21T10:00:00")) }
        Files.setLastModifiedTime(bak, java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L))
        assertTrue(services.legacy.importFile(bak).notes.none { it.contains(SheetStore.LEGACY_FILE_NAME) })
    }

    @Test
    fun rowsWithoutAUsableUnitAreInferredOrRecordedAndUnmappedValuesAreKept() {
        val rows = listOf(
            SheetRow(1, "Ammiak", "", "1", "l", "01.01.2027", "1", createdAt = 10),
            SheetRow(2, "Ammiak", "", "2", "", "01.02.2027", "2", createdAt = 20), // birlik tanlanmagan
            SheetRow(3, "Fenol", "", "500", "mg", "01.01.2027", "500", "Biologiya", "", createdAt = 30), // noma’lum birlik
            SheetRow(4, "Aseton", "2026-13-01", "1", "l", "01.01.2027", "1", "123", "", createdAt = 40, legacyReceivedAt = "sentyabr 2026"),
        )
        val result = services.legacy.importFile(writeJson("reaktivlar-jadval.json", SheetStore.toJson(rows, "2026-10-20T10:00:00")))
        assertEquals(3, result.imported)
        assertEquals(1, result.skipped)
        // Ikkinchi «Ammiak» birligi shu nomdagi boshqa qatordan olindi.
        val ammiak = services.queries.batchRows(today = today).filter { it.name == "Ammiak" }
        assertEquals(listOf(L, L), ammiak.map { it.unit })
        assertEquals(litres("3"), ammiak.sumOf { it.remainder })
        assertTrue(result.notes.toString(), result.notes.any { it.contains("2-qator") && it.contains("«l» olindi") })
        // «Fenol» o‘tkazib yuborildi, lekin qiymatlari audit jurnalida doimiy saqlandi.
        val skipped = services.queries.auditLogs(AuditFilter(action = AuditAction.LEGACY_IMPORTED)).single { it.entity == "legacy_row" }
        listOf("Fenol", "«mg»", "«500»", "Biologiya", "01.01.2027").forEach { assertTrue(skipped.details, skipped.details.contains(it)) }
        // O‘qib bo‘lmagan sana va bo‘lim — izohda va partiya izohida (yo‘qolmaydi).
        val aseton = services.queries.batchRows(today = today).single { it.name == "Aseton" }
        assertNull(aseton.producedOn)
        assertNull(aseton.departmentId)
        listOf("2026-13-01", "sentyabr 2026", "123").forEach { assertTrue(aseton.note, aseton.note.contains(it)) }
        assertTrue(result.notes.toString(), result.notes.any { it.contains("ishlab chiqarilgan sana («2026-13-01») sana emas") })
        assertTrue(result.notes.toString(), result.notes.any { it.contains("«Kelgan vaqt» («sentyabr 2026») sana emas") })
        assertTrue(result.notes.toString(), result.notes.any { it.contains("(«123») bo‘lim nomi emas") })
    }

    @Test
    fun negativeOrHugeOldAmountsAreNamedCorrectly() {
        val rows = listOf(
            SheetRow(1, "Aseton", "", "2000000000", "ml", "01.01.2027", "-5", createdAt = 10),
            SheetRow(2, "Fenol", "", "abc", "ml", "01.01.2027", "5", createdAt = 20),
        )
        val result = services.legacy.importFile(writeJson("reaktivlar-jadval.json", SheetStore.toJson(rows, "2026-10-20T10:00:00")))
        assertTrue(result.notes.toString(), result.notes.any { it.contains("«Ombordagi miqdor» qiymati («-5») manfiy") })
        assertTrue(result.notes.toString(), result.notes.any { it.contains("«Kelgan miqdor»") && it.contains("juda katta") })
        assertTrue(result.notes.toString(), result.notes.any { it.contains("«Kelgan miqdor» qiymati («abc») son emas") })
        assertTrue(result.notes.none { it.contains("(«-5») son emas") })
    }
}

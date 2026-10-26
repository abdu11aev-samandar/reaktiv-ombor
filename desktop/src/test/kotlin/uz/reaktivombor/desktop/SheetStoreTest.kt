package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.domain.Backup
import uz.reaktivombor.domain.BackupException
import uz.reaktivombor.domain.ExpiryStatus
import java.nio.file.Files
import java.time.LocalDate

class SheetStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val rows = listOf(
        SheetRow(1, "Etil spirti", "01.09.2026", "1000", "ml", "01.09.2027", "750", "Biologiya", "50", createdAt = 10),
        SheetRow(2, "Ammiak", "30.09.2026", "", "ml", "", "", createdAt = 20), // tugallanmagan
        SheetRow(3, "Shavel kislota", "15.07.2026", "250", "g", "20.12.2026", "180,5", createdAt = 30),
        SheetRow(4, createdAt = 40), // bo‘sh — saqlanmaydi
    )

    @Test
    fun saveAndLoadKeepsCompleteRowsAndDrafts() {
        val store = SheetStore(tmp.root.toPath().resolve("data/reaktivlar.json"))
        store.save(rows)
        val loaded = store.load()
        assertEquals(null, loaded.warning)
        assertEquals(listOf("Etil spirti", "Ammiak", "Shavel kislota"), loaded.rows.map { it.name })
        assertEquals("180,5", loaded.rows[2].stockAmount)
        assertEquals("20.12.2026", loaded.rows[2].expiresAt)
        assertEquals("", loaded.rows[1].receivedAmount)
        assertEquals("Biologiya", loaded.rows[0].department)
        assertEquals("50", loaded.rows[0].issuedAmount)
        assertEquals(rows.take(3).map { it.copy(id = 0) }, loaded.rows.map { it.copy(id = 0) })
    }

    @Test
    fun theDepartmentListIsSavedWithTheTable() {
        val store = SheetStore(tmp.root.toPath().resolve("data/reaktivlar.json"))
        // «Kimyo» hali hech qayerda yozilmagan — baribir saqlanadi. «Biologiya» qatorda yozilgan — ro‘yxatdan tushib qolmaydi.
        store.save(rows, departments = listOf("Gistologiya", "Kimyo"))
        val loaded = store.load()
        assertEquals(listOf("Gistologiya", "Kimyo", "Biologiya"), loaded.departments)
        val root = JSONObject(Files.readString(store.file))
        assertEquals(listOf("Gistologiya", "Kimyo", "Biologiya"), root.getJSONArray("departments").map { it as String })
    }

    @Test
    fun aFileWithoutADepartmentListGetsTheDefaultsAndItsOwnNames() {
        val text = """
            {"format":"reaktiv-ombor","version":2,"rows":[
              {"name":"A","department":"kimyo","createdAt":1},
              {"name":"B","department":"biologiya","createdAt":2}
            ]}
        """.trimIndent()
        val parsed = SheetStore.parse(text)
        assertEquals(null, parsed.departments)
        assertEquals(listOf("Kimyo", "Biologiya"), parsed.rows.map { it.department })
        assertEquals(DEFAULT_DEPARTMENTS + "Kimyo", parsed.allDepartments)
        val file = tmp.root.toPath().resolve("reaktivlar.json")
        Files.writeString(file, text)
        assertEquals(DEFAULT_DEPARTMENTS + "Kimyo", SheetStore(file).load().departments)
    }

    @Test
    fun aBrokenDepartmentListIsCleanedUp() {
        val text = """
            {"format":"reaktiv-ombor","version":2,"departments":["Kimyo"," kimyo ",42,"","123",null,"Fizika"],"rows":[
              {"name":"A","department":"Kimyo","createdAt":1}
            ]}
        """.trimIndent()
        assertEquals(listOf("Kimyo", "Fizika"), SheetStore.parse(text).departments)
        val notAList = """{"format":"reaktiv-ombor","version":2,"departments":"Kimyo","rows":[]}"""
        assertEquals(null, SheetStore.parse(notAList).departments)
        // Eski telefon zaxirasida ro‘yxat yo‘q — boshlang‘ich bo‘limlar.
        assertEquals(null, SheetStore.parse("""{"format":"reaktiv-ombor","version":1,"reagents":[]}""").departments)
    }

    @Test
    fun aBrokenFileFallsBackToTheDepartmentsOfThePreviousSave() {
        val file = tmp.root.toPath().resolve("reaktivlar-jadval.json")
        val store = SheetStore(file)
        store.save(rows, departments = listOf("Kimyo"))
        store.save(rows, departments = listOf("Kimyo", "Fizika")) // oldingi holat .bak.json ga o‘tadi
        Files.writeString(file, "{buzuq")
        assertEquals(listOf("Kimyo", "Biologiya"), store.load().departments)
    }

    @Test
    fun dataFileIsVersion2WithMachineReadableValues() {
        val root = JSONObject(SheetStore.toJson(rows, "2026-10-21T09:00:00"))
        assertEquals(2, root.getInt("version"))
        val first = root.getJSONArray("rows").getJSONObject(0)
        assertEquals("2026-09-01", first.getString("producedAt"))
        assertEquals("750", first.getString("stockAmount"))
        assertEquals("50", first.getString("issuedAmount"))
        assertEquals(3, root.getJSONArray("rows").length())
        // Eski telefon ilovasi yangi faylni “yangiroq versiya” deb rad etadi — ma’lumotni buzib o‘qimaydi.
        assertThrows(BackupException::class.java) { Backup.fromJson(root.toString()) }
    }

    @Test
    fun aPhoneBackupIsMovedIntoTheNewTable() {
        val phone = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"Xloroform","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"320.5","createdAt":5},
              {"name":"","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"1","createdAt":6}
            ]}
        """.trimIndent()
        val parsed = SheetStore.parse(phone)
        assertTrue(parsed.migrated)
        assertEquals(1, parsed.skipped)
        val row = parsed.rows.single()
        // Eski «Berilgan miqdor» (omborga kelgani) → «Kelgan miqdor»; «Kelgan vaqt» saqlanadi, lekin ustuni yo‘q.
        assertEquals("500", row.receivedAmount)
        assertEquals("05.11.2026", row.expiresAt)
        assertEquals("320,5", row.stockAmount)
        assertEquals("", row.producedAt)
        assertEquals("10.08.2026", row.legacyReceivedAt)
        assertEquals(ExpiryStatus.RED, row.check().status(LocalDate.parse("2026-10-21")))
        assertEquals(setOf(Col.PRODUCED), row.check().errors.keys)
    }

    private val oldFile = """
        {"format":"reaktiv-ombor","version":1,"reagents":[
          {"name":"Etil spirti","receivedAt":"2026-09-01","givenAmount":"1000","unit":"ml","expiresAt":"2027-09-01","stockAmount":"750","createdAt":1}
        ],"drafts":[
          {"name":"Jelatin","receivedAt":"20.09.2026","givenAmount":"","unit":"gr","expiresAt":"","stockAmount":"","createdAt":2},
          {"name":"","receivedAt":"21.09.2026","givenAmount":"","unit":"gr","expiresAt":"","stockAmount":"","createdAt":3}
        ]}
    """.trimIndent()

    @Test
    fun theOldDataFileIsMigratedButNeverWritten() {
        val dir = tmp.root.toPath()
        val legacy = dir.resolve(SheetStore.LEGACY_FILE_NAME)
        Files.writeString(legacy, oldFile)
        val store = SheetStore(dir.resolve(SheetStore.FILE_NAME))
        val loaded = store.load()
        assertNotNull(loaded.warning)
        assertEquals(listOf("Etil spirti", "Jelatin", ""), loaded.rows.map { it.name })
        assertEquals("g", loaded.rows[1].unit)
        assertEquals("20.09.2026", loaded.rows[1].legacyReceivedAt)

        store.save(loaded.rows)
        assertEquals(oldFile, Files.readString(legacy)) // eski fayl o‘zgarmaydi
        val saved = JSONObject(Files.readString(store.file))
        assertEquals(2, saved.getInt("version"))
        val rowsJson = saved.getJSONArray("rows")
        assertEquals("2026-09-01", rowsJson.getJSONObject(0).getString("receivedAt"))
        assertEquals("2026-09-21", rowsJson.getJSONObject(2).getString("receivedAt")) // faqat sanasi bor eski qator ham qoldi
        assertEquals(null, store.load().warning) // endi yangi fayldan
    }

    @Test
    fun runningTheOldVersionAgainCannotEmptyTheNewTable() {
        val dir = tmp.root.toPath()
        val legacy = dir.resolve(SheetStore.LEGACY_FILE_NAME)
        Files.writeString(legacy, oldFile)
        val store = SheetStore(dir.resolve(SheetStore.FILE_NAME))
        store.save(store.load().rows.map { it.copy(producedAt = "01.08.2026") })
        // 1.0.5 qayta ochildi va o‘z faylini bo‘sh jadval bilan saqladi.
        Files.writeString(legacy, """{"format":"reaktiv-ombor","version":1,"reagents":[],"drafts":[]}""")
        Files.setLastModifiedTime(legacy, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 60_000))
        val again = SheetStore(store.file).load()
        assertEquals(3, again.rows.size)
        assertEquals("01.08.2026", again.rows[0].producedAt)
        assertTrue(again.warning!!.contains("Eski dastur"))
    }

    @Test
    fun csvShowsTheExpiryColourOfAnUnfinishedRow() {
        val row = SheetRow(1, "Eski", receivedAmount = "1000", unit = "ml", expiresAt = "10.10.2026", stockAmount = "500", createdAt = 1, legacyReceivedAt = "01.01.2026")
        val line = SheetStore.toCsv(listOf(row), LocalDate.parse("2026-10-22")).removePrefix("\uFEFF").trimEnd().split("\r\n")[1]
        assertEquals("1;Eski;;1000;ml;10.10.2026;500;;;500;-12;1 oydan kam (tugallanmagan)", line)
    }

    @Test
    fun brokenFileFallsBackToPreviousSave() {
        val file = tmp.root.toPath().resolve("reaktivlar.json")
        val store = SheetStore(file)
        store.save(rows)
        store.save(rows.take(1)) // oldingi holat .bak.json ga o‘tadi
        Files.writeString(file, "{buzilgan")
        val loaded = store.load()
        assertNotNull(loaded.warning)
        assertEquals(3, loaded.rows.size)
        assertTrue(Files.list(tmp.root.toPath()).use { s -> s.anyMatch { it.fileName.toString().startsWith("reaktivlar-buzuq-") } })
    }

    @Test
    fun csvOpensInExcel() {
        val csv = SheetStore.toCsv(rows.take(3), LocalDate.parse("2026-10-21"))
        val lines = csv.removePrefix("\uFEFF").trimEnd().split("\r\n")
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals(
            "№;Reaktiv nomi;Ishlab chiqarilgan vaqti;Kelgan miqdor;O‘lchov birligi;Yaroqlilik muddati;Ombordagi miqdor;Bo‘lim;Berilgan miqdor;Qoldiq;Qolgan muddat (kun);Holat",
            lines[0],
        )
        assertEquals("1;Etil spirti;01.09.2026;1000;ml;01.09.2027;750;Biologiya;50;700;315;3 oydan ko‘p", lines[1])
        assertEquals("2;Ammiak;30.09.2026;;ml;;;;;;;Tugallanmagan", lines[2])
        assertEquals("3;Shavel kislota;15.07.2026;250;g;20.12.2026;180,5;;;180,5;60;1 – 3 oy", lines[3])
    }

    @Test
    fun backupCopyProblemDoesNotBlockSaving() {
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        store.save(rows.take(1))
        // .bak almashtirib bo‘lmaydigan holatda (band yoki faqat o‘qish uchun) — bu yerda bo‘sh bo‘lmagan papka.
        Files.createDirectories(store.backupFile.resolve("band"))
        store.save(rows)
        assertEquals(listOf("Etil spirti", "Ammiak", "Shavel kislota"), store.load().rows.map { it.name })
    }

    @Test
    fun simultaneousSavesNeverBreakTheFile() {
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        val many = (1..100).map { SheetRow(it.toLong(), "Reaktiv $it", "01.01.2026", "1", "g", "01.01.2028", "$it", createdAt = it.toLong()) }
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val threads = (1..4).map { Thread { repeat(40) { runCatching { store.save(many) }.onFailure(errors::add) } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(emptyList<Throwable>(), errors.toList())
        val loaded = store.load()
        assertEquals(null, loaded.warning)
        assertEquals(100, loaded.rows.size)
    }

    @Test
    fun equalTimestampsKeepTheirOrderAfterReload() {
        val json = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"B","receivedAt":"2026-01-01","givenAmount":"1","unit":"gr","expiresAt":"2028-01-01","stockAmount":"1","createdAt":5}
            ],"drafts":[
              {"name":"A","receivedAt":"","givenAmount":"","unit":"gr","expiresAt":"","stockAmount":"","createdAt":5}
            ]}
        """.trimIndent()
        val first = SheetStore.parse(json).rows
        val second = SheetStore.parse(SheetStore.toJson(first, "x")).rows
        assertEquals(first.map { it.name }, second.map { it.name })
        assertTrue(first.zipWithNext().all { (a, b) -> a.createdAt < b.createdAt })
    }

    @Test
    fun anOlderStateNeverOverwritesANewerOne() {
        val store = SheetStore(tmp.root.toPath().resolve("reaktivlar.json"))
        store.save(rows.take(3), revision = 5)
        store.save(rows.take(1), revision = 3) // kechikkan fon saqlash
        assertEquals(3, store.load().rows.size)
        store.save(rows.take(2), revision = 6)
        assertEquals(2, store.load().rows.size)
    }
}

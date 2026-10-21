package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.domain.Backup
import java.math.BigDecimal
import java.nio.file.Files
import java.time.LocalDate

class SheetStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val rows = listOf(
        SheetRow(1, "Etil spirti", "01.09.2026", "1000", "ml", "01.09.2027", "750", 10),
        SheetRow(2, "Ammiak", "30.09.2026", "", "ml", "", "", 20), // tugallanmagan
        SheetRow(3, "Shavel kislota", "15.07.2026", "250", "gr", "20.12.2026", "180,5", 30),
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
        assertEquals("", loaded.rows[1].givenAmount)
    }

    @Test
    fun dataFileIsAPhoneBackup() {
        val json = SheetStore.toJson(rows, "2026-10-21T09:00:00")
        val parsed = Backup.fromJson(json)
        // Telefon faqat tugallangan qatorlarni oladi; drafts bo‘limini e’tiborsiz qoldiradi.
        assertEquals(listOf("Etil spirti", "Shavel kislota"), parsed.items.map { it.name })
        assertEquals(0, parsed.skipped)
        assertEquals(BigDecimal("180.500"), parsed.items[1].stockAmount)
        assertEquals(LocalDate.parse("2026-12-20"), parsed.items[1].expiresAt)
    }

    @Test
    fun readsAPhoneBackup() {
        val phone = """
            {"format":"reaktiv-ombor","version":1,"reagents":[
              {"name":"Xloroform","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"320.5","createdAt":5},
              {"name":"","receivedAt":"2026-08-10","givenAmount":"500","unit":"ml","expiresAt":"2026-11-05","stockAmount":"1","createdAt":6}
            ]}
        """.trimIndent()
        val parsed = SheetStore.parse(phone)
        assertEquals(1, parsed.skipped)
        val row = parsed.rows.single()
        assertEquals("05.11.2026", row.expiresAt)
        assertEquals("320,5", row.stockAmount)
        assertTrue(row.check().complete)
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
        val lines = csv.removePrefix("﻿").trimEnd().split("\r\n")
        assertTrue(csv.startsWith("﻿"))
        assertEquals("№;Reaktiv nomi;Kelgan vaqt;Berilgan miqdor;Birlik;Yaroqlilik muddati;Ombordagi miqdor;Holat;Qoldi", lines[0])
        assertEquals("1;Etil spirti;01.09.2026;1000;ml;01.09.2027;750;3 oydan ko‘p;315 kun qoldi", lines[1])
        assertEquals("2;Ammiak;30.09.2026;;ml;;;Tugallanmagan;", lines[2])
        assertEquals("3;Shavel kislota;15.07.2026;250;gr;20.12.2026;180,5;1 – 3 oy;60 kun qoldi", lines[3])
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
        val many = (1..100).map { SheetRow(it.toLong(), "Reaktiv $it", "01.01.2026", "1", "gr", "01.01.2028", "$it", it.toLong()) }
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
}

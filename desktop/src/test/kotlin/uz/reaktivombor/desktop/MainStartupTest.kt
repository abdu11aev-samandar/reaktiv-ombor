package uz.reaktivombor.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.sqlite.NativeLibraryNotFoundException
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.service.LegacyResult
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.theme.Severity
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

/** Main.kt: SQLite kutubxonasi papkasi (Windows, lotin bo‘lmagan foydalanuvchi nomi), kutubxona yuklanmasa xabar, eski jadval xabari. */
class MainStartupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val cyrillicTemp = "C:\\Users\\Ғайрат\\AppData\\Local\\Temp"

    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    // ---------------------------------------------------------------- SQLite kutubxonasi papkasi (#37)

    @Test
    fun nonAsciiTempOnWindowsMovesTheSqliteLibraryToAnAsciiFolder() {
        val programData = tmp.newFolder("ProgramData").toPath()
        val choice = SqliteNative.choose(
            osName = "Windows 10",
            jnuEncoding = "Cp1252",
            tmpDir = cyrillicTemp,
            current = null,
            userName = "Ғайрат",
            env = env("ProgramData" to programData.toString(), "PUBLIC" to tmp.newFolder("Public").toString()),
        )
        assertNotNull(choice)
        val dir = choice!!.dir!!
        assertEquals(programData.resolve("ReaktivControl").resolve("native"), dir.parent)
        assertTrue(dir.name, dir.name.all { it.code < 128 })
        assertTrue(Files.isDirectory(dir))
        // Sinov fayli qolmaydi.
        assertEquals(0, Files.list(dir).use { it.count() })
        assertTrue(choice.reason, choice.reason.contains("Cp1252"))
        assertTrue(choice.describe(), choice.describe().contains(dir.toString()))
    }

    @Test
    fun asciiTempOtherSystemsAndAnExplicitSettingAreLeftAlone() {
        val programData = tmp.newFolder("ProgramData").toString()
        val writableTemp = tmp.newFolder("Temp").toString()
        assertNull(SqliteNative.choose("Windows 11", "Cp1252", writableTemp, null, "runneradmin", env("ProgramData" to programData)))
        // O‘zbek lotinidagi ‘ (U+2018) Cp1252 da bor — yo‘naltirish kerak emas.
        assertTrue(SqliteNative.encodable("C:\\Users\\O‘tkir\\AppData\\Local\\Temp", "Cp1252"))
        assertNull(SqliteNative.choose("Linux", "ANSI_X3.4-1968", cyrillicTemp, null, "gayrat", env("ProgramData" to programData)))
        assertNull(SqliteNative.choose("Windows 10", "Cp1252", cyrillicTemp, "D:\\sqlite", "Ғайрат", env("ProgramData" to programData)))
        // ʻ (U+02BB) Cp1252 da yo‘q — yo‘naltiriladi.
        assertNotNull(SqliteNative.choose("Windows 10", "Cp1252", "C:\\Users\\Gʻayrat\\Temp", null, "Gʻayrat", env("ProgramData" to programData))?.dir)
    }

    @Test
    fun unwritableTempIsRedirectedAndUnusableFoldersFallBackOrGiveUp() {
        val blocker = tmp.newFile("fayl").toPath()
        val public = tmp.newFolder("Public").toPath()
        // %TEMP% yozib bo‘lmaydi (ASCII bo‘lsa ham), ProgramData ham yaroqsiz — PUBLIC tanlanadi.
        val choice = SqliteNative.choose(
            "Windows 10", "Cp1252", blocker.resolve("Temp").toString(), null, "lab",
            env("ProgramData" to blocker.resolve("pd").toString(), "PUBLIC" to public.toString()),
        )!!
        assertTrue(choice.reason, choice.reason.contains("not writable"))
        assertEquals(public.resolve("ReaktivControl").resolve("native").resolve("lab"), choice.dir)
        // Hech biri yaroqsiz — hech narsa o‘zgarmaydi, sabab jurnal uchun qoladi.
        val none = SqliteNative.choose("Windows 10", "Cp1252", cyrillicTemp, null, "Ғайрат", env("ProgramData" to blocker.resolve("pd").toString()))!!
        assertNull(none.dir)
        assertTrue(none.describe(), none.describe().contains("not changed"))
        // ProgramData yo‘li ham kodlashga sig‘masa — o‘tkazib yuboriladi.
        val cyrPublic = SqliteNative.choose(
            "Windows 10", "Cp1252", cyrillicTemp, null, "Ғайрат",
            // (diskda yaratilmaydi: tizim kodlashi ASCII bo‘lgan JVM’da bunday nomli papka ochilmaydi)
            env("ProgramData" to tmp.root.toString() + "/Ғ", "PUBLIC" to public.toString()),
        )!!
        assertEquals(public, cyrPublic.dir!!.parent.parent.parent)
    }

    @Test
    fun userFolderNamesAreAsciiAndDistinct() {
        assertEquals("runneradmin", SqliteNative.userFolder("runneradmin"))
        val cyr = SqliteNative.userFolder("Ғайрат")
        assertTrue(cyr, cyr.matches(Regex("user-[0-9a-f]{8}")))
        assertTrue(SqliteNative.userFolder("Gʻayrat").startsWith("Gayrat-"))
        assertFalse(SqliteNative.userFolder("Ғайрат") == SqliteNative.userFolder("Қодир"))
        assertTrue(SqliteNative.encodable(cyrillicTemp, "UTF-8"))
        assertFalse(SqliteNative.encodable(cyrillicTemp, "Cp1252"))
        assertFalse(SqliteNative.encodable(cyrillicTemp, "no-such-charset"))
        assertTrue(SqliteNative.encodable("C:\\ProgramData", null))
    }

    // ---------------------------------------------------------------- kutubxona yuklanmasa (#38)

    @Test
    fun nativeLibraryFailureIsRecognisedAnywhereInTheCauseChain() {
        val missing = SQLException("Error opening connection").apply { initCause(NativeLibraryNotFoundException("No native library found")) }
        assertTrue(SqliteNative.isLoadFailure(missing))
        assertTrue(SqliteNative.isLoadFailure(UnsatisfiedLinkError("'int org.sqlite.core.NativeDB._open_utf8(byte[], int)'")))
        assertTrue(SqliteNative.isLoadFailure(RuntimeException(SQLException("x", UnsatisfiedLinkError("y")))))
        assertFalse(SqliteNative.isLoadFailure(SQLException("[SQLITE_NOTADB] file is not a database")))
        assertFalse(SqliteNative.isLoadFailure(IllegalStateException("baza buzilgan")))
        val message = SqliteNative.failureMessage(Paths.get("data", "reaktiv-control.db"), "C:\\ProgramData\\ReaktivControl\\native", Paths.get("logs"))
        assertTrue(message, message.startsWith("SQLite kutubxonasini (sqlitejdbc) yuklab bo‘lmadi"))
        assertTrue(message, message.contains("baza va zaxira nusxalar o‘zgartirilmadi"))
        assertTrue(message, message.contains("C:\\ProgramData\\ReaktivControl\\native"))
        assertFalse(message, message.contains("buzilgan"))
    }

    /**
     * Haqiqiy main() alohida JVM’da: baza va zaxira bor, lekin SQLite kutubxonasini chiqarib bo‘lmaydi (yozib bo‘lmaydigan
     * papka — %TEMP% / antivirus holati). Tiklash taklif qilinmaydi, xabar kutubxona haqida, fayllarga tegilmaydi.
     */
    @Test
    fun startupWithoutTheSqliteLibraryNamesTheLibraryAndTouchesNoFiles() {
        val dataDir = tmp.newFolder("data").toPath()
        val services = DemoData.open(dataDir)
        val backup = try {
            DemoData.seed(services)
            services.backup.autoBackupIfDue(DemoData.TODAY)!!
        } finally {
            services.db.close()
        }
        val dbFile = DataDir.dbFile(dataDir)
        val dbBytes = Files.readAllBytes(dbFile)
        val backupBytes = Files.readAllBytes(backup)
        val before = Files.list(dataDir).use { files -> files.map { it.name }.toList() }.toSet()

        val blocker = tmp.newFile("blocker").toPath()
        val (code, output) = runMainInNewJvm(dataDir, "-D${SqliteNative.TMPDIR_PROPERTY}=${blocker.resolve("native")}")
        assertEquals(output, 1, code)

        val log = Files.readString(DataDir.logsDir(dataDir).resolve("reaktiv-control.0.log"), StandardCharsets.UTF_8)
        assertTrue(log, log.contains("sqlite native library could not be loaded"))
        assertTrue(log, log.contains("SQLite kutubxonasini (sqlitejdbc) yuklab bo‘lmadi"))
        assertTrue(log, log.contains("baza va zaxira nusxalar o‘zgartirilmadi"))
        assertTrue(log, log.contains(blocker.resolve("native").toString()))
        // Eski xato yo‘l: zaxiralarni SQLite bilan tekshirib, «Yaroqli zaxira nusxa topilmadi» degan edi.
        assertFalse(log, log.contains("Yaroqli zaxira nusxa topilmadi"))
        assertFalse(log, log.contains("backup lookup failed"))
        assertTrue(Files.readAllBytes(dbFile).contentEquals(dbBytes))
        assertTrue(Files.readAllBytes(backup).contentEquals(backupBytes))
        val after = Files.list(dataDir).use { files -> files.map { it.name }.toList() }.toSet()
        assertTrue("$after", after.none { "buzuq" in it })
        assertEquals(before + setOf("logs", ".lock"), after + setOf("logs", ".lock"))
    }

    /** `--self-test` ham: qadamlar yiqilsa, asl sabab (kutubxona topilmadi) va kutubxona papkasi natija faylida. */
    @Test
    fun selfTestWithoutTheSqliteLibraryReportsTheRootCause() {
        val blocker = tmp.newFile("blocker").toPath()
        val out = tmp.root.toPath().resolve("selftest.txt")
        val (code, output) = runMainInNewJvm(
            tmp.newFolder("unused").toPath(), "-D${SqliteNative.TMPDIR_PROPERTY}=${blocker.resolve("native")}",
            args = listOf("--self-test", out.toString()),
        )
        assertEquals(output, 1, code)
        val text = Files.readString(out, StandardCharsets.UTF_8)
        assertTrue(text, text.contains("# SQLite kutubxonasi papkasi: ${blocker.resolve("native")} (org.sqlite.tmpdir)"))
        assertTrue(text, text.lines().first { it.startsWith("FAIL ") }.contains("[NativeLibraryNotFoundException]"))
    }

    private fun runMainInNewJvm(dataDir: Path, vararg jvmArgs: String, args: List<String> = emptyList()): Pair<Int, String> {
        val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
        val outFile = tmp.root.toPath().resolve("jvm-${System.nanoTime()}.txt")
        val command = listOf(java, "-cp", System.getProperty("java.class.path"), "-Djava.awt.headless=true", "-D${DataDir.PROPERTY}=$dataDir") +
            jvmArgs + "uz.reaktivombor.desktop.MainKt" + args
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outFile.toFile()).start()
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("main() 90 s ichida tugamadi: " + Files.readString(outFile))
        }
        return process.exitValue() to Files.readString(outFile)
    }

    // ---------------------------------------------------------------- eski jadval xabari (#36)

    @Test
    fun legacyMessageWithManyNotesSaysHowManyMoreAndOpensTheBackupPage() {
        val services = DemoData.open(tmp.newFolder("data").toPath())
        try {
            val state = AppState(services, FakeFileDialogs())
            val notes = (1..5).map { "$it-qator: izoh $it." }
            showLegacyResult(state, LegacyResult(imported = 4, skipped = 1, notes = notes))
            val message = state.messages.single()
            assertEquals(Severity.WARNING, message.severity)
            assertFalse(message.autoHide)
            assertTrue(message.text, message.text.contains("Eski jadvaldan 4 ta partiya ko‘chirildi, 1 ta qator o‘tkazib yuborildi."))
            assertTrue(message.text, message.text.contains("1-qator: izoh 1. 2-qator: izoh 2. 3-qator: izoh 3."))
            assertFalse(message.text, message.text.contains("4-qator"))
            assertTrue(message.text, message.text.endsWith("… yana 2 ta izoh."))
            assertEquals("Barcha izohlar (5 ta)", message.actionLabel)
            message.onAction!!.invoke()
            assertEquals(Page.BACKUP, state.page)

            // Izohlar 3 tadan ko‘p bo‘lmasa — hammasi xabarda, tugma yo‘q.
            val few = AppState(services, FakeFileDialogs())
            showLegacyResult(few, LegacyResult(imported = 0, skipped = 0, notes = listOf("Eski ma’lumotlarni ko‘chirib bo‘lmadi.")))
            val single = few.messages.single()
            assertEquals("Eski ma’lumotlarni ko‘chirib bo‘lmadi.", single.text)
            assertNull(single.actionLabel)
            assertNull(single.onAction)
        } finally {
            services.db.close()
        }
    }
}

package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.SheetRow
import uz.reaktivombor.desktop.SheetStore
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.service.AuditAction
import uz.reaktivombor.desktop.service.BackupKind
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.LegacyImport
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.nio.file.Files
import kotlin.io.path.name

/** «Ma’lumotlarni zaxiralash» va «Sozlamalar» — haqiqiy baza (DemoData) bilan, natija bazada tekshiriladi. */
@OptIn(ExperimentalTestApi::class)
class BackupSettingsUiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids
    private val today = DemoData.TODAY
    private val dialogs = FakeFileDialogs()

    @Before
    fun setUp() {
        val (s, i) = DemoData.seeded(tmp.newFolder("data").toPath())
        services = s
        ids = i
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.app(page: Page): AppState {
        val state = AppState(services, dialogs, page)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    private fun SkikoComposeUiTest.dialogButton(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun SkikoComposeUiTest.inside(tag: String, text: String) = onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag)))

    private fun batchCount(): Int = services.queries.batchRows(BatchFilter(includeArchived = true), today).size

    private fun addMetanol(): Long = services.reagents.createBatch(
        NewBatch(name = "Metanol", series = "MT-1", receivedOn = today, initialQtyText = "1", unit = MeasureUnit.L, expiresOn = today.plusYears(1)),
    )

    // ---------------------------------------------------------------- zaxira nusxa

    @Test
    fun createBackupWritesAValidatedFile() = runDesktopComposeUiTest(1366, 768) {
        val target = tmp.root.toPath().resolve("flesh/nusxa.db")
        dialogs.saveAnswer = target
        app(Page.BACKUP)
        onNodeWithTag("backup-create").performClick()
        waitForIdle()
        assertEquals("save:reaktiv-control-2026-10-29-0900.db", dialogs.calls.single())
        assertTrue(Files.isRegularFile(target))
        val check = services.backup.validate(target)
        assertEquals(10L, check.batches)
        assertEquals(7L, check.reagents)
        assertTrue(services.queries.auditLogs().any { it.action == AuditAction.BACKUP_CREATED })
        inside("backup-created", "nusxa.db").assertExists()
        onNode(hasText("Papkani ochish") and hasAnyAncestor(hasTestTag("backup-created"))).performClick()
        assertEquals("folder:${target.parent}", dialogs.calls.last())
    }

    @Test
    fun cancelledSaveDialogDoesNothing() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.BACKUP)
        val tick = state.refreshTick
        onNodeWithTag("backup-create").performClick()
        waitForIdle()
        onNodeWithTag("backup-created").assertDoesNotExist()
        assertEquals(tick, state.refreshTick)
        assertTrue(services.backup.list().isEmpty())
        inside("backups-empty", "Hali zaxira nusxa yo‘q").assertExists()
    }

    // ---------------------------------------------------------------- tiklash

    @Test
    fun restoreFromFileReplacesTheDataAndKeepsThePreviousState() = runDesktopComposeUiTest(1366, 768) {
        val backupFile = services.backup.create(tmp.root.toPath().resolve("oldingi.db"))
        val metanol = addMetanol()
        assertEquals(11, batchCount())
        dialogs.openAnswer = backupFile
        val state = app(Page.BACKUP)
        val tick = state.refreshTick

        onNodeWithTag("backup-restore-file").performClick()
        waitForIdle()
        assertEquals("open:db", dialogs.calls.last())
        onNodeWithTag("restore-dialog").assertExists()
        inside("restore-file-counts", "10 ta").assertExists()
        inside("restore-dialog", "11 ta").assertExists() // hozirgi baza
        inside("restore-dialog", "avtomatik ravishda backups papkasiga").assertExists()
        // Hali hech narsa o‘zgarmagan.
        assertEquals(11, batchCount())

        onNodeWithTag("restore-confirm").performClick()
        waitForIdle()
        onNodeWithTag("restore-dialog").assertDoesNotExist()
        assertEquals(10, batchCount())
        assertNull(services.reagents.batch(metanol))
        assertTrue(state.refreshTick > tick)
        val pre = services.backup.list().single { it.kind == BackupKind.PRE_RESTORE }
        assertTrue(pre.path.name.startsWith("before-restore-20261029-090000"))
        assertEquals(11L, services.backup.validate(pre.path).batches) // tiklashdan oldingi holat saqlangan
        inside("message-success", pre.path.name).assertExists()
        inside("backup-restored", pre.path.name).assertExists()
        assertTrue(services.queries.auditLogs().any { it.action == AuditAction.BACKUP_RESTORED })
        // Ro‘yxatda yangi nusxa ko‘rinadi.
        onNodeWithTag("backups-row-${pre.path.name}").assertExists()
    }

    @Test
    fun restoreCanBeCancelledAndInvalidFilesChangeNothing() = runDesktopComposeUiTest(1366, 768) {
        val backupFile = services.backup.create(tmp.root.toPath().resolve("oldingi.db"))
        addMetanol()
        dialogs.openAnswer = backupFile
        app(Page.BACKUP)
        onNodeWithTag("backup-restore-file").performClick()
        onNodeWithTag("restore-cancel").performClick()
        waitForIdle()
        onNodeWithTag("restore-dialog").assertDoesNotExist()
        assertEquals(11, batchCount())
        assertTrue(services.backup.list().isEmpty())

        val junk = tmp.root.toPath().resolve("boshqa.db").also { Files.writeString(it, "bu baza emas") }
        dialogs.openAnswer = junk
        onNodeWithTag("backup-restore-file").performClick()
        waitForIdle()
        onNodeWithTag("restore-dialog").assertDoesNotExist()
        inside("message-error", "zaxira nusxasi emas").assertExists()
        inside("message-error", "Joriy ma’lumotlar o‘zgartirilmadi.").assertExists()
        assertEquals(11, batchCount())

        // Ishlab turgan bazaning o‘zi tanlansa — rad etiladi.
        dialogs.openAnswer = DataDir.dbFile(services.dataDir)
        onNodeWithTag("backup-restore-file").performClick()
        waitForIdle()
        onNodeWithTag("restore-dialog").assertDoesNotExist()
        inside("message-error", "hozir ishlatayotgan bazaning o‘zi").assertExists()
        assertEquals(11, batchCount())
    }

    @Test
    fun backupListShowsKindsAndRestoresFromARow() = runDesktopComposeUiTest(1366, 768) {
        val auto = services.backup.autoBackupIfDue(today)!!
        addMetanol()
        services.backup.restore(auto) // before-restore-… nusxasi paydo bo‘ladi
        assertEquals(10, batchCount())
        val manual = services.backup.create(services.backup.dir.resolve("qolda-nusxa.db"))
        app(Page.BACKUP)
        val pre = services.backup.list().single { it.kind == BackupKind.PRE_RESTORE }
        inside("backups-row-${auto.name}", "Avtomatik").assertExists()
        inside("backups-row-${pre.path.name}", "Tiklashdan oldin").assertExists()
        inside("backups-row-${manual.name}", "Qo‘lda").assertExists()
        inside("backup-list-card", "3 ta: avtomatik 1, tiklashdan oldin 1, qo‘lda 1").assertExists()

        // Tiklashdan oldingi nusxadan qaytarish (unda Metanol bor).
        onNode(hasText("Tiklash") and hasAnyAncestor(hasTestTag("backups-row-${pre.path.name}"))).performClick()
        waitForIdle()
        inside("restore-file-counts", "Tiklashdan oldin").assertExists()
        inside("restore-file-counts", "11 ta").assertExists()
        onNodeWithTag("restore-confirm").performClick()
        waitForIdle()
        assertEquals(11, batchCount())
        // Ikkinchi tiklash ham oldingi holatni saqlaydi (nomi to‘qnashmaydi).
        assertEquals(2, services.backup.list().count { it.kind == BackupKind.PRE_RESTORE })

        onNode(hasText("Papkani ochish") and hasAnyAncestor(hasTestTag("backup-list-card"))).performClick()
        assertEquals("folder:${services.backup.dir}", dialogs.calls.last())
    }

    @Test
    fun locationsShowDataFolderDatabaseAndLogs() = runDesktopComposeUiTest(1366, 768) {
        services.settings.autoBackupKeep = 20
        services.backup.autoBackupIfDue(today)
        val state = app(Page.BACKUP)
        val dataDir = services.dataDir.toAbsolutePath()
        onNodeWithTag("backup-locations").performScrollTo()
        inside("backup-data-dir", dataDir.toString()).assertExists()
        inside("backup-db-file", DataDir.DB_FILE).assertExists()
        inside("backup-db-file", "KB").assertExists()
        inside("backup-logs-dir", DataDir.logsDir(dataDir).toString()).assertExists()
        onNodeWithTag("backup-auto-info").assert(hasText("Oxirgisi: 29.10.2026 · eng yangi 20 tasi saqlanadi"))
        onNode(hasText("Papkani ochish") and hasAnyAncestor(hasTestTag("backup-logs-dir"))).performClick()
        assertEquals("folder:${DataDir.logsDir(dataDir)}", dialogs.calls.last())
        onNode(hasText("Sozlamalar") and hasAnyAncestor(hasTestTag("backup-locations"))).performClick()
        waitForIdle()
        assertEquals(Page.SETTINGS, state.page)
    }

    // ---------------------------------------------------------------- eski dasturdan

    @Test
    fun legacyImportShowsCountsAndRefusesTheSameFileTwice() = runDesktopComposeUiTest(1366, 768) {
        val rows = listOf(
            SheetRow(1, "Metilen ko‘ki", "01.03.2026", "500", "ml", "01.03.2028", "500", "Gistologiya", "100", createdAt = 10),
            SheetRow(2, "Gematoksilin", "", "250", "g", "15.12.2026", "250", createdAt = 20),
            SheetRow(3, "", "", "5", "l", "01.01.2027", "5", createdAt = 30), // nomsiz — o‘tkazib yuboriladi
        )
        val file = tmp.root.toPath().resolve("reaktivlar-jadval.json")
        Files.writeString(file, SheetStore.toJson(rows, "2026-10-20T10:00:00"))
        dialogs.openAnswer = file
        app(Page.BACKUP)
        onNodeWithTag("backup-legacy-pick").performScrollTo().performClick()
        assertEquals("open:json", dialogs.calls.last())
        dialogButton("Ko‘chirish").performClick()
        waitForIdle()
        assertEquals(12, batchCount())
        val metilen = services.queries.batchRows(BatchFilter(text = "Metilen"), today).single()
        assertEquals(400_000L, metilen.remainder) // 500 ml kelgan, Gistologiyaga 100 ml berilgan → 400 ml
        inside("backup-legacy-result", "Ko‘chirildi: 2 ta partiya · o‘tkazib yuborildi: 1 ta qator").assertExists()
        onNodeWithTag("backup-legacy-notes").performScrollTo().performClick()
        onNodeWithTag("legacy-notes-dialog").assertExists()
        inside("legacy-notes-dialog", "3-qator: reaktiv nomi yo‘q — o‘tkazib yuborildi.").assertExists()
        onNodeWithTag("legacy-notes-dialog-close").performClick()
        inside("backup-legacy-last", "reaktivlar-jadval.json").assertExists()

        // Xuddi shu fayl qayta — rad etiladi, hech narsa qo‘shilmaydi.
        onNodeWithTag("backup-legacy-pick").performScrollTo().performClick()
        dialogButton("Ko‘chirish").performClick()
        waitForIdle()
        inside("message-error", "allaqachon ko‘chirilgan").assertExists()
        inside("message-error", "Bazaga hech narsa qo‘shilmadi.").assertExists()
        assertEquals(12, batchCount())
    }

    @Test
    fun legacyFileFoundOnThisComputerIsOfferedUntilImported() = runDesktopComposeUiTest(1366, 768) {
        val file = tmp.newFolder("eski").toPath().resolve("reaktivlar-jadval.json")
        Files.writeString(
            file,
            SheetStore.toJson(listOf(SheetRow(1, "Sudan III", "01.02.2026", "100", "g", "01.02.2028", "100", "Gistologiya", "", createdAt = 1)), "2026-10-20T10:00:00"),
        )
        val old = System.getProperty(LegacyImport.PROPERTY)
        System.setProperty(LegacyImport.PROPERTY, file.toString())
        try {
            app(Page.BACKUP)
            inside("backup-legacy-found", file.toString()).assertExists()
            inside("backup-legacy-found", "allaqachon ko‘chirilgan").assertDoesNotExist()
            onNodeWithTag("backup-legacy-found-import").performScrollTo().performClick()
            dialogButton("Ko‘chirish").performClick()
            waitForIdle()
            assertEquals(11, batchCount())
            assertEquals(100_000L, services.queries.batchRows(BatchFilter(text = "Sudan"), today).single().remainder)
            inside("backup-legacy-result", "Ko‘chirildi: 1 ta partiya").assertExists()
            // Endi shu fayl uchun tugma yo‘q — fayl allaqachon ko‘chirilgan deb yoziladi.
            inside("backup-legacy-found", "allaqachon ko‘chirilgan").assertExists()
            onNodeWithTag("backup-legacy-found-import").assertDoesNotExist()
        } finally {
            if (old == null) System.clearProperty(LegacyImport.PROPERTY) else System.setProperty(LegacyImport.PROPERTY, old)
        }
    }

    // ---------------------------------------------------------------- sozlamalar

    @Test
    fun settingsSaveOrganisationAndStaffNameUsedByForms() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.SETTINGS)
        onNodeWithTag("settings-org").performTextReplacement("  Respublika sud-tibbiy   ekspertiza markazi ")
        onNodeWithTag("settings-actor").performTextReplacement("Karimova D.")
        onNodeWithText("Saqlanmagan o‘zgarish bor").assertExists()
        onNodeWithTag("settings-people-save").performClick()
        waitForIdle()
        assertEquals("Respublika sud-tibbiy ekspertiza markazi", services.settings.orgName)
        assertEquals("Karimova D.", services.settings.actorName)
        inside("settings-people-save-state", "Saqlandi").assertExists()
        onNodeWithText("Saqlanmagan o‘zgarish bor").assertDoesNotExist()
        assertTrue(services.queries.auditLogs().any { it.action == AuditAction.SETTINGS_CHANGED && it.details.contains("Tashkilot nomi") })
        // Hisobot sarlavhasi va forma standart qiymati shu sozlamalardan.
        assertEquals("Respublika sud-tibbiy ekspertiza markazi", uz.reaktivombor.desktop.report.ReportBuilder(services).build(uz.reaktivombor.desktop.report.ReportKind.ALL_REAGENTS).orgName)
        state.navigate(Page.ADD_REAGENT)
        waitForIdle()
        onNodeWithTag("add-receiver").assert(hasText("Karimova D."))
    }

    @Test
    fun settingsShowFieldErrorsAndSaveNothingWhenInvalid() = runDesktopComposeUiTest(1366, 768) {
        app(Page.SETTINGS)
        onNodeWithTag("settings-org").performTextReplacement("A".repeat(201))
        onNodeWithTag("settings-actor").performTextReplacement("B".repeat(101))
        onNodeWithTag("settings-people-save").performClick()
        waitForIdle()
        onNodeWithText("Tashkilot nomi 200 belgidan oshmasligi kerak.").assertExists()
        onNodeWithText("Xodim ismi 100 belgidan oshmasligi kerak.").assertExists()
        assertEquals("", services.settings.orgName)
        assertEquals("", services.settings.actorName)

        onNodeWithTag("settings-keep").performTextReplacement("0")
        onNodeWithTag("settings-keep-save").performClick()
        waitForIdle()
        onNodeWithText("Avtomatik zaxiralar soni 1 dan 365 gacha bo‘lishi kerak.").assertExists()
        assertEquals(14, services.settings.autoBackupKeep)
        onNodeWithTag("settings-keep").performTextReplacement("abc")
        onNodeWithTag("settings-keep-save").performClick()
        waitForIdle()
        onNodeWithText("butun son bo‘lishi kerak", substring = true).assertExists()
        assertEquals(14, services.settings.autoBackupKeep)
        onNodeWithTag("settings-keep").performTextReplacement("30")
        onNodeWithText("Avtomatik zaxiralar soni 1 dan 365 gacha bo‘lishi kerak.").assertDoesNotExist()
        onNodeWithTag("settings-keep-save").performClick()
        waitForIdle()
        assertEquals(30, services.settings.autoBackupKeep)
        inside("settings-keep-save-state", "Saqlandi").assertExists()
    }

    @Test
    fun resetTableLayoutsClearsEverySavedLayout() = runDesktopComposeUiTest(1366, 768) {
        services.settings.setTableLayout("reagents", """{"order":["name"]}""")
        services.settings.setTableLayout("backups", """{"order":["kind"]}""")
        app(Page.SETTINGS)
        onNodeWithTag("settings-layout-count").assert(hasText("Hozir 2 ta jadvalning ko‘rinishi o‘zgartirilgan."))
        onNodeWithTag("settings-reset-layouts").performClick()
        dialogButton("Standart holatga qaytarish").performClick()
        waitForIdle()
        assertNull(services.settings.tableLayout("reagents"))
        assertNull(services.settings.tableLayout("backups"))
        onNodeWithTag("settings-layout-count").assert(hasText("Hozir hamma jadvallar standart holatda."))
        // Qaytaradigan narsa qolmagan — faqat xabar.
        onNodeWithTag("settings-reset-layouts").performClick()
        waitForIdle()
        inside("message-info", "allaqachon standart holatda").assertExists()
    }

    @Test
    fun settingsShowThresholdsAndAboutInformation() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.SETTINGS)
        inside("threshold-GREEN", "> 90 kun").assertExists()
        inside("threshold-YELLOW", "31–90 kun").assertExists()
        inside("threshold-RED", "0–30 kun").assertExists()
        inside("threshold-EXPIRED", "< 0 kun").assertExists()
        inside("threshold-EXPIRED", "Muddati o‘tgan").assertExists()
        inside("settings-expiry", "o‘zgarmas").assertExists()
        onNodeWithTag("settings-about").performScrollTo()
        val sqlite = services.db.read { conn -> conn.createStatement().use { it.executeQuery("SELECT sqlite_version()").use { rs -> rs.next(); rs.getString(1) } } }
        inside("about-sqlite", sqlite).assertExists()
        inside("about-version", System.getProperty("jpackage.app-version") ?: "ishlab chiqish nusxasi").assertExists()
        inside("about-java", System.getProperty("java.version")).assertExists()
        inside("about-db", DataDir.DB_FILE).assertExists()
        onNode(hasText("Papkani ochish") and hasAnyAncestor(hasTestTag("about-logs"))).performClick()
        assertEquals("folder:${DataDir.logsDir(services.dataDir.toAbsolutePath())}", dialogs.calls.last())
        onNode(hasText("Papkani ochish") and hasAnyAncestor(hasTestTag("about-data-dir"))).performClick()
        assertEquals("folder:${services.dataDir.toAbsolutePath()}", dialogs.calls.last())
        onNode(hasText("Zaxiralash") and hasAnyAncestor(hasTestTag("settings-backup-info"))).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Page.BACKUP, state.page)
    }

    @Test
    fun writeButtonsAreDisabledWhileBusy() = runDesktopComposeUiTest(1366, 768) {
        val state = app(Page.SETTINGS)
        runOnIdle { state.busy = true }
        onNodeWithTag("settings-people-save").assertIsNotEnabled()
        onNodeWithTag("settings-keep-save").assertIsNotEnabled()
        onNodeWithTag("settings-reset-layouts").assertIsNotEnabled()
        runOnIdle { state.busy = false }
        onNodeWithTag("settings-people-save").assertIsEnabled()
    }
}

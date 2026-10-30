package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.service.LegacyResult
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.service.Settings
import uz.reaktivombor.desktop.showLegacyResult
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.ReaktivControlApp

/**
 * Birinchi ochilishdagi avtomatik ko‘chirish izohlari: xabar 3 tasini ko‘rsatadi va «Barcha izohlar» bilan
 * «Ma’lumotlarni zaxiralash» sahifasini ochadi; u yerda «Oxirgi ko‘chirish» yonida hammasi (app_settings dan).
 */
@OptIn(ExperimentalTestApi::class)
class BackupLegacyNotesUiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private val notes = (1..6).map { "$it-qator («Kumush nitrat $it»): yaroqlilik muddati yo‘q — partiyani tahrirlab kiriting." }

    @Before
    fun setUp() {
        services = DemoData.seeded(tmp.newFolder("data").toPath()).first
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    /** Ko‘chirish yozuvini to‘g‘ridan-to‘g‘ri sozlamalarga (LegacyImport shunday saqlaydi). */
    private fun storeLastImport(notes: List<String>?) {
        services.db.write { conn ->
            services.settings.put(conn, Settings.LEGACY_IMPORT, "2026-10-29T09:00|reaktivlar-jadval.json")
            services.settings.put(conn, "legacy_import_notes", notes?.joinToString("\n"))
        }
    }

    private fun SkikoComposeUiTest.inside(tag: String, text: String) = onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun startupMessageOpensTheBackupPageWhereAllNotesOfTheLastImportAreShown() = runDesktopComposeUiTest(1366, 768) {
        storeLastImport(notes)
        val state = AppState(services, FakeFileDialogs())
        showLegacyResult(state, LegacyResult(imported = 6, skipped = 0, notes = notes))
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        inside("message-info", "… yana 3 ta izoh.").assertExists()
        onNode(hasText("Barcha izohlar (6 ta)") and hasAnyAncestor(hasTestTag("message-info"))).performClick()
        waitForIdle()
        assertEquals(Page.BACKUP, state.page)
        onNodeWithTag("message-info").assertDoesNotExist()

        inside("backup-legacy-last", "reaktivlar-jadval.json").performScrollTo().assertExists()
        onNodeWithTag("backup-legacy-last-notes").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("legacy-notes-dialog").assertExists()
        inside("legacy-notes-dialog", "6 ta izoh").assertExists()
        notes.forEach { inside("legacy-notes-dialog", it).assertExists() }
        onNodeWithTag("legacy-notes-dialog-close").performClick()
        waitForIdle()
        onNodeWithTag("legacy-notes-dialog").assertDoesNotExist()
    }

    @Test
    fun noStoredNotesMeansNoNotesButton() = runDesktopComposeUiTest(1366, 768) {
        storeLastImport(null)
        setContent { ReaktivControlApp(AppState(services, FakeFileDialogs(), Page.BACKUP)) }
        waitForIdle()
        inside("backup-legacy-last", "reaktivlar-jadval.json").performScrollTo().assertExists()
        onNodeWithTag("backup-legacy-last-notes").assertDoesNotExist()
    }
}

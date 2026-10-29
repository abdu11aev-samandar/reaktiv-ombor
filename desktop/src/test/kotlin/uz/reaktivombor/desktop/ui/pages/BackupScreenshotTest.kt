package uz.reaktivombor.desktop.ui.pages

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.SheetRow
import uz.reaktivombor.desktop.SheetStore
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.DemoData
import uz.reaktivombor.desktop.ui.FakeFileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * «Ma’lumotlarni zaxiralash», «Sozlamalar» va «Exceldan import» 1366×768 da (haqiqiy dastur + namuna baza):
 * skrinshotlar desktop/build/ui-screens/backupui ga yoziladi va ko‘z bilan tekshiriladi.
 */
@OptIn(ExperimentalTestApi::class)
class BackupScreenshotTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private val dialogs = FakeFileDialogs()

    @Before
    fun setUp() {
        services = DemoData.seeded(tmp.newFolder("data").toPath()).first
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    private fun SkikoComposeUiTest.save(name: String) {
        waitForIdle()
        val dir = File("build/ui-screens/backupui").apply { mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SkikoComposeUiTest.app(page: Page): AppState {
        val state = AppState(services, dialogs, page)
        setContent { ReaktivControlApp(state) }
        waitForIdle()
        return state
    }

    /** Tugma oynaga to‘liq sig‘adi (kesilmagan). */
    private fun SkikoComposeUiTest.assertInWindow(tag: String) {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        val right = node.positionInRoot.x + node.size.width
        val bottom = node.positionInRoot.y + node.size.height
        assertTrue("$tag: ${node.positionInRoot} ${node.size}", node.positionInRoot.x >= 0f && right <= 1366f && bottom <= 768f)
    }

    private fun seedBackups() {
        val auto = services.backup.autoBackupIfDue(DemoData.TODAY)!!
        services.backup.restore(auto)
        services.backup.create(services.backup.dir.resolve("oy-oxiri-nusxa.db"))
    }

    @Test
    fun backupPage() = runDesktopComposeUiTest(1366, 768) {
        seedBackups()
        app(Page.BACKUP)
        assertInWindow("backup-create")
        assertInWindow("backup-restore-file")
        save("01-backup")
        onNodeWithTag("backup-legacy-pick").performScrollTo()
        assertInWindow("backup-legacy-pick")
        save("02-backup-bottom")
    }

    @Test
    fun backupEmpty() = runDesktopComposeUiTest(1366, 768) {
        app(Page.BACKUP)
        save("03-backup-empty-list")
    }

    @Test
    fun backupCreateAndRestore() = runDesktopComposeUiTest(1366, 768) {
        seedBackups()
        val file = services.backup.create(tmp.root.toPath().resolve("flesh/reaktiv-control-2026-10-29-0900.db"))
        dialogs.saveAnswer = tmp.root.toPath().resolve("flesh/reaktiv-control-2026-10-29-0905.db")
        dialogs.openAnswer = file
        app(Page.BACKUP)
        onNodeWithTag("backup-create").performClick()
        onNodeWithTag("backup-restore-file").performClick()
        assertInWindow("restore-confirm")
        save("04-backup-restore-dialog")
        onNodeWithTag("restore-confirm").performClick()
        save("05-backup-after-restore")
    }

    @Test
    fun backupLegacyImport() = runDesktopComposeUiTest(1366, 768) {
        val rows = (1..12).map { i ->
            SheetRow(i.toLong(), if (i % 4 == 0) "" else "Eski reaktiv $i", "", "${i * 100}", if (i == 5) "xyz" else "ml", if (i % 3 == 0) "" else "01.0${i % 9 + 1}.2027", "${i * 100}", "Gistologiya", "", createdAt = i.toLong())
        }
        val file = tmp.root.toPath().resolve("reaktivlar-jadval.json")
        Files.writeString(file, SheetStore.toJson(rows, "2026-10-20T10:00:00"))
        dialogs.openAnswer = file
        app(Page.BACKUP)
        onNodeWithTag("backup-legacy-pick").performScrollTo().performClick()
        save("06-backup-legacy-confirm")
        onNode(hasText("Ko‘chirish") and hasAnyAncestor(isDialog())).performClick()
        onNodeWithTag("backup-legacy-notes").performScrollTo()
        save("07-backup-legacy-result")
        onNodeWithTag("backup-legacy-notes").performClick()
        save("08-backup-legacy-notes")
    }

    @Test
    fun settingsPage() = runDesktopComposeUiTest(1366, 768) {
        services.settings.orgName = "Respublika sud-tibbiy ekspertiza ilmiy-amaliy markazi"
        services.settings.actorName = "Karimova D."
        services.settings.setTableLayout("reagents", """{"order":["name"]}""")
        services.backup.autoBackupIfDue(DemoData.TODAY)
        app(Page.SETTINGS)
        assertInWindow("settings-people-save")
        assertInWindow("settings-keep-save")
        save("09-settings")
        onNodeWithTag("settings-about").performScrollTo()
        save("10-settings-about")
    }

    @Test
    fun settingsErrorsAndReset() = runDesktopComposeUiTest(1366, 768) {
        services.settings.setTableLayout("reagents", """{"order":["name"]}""")
        app(Page.SETTINGS)
        onNodeWithTag("settings-org").performTextReplacement("A".repeat(201))
        onNodeWithTag("settings-people-save").performClick()
        onNodeWithTag("settings-keep").performTextReplacement("0")
        onNodeWithTag("settings-keep-save").performClick()
        save("11-settings-errors")
        onNodeWithTag("settings-reset-layouts").performClick()
        save("12-settings-reset-confirm")
    }

    @Test
    fun excelImport() = runDesktopComposeUiTest(1366, 768) {
        dialogs.openAnswer = AddReagentImportUiTest.filledFile(tmp.root.toPath().resolve("partiyalar.xlsx"), DemoData.TODAY)
        app(Page.ADD_REAGENT)
        onNodeWithTag("add-goto-import").performClick()
        save("13-add-import-section")
        onNodeWithTag("import-pick").performClick()
        assertInWindow("import-run")
        save("14-add-import-preview")
        onNodeWithTag("import-preview-row-5").performClick()
        save("15-add-import-row-details")
        onNodeWithTag("import-run").performClick()
        save("16-add-import-confirm")
        onNode(hasText("2 ta qatorni import qilish") and hasAnyAncestor(isDialog())).performClick()
        save("17-add-import-done")
    }
}

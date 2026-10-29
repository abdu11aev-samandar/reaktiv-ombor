package uz.reaktivombor.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import uz.reaktivombor.desktop.core.AppLogging
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.service.LegacyResult
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.APP_NAME
import uz.reaktivombor.desktop.ui.APP_SUBTITLE
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FileDialogs
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import uz.reaktivombor.desktop.ui.theme.Severity
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private const val WINDOW_TITLE = "$APP_NAME — $APP_SUBTITLE"
private const val DEFAULT_WIDTH = 1366
private const val DEFAULT_HEIGHT = 768
private const val MIN_WIDTH = 1280
private const val MIN_HEIGHT = 720

private val log: Logger = Logger.getLogger("uz.reaktivombor.Main")

/** Ikkinchi nusxa bitta bazaga yozmasligi uchun (ma’lumotlar papkasidagi .lock). */
private var instanceLock: FileLock? = null

fun main(args: Array<String>) {
    // ---- SELF-TEST: bosh muhandis shu yerga "--self-test <fayl>" tarmog‘ini qo‘shadi (UI va qulfdan oldin). ----

    val dataDir = DataDir.default()
    try {
        Files.createDirectories(dataDir)
    } catch (e: Exception) {
        fatal("Ma’lumotlar papkasini yaratib bo‘lmadi:\n$dataDir\n\n${e.message ?: e}")
    }

    instanceLock = acquireLock(DataDir.lockFile(dataDir))
    if (instanceLock == null) {
        JOptionPane.showMessageDialog(
            null,
            "«$APP_NAME» dasturi allaqachon ochiq.\nIkkinchi nusxani ochib bo‘lmaydi — ochiq oynadan foydalaning.",
            APP_NAME,
            JOptionPane.INFORMATION_MESSAGE,
        )
        return
    }

    // Jurnal yozilmasa ham dastur ishlayveradi (masalan, papka faqat o‘qish uchun).
    runCatching { AppLogging.setup(dataDir) }.onFailure { System.err.println("Jurnalni yoqib bo‘lmadi: ${it.message}") }
    AppLogging.installUncaughtHandler { _, _ -> showErrorLater(AppLogging.GENERIC_ERROR) }
    log.info("start, data dir=$dataDir")

    val services = try {
        Services.open(dataDir)
    } catch (e: UserError) {
        log.log(Level.WARNING, "database refused", e)
        fatal(e.message)
    } catch (e: Exception) {
        log.log(Level.SEVERE, "database open failed", e)
        fatal("Ma’lumotlar bazasini ochib bo‘lmadi:\n${DataDir.dbFile(dataDir)}\n\nTafsilotlar jurnal faylida (logs papkasi).")
    }

    // Eski jadvaldan bir martalik ko‘chirish — oyna ochilishidan oldin (ma’lumot darhol ko‘rinsin).
    val legacy: LegacyResult? = try {
        services.legacy.autoImportIfNeeded()
    } catch (e: Exception) {
        log.log(Level.WARNING, "legacy import failed", e)
        LegacyResult(0, 0, listOf("Eski ma’lumotlarni ko‘chirib bo‘lmadi (tafsiloti jurnalda)."))
    }

    // Kunlik avtomatik zaxira — fonda; xato bo‘lsa faqat jurnalga yoziladi.
    Thread({
        try {
            services.backup.autoBackupIfDue(services.today())?.let { log.info("auto backup: ${it.fileName}") }
        } catch (e: Exception) {
            log.log(Level.WARNING, "auto backup failed", e)
        }
    }, "auto-backup").apply { isDaemon = true }.start()

    val dialogs = AwtFileDialogs()
    val state = AppState(services, dialogs)
    legacy?.let { showLegacyResult(state, it) }

    // Windows o‘chirilganda oyna yopilish xabarini olmaydi — baza baribir to‘g‘ri yopilsin.
    Runtime.getRuntime().addShutdownHook(Thread { closeServices(services) })

    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val fits = screen.width >= DEFAULT_WIDTH && screen.height >= DEFAULT_HEIGHT

    application {
        val windowState = rememberWindowState(
            placement = if (fits) WindowPlacement.Floating else WindowPlacement.Maximized,
            width = minOf(DEFAULT_WIDTH, screen.width).dp,
            height = minOf(DEFAULT_HEIGHT, screen.height).dp,
            position = WindowPosition(Alignment.Center),
        )
        val icon = remember {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { BitmapPainter(loadImageBitmap(it)) }
        }
        Window(
            onCloseRequest = {
                closeServices(services)
                exitApplication()
            },
            title = WINDOW_TITLE,
            icon = icon,
            state = windowState,
        ) {
            LaunchedEffect(Unit) {
                dialogs.frame = window
                window.minimumSize = Dimension(minOf(MIN_WIDTH, screen.width), minOf(MIN_HEIGHT, screen.height))
            }
            // Yarim tunda sana o‘zgarsa — holatlar va eslatmalar qayta hisoblanadi.
            LaunchedEffect(Unit) {
                var day = state.today()
                while (true) {
                    delay(60_000)
                    val now = runCatching { state.today() }.getOrNull() ?: continue
                    if (now != day) {
                        day = now
                        state.refresh()
                    }
                }
            }
            ReaktivControlApp(state)
        }
    }
}

/** Eski jadvaldan ko‘chirish natijasi — bir marta, xabar sifatida. */
private fun showLegacyResult(state: AppState, result: LegacyResult) {
    val notes = result.notes.take(3).joinToString(" ")
    when {
        result.imported > 0 -> state.showMessage(
            "Eski jadvaldan ${result.imported} ta partiya ko‘chirildi" +
                (if (result.skipped > 0) ", ${result.skipped} ta qator o‘tkazib yuborildi" else "") + ". " +
                "Eski fayl o‘zgartirilmadi." + if (notes.isNotEmpty()) " $notes" else "",
            severity = if (result.skipped > 0) Severity.WARNING else Severity.INFO,
            title = "Eski ma’lumotlar ko‘chirildi",
            autoHide = false,
        )
        notes.isNotEmpty() -> state.showMessage(notes, Severity.WARNING, title = "Eski ma’lumotlar", autoHide = false)
    }
}

private fun closeServices(services: Services) {
    runCatching { if (services.db.isOpen) services.db.close() }.onFailure { log.log(Level.WARNING, "db close failed", it) }
    runCatching { instanceLock?.release() }
}

private fun fatal(message: String): Nothing {
    runCatching { JOptionPane.showMessageDialog(null, message, APP_NAME, JOptionPane.ERROR_MESSAGE) }
    runCatching { instanceLock?.release() }
    exitProcess(1)
}

private fun showErrorLater(message: String) {
    SwingUtilities.invokeLater {
        runCatching { JOptionPane.showMessageDialog(null, message, APP_NAME, JOptionPane.ERROR_MESSAGE) }
    }
}

private fun acquireLock(path: Path): FileLock? = runCatching {
    val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    channel.tryLock() ?: run {
        channel.close()
        null
    }
}.getOrNull()

/** Tizimning o‘z fayl tanlash oynalari (AWT FileDialog). [frame] oyna ochilgach beriladi. */
class AwtFileDialogs(var frame: Frame? = null) : FileDialogs {
    override fun save(title: String, defaultName: String, extension: String): Path? {
        val ext = "." + extension.removePrefix(".")
        val dialog = FileDialog(frame, title, FileDialog.SAVE)
        dialog.file = if (defaultName.endsWith(ext, ignoreCase = true)) defaultName else defaultName + ext
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val directory = dialog.directory ?: return null
        if (name.endsWith(ext, ignoreCase = true)) return Paths.get(directory, name)
        // Windows oynasi kengaytmani o‘zi qo‘shmaydi. Qo‘shilgan nom bilan fayl bo‘lsa — oyna buni so‘ramagan, biz so‘raymiz.
        val path = Paths.get(directory, name + ext)
        if (Files.exists(path)) {
            val options = arrayOf("Ustidan yozish", "Bekor qilish")
            val answer = JOptionPane.showOptionDialog(
                frame,
                "«${path.fileName}» fayli allaqachon bor. Ustidan yozilsinmi?",
                title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1],
            )
            if (answer != 0) return null
        }
        return path
    }

    override fun open(title: String, extensions: List<String>): Path? {
        val exts = extensions.map { it.removePrefix(".").lowercase() }
        val dialog = FileDialog(frame, title, FileDialog.LOAD)
        // Windows filtrni nom shablonidan oladi, boshqa tizimlar — filenameFilter’dan.
        if (exts.isNotEmpty()) {
            dialog.file = exts.joinToString(";") { "*.$it" }
            dialog.filenameFilter = java.io.FilenameFilter { _: File, name: String -> exts.any { name.lowercase().endsWith(".$it") } }
        }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val directory = dialog.directory ?: return null
        return Paths.get(directory, name)
    }

    override fun openFolder(dir: Path) {
        runCatching {
            Files.createDirectories(dir)
            Desktop.getDesktop().open(dir.toFile())
        }.onFailure { log.log(Level.WARNING, "open folder failed: $dir", it) }
    }
}

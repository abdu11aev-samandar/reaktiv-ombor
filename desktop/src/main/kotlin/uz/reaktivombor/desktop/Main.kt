package uz.reaktivombor.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane

/** Ikkita oyna bitta faylga yozib, bir-birini o‘chirib yubormasligi uchun. */
private var instanceLock: FileLock? = null

fun main() {
    val store = SheetStore(SheetStore.defaultFile())
    Files.createDirectories(store.file.parent)

    instanceLock = acquireLock(store.file.resolveSibling(".reaktivlar.lock"))
    if (instanceLock == null) {
        JOptionPane.showMessageDialog(null, "«Reaktivlar» dasturi allaqachon ochiq.", "Reaktivlar hisobi", JOptionPane.INFORMATION_MESSAGE)
        return
    }

    val loaded = store.load()
    val state = SheetState(loaded.rows)

    application {
        val windowState = rememberWindowState(width = 1320.dp, height = 820.dp, position = WindowPosition(Alignment.Center))
        val icon = remember {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { BitmapPainter(loadImageBitmap(it)) }
        }
        Window(
            onCloseRequest = {
                runCatching { store.save(state.rows) }
                exitApplication()
            },
            title = "Reaktivlar hisobi",
            icon = icon,
            state = windowState,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(1000, 560) }
            ReaktivlarApp(state, store, AwtFileDialogs(window), loaded.warning)
        }
    }
}

private fun acquireLock(path: Path): FileLock? = runCatching {
    val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    channel.tryLock() ?: run {
        channel.close()
        null
    }
}.getOrNull()

/** Windows’ning o‘z fayl tanlash oynalari. */
class AwtFileDialogs(private val frame: Frame) : FileDialogs {
    override fun openJson(): Path? {
        val dialog = FileDialog(frame, "Zaxira faylini tanlang (.json)", FileDialog.LOAD)
        dialog.file = "*.json"
        dialog.isVisible = true
        return dialog.file?.let { Paths.get(dialog.directory, it) }
    }

    override fun saveFile(title: String, defaultName: String): Path? {
        val dialog = FileDialog(frame, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        return dialog.file?.let { Paths.get(dialog.directory, it) }
    }

    override fun openFolder(dir: Path) {
        runCatching { Desktop.getDesktop().open(dir.toFile()) }
    }
}

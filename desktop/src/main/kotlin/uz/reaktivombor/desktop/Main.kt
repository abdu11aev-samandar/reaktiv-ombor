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
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
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

    // Vazifalar paneli chiqarib tashlangan ekran maydoni. Oyna undan katta bo‘lsa, sarlavhasi ekrandan chiqib ketadi.
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val fits = screen.width >= WIDTH && screen.height >= HEIGHT

    application {
        val windowState = rememberWindowState(
            placement = if (fits) WindowPlacement.Floating else WindowPlacement.Maximized,
            width = minOf(WIDTH, screen.width).dp,
            height = minOf(HEIGHT, screen.height).dp,
            position = WindowPosition(Alignment.Center),
        )
        val icon = remember {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { BitmapPainter(loadImageBitmap(it)) }
        }
        Window(
            onCloseRequest = {
                // Katakda yozib turilgan (Enter bosilmagan) qiymat ham saqlansin.
                state.flushPendingEdit()
                val error = runCatching { store.save(state.rows) }.exceptionOrNull()
                if (error == null || confirmCloseWithoutSaving(error)) exitApplication()
            },
            title = "Reaktivlar hisobi",
            icon = icon,
            state = windowState,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(minOf(1000, screen.width), minOf(560, screen.height)) }
            ReaktivlarApp(state, store, AwtFileDialogs(window), loaded.warning)
        }
    }
}

private const val WIDTH = 1320
private const val HEIGHT = 820

private fun confirmCloseWithoutSaving(error: Throwable): Boolean {
    val options = arrayOf("Saqlamasdan yopish", "Ochiq qoldirish")
    val answer = JOptionPane.showOptionDialog(
        null,
        "Ma’lumotlarni saqlab bo‘lmadi:\n${error.message ?: error}\n\n" +
            "Hozir yopilsa, oxirgi o‘zgarishlar yo‘qoladi. Dasturni ochiq qoldirib,\n" +
            "«Fayl» menyusi orqali boshqa joyga saqlab olishingiz mumkin.",
        "Reaktivlar hisobi",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.WARNING_MESSAGE,
        null,
        options,
        options[1],
    )
    return answer == 0
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
        val name = dialog.file ?: return null
        val ext = "." + defaultName.substringAfterLast('.')
        if (name.endsWith(ext, ignoreCase = true)) return Paths.get(dialog.directory, name)
        // Windows oynasi kengaytmani o‘zi qo‘shmaydi. Qo‘shilgan nom bilan fayl bo‘lsa — oyna buni so‘ramagan, biz so‘raymiz.
        val path = Paths.get(dialog.directory, name + ext)
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

    override fun openFolder(dir: Path) {
        runCatching { Desktop.getDesktop().open(dir.toFile()) }
    }
}

package uz.reaktivombor.desktop.core

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Jurnal: `<ma’lumotlar papkasi>/logs/reaktiv-control.N.log`, har biri 1 MB gacha, 5 ta fayl aylanib yoziladi.
 * INFO darajasida reaktiv ma’lumotlari yozilmaydi — faqat operatsiya raqamlari.
 */
object AppLogging {
    const val LOGGER_NAME = "uz.reaktivombor"
    const val FILE_LIMIT = 1_000_000
    const val FILE_COUNT = 5

    /** Dastur kodi uchun logger. */
    val log: Logger get() = Logger.getLogger(LOGGER_NAME)

    private var handler: Handler? = null

    /** Jurnalni yoqadi (qayta chaqirilsa — eski fayl ulagichi almashtiriladi). Jurnallar papkasini qaytaradi. */
    @Synchronized
    fun setup(dataDir: Path, level: Level = Level.INFO): Path {
        val dir = DataDir.logsDir(dataDir)
        Files.createDirectories(dir)
        // FileHandler shablonida "%" maxsus belgi, "/" — papka ajratgichi.
        val pattern = dir.toAbsolutePath().toString().replace("%", "%%").replace('\\', '/') + "/reaktiv-control.%g.log"
        val fileHandler = FileHandler(pattern, FILE_LIMIT, FILE_COUNT, true).apply {
            encoding = "UTF-8"
            formatter = LineFormatter()
            this.level = level
        }
        val root = Logger.getLogger("")
        handler?.let { old ->
            root.removeHandler(old)
            runCatching { old.close() }
        }
        root.addHandler(fileHandler)
        Logger.getLogger(LOGGER_NAME).level = level
        handler = fileHandler
        return dir
    }

    /**
     * Ushlanmagan xatolar jurnalga yoziladi, so‘ng [show] chaqiriladi (UI o‘zbekcha xato oynasini ko‘rsatadi).
     * [show] o‘zi xato bersa ham dastur yiqilmaydi.
     */
    fun installUncaughtHandler(show: (Thread, Throwable) -> Unit = { _, _ -> }) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log.log(Level.SEVERE, "Kutilmagan xato (${thread.name})", error) }
            runCatching { show(thread, error) }
        }
    }

    /** Foydalanuvchiga ko‘rsatiladigan umumiy xabar (tafsilot jurnalda). */
    const val GENERIC_ERROR = "Kutilmagan xato yuz berdi. Ma’lumotlar o‘zgartirilmadi. Tafsilotlar jurnal faylida (logs papkasi)."

    private class LineFormatter : Formatter() {
        private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        override fun format(record: LogRecord): String {
            val sb = StringBuilder()
            sb.append(time.format(Instant.ofEpochMilli(record.millis))).append(' ')
                .append(record.level.name).append(' ')
                .append(record.loggerName ?: "").append(": ")
                .append(formatMessage(record)).append(System.lineSeparator())
            record.thrown?.let { error ->
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                sb.append(sw)
            }
            return sb.toString()
        }
    }
}

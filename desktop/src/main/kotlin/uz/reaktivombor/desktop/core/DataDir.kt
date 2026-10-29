package uz.reaktivombor.desktop.core

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Ma’lumotlar papkasi. Hech qachon o‘rnatish papkasida emas (u yangilanishda almashtiriladi):
 * Windows — %APPDATA%\ReaktivControl, macOS — ~/Library/Application Support/ReaktivControl,
 * Linux — ${XDG_DATA_HOME:-~/.local/share}/reaktiv-control. Testlar va self-test: `-Dreaktiv.dataDir=…`.
 */
object DataDir {
    const val PROPERTY = "reaktiv.dataDir"
    const val APP_DIR = "ReaktivControl"
    const val DB_FILE = "reaktiv-control.db"

    fun default(): Path {
        System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let { return Paths.get(it).toAbsolutePath() }
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        return when {
            os.contains("win") -> Paths.get(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: "$home\\AppData\\Roaming", APP_DIR)
            os.contains("mac") -> Paths.get(home, "Library", "Application Support", APP_DIR)
            else -> Paths.get(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "reaktiv-control")
        }
    }

    fun dbFile(dataDir: Path): Path = dataDir.resolve(DB_FILE)

    fun backupsDir(dataDir: Path): Path = dataDir.resolve("backups")

    fun logsDir(dataDir: Path): Path = dataDir.resolve("logs")

    /** Ikkinchi nusxa ochilmasligi uchun qulf fayli. */
    fun lockFile(dataDir: Path): Path = dataDir.resolve(".lock")
}

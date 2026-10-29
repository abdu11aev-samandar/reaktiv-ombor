package uz.reaktivombor.desktop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.logging.Handler
import java.util.logging.Logger

class DataDirAndLoggingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun dataDirCanBeOverriddenAndHasFixedLayout() {
        val dir = tmp.newFolder("Malumotlar papkasi").toPath()
        val old = System.getProperty(DataDir.PROPERTY)
        try {
            System.setProperty(DataDir.PROPERTY, dir.toString())
            assertEquals(dir.toAbsolutePath(), DataDir.default())
        } finally {
            if (old == null) System.clearProperty(DataDir.PROPERTY) else System.setProperty(DataDir.PROPERTY, old)
        }
        assertEquals("reaktiv-control.db", DataDir.dbFile(dir).fileName.toString())
        assertEquals(dir.resolve("backups"), DataDir.backupsDir(dir))
        assertEquals(dir.resolve("logs"), DataDir.logsDir(dir))
        assertEquals(dir.resolve(".lock"), DataDir.lockFile(dir))
        // Xususiyatsiz: foydalanuvchi profilidagi papka (o‘rnatish papkasi emas).
        if (old == null) {
            val name = DataDir.default().fileName.toString()
            assertTrue(name, name == "ReaktivControl" || name == "reaktiv-control")
        }
    }

    @Test
    fun loggingWritesRotatingFilesIntoTheLogsFolder() {
        val dir = tmp.newFolder("data").toPath()
        val root = Logger.getLogger("")
        val before = root.handlers.toSet()
        val logs = AppLogging.setup(dir)
        val added: List<Handler> = root.handlers.filter { it !in before }
        try {
            assertEquals(DataDir.logsDir(dir), logs)
            AppLogging.log.info("sinov yozuvi o‘zbekcha")
            added.forEach { it.flush() }
            val file = logs.resolve("reaktiv-control.0.log")
            assertTrue(Files.exists(file))
            assertTrue(Files.readString(file).contains("sinov yozuvi o‘zbekcha"))
            // Qayta sozlansa — eski ulagich almashtiriladi (ikki marta yozilmaydi).
            AppLogging.setup(dir)
            assertEquals(1, root.handlers.count { it !in before })
        } finally {
            root.handlers.filter { it !in before }.forEach {
                root.removeHandler(it)
                it.close()
            }
        }
    }
}

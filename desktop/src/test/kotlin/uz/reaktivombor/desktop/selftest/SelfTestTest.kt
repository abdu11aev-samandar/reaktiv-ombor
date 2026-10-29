package uz.reaktivombor.desktop.selftest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.report.ReportKind
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class SelfTestTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun selfTestPassesEveryStepAndWritesAUtf8Report() {
        val headless = System.getProperty("java.awt.headless")
        val dataDirProperty = System.getProperty(DataDir.PROPERTY)
        val output = tmp.root.toPath().resolve("natija/selftest.txt")
        val started = System.nanoTime()
        val code = try {
            runSelfTest(output)
        } finally {
            if (headless == null) System.clearProperty("java.awt.headless") else System.setProperty("java.awt.headless", headless)
        }
        val seconds = (System.nanoTime() - started) / 1e9
        val lines = Files.readAllLines(output, StandardCharsets.UTF_8)
        val text = lines.joinToString("\n")
        assertEquals(text, 0, code)
        assertTrue("60 s dan kam bo‘lishi kerak: $seconds", seconds < 60)

        val steps = lines.filterNot { it.startsWith("#") }
        assertTrue(text, steps.all { it.startsWith("OK  ") })
        // 13 asosiy qadam + har bir hisobot turi uchun bittadan.
        assertEquals(text, 13 + ReportKind.entries.size, steps.size)
        assertTrue(text, steps.any { it.contains("Etil spirti: 19 l kelgan, 14 l qoldiq") })
        assertTrue(text, steps.any { it.contains("Hisobot «Muddati o‘tgan reaktivlar»") })
        // Muhit haqida ma’lumot.
        assertTrue(text, lines.any { it.startsWith("# OS: ") })
        assertTrue(text, lines.any { it.startsWith("# Java: ") })
        assertTrue(text, lines.any { it.startsWith("# SQLite: 3.") })
        assertTrue(text, lines.last().startsWith("# Natija: ${steps.size}/${steps.size} OK"))
        // Vaqtinchalik papka ishlatildi va o‘chirildi; dastur papkasi sozlamasi o‘zgarmadi.
        val dataDir = lines.first { it.startsWith("# Ma’lumotlar papkasi (vaqtinchalik): ") }.substringAfter(": ")
        assertTrue(dataDir, Files.notExists(Paths.get(dataDir)))
        assertEquals(dataDirProperty, System.getProperty(DataDir.PROPERTY))
    }

    @Test
    fun unwritableOutputGivesExitCode3() {
        val blocker = tmp.newFile("fayl")
        assertEquals(3, runSelfTest(blocker.toPath().resolve("ichida/natija.txt")))
    }
}

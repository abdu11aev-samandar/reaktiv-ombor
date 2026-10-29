package uz.reaktivombor.desktop.core

import org.dhatim.fastexcel.Workbook
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * .xlsx yozish (fastexcel). fastexcel zip ichida Zip64 ko‘rinishidagi 24 baytli ma’lumot tavsiflagichlarini yozadi —
 * Excel ochadi, LibreOffice esa «fayl buzilgan» deydi. Shuning uchun tayyor fayl oddiy zip sifatida qayta yoziladi.
 */
object Xlsx {
    const val APP_NAME = "Reaktiv Control"

    fun write(out: OutputStream, build: (Workbook) -> Unit) {
        val temp = Files.createTempFile("reaktiv-control-", ".xlsx")
        try {
            Files.newOutputStream(temp).use { raw ->
                val workbook = Workbook(raw, APP_NAME, "1.0")
                build(workbook)
                workbook.finish()
            }
            val now = System.currentTimeMillis()
            ZipFile(temp.toFile()).use { zip ->
                val target = ZipOutputStream(NonClosing(out))
                for (entry in zip.entries().asSequence()) {
                    target.putNextEntry(ZipEntry(entry.name).apply { time = now })
                    zip.getInputStream(entry).use { it.copyTo(target) }
                    target.closeEntry()
                }
                target.close()
            }
        } finally {
            runCatching { Files.deleteIfExists(temp) }
        }
    }

    /** Chaqiruvchining oqimi yopilmaydi (uni o‘zi yopadi). */
    private class NonClosing(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)

        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

        override fun flush() = out.flush()

        override fun close() = out.flush()
    }
}

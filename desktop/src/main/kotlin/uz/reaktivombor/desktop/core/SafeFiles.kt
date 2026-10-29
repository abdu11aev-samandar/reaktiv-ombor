package uz.reaktivombor.desktop.core

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Fayllarni xavfsiz yozish (hisobotlar, Excel shabloni). */
object SafeFiles {
    /**
     * Avval vaqtinchalik faylga yoziladi, so‘ng [target] o‘rniga qo‘yiladi: xato bo‘lsa yarim yozilgan fayl qolmaydi.
     * Fayl boshqa dasturda ochiq bo‘lsa (Windows’da Excel uni qulflaydi) — tushunarli xabar.
     */
    fun write(target: Path, write: (OutputStream) -> Unit): Path {
        val file = target.toAbsolutePath()
        val dir = file.parent ?: throw UserError("Faylni saqlash uchun papka tanlang.")
        try {
            Files.createDirectories(dir)
        } catch (e: IOException) {
            throw UserError("Papkaga yozib bo‘lmadi: $dir (${e.message ?: e.javaClass.simpleName}).")
        }
        // Oddiy fayl (createTempFile’ning faqat egasiga ochiq huquqlari emas) — saqlangan hisobotni boshqalar ham ocha oladi.
        val temp = dir.resolve(".${file.fileName}.${System.nanoTime()}.tmp")
        try {
            BufferedOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)).use { out -> write(out) }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            runCatching { Files.deleteIfExists(temp) }
            throw UserError(
                "«${file.fileName}» faylini yozib bo‘lmadi — u boshqa dasturda (masalan, Excelda) ochiq bo‘lishi mumkin. " +
                    "Faylni yopib, qayta urinib ko‘ring. (${e.message ?: e.javaClass.simpleName})",
            )
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(temp) }
            throw t
        }
        return file
    }
}

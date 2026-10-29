package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.db.Database
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate

/**
 * UI xizmatlarga faqat shu obyekt orqali murojaat qiladi (dastur uchun bitta nusxa).
 * Barcha yozishlar bitta [Database] orqali navbat bilan; zaxiradan tiklanganda ham shu obyektlar ishlayveradi.
 */
class Services(val db: Database, val dataDir: Path, val clock: Clock = Clock.systemDefaultZone()) {
    val audit: Audit = Audit(db, clock)
    val settings: Settings = Settings(db, audit)
    val directory: Directory = Directory(db, audit, clock)
    val reagents: Reagents = Reagents(db, audit, directory, clock)
    val stock: Stock = Stock(db, audit, directory, clock)
    val queries: Queries = Queries(db, audit)
    val backup: Backup = Backup(db, audit, settings, DataDir.backupsDir(dataDir), clock)
    val legacy: LegacyImport = LegacyImport(db, audit, settings, directory, reagents, clock)
    val excelImport: ExcelImport = ExcelImport(db, audit, directory, reagents, clock)

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {
        /** Ma’lumotlar papkasidagi bazani ochadi (yo‘q bo‘lsa yaratadi) va xizmatlarni tayyorlaydi. */
        fun open(dataDir: Path = DataDir.default(), clock: Clock = Clock.systemDefaultZone()): Services =
            Services(Database(DataDir.dbFile(dataDir)), dataDir, clock)
    }
}

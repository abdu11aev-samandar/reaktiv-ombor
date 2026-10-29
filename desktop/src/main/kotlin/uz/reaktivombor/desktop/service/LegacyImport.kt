package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.DateText
import uz.reaktivombor.desktop.SheetRow
import uz.reaktivombor.desktop.SheetStore
import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.charCount
import uz.reaktivombor.desktop.core.display
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.insert
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.isDepartmentName
import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.BackupException
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.logging.Level
import java.util.logging.Logger

/** Eski jadvaldan ko‘chirish natijasi: [imported] — yaratilgan partiyalar, [skipped] — o‘tkazib yuborilgan qatorlar. */
data class LegacyResult(val imported: Int, val skipped: Int, val notes: List<String>, val file: Path? = null)

/**
 * Eski dastur ma’lumotlari (reaktivlar-jadval.json, reaktivlar.json va ularning .bak.json nusxalari) → yangi baza.
 * Eski fayl faqat o‘qiladi. Bir fayl ikki marta ko‘chirilmaydi (mazmuni xeshi app_settings da saqlanadi).
 */
class LegacyImport(
    private val db: Database,
    private val audit: Audit,
    private val settings: Settings,
    private val directory: Directory,
    private val reagents: Reagents,
    private val clock: Clock,
    /** Eski jadval fayli qayerda (null — SheetStore.defaultFile(); testlar uchun `-Dreaktiv.legacyFile=…`). */
    private val legacyLocation: Path? = null,
) {
    companion object {
        const val PROPERTY = "reaktiv.legacyFile"
        private const val ADJUST_NOTE = "Eski jadvaldagi ombordagi miqdorga moslash"
        private const val SOURCE_NOTE = "Eski jadvaldan ko‘chirildi"
        private val log: Logger = Logger.getLogger("uz.reaktivombor.service.LegacyImport")
    }

    /** Ko‘chirish allaqachon bajarilganmi (avtomatik yoki qo‘lda). */
    fun isDone(): Boolean = settings.get(Settings.LEGACY_IMPORT) != null

    /** Eski fayl: avval yangi jadval, so‘ng uning zaxirasi, eski dastur fayli va uning zaxirasi — o‘qiladiganining birinchisi. */
    fun findLegacyFile(): Path? {
        val base = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
            ?: legacyLocation
            ?: SheetStore.defaultFile()
        val store = SheetStore(base)
        val candidates = listOf(store.file, store.backupFile, store.legacyFile, store.legacyFile.resolveSibling("reaktivlar.bak.json"))
            .distinct()
            .filter { Files.isRegularFile(it) }
        return candidates.firstOrNull { path ->
            runCatching { SheetStore.parse(Files.readString(path, StandardCharsets.UTF_8)) }.isSuccess
        } ?: candidates.firstOrNull()
    }

    /**
     * Dastur ochilganda: baza bo‘sh (partiya yo‘q), ko‘chirish hali bajarilmagan va eski fayl topilsa — ko‘chiradi.
     * Bajarilmasa null. Xato bo‘lsa natijada izoh bilan qaytadi (dastur ishlayveradi).
     */
    fun autoImportIfNeeded(): LegacyResult? {
        if (isDone()) return null
        if (db.read { conn -> conn.queryLong("SELECT COUNT(*) FROM batches") } > 0) return null
        val file = findLegacyFile() ?: return null
        return try {
            importFile(file)
        } catch (e: UserError) {
            LegacyResult(0, 0, listOf("Eski ma’lumotlarni ko‘chirib bo‘lmadi: ${e.message}"), file)
        } catch (e: Exception) {
            log.log(Level.WARNING, "legacy import failed", e)
            LegacyResult(0, 0, listOf("Eski ma’lumotlarni ko‘chirib bo‘lmadi (kutilmagan xato, tafsiloti jurnalda)."), file)
        }
    }

    /** Faylni ko‘chiradi — hammasi bitta tranzaksiyada (yarim ko‘chirilgan holat qolmaydi). */
    fun importFile(path: Path): LegacyResult {
        val text = try {
            Files.readString(path, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw UserError("Faylni o‘qib bo‘lmadi: ${path.fileName} (${e.message}).")
        }
        val parsed = try {
            SheetStore.parse(text)
        } catch (e: BackupException) {
            throw UserError(e.message ?: "Bu fayl eski dastur ma’lumotlari emas.")
        }
        val hash = sha256(text)
        val today = clock.today()
        val notes = mutableListOf<String>()
        var imported = 0
        var skipped = parsed.skipped
        if (parsed.skipped > 0) notes += "Faylda ${parsed.skipped} ta buzuq yozuv bor edi — o‘tkazib yuborildi."

        db.write { conn ->
            val done = settings.get(conn, Settings.LEGACY_IMPORT_HASHES).orEmpty().split(',').filter { it.isNotEmpty() }
            if (hash in done) throw UserError("Bu fayl allaqachon ko‘chirilgan — ikkinchi marta qo‘shilmaydi.")

            // Eski bo‘limlar ro‘yxati (yangi yozilmaganlari ham).
            parsed.allDepartments.filter(::isDepartmentName).forEach { directory.findOrCreate(conn, PlaceKind.DEPARTMENT, it, reactivate = false) }

            parsed.rows.forEachIndexed { index, row ->
                val number = index + 1
                when (val outcome = importRow(conn, row, number, today)) {
                    is RowOutcome.Skipped -> {
                        skipped++
                        notes += outcome.note
                    }
                    is RowOutcome.Imported -> {
                        imported++
                        notes += outcome.notes
                    }
                }
            }

            val now = clock.now()
            settings.put(conn, Settings.LEGACY_IMPORT, "$now|${path.fileName}")
            settings.put(conn, Settings.LEGACY_IMPORT_HASHES, (done + hash).joinToString(","))
            audit.log(
                conn, AuditAction.LEGACY_IMPORTED, "legacy", null,
                "Fayl: ${path.fileName}; ko‘chirildi: $imported ta partiya; o‘tkazib yuborildi: $skipped ta qator",
            )
        }
        log.info("legacy import done imported=$imported skipped=$skipped")
        return LegacyResult(imported, skipped, notes, path)
    }

    private sealed interface RowOutcome {
        class Skipped(val note: String) : RowOutcome
        class Imported(val notes: List<String>) : RowOutcome
    }

    private fun importRow(conn: Connection, row: SheetRow, number: Int, today: LocalDate): RowOutcome {
        val name = cleanName(row.name)
        val where = "$number-qator" + if (name.isNotEmpty()) " («$name»)" else ""
        if (name.isEmpty()) return RowOutcome.Skipped("$where: reaktiv nomi yo‘q — o‘tkazib yuborildi.")
        if (name.charCount() > Reagents.NAME_MAX) return RowOutcome.Skipped("$where: nomi juda uzun — o‘tkazib yuborildi.")
        val unit = MeasureUnit.of(row.unit) ?: return RowOutcome.Skipped(
            "$where: o‘lchov birligi noma’lum («${row.unit}») — o‘tkazib yuborildi.",
        )
        val notes = mutableListOf<String>()
        fun amount(raw: String, label: String): Long? {
            if (Amounts.normalize(raw).isEmpty()) return null
            val value = Amounts.parse(raw)
            if (value == null || value.signum() < 0 || value > Amounts.MAX) {
                notes += "$where: «$label» qiymati («$raw») son emas — hisobga olinmadi."
                return null
            }
            return value.multiply(BigDecimal.valueOf(unit.factor)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        }
        val received = amount(row.receivedAmount, "Kelgan miqdor")
        val stock = amount(row.stockAmount, "Ombordagi miqdor")
        val issued = amount(row.issuedAmount, "Berilgan miqdor")

        var produced = DateText.parse(row.producedAt)
        val legacyReceived = DateText.parse(row.legacyReceivedAt)
        val expires = DateText.parse(row.expiresAt)
        if (row.expiresAt.isNotBlank() && expires == null) notes += "$where: yaroqlilik muddati («${row.expiresAt}») sana emas — kiritilmadi."
        if (expires == null) notes += "$where: yaroqlilik muddati yo‘q — partiyani tahrirlab kiriting."
        if (produced != null && expires != null && expires < produced) {
            notes += "$where: ishlab chiqarilgan sana muddatidan keyin edi — kiritilmadi."
            produced = null
        }
        val department = cleanName(row.department).takeIf { it.isNotEmpty() && isDepartmentName(it) }
            ?.let { directory.findOrCreate(conn, PlaceKind.DEPARTMENT, it, reactivate = false) }

        val createdOn = row.createdAt.takeIf { it > 0 }
            ?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }
            ?.takeIf { it >= MIN_DATE && it <= today }
        val date = legacyReceived ?: createdOn ?: today

        val reagentId = reagents.findOrCreateReagent(conn, name, formula = "", minStock = null, note = "")
        val now = clock.now()
        val batchId = conn.insert(
            """
            INSERT INTO batches(reagent_id, series, manufacturer, produced_on, received_on, expires_on, unit, department_id,
                storage_location, responsible, note, archived, created_at, updated_at)
            VALUES (?, '', '', ?, ?, ?, ?, ?, '', '', ?, 0, ?, ?)
            """,
            reagentId, produced, legacyReceived, expires, unit, department?.id, SOURCE_NOTE, now, now,
        )

        val initial = maxOf(received ?: 0L, stock ?: 0L)
        if (initial > 0) {
            val receiptId = conn.insert(
                """
                INSERT INTO receipts(batch_id, received_on, qty, entered_qty, entered_unit, note, is_initial, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?)
                """,
                batchId, date, initial, Qty.format(initial, unit), unit, SOURCE_NOTE, now,
            )
            insertMovement(conn, batchId, "RECEIPT", initial, date, now, receiptId = receiptId)
        } else {
            notes += "$where: miqdor yo‘q — partiya qoldiqsiz qo‘shildi."
        }
        // Eski «Ombordagi miqdor» kelganidan kam bo‘lsa — farq moslash yozuvi bilan (tarix ko‘rinib turadi).
        val inStock = stock ?: initial
        if (inStock < initial) {
            insertMovement(conn, batchId, "ADJUSTMENT", -(initial - inStock), date, now, note = ADJUST_NOTE)
        }
        var given = issued ?: 0L
        if (given > inStock) {
            notes += "$where: berilgan miqdor ombordagidan ko‘p edi — ${Qty.formatWithUnit(inStock, unit)} gacha kamaytirildi."
            given = inStock
        }
        if (given > 0) {
            if (department != null) {
                val issueId = conn.insert(
                    "INSERT INTO issues(issued_on, doc_no, issued_by, note, created_at) VALUES (?, '', '', ?, ?)",
                    date, SOURCE_NOTE, now,
                )
                val itemId = conn.insert(
                    """
                    INSERT INTO issue_items(issue_id, batch_id, recipient_type, department_id, qty, entered_qty, entered_unit, note, created_at)
                    VALUES (?, ?, 'DEPARTMENT', ?, ?, ?, ?, ?, ?)
                    """,
                    issueId, batchId, department.id, given, Qty.format(given, unit), unit, SOURCE_NOTE, now,
                )
                insertMovement(conn, batchId, "ISSUE", -given, date, now, issueItemId = itemId)
            } else {
                insertMovement(conn, batchId, "ADJUSTMENT", -given, date, now, note = "Eski jadvalda berilgan (bo‘lim ko‘rsatilmagan)")
                notes += "$where: berilgan miqdorning bo‘limi ko‘rsatilmagan — moslash yozuvi bilan ayirildi."
            }
        }
        if (legacyReceived == null && createdOn == null) notes += "$where: kelgan sana noma’lum — kirim ${date.display()} bilan yozildi."
        return RowOutcome.Imported(notes)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

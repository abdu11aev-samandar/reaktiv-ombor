package uz.reaktivombor.desktop.service

import org.dhatim.fastexcel.BorderStyle
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.reader.Cell
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.Row
import uz.reaktivombor.desktop.DateText
import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.core.DuplicateBatchError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.SafeFiles
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.Xlsx
import uz.reaktivombor.desktop.core.charCount
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.db.Database
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.util.logging.Logger

/**
 * Fayldagi bitta qator: [rowNumber] — Excel’dagi qator raqami. [input] — yaratiladigan partiya (namuna qatorida null),
 * [errors] bo‘sh bo‘lsa qator import qilinadi; [warnings] — import qilinadi, lekin foydalanuvchi bilishi kerak
 * (masalan, yangi bo‘lim yaratiladi). [departmentName] — fayldagi bo‘lim nomi: bazada yo‘q bo‘lsa import paytida yaratiladi.
 */
data class ImportRow(
    val rowNumber: Int,
    val input: NewBatch?,
    val errors: List<String>,
    val warnings: List<String> = emptyList(),
    val departmentName: String? = null,
) {
    val valid: Boolean get() = input != null && errors.isEmpty()
}

/** [created] — yaratilgan partiyalar, [skipped] — xatosi bor (import qilinmagan) qatorlar. */
data class ImportResult(val created: Int, val skipped: Int)

/**
 * Partiyalarni Excel (.xlsx) faylidan qo‘shish: shablon → to‘ldirish → ko‘rib chiqish (har qatordagi xatolar) → import.
 * Ko‘rib chiqish «Reaktiv qo‘shish» formasidagi tekshiruvlarning o‘zini ishlatadi (yozib, so‘ng to‘liq qaytarib oladi —
 * bazada hech narsa qolmaydi). Import — hammasi yoki hech biri: xatosiz qatorlar bitta tranzaksiyada saqlanadi.
 */
class ExcelImport(
    private val db: Database,
    private val audit: Audit,
    private val directory: Directory,
    private val reagents: Reagents,
    private val clock: Clock,
) {
    /** Shablon ustunlari. [field] — NewBatch xususiyati (xato maydon nomi). */
    enum class Column(
        val header: String,
        val required: Boolean,
        val field: String,
        val help: String,
        val aliases: List<String> = emptyList(),
    ) {
        NAME("Reaktiv nomi", true, "name", "Reaktivning to‘liq nomi. Bir xil nom — bitta reaktiv (katta-kichik harf farqsiz).", listOf("Nomi", "Reaktiv")),
        FORMULA("Kimyoviy formula", false, "formula", "Masalan: C2H5OH.", listOf("Formula", "Formulasi")),
        MANUFACTURER("Ishlab chiqaruvchi", false, "manufacturer", "Zavod yoki firma nomi.", listOf("Ishlab chiqaruvchi korxona")),
        SERIES("Seriya raqami", true, "series", "Partiya (seriya, lot) raqami.", listOf("Seriya", "Seriyasi", "Lot")),
        PRODUCED("Ishlab chiqarilgan sana", false, "producedOn", "Kelgan sanadan keyin bo‘lmasin.", listOf("Ishlab chiqarilgan")),
        RECEIVED("Kelgan sana", true, "receivedOn", "Omborga kelgan kun (bugundan keyin emas).", listOf("Kelgan vaqt", "Qabul qilingan sana")),
        EXPIRES("Yaroqlilik muddati", true, "expiresOn", "Yaroqlilik muddati tugaydigan sana.", listOf("Muddati", "Yaroqlilik muddati (gacha)")),
        QTY("Kelgan miqdor", true, "initialQtyText", "Son: 10, 2,5 yoki 1250,5 (ko‘pi bilan 3 kasr xona).", listOf("Miqdor", "Miqdori")),
        UNIT("O‘lchov birligi", true, "unit", "l, ml, kg, g yoki dona (litr, мл, gr, шт ham bo‘ladi).", listOf("Birlik", "Birligi")),
        DEPARTMENT("Bo‘lim", false, "departmentId", "Bo‘lim nomi; ro‘yxatda yo‘q nom yozilsa — import paytida yangi bo‘lim qo‘shiladi.", listOf("Bo‘lim nomi")),
        STORAGE("Saqlash joyi", false, "storageLocation", "Masalan: 2-xona, 3-shkaf.", listOf("Joyi")),
        RESPONSIBLE("Mas’ul xodim", false, "responsible", "Partiya uchun mas’ul xodim.", listOf("Mas’ul")),
        RECEIVED_BY("Qabul qilgan xodim", false, "receivedBy", "Reaktivni qabul qilib olgan xodim.", listOf("Qabul qilgan")),
        MIN_STOCK("Minimal qoldiq", false, "minStockText", "Shu reaktivning umumiy qoldig‘i bundan kamaysa — eslatma.", listOf("Minimal miqdor")),
        MIN_UNIT("Minimal qoldiq birligi", false, "minStockUnit", "Bo‘sh bo‘lsa — partiya birligi.", listOf("Minimal qoldiq o‘lchov birligi")),
        NOTE("Izoh", false, "note", "Qo‘shimcha ma’lumot.", listOf("Izohlar")),
    }

    companion object {
        const val SHEET = "Reaktivlar"
        const val GUIDE_SHEET = "Yo‘riqnoma"

        /** Shablondagi namuna qatorining seriyasi — o‘zgartirilmagan namuna import qilinmaydi. */
        const val EXAMPLE_SERIES = "NAMUNA-001"

        /** Shablon sarlavhalari (aynan shu tartibda). */
        val HEADERS: List<String> = Column.entries.map { it.header }

        private const val HEADER_SEARCH_ROWS = 20
        private val log: Logger = Logger.getLogger("uz.reaktivombor.service.ExcelImport")
    }

    // ---------------------------------------------------------------- shablon

    /** Bo‘sh shablon: sarlavhalar, bitta namuna qator va «Yo‘riqnoma» varag‘i (har bir ustun tavsifi). */
    fun template(path: Path): Path = SafeFiles.write(path) { out -> Xlsx.write(out) { workbook -> writeTemplate(workbook) } }

    private fun writeTemplate(workbook: Workbook) {
        val today = LocalDate.now(clock)
        val ws = workbook.newWorksheet(SHEET)
        Column.entries.forEachIndexed { c, column ->
            ws.value(0, c, column.header)
            ws.style(0, c).bold().fontColor("FFFFFF").fillColor(if (column.required) "0B2545" else "1E5AA8").wrapText(true)
                .horizontalAlignment("center").verticalAlignment("center").borderStyle(BorderStyle.THIN).borderColor("C2CAD5").set()
            ws.width(c, if (column == Column.NAME || column == Column.NOTE) 28.0 else 16.0)
        }
        ws.rowHeight(0, 32.0)
        val example: Map<Column, Any> = mapOf(
            Column.NAME to "Etil spirti",
            Column.FORMULA to "C2H5OH",
            Column.MANUFACTURER to "Kimyoreaktiv",
            Column.SERIES to EXAMPLE_SERIES,
            Column.PRODUCED to today.minusMonths(2),
            Column.RECEIVED to today.minusDays(7),
            Column.EXPIRES to today.plusYears(1),
            Column.QTY to BigDecimal("10"),
            Column.UNIT to "l",
            Column.DEPARTMENT to "Sud-kimyo",
            Column.STORAGE to "2-xona, 3-shkaf",
            Column.RESPONSIBLE to "Karimova D.",
            Column.RECEIVED_BY to "Aliyev A.",
            Column.MIN_STOCK to BigDecimal("2"),
            Column.MIN_UNIT to "l",
            Column.NOTE to "Namuna — bu qatorni o‘chirib, o‘z ma’lumotlaringizni yozing",
        )
        Column.entries.forEachIndexed { c, column ->
            when (val value = example[column]) {
                is LocalDate -> {
                    ws.value(1, c, value)
                    ws.style(1, c).format("dd.mm.yyyy").italic().fontColor("5B6575").set()
                }
                is BigDecimal -> {
                    ws.value(1, c, value)
                    ws.style(1, c).italic().fontColor("5B6575").set()
                }
                is String -> {
                    ws.value(1, c, value)
                    ws.style(1, c).italic().fontColor("5B6575").set()
                }
            }
        }
        ws.freezePane(0, 1)
        ws.pageOrientation("landscape")
        ws.setFitToPage(true)
        ws.fitToWidth(1.toShort())
        ws.fitToHeight(0.toShort())

        val guide = workbook.newWorksheet(GUIDE_SHEET)
        val notes = listOf(
            "Partiyalarni Exceldan qo‘shish — yo‘riqnoma",
            "1. «$SHEET» varag‘ida har bir qator — bitta partiya. 1-qatordagi sarlavhalarni o‘zgartirmang.",
            "2. 2-qatordagi namuna ($EXAMPLE_SERIES) import qilinmaydi — uni o‘chiring yoki o‘z ma’lumotingiz bilan almashtiring.",
            "3. Sanalar: Excel sanasi yoki kun.oy.yil matni (masalan, 05.10.2026).",
            "4. Miqdor: son, kasr qismi vergul yoki nuqta bilan (2,5 yoki 2.5), ko‘pi bilan 3 xona.",
            "5. Bir xil partiya (nom + seriya + muddat + birlik) ikkinchi marta qo‘shilmaydi — unga qo‘shimcha kirim qiling.",
            "6. Avval «Tekshirish»: har bir qatordagi xato ko‘rsatiladi. Import — hammasi yoki hech biri: xatosiz qatorlar bitta amalda saqlanadi.",
        )
        notes.forEachIndexed { r, text ->
            guide.value(r, 0, text)
            if (r == 0) guide.style(r, 0).bold().fontSize(13).fontColor("0B2545").set()
        }
        val top = notes.size + 1
        listOf("Ustun", "Majburiy", "Tavsif").forEachIndexed { c, title ->
            guide.value(top, c, title)
            guide.style(top, c).bold().fontColor("FFFFFF").fillColor("0B2545").borderStyle(BorderStyle.THIN).borderColor("C2CAD5").set()
        }
        Column.entries.forEachIndexed { index, column ->
            val r = top + 1 + index
            guide.value(r, 0, column.header)
            guide.value(r, 1, if (column.required) "Ha" else "Yo‘q")
            guide.value(r, 2, column.help)
            for (c in 0..2) {
                val style = guide.style(r, c).borderStyle(BorderStyle.THIN).borderColor("C2CAD5").wrapText(true).verticalAlignment("top")
                (if (c == 0 && column.required) style.bold() else style).set()
            }
        }
        guide.width(0, 26.0)
        guide.width(1, 10.0)
        guide.width(2, 80.0)
    }

    // ---------------------------------------------------------------- ko‘rib chiqish

    /**
     * Faylni o‘qib, har bir qatorni tekshiradi (bazaga hech narsa yozilmaydi). Sarlavhalar katta-kichik harf va tutuq
     * belgisi farqsiz topiladi. Fayl o‘qilmasa yoki majburiy ustun bo‘lmasa — [UserError].
     */
    fun preview(path: Path): List<ImportRow> {
        val table = readTable(path)
        val departments = directory.departments(includeInactive = true).associateBy { nameKey(it.name) }
        val parsed = table.rows.map { raw -> parseRow(raw, departments) }
        val result = dryRun(parsed)
        log.info("excel preview rows=${result.size} valid=${result.count { it.valid }}")
        return result
    }

    private class Parsed(
        val rowNumber: Int,
        val input: NewBatch?,
        val errors: MutableList<String>,
        val errorFields: MutableSet<String>,
        val warnings: MutableList<String>,
        val departmentName: String?,
    )

    private fun parseRow(raw: RawRow, departments: Map<String, Place>): Parsed {
        val errors = mutableListOf<String>()
        val fields = mutableSetOf<String>()
        val warnings = mutableListOf<String>()
        fun fail(column: Column, message: String) {
            errors += message
            fields += column.field
        }
        fun text(column: Column): String = raw.cells[column]?.text().orEmpty()
        fun date(column: Column): LocalDate? {
            val cell = raw.cells[column] ?: return null
            cell.date?.let { return it }
            val value = cell.text()
            if (value.isEmpty()) return null
            return DateText.parse(value) ?: run {
                fail(column, "${column.header}: «$value» — sana emas (kun.oy.yil, masalan 05.10.2026).")
                null
            }
        }
        fun unit(column: Column): MeasureUnit? {
            val value = text(column)
            if (value.isEmpty()) return null
            return MeasureUnit.of(value) ?: run {
                fail(column, "${column.header}: «$value» noma’lum (l, ml, kg, g yoki dona).")
                null
            }
        }

        val series = cleanName(text(Column.SERIES))
        if (nameKey(series) == nameKey(EXAMPLE_SERIES)) {
            return Parsed(
                raw.rowNumber, null,
                mutableListOf("Bu shablondagi namuna qator — o‘chiring yoki o‘z ma’lumotlaringiz bilan almashtiring."),
                mutableSetOf(), mutableListOf(), null,
            )
        }
        val producedOn = date(Column.PRODUCED)
        val receivedOn = date(Column.RECEIVED)
        val expiresOn = date(Column.EXPIRES)
        val unit = unit(Column.UNIT)
        if (unit == null && Column.UNIT.field !in fields) fail(Column.UNIT, "O‘lchov birligi kiritilmagan (l, ml, kg, g yoki dona).")
        val minUnit = unit(Column.MIN_UNIT)

        var departmentId: Long? = null
        var departmentName: String? = null
        val deptText = cleanName(text(Column.DEPARTMENT))
        if (deptText.isNotEmpty()) {
            val existing = departments[nameKey(deptText)]
            when {
                existing == null && (deptText.none { it.isLetter() } || deptText.charCount() > Directory.NAME_MAX) ->
                    fail(Column.DEPARTMENT, "Bo‘lim nomi harf bilan yozilishi kerak (${Directory.NAME_MAX} belgigacha): «$deptText».")
                existing == null -> {
                    departmentName = deptText
                    warnings += "Yangi bo‘lim qo‘shiladi: «$deptText»."
                }
                !existing.active -> fail(Column.DEPARTMENT, "«${existing.name}» bo‘limi nofaol — avval faollashtiring yoki boshqa bo‘lim yozing.")
                else -> departmentId = existing.id
            }
        }

        val input = NewBatch(
            name = text(Column.NAME),
            formula = text(Column.FORMULA).ifEmpty { null },
            manufacturer = text(Column.MANUFACTURER).ifEmpty { null },
            series = series,
            producedOn = producedOn,
            receivedOn = receivedOn,
            initialQtyText = text(Column.QTY),
            unit = unit,
            expiresOn = expiresOn,
            departmentId = departmentId,
            storageLocation = text(Column.STORAGE).ifEmpty { null },
            responsible = text(Column.RESPONSIBLE).ifEmpty { null },
            minStockText = text(Column.MIN_STOCK).ifEmpty { null },
            minStockUnit = minUnit,
            note = text(Column.NOTE).ifEmpty { null },
            receivedBy = text(Column.RECEIVED_BY).ifEmpty { null },
        )
        return Parsed(raw.rowNumber, input, errors, fields, warnings, departmentName)
    }

    /** Tekshiruvdan keyin hammasi qaytariladi (tranzaksiya bekor qilinadi). */
    private object DryRunRollback : RuntimeException() {
        private fun readResolve(): Any = DryRunRollback

        override fun fillInStackTrace(): Throwable = this
    }

    /**
     * Har bir qatorni «Reaktiv qo‘shish» qoidalari bilan sinab ko‘radi — bitta tranzaksiyada, oxirida bekor qilinadi.
     * Shu tufayli fayl ichidagi takror qatorlar ham (oldingi qator «yaratilgan» bo‘ladi) aniqlanadi.
     */
    private fun dryRun(rows: List<Parsed>): List<ImportRow> {
        val created = HashMap<Long, Int>()
        try {
            db.write { conn ->
                for (row in rows) {
                    var input = row.input ?: continue
                    row.departmentName?.let { name ->
                        input = input.copy(departmentId = directory.findOrCreate(conn, PlaceKind.DEPARTMENT, name, reactivate = false).id)
                    }
                    tryCreate(row, input, created)
                }
                throw DryRunRollback
            }
        } catch (_: DryRunRollback) {
            // Kutilgan: hech narsa saqlanmadi.
        }
        return rows.map { ImportRow(it.rowNumber, it.input, it.errors.toList(), it.warnings.toList(), it.departmentName) }
    }

    private fun tryCreate(row: Parsed, input: NewBatch, created: MutableMap<Long, Int>) {
        fun record(e: UserError) {
            if (e.fields.isEmpty()) {
                row.errors += e.message
            } else {
                e.fields.filterKeys { it !in row.errorFields }.values.forEach { row.errors += it }
            }
        }
        try {
            created[db.write { reagents.createBatch(input, confirmDuplicate = false) }] = row.rowNumber
        } catch (e: DuplicateBatchError) {
            val other = created[e.existingBatchId]
            if (e.exact) {
                row.errors += if (other != null) {
                    "Fayldagi $other-qator bilan bir xil partiya (nom, seriya, muddat va birlik bir xil)."
                } else {
                    "Bu partiya bazada allaqachon bor (nom, seriya, muddat va birlik bir xil) — qo‘shimcha kirim sifatida kiriting."
                }
                return
            }
            row.warnings += if (other != null) {
                "$other-qatordagi partiya bilan seriyasi bir xil, muddati yoki birligi boshqa — alohida partiya bo‘ladi."
            } else {
                "Shu nom va seriyali partiya bazada boshqa muddat yoki birlik bilan bor — alohida partiya bo‘ladi."
            }
            try {
                created[db.write { reagents.createBatch(input, confirmDuplicate = true) }] = row.rowNumber
            } catch (again: UserError) {
                record(again)
            }
        } catch (e: UserError) {
            record(e)
        }
    }

    // ---------------------------------------------------------------- import

    /**
     * Xatosiz qatorlarni saqlaydi — hammasi bitta tranzaksiyada: birortasi saqlanmasa, hech biri saqlanmaydi.
     * Yangi bo‘lim nomlari shu yerda yaratiladi. Audit: EXCEL_IMPORTED.
     */
    fun import(rows: List<ImportRow>, source: Path? = null): ImportResult {
        val valid = rows.filter { it.valid }
        if (valid.isEmpty()) throw UserError("Import qilinadigan xatosiz qator yo‘q.")
        val skipped = rows.size - valid.size
        db.write { conn ->
            for (row in valid) {
                var input = row.input!!
                if (input.departmentId == null && row.departmentName != null) {
                    val place = directory.findOrCreate(conn, PlaceKind.DEPARTMENT, row.departmentName, reactivate = false)
                    if (!place.active) {
                        throw UserError("${row.rowNumber}-qator: «${place.name}» bo‘limi nofaol. Hech narsa import qilinmadi.")
                    }
                    input = input.copy(departmentId = place.id)
                }
                try {
                    reagents.createBatch(input, confirmDuplicate = true)
                } catch (e: UserError) {
                    throw UserError("${row.rowNumber}-qator: ${e.message}\nHech narsa import qilinmadi — faylni tuzatib, qayta tekshiring.")
                }
            }
            audit.log(
                conn, AuditAction.EXCEL_IMPORTED, "excel", null,
                "Fayl: ${source?.fileName ?: "—"}; qo‘shildi: ${valid.size} ta partiya; o‘tkazib yuborildi: $skipped ta qator",
            )
        }
        log.info("excel import created=${valid.size} skipped=$skipped")
        return ImportResult(valid.size, skipped)
    }

    // ---------------------------------------------------------------- faylni o‘qish

    /** Katak qiymati: matn, son yoki sana (Excel sanasi). */
    private class RawCell(val string: String?, val number: BigDecimal?, val date: LocalDate?) {
        /** Matn ko‘rinishi: sonlar oddiy yozuvda (1250.5), Excel’ning 0,30000000000000004 kabi qoldiqlari yaxlitlanadi. */
        fun text(): String = when {
            number != null -> plain(number)
            date != null -> DateText.format(date)
            else -> cleanText(string)
        }
    }

    private class RawRow(val rowNumber: Int, val cells: Map<Column, RawCell>)

    private class Table(val rows: List<RawRow>)

    private fun readTable(path: Path): Table {
        if (!Files.isRegularFile(path)) throw UserError("Fayl topilmadi: $path")
        val workbook = try {
            ReadableWorkbook(path.toFile())
        } catch (e: Exception) {
            throw UserError("Faylni o‘qib bo‘lmadi: ${path.fileName}. Excel (.xlsx) faylini tanlang (eski .xls qo‘llanmaydi).")
        }
        workbook.use { wb ->
            val sheets = try {
                wb.sheets.toList()
            } catch (e: Exception) {
                throw UserError("Faylni o‘qib bo‘lmadi: ${path.fileName} (${e.message ?: e.javaClass.simpleName}).")
            }
            var missing: List<Column>? = null
            for (sheet in sheets) {
                val rows = try {
                    sheet.read()
                } catch (e: IOException) {
                    throw UserError("«${sheet.name}» varag‘ini o‘qib bo‘lmadi: ${e.message ?: e.javaClass.simpleName}.")
                } catch (e: RuntimeException) {
                    throw UserError("«${sheet.name}» varag‘ini o‘qib bo‘lmadi: ${e.message ?: e.javaClass.simpleName}.")
                }
                val headerIndex = rows.take(HEADER_SEARCH_ROWS).indexOfFirst { row -> headerMap(row).containsKey(Column.NAME) }
                if (headerIndex < 0) continue
                val header = headerMap(rows[headerIndex])
                val absent = Column.entries.filter { it.required && it !in header }
                if (absent.isNotEmpty()) {
                    if (missing == null) missing = absent
                    continue
                }
                val date1904 = wb.isDate1904
                val data = rows.drop(headerIndex + 1).mapNotNull { row ->
                    val cells = header.mapValues { (column, index) -> readCell(row, index, column, date1904) }
                    if (cells.values.all { it.text().isEmpty() }) null else RawRow(row.rowNum, cells)
                }
                return Table(data)
            }
            if (missing != null) {
                throw UserError(
                    "Faylda majburiy ustunlar yo‘q: " + missing.joinToString(", ") { "«${it.header}»" } +
                        ". Shablondan foydalaning (sarlavhalar 1-qatorda).",
                )
            }
            throw UserError("Faylda «${Column.NAME.header}» sarlavhali ustun topilmadi — dasturdagi shablondan foydalaning.")
        }
    }

    /** Sarlavha → ustun raqami (katta-kichik harf, tutuq belgisi, bo‘shliq, «*» va qavs ichidagi izoh farqsiz). */
    private fun headerMap(row: Row): Map<Column, Int> {
        val keys = Column.entries.flatMap { column -> (listOf(column.header) + column.aliases).map { headerKey(it) to column } }.toMap()
        val result = LinkedHashMap<Column, Int>()
        for (index in 0 until row.cellCount) {
            val cell = row.getOptionalCell(index).orElse(null) ?: continue
            if (cell.type != CellType.STRING) continue
            val column = keys[headerKey(cell.text ?: "")] ?: continue
            result.putIfAbsent(column, index)
        }
        return result
    }

    private fun headerKey(text: String): String =
        nameKey(text.replace(Regex("""\(.*?\)"""), " ").replace("*", " ").replace(":", " "))

    private fun readCell(row: Row, index: Int, column: Column, date1904: Boolean): RawCell {
        val cell: Cell = row.getOptionalCell(index).orElse(null) ?: return RawCell(null, null, null)
        val isDate = column == Column.PRODUCED || column == Column.RECEIVED || column == Column.EXPIRES
        val number: BigDecimal? = when (cell.type) {
            CellType.NUMBER -> runCatching { cell.asNumber() }.getOrNull()
            CellType.FORMULA -> cell.rawValue?.let { raw -> raw.toBigDecimalOrNull() }
            else -> null
        }
        if (number != null) {
            return if (isDate) RawCell(null, null, excelDate(number, date1904)) else RawCell(null, number, null)
        }
        val text = when (cell.type) {
            CellType.EMPTY -> null
            CellType.ERROR -> cell.rawValue
            else -> cell.text ?: cell.rawValue
        }
        return RawCell(text, null, null)
    }

    /** Excel sanasi (kunlar soni) → sana. 1900 tizimi: 1 = 01.01.1900 (Excel’ning 1900-yil 29-fevral xatosi hisobga olingan). */
    private fun excelDate(serial: BigDecimal, date1904: Boolean): LocalDate? {
        val days = serial.setScale(0, RoundingMode.FLOOR).toLong()
        if (days <= 0 || days > 2_958_465) return null
        return if (date1904) LocalDate.of(1904, 1, 1).plusDays(days) else LocalDate.of(1899, 12, 30).plusDays(days)
    }
}

private fun plain(number: BigDecimal): String {
    // Excel kasr sonlarni ikkilik ko‘rinishda saqlaydi: 0.30000000000000004 → 0.3.
    val rounded = number.setScale(9, RoundingMode.HALF_UP).stripTrailingZeros()
    return rounded.toPlainString()
}

private fun cleanText(value: String?): String = cleanName(value ?: "")

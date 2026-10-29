package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.DuplicateBatchError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.charCount
import uz.reaktivombor.desktop.core.display
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.bool
import uz.reaktivombor.desktop.db.date
import uz.reaktivombor.desktop.db.dateTime
import uz.reaktivombor.desktop.db.insert
import uz.reaktivombor.desktop.db.long
import uz.reaktivombor.desktop.db.longOrNull
import uz.reaktivombor.desktop.db.query
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.db.queryOne
import uz.reaktivombor.desktop.db.str
import uz.reaktivombor.desktop.db.unit
import uz.reaktivombor.desktop.db.unitOrNull
import uz.reaktivombor.desktop.db.update
import uz.reaktivombor.desktop.searchKey
import java.sql.Connection
import java.sql.ResultSet
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.logging.Logger

/** Reaktiv — bitta nom (bir nechta partiyasi bo‘lishi mumkin). */
data class Reagent(
    val id: Long,
    val name: String,
    val formula: String,
    val minStock: Long?,
    val minStockUnit: MeasureUnit?,
    val note: String,
    val archived: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/** Partiya: nom + seriya + muddat + birlik. Miqdorlari faqat operatsiyalar orqali o‘zgaradi. */
data class Batch(
    val id: Long,
    val reagentId: Long,
    val series: String,
    val manufacturer: String,
    val producedOn: LocalDate?,
    val receivedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val unit: MeasureUnit,
    val departmentId: Long?,
    val storageLocation: String,
    val responsible: String,
    val note: String,
    val archived: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/** «Reaktiv qo‘shish» formasi: yangi partiya va uning boshlang‘ich kirimi. Xato maydon nomlari — shu xususiyatlar nomi. */
data class NewBatch(
    val name: String,
    val formula: String? = null,
    val manufacturer: String? = null,
    val series: String,
    val producedOn: LocalDate? = null,
    val receivedOn: LocalDate?,
    val initialQtyText: String,
    val unit: MeasureUnit?,
    val expiresOn: LocalDate?,
    val departmentId: Long? = null,
    val storageLocation: String? = null,
    val responsible: String? = null,
    val minStockText: String? = null,
    val minStockUnit: MeasureUnit? = null,
    val note: String? = null,
    val receivedBy: String? = null,
)

data class ReagentEdit(
    val name: String,
    val formula: String? = null,
    val minStockText: String? = null,
    val minStockUnit: MeasureUnit? = null,
    val note: String? = null,
)

/** Faqat tavsif maydonlari: birlik va miqdorlar operatsiyalar orqali o‘zgaradi. */
data class BatchEdit(
    val series: String,
    val manufacturer: String? = null,
    val producedOn: LocalDate? = null,
    val receivedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val departmentId: Long? = null,
    val storageLocation: String? = null,
    val responsible: String? = null,
    val note: String? = null,
)

/** Reaktivlar va partiyalar: yaratish, tahrirlash, arxivlash, o‘chirish (faqat tarixsiz). */
class Reagents(
    private val db: Database,
    private val audit: Audit,
    private val directory: Directory,
    private val clock: Clock,
) {
    companion object {
        const val NAME_MAX = 255
        const val SERIES_MAX = 100
        private val log: Logger = Logger.getLogger("uz.reaktivombor.service.Reagents")
    }

    /** Partiya kelgan sanasi o‘zgarganda boshlang‘ich kirimni ko‘chirish uchun (o‘sha bazada, o‘sha tranzaksiyada). */
    private val stock = Stock(db, audit, directory, clock)

    // ---------------------------------------------------------------- o‘qish

    fun get(id: Long): Reagent? = db.read { conn -> findReagent(conn, id) }

    fun batch(id: Long): Batch? = db.read { conn -> findBatch(conn, id) }

    fun list(includeArchived: Boolean = false): List<Reagent> = db.read { conn ->
        conn.query(
            "SELECT * FROM reagents" + (if (includeArchived) "" else " WHERE archived = 0") + " ORDER BY name_key",
        ) { it.toReagent() }
    }

    fun batchesOf(reagentId: Long, includeArchived: Boolean = true): List<Batch> = db.read { conn ->
        conn.query(
            "SELECT * FROM batches WHERE reagent_id = ?" + (if (includeArchived) "" else " AND archived = 0") +
                " ORDER BY expires_on IS NULL, expires_on, series, id",
            reagentId,
        ) { it.toBatch() }
    }

    /** Nom kiritilayotganda taklif: avval shu bilan boshlanadiganlari, keyin ichida uchraydiganlari; arxivdagilar oxirida. */
    fun searchNames(prefix: String, limit: Int = 10): List<String> {
        val key = nameKey(prefix)
        val all = db.read { conn ->
            conn.query("SELECT name, name_key, archived FROM reagents") { rs -> Triple(rs.str("name"), rs.str("name_key"), rs.bool("archived")) }
        }
        return all
            .filter { key.isEmpty() || it.second.contains(key) }
            .sortedWith(compareBy({ it.third }, { !it.second.startsWith(key) }, { it.second }))
            .take(limit.coerceAtLeast(0))
            .map { it.first }
    }

    // ---------------------------------------------------------------- yaratish

    /**
     * Reaktiv (nomi bo‘yicha topiladi yoki yaratiladi) + partiya + boshlang‘ich kirim + harakat — bitta tranzaksiyada.
     * Nom + seriya + muddat + birlik bir xil partiya bor → [DuplicateBatchError] (exact = true, tasdiq bilan ham ochilmaydi).
     * Seriya bir xil, muddat yoki birlik boshqa → [DuplicateBatchError] (exact = false), [confirmDuplicate] bilan saqlanadi.
     */
    fun createBatch(input: NewBatch, confirmDuplicate: Boolean = false): Long {
        val today = clock.today()
        val errors = FieldErrors()

        val name = cleanName(input.name)
        when {
            name.isEmpty() -> errors.add("name", "Reaktiv nomi kiritilishi shart.")
            name.charCount() > NAME_MAX -> errors.add("name", "Reaktiv nomi $NAME_MAX belgidan oshmasligi kerak.")
        }
        val series = errors.capture("series") { shortText(input.series, "Seriya raqami", SERIES_MAX, "series") } ?: ""
        if (series.isEmpty() && "series" !in errors.map) errors.add("series", "Seriya raqami kiritilishi shart.")
        val formula = errors.capture("formula") { shortText(input.formula, "Formula", 100, "formula") } ?: ""
        val manufacturer = errors.capture("manufacturer") { shortText(input.manufacturer, "Ishlab chiqaruvchi", NAME_MAX, "manufacturer") } ?: ""
        val storage = errors.capture("storageLocation") { shortText(input.storageLocation, "Saqlash joyi", NAME_MAX, "storageLocation") } ?: ""
        val responsible = errors.capture("responsible") { shortText(input.responsible, "Mas’ul xodim", 100, "responsible") } ?: ""
        val receivedBy = errors.capture("receivedBy") { shortText(input.receivedBy, "Qabul qilgan xodim", 100, "receivedBy") } ?: ""
        val note = errors.capture("note") { longText(input.note, "Izoh", 2000, "note") } ?: ""

        val receivedOn = errors.capture("receivedOn") { checkDate(input.receivedOn, "Kelgan sana", "receivedOn", today) }
        val expiresOn = errors.capture("expiresOn") { checkDate(input.expiresOn, "Yaroqlilik muddati", "expiresOn", today, allowFuture = true) }
        val producedOn = errors.capture("producedOn") {
            checkDate(input.producedOn, "Ishlab chiqarilgan sana", "producedOn", today, required = false)
        }
        if (producedOn != null && receivedOn != null && producedOn > receivedOn) {
            errors.add("producedOn", "Ishlab chiqarilgan sana kelgan sanadan keyin bo‘lishi mumkin emas.")
        }
        if (producedOn != null && expiresOn != null && expiresOn < producedOn) {
            errors.add("expiresOn", "Yaroqlilik muddati ishlab chiqarilgan sanadan oldin bo‘lishi mumkin emas.")
        }

        val unit = input.unit
        if (unit == null) errors.add("unit", "O‘lchov birligini tanlang.")
        val qty = unit?.let { u -> errors.capture("initialQtyText") { Qty.parse(input.initialQtyText, u, "Kelgan miqdor", field = "initialQtyText") } }
        val minStock = errors.capture("minStockText") { parseMinStock(input.minStockText, input.minStockUnit ?: unit) }
        if (minStock != null && unit != null && minStock.second.dimension != unit.dimension) {
            // Minimal qoldiq faqat o‘z o‘lchov turidagi partiyalarga qo‘llanadi: reaktivning bunday partiyasi bo‘lmasa — hech qachon.
            val dimensions = batchDimensions(nameKey(name)) + unit.dimension
            if (minStock.second.dimension !in dimensions) errors.add("minStockText", minUnitMismatch(dimensions))
        }
        errors.throwIfAny()

        return db.write { conn ->
            input.departmentId?.let { requireActiveDepartment(conn, it) }
            val now = clock.now()
            val reagentId = findOrCreateReagent(conn, name, formula, minStock, note = "")
            val existing = conn.query(
                "SELECT id, series, expires_on, unit FROM batches WHERE reagent_id = ? AND series <> ''",
                reagentId,
            ) { rs -> SameSeries(rs.long("id"), rs.str("series"), rs.date("expires_on"), rs.unit("unit")) }
                .filter { searchKey(it.series) == searchKey(series) }
            existing.firstOrNull { it.expiresOn == expiresOn && it.unit == unit }?.let { same ->
                throw DuplicateBatchError(
                    same.id, exact = true,
                    "Bu partiya allaqachon bor: «$name», seriya ${same.series}, muddati ${expiresOn!!.display()}, birligi ${unit!!.code}. " +
                        "Yangi kelgan miqdorni shu partiyaga qo‘shimcha kirim sifatida kiriting.",
                )
            }
            if (existing.isNotEmpty() && !confirmDuplicate) {
                throw DuplicateBatchError(
                    existing.first().id, exact = false,
                    "«$name» reaktivining ${existing.first().series} seriyali partiyasi boshqa muddat yoki birlik bilan allaqachon bor. " +
                        "Baribir alohida partiya sifatida saqlansinmi?",
                )
            }
            val batchId = conn.insert(
                """
                INSERT INTO batches(reagent_id, series, manufacturer, produced_on, received_on, expires_on, unit, department_id,
                    storage_location, responsible, note, archived, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                reagentId, series, manufacturer, producedOn, receivedOn, expiresOn, unit, input.departmentId,
                storage, responsible, note, now, now,
            )
            val receiptId = conn.insert(
                """
                INSERT INTO receipts(batch_id, received_on, qty, entered_qty, entered_unit, received_by, storage_location, doc_no,
                    note, is_initial, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, '', '', 1, ?)
                """,
                batchId, receivedOn, qty!!, Qty.format(qty, unit!!), unit, receivedBy, storage, now,
            )
            insertMovement(conn, batchId, "RECEIPT", qty, receivedOn!!, now, receiptId = receiptId)
            audit.log(
                conn, AuditAction.BATCH_CREATED, "batch", batchId,
                "«$name», seriya $series, muddati ${expiresOn!!.display()}, kelgan ${receivedOn.display()}",
                reagentId = reagentId, batchId = batchId, qtyText = Qty.formatWithUnit(qty, unit),
            )
            log.info("batch created id=$batchId receipt=$receiptId")
            batchId
        }
    }

    private data class SameSeries(val id: Long, val series: String, val expiresOn: LocalDate?, val unit: MeasureUnit)

    /** Ochiq tranzaksiya ichida: nom kaliti bo‘yicha topadi (arxivda bo‘lsa — chiqaradi) yoki yaratadi. */
    internal fun findOrCreateReagent(
        conn: Connection,
        name: String,
        formula: String,
        minStock: Pair<Long, MeasureUnit>?,
        note: String,
    ): Long {
        val key = nameKey(name)
        val now = clock.now()
        val existing = conn.queryOne("SELECT * FROM reagents WHERE name_key = ?", key) { it.toReagent() }
        if (existing == null) {
            val id = conn.insert(
                """
                INSERT INTO reagents(name, name_key, formula, min_stock, min_stock_unit, note, archived, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                name, key, formula, minStock?.first, minStock?.second, note, now, now,
            )
            audit.log(conn, AuditAction.REAGENT_CREATED, "reagent", id, "«$name»", reagentId = id)
            return id
        }
        if (existing.archived) {
            conn.update("UPDATE reagents SET archived = 0, updated_at = ? WHERE id = ?", now, existing.id)
            audit.log(conn, AuditAction.UNARCHIVED, "reagent", existing.id, "«${existing.name}» — yangi partiya keldi", reagentId = existing.id)
        }
        val changes = mutableListOf<String>()
        if (formula.isNotEmpty() && formula != existing.formula) {
            conn.update("UPDATE reagents SET formula = ?, updated_at = ? WHERE id = ?", formula, now, existing.id)
            changes += "formula: «${existing.formula}» → «$formula»"
        }
        if (minStock != null && (minStock.first != existing.minStock || minStock.second != existing.minStockUnit)) {
            conn.update(
                "UPDATE reagents SET min_stock = ?, min_stock_unit = ?, updated_at = ? WHERE id = ?",
                minStock.first, minStock.second, now, existing.id,
            )
            changes += "minimal qoldiq: ${Qty.formatWithUnit(minStock.first, minStock.second)}"
        }
        if (changes.isNotEmpty()) {
            audit.log(conn, AuditAction.REAGENT_UPDATED, "reagent", existing.id, "«${existing.name}»: " + changes.joinToString("; "), reagentId = existing.id)
        }
        return existing.id
    }

    // ---------------------------------------------------------------- tahrirlash

    /** Boshqa reaktivning nomiga o‘zgartirib bo‘lmaydi (reaktivlar birlashtirilmaydi). */
    fun updateReagent(id: Long, edit: ReagentEdit) {
        val errors = FieldErrors()
        val name = cleanName(edit.name)
        when {
            name.isEmpty() -> errors.add("name", "Reaktiv nomi kiritilishi shart.")
            name.charCount() > NAME_MAX -> errors.add("name", "Reaktiv nomi $NAME_MAX belgidan oshmasligi kerak.")
        }
        val formula = errors.capture("formula") { shortText(edit.formula, "Formula", 100, "formula") } ?: ""
        val note = errors.capture("note") { longText(edit.note, "Izoh", 2000, "note") } ?: ""
        val minStock = errors.capture("minStockText") { parseMinStock(edit.minStockText, edit.minStockUnit) }
        errors.throwIfAny()
        db.write { conn ->
            val reagent = findReagent(conn, id) ?: throw UserError("Reaktiv topilmadi.")
            val key = nameKey(name)
            conn.queryOne("SELECT name FROM reagents WHERE name_key = ? AND id <> ?", key, id) { it.getString(1) }?.let { other ->
                throw UserError("«$other» nomli reaktiv allaqachon bor — boshqa reaktiv nomiga o‘zgartirib bo‘lmaydi.", "name")
            }
            val changes = buildList {
                if (name != reagent.name) add("nomi: «${reagent.name}» → «$name»")
                if (formula != reagent.formula) add("formula: «${reagent.formula}» → «$formula»")
                if (minStock?.first != reagent.minStock || minStock?.second != reagent.minStockUnit) {
                    add("minimal qoldiq: " + (minStock?.let { Qty.formatWithUnit(it.first, it.second) } ?: "yo‘q"))
                }
                if (note != reagent.note) add("izoh o‘zgardi")
            }
            if (changes.isEmpty()) return@write
            // Yangi (o‘zgargan) minimal qoldiq reaktivning birorta partiyasiga ham qo‘llanmasa — xato. Avvaldan shunday saqlangan
            // minimum o‘zgarmasa, reaktivning boshqa maydonlarini tahrirlash mumkin bo‘lib qoladi.
            if (minStock != null && (minStock.first != reagent.minStock || minStock.second != reagent.minStockUnit)) {
                val dimensions = batchDimensions(conn, id)
                if (dimensions.isNotEmpty() && minStock.second.dimension !in dimensions) {
                    throw UserError(minUnitMismatch(dimensions), "minStockText")
                }
            }
            conn.update(
                "UPDATE reagents SET name = ?, name_key = ?, formula = ?, min_stock = ?, min_stock_unit = ?, note = ?, updated_at = ? WHERE id = ?",
                name, key, formula, minStock?.first, minStock?.second, note, clock.now(), id,
            )
            audit.log(conn, AuditAction.REAGENT_UPDATED, "reagent", id, "«$name»: " + changes.joinToString("; "), reagentId = id)
        }
    }

    fun updateBatch(id: Long, edit: BatchEdit) {
        val today = clock.today()
        val current = batch(id) ?: throw UserError("Partiya topilmadi.")
        val errors = FieldErrors()
        val series = errors.capture("series") { shortText(edit.series, "Seriya raqami", SERIES_MAX, "series") } ?: current.series
        // Eski ma’lumotdagi seriyasiz partiya seriyasiz qolishi mumkin; seriya bir marta yozilgach — majburiy.
        if (series.isEmpty() && current.series.isNotEmpty()) errors.add("series", "Seriya raqami kiritilishi shart.")
        val manufacturer = errors.capture("manufacturer") { shortText(edit.manufacturer, "Ishlab chiqaruvchi", NAME_MAX, "manufacturer") } ?: ""
        val storage = errors.capture("storageLocation") { shortText(edit.storageLocation, "Saqlash joyi", NAME_MAX, "storageLocation") } ?: ""
        val responsible = errors.capture("responsible") { shortText(edit.responsible, "Mas’ul xodim", 100, "responsible") } ?: ""
        val note = errors.capture("note") { longText(edit.note, "Izoh", 2000, "note") } ?: ""
        val receivedOn = errors.capture("receivedOn") {
            checkDate(edit.receivedOn, "Kelgan sana", "receivedOn", today, required = current.receivedOn != null)
        }
        val expiresOn = errors.capture("expiresOn") {
            checkDate(edit.expiresOn, "Yaroqlilik muddati", "expiresOn", today, required = current.expiresOn != null, allowFuture = true)
        }
        val producedOn = errors.capture("producedOn") {
            checkDate(edit.producedOn, "Ishlab chiqarilgan sana", "producedOn", today, required = false)
        }
        if (producedOn != null && receivedOn != null && producedOn > receivedOn) {
            errors.add("producedOn", "Ishlab chiqarilgan sana kelgan sanadan keyin bo‘lishi mumkin emas.")
        }
        if (producedOn != null && expiresOn != null && expiresOn < producedOn) {
            errors.add("expiresOn", "Yaroqlilik muddati ishlab chiqarilgan sanadan oldin bo‘lishi mumkin emas.")
        }
        errors.throwIfAny()

        db.write { conn ->
            val batch = findBatch(conn, id) ?: throw UserError("Partiya topilmadi.")
            edit.departmentId?.let { if (it != batch.departmentId) requireActiveDepartment(conn, it) }
            if (receivedOn != null) {
                val firstIssue = conn.queryOne(
                    """
                    SELECT MIN(h.issued_on) FROM issue_items i JOIN issues h ON h.id = i.issue_id
                    WHERE i.batch_id = ? AND i.cancelled_at IS NULL
                    """,
                    id,
                ) { rs -> rs.getString(1)?.let(LocalDate::parse) }
                if (firstIssue != null && receivedOn > firstIssue) {
                    throw UserError(
                        "Partiyadan ${firstIssue.display()} da berilgan — kelgan sana undan keyin bo‘lishi mumkin emas.",
                        "receivedOn",
                    )
                }
            }
            // Kelgan sana bitta: partiya kartochkasidagi sana o‘zgarsa — boshlang‘ich kirim ham shu kunga ko‘chiriladi.
            if (receivedOn != null && receivedOn != batch.receivedOn) {
                try {
                    stock.moveInitialReceipt(conn, id, receivedOn)
                } catch (e: UserError) {
                    throw UserError(
                        "Kelgan sanani ${receivedOn.display()} qilib bo‘lmaydi: boshlang‘ich kirim shu sanaga ko‘chirilsa, undan oldingi " +
                            "sanalardagi qoldiq manfiy bo‘lib qoladi.",
                        "receivedOn",
                    )
                }
            }
            if (series.isNotEmpty()) {
                val same = conn.query(
                    "SELECT id, series, expires_on, unit FROM batches WHERE reagent_id = ? AND id <> ? AND series <> ''",
                    batch.reagentId, id,
                ) { rs -> SameSeries(rs.long("id"), rs.str("series"), rs.date("expires_on"), rs.unit("unit")) }
                    .firstOrNull { searchKey(it.series) == searchKey(series) && it.expiresOn == expiresOn && it.unit == batch.unit }
                if (same != null) {
                    throw DuplicateBatchError(
                        same.id, exact = true,
                        "Shu reaktivning ${same.series} seriyali, shu muddat va birlikdagi partiyasi allaqachon bor — ikkalasi bitta partiya bo‘lib qoladi.",
                    )
                }
            }
            val changes = buildList {
                if (series != batch.series) add("seriya: «${batch.series}» → «$series»")
                if (manufacturer != batch.manufacturer) add("ishlab chiqaruvchi")
                if (producedOn != batch.producedOn) add("ishlab chiqarilgan sana: ${batch.producedOn?.display() ?: "—"} → ${producedOn?.display() ?: "—"}")
                if (receivedOn != batch.receivedOn) add("kelgan sana: ${batch.receivedOn?.display() ?: "—"} → ${receivedOn?.display() ?: "—"}")
                if (expiresOn != batch.expiresOn) add("muddati: ${batch.expiresOn?.display() ?: "—"} → ${expiresOn?.display() ?: "—"}")
                if (edit.departmentId != batch.departmentId) add("bo‘lim")
                if (storage != batch.storageLocation) add("saqlash joyi")
                if (responsible != batch.responsible) add("mas’ul xodim")
                if (note != batch.note) add("izoh")
            }
            if (changes.isEmpty()) return@write
            conn.update(
                """
                UPDATE batches SET series = ?, manufacturer = ?, produced_on = ?, received_on = ?, expires_on = ?, department_id = ?,
                    storage_location = ?, responsible = ?, note = ?, updated_at = ?
                WHERE id = ?
                """,
                series, manufacturer, producedOn, receivedOn, expiresOn, edit.departmentId, storage, responsible, note, clock.now(), id,
            )
            audit.log(conn, AuditAction.BATCH_UPDATED, "batch", id, changes.joinToString("; "), reagentId = batch.reagentId, batchId = id)
        }
    }

    // ---------------------------------------------------------------- arxiv va o‘chirish

    fun archiveReagent(id: Long) = setReagentArchived(id, true)

    fun unarchiveReagent(id: Long) = setReagentArchived(id, false)

    fun archiveBatch(id: Long) = setBatchArchived(id, true)

    fun unarchiveBatch(id: Long) = setBatchArchived(id, false)

    private fun setReagentArchived(id: Long, archived: Boolean) {
        db.write { conn ->
            val reagent = findReagent(conn, id) ?: throw UserError("Reaktiv topilmadi.")
            if (reagent.archived == archived) return@write
            conn.update("UPDATE reagents SET archived = ?, updated_at = ? WHERE id = ?", archived, clock.now(), id)
            audit.log(conn, if (archived) AuditAction.ARCHIVED else AuditAction.UNARCHIVED, "reagent", id, "«${reagent.name}»", reagentId = id)
        }
    }

    private fun setBatchArchived(id: Long, archived: Boolean) {
        db.write { conn ->
            val batch = loadBatchInfo(conn, id) ?: throw UserError("Partiya topilmadi.")
            if (batch.archived == archived) return@write
            conn.update("UPDATE batches SET archived = ?, updated_at = ? WHERE id = ?", archived, clock.now(), id)
            val remainder = remainderOf(conn, id)
            audit.log(
                conn, if (archived) AuditAction.ARCHIVED else AuditAction.UNARCHIVED, "batch", id, batch.label,
                reagentId = batch.reagentId, batchId = id, qtyText = "qoldiq " + Qty.formatWithUnit(remainder, batch.unit),
            )
        }
    }

    /**
     * Faqat tarixsiz partiya o‘chiriladi: unda boshlang‘ich kirimdan boshqa hech qanday operatsiya bo‘lmagan
     * (xato kiritilgan partiyani darhol tuzatish uchun). Aks holda — arxivlash. Audit jurnalida yozuv qoladi.
     */
    fun deleteBatch(id: Long) {
        db.write { conn ->
            val batch = loadBatchInfo(conn, id) ?: throw UserError("Partiya topilmadi.")
            val receipts = conn.query("SELECT id, is_initial, cancelled_at FROM receipts WHERE batch_id = ?", id) { rs ->
                Triple(rs.long("id"), rs.bool("is_initial"), rs.getString("cancelled_at") != null)
            }
            val items = conn.queryLong("SELECT COUNT(*) FROM issue_items WHERE batch_id = ?", id)
            val movements = conn.query("SELECT kind FROM stock_movements WHERE batch_id = ?", id) { it.getString(1) }
            val onlyInitial = receipts.size <= 1 && receipts.all { it.second && !it.third } &&
                items == 0L && movements.all { it == "RECEIPT" } && movements.size == receipts.size
            if (!onlyInitial) {
                throw UserError("Partiyaning operatsiyalar tarixi bor — o‘chirib bo‘lmaydi, arxivlang.")
            }
            val remainder = remainderOf(conn, id)
            conn.update("INSERT INTO ledger_unlock(batch_id) VALUES (?)", id)
            conn.update("DELETE FROM stock_movements WHERE batch_id = ?", id)
            conn.update("DELETE FROM receipts WHERE batch_id = ?", id)
            conn.update("DELETE FROM batches WHERE id = ?", id)
            conn.update("DELETE FROM ledger_unlock WHERE batch_id = ?", id)
            audit.log(
                conn, AuditAction.DELETED, "batch", id, "${batch.label} partiyasi o‘chirildi (tarixi yo‘q edi)",
                reagentId = batch.reagentId, batchId = id, qtyText = Qty.formatWithUnit(remainder, batch.unit),
            )
        }
    }

    /** Faqat partiyasi yo‘q reaktiv o‘chiriladi. */
    fun deleteReagent(id: Long) {
        db.write { conn ->
            val reagent = findReagent(conn, id) ?: throw UserError("Reaktiv topilmadi.")
            if (conn.queryLong("SELECT COUNT(*) FROM batches WHERE reagent_id = ?", id) > 0) {
                throw UserError("Reaktivning tarixi bor (partiyalari mavjud) — o‘chirib bo‘lmaydi, arxivlang.")
            }
            conn.update("DELETE FROM reagents WHERE id = ?", id)
            audit.log(conn, AuditAction.DELETED, "reagent", id, "«${reagent.name}» o‘chirildi", reagentId = id)
        }
    }

    // ---------------------------------------------------------------- yordamchilar

    private fun parseMinStock(text: String?, unit: MeasureUnit?): Pair<Long, MeasureUnit>? {
        if (text.isNullOrBlank()) return null
        if (unit == null) throw UserError("Minimal qoldiq birligini tanlang.", "minStockText")
        return Qty.parse(text, unit, "Minimal qoldiq", allowZero = true, field = "minStockText") to unit
    }

    /** Reaktivning partiyalari qaysi o‘lchov turlarida (arxivdagilari ham). */
    private fun batchDimensions(conn: Connection, reagentId: Long): Set<Dimension> =
        conn.query("SELECT DISTINCT unit FROM batches WHERE reagent_id = ?", reagentId) { it.unit("unit").dimension }.toSet()

    private fun batchDimensions(key: String): Set<Dimension> = db.read { conn ->
        conn.queryOne("SELECT id FROM reagents WHERE name_key = ?", key) { it.getLong(1) }?.let { batchDimensions(conn, it) } ?: emptySet()
    }

    private fun minUnitMismatch(dimensions: Set<Dimension>): String =
        "Minimal qoldiq birligi reaktiv partiyalarining birligiga mos emas — bunday minimum hech qachon tekshirilmaydi. " +
            dimensions.flatMap { MeasureUnit.of(it) }.joinToString(" yoki ") { it.code } + " tanlang."

    private fun requireActiveDepartment(conn: Connection, departmentId: Long) {
        val place = directory.find(conn, PlaceKind.DEPARTMENT, departmentId) ?: throw UserError("Tanlangan bo‘lim topilmadi.", "departmentId")
        if (!place.active) throw UserError("«${place.name}» bo‘limi nofaol — avval faollashtiring.", "departmentId")
    }

    private fun findReagent(conn: Connection, id: Long): Reagent? =
        conn.queryOne("SELECT * FROM reagents WHERE id = ?", id) { it.toReagent() }

    private fun findBatch(conn: Connection, id: Long): Batch? =
        conn.queryOne("SELECT * FROM batches WHERE id = ?", id) { it.toBatch() }

    private fun ResultSet.toReagent() = Reagent(
        id = long("id"),
        name = str("name"),
        formula = str("formula"),
        minStock = longOrNull("min_stock"),
        minStockUnit = unitOrNull("min_stock_unit"),
        note = str("note"),
        archived = bool("archived"),
        createdAt = dateTime("created_at")!!,
        updatedAt = dateTime("updated_at")!!,
    )

    private fun ResultSet.toBatch() = Batch(
        id = long("id"),
        reagentId = long("reagent_id"),
        series = str("series"),
        manufacturer = str("manufacturer"),
        producedOn = date("produced_on"),
        receivedOn = date("received_on"),
        expiresOn = date("expires_on"),
        unit = unit("unit"),
        departmentId = longOrNull("department_id"),
        storageLocation = str("storage_location"),
        responsible = str("responsible"),
        note = str("note"),
        archived = bool("archived"),
        createdAt = dateTime("created_at")!!,
        updatedAt = dateTime("updated_at")!!,
    )
}

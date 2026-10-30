package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.DuplicateOperationError
import uz.reaktivombor.desktop.core.ExpiredBatchError
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.InsufficientStockError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.daysLeft
import uz.reaktivombor.desktop.core.display
import uz.reaktivombor.desktop.core.expiryState
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.bool
import uz.reaktivombor.desktop.db.date
import uz.reaktivombor.desktop.db.insert
import uz.reaktivombor.desktop.db.long
import uz.reaktivombor.desktop.db.longOrNull
import uz.reaktivombor.desktop.db.query
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.db.queryOne
import uz.reaktivombor.desktop.db.str
import uz.reaktivombor.desktop.db.unit
import uz.reaktivombor.desktop.db.update
import java.sql.Connection
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.logging.Logger

/** Kimga berildi: bo‘lim, tuman yoki hisobdan chiqarish (masalan, muddati o‘tgani uchun). */
enum class RecipientType(val label: String) {
    DEPARTMENT("Bo‘lim"),
    DISTRICT("Tuman"),
    WRITE_OFF("Hisobdan chiqarish"),
}

/** Mavjud partiyaga qo‘shimcha kirim. Miqdor partiya o‘lchov turidagi istalgan birlikda. */
data class ReceiptInput(
    val date: LocalDate?,
    val qtyText: String,
    val unit: MeasureUnit?,
    val receivedBy: String? = null,
    val storageLocation: String? = null,
    val docNo: String? = null,
    val note: String? = null,
)

/** Berish operatsiyasi: sarlavha va istalgancha qator — hammasi bitta tranzaksiyada saqlanadi yoki hech biri. */
data class IssueInput(
    val date: LocalDate?,
    val docNo: String? = null,
    val issuedBy: String? = null,
    val note: String? = null,
    val lines: List<IssueLine>,
)

/**
 * Bitta qator. Tuman [districtId] bilan yoki nomi ([districtName]) bilan — yangi nom bo‘lsa tuman yaratiladi.
 * Hisobdan chiqarishda [note] — sabab (majburiy).
 */
data class IssueLine(
    val batchId: Long,
    val qtyText: String,
    val unit: MeasureUnit?,
    val recipientType: RecipientType,
    val departmentId: Long? = null,
    val districtId: Long? = null,
    val districtName: String? = null,
    val organization: String? = null,
    val receiver: String? = null,
    val note: String? = null,
)

/** Foydalanuvchi ogohlantirishni ko‘rib, baribir saqlashni tasdiqladi. */
data class IssueConfirm(val allowExpired: Boolean = false, val allowDuplicate: Boolean = false) {
    companion object {
        val none = IssueConfirm()
    }
}

data class ReturnInput(
    val date: LocalDate?,
    val qtyText: String,
    val unit: MeasureUnit?,
    val reason: String? = null,
    val receivedBy: String? = null,
)

/** FEFO ro‘yxati uchun partiya va uning qoldig‘i. */
data class BatchStock(
    val batchId: Long,
    val reagentId: Long,
    val series: String,
    val receivedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val daysLeft: Long?,
    val state: ExpiryState,
    val unit: MeasureUnit,
    val remainder: Long,
)

/**
 * Ombor operatsiyalari. Qoldiq faqat stock_movements yig‘indisi; har bir operatsiya — bitta tranzaksiya
 * (tekshiruv, operatsiya yozuvi, harakat va audit birga). Bekor qilish va tuzatish tarixni o‘chirmaydi.
 */
class Stock(
    private val db: Database,
    private val audit: Audit,
    private val directory: Directory,
    private val clock: Clock,
) {
    companion object {
        /** Shu vaqt ichida aynan bir xil operatsiya qayta saqlansa — ogohlantirish (ikki marta bosish). */
        const val DUPLICATE_WINDOW_SECONDS = 120L

        /** Qoldiq arxivdagi partiyaga qaytganda audit yozuvi matni. */
        const val UNARCHIVE_REASON = "qaytarish/bekor qilish sababli arxivdan chiqarildi"

        /** Partiya kelgan sanasi o‘zgarganda boshlang‘ich kirim tuzatilishi sababi. */
        const val RECEIVED_DATE_REASON = "Kelgan sana tuzatildi"
        private val log: Logger = Logger.getLogger("uz.reaktivombor.service.Stock")
    }

    // ---------------------------------------------------------------- kirim

    fun addReceipt(batchId: Long, input: ReceiptInput, confirmDuplicate: Boolean = false): Long = stockGuard {
        db.write { conn ->
            val id = insertReceipt(conn, batchId, input, confirmDuplicate, isInitial = false, correctsId = null)
            log.info("receipt saved id=$id")
            id
        }
    }

    private fun insertReceipt(
        conn: Connection,
        batchId: Long,
        input: ReceiptInput,
        confirmDuplicate: Boolean,
        isInitial: Boolean,
        correctsId: Long?,
    ): Long {
        val today = clock.today()
        val batch = loadBatchInfo(conn, batchId) ?: throw UserError("Partiya topilmadi.")
        if (batch.archived || batch.reagentArchived) {
            throw UserError("${batch.label} arxivda — kirim qilish uchun avval arxivdan chiqaring.")
        }
        val errors = FieldErrors()
        val date = errors.capture("date") { checkDate(input.date, "Kirim sanasi", "date", today) }
        val unit = input.unit
        if (unit == null) {
            errors.add("unit", "O‘lchov birligini tanlang.")
        } else if (unit.dimension != batch.unit.dimension) {
            errors.add("unit", unitMismatch(batch.unit))
        }
        val qty = unit?.let { u -> errors.capture("qtyText") { Qty.parse(input.qtyText, u, "Kirim miqdori", field = "qtyText") } }
        val receivedBy = errors.capture("receivedBy") { shortText(input.receivedBy, "Qabul qilgan xodim", 100, "receivedBy") } ?: ""
        val storage = errors.capture("storageLocation") { shortText(input.storageLocation, "Saqlash joyi", 255, "storageLocation") } ?: ""
        val docNo = errors.capture("docNo") { shortText(input.docNo, "Hujjat raqami", 100, "docNo") } ?: ""
        val note = errors.capture("note") { longText(input.note, "Izoh", 2000, "note") } ?: ""
        // Qo‘shimcha kirim partiya kelgan sanadan (eski ma’lumotda kelgan sana bo‘lmasa — ishlab chiqarilgan sanadan) oldin
        // bo‘lolmaydi. Boshlang‘ich kirimning sanasi — partiyaning kelgan sanasi (u checkInitialDate bilan tekshiriladi).
        if (!isInitial && date != null) {
            val floor = batch.receivedOn
                ?: conn.queryOne("SELECT produced_on FROM batches WHERE id = ?", batchId) { rs -> rs.date("produced_on") }
            if (floor != null && date < floor) {
                val what = if (batch.receivedOn != null) "partiya kelgan sanadan" else "partiya ishlab chiqarilgan sanadan"
                errors.add("date", "Kirim sanasi (${date.display()}) $what (${floor.display()}) oldin bo‘lishi mumkin emas.")
            }
        }
        errors.throwIfAny()
        date!!
        qty!!
        unit!!

        val now = clock.now()
        if (!confirmDuplicate) {
            val since = now.minusSeconds(DUPLICATE_WINDOW_SECONDS)
            val same = conn.queryLong(
                """
                SELECT COUNT(*) FROM receipts
                WHERE batch_id = ? AND qty = ? AND received_on = ? AND cancelled_at IS NULL AND created_at >= ?
                """,
                batchId, qty, date, since,
            )
            if (same > 0) throw DuplicateOperationError(duplicateMessage("kirim"))
        }
        val id = conn.insert(
            """
            INSERT INTO receipts(batch_id, received_on, qty, entered_qty, entered_unit, received_by, storage_location, doc_no, note,
                is_initial, created_at, corrects_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            batchId, date, qty, Qty.format(qty, unit), unit, receivedBy, storage, docNo, note, isInitial, now, correctsId,
        )
        insertMovement(conn, batchId, "RECEIPT", qty, date, now, receiptId = id)
        audit.log(
            conn, AuditAction.RECEIPT, "receipt", id,
            "${batch.label}: kirim ${date.display()}" + (if (docNo.isNotEmpty()) ", hujjat $docNo" else ""),
            reagentId = batch.reagentId, batchId = batchId, qtyText = Qty.formatWithUnit(qty, unit),
        )
        return id
    }

    // ---------------------------------------------------------------- berish

    /**
     * Bir yoki bir nechta qator — bitta tranzaksiyada. Bir partiyadagi qatorlar qo‘shib tekshiriladi; birortasida qoldiq
     * yetmasa — [InsufficientStockError] va hech narsa saqlanmaydi. Muddati o‘tgan partiya — [ExpiredBatchError]
     * ([IssueConfirm.allowExpired] bo‘lmasa; xabarda muddati o‘tgan HAMMA qatorlar), 120 s ichidagi aynan bir xil qator —
     * [DuplicateOperationError].
     */
    fun issue(input: IssueInput, confirm: IssueConfirm = IssueConfirm.none): Long = stockGuard {
        db.write { conn ->
            val (issueId, items) = insertIssue(conn, input, confirm, correctsId = null)
            log.info("issue saved id=$issueId items=$items")
            issueId
        }
    }

    /** Hisobdan chiqarish (muddati o‘tgan, singan, to‘kilgan …) — bitta qatorli berish, sabab majburiy. */
    fun writeOff(
        batchId: Long,
        date: LocalDate?,
        qtyText: String,
        unit: MeasureUnit?,
        reason: String?,
        confirm: IssueConfirm = IssueConfirm.none,
    ): Long = issue(
        IssueInput(date = date, lines = listOf(IssueLine(batchId, qtyText, unit, RecipientType.WRITE_OFF, note = reason))),
        confirm,
    )

    private class PreparedLine(
        val number: Int,
        val line: IssueLine,
        val batch: BatchInfo,
        val unit: MeasureUnit,
        val qty: Long,
        val departmentId: Long?,
        val districtId: Long?,
        val recipientName: String,
        val organization: String,
        val receiver: String,
        val note: String,
    )

    private fun insertIssue(conn: Connection, input: IssueInput, confirm: IssueConfirm, correctsId: Long?): Pair<Long, List<Long>> {
        val today = clock.today()
        val date = checkDate(input.date, "Berish sanasi", "date", today)!!
        val docNo = shortText(input.docNo, "Hujjat (talabnoma) raqami", 100, "docNo")
        val issuedBy = shortText(input.issuedBy, "Beruvchi", 100, "issuedBy")
        val headerNote = longText(input.note, "Izoh", 2000, "note")
        if (input.lines.isEmpty()) throw UserError("Kamida bitta qator kiriting.", "lines")

        // 1. Har bir qatorni tekshirish (hali hech narsa yozilmaydi).
        val prepared = input.lines.mapIndexed { index, line -> prepareLine(conn, index + 1, line, date, input.lines.size) }

        // 1a. Muddati o‘tgan partiyalar — hammasi bitta xabarda: bitta tasdiq foydalanuvchi ko‘rgan hamma qatorni qamraydi.
        if (!confirm.allowExpired) {
            val expired = prepared.filter { p ->
                p.line.recipientType != RecipientType.WRITE_OFF && p.batch.expiresOn != null && p.batch.expiresOn < date
            }
            if (expired.isNotEmpty()) {
                val text = expired.joinToString("\n") { p ->
                    "${prefix(p.number, prepared.size)}${p.batch.label} partiyasining yaroqlilik muddati o‘tgan (${p.batch.expiresOn!!.display()})."
                }
                throw ExpiredBatchError(
                    expired.first().batch.id,
                    text + (if (expired.size > 1) "\n" else " ") + "Baribir berish uchun tasdiqlang.",
                    expired.map { it.batch.id }.distinct(),
                )
            }
        }

        // 2. Qoldiq: bir partiyadagi qatorlar qo‘shiladi; sana bo‘yicha — keyingi kunlarda ham manfiy bo‘lmasin.
        val requested = prepared.groupBy { it.batch.id }.mapValues { (_, lines) -> lines.sumOf { it.qty } }
        for ((batchId, total) in requested) {
            val batch = prepared.first { it.batch.id == batchId }.batch
            val available = availableFrom(conn, batchId, date)
            if (total > available) throw insufficient(batch, available, total)
        }

        // 3. Ikki marta bosish himoyasi.
        val now = clock.now()
        if (!confirm.allowDuplicate) {
            val since = now.minusSeconds(DUPLICATE_WINDOW_SECONDS)
            for (p in prepared) {
                val same = conn.queryLong(
                    """
                    SELECT COUNT(*) FROM issue_items i JOIN issues h ON h.id = i.issue_id
                    WHERE i.batch_id = ? AND i.qty = ? AND i.recipient_type = ? AND i.department_id IS ? AND i.district_id IS ?
                      AND h.issued_on = ? AND i.cancelled_at IS NULL AND i.created_at >= ?
                    """,
                    p.batch.id, p.qty, p.line.recipientType, p.departmentId, p.districtId, date, since,
                )
                if (same > 0) throw DuplicateOperationError(prefix(p.number, prepared.size) + duplicateMessage("berish"))
            }
        }

        // 4. FEFO belgisi — yozishdan oldin (shu operatsiyaning oldingi qatorlari qoldiqni hali o‘zgartirmagan holatda).
        val fefoFlags = prepared.map { p ->
            p.line.recipientType != RecipientType.WRITE_OFF && earlierBatchExists(conn, p.batch, date, requested)
        }

        // 5. Yozish.
        val issueId = conn.insert(
            "INSERT INTO issues(issued_on, doc_no, issued_by, note, created_at) VALUES (?, ?, ?, ?, ?)",
            date, docNo, issuedBy, headerNote, now,
        )
        val itemIds = prepared.mapIndexed { index, p ->
            val fefo = fefoFlags[index]
            val expired = p.batch.expiresOn != null && p.batch.expiresOn < date
            val expiredOverride = expired && p.line.recipientType != RecipientType.WRITE_OFF
            val itemId = conn.insert(
                """
                INSERT INTO issue_items(issue_id, batch_id, recipient_type, department_id, district_id, organization, receiver, qty,
                    entered_qty, entered_unit, fefo_override, expired_override, note, created_at, corrects_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                issueId, p.batch.id, p.line.recipientType, p.departmentId, p.districtId, p.organization, p.receiver, p.qty,
                Qty.format(p.qty, p.unit), p.unit, fefo, expiredOverride, p.note, now, correctsId,
            )
            insertMovement(conn, p.batch.id, "ISSUE", -p.qty, date, now, issueItemId = itemId)
            val action = when (p.line.recipientType) {
                RecipientType.DEPARTMENT -> AuditAction.ISSUE_DEPARTMENT
                RecipientType.DISTRICT -> AuditAction.ISSUE_DISTRICT
                RecipientType.WRITE_OFF -> AuditAction.WRITE_OFF
            }
            val details = buildString {
                append(p.batch.label).append(" → ").append(p.recipientName).append(", ").append(date.display())
                if (docNo.isNotEmpty()) append(", hujjat ").append(docNo)
                if (fefo) append(". FEFO buzildi: muddati yaqinroq partiya bor edi")
                if (expiredOverride) append(". Muddati o‘tgan partiyadan berildi (tasdiqlangan)")
                if (p.line.recipientType == RecipientType.WRITE_OFF) append(". Sabab: ").append(p.note)
            }
            audit.log(
                conn, action, "issue_item", itemId, details,
                reagentId = p.batch.reagentId, batchId = p.batch.id, qtyText = Qty.formatWithUnit(p.qty, p.unit),
            )
            itemId
        }
        return issueId to itemIds
    }

    private fun prepareLine(conn: Connection, number: Int, line: IssueLine, date: LocalDate, total: Int): PreparedLine {
        val p = prefix(number, total)
        val batch = loadBatchInfo(conn, line.batchId) ?: throw UserError(cap(p, "partiya topilmadi."))
        if (batch.archived || batch.reagentArchived) throw UserError("$p${batch.label} arxivda — undan berib bo‘lmaydi.")
        val unit = line.unit ?: throw UserError(cap(p, "o‘lchov birligini tanlang."), "unit")
        if (unit.dimension != batch.unit.dimension) throw UserError(p + unitMismatch(batch.unit), "unit")
        val qty = Qty.parse(line.qtyText, unit, cap(p, "miqdor"), field = "qtyText")
        val organization = shortText(line.organization, "Tashkilot", 255, "organization")
        val receiver = shortText(line.receiver, "Qabul qiluvchi", 100, "receiver")
        val note = longText(line.note, "Izoh", 2000, "note")

        var departmentId: Long? = null
        var districtId: Long? = null
        val recipientName: String
        when (line.recipientType) {
            RecipientType.DEPARTMENT -> {
                val id = line.departmentId ?: throw UserError(cap(p, "qaysi bo‘limga berilishini tanlang."), "departmentId")
                val place = directory.find(conn, PlaceKind.DEPARTMENT, id) ?: throw UserError(cap(p, "bo‘lim topilmadi."), "departmentId")
                if (!place.active) throw UserError("$p«${place.name}» bo‘limi nofaol.", "departmentId")
                departmentId = place.id
                recipientName = "${place.name} bo‘limi"
            }
            RecipientType.DISTRICT -> {
                val place = when {
                    line.districtId != null ->
                        directory.find(conn, PlaceKind.DISTRICT, line.districtId) ?: throw UserError(cap(p, "tuman topilmadi."), "districtId")
                    // Nofaol tuman nomi yozilsa — jimgina faollashtirilmaydi (nofaol qilish va'dasi buzilmasin), rad etiladi.
                    !line.districtName.isNullOrBlank() ->
                        try {
                            directory.findOrCreate(conn, PlaceKind.DISTRICT, line.districtName, reactivate = false)
                        } catch (e: UserError) {
                            throw UserError(p + e.message, "districtName")
                        }
                    else -> throw UserError(cap(p, "qaysi tumanga berilishini tanlang yoki yozing."), "districtName")
                }
                if (!place.active) {
                    throw UserError(
                        "$p«${place.name}» tumani nofaol — unga berib bo‘lmaydi. Avval «Tumanlarga berilgan reaktivlar» sahifasida faollashtiring.",
                        if (line.districtId != null) "districtId" else "districtName",
                    )
                }
                districtId = place.id
                recipientName = "${place.name} tumani"
            }
            RecipientType.WRITE_OFF -> {
                if (note.isEmpty()) throw UserError(cap(p, "hisobdan chiqarish sababini yozing."), "note")
                recipientName = "hisobdan chiqarildi"
            }
        }

        val receivedOn = batch.receivedOn
        if (receivedOn != null && date < receivedOn) {
            throw UserError(cap(p, "berish sanasi (${date.display()}) partiya kelgan sanadan (${receivedOn.display()}) oldin bo‘lishi mumkin emas."), "date")
        }
        return PreparedLine(number, line, batch, unit, qty, departmentId, districtId, recipientName, organization, receiver, note)
    }

    /**
     * FEFO: shu reaktiv va o‘lchov turidagi, arxivda bo‘lmagan, muddati o‘tmagan, berish sanasida ([date]) qoldig‘i bor
     * (shu operatsiyada olinadiganidan tashqari) boshqa partiya muddati ertaroq tugaydimi. O‘tgan sana bilan yozilgan berishda
     * o‘sha kuni hali kelmagan yoki tugagan partiya hisobga olinmaydi.
     */
    private fun earlierBatchExists(conn: Connection, batch: BatchInfo, date: LocalDate, requested: Map<Long, Long>): Boolean {
        val others = conn.query(
            """
            SELECT b.id, b.expires_on, b.unit
            FROM batches b
            WHERE b.reagent_id = ? AND b.id <> ? AND b.archived = 0 AND b.expires_on IS NOT NULL AND b.expires_on >= ?
            """,
            batch.reagentId, batch.id, date,
        ) { rs -> Triple(rs.long("id"), rs.date("expires_on")!!, rs.unit("unit")) }
        return others.any { (id, expires, unit) ->
            unit.dimension == batch.unit.dimension &&
                (batch.expiresOn == null || expires < batch.expiresOn) &&
                availableFrom(conn, id, date) - (requested[id] ?: 0L) > 0
        }
    }

    // ---------------------------------------------------------------- qaytarish

    /**
     * Berilgan qatordan qaytarish. Faol qaytarishlar yig‘indisi berilganidan oshmaydi. 120 s ichida aynan shunday qaytarish
     * (o‘sha qator, miqdor, sana) saqlangan bo‘lsa — [DuplicateOperationError] ([confirmDuplicate] bo‘lmasa).
     * Partiya (yoki reaktiv) arxivda bo‘lsa — shu tranzaksiyada arxivdan chiqariladi: qoldiq jamilardan yashirinmasin.
     */
    fun returnItem(issueItemId: Long, input: ReturnInput, confirmDuplicate: Boolean = false): Long = stockGuard {
        db.write { conn -> insertReturn(conn, issueItemId, input, confirmDuplicate) }
    }

    private fun insertReturn(conn: Connection, issueItemId: Long, input: ReturnInput, confirmDuplicate: Boolean): Long {
        val today = clock.today()
        val item = loadItem(conn, issueItemId) ?: throw UserError("Berish yozuvi topilmadi.")
        if (item.cancelled) throw UserError("Bekor qilingan berishdan qaytarib bo‘lmaydi.")
        if (item.recipientType == RecipientType.WRITE_OFF) {
            throw UserError("Hisobdan chiqarilgan miqdor qaytarilmaydi — xato bo‘lsa, operatsiyani bekor qiling.")
        }
        val batch = loadBatchInfo(conn, item.batchId)!!
        val errors = FieldErrors()
        val date = errors.capture("date") { checkDate(input.date, "Qaytarish sanasi", "date", today) }
        if (date != null && date < item.issuedOn) {
            errors.add("date", "Qaytarish sanasi berilgan sanadan (${item.issuedOn.display()}) oldin bo‘lishi mumkin emas.")
        }
        val unit = input.unit
        if (unit == null) {
            errors.add("unit", "O‘lchov birligini tanlang.")
        } else if (unit.dimension != batch.unit.dimension) {
            errors.add("unit", unitMismatch(batch.unit))
        }
        val qty = unit?.let { u -> errors.capture("qtyText") { Qty.parse(input.qtyText, u, "Qaytarilgan miqdor", field = "qtyText") } }
        val reason = errors.capture("reason") { longText(input.reason, "Sabab", 500, "reason") } ?: ""
        val receivedBy = errors.capture("receivedBy") { shortText(input.receivedBy, "Qabul qilgan xodim", 100, "receivedBy") } ?: ""
        errors.throwIfAny()
        date!!
        qty!!
        unit!!

        val now = clock.now()
        if (!confirmDuplicate) {
            val same = conn.queryLong(
                """
                SELECT COUNT(*) FROM returns
                WHERE issue_item_id = ? AND qty = ? AND returned_on = ? AND cancelled_at IS NULL AND created_at >= ?
                """,
                issueItemId, qty, date, now.minusSeconds(DUPLICATE_WINDOW_SECONDS),
            )
            if (same > 0) throw DuplicateOperationError(duplicateMessage("qaytarish"))
        }
        val returned = activeReturns(conn, issueItemId)
        if (returned + qty > item.qty) {
            throw UserError(
                "Qaytariladigan miqdor berilganidan ko‘p: berilgan ${Qty.formatWithUnit(item.qty, batch.unit)}, " +
                    "avval qaytarilgan ${Qty.formatWithUnit(returned, batch.unit)}, endi ${Qty.formatWithUnit(qty, unit)}.",
                "qtyText",
            )
        }
        unarchiveForReturnedStock(conn, batch, now)
        val id = conn.insert(
            """
            INSERT INTO returns(issue_item_id, returned_on, qty, entered_qty, entered_unit, reason, received_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            issueItemId, date, qty, Qty.format(qty, unit), unit, reason, receivedBy, now,
        )
        insertMovement(conn, item.batchId, "RETURN", qty, date, now, issueItemId = issueItemId, returnId = id)
        audit.log(
            conn, AuditAction.RETURN, "return", id,
            "${batch.label}: ${item.recipientName} qaytardi, ${date.display()}" + (if (reason.isNotEmpty()) ". Sabab: $reason" else ""),
            reagentId = batch.reagentId, batchId = batch.id, qtyText = Qty.formatWithUnit(qty, unit),
        )
        log.info("return saved id=$id item=$issueItemId")
        return id
    }

    // ---------------------------------------------------------------- bekor qilish va tuzatish

    /** Kirimni bekor qilish: undan keyin berilgani sababli qoldiq manfiy bo‘lib qolsa — rad etiladi. */
    fun cancelReceipt(id: Long, reason: String?) = stockGuard {
        db.write { conn ->
            val text = reasonText(reason, "Bekor qilish sababi")
            cancelReceiptIn(conn, id, text)
        }
    }

    /** Berishni bekor qilish (miqdor partiyaga qaytadi). Faol qaytarishlari bo‘lsa — avval ularni bekor qilish kerak. */
    fun cancelIssueItem(id: Long, reason: String?) = stockGuard {
        db.write { conn ->
            val text = reasonText(reason, "Bekor qilish sababi")
            cancelIssueItemIn(conn, id, text)
        }
    }

    fun cancelReturn(id: Long, reason: String?) = stockGuard {
        db.write { conn ->
            val text = reasonText(reason, "Bekor qilish sababi")
            val ret = conn.queryOne("SELECT issue_item_id, qty, returned_on, cancelled_at FROM returns WHERE id = ?", id) { rs ->
                Ret(rs.long("issue_item_id"), rs.long("qty"), rs.date("returned_on")!!, rs.getString("cancelled_at") != null)
            } ?: throw UserError("Qaytarish yozuvi topilmadi.")
            if (ret.cancelled) throw UserError("Bu qaytarish allaqachon bekor qilingan.")
            val item = loadItem(conn, ret.itemId)!!
            val batch = loadBatchInfo(conn, item.batchId)!!
            val available = availableFrom(conn, batch.id, ret.date)
            if (ret.qty > available) throw cannotCancel(batch, available, ret.qty, "qaytarish")
            val movement = conn.queryLong("SELECT id FROM stock_movements WHERE return_id = ? AND kind = 'RETURN'", id)
            val now = clock.now()
            conn.update("UPDATE returns SET cancelled_at = ?, cancel_reason = ? WHERE id = ?", now, text, id)
            reverseMovement(conn, movement, text, now)
            audit.log(
                conn, AuditAction.CANCELLED, "return", id, "${batch.label}: qaytarish bekor qilindi. Sabab: $text",
                reagentId = batch.reagentId, batchId = batch.id, qtyText = Qty.formatWithUnit(ret.qty, batch.unit),
            )
        }
    }

    /**
     * Kirimni tuzatish: eski kirim bekor qilinadi va yangisi (corrects_id bilan) yoziladi — bitta tranzaksiyada.
     * Boshlang‘ich kirimning sanasi o‘zgarsa — partiyaning kelgan sanasi (batches.received_on) ham o‘sha sanaga o‘tadi
     * (ishlab chiqarilgan sanadan oldin va birinchi berishdan keyin bo‘lolmaydi).
     */
    fun correctReceipt(id: Long, input: ReceiptInput, reason: String?): Long = stockGuard {
        db.write { conn ->
            val text = reasonText(reason, "Tuzatish sababi")
            val old = loadReceipt(conn, id) ?: throw UserError("Kirim topilmadi.")
            if (old.cancelled) throw UserError("Bekor qilingan kirimni tuzatib bo‘lmaydi.")
            val newDate = input.date
            val syncBatch = old.isInitial && newDate != null && newDate != old.date
            // Sana o‘zi noto‘g‘ri bo‘lsa (kelajak, oraliqdan tashqari) — xabarni kirim tekshiruvi beradi.
            if (syncBatch && newDate!! >= MIN_DATE && newDate <= clock.today()) checkInitialDate(conn, old.batchId, newDate)
            // Avval yangisi — eski kirim bekor qilinganda qoldiq vaqtincha ham manfiy bo‘lmasin.
            val newId = insertReceipt(conn, old.batchId, input, confirmDuplicate = true, isInitial = old.isInitial, correctsId = id)
            cancelReceiptIn(conn, id, text, auditAction = AuditAction.CORRECTED)
            if (syncBatch) syncReceivedOn(conn, old.batchId, newDate!!)
            log.info("receipt corrected old=$id new=$newId")
            newId
        }
    }

    /** Boshlang‘ich kirimning yangi sanasi partiyaning kelgan sanasi bo‘la oladimi (ishlab chiqarilgandan keyin, birinchi berishgacha). */
    private fun checkInitialDate(conn: Connection, batchId: Long, date: LocalDate) {
        val produced = conn.queryOne("SELECT produced_on FROM batches WHERE id = ?", batchId) { rs -> rs.date("produced_on") }
        if (produced != null && date < produced) {
            throw UserError(
                "Boshlang‘ich kirim sanasi — partiyaning kelgan sanasi: u ishlab chiqarilgan sanadan (${produced.display()}) oldin bo‘lishi mumkin emas.",
                "date",
            )
        }
        val firstIssue = firstIssueDate(conn, batchId)
        if (firstIssue != null && date > firstIssue) {
            throw UserError("Partiyadan ${firstIssue.display()} da berilgan — kelgan sana undan keyin bo‘lishi mumkin emas.", "date")
        }
    }

    /** Partiyaning kelgan sanasi = faol boshlang‘ich kirim sanasi (bitta haqiqat manbai). */
    private fun syncReceivedOn(conn: Connection, batchId: Long, date: LocalDate) {
        val batch = loadBatchInfo(conn, batchId)!!
        if (batch.receivedOn == date) return
        conn.update("UPDATE batches SET received_on = ?, updated_at = ? WHERE id = ?", date, clock.now(), batchId)
        audit.log(
            conn, AuditAction.BATCH_UPDATED, "batch", batchId,
            "${batch.label}: kelgan sana: ${batch.receivedOn?.display() ?: "—"} → ${date.display()} (boshlang‘ich kirim tuzatildi)",
            reagentId = batch.reagentId, batchId = batchId,
        )
    }

    /**
     * Partiya kartochkasida kelgan sana o‘zgarganda ([Reagents.updateBatch]): faol boshlang‘ich kirim o‘sha kunga ko‘chiriladi —
     * bekor qilish + yangi yozuv (sabab [RECEIVED_DATE_REASON]). Ochiq tranzaksiya ichida chaqiriladi. Ko‘chirilsa —
     * yangi kirim id’si; boshlang‘ich kirim yo‘q yoki sanasi allaqachon shu bo‘lsa — null. Oldingi sanalardagi qoldiq manfiy
     * bo‘lib qolsa — [UserError] (sana bo‘yicha qoldiq tekshiruvi).
     */
    internal fun moveInitialReceipt(conn: Connection, batchId: Long, date: LocalDate): Long? {
        class Initial(
            val id: Long, val qty: Long, val date: LocalDate, val enteredQty: String, val enteredUnit: MeasureUnit,
            val receivedBy: String, val storage: String, val docNo: String, val note: String,
        )
        val old = conn.queryOne(
            """
            SELECT id, qty, received_on, entered_qty, entered_unit, received_by, storage_location, doc_no, note
            FROM receipts WHERE batch_id = ? AND is_initial = 1 AND cancelled_at IS NULL ORDER BY id DESC LIMIT 1
            """,
            batchId,
        ) { rs ->
            Initial(
                rs.long("id"), rs.long("qty"), rs.date("received_on")!!, rs.str("entered_qty"), rs.unit("entered_unit"),
                rs.str("received_by"), rs.str("storage_location"), rs.str("doc_no"), rs.str("note"),
            )
        } ?: return null
        if (old.date == date) return null
        val batch = loadBatchInfo(conn, batchId)!!
        val now = clock.now()
        val id = conn.insert(
            """
            INSERT INTO receipts(batch_id, received_on, qty, entered_qty, entered_unit, received_by, storage_location, doc_no, note,
                is_initial, created_at, corrects_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
            """,
            batchId, date, old.qty, old.enteredQty, old.enteredUnit, old.receivedBy, old.storage, old.docNo, old.note, now, old.id,
        )
        insertMovement(conn, batchId, "RECEIPT", old.qty, date, now, receiptId = id)
        audit.log(
            conn, AuditAction.RECEIPT, "receipt", id, "${batch.label}: kirim ${date.display()} (boshlang‘ich, sanasi tuzatildi)",
            reagentId = batch.reagentId, batchId = batchId, qtyText = Qty.formatWithUnit(old.qty, batch.unit),
        )
        cancelReceiptIn(conn, old.id, RECEIVED_DATE_REASON, auditAction = AuditAction.CORRECTED)
        return id
    }

    /** Berishni tuzatish: eski qator bekor qilinadi, yangisi (o‘sha hujjat ma’lumotlari bilan) yoziladi. Yangi qator id’si. */
    fun correctIssueItem(id: Long, line: IssueLine, date: LocalDate?, reason: String?, confirm: IssueConfirm = IssueConfirm.none): Long =
        stockGuard {
            db.write { conn ->
                val text = reasonText(reason, "Tuzatish sababi")
                val old = loadItem(conn, id) ?: throw UserError("Berish yozuvi topilmadi.")
                if (old.cancelled) throw UserError("Bekor qilingan berishni tuzatib bo‘lmaydi.")
                cancelIssueItemIn(conn, id, text, auditAction = AuditAction.CORRECTED)
                val header = conn.queryOne("SELECT doc_no, issued_by, note FROM issues WHERE id = ?", old.issueId) { rs ->
                    Triple(rs.str("doc_no"), rs.str("issued_by"), rs.str("note"))
                }!!
                val (_, items) = insertIssue(
                    conn,
                    IssueInput(date = date, docNo = header.first, issuedBy = header.second, note = header.third, lines = listOf(line)),
                    confirm.copy(allowDuplicate = true),
                    correctsId = id,
                )
                log.info("issue item corrected old=$id new=${items.single()}")
                items.single()
            }
        }

    private fun cancelReceiptIn(conn: Connection, id: Long, reason: String, auditAction: AuditAction = AuditAction.CANCELLED) {
        val receipt = loadReceipt(conn, id) ?: throw UserError("Kirim topilmadi.")
        if (receipt.cancelled) throw UserError("Bu kirim allaqachon bekor qilingan.")
        val batch = loadBatchInfo(conn, receipt.batchId)!!
        val available = availableFrom(conn, batch.id, receipt.date)
        if (receipt.qty > available) throw cannotCancel(batch, available, receipt.qty, "kirim")
        val movement = conn.queryLong("SELECT id FROM stock_movements WHERE receipt_id = ? AND kind = 'RECEIPT'", id)
        val now = clock.now()
        conn.update("UPDATE receipts SET cancelled_at = ?, cancel_reason = ? WHERE id = ?", now, reason, id)
        reverseMovement(conn, movement, reason, now)
        audit.log(
            conn, auditAction, "receipt", id,
            "${batch.label}: ${receipt.date.display()} dagi kirim " + (if (auditAction == AuditAction.CORRECTED) "tuzatildi" else "bekor qilindi") +
                ". Sabab: $reason",
            reagentId = batch.reagentId, batchId = batch.id, qtyText = Qty.formatWithUnit(receipt.qty, batch.unit),
        )
    }

    private fun cancelIssueItemIn(conn: Connection, id: Long, reason: String, auditAction: AuditAction = AuditAction.CANCELLED) {
        val item = loadItem(conn, id) ?: throw UserError("Berish yozuvi topilmadi.")
        if (item.cancelled) throw UserError("Bu berish allaqachon bekor qilingan.")
        if (activeReturns(conn, id) > 0) {
            throw UserError("Bu berishdan qaytarilgan miqdor bor — avval qaytarishni bekor qiling.")
        }
        val batch = loadBatchInfo(conn, item.batchId)!!
        val movement = conn.queryLong("SELECT id FROM stock_movements WHERE issue_item_id = ? AND kind = 'ISSUE'", id)
        val now = clock.now()
        unarchiveForReturnedStock(conn, batch, now)
        conn.update("UPDATE issue_items SET cancelled_at = ?, cancel_reason = ? WHERE id = ?", now, reason, id)
        reverseMovement(conn, movement, reason, now)
        audit.log(
            conn, auditAction, "issue_item", id,
            "${batch.label} → ${item.recipientName}, ${item.issuedOn.display()}: berish " +
                (if (auditAction == AuditAction.CORRECTED) "tuzatildi" else "bekor qilindi") + ". Sabab: $reason",
            reagentId = batch.reagentId, batchId = batch.id, qtyText = Qty.formatWithUnit(item.qty, batch.unit),
        )
    }

    // ---------------------------------------------------------------- qoldiq va FEFO

    fun remainder(batchId: Long): Long = db.read { conn -> remainderOf(conn, batchId) }

    /** [date] sanasi bilan chiqarish mumkin bo‘lgan miqdor (bugun uchun — qoldiq). */
    fun available(batchId: Long, date: LocalDate): Long = db.read { conn -> availableFrom(conn, batchId, date) }

    /**
     * FEFO tartibi: qoldig‘i bor, arxivda bo‘lmagan partiyalar — avval muddati o‘tmaganlari (muddati yaqinroq birinchi),
     * keyin muddati kiritilmaganlari, oxirida muddati o‘tganlari.
     */
    fun fefoOrder(reagentId: Long, dimension: Dimension, today: LocalDate): List<BatchStock> = db.read { conn ->
        conn.query(
            """
            SELECT b.id, b.reagent_id, b.series, b.received_on, b.expires_on, b.unit,
                   COALESCE((SELECT SUM(m.qty) FROM stock_movements m WHERE m.batch_id = b.id), 0) AS remainder
            FROM batches b JOIN reagents r ON r.id = b.reagent_id
            WHERE b.reagent_id = ? AND b.archived = 0 AND r.archived = 0
            """,
            reagentId,
        ) { rs ->
            val expires = rs.date("expires_on")
            BatchStock(
                batchId = rs.long("id"),
                reagentId = rs.long("reagent_id"),
                series = rs.str("series"),
                receivedOn = rs.date("received_on"),
                expiresOn = expires,
                daysLeft = daysLeft(expires, today),
                state = expiryState(expires, today),
                unit = rs.unit("unit"),
                remainder = rs.long("remainder"),
            )
        }
            .filter { it.unit.dimension == dimension && it.remainder > 0 }
            .sortedWith(
                compareBy<BatchStock>(
                    {
                        when (it.state) {
                            ExpiryState.EXPIRED -> 2
                            ExpiryState.UNKNOWN -> 1
                            else -> 0
                        }
                    },
                    { it.expiresOn },
                    { it.series },
                    { it.batchId },
                ),
            )
    }

    /** Tavsiya etiladigan partiya: muddati o‘tmagan, eng tez tugaydigani. Muddati o‘tgani hech qachon tavsiya etilmaydi. */
    fun fefoRecommendation(reagentId: Long, dimension: Dimension, today: LocalDate): Long? =
        fefoOrder(reagentId, dimension, today).firstOrNull { it.state != ExpiryState.EXPIRED }?.batchId

    /** Miqdorni muddati o‘tmagan partiyalarga FEFO bo‘yicha taqsimlash: (partiya, asosiy birlikdagi miqdor). */
    fun fefoSplit(reagentId: Long, dimension: Dimension, qtyBase: Long, today: LocalDate): List<Pair<Long, Long>> {
        if (qtyBase <= 0) throw UserError("Miqdor noldan katta bo‘lishi kerak.", "qtyText")
        val usable = fefoOrder(reagentId, dimension, today).filter { it.state != ExpiryState.EXPIRED }
        val total = usable.sumOf { it.remainder }
        if (total < qtyBase) {
            throw UserError(
                "Muddati o‘tmagan partiyalarda yetarli qoldiq yo‘q: mavjud ${Qty.formatAuto(total, dimension)}, " +
                    "kerak ${Qty.formatAuto(qtyBase, dimension)}.",
                "qtyText",
            )
        }
        var left = qtyBase
        val result = mutableListOf<Pair<Long, Long>>()
        for (batch in usable) {
            if (left == 0L) break
            val take = minOf(left, batch.remainder)
            result += batch.batchId to take
            left -= take
        }
        return result
    }

    // ---------------------------------------------------------------- yordamchilar

    private class ItemInfo(
        val id: Long,
        val issueId: Long,
        val batchId: Long,
        val qty: Long,
        val issuedOn: LocalDate,
        val recipientType: RecipientType,
        val recipientName: String,
        val cancelled: Boolean,
    )

    private fun loadItem(conn: Connection, id: Long): ItemInfo? = conn.queryOne(
        """
        SELECT i.id, i.issue_id, i.batch_id, i.qty, i.recipient_type, i.cancelled_at, h.issued_on, d.name AS dept, t.name AS district
        FROM issue_items i JOIN issues h ON h.id = i.issue_id
        LEFT JOIN departments d ON d.id = i.department_id LEFT JOIN districts t ON t.id = i.district_id
        WHERE i.id = ?
        """,
        id,
    ) { rs ->
        val type = RecipientType.valueOf(rs.str("recipient_type"))
        ItemInfo(
            id = rs.long("id"),
            issueId = rs.long("issue_id"),
            batchId = rs.long("batch_id"),
            qty = rs.long("qty"),
            issuedOn = rs.date("issued_on")!!,
            recipientType = type,
            recipientName = when (type) {
                RecipientType.DEPARTMENT -> "${rs.str("dept")} bo‘limi"
                RecipientType.DISTRICT -> "${rs.str("district")} tumani"
                RecipientType.WRITE_OFF -> "hisobdan chiqarish"
            },
            cancelled = rs.getString("cancelled_at") != null,
        )
    }

    private class Ret(val itemId: Long, val qty: Long, val date: LocalDate, val cancelled: Boolean)

    private class ReceiptInfo(val batchId: Long, val qty: Long, val date: LocalDate, val isInitial: Boolean, val cancelled: Boolean)

    private fun loadReceipt(conn: Connection, id: Long): ReceiptInfo? =
        conn.queryOne("SELECT batch_id, qty, received_on, is_initial, cancelled_at FROM receipts WHERE id = ?", id) { rs ->
            ReceiptInfo(rs.long("batch_id"), rs.long("qty"), rs.date("received_on")!!, rs.bool("is_initial"), rs.getString("cancelled_at") != null)
        }

    /**
     * Qoldiq arxivdagi partiyaga qaytsa (qaytarish, berishni bekor qilish yoki tuzatish) — partiya va reaktiv shu tranzaksiyada
     * arxivdan chiqariladi (audit bilan): aks holda qoldiq jamilar, FEFO va jadvaldan yashirinib qoladi.
     */
    private fun unarchiveForReturnedStock(conn: Connection, batch: BatchInfo, now: LocalDateTime) {
        if (batch.reagentArchived) {
            conn.update("UPDATE reagents SET archived = 0, updated_at = ? WHERE id = ?", now, batch.reagentId)
            audit.log(conn, AuditAction.UNARCHIVED, "reagent", batch.reagentId, "«${batch.name}» — $UNARCHIVE_REASON", reagentId = batch.reagentId)
        }
        if (batch.archived) {
            conn.update("UPDATE batches SET archived = 0, updated_at = ? WHERE id = ?", now, batch.id)
            audit.log(
                conn, AuditAction.UNARCHIVED, "batch", batch.id, "${batch.label} — $UNARCHIVE_REASON",
                reagentId = batch.reagentId, batchId = batch.id,
            )
        }
    }

    private fun firstIssueDate(conn: Connection, batchId: Long): LocalDate? = conn.queryOne(
        "SELECT MIN(h.issued_on) FROM issue_items i JOIN issues h ON h.id = i.issue_id WHERE i.batch_id = ? AND i.cancelled_at IS NULL",
        batchId,
    ) { rs -> rs.getString(1)?.let(LocalDate::parse) }

    private fun activeReturns(conn: Connection, issueItemId: Long): Long =
        conn.queryLong("SELECT COALESCE(SUM(qty), 0) FROM returns WHERE issue_item_id = ? AND cancelled_at IS NULL", issueItemId)

    private fun insufficient(batch: BatchInfo, available: Long, requested: Long) = InsufficientStockError(
        batch.id, available, requested, batch.unit,
        "${batch.label} partiyasida qoldiq yetarli emas: mavjud ${Qty.formatWithUnit(available, batch.unit)}, " +
            "so‘ralgan ${Qty.formatWithUnit(requested, batch.unit)}. Hech narsa saqlanmadi.",
    )

    private fun cannotCancel(batch: BatchInfo, available: Long, qty: Long, what: String) = UserError(
        "Bu $what bekor qilinsa ${batch.label} partiyasining qoldig‘i manfiy bo‘lib qoladi " +
            "(undan keyin berilgan: mavjud ${Qty.formatWithUnit(available, batch.unit)}, $what ${Qty.formatWithUnit(qty, batch.unit)}). " +
            "Avval keyingi berishlarni bekor qiling yoki tuzating.",
    )

    private fun unitMismatch(batchUnit: MeasureUnit) =
        "Birlik partiya birligiga mos emas: ${batchUnit.siblings.joinToString(" yoki ") { it.code }} tanlang."

    private fun duplicateMessage(what: String) =
        "Xuddi shunday $what hozirgina (2 daqiqa ichida) saqlangan. Ikki marta bosilgan bo‘lishi mumkin — baribir saqlash uchun tasdiqlang."

    private fun prefix(number: Int, total: Int) = if (total > 1) "$number-qator: " else ""

    /** Qator raqami bo‘lmasa — xabar bosh harf bilan. */
    private fun cap(prefix: String, text: String) = if (prefix.isEmpty()) text.replaceFirstChar { it.uppercase() } else prefix + text
}

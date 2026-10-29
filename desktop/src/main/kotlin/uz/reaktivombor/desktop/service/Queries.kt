package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.daysLeft
import uz.reaktivombor.desktop.core.display
import uz.reaktivombor.desktop.core.expiryState
import uz.reaktivombor.desktop.core.matchesQuery
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.bool
import uz.reaktivombor.desktop.db.date
import uz.reaktivombor.desktop.db.dateTime
import uz.reaktivombor.desktop.db.long
import uz.reaktivombor.desktop.db.longOrNull
import uz.reaktivombor.desktop.db.query
import uz.reaktivombor.desktop.db.str
import uz.reaktivombor.desktop.db.strOrNull
import uz.reaktivombor.desktop.db.unit
import uz.reaktivombor.desktop.db.unitOrNull
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime

// ---------------------------------------------------------------- jadval modellari

/**
 * «Barcha reaktivlar» jadvalining bitta qatori — bitta partiya. Miqdorlar asosiy birlikda, [unit] — partiya birligi.
 * [issuedQty] — bo‘lim va tumanlarga berilgani (qaytarilgani ayirilgan), [writtenOffQty] — hisobdan chiqarilgani.
 * [groupTotal]/[groupReceived] — shu reaktiv va o‘lchov turidagi arxivlanmagan BARCHA partiyalar yig‘indisi
 * (jadval filtrlariga bog‘liq emas), [groupUnit] da ko‘rsatiladi.
 */
data class BatchRow(
    val batchId: Long,
    val reagentId: Long,
    val name: String,
    val formula: String,
    val series: String,
    val manufacturer: String,
    val departmentId: Long?,
    val departmentName: String?,
    val storageLocation: String,
    val producedOn: LocalDate?,
    val receivedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val daysLeft: Long?,
    val state: ExpiryState,
    val unit: MeasureUnit,
    val receivedQty: Long,
    val issuedQty: Long,
    val writtenOffQty: Long,
    val remainder: Long,
    val groupTotal: Long,
    val groupUnit: MeasureUnit,
    val groupReceived: Long,
    val responsible: String,
    val note: String,
    val archived: Boolean,
    val reagentArchived: Boolean,
    val belowMin: Boolean,
) {
    val dimension: Dimension get() = unit.dimension
}

/**
 * Jadval filtri. Bo‘sh/null maydon — cheklov yo‘q. [states] bo‘sh — hamma holatlar.
 * [remainderMin]/[remainderMax] asosiy birlikda va faqat [remainderUnit] o‘lchov turidagi qatorlarga qo‘llanadi.
 */
data class BatchFilter(
    val text: String? = null,
    val series: String? = null,
    val departmentId: Long? = null,
    val districtId: Long? = null,
    val producedFrom: LocalDate? = null,
    val producedTo: LocalDate? = null,
    val receivedFrom: LocalDate? = null,
    val receivedTo: LocalDate? = null,
    val expiresFrom: LocalDate? = null,
    val expiresTo: LocalDate? = null,
    val states: Set<ExpiryState> = emptySet(),
    val remainderMin: Long? = null,
    val remainderMax: Long? = null,
    val remainderUnit: MeasureUnit? = null,
    val responsible: String? = null,
    val includeArchived: Boolean = false,
    val includeEmpty: Boolean = true,
    val reagentId: Long? = null,
)

/** «Umumiy zaxiralar»: reaktiv + o‘lchov turi bo‘yicha jami. */
data class StockTotal(
    val reagentId: Long,
    val name: String,
    val formula: String,
    val dimension: Dimension,
    val unit: MeasureUnit,
    val batches: Int,
    val activeBatches: Int,
    val received: Long,
    val issued: Long,
    val writtenOff: Long,
    val remainder: Long,
    val minStock: Long?,
    val minStockUnit: MeasureUnit?,
    val belowMin: Boolean,
    val nearestExpiry: LocalDate?,
    /** Qoldig‘i bor partiyalar ichida eng xavflisi; qoldiq yo‘q bo‘lsa — null. */
    val worstState: ExpiryState?,
    val reagentArchived: Boolean,
)

data class OpFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val text: String? = null,
    val series: String? = null,
    val departmentId: Long? = null,
    val districtId: Long? = null,
    val recipientType: RecipientType? = null,
    val includeCancelled: Boolean = false,
    val batchId: Long? = null,
    val reagentId: Long? = null,
)

data class ReceiptRow(
    val id: Long,
    val batchId: Long,
    val reagentId: Long,
    val name: String,
    val series: String,
    val expiresOn: LocalDate?,
    val unit: MeasureUnit,
    val departmentName: String?,
    val receivedOn: LocalDate,
    val qty: Long,
    val enteredQty: String,
    val enteredUnit: MeasureUnit,
    val receivedBy: String,
    val storageLocation: String,
    val docNo: String,
    val note: String,
    val isInitial: Boolean,
    val createdAt: LocalDateTime,
    val cancelled: Boolean,
    val cancelledAt: LocalDateTime?,
    val cancelReason: String?,
    val correctsId: Long?,
)

data class IssueItemRow(
    val id: Long,
    val issueId: Long,
    val issuedOn: LocalDate,
    val docNo: String,
    val issuedBy: String,
    val issueNote: String,
    val batchId: Long,
    val reagentId: Long,
    val name: String,
    val series: String,
    val expiresOn: LocalDate?,
    val unit: MeasureUnit,
    val recipientType: RecipientType,
    val departmentId: Long?,
    val departmentName: String?,
    val districtId: Long?,
    val districtName: String?,
    /** «Biologiya», «Chilonzor» yoki «Hisobdan chiqarish». */
    val recipientName: String,
    val organization: String,
    val receiver: String,
    val qty: Long,
    val enteredQty: String,
    val enteredUnit: MeasureUnit,
    val returnedQty: Long,
    val fefoOverride: Boolean,
    val expiredOverride: Boolean,
    val note: String,
    val createdAt: LocalDateTime,
    val cancelled: Boolean,
    val cancelledAt: LocalDateTime?,
    val cancelReason: String?,
    val correctsId: Long?,
) {
    /** Berilgan − qaytarilgan. */
    val netQty: Long get() = qty - returnedQty
}

data class ReturnRow(
    val id: Long,
    val issueItemId: Long,
    val returnedOn: LocalDate,
    val qty: Long,
    val enteredQty: String,
    val enteredUnit: MeasureUnit,
    val reason: String,
    val receivedBy: String,
    val createdAt: LocalDateTime,
    val cancelled: Boolean,
    val cancelledAt: LocalDateTime?,
    val cancelReason: String?,
    val batchId: Long,
    val reagentId: Long,
    val name: String,
    val series: String,
    val unit: MeasureUnit,
    val recipientType: RecipientType,
    val recipientName: String,
    val departmentId: Long?,
    val districtId: Long?,
    val issuedOn: LocalDate,
)

/** Bo‘lim yoki tumanga berilgan jami: reaktiv + o‘lchov turi bo‘yicha. */
data class ReagentQtyTotal(
    val reagentId: Long,
    val name: String,
    val dimension: Dimension,
    val unit: MeasureUnit,
    val issued: Long,
    val returned: Long,
    val items: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
) {
    val net: Long get() = issued - returned
}

data class DepartmentSummary(
    val department: Place,
    /** Shu bo‘limda saqlanadigan (bo‘limga biriktirilgan) partiyalar. */
    val ownedBatches: List<BatchRow>,
    /** Bo‘limga berilgan reaktivlar (qaytarilgani ayirilgan), davr bo‘yicha. */
    val issuedTo: List<ReagentQtyTotal>,
    /** Bo‘limga berish tarixi (davr bo‘yicha, yangisi birinchi). */
    val history: List<IssueItemRow>,
)

data class DistrictReagentTotal(
    val districtId: Long,
    val districtName: String,
    val total: ReagentQtyTotal,
)

data class DistrictTotal(
    val districtId: Long,
    val name: String,
    val items: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    /** O‘lchov turi bo‘yicha (qaytarilgani ayirilgan). */
    val net: Map<Dimension, Long>,
)

data class DistrictSummary(
    val districts: List<DistrictTotal>,
    val perReagent: List<DistrictReagentTotal>,
    val items: List<IssueItemRow>,
)

data class DepartmentStat(
    val departmentId: Long,
    val name: String,
    /** Bo‘limdagi qoldig‘i bor partiyalar soni. */
    val ownedBatches: Int,
    val remainder: Map<Dimension, Long>,
    val issuedTo: Map<Dimension, Long>,
    val issuedItems: Int,
)

data class DistrictStat(val districtId: Long, val name: String, val items: Int, val issued: Map<Dimension, Long>)

data class Dashboard(
    val reagentTypes: Int,
    val activeBatches: Int,
    /** Qizil va sariq, qoldig‘i bor. */
    val expiringBatches: Int,
    /** Muddati o‘tgan, qoldig‘i bor. */
    val expiredBatches: Int,
    val belowMinReagents: Int,
    val byDepartment: List<DepartmentStat>,
    val byDistrict: List<DistrictStat>,
    /** Qoldig‘i bor partiyalar holat bo‘yicha (hamma holatlar, 0 bilan ham). */
    val stateCounts: Map<ExpiryState, Int>,
    val recentReceipts: List<ReceiptRow>,
    val recentIssues: List<IssueItemRow>,
)

enum class NotificationKind(val label: String) {
    EXPIRED("Muddati o‘tgan"),
    RED("1 oy yoki kam qoldi"),
    BELOW_MIN("Minimal qoldiqdan kam"),
    YELLOW("1–3 oy qoldi"),
}

/** Eslatma bosilganda ochiladigan sahifa. */
enum class NotificationTarget { EXPIRY, STOCK }

data class Notification(
    val kind: NotificationKind,
    val text: String,
    val target: NotificationTarget,
    val reagentId: Long,
    val batchId: Long?,
    val daysLeft: Long?,
) {
    /** Qo‘ng‘iroqcha sonida hisoblanadimi (muddati o‘tgan, qizil, minimal qoldiqdan kam). */
    val counted: Boolean get() = kind != NotificationKind.YELLOW
}

enum class MovementKind(val label: String) {
    RECEIPT("Kirim"),
    ISSUE("Chiqim"),
    RETURN("Qaytarish"),
    ADJUSTMENT("Moslash"),
    REVERSAL("Bekor qilish"),
}

/**
 * Harakatlar jurnali qatori: [balance] — shu partiyaning shu harakatdan keyingi qoldig‘i. Bekor qilingan harakat va uning
 * teskari (REVERSAL) yozuvi qoldiqni o‘zgartirmaydi — ikkalasida ham oldingi qoldiq ko‘rsatiladi.
 */
data class LedgerRow(
    val movementId: Long,
    val batchId: Long,
    val reagentId: Long,
    val name: String,
    val series: String,
    val unit: MeasureUnit,
    val movedOn: LocalDate,
    val kind: MovementKind,
    val qty: Long,
    val balance: Long,
    val description: String,
    val docNo: String,
    val receiptId: Long?,
    val issueItemId: Long?,
    val returnId: Long?,
    val reversesId: Long?,
    val note: String,
    val createdAt: LocalDateTime,
    /** Bu harakatning operatsiyasi keyin bekor qilinganmi. */
    val operationCancelled: Boolean,
)

/** Jadvallar, bosh sahifa, eslatmalar va jurnallar uchun o‘qish modellari. Yozmaydi. */
class Queries(private val db: Database, private val audit: Audit) {

    // ---------------------------------------------------------------- partiyalar

    private class BatchData(
        val batchId: Long,
        val reagentId: Long,
        val name: String,
        val nameKey: String,
        val formula: String,
        val series: String,
        val manufacturer: String,
        val departmentId: Long?,
        val departmentName: String?,
        val storageLocation: String,
        val producedOn: LocalDate?,
        val receivedOn: LocalDate?,
        val expiresOn: LocalDate?,
        val unit: MeasureUnit,
        val responsible: String,
        val note: String,
        val archived: Boolean,
        val reagentArchived: Boolean,
        val minStock: Long?,
        val minStockUnit: MeasureUnit?,
        val remainder: Long,
        val received: Long,
        val issued: Long,
        val writtenOff: Long,
    )

    private fun loadBatches(conn: Connection): List<BatchData> = conn.query(
        """
        WITH agg AS (
            SELECT m.batch_id,
                   SUM(m.qty) AS remainder,
                   SUM(CASE WHEN m.receipt_id IS NOT NULL THEN m.qty ELSE 0 END) AS received,
                   SUM(CASE WHEN i.recipient_type IN ('DEPARTMENT','DISTRICT') THEN -m.qty ELSE 0 END) AS issued,
                   SUM(CASE WHEN i.recipient_type = 'WRITE_OFF' THEN -m.qty ELSE 0 END) AS written_off
            FROM stock_movements m LEFT JOIN issue_items i ON i.id = m.issue_item_id
            GROUP BY m.batch_id
        )
        SELECT b.*, r.name, r.name_key, r.formula, r.archived AS r_archived, r.min_stock, r.min_stock_unit, d.name AS dept_name,
               COALESCE(a.remainder, 0) AS remainder, COALESCE(a.received, 0) AS received,
               COALESCE(a.issued, 0) AS issued, COALESCE(a.written_off, 0) AS written_off
        FROM batches b
        JOIN reagents r ON r.id = b.reagent_id
        LEFT JOIN departments d ON d.id = b.department_id
        LEFT JOIN agg a ON a.batch_id = b.id
        """,
    ) { rs ->
        BatchData(
            batchId = rs.long("id"),
            reagentId = rs.long("reagent_id"),
            name = rs.str("name"),
            nameKey = rs.str("name_key"),
            formula = rs.str("formula"),
            series = rs.str("series"),
            manufacturer = rs.str("manufacturer"),
            departmentId = rs.longOrNull("department_id"),
            departmentName = rs.strOrNull("dept_name"),
            storageLocation = rs.str("storage_location"),
            producedOn = rs.date("produced_on"),
            receivedOn = rs.date("received_on"),
            expiresOn = rs.date("expires_on"),
            unit = rs.unit("unit"),
            responsible = rs.str("responsible"),
            note = rs.str("note"),
            archived = rs.bool("archived"),
            reagentArchived = rs.bool("r_archived"),
            minStock = rs.longOrNull("min_stock"),
            minStockUnit = rs.unitOrNull("min_stock_unit"),
            remainder = rs.long("remainder"),
            received = rs.long("received"),
            issued = rs.long("issued"),
            writtenOff = rs.long("written_off"),
        )
    }

    private data class GroupKey(val reagentId: Long, val dimension: Dimension)

    private class Group(val total: Long, val received: Long, val unit: MeasureUnit)

    /** Guruh jamilari: arxivlanmagan partiyalar bo‘yicha (filtrlardan mustaqil). */
    private fun groups(all: List<BatchData>): Map<GroupKey, Group> =
        all.groupBy { GroupKey(it.reagentId, it.unit.dimension) }.mapValues { (_, list) ->
            val live = list.filter { !it.archived }
            Group(
                total = live.sumOf { it.remainder },
                received = live.sumOf { it.received },
                unit = MeasureUnit.largest(live.map { it.unit }) ?: MeasureUnit.largest(list.map { it.unit })!!,
            )
        }

    private fun BatchData.belowMin(group: Group?): Boolean {
        val min = minStock ?: return false
        val minUnit = minStockUnit ?: return false
        return !reagentArchived && group != null && minUnit.dimension == unit.dimension && group.total < min
    }

    /**
     * Partiyalar jadvali. Tartib: reaktiv nomi (katta-kichik harf va tutuq belgisi farqsiz), o‘lchov turi, muddati
     * (FEFO, muddatsizlar oxirida), seriya — bir xil nomli qatorlar doim yonma-yon.
     */
    fun batchRows(filter: BatchFilter = BatchFilter(), today: LocalDate): List<BatchRow> = db.read { conn ->
        val all = loadBatches(conn)
        val groups = groups(all)
        val districtBatches = filter.districtId?.let { id ->
            conn.query(
                "SELECT DISTINCT batch_id FROM issue_items WHERE district_id = ? AND cancelled_at IS NULL",
                id,
            ) { it.getLong(1) }.toSet()
        }
        all.asSequence()
            .map { it to toRow(it, groups[GroupKey(it.reagentId, it.unit.dimension)]!!, today) }
            .filter { (data, row) -> matches(filter, data, row, districtBatches) }
            .sortedWith(rowOrder)
            .map { it.second }
            .toList()
    }

    // name_key bazada tayyor (katta-kichik harf va tutuq belgisi farqsiz).
    private val rowOrder = compareBy<Pair<BatchData, BatchRow>>(
        { it.first.nameKey },
        { it.first.name },
        { it.second.unit.dimension.ordinal },
        { it.second.expiresOn == null },
        { it.second.expiresOn },
        { it.second.series.lowercase() },
        { it.second.batchId },
    )

    private fun toRow(data: BatchData, group: Group, today: LocalDate) = BatchRow(
        batchId = data.batchId,
        reagentId = data.reagentId,
        name = data.name,
        formula = data.formula,
        series = data.series,
        manufacturer = data.manufacturer,
        departmentId = data.departmentId,
        departmentName = data.departmentName,
        storageLocation = data.storageLocation,
        producedOn = data.producedOn,
        receivedOn = data.receivedOn,
        expiresOn = data.expiresOn,
        daysLeft = daysLeft(data.expiresOn, today),
        state = expiryState(data.expiresOn, today),
        unit = data.unit,
        receivedQty = data.received,
        issuedQty = data.issued,
        writtenOffQty = data.writtenOff,
        remainder = data.remainder,
        groupTotal = group.total,
        groupUnit = group.unit,
        groupReceived = group.received,
        responsible = data.responsible,
        note = data.note,
        archived = data.archived,
        reagentArchived = data.reagentArchived,
        belowMin = data.belowMin(group),
    )

    private fun matches(f: BatchFilter, data: BatchData, row: BatchRow, districtBatches: Set<Long>?): Boolean {
        if (!f.includeArchived && (row.archived || row.reagentArchived)) return false
        if (!f.includeEmpty && row.remainder == 0L) return false
        if (f.reagentId != null && row.reagentId != f.reagentId) return false
        if (!matchesQuery(
                f.text, row.name, row.formula, row.series, row.manufacturer, row.departmentName,
                row.storageLocation, row.responsible, row.note,
            )
        ) {
            return false
        }
        if (!f.series.isNullOrBlank() && !nameKey(row.series).contains(nameKey(f.series))) return false
        if (!f.responsible.isNullOrBlank() && !nameKey(row.responsible).contains(nameKey(f.responsible))) return false
        if (f.departmentId != null && row.departmentId != f.departmentId) return false
        if (districtBatches != null && data.batchId !in districtBatches) return false
        if (!inRange(row.producedOn, f.producedFrom, f.producedTo)) return false
        if (!inRange(row.receivedOn, f.receivedFrom, f.receivedTo)) return false
        if (!inRange(row.expiresOn, f.expiresFrom, f.expiresTo)) return false
        if (f.states.isNotEmpty() && row.state !in f.states) return false
        val unit = f.remainderUnit
        if (unit != null && (f.remainderMin != null || f.remainderMax != null)) {
            if (row.unit.dimension != unit.dimension) return false
            if (f.remainderMin != null && row.remainder < f.remainderMin) return false
            if (f.remainderMax != null && row.remainder > f.remainderMax) return false
        }
        return true
    }

    private fun inRange(date: LocalDate?, from: LocalDate?, to: LocalDate?): Boolean {
        if (from == null && to == null) return true
        if (date == null) return false
        return (from == null || date >= from) && (to == null || date <= to)
    }

    /** Reaktiv + o‘lchov turi bo‘yicha jamilar (filtrdagi partiyalar bo‘yicha; «minimal qoldiqdan kam» — butun guruh bo‘yicha). */
    fun stockTotals(filter: BatchFilter = BatchFilter(), today: LocalDate): List<StockTotal> {
        val rows = batchRows(filter, today)
        return rows.groupBy { GroupKey(it.reagentId, it.unit.dimension) }.map { (key, list) ->
            val withStock = list.filter { it.remainder > 0 && !it.archived }
            val minInfo = db.read { conn ->
                conn.query("SELECT min_stock, min_stock_unit FROM reagents WHERE id = ?", key.reagentId) { rs ->
                    rs.longOrNull("min_stock") to rs.unitOrNull("min_stock_unit")
                }.firstOrNull()
            }
            StockTotal(
                reagentId = key.reagentId,
                name = list.first().name,
                formula = list.first().formula,
                dimension = key.dimension,
                unit = list.first().groupUnit,
                batches = list.size,
                activeBatches = withStock.size,
                received = list.sumOf { it.receivedQty },
                issued = list.sumOf { it.issuedQty },
                writtenOff = list.sumOf { it.writtenOffQty },
                remainder = list.sumOf { it.remainder },
                minStock = minInfo?.first,
                minStockUnit = minInfo?.second,
                belowMin = list.any { it.belowMin },
                nearestExpiry = withStock.mapNotNull { it.expiresOn }.minOrNull(),
                worstState = withStock.maxByOrNull { it.state.severity }?.state,
                reagentArchived = list.first().reagentArchived,
            )
        }.sortedWith(compareBy({ nameKey(it.name) }, { it.name }, { it.dimension.ordinal }))
    }

    // ---------------------------------------------------------------- operatsiyalar

    fun receipts(filter: OpFilter = OpFilter()): List<ReceiptRow> = db.read { conn ->
        conn.query(
            """
            SELECT x.*, b.reagent_id, b.series, b.expires_on, b.unit, b.department_id, r.name, d.name AS dept_name
            FROM receipts x
            JOIN batches b ON b.id = x.batch_id
            JOIN reagents r ON r.id = b.reagent_id
            LEFT JOIN departments d ON d.id = b.department_id
            ORDER BY x.received_on DESC, x.id DESC
            """,
        ) { rs ->
            rs.longOrNull("department_id") to ReceiptRow(
                id = rs.long("id"),
                batchId = rs.long("batch_id"),
                reagentId = rs.long("reagent_id"),
                name = rs.str("name"),
                series = rs.str("series"),
                expiresOn = rs.date("expires_on"),
                unit = rs.unit("unit"),
                departmentName = rs.strOrNull("dept_name"),
                receivedOn = rs.date("received_on")!!,
                qty = rs.long("qty"),
                enteredQty = rs.str("entered_qty"),
                enteredUnit = rs.unit("entered_unit"),
                receivedBy = rs.str("received_by"),
                storageLocation = rs.str("storage_location"),
                docNo = rs.str("doc_no"),
                note = rs.str("note"),
                isInitial = rs.bool("is_initial"),
                createdAt = rs.dateTime("created_at")!!,
                cancelled = rs.getString("cancelled_at") != null,
                cancelledAt = rs.dateTime("cancelled_at"),
                cancelReason = rs.strOrNull("cancel_reason"),
                correctsId = rs.longOrNull("corrects_id"),
            )
        }.filter { (departmentId, row) ->
            (filter.includeCancelled || !row.cancelled) &&
                inRange(row.receivedOn, filter.from, filter.to) &&
                (filter.batchId == null || row.batchId == filter.batchId) &&
                (filter.reagentId == null || row.reagentId == filter.reagentId) &&
                (filter.departmentId == null || departmentId == filter.departmentId) &&
                (filter.series.isNullOrBlank() || nameKey(row.series).contains(nameKey(filter.series))) &&
                matchesQuery(filter.text, row.name, row.series, row.docNo, row.receivedBy, row.note, row.storageLocation)
        }.map { it.second }
    }

    fun issueItems(filter: OpFilter = OpFilter()): List<IssueItemRow> = db.read { conn -> loadIssueItems(conn, filter) }

    private fun loadIssueItems(conn: Connection, filter: OpFilter): List<IssueItemRow> = conn.query(
        """
        SELECT i.*, h.issued_on, h.doc_no, h.issued_by, h.note AS issue_note,
               b.reagent_id, b.series, b.expires_on, b.unit, r.name, d.name AS dept_name, t.name AS district_name,
               COALESCE((SELECT SUM(x.qty) FROM returns x WHERE x.issue_item_id = i.id AND x.cancelled_at IS NULL), 0) AS returned
        FROM issue_items i
        JOIN issues h ON h.id = i.issue_id
        JOIN batches b ON b.id = i.batch_id
        JOIN reagents r ON r.id = b.reagent_id
        LEFT JOIN departments d ON d.id = i.department_id
        LEFT JOIN districts t ON t.id = i.district_id
        ORDER BY h.issued_on DESC, i.id DESC
        """,
    ) { rs ->
        val type = RecipientType.valueOf(rs.str("recipient_type"))
        val deptName = rs.strOrNull("dept_name")
        val districtName = rs.strOrNull("district_name")
        IssueItemRow(
            id = rs.long("id"),
            issueId = rs.long("issue_id"),
            issuedOn = rs.date("issued_on")!!,
            docNo = rs.str("doc_no"),
            issuedBy = rs.str("issued_by"),
            issueNote = rs.str("issue_note"),
            batchId = rs.long("batch_id"),
            reagentId = rs.long("reagent_id"),
            name = rs.str("name"),
            series = rs.str("series"),
            expiresOn = rs.date("expires_on"),
            unit = rs.unit("unit"),
            recipientType = type,
            departmentId = rs.longOrNull("department_id"),
            departmentName = deptName,
            districtId = rs.longOrNull("district_id"),
            districtName = districtName,
            recipientName = when (type) {
                RecipientType.DEPARTMENT -> deptName ?: ""
                RecipientType.DISTRICT -> districtName ?: ""
                RecipientType.WRITE_OFF -> RecipientType.WRITE_OFF.label
            },
            organization = rs.str("organization"),
            receiver = rs.str("receiver"),
            qty = rs.long("qty"),
            enteredQty = rs.str("entered_qty"),
            enteredUnit = rs.unit("entered_unit"),
            returnedQty = rs.long("returned"),
            fefoOverride = rs.bool("fefo_override"),
            expiredOverride = rs.bool("expired_override"),
            note = rs.str("note"),
            createdAt = rs.dateTime("created_at")!!,
            cancelled = rs.getString("cancelled_at") != null,
            cancelledAt = rs.dateTime("cancelled_at"),
            cancelReason = rs.strOrNull("cancel_reason"),
            correctsId = rs.longOrNull("corrects_id"),
        )
    }.filter { row ->
        (filter.includeCancelled || !row.cancelled) &&
            inRange(row.issuedOn, filter.from, filter.to) &&
            (filter.batchId == null || row.batchId == filter.batchId) &&
            (filter.reagentId == null || row.reagentId == filter.reagentId) &&
            (filter.departmentId == null || row.departmentId == filter.departmentId) &&
            (filter.districtId == null || row.districtId == filter.districtId) &&
            (filter.recipientType == null || row.recipientType == filter.recipientType) &&
            (filter.series.isNullOrBlank() || nameKey(row.series).contains(nameKey(filter.series))) &&
            matchesQuery(filter.text, row.name, row.series, row.docNo, row.issuedBy, row.recipientName, row.organization, row.receiver, row.note)
    }

    fun returns(filter: OpFilter = OpFilter()): List<ReturnRow> = db.read { conn ->
        conn.query(
            """
            SELECT x.*, i.batch_id, i.recipient_type, i.department_id, i.district_id, h.issued_on,
                   b.reagent_id, b.series, b.unit, r.name, d.name AS dept_name, t.name AS district_name
            FROM returns x
            JOIN issue_items i ON i.id = x.issue_item_id
            JOIN issues h ON h.id = i.issue_id
            JOIN batches b ON b.id = i.batch_id
            JOIN reagents r ON r.id = b.reagent_id
            LEFT JOIN departments d ON d.id = i.department_id
            LEFT JOIN districts t ON t.id = i.district_id
            ORDER BY x.returned_on DESC, x.id DESC
            """,
        ) { rs ->
            val type = RecipientType.valueOf(rs.str("recipient_type"))
            ReturnRow(
                id = rs.long("id"),
                issueItemId = rs.long("issue_item_id"),
                returnedOn = rs.date("returned_on")!!,
                qty = rs.long("qty"),
                enteredQty = rs.str("entered_qty"),
                enteredUnit = rs.unit("entered_unit"),
                reason = rs.str("reason"),
                receivedBy = rs.str("received_by"),
                createdAt = rs.dateTime("created_at")!!,
                cancelled = rs.getString("cancelled_at") != null,
                cancelledAt = rs.dateTime("cancelled_at"),
                cancelReason = rs.strOrNull("cancel_reason"),
                batchId = rs.long("batch_id"),
                reagentId = rs.long("reagent_id"),
                name = rs.str("name"),
                series = rs.str("series"),
                unit = rs.unit("unit"),
                recipientType = type,
                recipientName = when (type) {
                    RecipientType.DEPARTMENT -> rs.str("dept_name")
                    RecipientType.DISTRICT -> rs.str("district_name")
                    RecipientType.WRITE_OFF -> RecipientType.WRITE_OFF.label
                },
                departmentId = rs.longOrNull("department_id"),
                districtId = rs.longOrNull("district_id"),
                issuedOn = rs.date("issued_on")!!,
            )
        }.filter { row ->
            (filter.includeCancelled || !row.cancelled) &&
                inRange(row.returnedOn, filter.from, filter.to) &&
                (filter.batchId == null || row.batchId == filter.batchId) &&
                (filter.reagentId == null || row.reagentId == filter.reagentId) &&
                (filter.departmentId == null || row.departmentId == filter.departmentId) &&
                (filter.districtId == null || row.districtId == filter.districtId) &&
                (filter.recipientType == null || row.recipientType == filter.recipientType) &&
                (filter.series.isNullOrBlank() || nameKey(row.series).contains(nameKey(filter.series))) &&
                matchesQuery(filter.text, row.name, row.series, row.recipientName, row.reason, row.receivedBy)
        }
    }

    // ---------------------------------------------------------------- bo‘lim va tumanlar

    fun departmentSummary(departmentId: Long, from: LocalDate? = null, to: LocalDate? = null, today: LocalDate): DepartmentSummary {
        val department = db.read { conn ->
            conn.query("SELECT id, name, active, sort_order FROM departments WHERE id = ?", departmentId) { rs ->
                Place(rs.long("id"), rs.str("name"), rs.bool("active"), rs.getInt("sort_order"))
            }.firstOrNull()
        } ?: throw UserError("Bo‘lim topilmadi.")
        val owned = batchRows(BatchFilter(departmentId = departmentId), today)
        val history = issueItems(OpFilter(from = from, to = to, departmentId = departmentId, recipientType = RecipientType.DEPARTMENT))
        return DepartmentSummary(department, owned, totalsByReagent(history), history)
    }

    fun districtSummary(districtId: Long? = null, from: LocalDate? = null, to: LocalDate? = null): DistrictSummary {
        val items = issueItems(OpFilter(from = from, to = to, districtId = districtId, recipientType = RecipientType.DISTRICT))
        val byDistrict = items.groupBy { it.districtId!! }
        val districts = byDistrict.map { (id, list) ->
            DistrictTotal(
                districtId = id,
                name = list.first().districtName.orEmpty(),
                items = list.size,
                firstDate = list.minOfOrNull { it.issuedOn },
                lastDate = list.maxOfOrNull { it.issuedOn },
                net = list.groupBy { it.unit.dimension }.mapValues { (_, l) -> l.sumOf { it.netQty } },
            )
        }.sortedWith(compareBy({ nameKey(it.name) }, { it.districtId }))
        val perReagent = byDistrict.flatMap { (id, list) ->
            totalsByReagent(list).map { DistrictReagentTotal(id, list.first().districtName.orEmpty(), it) }
        }.sortedWith(compareBy({ nameKey(it.districtName) }, { nameKey(it.total.name) }, { it.total.dimension.ordinal }))
        return DistrictSummary(districts, perReagent, items)
    }

    private fun totalsByReagent(items: List<IssueItemRow>): List<ReagentQtyTotal> =
        items.groupBy { it.reagentId to it.unit.dimension }.map { (key, list) ->
            ReagentQtyTotal(
                reagentId = key.first,
                name = list.first().name,
                dimension = key.second,
                unit = MeasureUnit.largest(list.map { it.unit })!!,
                issued = list.sumOf { it.qty },
                returned = list.sumOf { it.returnedQty },
                items = list.size,
                firstDate = list.minOfOrNull { it.issuedOn },
                lastDate = list.maxOfOrNull { it.issuedOn },
            )
        }.sortedWith(compareBy({ nameKey(it.name) }, { it.dimension.ordinal }))

    // ---------------------------------------------------------------- bosh sahifa va eslatmalar

    fun dashboard(today: LocalDate): Dashboard {
        val rows = batchRows(BatchFilter(), today)
        val withStock = rows.filter { it.remainder > 0 }
        val items = issueItems(OpFilter())
        val departments = db.read { conn ->
            conn.query("SELECT id, name, active, sort_order FROM departments ORDER BY sort_order, name_key") { rs ->
                Place(rs.long("id"), rs.str("name"), rs.bool("active"), rs.getInt("sort_order"))
            }
        }
        val byDepartment = departments.mapNotNull { dept ->
            val owned = withStock.filter { it.departmentId == dept.id }
            val issued = items.filter { it.recipientType == RecipientType.DEPARTMENT && it.departmentId == dept.id }
            if (!dept.active && owned.isEmpty() && issued.isEmpty()) return@mapNotNull null
            DepartmentStat(
                departmentId = dept.id,
                name = dept.name,
                ownedBatches = owned.size,
                remainder = owned.groupBy { it.unit.dimension }.mapValues { (_, l) -> l.sumOf { it.remainder } },
                issuedTo = issued.groupBy { it.unit.dimension }.mapValues { (_, l) -> l.sumOf { it.netQty } },
                issuedItems = issued.size,
            )
        }
        val byDistrict = items.filter { it.recipientType == RecipientType.DISTRICT }
            .groupBy { it.districtId!! }
            .map { (id, list) ->
                DistrictStat(
                    districtId = id,
                    name = list.first().districtName.orEmpty(),
                    items = list.size,
                    issued = list.groupBy { it.unit.dimension }.mapValues { (_, l) -> l.sumOf { it.netQty } },
                )
            }
            .sortedWith(compareBy({ nameKey(it.name) }, { it.districtId }))
        return Dashboard(
            reagentTypes = rows.map { it.reagentId }.distinct().size,
            activeBatches = withStock.size,
            expiringBatches = withStock.count { it.state == ExpiryState.RED || it.state == ExpiryState.YELLOW },
            expiredBatches = withStock.count { it.state == ExpiryState.EXPIRED },
            belowMinReagents = rows.filter { it.belowMin }.map { it.reagentId to it.unit.dimension }.distinct().size,
            byDepartment = byDepartment,
            byDistrict = byDistrict,
            stateCounts = ExpiryState.entries.associateWith { state -> withStock.count { it.state == state } },
            recentReceipts = receipts(OpFilter()).sortedByDescending { it.id }.take(10),
            recentIssues = items.sortedByDescending { it.id }.take(10),
        )
    }

    /** Eslatmalar: muddati o‘tgan, qizil, minimal qoldiqdan kam, sariq (qoldig‘i bor partiyalar bo‘yicha). */
    fun notifications(today: LocalDate): List<Notification> {
        val rows = batchRows(BatchFilter(includeEmpty = false), today)
        val result = mutableListOf<Notification>()
        for (row in rows.sortedWith(compareBy({ it.daysLeft ?: Long.MAX_VALUE }, { nameKey(it.name) }))) {
            val label = "«${row.name}»" + if (row.series.isNotEmpty()) " (${row.series})" else ""
            val left = Qty.formatWithUnit(row.remainder, row.unit)
            val days = row.daysLeft
            when (row.state) {
                ExpiryState.EXPIRED -> result += Notification(
                    NotificationKind.EXPIRED,
                    "$label: muddati ${-days!!} kun oldin o‘tgan (${row.expiresOn!!.display()}), qoldiq $left",
                    NotificationTarget.EXPIRY, row.reagentId, row.batchId, days,
                )
                ExpiryState.RED -> result += Notification(
                    NotificationKind.RED,
                    "$label: " + (if (days == 0L) "muddati bugun tugaydi" else "muddati tugashiga $days kun qoldi") +
                        " (${row.expiresOn!!.display()}), qoldiq $left",
                    NotificationTarget.EXPIRY, row.reagentId, row.batchId, days,
                )
                ExpiryState.YELLOW -> result += Notification(
                    NotificationKind.YELLOW,
                    "$label: muddati tugashiga $days kun qoldi (${row.expiresOn!!.display()}), qoldiq $left",
                    NotificationTarget.EXPIRY, row.reagentId, row.batchId, days,
                )
                else -> Unit
            }
        }
        // Minimal qoldiq — reaktiv + o‘lchov turi bo‘yicha bitta eslatma (qoldiq 0 bo‘lsa ham).
        batchRows(BatchFilter(), today).filter { it.belowMin }.distinctBy { it.reagentId to it.unit.dimension }.forEach { row ->
            val min = db.read { conn ->
                conn.query("SELECT min_stock, min_stock_unit FROM reagents WHERE id = ?", row.reagentId) { rs ->
                    rs.long("min_stock") to rs.unit("min_stock_unit")
                }.first()
            }
            result += Notification(
                NotificationKind.BELOW_MIN,
                "«${row.name}»: umumiy qoldiq ${Qty.formatWithUnit(row.groupTotal, row.groupUnit)} — " +
                    "minimal ${Qty.formatWithUnit(min.first, min.second)} dan kam",
                NotificationTarget.STOCK, row.reagentId, null, null,
            )
        }
        return result.sortedBy { it.kind.ordinal }
    }

    /** Qo‘ng‘iroqcha soni: muddati o‘tgan + qizil + minimal qoldiqdan kam. */
    fun notificationCount(today: LocalDate): Int = notifications(today).count { it.counted }

    // ---------------------------------------------------------------- jurnallar

    /** Partiya harakatlari, har biridan keyingi qoldiq bilan (sana, so‘ng yozilish tartibida). */
    fun ledger(batchId: Long): List<LedgerRow> = db.read { conn -> loadLedger(conn, "m.batch_id = ?", batchId) }

    /** Reaktivning barcha partiyalari harakatlari; qoldiq har bir partiya bo‘yicha alohida. */
    fun reagentLedger(reagentId: Long): List<LedgerRow> = db.read { conn -> loadLedger(conn, "b.reagent_id = ?", reagentId) }

    /** Barcha partiyalar harakatlari (davr hisobotlari uchun); qoldiq har bir partiya bo‘yicha alohida. */
    fun fullLedger(): List<LedgerRow> = db.read { conn -> loadLedger(conn, "1 = ?", 1L) }

    private fun loadLedger(conn: Connection, where: String, id: Long): List<LedgerRow> {
        class Raw(val row: LedgerRow)
        val raws = conn.query(
            """
            SELECT m.*, b.reagent_id, b.series, b.unit, r.name,
                   x.is_initial, x.doc_no AS receipt_doc, x.cancelled_at AS receipt_cancelled, x.received_by,
                   i.recipient_type, i.cancelled_at AS item_cancelled, h.doc_no AS issue_doc,
                   d.name AS dept_name, t.name AS district_name, i.note AS item_note,
                   ret.cancelled_at AS return_cancelled, ret.reason AS return_reason,
                   o.kind AS reversed_kind
            FROM stock_movements m
            JOIN batches b ON b.id = m.batch_id
            JOIN reagents r ON r.id = b.reagent_id
            LEFT JOIN receipts x ON x.id = m.receipt_id
            LEFT JOIN issue_items i ON i.id = m.issue_item_id
            LEFT JOIN issues h ON h.id = i.issue_id
            LEFT JOIN departments d ON d.id = i.department_id
            LEFT JOIN districts t ON t.id = i.district_id
            LEFT JOIN returns ret ON ret.id = m.return_id
            LEFT JOIN stock_movements o ON o.id = m.reverses_id
            WHERE $where
            ORDER BY m.moved_on, m.id
            """,
            id,
        ) { rs ->
            val kind = MovementKind.valueOf(rs.str("kind"))
            val recipient = rs.strOrNull("recipient_type")?.let(RecipientType::valueOf)
            val recipientText = when (recipient) {
                RecipientType.DEPARTMENT -> "${rs.str("dept_name")} bo‘limi"
                RecipientType.DISTRICT -> "${rs.str("district_name")} tumani"
                RecipientType.WRITE_OFF -> "hisobdan chiqarish"
                null -> ""
            }
            val reversedKind = rs.strOrNull("reversed_kind")?.let(MovementKind::valueOf)
            val note = rs.str("note")
            val description = when (kind) {
                MovementKind.RECEIPT -> if (rs.bool("is_initial")) "Kirim (boshlang‘ich)" else "Kirim"
                MovementKind.ISSUE -> when (recipient) {
                    RecipientType.WRITE_OFF -> "Hisobdan chiqarildi" + rs.str("item_note").let { if (it.isNotEmpty()) ": $it" else "" }
                    else -> "Berildi: $recipientText"
                }
                MovementKind.RETURN -> "Qaytarildi: $recipientText"
                MovementKind.ADJUSTMENT -> note.ifEmpty { MovementKind.ADJUSTMENT.label }
                MovementKind.REVERSAL -> "Bekor qilindi (" + (reversedKind?.label?.lowercase() ?: "") +
                    (if (recipientText.isNotEmpty()) ", $recipientText" else "") + "): $note"
            }
            val cancelled = when (kind) {
                MovementKind.RECEIPT -> rs.getString("receipt_cancelled") != null
                MovementKind.ISSUE -> rs.getString("item_cancelled") != null
                MovementKind.RETURN -> rs.getString("return_cancelled") != null
                else -> false
            }
            Raw(
                LedgerRow(
                    movementId = rs.long("id"),
                    batchId = rs.long("batch_id"),
                    reagentId = rs.long("reagent_id"),
                    name = rs.str("name"),
                    series = rs.str("series"),
                    unit = rs.unit("unit"),
                    movedOn = rs.date("moved_on")!!,
                    kind = kind,
                    qty = rs.long("qty"),
                    balance = 0,
                    description = description,
                    docNo = rs.strOrNull("receipt_doc")?.takeIf { it.isNotEmpty() } ?: rs.str("issue_doc"),
                    receiptId = rs.longOrNull("receipt_id"),
                    issueItemId = rs.longOrNull("issue_item_id"),
                    returnId = rs.longOrNull("return_id"),
                    reversesId = rs.longOrNull("reverses_id"),
                    note = note,
                    createdAt = rs.dateTime("created_at")!!,
                    operationCancelled = cancelled,
                ),
            )
        }
        // Bekor qilingan harakat va uning teskarisi qoldiqni o‘zgartirmaydi (ikkalasi ro‘yxatda qoladi). Teskari yozuv asl sana
        // bilan, lekin keyinroq yoziladi: ularni qo‘shib borilsa, oraliqda manfiy qoldiq ko‘rinib qolardi (masalan, berish
        // oldingi sanaga tuzatilganda). Juftlik bir sanada bo‘lgani uchun har bir kun oxiridagi qoldiq o‘zgarmaydi.
        val reversed = raws.mapNotNullTo(HashSet()) { it.row.reversesId }
        val balances = HashMap<Long, Long>()
        return raws.map { raw ->
            val counted = raw.row.reversesId == null && raw.row.movementId !in reversed
            val balance = (balances[raw.row.batchId] ?: 0L) + if (counted) raw.row.qty else 0L
            balances[raw.row.batchId] = balance
            raw.row.copy(balance = balance)
        }
    }

    fun auditLogs(filter: AuditFilter = AuditFilter(), limit: Int = 1000): List<AuditEntry> = audit.list(filter, limit)
}

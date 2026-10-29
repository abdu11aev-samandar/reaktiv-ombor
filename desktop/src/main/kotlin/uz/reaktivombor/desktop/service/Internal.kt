package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.charCount
import uz.reaktivombor.desktop.core.display
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
import java.sql.Connection
import java.sql.SQLException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// Xizmatlar uchun umumiy yordamchilar: sana va matn tekshiruvi, partiya ma’lumoti, qoldiq hisobi.

internal fun Clock.today(): LocalDate = LocalDate.now(this)

internal fun Clock.now(): LocalDateTime = LocalDateTime.now(this).truncatedTo(ChronoUnit.SECONDS)

/** Sanalar oralig‘i (texnik topshiriq): 01.01.1990 — 31.12.2199. */
internal val MIN_DATE: LocalDate = LocalDate.of(1990, 1, 1)
internal val MAX_DATE: LocalDate = LocalDate.of(2199, 12, 31)

/** Sana tekshiruvi: majburiy bo‘lsa — kiritilgan, oraliqda, [allowFuture] bo‘lmasa — bugundan keyin emas. */
internal fun checkDate(
    date: LocalDate?,
    label: String,
    field: String?,
    today: LocalDate,
    required: Boolean = true,
    allowFuture: Boolean = false,
): LocalDate? {
    if (date == null) {
        if (required) throw UserError("$label kiritilishi shart.", field)
        return null
    }
    if (date < MIN_DATE || date > MAX_DATE) {
        throw UserError("$label ${MIN_DATE.display()} – ${MAX_DATE.display()} oralig‘ida bo‘lishi kerak.", field)
    }
    if (!allowFuture && date > today) {
        throw UserError("$label (${date.display()}) bugundan keyin bo‘lishi mumkin emas.", field)
    }
    return date
}

/** Qisqa matn (nom, seriya, hujjat raqami …): chetidagi va ortiqcha ichki bo‘shliqlarsiz, [max] belgigacha. */
internal fun shortText(raw: String?, label: String, max: Int, field: String?): String {
    val text = cleanName(raw ?: "")
    if (text.charCount() > max) throw UserError("$label $max belgidan oshmasligi kerak.", field)
    return text
}

/** Uzun matn (izoh): faqat chetidagi bo‘shliqlar olib tashlanadi, qatorlar saqlanadi. */
internal fun longText(raw: String?, label: String, max: Int, field: String?): String {
    val text = (raw ?: "").trim()
    if (text.charCount() > max) throw UserError("$label $max belgidan oshmasligi kerak.", field)
    return text
}

/** Forma xatolarini yig‘adi: hammasi bir vaqtda ko‘rsatiladi (har biri o‘z maydoni yonida). */
internal class FieldErrors {
    val map = linkedMapOf<String, String>()

    fun add(field: String, message: String) {
        map.putIfAbsent(field, message)
    }

    /** [block] UserError tashlasa — xato [field] ga yoziladi va null qaytadi. */
    inline fun <T> capture(field: String, block: () -> T): T? = try {
        block()
    } catch (e: UserError) {
        add(field, e.message)
        null
    }

    fun throwIfAny() {
        if (map.isNotEmpty()) throw UserError(map.values.joinToString("\n"), map.toMap())
    }
}

/** Operatsiya uchun partiya ma’lumoti. */
internal data class BatchInfo(
    val id: Long,
    val reagentId: Long,
    val name: String,
    val series: String,
    val unit: MeasureUnit,
    val receivedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val archived: Boolean,
    val reagentArchived: Boolean,
) {
    /** Xabarlar uchun: «Etil spirti» (seriya ES-001). */
    val label: String get() = "«$name»" + if (series.isNotEmpty()) " (seriya $series)" else ""
}

internal fun loadBatchInfo(conn: Connection, batchId: Long): BatchInfo? = conn.queryOne(
    """
    SELECT b.id, b.reagent_id, r.name, b.series, b.unit, b.received_on, b.expires_on, b.archived, r.archived AS r_archived
    FROM batches b JOIN reagents r ON r.id = b.reagent_id WHERE b.id = ?
    """,
    batchId,
) { rs ->
    BatchInfo(
        id = rs.long("id"),
        reagentId = rs.long("reagent_id"),
        name = rs.str("name"),
        series = rs.str("series"),
        unit = rs.unit("unit"),
        receivedOn = rs.date("received_on"),
        expiresOn = rs.date("expires_on"),
        archived = rs.bool("archived"),
        reagentArchived = rs.bool("r_archived"),
    )
}

/** Partiya qoldig‘i — faqat harakatlar yig‘indisi. */
internal fun remainderOf(conn: Connection, batchId: Long): Long =
    conn.queryLong("SELECT COALESCE(SUM(qty), 0) FROM stock_movements WHERE batch_id = ?", batchId)

/**
 * [date] kuni va undan keyingi har bir kunda qoldiq qancha bo‘lgan — eng kichigi. Shuncha miqdorni [date] sanasi bilan
 * chiqarish mumkin: o‘tgan sana bilan yozilgan berish keyingi kunlardagi qoldiqni ham manfiy qilib qo‘ymaydi.
 * Bugungi sana bilan bu oddiy qoldiqqa teng.
 */
internal fun availableFrom(conn: Connection, batchId: Long, date: LocalDate): Long {
    val days = conn.query(
        "SELECT moved_on, SUM(qty) AS total FROM stock_movements WHERE batch_id = ? GROUP BY moved_on ORDER BY moved_on",
        batchId,
    ) { rs -> LocalDate.parse(rs.getString("moved_on")) to rs.getLong("total") }
    var balance = 0L
    var atDate: Long? = null
    var minAfter = Long.MAX_VALUE
    for ((day, total) in days) {
        if (day > date && atDate == null) atDate = balance
        balance += total
        if (day > date) minAfter = minOf(minAfter, balance)
    }
    return minOf(atDate ?: balance, minAfter)
}

internal fun insertMovement(
    conn: Connection,
    batchId: Long,
    kind: String,
    qty: Long,
    movedOn: LocalDate,
    createdAt: LocalDateTime,
    receiptId: Long? = null,
    issueItemId: Long? = null,
    returnId: Long? = null,
    reversesId: Long? = null,
    note: String = "",
): Long = conn.insert(
    """
    INSERT INTO stock_movements(batch_id, kind, qty, moved_on, receipt_id, issue_item_id, return_id, reverses_id, note, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """,
    batchId, kind, qty, movedOn, receiptId, issueItemId, returnId, reversesId, note, createdAt,
)

/** Asl harakatning teskarisi (bekor qilish): o‘sha sana bilan — davr hisobotlari to‘g‘ri qoladi. */
internal fun reverseMovement(conn: Connection, movementId: Long, reason: String, createdAt: LocalDateTime): Long {
    data class Original(
        val batchId: Long,
        val qty: Long,
        val movedOn: LocalDate,
        val receiptId: Long?,
        val issueItemId: Long?,
        val returnId: Long?,
    )
    val original = conn.queryOne(
        "SELECT batch_id, qty, moved_on, receipt_id, issue_item_id, return_id FROM stock_movements WHERE id = ?",
        movementId,
    ) { rs ->
        Original(
            rs.long("batch_id"), rs.long("qty"), LocalDate.parse(rs.getString("moved_on")),
            rs.longOrNull("receipt_id"), rs.longOrNull("issue_item_id"), rs.longOrNull("return_id"),
        )
    } ?: throw IllegalStateException("Harakat topilmadi: $movementId")
    return insertMovement(
        conn, original.batchId, "REVERSAL", -original.qty, original.movedOn, createdAt,
        receiptId = original.receiptId, issueItemId = original.issueItemId, returnId = original.returnId,
        reversesId = movementId, note = reason,
    )
}

/** Bazaning o‘z himoyasi ishlab ketsa (qoldiq manfiy) — tushunarli xabar. Bu holatga xizmatlar tekshiruvi yo‘l qo‘ymaydi. */
internal inline fun <T> stockGuard(block: () -> T): T = try {
    block()
} catch (e: SQLException) {
    if (e.message?.contains("NEGATIVE_STOCK") == true) {
        throw UserError("Qoldiq yetarli emas — operatsiya saqlanmadi.")
    }
    throw e
}

internal fun reasonText(raw: String?, label: String = "Sabab"): String {
    val text = longText(raw, label, 500, "reason")
    if (text.isEmpty()) throw UserError("$label kiritilishi shart.", "reason")
    return text
}

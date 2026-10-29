package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.matchesQuery
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.dateTime
import uz.reaktivombor.desktop.db.long
import uz.reaktivombor.desktop.db.longOrNull
import uz.reaktivombor.desktop.db.query
import uz.reaktivombor.desktop.db.queryOne
import uz.reaktivombor.desktop.db.str
import uz.reaktivombor.desktop.db.insert
import java.sql.Connection
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** Audit jurnalidagi amal turlari (o‘zbekcha nomi bilan). */
enum class AuditAction(val label: String) {
    REAGENT_CREATED("Reaktiv qo‘shildi"),
    REAGENT_UPDATED("Reaktiv tahrirlandi"),
    BATCH_CREATED("Partiya qo‘shildi"),
    BATCH_UPDATED("Partiya tahrirlandi"),
    RECEIPT("Kirim"),
    ISSUE_DEPARTMENT("Bo‘limga berildi"),
    ISSUE_DISTRICT("Tumanga berildi"),
    WRITE_OFF("Hisobdan chiqarildi"),
    RETURN("Qaytarildi"),
    CANCELLED("Bekor qilindi"),
    CORRECTED("Tuzatildi"),
    ARCHIVED("Arxivlandi"),
    UNARCHIVED("Arxivdan chiqarildi"),
    DELETED("O‘chirildi"),
    DEPARTMENT_CHANGED("Bo‘lim o‘zgartirildi"),
    DISTRICT_CHANGED("Tuman o‘zgartirildi"),
    BACKUP_CREATED("Zaxira nusxa olindi"),
    BACKUP_RESTORED("Zaxiradan tiklandi"),
    LEGACY_IMPORTED("Eski ma’lumotlar ko‘chirildi"),
    EXCEL_IMPORTED("Exceldan import qilindi"),
    SETTINGS_CHANGED("Sozlamalar o‘zgartirildi"),
}

data class AuditEntry(
    val id: Long,
    val at: LocalDateTime,
    val action: AuditAction?,
    /** Bazadagi amal nomi (yangiroq versiyada qo‘shilgan, bu versiya bilmaydigan amal bo‘lsa ham ko‘rinadi). */
    val actionCode: String,
    val entity: String,
    val entityId: Long?,
    val reagentId: Long?,
    val batchId: Long?,
    val qtyText: String,
    val details: String,
    val actor: String,
) {
    val actionLabel: String get() = action?.label ?: actionCode
}

data class AuditFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val action: AuditAction? = null,
    val text: String? = null,
    val reagentId: Long? = null,
    val batchId: Long? = null,
)

/** Audit jurnali: har bir yozish amali o‘sha tranzaksiya ichida qayd etiladi. O‘chirib/o‘zgartirib bo‘lmaydi (trigger). */
class Audit(private val db: Database, private val clock: Clock) {

    /** Faqat ochiq yozish tranzaksiyasi ichida ([Database.write]) chaqiriladi. */
    fun log(
        conn: Connection,
        action: AuditAction,
        entity: String,
        entityId: Long?,
        details: String,
        reagentId: Long? = null,
        batchId: Long? = null,
        qtyText: String = "",
    ) {
        val actor = conn.queryOne("SELECT value FROM app_settings WHERE key = 'actor_name'") { it.getString(1) } ?: ""
        conn.insert(
            """
            INSERT INTO audit_logs(at, action, entity, entity_id, reagent_id, batch_id, qty_text, details, actor)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            clock.now(), action.name, entity, entityId, reagentId, batchId, qtyText, details, actor,
        )
    }

    /** Yangisi birinchi. */
    fun list(filter: AuditFilter = AuditFilter(), limit: Int = 1000): List<AuditEntry> = db.read { conn ->
        val where = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        filter.from?.let { where += "at >= ?"; args += it.toString() }
        filter.to?.let { where += "at < ?"; args += it.plusDays(1).toString() }
        filter.action?.let { where += "action = ?"; args += it.name }
        filter.reagentId?.let { where += "reagent_id = ?"; args += it }
        filter.batchId?.let { where += "batch_id = ?"; args += it }
        val sql = "SELECT * FROM audit_logs" + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY at DESC, id DESC"
        conn.query(sql, *args.toTypedArray()) { rs ->
            val code = rs.str("action")
            AuditEntry(
                id = rs.long("id"),
                at = rs.dateTime("at")!!,
                action = AuditAction.entries.firstOrNull { it.name == code },
                actionCode = code,
                entity = rs.str("entity"),
                entityId = rs.longOrNull("entity_id"),
                reagentId = rs.longOrNull("reagent_id"),
                batchId = rs.longOrNull("batch_id"),
                qtyText = rs.str("qty_text"),
                details = rs.str("details"),
                actor = rs.str("actor"),
            )
        }
            .asSequence()
            .filter { matchesQuery(filter.text, it.details, it.qtyText, it.actor, it.actionLabel) }
            .take(limit.coerceAtLeast(0))
            .toList()
    }
}

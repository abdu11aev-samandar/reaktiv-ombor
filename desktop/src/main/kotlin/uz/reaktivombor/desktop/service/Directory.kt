package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.charCount
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.bool
import uz.reaktivombor.desktop.db.insert
import uz.reaktivombor.desktop.db.int
import uz.reaktivombor.desktop.db.long
import uz.reaktivombor.desktop.db.query
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.db.queryOne
import uz.reaktivombor.desktop.db.str
import uz.reaktivombor.desktop.db.update
import java.sql.Connection
import java.time.Clock

/** Bo‘lim yoki tuman. */
data class Place(val id: Long, val name: String, val active: Boolean, val sortOrder: Int)

typealias Department = Place
typealias District = Place

enum class PlaceKind(internal val table: String, val title: String, internal val auditAction: AuditAction) {
    DEPARTMENT("departments", "Bo‘lim", AuditAction.DEPARTMENT_CHANGED),
    DISTRICT("districts", "Tuman", AuditAction.DISTRICT_CHANGED),
}

/**
 * Bo‘limlar va tumanlar ro‘yxati. Nom kaliti (katta-kichik harf, tutuq belgisi, bo‘shliqlar farqsiz) bo‘yicha takrorlanmaydi.
 * O‘chirilmaydi — faqat nofaol qilinadi (eski operatsiyalar unga bog‘langan).
 */
class Directory(private val db: Database, private val audit: Audit, private val clock: Clock) {

    companion object {
        const val NAME_MAX = 60
    }

    fun departments(includeInactive: Boolean = false): List<Place> = list(PlaceKind.DEPARTMENT, includeInactive)

    fun districts(includeInactive: Boolean = false): List<Place> = list(PlaceKind.DISTRICT, includeInactive)

    fun list(kind: PlaceKind, includeInactive: Boolean = false): List<Place> = db.read { conn ->
        conn.query(
            "SELECT * FROM ${kind.table}" + (if (includeInactive) "" else " WHERE active = 1") + " ORDER BY sort_order, name_key",
        ) { rs -> rs.toPlace() }
    }

    fun get(kind: PlaceKind, id: Long): Place? = db.read { conn -> find(conn, kind, id) }

    /** Bor nom (kaliti bo‘yicha) — o‘sha yozuv qaytadi (nofaol bo‘lsa faollashtiriladi). */
    fun addDepartment(name: String): Place = add(PlaceKind.DEPARTMENT, name)

    fun addDistrict(name: String): Place = add(PlaceKind.DISTRICT, name)

    fun add(kind: PlaceKind, name: String): Place = db.write { conn -> findOrCreate(conn, kind, name, reactivate = true) }

    /** Berish formasi: yozilgan tuman nomi — bori topiladi, yo‘q bo‘lsa yaratiladi. */
    fun resolveDistrict(name: String): Place = add(PlaceKind.DISTRICT, name)

    /** Boshqa yozuvda shu nom bo‘lsa — rad etiladi. */
    fun rename(kind: PlaceKind, id: Long, name: String): Place = db.write { conn ->
        val place = find(conn, kind, id) ?: throw UserError("${kind.title} topilmadi.")
        val clean = validName(kind, name)
        val key = nameKey(clean)
        val other = conn.queryOne("SELECT * FROM ${kind.table} WHERE name_key = ? AND id <> ?", key, id) { it.toPlace() }
        if (other != null) throw UserError("«${other.name}» nomli ${kind.title.lowercase()} allaqachon bor.", "name")
        if (clean != place.name) {
            conn.update("UPDATE ${kind.table} SET name = ?, name_key = ?, updated_at = ? WHERE id = ?", clean, key, clock.now(), id)
            audit.log(conn, kind.auditAction, kind.table, id, "Nomi o‘zgartirildi: «${place.name}» → «$clean»")
        }
        find(conn, kind, id)!!
    }

    fun setActive(kind: PlaceKind, id: Long, active: Boolean) {
        db.write { conn ->
            val place = find(conn, kind, id) ?: throw UserError("${kind.title} topilmadi.")
            if (place.active != active) {
                conn.update("UPDATE ${kind.table} SET active = ?, updated_at = ? WHERE id = ?", active, clock.now(), id)
                audit.log(conn, kind.auditAction, kind.table, id, "«${place.name}» " + if (active) "faollashtirildi" else "nofaol qilindi")
            }
        }
    }

    internal fun find(conn: Connection, kind: PlaceKind, id: Long): Place? =
        conn.queryOne("SELECT * FROM ${kind.table} WHERE id = ?", id) { it.toPlace() }

    /** Ochiq tranzaksiya ichida: nom bo‘yicha topadi yoki yaratadi. */
    internal fun findOrCreate(conn: Connection, kind: PlaceKind, name: String, reactivate: Boolean): Place {
        val clean = validName(kind, name)
        val key = nameKey(clean)
        val existing = conn.queryOne("SELECT * FROM ${kind.table} WHERE name_key = ?", key) { it.toPlace() }
        if (existing != null) {
            if (reactivate && !existing.active) {
                conn.update("UPDATE ${kind.table} SET active = 1, updated_at = ? WHERE id = ?", clock.now(), existing.id)
                audit.log(conn, kind.auditAction, kind.table, existing.id, "«${existing.name}» faollashtirildi")
                return existing.copy(active = true)
            }
            return existing
        }
        val order = conn.queryLong("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM ${kind.table}").toInt()
        val now = clock.now()
        val id = conn.insert(
            "INSERT INTO ${kind.table}(name, name_key, active, sort_order, created_at, updated_at) VALUES (?, ?, 1, ?, ?, ?)",
            clean, key, order, now, now,
        )
        audit.log(conn, kind.auditAction, kind.table, id, "Qo‘shildi: «$clean»")
        return Place(id, clean, true, order)
    }

    private fun validName(kind: PlaceKind, raw: String): String {
        val clean = cleanName(raw)
        if (clean.isEmpty()) throw UserError("${kind.title} nomi kiritilishi shart.", "name")
        if (clean.none { it.isLetter() } || clean.charCount() > NAME_MAX) {
            throw UserError("${kind.title} nomi harf bilan yozilishi kerak ($NAME_MAX belgigacha).", "name")
        }
        return clean
    }

    private fun java.sql.ResultSet.toPlace() = Place(long("id"), str("name"), bool("active"), int("sort_order"))
}

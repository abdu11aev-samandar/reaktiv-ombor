package uz.reaktivombor.desktop.db

import org.sqlite.SQLiteConfig
import uz.reaktivombor.desktop.core.MeasureUnit
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bitta SQLite fayl, butun dastur uchun bitta ulanish. Hamma murojaat [lock] orqali navbat bilan:
 * bir vaqtda ikkita oqim yozsa ham qoldiq hisobi aralashib ketmaydi.
 *
 * Ochilganda sxema oxirgi versiyaga keltiriladi ([Schema.migrate]).
 */
class Database(path: Path) : AutoCloseable {
    @Volatile
    var path: Path = path.toAbsolutePath()
        private set

    private val lock = ReentrantLock()
    private var connection: Connection? = null

    /** Ochiq tranzaksiyalar chuqurligi (0 — tranzaksiya yo‘q). Faqat [lock] ostida o‘zgaradi. */
    private var depth = 0

    init {
        open(this.path)
    }

    val isOpen: Boolean get() = lock.withLock { connection != null }

    /** O‘qish: navbat bilan, tranzaksiyasiz. */
    fun <T> read(block: (Connection) -> T): T = lock.withLock { block(current()) }

    /**
     * Yozish: BEGIN IMMEDIATE … COMMIT; har qanday xatoda ROLLBACK — yarim saqlangan operatsiya qolmaydi.
     * Ichma-ich chaqirilsa tashqi tranzaksiyaga qo‘shiladi (SAVEPOINT): ichkisi xato bersa faqat uning o‘zgarishlari qaytadi.
     */
    fun <T> write(block: (Connection) -> T): T = lock.withLock {
        val conn = current()
        if (depth == 0) {
            conn.exec("BEGIN IMMEDIATE")
            depth = 1
            try {
                val result = block(conn)
                conn.exec("COMMIT")
                result
            } catch (t: Throwable) {
                runCatching { conn.exec("ROLLBACK") }
                throw t
            } finally {
                depth = 0
            }
        } else {
            val name = "sp$depth"
            conn.exec("SAVEPOINT $name")
            depth++
            try {
                val result = block(conn)
                conn.exec("RELEASE $name")
                result
            } catch (t: Throwable) {
                runCatching {
                    conn.exec("ROLLBACK TO $name")
                    conn.exec("RELEASE $name")
                }
                throw t
            } finally {
                depth--
            }
        }
    }

    /** Ulanishni yopib, [newPath] dagi faylni ochadi (zaxiradan tiklash). Xizmatlar shu [Database] obyektini ishlataveradi. */
    fun reopen(newPath: Path = path) {
        lock.withLock {
            check(depth == 0) { "Tranzaksiya ichida qayta ochib bo‘lmaydi" }
            closeConnection()
            open(newPath.toAbsolutePath())
        }
    }

    /**
     * Fayl ustida ish (zaxiradan tiklash): ulanish yopiladi, [block] baza faylining yo‘li bilan bajariladi,
     * so‘ng fayl qayta ochiladi (ochilmasa — xato tashlanadi, ulanish yopiq qoladi).
     */
    fun <T> offline(block: (Path) -> T): T = lock.withLock {
        check(depth == 0) { "Tranzaksiya ichida faylni almashtirib bo‘lmaydi" }
        closeConnection()
        val result = try {
            block(path)
        } catch (t: Throwable) {
            runCatching { open(path) }
            throw t
        }
        open(path)
        result
    }

    override fun close() {
        lock.withLock { closeConnection() }
    }

    private fun current(): Connection = connection ?: throw IllegalStateException("Ma’lumotlar bazasi yopiq")

    private fun open(target: Path) {
        target.parent?.let { Files.createDirectories(it) }
        val conn = connect(target)
        try {
            Schema.migrate(conn)
        } catch (t: Throwable) {
            runCatching { conn.close() }
            throw t
        }
        connection = conn
        path = target
    }

    private fun closeConnection() {
        val conn = connection ?: return
        connection = null
        runCatching { conn.close() }
    }

    companion object {
        /** PRAGMA: tashqi kalitlar yoqiq, jurnal DELETE (bitta fayl), to‘liq sinxron yozish, band bo‘lsa 5 s kutish. */
        fun connect(target: Path, readOnly: Boolean = false): Connection {
            val config = SQLiteConfig().apply {
                enforceForeignKeys(true)
                setBusyTimeout(5000)
                if (readOnly) {
                    setReadOnly(true)
                } else {
                    setJournalMode(SQLiteConfig.JournalMode.DELETE)
                    setSynchronous(SQLiteConfig.SynchronousMode.FULL)
                }
            }
            return config.createConnection("jdbc:sqlite:" + target.toAbsolutePath())
        }
    }
}

// ---- Yordamchilar: tayyorlangan so‘rovlar, qiymatlarni bog‘lash va o‘qish ----

private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

/** Bazadagi vaqt matni: 2026-10-29T09:15:00 (mahalliy vaqt, soniyagacha). */
fun LocalDateTime.toDbText(): String = truncatedTo(ChronoUnit.SECONDS).format(TIMESTAMP)

fun parseDbTimestamp(text: String): LocalDateTime = LocalDateTime.parse(text, TIMESTAMP)

/** DDL va boshqaruv buyruqlari (parametrsiz). */
fun Connection.exec(sql: String) {
    createStatement().use { it.execute(sql) }
}

fun <T> Connection.query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { st ->
        st.bind(args)
        st.executeQuery().use { rs ->
            val result = ArrayList<T>()
            while (rs.next()) result += map(rs)
            result
        }
    }

fun <T> Connection.queryOne(sql: String, vararg args: Any?, map: (ResultSet) -> T): T? =
    prepareStatement(sql).use { st ->
        st.bind(args)
        st.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
    }

/** Bitta son (COUNT, SUM …); natija yo‘q yoki NULL bo‘lsa 0. */
fun Connection.queryLong(sql: String, vararg args: Any?): Long =
    queryOne(sql, *args) { rs -> rs.getLong(1) } ?: 0L

fun Connection.update(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { st ->
        st.bind(args)
        st.executeUpdate()
    }

/** INSERT va yangi qatorning id’si (last_insert_rowid()). */
fun Connection.insert(sql: String, vararg args: Any?): Long {
    prepareStatement(sql).use { st ->
        st.bind(args)
        st.executeUpdate()
    }
    return createStatement().use { st -> st.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getLong(1) } }
}

/** String / Long / Int / Boolean (0/1) / LocalDate (YYYY-MM-DD) / LocalDateTime / MeasureUnit (kodi) / enum (nomi) / null. */
fun PreparedStatement.bind(args: Array<out Any?>) {
    args.forEachIndexed { i, value ->
        val index = i + 1
        when (value) {
            null -> setNull(index, Types.NULL)
            is String -> setString(index, value)
            is Long -> setLong(index, value)
            is Int -> setInt(index, value)
            is Boolean -> setInt(index, if (value) 1 else 0)
            is LocalDate -> setString(index, value.toString())
            is LocalDateTime -> setString(index, value.toDbText())
            is MeasureUnit -> setString(index, value.code)
            is Enum<*> -> setString(index, value.name)
            else -> throw IllegalArgumentException("Qo‘llanmaydigan qiymat turi: ${value::class.java.name}")
        }
    }
}

fun ResultSet.str(column: String): String = getString(column) ?: ""

fun ResultSet.strOrNull(column: String): String? = getString(column)

fun ResultSet.long(column: String): Long = getLong(column)

fun ResultSet.longOrNull(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

fun ResultSet.int(column: String): Int = getInt(column)

fun ResultSet.bool(column: String): Boolean = getInt(column) != 0

fun ResultSet.date(column: String): LocalDate? = getString(column)?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)

fun ResultSet.dateTime(column: String): LocalDateTime? = getString(column)?.takeIf { it.isNotBlank() }?.let(::parseDbTimestamp)

fun ResultSet.unit(column: String): MeasureUnit {
    val code = getString(column)
    return MeasureUnit.entries.firstOrNull { it.code == code } ?: throw IllegalStateException("Noma’lum birlik bazada: $code")
}

fun ResultSet.unitOrNull(column: String): MeasureUnit? = getString(column)?.let { code -> MeasureUnit.entries.firstOrNull { it.code == code } }

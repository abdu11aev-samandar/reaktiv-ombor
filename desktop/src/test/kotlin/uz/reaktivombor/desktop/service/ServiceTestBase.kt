package uz.reaktivombor.desktop.service

import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.db.queryLong
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Testlar uchun soat: vaqt qo‘lda suriladi. */
class TestClock(@Volatile var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = TestClock(instant, zone)

    override fun instant(): Instant = instant

    fun advanceSeconds(seconds: Long) {
        instant = instant.plusSeconds(seconds)
    }
}

/** Har bir test — vaqtinchalik papkada yangi baza, «bugun» = 29.10.2026. */
abstract class ServiceTestBase {
    @get:Rule
    val tmp = TemporaryFolder()

    protected val today: LocalDate = LocalDate.of(2026, 10, 29)
    protected val clock = TestClock(Instant.parse("2026-10-29T09:00:00Z"))
    protected lateinit var dataDir: Path
    protected lateinit var db: Database
    protected lateinit var services: Services

    protected val L = MeasureUnit.L
    protected val ML = MeasureUnit.ML
    protected val KG = MeasureUnit.KG
    protected val G = MeasureUnit.G

    @Before
    fun openServices() {
        dataDir = tmp.newFolder("data").toPath()
        db = Database(DataDir.dbFile(dataDir))
        services = Services(db, dataDir, clock)
    }

    @After
    fun closeServices() {
        db.close()
    }

    protected fun litres(text: String): Long = Qty.parse(text, L)

    protected fun ml(text: String): Long = Qty.parse(text, ML)

    protected fun newBatch(
        name: String,
        series: String,
        expires: LocalDate,
        qty: String,
        unit: MeasureUnit = L,
        received: LocalDate = today.minusDays(10),
        departmentId: Long? = null,
        produced: LocalDate? = null,
        minStock: String? = null,
        minStockUnit: MeasureUnit? = null,
    ): Long = services.reagents.createBatch(
        NewBatch(
            name = name,
            series = series,
            producedOn = produced,
            receivedOn = received,
            initialQtyText = qty,
            unit = unit,
            expiresOn = expires,
            departmentId = departmentId,
            minStockText = minStock,
            minStockUnit = minStockUnit,
        ),
    )

    protected fun deptId(name: String): Long = services.directory.departments(includeInactive = true).first { it.name == name }.id

    protected fun toDepartment(batchId: Long, qty: String, unit: MeasureUnit, department: String, note: String? = null) =
        IssueLine(batchId, qty, unit, RecipientType.DEPARTMENT, departmentId = deptId(department), note = note)

    protected fun issueTo(
        department: String,
        batchId: Long,
        qty: String,
        unit: MeasureUnit,
        date: LocalDate = today,
        confirm: IssueConfirm = IssueConfirm.none,
    ): Long = services.stock.issue(IssueInput(date = date, lines = listOf(toDepartment(batchId, qty, unit, department))), confirm)

    protected fun count(table: String): Long = db.read { it.queryLong("SELECT COUNT(*) FROM $table") }

    /** Partiya qatori (arxivdagilar ham). */
    protected fun row(batchId: Long): BatchRow =
        services.queries.batchRows(BatchFilter(includeArchived = true), today).single { it.batchId == batchId }

    protected fun movementSum(batchId: Long): Long =
        db.read { it.queryLong("SELECT COALESCE(SUM(qty), 0) FROM stock_movements WHERE batch_id = ?", batchId) }
}

package uz.reaktivombor.desktop.ui

import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.db.Database
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.ReturnInput
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.service.TestClock
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/**
 * Sahifa testlari va skrinshotlar uchun haqiqiy baza (vaqtinchalik papkada) va namuna ma’lumot. «Bugun» = 29.10.2026.
 *
 * Partiyalar (qoldiq — bugungi holat):
 * - Etil spirti 96%: ES-001 (10 l → 6 l, yashil), ES-002 (5 l, yashil), ES-003 (3 l, sariq) — umumiy qoldiq 14 l;
 *   ES-001 dan Gistologiya (3 l, 0,5 l qaytarilgan), Biologiya (1 l) va Chilonzor tumaniga (0,5 l) berilgan.
 * - Xloroform: XL-117 (1000 ml → 320 ml, muddati o‘tgan, Sud-kimyoga 680 ml), XL-131 (1000 ml, yashil).
 * - Kumush nitrat: KN-09 (50 g → 12 g, qizil, minimal 20 g dan kam): Sud-kimyo 30 g, Yunusobod tumani 8 g.
 * - Natriy xlorid: NX-12 (2 kg → 1,75 kg; Morfologiyaga 250 g, grammda kiritilgan).
 * - Petri likopchasi: PL-3 (100 dona → 80 dona; Biologiyaga 20 dona).
 * - Formalin 10%: FR-201 (25 l → 18 l, sariq): Morfologiya 5 l, Chilonzor tumani 2 l.
 * - Eozin: EO-77 (250 ml, sariq) — faqat boshlang‘ich kirim (o‘chirish mumkin).
 * Qoldig‘i bor holatlar: yashil 5, sariq 3, qizil 1, muddati o‘tgan 1.
 */
object DemoData {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 29)

    fun clock(): TestClock = TestClock(Instant.parse("2026-10-29T09:00:00Z"))

    /** Yaratilgan yozuvlar raqamlari. */
    data class Ids(
        val etilReagent: Long,
        val es001: Long,
        val es002: Long,
        val es003: Long,
        val xloroformReagent: Long,
        val xl117: Long,
        val xl131: Long,
        val kn09: Long,
        val nx12: Long,
        val pl3: Long,
        val fr201: Long,
        val eo77: Long,
        val gistologiyaIssueItem: Long,
    )

    /** [dataDir] da yangi baza va xizmatlar (soat — 29.10.2026 09:00). Testdan keyin `services.db.close()`. */
    fun open(dataDir: Path): Services {
        Files.createDirectories(dataDir)
        return Services(Database(DataDir.dbFile(dataDir)), dataDir, clock())
    }

    /** [open] + [seed]. */
    fun seeded(dataDir: Path): Pair<Services, Ids> {
        val services = open(dataDir)
        return services to seed(services)
    }

    fun seed(s: Services): Ids {
        fun d(text: String) = LocalDate.parse(text)
        val dept = s.directory.departments().associate { it.name to it.id }
        fun batch(
            name: String,
            series: String,
            received: String,
            expires: String,
            qty: String,
            unit: MeasureUnit,
            department: String,
            storage: String,
            responsible: String,
            produced: String? = null,
            formula: String? = null,
            manufacturer: String? = null,
            minStock: String? = null,
            minUnit: MeasureUnit? = null,
        ): Long = s.reagents.createBatch(
            NewBatch(
                name = name, formula = formula, manufacturer = manufacturer, series = series,
                producedOn = produced?.let(::d), receivedOn = d(received), initialQtyText = qty, unit = unit,
                expiresOn = d(expires), departmentId = dept.getValue(department), storageLocation = storage,
                responsible = responsible, minStockText = minStock, minStockUnit = minUnit, receivedBy = "Karimova D.",
            ),
        )

        val es001 = batch("Etil spirti 96%", "ES-001", "2026-02-01", "2027-09-01", "10", MeasureUnit.L, "Gistologiya", "1-shkaf", "Karimova D.", "2026-01-10", "C₂H₅OH", "Biokimyo MChJ", "5", MeasureUnit.L)
        val es002 = batch("Etil spirti 96%", "ES-002", "2026-04-05", "2027-03-01", "5", MeasureUnit.L, "Biologiya", "Sovutgich", "Rahimov A.", "2026-03-01", manufacturer = "Biokimyo MChJ")
        val es003 = batch("Etil spirti 96%", "ES-003", "2026-09-01", "2026-12-15", "3", MeasureUnit.L, "Gistologiya", "1-shkaf", "Karimova D.", "2025-12-15")
        val xl117 = batch("Xloroform", "XL-117", "2025-04-10", "2026-10-01", "1000", MeasureUnit.ML, "Sud-kimyo", "Mo‘rili shkaf", "To‘xtayev B.", "2025-03-01", "CHCl₃")
        val xl131 = batch("Xloroform", "XL-131", "2026-06-01", "2028-05-12", "1000", MeasureUnit.ML, "Sud-kimyo", "Mo‘rili shkaf", "To‘xtayev B.", "2026-05-12")
        val kn09 = batch("Kumush nitrat", "KN-09", "2025-11-05", "2026-11-20", "50", MeasureUnit.G, "Sud-kimyo", "Seyf", "To‘xtayev B.", "2025-10-01", "AgNO₃", minStock = "20", minUnit = MeasureUnit.G)
        val nx12 = batch("Natriy xlorid", "NX-12", "2026-01-20", "2029-01-20", "2", MeasureUnit.KG, "Morfologiya", "Ombor", "Saidova N.", formula = "NaCl")
        val pl3 = batch("Petri likopchasi", "PL-3", "2026-03-01", "2030-03-01", "100", MeasureUnit.PIECE, "Biologiya", "2-javon", "Rahimov A.")
        val fr201 = batch("Formalin 10%", "FR-201", "2025-12-20", "2026-12-20", "25", MeasureUnit.L, "Morfologiya", "Ombor", "Saidova N.", "2025-12-01")
        val eo77 = batch("Eozin", "EO-77", "2026-02-05", "2027-01-10", "250", MeasureUnit.ML, "Gistologiya", "2-shkaf", "Karimova D.", "2026-01-20")

        fun toDept(batchId: Long, qty: String, unit: MeasureUnit, department: String, receiver: String? = null) =
            IssueLine(batchId, qty, unit, RecipientType.DEPARTMENT, departmentId = dept.getValue(department), receiver = receiver)

        fun toDistrict(batchId: Long, qty: String, unit: MeasureUnit, district: String, organization: String, receiver: String) =
            IssueLine(batchId, qty, unit, RecipientType.DISTRICT, districtName = district, organization = organization, receiver = receiver)

        fun issue(date: String, doc: String, vararg lines: IssueLine): Long =
            s.stock.issue(IssueInput(date = d(date), docNo = doc, issuedBy = "Karimova D.", lines = lines.toList()))

        issue("2026-02-10", "T-03", toDept(kn09, "30", MeasureUnit.G, "Sud-kimyo", "To‘xtayev B."))
        val gistologiyaIssue = issue("2026-03-10", "T-12", toDept(es001, "3", MeasureUnit.L, "Gistologiya", "Karimova D."))
        issue("2026-04-02", "T-15", toDept(fr201, "5", MeasureUnit.L, "Morfologiya", "Saidova N."))
        issue("2026-05-10", "T-21", toDept(xl117, "680", MeasureUnit.ML, "Sud-kimyo", "To‘xtayev B."))
        issue("2026-05-20", "T-24", toDept(es001, "1", MeasureUnit.L, "Biologiya", "Rahimov A."))
        issue("2026-06-15", "T-30", toDistrict(es001, "0,5", MeasureUnit.L, "Chilonzor", "Chilonzor tuman sud-tibbiy ekspertiza bo‘limi", "Aliyev S."))
        issue("2026-07-01", "T-33", toDistrict(kn09, "8", MeasureUnit.G, "Yunusobod", "Yunusobod tuman bo‘limi", "Qodirov J."))
        issue("2026-08-12", "T-40", toDept(nx12, "250", MeasureUnit.G, "Morfologiya", "Saidova N."))
        issue("2026-09-15", "T-44", toDept(pl3, "20", MeasureUnit.PIECE, "Biologiya", "Rahimov A."))
        issue("2026-10-20", "T-51", toDistrict(fr201, "2", MeasureUnit.L, "Chilonzor", "Chilonzor tuman sud-tibbiy ekspertiza bo‘limi", "Aliyev S."))

        val item = s.queries.issueItems().single { it.issueId == gistologiyaIssue }.id
        s.stock.returnItem(item, ReturnInput(d("2026-04-01"), "0,5", MeasureUnit.L, reason = "Ortib qoldi", receivedBy = "Karimova D."))

        val etil = s.reagents.batch(es001)!!.reagentId
        val xloroform = s.reagents.batch(xl117)!!.reagentId
        return Ids(etil, es001, es002, es003, xloroform, xl117, xl131, kn09, nx12, pl3, fr201, eo77, item)
    }
}

/** Fayl oynalari o‘rniga: oldindan berilgan javoblar, chaqiruvlar yoziladi. */
class FakeFileDialogs(
    var saveAnswer: Path? = null,
    var openAnswer: Path? = null,
) : FileDialogs {
    val calls = mutableListOf<String>()

    override fun save(title: String, defaultName: String, extension: String): Path? {
        calls += "save:$defaultName"
        return saveAnswer
    }

    override fun open(title: String, extensions: List<String>): Path? {
        calls += "open:${extensions.joinToString(",")}"
        return openAnswer
    }

    override fun openFolder(dir: Path) {
        calls += "folder:$dir"
    }
}

package uz.reaktivombor.desktop.selftest

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import org.dhatim.fastexcel.reader.ReadableWorkbook
import uz.reaktivombor.desktop.SheetRow
import uz.reaktivombor.desktop.SheetStore
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.InsufficientStockError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.Xlsx
import uz.reaktivombor.desktop.core.expiryState
import uz.reaktivombor.desktop.db.Schema
import uz.reaktivombor.desktop.db.queryLong
import uz.reaktivombor.desktop.db.queryOne
import uz.reaktivombor.desktop.report.ExcelExport
import uz.reaktivombor.desktop.report.PdfExport
import uz.reaktivombor.desktop.report.ReportBuilder
import uz.reaktivombor.desktop.report.ReportFilter
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.service.AuditAction
import uz.reaktivombor.desktop.service.AuditFilter
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.ExcelImport
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.MovementKind
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.NotificationKind
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.ReturnInput
import uz.reaktivombor.desktop.service.Services
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Comparator

/** Butun self-test uchun vaqt chegarasi (CI 60 s kutadi). */
private const val TIMEOUT_MS = 50_000L

/**
 * `--self-test <fayl>`: o‘rnatilgan dasturning o‘zida (Windows CI, .deb) bazadan hisobotgacha bo‘lgan asosiy yo‘lni
 * tekshiradi. Har bir qadam — bitta qator: "OK  <qadam>" yoki "FAIL <qadam>: <sabab>" (UTF-8); "#" bilan boshlangan
 * qatorlar — muhit haqida ma’lumot. Foydalanuvchining haqiqiy ma’lumotlariga tegmaydi: vaqtinchalik papkada yangi baza.
 * Natija: 0 — hammasi OK, 1 — biror qadam o‘tmadi, 2 — vaqt tugadi yoki kutilmagan xato, 3 — natija faylini yozib bo‘lmadi.
 * Hech qachon osilib qolmaydi: [TIMEOUT_MS] dan keyin to‘xtatiladi.
 */
fun runSelfTest(output: Path): Int {
    // AWT (OpenPDF ranglari) ekran so‘ramasin: CI va xizmat sifatida ishga tushirilganda ham ishlaydi.
    System.setProperty("java.awt.headless", "true")
    val writer = try {
        output.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(output, StandardCharsets.UTF_8)
    } catch (e: Exception) {
        System.err.println("Self-test: natija faylini ochib bo‘lmadi: $output (${e.message})")
        return 3
    }
    val log = SelfTestLog(writer)
    try {
        val run = SelfTestRun(log)
        val worker = Thread({ run.runAll() }, "reaktiv-self-test").apply { isDaemon = true }
        val started = System.nanoTime()
        worker.start()
        worker.join(TIMEOUT_MS)
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        if (worker.isAlive) {
            log.fail("Vaqt chegarasi", "self-test ${TIMEOUT_MS / 1000} s ichida tugamadi — to‘xtatildi")
            log.info("Natija: FAIL (vaqt tugadi)")
            return 2
        }
        log.info("Natija: ${run.passed}/${run.total} OK, ${"%.1f".format(java.util.Locale.ROOT, seconds)} s")
        return when {
            run.crashed -> 2
            run.failed > 0 -> 1
            else -> 0
        }
    } catch (e: Throwable) {
        log.fail("Self-test", e.message ?: e.javaClass.name)
        return 2
    } finally {
        log.close()
    }
}

/**
 * sqlite-jdbc kutubxonasini qayerga chiqaradi: «<papka> (org.sqlite.tmpdir)» — dastur ASCII papkaga yo‘naltirgan
 * (Windows, %TEMP% yo‘li tizim kodlashiga sig‘maydi), «<papka> (java.io.tmpdir)» — odatdagi vaqtinchalik papka.
 */
internal fun sqliteNativeDirText(): String {
    val dir = System.getProperty("org.sqlite.tmpdir")?.takeIf { it.isNotBlank() }?.let { "$it (org.sqlite.tmpdir)" }
        ?: "${System.getProperty("java.io.tmpdir")} (java.io.tmpdir)"
    return "$dir, sun.jnu.encoding=${System.getProperty("sun.jnu.encoding")}"
}

/** Natija fayli: har bir qator darhol yoziladi (to‘xtab qolsa ham yozilgani qoladi). */
private class SelfTestLog(private val writer: BufferedWriter) {
    private var closed = false

    @Synchronized
    fun line(text: String) {
        if (closed) return
        runCatching {
            writer.write(text)
            writer.newLine()
            writer.flush()
        }
    }

    fun info(text: String) = line("# $text")

    fun ok(step: String) = line("OK  $step")

    fun fail(step: String, message: String) = line("FAIL $step: " + message.replace('\n', ' ').replace('\r', ' '))

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        runCatching { writer.close() }
    }
}

private class SelfTestRun(private val log: SelfTestLog) {
    var total = 0
    var passed = 0
    val failed: Int get() = total - passed
    var crashed = false

    private val clock: Clock = Clock.systemDefaultZone()
    private lateinit var dataDir: Path
    private var services: Services? = null
    private val today: LocalDate get() = LocalDate.now(clock)
    private val s: Services get() = services ?: throw IllegalStateException("baza ochilmagan (oldingi qadam o‘tmadi)")

    private val ids = HashMap<String, Long>()

    private fun step(name: String, block: () -> Unit) {
        total++
        try {
            block()
            passed++
            log.ok(name)
        } catch (e: Throwable) {
            // Asl sabab ham (masalan, «Error opening connection» ← SQLite kutubxonasi yuklanmadi).
            val root = generateSequence(e) { it.cause?.takeIf { cause -> cause !== it } }.take(16).last()
            val cause = if (root === e) "" else " ← ${root.message ?: root.javaClass.simpleName} [${root.javaClass.simpleName}]"
            log.fail(name, (e.message ?: e.javaClass.simpleName) + " [${e.javaClass.simpleName}]" + cause)
        }
    }

    private fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw IllegalStateException(message())
    }

    private fun <T> expectEquals(expected: T, actual: T, what: String) {
        if (expected != actual) throw IllegalStateException("$what: kutilgan $expected, aslida $actual")
    }

    fun runAll() {
        try {
            dataDir = Files.createTempDirectory("reaktiv-selftest-")
            log.info("Reaktiv Control self-test, ${LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}")
            log.info("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
            log.info("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")}), ${System.getProperty("java.home")}")
            log.info("SQLite kutubxonasi papkasi: ${sqliteNativeDirText()}")
            log.info("Ma’lumotlar papkasi (vaqtinchalik): $dataDir")
            steps()
        } catch (e: Throwable) {
            crashed = true
            log.fail("Self-test", e.message ?: e.javaClass.name)
        } finally {
            runCatching { services?.db?.close() }
            // Muvaffaqiyatli bo‘lsa vaqtinchalik papka o‘chiriladi; xato bo‘lsa — tekshirish uchun qoldiriladi.
            if (::dataDir.isInitialized && failed == 0 && !crashed) deleteTree(dataDir)
        }
    }

    private fun steps() {
        step("Baza: ochish, sxema va boshlang‘ich ma’lumotlar") {
            services = Services.open(dataDir, clock)
            val version = s.db.read { Schema.userVersion(it) }
            expectEquals(Schema.VERSION, version, "sxema versiyasi")
            expectEquals(Schema.APP_MARKER, s.settings.get("app"), "dastur belgisi")
            expectEquals(listOf("Gistologiya", "Biologiya", "Sud-kimyo", "Morfologiya"), s.directory.departments().map { it.name }, "bo‘limlar")
            val sqlite = s.db.read { conn -> conn.queryOne("SELECT sqlite_version()") { it.getString(1) } }
            log.info("SQLite: $sqlite, baza fayli: ${DataDir.dbFile(dataDir)}")
        }

        step("Etil spirti: 19 l kelgan, 14 l qoldiq, uch xil holat") {
            val es1 = batch("Etil spirti", "ES-001", today.plusDays(20), "5", MeasureUnit.L)
            val es2 = batch("Etil spirti", "ES-002", today.plusDays(60), "10", MeasureUnit.L)
            batch("Etil spirti", "ES-003", today.plusDays(200), "4", MeasureUnit.L)
            issue(es1, "2", MeasureUnit.L, department = "Biologiya")
            issue(es2, "3", MeasureUnit.L, department = "Gistologiya")
            val rows = s.queries.batchRows(BatchFilter(text = "Etil spirti"), today)
            expectEquals(listOf("ES-001", "ES-002", "ES-003"), rows.map { it.series }, "partiyalar tartibi (FEFO)")
            expectEquals(listOf(ExpiryState.RED, ExpiryState.YELLOW, ExpiryState.GREEN), rows.map { it.state }, "holatlar")
            expectEquals("19 l", Qty.formatWithUnit(rows.first().groupReceived, rows.first().groupUnit), "kelgan jami")
            expectEquals("14 l", Qty.formatWithUnit(rows.first().groupTotal, rows.first().groupUnit), "umumiy qoldiq")
            expectEquals(listOf("3", "7", "4"), rows.map { Qty.format(it.remainder, it.unit) }, "partiya qoldiqlari")
        }

        step("Bir operatsiyada to‘rt bo‘limga: 200 / 300 / 150 / 100 ml") {
            val f = batch("Formalin", "F-1", today.plusDays(300), "1", MeasureUnit.L)
            ids["formalin"] = f
            val lines = listOf("Gistologiya" to "200", "Biologiya" to "300", "Sud-kimyo" to "150", "Morfologiya" to "100").map { (dept, qty) ->
                IssueLine(f, qty, MeasureUnit.ML, RecipientType.DEPARTMENT, departmentId = deptId(dept))
            }
            val before = count("issues")
            s.stock.issue(IssueInput(today, docNo = "T-1", lines = lines))
            expectEquals(before + 1, count("issues"), "operatsiyalar soni")
            expectEquals(4, s.queries.issueItems(OpFilter(batchId = f)).size, "qatorlar soni")
            expectEquals("250 ml", Qty.formatWithUnit(s.stock.remainder(f), MeasureUnit.ML), "qoldiq")
        }

        step("Tumanga ikki marta, turli sanalarda") {
            val a = batch("Aseton", "A-1", today.plusDays(150), "2", MeasureUnit.L, received = today.minusDays(30))
            ids["aseton"] = a
            s.stock.issue(IssueInput(today.minusDays(10), lines = listOf(IssueLine(a, "100", MeasureUnit.ML, RecipientType.DISTRICT, districtName = "Chilonzor"))))
            s.stock.issue(IssueInput(today, lines = listOf(IssueLine(a, "150", MeasureUnit.ML, RecipientType.DISTRICT, districtName = "Chilonzor"))))
            val summary = s.queries.districtSummary()
            val chilonzor = summary.districts.single()
            expectEquals(2, chilonzor.items, "berishlar soni")
            expectEquals(today.minusDays(10), chilonzor.firstDate, "birinchi sana")
            expectEquals(today, chilonzor.lastDate, "oxirgi sana")
            expectEquals("1,75 l", Qty.formatWithUnit(s.stock.remainder(a), MeasureUnit.L), "qoldiq")
        }

        step("Qoldiq yetmasa — butun operatsiya saqlanmaydi") {
            val f = ids.getValue("formalin")
            val a = ids.getValue("aseton")
            val items = count("issue_items")
            val movements = count("stock_movements")
            val error = runCatching {
                s.stock.issue(
                    IssueInput(
                        today,
                        lines = listOf(
                            IssueLine(f, "100", MeasureUnit.ML, RecipientType.DEPARTMENT, departmentId = deptId("Biologiya")),
                            IssueLine(a, "50", MeasureUnit.L, RecipientType.DEPARTMENT, departmentId = deptId("Biologiya")),
                        ),
                    ),
                )
            }.exceptionOrNull()
            expect(error is InsufficientStockError) { "InsufficientStockError kutilgan edi, aslida: $error" }
            expectEquals(items, count("issue_items"), "berish qatorlari")
            expectEquals(movements, count("stock_movements"), "harakatlar")
            expectEquals("250 ml", Qty.formatWithUnit(s.stock.remainder(f), MeasureUnit.ML), "Formalin qoldig‘i")
        }

        step("Qaytarish") {
            val f = ids.getValue("formalin")
            val item = s.queries.issueItems(OpFilter(batchId = f)).single { it.departmentName == "Biologiya" }
            s.stock.returnItem(item.id, ReturnInput(today, "100", MeasureUnit.ML, reason = "Ishlatilmadi"))
            expectEquals("350 ml", Qty.formatWithUnit(s.stock.remainder(f), MeasureUnit.ML), "qoldiq")
            expectEquals(100_000L, s.queries.issueItems(OpFilter(batchId = f)).single { it.id == item.id }.returnedQty, "qaytarilgan")
        }

        step("Bekor qilish va tuzatish (tarix saqlanadi)") {
            val f = ids.getValue("formalin")
            val items = s.queries.issueItems(OpFilter(batchId = f))
            s.stock.cancelIssueItem(items.single { it.departmentName == "Morfologiya" }.id, "Xato kiritildi")
            expectEquals("450 ml", Qty.formatWithUnit(s.stock.remainder(f), MeasureUnit.ML), "bekor qilingandan keyin")
            val sud = items.single { it.departmentName == "Sud-kimyo" }
            s.stock.correctIssueItem(
                sud.id,
                IssueLine(f, "120", MeasureUnit.ML, RecipientType.DEPARTMENT, departmentId = deptId("Sud-kimyo")),
                today, "Miqdor noto‘g‘ri yozilgan",
            )
            expectEquals("480 ml", Qty.formatWithUnit(s.stock.remainder(f), MeasureUnit.ML), "tuzatilgandan keyin")
            val all = s.queries.issueItems(OpFilter(batchId = f, includeCancelled = true))
            expectEquals(2, all.count { it.cancelled }, "bekor qilingan qatorlar")
            expectEquals(2, s.queries.ledger(f).count { it.kind == MovementKind.REVERSAL }, "teskari harakatlar")
            expect(s.queries.auditLogs(AuditFilter(action = AuditAction.CORRECTED)).isNotEmpty()) { "auditda tuzatish yo‘q" }
        }

        step("Yaroqlilik chegaralari: 90 / 30 / 0 kun") {
            val expected = mapOf(
                91L to ExpiryState.GREEN, 90L to ExpiryState.YELLOW, 31L to ExpiryState.YELLOW, 30L to ExpiryState.RED,
                0L to ExpiryState.RED, -1L to ExpiryState.EXPIRED,
            )
            expected.forEach { (days, state) -> expectEquals(state, expiryState(today.plusDays(days), today), "$days kun") }
            expectEquals(ExpiryState.UNKNOWN, expiryState(null, today), "muddatsiz")
            val x = batch("Xloroform", "X-1", today.minusDays(1), "500", MeasureUnit.ML, received = today.minusDays(200))
            expectEquals(ExpiryState.EXPIRED, s.queries.batchRows(BatchFilter(reagentId = reagentOf(x)), today).single().state, "muddati o‘tgan partiya")
        }

        step("Minimal qoldiqdan kam — eslatma") {
            val n = s.reagents.createBatch(
                NewBatch(
                    name = "Natriy xlorid", series = "N-1", receivedOn = today.minusDays(5), initialQtyText = "1", unit = MeasureUnit.KG,
                    expiresOn = today.plusDays(400), minStockText = "2", minStockUnit = MeasureUnit.KG,
                ),
            )
            val notes = s.queries.notifications(today)
            expect(notes.any { it.kind == NotificationKind.BELOW_MIN && it.reagentId == reagentOf(n) }) { "minimal qoldiq eslatmasi yo‘q" }
            expect(notes.any { it.kind == NotificationKind.EXPIRED }) { "muddati o‘tgan eslatmasi yo‘q" }
            expect(s.queries.notificationCount(today) >= 2) { "qo‘ng‘iroqcha soni" }
        }

        val reports = dataDir.resolve("hisobotlar")
        for (kind in ReportKind.entries) {
            step("Hisobot «${kind.title}»: Excel va PDF") {
                val filter = if (kind.needsName) ReportFilter(name = "Etil spirti") else ReportFilter()
                val data = ReportBuilder(s).build(kind, filter, today)
                val xlsx = ExcelExport.write(data, reports.resolve("${kind.name}.xlsx"))
                val pdf = PdfExport.write(data, reports.resolve("${kind.name}.pdf"))
                checkXlsx(xlsx, data.columns.map { it.title }, data.title)
                checkPdf(pdf, data.title)
            }
        }

        step("Exceldan import: shablon → to‘ldirish → tekshirish → import") {
            val template = s.excelImport.template(dataDir.resolve("import/shablon.xlsx"))
            val example = s.excelImport.preview(template)
            expect(example.size == 1 && !example.single().valid) { "shablondagi namuna qator import qilinmasligi kerak" }
            val headers = readRows(template, ExcelImport.SHEET).first()
            expectEquals(ExcelImport.HEADERS, headers, "shablon sarlavhalari")
            val filled = dataDir.resolve("import/toldirilgan.xlsx")
            Files.newOutputStream(filled).use { out ->
                Xlsx.write(out) { wb ->
                    val ws = wb.newWorksheet(ExcelImport.SHEET)
                    headers.forEachIndexed { c, h -> ws.value(0, c, h) }
                    fun put(r: Int, column: ExcelImport.Column, value: Any) {
                        val c = ExcelImport.Column.entries.indexOf(column)
                        when (value) {
                            is LocalDate -> ws.value(r, c, value)
                            is Number -> ws.value(r, c, value)
                            else -> ws.value(r, c, value.toString())
                        }
                    }
                    put(1, ExcelImport.Column.NAME, "Glitserin"); put(1, ExcelImport.Column.SERIES, "G-7")
                    put(1, ExcelImport.Column.RECEIVED, today.minusDays(2)); put(1, ExcelImport.Column.EXPIRES, today.plusDays(500))
                    put(1, ExcelImport.Column.QTY, 2.5); put(1, ExcelImport.Column.UNIT, "litr"); put(1, ExcelImport.Column.DEPARTMENT, "Biologiya")
                    put(2, ExcelImport.Column.NAME, "Kaliy permanganat"); put(2, ExcelImport.Column.SERIES, "KP-1")
                    put(2, ExcelImport.Column.RECEIVED, uz.reaktivombor.desktop.DateText.format(today.minusDays(1)))
                    put(2, ExcelImport.Column.EXPIRES, uz.reaktivombor.desktop.DateText.format(today.plusDays(700)))
                    put(2, ExcelImport.Column.QTY, "1 250,5"); put(2, ExcelImport.Column.UNIT, "gr"); put(2, ExcelImport.Column.DEPARTMENT, "Toksikologiya")
                }
            }
            val batchesBefore = count("batches")
            val preview = s.excelImport.preview(filled)
            expectEquals(listOf(2, 3), preview.map { it.rowNumber }, "qator raqamlari")
            expect(preview.all { it.valid }) { "xatolar: " + preview.flatMap { it.errors } }
            expectEquals(batchesBefore, count("batches"), "tekshirish bazaga yozmasligi kerak")
            val result = s.excelImport.import(preview, filled)
            expectEquals(2, result.created, "yaratilgan partiyalar")
            expectEquals(batchesBefore + 2, count("batches"), "partiyalar soni")
            expect(s.directory.departments().any { it.name == "Toksikologiya" }) { "yangi bo‘lim yaratilmadi" }
            expect(s.queries.auditLogs(AuditFilter(action = AuditAction.EXCEL_IMPORTED)).size == 1) { "auditda import yo‘q" }
        }

        step("Zaxira: yaratish, tekshirish, tiklash") {
            val backup = s.backup.create(dataDir.resolve("qolda/zaxira.db"))
            val batches = count("batches")
            expectEquals(batches, s.backup.validate(backup).batches, "zaxiradagi partiyalar")
            batch("Vaqtinchalik", "V-1", today.plusDays(100), "1", MeasureUnit.L)
            expectEquals(batches + 1, count("batches"), "qo‘shilgandan keyin")
            val restored = s.backup.restore(backup)
            expectEquals(batches, count("batches"), "tiklangandan keyin")
            expect(Files.isRegularFile(restored.preRestoreBackup)) { "tiklashdan oldingi nusxa yo‘q" }
            expect(s.reagents.searchNames("Vaqtinchalik").isEmpty()) { "tiklangan bazada keyingi o‘zgarish qolgan" }
        }

        step("Eski dastur ma’lumotlari (JSON) ko‘chiriladi") {
            val rows = listOf(
                SheetRow(1, "Sirka kislota", "01.06.2026", "1000", "ml", "01.06.2027", "800", "Biologiya", "200", createdAt = 1),
                SheetRow(2, "Yod eritmasi", "", "250", "g", "01.03.2027", "250", "", "", createdAt = 2),
            )
            val file = dataDir.resolve("eski/reaktivlar-jadval.json")
            Files.createDirectories(file.parent)
            Files.writeString(file, SheetStore.toJson(rows, "2026-10-01T10:00:00"), StandardCharsets.UTF_8)
            val before = count("batches")
            val result = s.legacy.importFile(file)
            expectEquals(2, result.imported, "ko‘chirilgan qatorlar")
            expectEquals(before + 2, count("batches"), "partiyalar soni")
            val sirka = s.queries.batchRows(BatchFilter(text = "Sirka kislota"), today).single()
            expectEquals("600 ml", Qty.formatWithUnit(sirka.remainder, sirka.unit), "Sirka kislota qoldig‘i (omborda 800, berilgan 200)")
        }

        step("Yopib qayta ochish — ma’lumotlar saqlanadi") {
            val batches = count("batches")
            val movements = count("stock_movements")
            val formalin = s.stock.remainder(ids.getValue("formalin"))
            s.db.close()
            services = null
            services = Services.open(dataDir, clock)
            expectEquals(batches, count("batches"), "partiyalar")
            expectEquals(movements, count("stock_movements"), "harakatlar")
            expectEquals(formalin, s.stock.remainder(ids.getValue("formalin")), "Formalin qoldig‘i")
            expectEquals("ok", s.db.read { conn -> conn.queryOne("PRAGMA integrity_check") { it.getString(1) } }, "integrity_check")
        }
    }

    // ---------------------------------------------------------------- yordamchilar

    private fun batch(
        name: String,
        series: String,
        expires: LocalDate,
        qty: String,
        unit: MeasureUnit,
        received: LocalDate = today.minusDays(10),
    ): Long = s.reagents.createBatch(
        NewBatch(name = name, series = series, receivedOn = received, initialQtyText = qty, unit = unit, expiresOn = expires),
    )

    private fun issue(batchId: Long, qty: String, unit: MeasureUnit, department: String) {
        s.stock.issue(IssueInput(today, lines = listOf(IssueLine(batchId, qty, unit, RecipientType.DEPARTMENT, departmentId = deptId(department)))))
    }

    private fun deptId(name: String): Long = s.directory.departments(includeInactive = true).first { it.name == name }.id

    private fun reagentOf(batchId: Long): Long = s.reagents.batch(batchId)!!.reagentId

    private fun count(table: String): Long = s.db.read { it.queryLong("SELECT COUNT(*) FROM $table") }

    private fun readRows(file: Path, sheetName: String? = null): List<List<String>> =
        ReadableWorkbook(file.toFile()).use { wb ->
            val sheet = if (sheetName == null) wb.firstSheet else wb.findSheet(sheetName).orElseThrow { IllegalStateException("varaq yo‘q: $sheetName") }
            sheet.read().map { row -> (0 until row.cellCount).map { i -> row.getCellText(i).orEmpty() }.dropLastWhile { it.isEmpty() } }
        }

    /** Excel fayli qayta ochiladi: sarlavha va jadval sarlavhasi qatori o‘qiladi. */
    private fun checkXlsx(file: Path, headers: List<String>, title: String) {
        val rows = readRows(file)
        expectEquals(title, rows.first().firstOrNull(), "Excel sarlavhasi")
        expect(rows.any { it == headers }) { "Excel: jadval sarlavhasi qatori topilmadi" }
    }

    /** PDF qayta ochiladi: kamida bitta sahifa, sarlavha va o‘zbekcha harflar (o‘, g‘) matnda bor. */
    private fun checkPdf(file: Path, title: String) {
        // Fayl xotiraga o‘qiladi: nomi bilan ochilsa OpenPDF uni xaritalab (mmap) ochadi va Windows uni o‘chirishga qo‘ymaydi.
        val reader = PdfReader(Files.readAllBytes(file))
        try {
            expect(reader.numberOfPages >= 1) { "PDF sahifasi yo‘q" }
            val text = PdfTextExtractor(reader).getTextFromPage(1)
            val flat = text.replace(Regex("\\s+"), " ")
            expect(flat.contains(title)) { "PDF matnida sarlavha yo‘q: «$title»" }
            expect(flat.contains("o‘") || flat.contains("g‘")) { "PDF matnida o‘zbekcha o‘/g‘ harflari yo‘q" }
            expect(flat.contains("Yaratilgan:")) { "PDF matnida «Yaratilgan:» yo‘q" }
        } finally {
            reader.close()
        }
    }

    /** Har bir fayl alohida o‘chiriladi; Windows hali qo‘yib yubormagan fayl bo‘lsa — bir oz kutib, yana bir marta. */
    private fun deleteTree(root: Path) {
        repeat(3) { attempt ->
            if (Files.notExists(root)) return
            if (attempt > 0) {
                System.gc()
                Thread.sleep(300L * attempt)
            }
            runCatching {
                Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).toList() }
                    .forEach { path -> runCatching { Files.deleteIfExists(path) } }
            }
        }
    }
}

package uz.reaktivombor.desktop.report

import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.SafeFiles
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.display
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.LedgerRow
import uz.reaktivombor.desktop.service.MovementKind
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.service.Reagent
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.service.StockTotal
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------- model

/**
 * Hisobot turlari (texnik topshiriq, 13-bo‘lim). [periodLabel] — davr filtri nimaga qo‘llanadi (filtr qatoridagi nomi);
 * null — davr hisobga olinmaydi (joriy holat hisoboti). Qolgan bayroqlar — UI qaysi filtrlarni ko‘rsatishi.
 */
enum class ReportKind(
    val title: String,
    val periodLabel: String?,
    val usesSeries: Boolean,
    val usesDepartment: Boolean,
    val usesDistrict: Boolean,
    /** Reaktiv nomi majburiy (bitta reaktiv bo‘yicha hisobot). */
    val needsName: Boolean = false,
) {
    ALL_REAGENTS("Barcha reaktivlar ro‘yxati", "Kelgan sana", true, true, false),
    BATCHES("Reaktivlarning partiyalar bo‘yicha ro‘yxati", "Kelgan sana", true, true, false),
    GROUP_TOTALS("Bir xil nomdagi reaktivlarning umumiy qoldig‘i", null, false, true, false),
    BY_DEPARTMENT("Bo‘limlar bo‘yicha reaktivlar qoldig‘i", "Davr", true, true, false),
    DISTRICTS("Tumanlarga berilgan reaktivlar hisoboti", "Davr", true, false, true),
    RECEIPTS("Kirim operatsiyalari", "Davr", true, true, false),
    ISSUES("Sarf operatsiyalari", "Davr", true, true, true),
    EXPIRING("Yaroqlilik muddati yaqinlashgan reaktivlar", "Yaroqlilik muddati", true, true, false),
    EXPIRED("Muddati o‘tgan reaktivlar", "Yaroqlilik muddati", true, true, false),
    BELOW_MIN("Minimal qoldiqdan kamaygan reaktivlar", null, false, false, false),
    MOVEMENT("Davr uchun reaktivlar harakati", "Davr", true, true, false),
    HISTORY("Reaktivning to‘liq operatsiyalar tarixi", "Davr", true, false, false, needsName = true),
}

/** Hisobot filtri. Bo‘sh/null — cheklov yo‘q. Hisobot turiga tegishli bo‘lmagan filtr e’tiborga olinmaydi. */
data class ReportFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val name: String? = null,
    val series: String? = null,
    val departmentId: Long? = null,
    val districtId: Long? = null,
)

/**
 * Ustun turi — katakdagi qiymat turi: TEXT — String, DATE — LocalDate, QTY — BigDecimal (qatorning o‘z birligida,
 * birlik alohida ustunda), INT — Long, STATUS — ExpiryState (matni va rangi bilan). Bo‘sh katak — null.
 */
enum class ReportColumnType { TEXT, DATE, QTY, INT, STATUS }

/** [width] — Excel ustun kengligi (belgilar soni); PDF da nisbiy kenglik. */
data class ReportColumn(val title: String, val type: ReportColumnType = ReportColumnType.TEXT, val width: Int = 12)

/** Qator: [state] — butun qator shu holat rangida (och tusda); [summary] — jami qatori (qalin, kulrang fon). */
data class ReportRow(val cells: List<Any?>, val state: ExpiryState? = null, val summary: Boolean = false)

data class ReportData(
    val kind: ReportKind,
    val title: String,
    val orgName: String,
    val generatedAt: LocalDateTime,
    val filterLines: List<String>,
    val columns: List<ReportColumn>,
    val rows: List<ReportRow>,
    val footerLines: List<String>,
) {
    /** Katak matni (ko‘rib chiqish jadvali va PDF uchun). */
    fun text(row: ReportRow, column: Int): String = ReportFormat.cell(columns[column].type, row.cells.getOrNull(column))

    /** "Yaratilgan: 29.10.2026 14:05". */
    val generatedLine: String get() = "Yaratilgan: " + ReportFormat.dateTime(generatedAt)
}

/** Hisobot qiymatlarini matnga aylantirish (Excel’dan tashqari hamma joyda bir xil). */
object ReportFormat {
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun dateTime(value: LocalDateTime): String = value.format(DATE_TIME)

    fun cell(type: ReportColumnType, value: Any?): String = when {
        value == null -> ""
        type == ReportColumnType.DATE && value is LocalDate -> value.display()
        type == ReportColumnType.QTY && value is BigDecimal -> decimal(value)
        type == ReportColumnType.INT && value is Number -> group(value.toLong().toString())
        type == ReportColumnType.STATUS && value is ExpiryState -> value.label
        value is LocalDate -> value.display()
        value is BigDecimal -> decimal(value)
        value is ExpiryState -> value.label
        else -> value.toString()
    }

    /** 1250.5 → "1 250,5" (Qty.format bilan bir xil ko‘rinish). */
    fun decimal(value: BigDecimal): String {
        val plain = value.stripTrailingZeros().toPlainString()
        val negative = plain.startsWith("-")
        val digits = plain.removePrefix("-")
        val whole = digits.substringBefore('.')
        val fraction = digits.substringAfter('.', "")
        return (if (negative) "-" else "") + group(whole) + if (fraction.isNotEmpty()) ",$fraction" else ""
    }

    private fun group(digits: String): String {
        val negative = digits.startsWith("-")
        val text = digits.removePrefix("-")
        val sb = StringBuilder()
        text.forEachIndexed { index, c ->
            if (index > 0 && (text.length - index) % 3 == 0) sb.append(' ')
            sb.append(c)
        }
        return (if (negative) "-" else "") + sb
    }
}

// ---------------------------------------------------------------- tuzuvchi

/**
 * Hisobotlarni xizmatlardagi o‘qish so‘rovlaridan tuzadi (yozmaydi). Miqdorlar har bir qatorning o‘z birligida
 * (birlik alohida ustunda); jamilarda turli o‘lchov turlari (litr va gramm) hech qachon qo‘shilmaydi.
 */
class ReportBuilder(private val services: Services) {

    fun build(kind: ReportKind, filter: ReportFilter = ReportFilter(), today: LocalDate = services.today()): ReportData {
        if (filter.from != null && filter.to != null && filter.from > filter.to) {
            throw UserError("Davr boshi (${filter.from.display()}) oxiridan (${filter.to.display()}) keyin bo‘lishi mumkin emas.", "from")
        }
        val ctx = Context(kind, filter, today)
        val body = when (kind) {
            ReportKind.ALL_REAGENTS -> allReagents(ctx)
            ReportKind.BATCHES -> batches(ctx)
            ReportKind.GROUP_TOTALS -> groupTotals(ctx)
            ReportKind.BY_DEPARTMENT -> byDepartment(ctx)
            ReportKind.DISTRICTS -> districts(ctx)
            ReportKind.RECEIPTS -> receipts(ctx)
            ReportKind.ISSUES -> issues(ctx)
            ReportKind.EXPIRING -> expiryList(ctx, expired = false)
            ReportKind.EXPIRED -> expiryList(ctx, expired = true)
            ReportKind.BELOW_MIN -> belowMin(ctx)
            ReportKind.MOVEMENT -> movement(ctx)
            ReportKind.HISTORY -> history(ctx)
        }
        return ReportData(
            kind = kind,
            title = kind.title,
            orgName = services.settings.orgName,
            generatedAt = LocalDateTime.now(services.clock).truncatedTo(ChronoUnit.SECONDS),
            filterLines = ctx.filterLines(),
            columns = body.columns,
            rows = body.rows,
            footerLines = body.footer,
        )
    }

    private class Body(val columns: List<ReportColumn>, val rows: List<ReportRow>, val footer: List<String>)

    /** Filtrning shu hisobotga tegishli qismi va tanlangan bo‘lim/tuman. */
    private inner class Context(val kind: ReportKind, val filter: ReportFilter, val today: LocalDate) {
        val from: LocalDate? = filter.from.takeIf { kind.periodLabel != null }
        val to: LocalDate? = filter.to.takeIf { kind.periodLabel != null }
        val name: String? = filter.name?.let(::cleanName)?.takeIf { it.isNotEmpty() }
        val series: String? = filter.series?.let(::cleanName)?.takeIf { it.isNotEmpty() && kind.usesSeries }
        val department: Place? = filter.departmentId?.takeIf { kind.usesDepartment }?.let { id ->
            services.directory.get(PlaceKind.DEPARTMENT, id) ?: throw UserError("Tanlangan bo‘lim topilmadi.", "departmentId")
        }
        val district: Place? = filter.districtId?.takeIf { kind.usesDistrict }?.let { id ->
            services.directory.get(PlaceKind.DISTRICT, id) ?: throw UserError("Tanlangan tuman topilmadi.", "districtId")
        }

        /** Tarix hisobotida — topilgan reaktivning to‘liq nomi. */
        var resolvedName: String? = null

        fun nameOk(value: String): Boolean = name == null || nameKey(value).contains(nameKey(name))

        fun seriesOk(value: String): Boolean = series == null || nameKey(value).contains(nameKey(series))

        fun inPeriod(date: LocalDate?): Boolean {
            if (from == null && to == null) return true
            if (date == null) return false
            return (from == null || date >= from) && (to == null || date <= to)
        }

        fun filterLines(): List<String> {
            val lines = mutableListOf<String>()
            val label = kind.periodLabel
            if (label != null && (from != null || to != null)) lines += "$label: " + period(from, to)
            (resolvedName ?: name)?.let { lines += "Reaktiv: $it" }
            series?.let { lines += "Seriya: $it" }
            department?.let { lines += "Bo‘lim: ${it.name}" }
            district?.let { lines += "Tuman: ${it.name}" }
            if (lines.isEmpty()) lines += "Filtr: yo‘q"
            return lines
        }
    }

    // ---------------------------------------------------------------- partiyalar ro‘yxati

    private fun batchRows(ctx: Context, filter: BatchFilter): List<BatchRow> =
        services.queries.batchRows(filter, ctx.today).filter { ctx.nameOk(it.name) && ctx.seriesOk(it.series) }

    private fun allReagents(ctx: Context): Body {
        val rows = batchRows(ctx, BatchFilter(departmentId = ctx.department?.id, receivedFrom = ctx.from, receivedTo = ctx.to))
        val columns = listOf(
            col("№", INT, 5), col("Reaktiv nomi", TEXT, 28), col("Seriya raqami", TEXT, 14),
            col("Bo‘lim / saqlash joyi", TEXT, 20), col("Ishlab chiqarilgan sana", DATE, 12), col("Kelgan sana", DATE, 12),
            col("Yaroqlilik muddati", DATE, 12), col("Qolgan kunlar", INT, 9), col("Holati", STATUS, 17),
            col("Kelgan miqdor", QTY, 11), col("Jami berilgan miqdor", QTY, 11), col("Joriy qoldiq", QTY, 11),
            col("Birlik", TEXT, 7), col("Umumiy qoldiq (shu nom)", TEXT, 14), col("Mas’ul xodim", TEXT, 16),
        )
        val out = rows.mapIndexed { index, r ->
            ReportRow(
                listOf(
                    (index + 1).toLong(), r.name, r.series, placeText(r.departmentName, r.storageLocation), r.producedOn,
                    r.receivedOn, r.expiresOn, r.daysLeft, r.state, qty(r.receivedQty, r.unit), qty(r.issuedQty, r.unit),
                    qty(r.remainder, r.unit), r.unit.code, Qty.formatWithUnit(r.groupTotal, r.groupUnit), r.responsible,
                ),
                state = r.state,
            )
        }
        val footer = listOf(
            "Jami: ${rows.size} ta partiya, qoldig‘i bor: ${rows.count { it.remainder > 0 }} ta",
            "Jami qoldiq: " + totalsText(rows.map { it.unit to it.remainder }),
        )
        return Body(columns, out, footer)
    }

    private fun batches(ctx: Context): Body {
        val rows = batchRows(ctx, BatchFilter(departmentId = ctx.department?.id, receivedFrom = ctx.from, receivedTo = ctx.to))
        val columns = listOf(
            col("№", INT, 5), col("Reaktiv nomi", TEXT, 26), col("Seriya raqami", TEXT, 14), col("Ishlab chiqaruvchi", TEXT, 16),
            col("Kelgan sana", DATE, 12), col("Yaroqlilik muddati", DATE, 12), col("Qolgan kunlar", INT, 9),
            col("Holati", STATUS, 17), col("Kelgan", QTY, 11), col("Berilgan", QTY, 11), col("Hisobdan chiqarilgan", QTY, 11),
            col("Qoldiq", QTY, 11), col("Birlik", TEXT, 7), col("Bo‘lim", TEXT, 14),
        )
        val out = mutableListOf<ReportRow>()
        var number = 0
        // Bir nom va o‘lchov turidagi partiyalar ketma-ket (batchRows tartibi), oxirida — jami qatori.
        rows.groupBy { it.reagentId to it.unit.dimension }.values.forEach { group ->
            group.forEach { r ->
                number++
                out += ReportRow(
                    listOf(
                        number.toLong(), r.name, r.series, r.manufacturer, r.receivedOn, r.expiresOn, r.daysLeft, r.state,
                        qty(r.receivedQty, r.unit), qty(r.issuedQty, r.unit), qty(r.writtenOffQty, r.unit), qty(r.remainder, r.unit),
                        r.unit.code, r.departmentName.orEmpty(),
                    ),
                    state = r.state,
                )
            }
            val unit = MeasureUnit.largest(group.map { it.unit })!!
            out += ReportRow(
                listOf(
                    null, "Jami: ${group.first().name} (${group.size} ta partiya)", null, null, null, null, null, null,
                    qty(group.sumOf { it.receivedQty }, unit), qty(group.sumOf { it.issuedQty }, unit),
                    qty(group.sumOf { it.writtenOffQty }, unit), qty(group.sumOf { it.remainder }, unit), unit.code, null,
                ),
                summary = true,
            )
        }
        val footer = listOf(
            "Jami: ${rows.map { it.reagentId }.distinct().size} ta reaktiv, ${rows.size} ta partiya",
            "Jami qoldiq: " + totalsText(rows.map { it.unit to it.remainder }),
        )
        return Body(columns, out, footer)
    }

    private fun groupTotals(ctx: Context): Body {
        val totals = services.queries.stockTotals(BatchFilter(departmentId = ctx.department?.id), ctx.today).filter { ctx.nameOk(it.name) }
        val columns = listOf(
            col("№", INT, 5), col("Reaktiv nomi", TEXT, 28), col("Formula", TEXT, 12), col("Partiyalar", INT, 9),
            col("Qoldig‘i bor partiyalar", INT, 10), col("Kelgan jami", QTY, 12), col("Berilgan jami", QTY, 12),
            col("Hisobdan chiqarilgan", QTY, 12), col("Umumiy qoldiq", QTY, 12), col("Birlik", TEXT, 7),
            col("Minimal qoldiq", QTY, 11), col("Eng yaqin muddat", DATE, 12), col("Holati", STATUS, 17),
        )
        val out = totals.mapIndexed { index, t ->
            ReportRow(
                listOf(
                    (index + 1).toLong(), t.name, t.formula, t.batches.toLong(), t.activeBatches.toLong(),
                    qty(t.received, t.unit), qty(t.issued, t.unit), qty(t.writtenOff, t.unit), qty(t.remainder, t.unit),
                    t.unit.code, minStockIn(t, t.unit), t.nearestExpiry, t.worstState,
                ),
                state = t.worstState,
            )
        }
        val footer = listOf(
            "Jami: ${totals.map { it.reagentId }.distinct().size} ta reaktiv, ${totals.sumOf { it.batches }} ta partiya",
            "Umumiy qoldiq: " + totalsText(totals.map { it.unit to it.remainder }),
            "Minimal qoldiqdan kam: ${totals.count { it.belowMin }} ta",
        )
        return Body(columns, out, footer)
    }

    // ---------------------------------------------------------------- bo‘lim va tumanlar

    private fun byDepartment(ctx: Context): Body {
        val departments = ctx.department?.let { listOf(it) } ?: services.directory.departments(includeInactive = true)
        val columns = listOf(
            col("№", INT, 5), col("Bo‘lim", TEXT, 16), col("Reaktiv nomi", TEXT, 26), col("Partiyalar (bo‘limda)", INT, 10),
            col("Bo‘limdagi qoldiq", QTY, 12), col("Bo‘limga berilgan", QTY, 12), col("Qaytarilgan", QTY, 11),
            col("Sof berilgan", QTY, 11), col("Birlik", TEXT, 7), col("Eng yaqin muddat", DATE, 12),
            col("Holati", STATUS, 17),
        )
        val all = batchRows(ctx, BatchFilter(includeEmpty = false))
        val issued = services.queries.issueItems(OpFilter(from = ctx.from, to = ctx.to, recipientType = RecipientType.DEPARTMENT))
            .filter { ctx.nameOk(it.name) && ctx.seriesOk(it.series) }
        val out = mutableListOf<ReportRow>()
        val footer = mutableListOf<String>()
        var number = 0
        for (dept in departments) {
            val owned = all.filter { it.departmentId == dept.id }
            val given = issued.filter { it.departmentId == dept.id }
            if (owned.isEmpty() && given.isEmpty()) continue
            val keys = (owned.map { Triple(nameKey(it.name), it.reagentId, it.unit.dimension) } +
                given.map { Triple(nameKey(it.name), it.reagentId, it.unit.dimension) })
                .distinct()
                .sortedWith(compareBy({ it.first }, { it.third.ordinal }))
            for ((_, reagentId, dimension) in keys) {
                val o = owned.filter { it.reagentId == reagentId && it.unit.dimension == dimension }
                val g = given.filter { it.reagentId == reagentId && it.unit.dimension == dimension }
                val unit = MeasureUnit.largest(o.map { it.unit } + g.map { it.unit })!!
                val worst = o.maxByOrNull { it.state.severity }?.state
                number++
                out += ReportRow(
                    listOf(
                        number.toLong(), dept.name, (o.firstOrNull()?.name ?: g.first().name), o.size.toLong(),
                        qty(o.sumOf { it.remainder }, unit), qty(g.sumOf { it.qty }, unit), qty(g.sumOf { it.returnedQty }, unit),
                        qty(g.sumOf { it.netQty }, unit), unit.code, o.mapNotNull { it.expiresOn }.minOrNull(), worst,
                    ),
                    state = worst,
                )
            }
            footer += "${dept.name}: qoldiq " + totalsText(owned.map { it.unit to it.remainder }) +
                "; berilgan (sof) " + totalsText(given.map { it.unit to it.netQty })
        }
        if (footer.isEmpty()) footer += "Ma’lumot topilmadi."
        return Body(columns, out, footer)
    }

    private fun districts(ctx: Context): Body {
        val items = services.queries.issueItems(
            OpFilter(from = ctx.from, to = ctx.to, districtId = ctx.district?.id, recipientType = RecipientType.DISTRICT),
        ).filter { ctx.nameOk(it.name) && ctx.seriesOk(it.series) }
            .sortedWith(compareBy({ nameKey(it.districtName.orEmpty()) }, { it.issuedOn }, { it.id }))
        val columns = listOf(
            col("№", INT, 5), col("Sana", DATE, 12), col("Tuman", TEXT, 16), col("Reaktiv nomi", TEXT, 24),
            col("Seriya raqami", TEXT, 13), col("Yaroqlilik muddati", DATE, 12), col("Berilgan", QTY, 11),
            col("Qaytarilgan", QTY, 11), col("Sof berilgan", QTY, 11), col("Birlik", TEXT, 7),
            col("Hujjat raqami", TEXT, 12), col("Tashkilot", TEXT, 18), col("Qabul qiluvchi", TEXT, 16),
        )
        val out = items.mapIndexed { index, it ->
            val unit = it.enteredUnit
            ReportRow(
                listOf(
                    (index + 1).toLong(), it.issuedOn, it.districtName.orEmpty(), it.name, it.series, it.expiresOn,
                    qty(it.qty, unit), qty(it.returnedQty, unit), qty(it.netQty, unit), unit.code, it.docNo,
                    it.organization, it.receiver,
                ),
            )
        }
        val footer = items.groupBy { it.districtId }.values.map { list ->
            "${list.first().districtName}: ${list.size} ta berish, sof " + totalsText(list.map { it.unit to it.netQty })
        }.ifEmpty { listOf("Ma’lumot topilmadi.") } + "Jami: ${items.size} ta berish, sof " + totalsText(items.map { it.unit to it.netQty })
        return Body(columns, out, footer)
    }

    // ---------------------------------------------------------------- operatsiyalar

    private fun receipts(ctx: Context): Body {
        val list = services.queries.receipts(OpFilter(from = ctx.from, to = ctx.to, departmentId = ctx.department?.id))
            .filter { ctx.nameOk(it.name) && ctx.seriesOk(it.series) }
            .sortedWith(compareBy({ it.receivedOn }, { it.id }))
        val columns = listOf(
            col("№", INT, 5), col("Sana", DATE, 12), col("Reaktiv nomi", TEXT, 26), col("Seriya raqami", TEXT, 13),
            col("Yaroqlilik muddati", DATE, 12), col("Miqdor", QTY, 11), col("Birlik", TEXT, 7),
            col("Turi", TEXT, 13), col("Hujjat raqami", TEXT, 12), col("Qabul qilgan", TEXT, 16), col("Bo‘lim", TEXT, 14),
            col("Izoh", TEXT, 22),
        )
        val out = list.mapIndexed { index, r ->
            val type = when {
                r.correctsId != null -> "Tuzatilgan"
                r.isInitial -> "Boshlang‘ich"
                else -> "Qo‘shimcha"
            }
            ReportRow(
                listOf(
                    (index + 1).toLong(), r.receivedOn, r.name, r.series, r.expiresOn, qty(r.qty, r.enteredUnit), r.enteredUnit.code,
                    type, r.docNo, r.receivedBy, r.departmentName.orEmpty(), r.note,
                ),
            )
        }
        val footer = listOf(
            "Jami: ${list.size} ta kirim",
            "Jami miqdor: " + totalsText(list.map { it.unit to it.qty }),
        )
        return Body(columns, out, footer)
    }

    private fun issues(ctx: Context): Body {
        val list = services.queries.issueItems(
            OpFilter(from = ctx.from, to = ctx.to, departmentId = ctx.department?.id, districtId = ctx.district?.id),
        ).filter { ctx.nameOk(it.name) && ctx.seriesOk(it.series) }
            .sortedWith(compareBy({ it.issuedOn }, { it.id }))
        val columns = listOf(
            col("№", INT, 5), col("Sana", DATE, 12), col("Reaktiv nomi", TEXT, 24), col("Seriya raqami", TEXT, 13),
            col("Yaroqlilik muddati", DATE, 12), col("Turi", TEXT, 13), col("Kimga", TEXT, 18), col("Miqdor", QTY, 11),
            col("Qaytarilgan", QTY, 11), col("Birlik", TEXT, 7), col("Hujjat raqami", TEXT, 12),
            col("Tashkilot / qabul qiluvchi", TEXT, 20), col("Izoh", TEXT, 20),
        )
        val out = list.mapIndexed { index, it ->
            val unit = it.enteredUnit
            ReportRow(
                listOf(
                    (index + 1).toLong(), it.issuedOn, it.name, it.series, it.expiresOn, it.recipientType.label, it.recipientName,
                    qty(it.qty, unit), qty(it.returnedQty, unit), unit.code, it.docNo,
                    listOf(it.organization, it.receiver).filter { s -> s.isNotEmpty() }.joinToString(", "), it.note,
                ),
            )
        }
        fun count(type: RecipientType) = list.count { it.recipientType == type }
        val footer = listOf(
            "Jami: ${list.size} ta qator — bo‘limlarga ${count(RecipientType.DEPARTMENT)}, tumanlarga ${count(RecipientType.DISTRICT)}, " +
                "hisobdan chiqarilgan ${count(RecipientType.WRITE_OFF)}",
            "Jami sarf (qaytarilgani ayirilgan): " + totalsText(list.map { it.unit to it.netQty }),
        )
        return Body(columns, out, footer)
    }

    // ---------------------------------------------------------------- muddat va minimal qoldiq

    private fun expiryList(ctx: Context, expired: Boolean): Body {
        val states = if (expired) setOf(ExpiryState.EXPIRED) else setOf(ExpiryState.RED, ExpiryState.YELLOW)
        val rows = batchRows(
            ctx,
            BatchFilter(
                departmentId = ctx.department?.id, expiresFrom = ctx.from, expiresTo = ctx.to, states = states, includeEmpty = false,
            ),
        ).sortedWith(compareBy({ it.expiresOn }, { nameKey(it.name) }, { it.batchId }))
        val columns = listOf(
            col("№", INT, 5), col("Reaktiv nomi", TEXT, 26), col("Seriya raqami", TEXT, 13), col("Yaroqlilik muddati", DATE, 12),
            col(if (expired) "O‘tgan kunlar" else "Qolgan kunlar", INT, 9), col("Holati", STATUS, 17), col("Qoldiq", QTY, 11),
            col("Birlik", TEXT, 7), col("Bo‘lim", TEXT, 14), col("Saqlash joyi", TEXT, 14), col("Mas’ul xodim", TEXT, 16),
        )
        val out = rows.mapIndexed { index, r ->
            ReportRow(
                listOf(
                    (index + 1).toLong(), r.name, r.series, r.expiresOn, r.daysLeft?.let { if (expired) -it else it }, r.state,
                    qty(r.remainder, r.unit), r.unit.code, r.departmentName.orEmpty(), r.storageLocation, r.responsible,
                ),
                state = r.state,
            )
        }
        val footer = buildList {
            if (expired) {
                add("Jami: ${rows.size} ta partiya (muddati o‘tgan, qoldig‘i bor) — hisobdan chiqarishni ko‘rib chiqing")
            } else {
                add(
                    "Jami: ${rows.size} ta partiya — qizil (30 kun va kam): ${rows.count { it.state == ExpiryState.RED }}, " +
                        "sariq (31–90 kun): ${rows.count { it.state == ExpiryState.YELLOW }}",
                )
            }
            add("Jami qoldiq: " + totalsText(rows.map { it.unit to it.remainder }))
        }
        return Body(columns, out, footer)
    }

    private fun belowMin(ctx: Context): Body {
        val totals = services.queries.stockTotals(BatchFilter(), ctx.today).filter { it.belowMin && ctx.nameOk(it.name) }
        val columns = listOf(
            col("№", INT, 5), col("Reaktiv nomi", TEXT, 28), col("Formula", TEXT, 12), col("Umumiy qoldiq", QTY, 12),
            col("Minimal qoldiq", QTY, 12), col("Yetishmaydi", QTY, 12), col("Birlik", TEXT, 7),
            col("Partiyalar", INT, 9), col("Eng yaqin muddat", DATE, 12), col("Holati", STATUS, 17),
        )
        val out = totals.mapIndexed { index, t ->
            val min = minStockBase(t)
            ReportRow(
                listOf(
                    (index + 1).toLong(), t.name, t.formula, qty(t.remainder, t.unit), min?.let { qty(it, t.unit) },
                    min?.let { qty((it - t.remainder).coerceAtLeast(0), t.unit) }, t.unit.code, t.batches.toLong(),
                    t.nearestExpiry, t.worstState,
                ),
                state = t.worstState,
            )
        }
        return Body(columns, out, listOf("Jami: ${totals.size} ta reaktiv minimal qoldiqdan kam"))
    }

    private fun minStockBase(t: StockTotal): Long? {
        val min = t.minStock ?: return null
        val unit = t.minStockUnit ?: return null
        return if (unit.dimension == t.dimension) min else null
    }

    private fun minStockIn(t: StockTotal, unit: MeasureUnit): BigDecimal? = minStockBase(t)?.let { qty(it, unit) }

    // ---------------------------------------------------------------- harakat va tarix

    /** Partiya harakati davr ichida: boshidagi qoldiq + kirim − berilgan + qaytarilgan − hisobdan chiqarilgan ± moslash = oxiridagi. */
    private class Flow {
        var opening = 0L
        var receipts = 0L
        var issues = 0L
        var returns = 0L
        var writeOffs = 0L
        var adjustments = 0L
        var moved = false
        val closing: Long get() = opening + receipts - issues + returns - writeOffs + adjustments
    }

    private fun movement(ctx: Context): Body {
        val batches = batchRows(ctx, BatchFilter(departmentId = ctx.department?.id, includeArchived = true))
        val ids = batches.map { it.batchId }.toSet()
        val types = services.queries.issueItems(OpFilter(includeCancelled = true)).associate { it.id to it.recipientType }
        val flows = LinkedHashMap<Long, Flow>()
        batches.forEach { flows[it.batchId] = Flow() }
        for (m in services.queries.fullLedger()) {
            if (m.batchId !in ids) continue
            val flow = flows.getValue(m.batchId)
            if (ctx.from != null && m.movedOn < ctx.from) {
                flow.opening += m.qty
                continue
            }
            if (ctx.to != null && m.movedOn > ctx.to) continue
            flow.moved = true
            classify(m, types, flow)
        }
        val shown = batches.filter { b -> flows.getValue(b.batchId).let { it.moved || it.opening != 0L } }
        val withAdjustments = shown.any { flows.getValue(it.batchId).adjustments != 0L }
        val columns = buildList {
            add(col("№", INT, 5)); add(col("Reaktiv nomi", TEXT, 24)); add(col("Seriya raqami", TEXT, 13))
            add(col("Yaroqlilik muddati", DATE, 12)); add(col("Davr boshidagi qoldiq", QTY, 12)); add(col("Kirim", QTY, 11))
            add(col("Berilgan", QTY, 11)); add(col("Qaytarilgan", QTY, 11)); add(col("Hisobdan chiqarilgan", QTY, 11))
            if (withAdjustments) add(col("Moslash", QTY, 10))
            add(col("Davr oxiridagi qoldiq", QTY, 12)); add(col("Birlik", TEXT, 7))
        }
        val out = shown.mapIndexed { index, b ->
            val f = flows.getValue(b.batchId)
            val u = b.unit
            ReportRow(
                buildList {
                    add((index + 1).toLong()); add(b.name); add(b.series); add(b.expiresOn); add(qty(f.opening, u))
                    add(qty(f.receipts, u)); add(qty(f.issues, u)); add(qty(f.returns, u)); add(qty(f.writeOffs, u))
                    if (withAdjustments) add(qty(f.adjustments, u))
                    add(qty(f.closing, u)); add(u.code)
                },
            )
        }
        fun sum(pick: (Flow) -> Long) = totalsText(shown.map { it.unit to pick(flows.getValue(it.batchId)) })
        val footer = listOf(
            "Jami: ${shown.size} ta partiya",
            "Davr boshida: ${sum { it.opening }}; kirim: ${sum { it.receipts }}; berilgan: ${sum { it.issues }}",
            "Qaytarilgan: ${sum { it.returns }}; hisobdan chiqarilgan: ${sum { it.writeOffs }}" +
                (if (withAdjustments) "; moslash: ${sum { it.adjustments }}" else ""),
            "Davr oxirida: ${sum { it.closing }}",
        )
        return Body(columns, out, footer)
    }

    /** Harakat turi: bekor qilish asl operatsiya turiga yoziladi (asl sanasi bilan — davr hisobi to‘g‘ri qoladi). */
    private fun classify(m: LedgerRow, types: Map<Long, RecipientType>, flow: Flow) {
        fun issueSide(itemId: Long, qty: Long) {
            if (types[itemId] == RecipientType.WRITE_OFF) flow.writeOffs -= qty else flow.issues -= qty
        }
        when (m.kind) {
            MovementKind.RECEIPT -> flow.receipts += m.qty
            MovementKind.ISSUE -> issueSide(m.issueItemId ?: -1, m.qty)
            MovementKind.RETURN -> flow.returns += m.qty
            MovementKind.ADJUSTMENT -> flow.adjustments += m.qty
            MovementKind.REVERSAL -> when {
                m.returnId != null -> flow.returns += m.qty
                m.issueItemId != null -> issueSide(m.issueItemId, m.qty)
                m.receiptId != null -> flow.receipts += m.qty
                else -> flow.adjustments += m.qty
            }
        }
    }

    private fun history(ctx: Context): Body {
        val reagent = resolveReagent(ctx.name)
        ctx.resolvedName = reagent.name
        val batches = services.reagents.batchesOf(reagent.id).associateBy { it.id }
        val ledger = services.queries.reagentLedger(reagent.id).filter { ctx.seriesOk(it.series) }
        val columns = listOf(
            col("№", INT, 5), col("Sana", DATE, 12), col("Seriya raqami", TEXT, 13), col("Yaroqlilik muddati", DATE, 12),
            col("Operatsiya", TEXT, 34), col("Hujjat raqami", TEXT, 12), col("Kirim", QTY, 11), col("Chiqim", QTY, 11),
            col("Partiya qoldig‘i", QTY, 12), col("Birlik", TEXT, 7), col("Kiritilgan vaqt", TEXT, 16),
            col("Izoh", TEXT, 16),
        )
        val out = mutableListOf<ReportRow>()
        // Davr boshidagi qoldiq — har bir partiya uchun (qoldiq hisobi boshidan yuritiladi).
        if (ctx.from != null) {
            ledger.filter { it.movedOn < ctx.from }.groupBy { it.batchId }.forEach { (batchId, list) ->
                val last = list.last()
                out += ReportRow(
                    listOf(
                        null, ctx.from, last.series, batches[batchId]?.expiresOn, "Davr boshidagi qoldiq", null, null, null,
                        qty(last.balance, last.unit), last.unit.code, null, null,
                    ),
                    summary = true,
                )
            }
        }
        val inPeriod = ledger.filter { ctx.inPeriod(it.movedOn) }
        inPeriod.forEachIndexed { index, m ->
            out += ReportRow(
                listOf(
                    (index + 1).toLong(), m.movedOn, m.series, batches[m.batchId]?.expiresOn, m.description, m.docNo,
                    if (m.qty > 0) qty(m.qty, m.unit) else null, if (m.qty < 0) qty(-m.qty, m.unit) else null,
                    qty(m.balance, m.unit), m.unit.code, ReportFormat.dateTime(m.createdAt),
                    if (m.operationCancelled) "Keyin bekor qilingan" else "",
                ),
            )
        }
        // Davr oxiridagi qoldiq: har bir partiyaning shu sanagacha bo‘lgan oxirgi qoldig‘i.
        val closing = ledger.filter { ctx.to == null || it.movedOn <= ctx.to }.groupBy { it.batchId }.values.map { it.last() }
        val footer = listOf(
            "Reaktiv: ${reagent.name}" + (if (reagent.formula.isNotEmpty()) " (${reagent.formula})" else "") +
                ", partiyalar: ${batches.size} ta",
            "Operatsiyalar: ${inPeriod.size} ta",
            (if (ctx.to != null) "Davr oxiridagi qoldiq: " else "Joriy qoldiq: ") + totalsText(closing.map { it.unit to it.balance }),
        )
        return Body(columns, out, footer)
    }

    private fun resolveReagent(name: String?): Reagent {
        if (name.isNullOrEmpty()) throw UserError("Tarix hisoboti uchun reaktiv nomini kiriting.", "name")
        val all = services.reagents.list(includeArchived = true)
        val key = nameKey(name)
        all.firstOrNull { nameKey(it.name) == key }?.let { return it }
        val partial = all.filter { nameKey(it.name).contains(key) }
        return when (partial.size) {
            0 -> throw UserError("«$name» nomli reaktiv topilmadi.", "name")
            1 -> partial.single()
            else -> throw UserError(
                "«$name» bir nechta reaktivga mos keladi: " + partial.take(5).joinToString(", ") { "«${it.name}»" } +
                    (if (partial.size > 5) " …" else "") + ". To‘liq nomini kiriting.",
                "name",
            )
        }
    }

    // ---------------------------------------------------------------- yordamchilar

    /** Qatorning o‘z birligida; 400 "4E+2" ko‘rinishida emas (manfiy masshtabsiz). */
    private fun qty(base: Long, unit: MeasureUnit): BigDecimal = Qty.toDecimal(base, unit).let { if (it.scale() < 0) it.setScale(0) else it }

    private fun placeText(department: String?, storage: String): String =
        listOf(department.orEmpty(), storage).filter { it.isNotEmpty() }.joinToString(" / ")

    companion object {
        private val TEXT = ReportColumnType.TEXT
        private val DATE = ReportColumnType.DATE
        private val QTY = ReportColumnType.QTY
        private val INT = ReportColumnType.INT
        private val STATUS = ReportColumnType.STATUS

        private fun col(title: String, type: ReportColumnType, width: Int) = ReportColumn(title, type, width)

        /** "01.10.2026 — 31.10.2026", "01.10.2026 dan", "31.10.2026 gacha". */
        fun period(from: LocalDate?, to: LocalDate?): String = when {
            from != null && to != null -> "${from.display()} — ${to.display()}"
            from != null -> "${from.display()} dan"
            to != null -> "${to.display()} gacha"
            else -> "hammasi"
        }

        /** O‘lchov turlari bo‘yicha jami: "5,5 l · 200 g"; hech narsa bo‘lmasa — "0". Turlar qo‘shilmaydi. */
        fun totalsText(items: List<Pair<MeasureUnit, Long>>): String {
            val map = items.groupBy { it.first.dimension }.mapValues { (_, list) -> list.sumOf { it.second } }
            return if (map.isEmpty()) "0" else Qty.formatTotals(map)
        }
    }
}

// ---------------------------------------------------------------- ranglar va fayllar

/** Excel va PDF ranglari (hex, # siz) — ekrandagi holat ranglari bilan bir xil (ui/theme). */
object ReportColors {
    const val NAVY = "0B2545"
    const val WHITE = "FFFFFF"
    const val TEXT = "1B2430"
    const val MUTED = "5B6575"
    const val BORDER = "C2CAD5"
    const val SUMMARY = "E9EEF5"
    const val ZEBRA = "F7F8FA"

    /** Butun qator uchun och tus. */
    fun tint(state: ExpiryState): String = when (state) {
        ExpiryState.GREEN -> "E6F4EA"
        ExpiryState.YELLOW -> "FFF4CC"
        ExpiryState.RED -> "FDE2E1"
        ExpiryState.EXPIRED -> "F4D3D3"
        ExpiryState.UNKNOWN -> "ECEEF2"
    }

    /** «Holati» katagi foni (to‘yingan rang). */
    fun fill(state: ExpiryState): String = when (state) {
        ExpiryState.GREEN -> "2E9B4C"
        ExpiryState.YELLOW -> "E0A800"
        ExpiryState.RED -> "D92D20"
        ExpiryState.EXPIRED -> "7A1212"
        ExpiryState.UNKNOWN -> "98A2B3"
    }

    /** «Holati» katagi matni: sariq fonda — to‘q, qolganlarida — oq. */
    fun onFill(state: ExpiryState): String = if (state == ExpiryState.YELLOW) TEXT else WHITE
}

object ReportFiles {
    private val FORBIDDEN = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /** Saqlash oynasi uchun taklif: "Muddati o‘tgan reaktivlar 2026-10-29.xlsx" (Windows’da taqiqlangan belgilarsiz). */
    fun suggestedFileName(data: ReportData, extension: String): String {
        val base = data.title.replace(FORBIDDEN, " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "Hisobot" }
        return "$base ${data.generatedAt.toLocalDate()}.${extension.removePrefix(".")}"
    }

    /** Fayl avval vaqtinchalik nusxaga yoziladi, so‘ng [target] o‘rniga qo‘yiladi ([SafeFiles.write]). */
    fun writeAtomically(target: Path, write: (OutputStream) -> Unit): Path = SafeFiles.write(target, write)
}

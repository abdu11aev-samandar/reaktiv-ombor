package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.report.ReportBuilder
import uz.reaktivombor.desktop.report.ReportColumn
import uz.reaktivombor.desktop.report.ReportColumnType
import uz.reaktivombor.desktop.report.ReportData
import uz.reaktivombor.desktop.report.ReportFilter
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.report.ReportRow
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.ComboField
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.EmptyState
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import java.time.LocalDate

/** Hisobot filtrlari (hamma turlar uchun bitta; tur ishlatmaydiganlari ko‘rsatilmaydi va hisobotga berilmaydi). */
@Immutable
private data class ReportsPageFilters(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val name: String = "",
    val series: String = "",
    val departmentId: Long? = null,
    val districtId: Long? = null,
) {
    /** Shu hisobot turiga tegishli qismi. */
    fun toFilter(kind: ReportKind): ReportFilter = ReportFilter(
        from = from.takeIf { kind.periodLabel != null },
        to = to.takeIf { kind.periodLabel != null },
        name = name.trim().ifEmpty { null },
        series = series.trim().ifEmpty { null }?.takeIf { kind.usesSeries },
        departmentId = departmentId?.takeIf { kind.usesDepartment },
        districtId = districtId?.takeIf { kind.usesDistrict },
    )

    fun activeCount(kind: ReportKind): Int = toFilter(kind).let { f ->
        listOf(f.from != null || f.to != null, f.name != null, f.series != null, f.departmentId != null, f.districtId != null).count { it }
    }
}

/** Ko‘rib chiqilgan hisobot va u qaysi tur/filtr bilan tuzilgani (filtr o‘zgarsa — eskirgan). */
@Immutable
private class ReportsPreview(val kind: ReportKind, val filter: ReportFilter, val data: ReportData)

/** Jadval qatori: tartib raqami (kalit) va hisobot qatori. */
@Immutable
private class ReportsLine(val index: Int, val row: ReportRow)

/** Ro‘yxatdagi qisqa izoh (hisobotda nima bor). */
private fun ReportKind.summary(): String = when (this) {
    ReportKind.ALL_REAGENTS -> "Partiyalar: muddati, holati, qoldig‘i"
    ReportKind.BATCHES -> "Nom bo‘yicha guruhlar va jami qatori"
    ReportKind.GROUP_TOTALS -> "Har reaktiv: kirim, sarf, qoldiq"
    ReportKind.BY_DEPARTMENT -> "Bo‘lim qoldig‘i va bo‘limga berilgan"
    ReportKind.DISTRICTS -> "Har berish: hujjat, qabul qiluvchi"
    ReportKind.RECEIPTS -> "Boshlang‘ich va qo‘shimcha kirimlar"
    ReportKind.ISSUES -> "Berish va hisobdan chiqarish qatorlari"
    ReportKind.EXPIRING -> "90 kungacha qolgan, qoldig‘i borlar"
    ReportKind.EXPIRED -> "Qoldig‘i bor, muddati o‘tganlar"
    ReportKind.BELOW_MIN -> "Qoldig‘i minimaldan kam reaktivlar"
    ReportKind.MOVEMENT -> "Davr boshi va oxiri qoldig‘i, harakat"
    ReportKind.HISTORY -> "Bitta reaktivning barcha harakatlari"
}

/** Filtr panelidagi to‘liqroq izoh: davr nimaga qo‘llanadi. */
private fun ReportKind.details(): String = when (this) {
    ReportKind.ALL_REAGENTS -> "Arxivlanmagan barcha partiyalar, «Barcha reaktivlar» jadvali tartibida. Davr — kelgan sana bo‘yicha."
    ReportKind.BATCHES -> "Har reaktiv guruhi oxirida jami qatori (guruhning eng katta birligida). Davr — kelgan sana bo‘yicha."
    ReportKind.GROUP_TOTALS -> "Joriy holat: reaktiv va o‘lchov turi bo‘yicha jami. Davr tanlanmaydi."
    ReportKind.BY_DEPARTMENT -> "Bo‘lim partiyalaridagi qoldiq va bo‘limga berilganlar (qaytarilgani ayirilgan). Davr — berilgan sana bo‘yicha."
    ReportKind.DISTRICTS -> "Har bir berish alohida qator, kiritilgan birlikda. Davr — berilgan sana bo‘yicha."
    ReportKind.RECEIPTS -> "Bekor qilinmagan kirimlar: boshlang‘ich, qo‘shimcha va tuzatilgan. Davr — kirim sanasi bo‘yicha."
    ReportKind.ISSUES -> "Bekor qilinmagan berish va hisobdan chiqarish qatorlari. Davr — berilgan sana bo‘yicha."
    ReportKind.EXPIRING -> "Qoldig‘i bor qizil (30 kungacha) va sariq (31–90 kun) partiyalar. Davr — yaroqlilik muddati bo‘yicha."
    ReportKind.EXPIRED -> "Muddati o‘tgan, qoldig‘i bor partiyalar — hisobdan chiqarishni ko‘rib chiqing. Davr — yaroqlilik muddati bo‘yicha."
    ReportKind.BELOW_MIN -> "Joriy holat: umumiy qoldig‘i belgilangan minimal qoldiqdan kam reaktivlar. Davr tanlanmaydi."
    ReportKind.MOVEMENT -> "Har partiya: davr boshidagi qoldiq, kirim, berilgan, qaytarilgan, hisobdan chiqarilgan va davr oxiridagi qoldiq."
    ReportKind.HISTORY -> "Reaktiv nomi majburiy. Har partiya bo‘yicha qoldiq bilan; davr berilsa — davr boshidagi qoldiq ham."
}

/**
 * «Hisobotlar» (texnik topshiriq §13): chapda 12 ta hisobot turi, o‘ngda tanlangan turning filtrlari (faqat u
 * ishlatadiganlari), «Ko‘rish» — jadval ko‘rinishida (Excel/PDF bilan bir xil ustunlar, holat ranglari, jami qatorlari),
 * «Excelga saqlash» / «PDFga saqlash» — saqlash oynasi, so‘ng «Papkani ochish».
 */
@Composable
fun ReportsPage(state: AppState) {
    var kind by remember { mutableStateOf(ReportKind.ALL_REAGENTS) }
    var filters by remember { mutableStateOf(ReportsPageFilters()) }
    var preview by remember { mutableStateOf<ReportsPreview?>(null) }
    val errors = remember { FormErrors() }

    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val names = remember(tick, filters.name) { state.load(emptyList()) { state.services.reagents.searchNames(filters.name, 30) } }
    val districts = remember(tick) { state.load(emptyList()) { state.services.directory.districts(includeInactive = true) } }
    val request = filters.toFilter(kind)

    fun build(): ReportData = ReportBuilder(state.services).build(kind, request, state.today())

    fun show() {
        val chosen = kind
        state.run({ build() }, form = errors, refresh = false) { data -> preview = ReportsPreview(chosen, request, data) }
    }

    fun save(format: ReportsFileFormat) {
        val chosen = kind
        reportsExportFile(state, format, form = errors, onBuilt = { preview = ReportsPreview(chosen, request, it) }) { build() }
    }

    fun change(next: ReportsPageFilters) {
        filters = next
    }

    PageScaffold(
        title = Page.REPORTS.title,
        subtitle = "Hisobot turini tanlang, filtrlarni belgilang va «Ko‘rish» ni bosing. Excel va PDF fayl shu ko‘rinishda saqlanadi.",
        actions = { ReportsSaveButtons(onSave = { save(it) }, enabled = !state.busy, testTagPrefix = "report-save") },
    ) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportKindList(
                selected = kind,
                onSelect = {
                    if (it != kind) {
                        kind = it
                        preview = null
                        errors.clear()
                    }
                },
                modifier = Modifier.width(272.dp).fillMaxHeight(),
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportFilterCard(
                    state = state,
                    kind = kind,
                    filters = filters,
                    onChange = { change(it) },
                    onClear = {
                        change(ReportsPageFilters())
                        errors.clear()
                    },
                    onShow = { show() },
                    errors = errors,
                    names = names,
                    districts = districts,
                    today = today,
                )
                val shown = preview?.takeIf { it.kind == kind }
                if (shown == null) {
                    Surface(
                        Modifier.fillMaxWidth().weight(1f).testTag("report-empty"),
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.Surface,
                        border = BorderStroke(1.dp, AppColors.Divider),
                    ) {
                        EmptyState(
                            "Hisobot hali tuzilmagan",
                            hint = "Filtrlarni belgilang va «Ko‘rish» ni bosing. «Excelga saqlash» va «PDFga saqlash» hisobotni " +
                                "shu filtrlar bilan darhol tuzib, faylga yozadi.",
                            icon = AppIcons.Reports,
                            actionLabel = "Ko‘rish",
                            onAction = { show() },
                        )
                    }
                } else {
                    ReportPreviewPanel(
                        state = state,
                        preview = shown,
                        stale = shown.filter != request,
                        onRefresh = { show() },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- hisobot turlari ro‘yxati

@Composable
private fun ReportKindList(selected: ReportKind, onSelect: (ReportKind) -> Unit, modifier: Modifier) {
    val scroll = rememberScrollState()
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Divider)) {
        Box {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 4.dp).testTag("report-kinds")) {
                ReportKind.entries.forEach { kind -> ReportKindItem(kind, kind == selected) { onSelect(kind) } }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun ReportKindItem(kind: ReportKind, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) AppColors.PrimarySoft else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag("report-kind-${kind.name}"),
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(if (selected) AppColors.Primary else Color.Transparent))
        Column(Modifier.weight(1f).padding(start = 9.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)) {
            Text(
                kind.title,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AppColors.Primary else AppColors.Text,
            )
            Text(kind.summary(), fontSize = 11.5.sp, lineHeight = 14.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- filtrlar

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportFilterCard(
    state: AppState,
    kind: ReportKind,
    filters: ReportsPageFilters,
    onChange: (ReportsPageFilters) -> Unit,
    onClear: () -> Unit,
    onShow: () -> Unit,
    errors: FormErrors,
    names: List<String>,
    districts: List<Place>,
    today: LocalDate,
) {
    // Maydon xatolari shu yerda o‘qiladi — ular shaklda ko‘rsatilgan deb belgilanadi (pastda takrorlanmaydi).
    val nameError = errors["name"]
    val periodError = if (kind.periodLabel != null) errors["from"] else null
    val departmentError = if (kind.usesDepartment) errors["departmentId"] else null
    val districtError = if (kind.usesDistrict) errors["districtId"] else null
    val active = filters.activeCount(kind)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Divider)) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kind.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy, modifier = Modifier.testTag("report-title"))
                    Text(kind.details(), fontSize = 12.sp, lineHeight = 16.sp, color = AppColors.Muted)
                }
                Spacer(Modifier.width(8.dp))
                AppButton(
                    if (active > 0) "Filtrlarni tozalash ($active)" else "Filtrlarni tozalash",
                    onClick = onClear,
                    kind = ButtonKind.TEXT,
                    icon = AppIcons.Close,
                    enabled = active > 0,
                )
                Spacer(Modifier.width(4.dp))
                AppButton("Ko‘rish", onClick = onShow, icon = AppIcons.Search, enabled = !state.busy, modifier = Modifier.testTag("report-show"))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kind.periodLabel?.let { label ->
                    Column {
                        DateRangeField(
                            filters.from, filters.to,
                            { f, t ->
                                onChange(filters.copy(from = f, to = t))
                                errors.clear("from")
                            },
                            label = label, today = today, fieldWidth = 130.dp, testTag = "report-period",
                        )
                        // Davr xatosini DateRangeField o‘zi ko‘rsatadi; servisniki boshqacha bo‘lsa — ostida.
                        val own = filters.from != null && filters.to != null && filters.from > filters.to
                        if (periodError != null && !own) ReportsFieldError(periodError)
                    }
                }
                ComboField(
                    value = filters.name,
                    onValueChange = {
                        onChange(filters.copy(name = it))
                        errors.clear("name")
                    },
                    suggestions = names,
                    label = "Reaktiv nomi",
                    required = kind.needsName,
                    placeholder = if (kind.needsName) "Reaktivni tanlang" else "Hammasi",
                    error = nameError,
                    modifier = Modifier.width(190.dp),
                    testTag = "report-name",
                )
                if (kind.usesSeries) {
                    AppTextField(
                        filters.series, { onChange(filters.copy(series = it)) },
                        label = "Seriya raqami", placeholder = "Hammasi", onEnter = onShow,
                        modifier = Modifier.width(110.dp), testTag = "report-series",
                    )
                }
                if (kind.usesDepartment) {
                    DepartmentSelect(
                        state, filters.departmentId,
                        {
                            onChange(filters.copy(departmentId = it))
                            errors.clear("departmentId")
                        },
                        placeholder = "Hammasi", allowAdd = false, error = departmentError,
                        modifier = Modifier.width(150.dp), testTag = "report-department",
                    )
                }
                if (kind.usesDistrict) {
                    SelectField(
                        value = districts.firstOrNull { it.id == filters.districtId },
                        options = listOf<Place?>(null) + districts,
                        onSelect = {
                            onChange(filters.copy(districtId = it?.id))
                            errors.clear("districtId")
                        },
                        label = "Tuman",
                        placeholder = "Hammasi",
                        optionLabel = { place -> place?.let { it.name + if (it.active) "" else " (nofaol)" } ?: "— Hammasi —" },
                        error = districtError,
                        modifier = Modifier.width(150.dp),
                        testTag = "report-district",
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsFieldError(text: String) {
    Text(text, fontSize = 12.sp, lineHeight = 15.sp, color = AppColors.Error, modifier = Modifier.padding(top = 3.dp).width(260.dp))
}

// ---------------------------------------------------------------- ko‘rib chiqish

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportPreviewPanel(state: AppState, preview: ReportsPreview, stale: Boolean, onRefresh: () -> Unit, modifier: Modifier) {
    val data = preview.data
    val lines = remember(data) { data.rows.mapIndexed { i, row -> ReportsLine(i, row) } }
    val summaries = data.rows.count { it.summary }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    data.filterLines.joinToString(" · "),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("report-filter-lines"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val org = data.orgName.ifBlank { null }
                    Text(
                        listOfNotNull(org, data.generatedLine).joinToString(" · "),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = AppColors.Muted,
                        maxLines = 1,
                        modifier = Modifier.testTag("report-header-line"),
                    )
                    if (org == null) {
                        Text(" · Tashkilot nomi kiritilmagan — ", fontSize = 12.sp, lineHeight = 16.sp, color = AppColors.Muted, maxLines = 1)
                        Text(
                            "Sozlamalarda kiriting",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = AppColors.Primary,
                            maxLines = 1,
                            modifier = Modifier.clickable(role = Role.Button) { state.navigate(Page.SETTINGS) }.testTag("report-org-settings"),
                        )
                    }
                }
            }
            if (stale) {
                StatusChip("Filtr o‘zgardi", StatusStyles.YELLOW.background, StatusStyles.YELLOW.foreground, dot = null, modifier = Modifier.testTag("report-stale"))
                AppButton("Yangilash", onClick = onRefresh, kind = ButtonKind.TEXT, icon = AppIcons.Refresh, enabled = !state.busy)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "${data.reportsRowCount} ta qator" + when {
                    summaries == 0 -> ""
                    preview.kind == ReportKind.HISTORY -> " + $summaries ta davr boshi qoldig‘i"
                    else -> " + $summaries ta jami"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Navy,
                maxLines = 1,
                modifier = Modifier.testTag("report-row-count"),
            )
        }
        key(preview.kind, data.columns) {
            val columns = remember(data) { reportColumns(data) }
            val layout = rememberAppTableLayout(state, "report-${preview.kind.name.lowercase()}", columns)
            DataTable(
                rows = lines,
                columns = columns,
                rowKey = { it.index },
                layout = layout,
                modifier = Modifier.fillMaxWidth().weight(1f),
                rowBackground = { line -> if (line.row.summary) AppColors.GroupHeader else line.row.state?.style?.background },
                emptyText = "Ma’lumot topilmadi",
                emptyHint = "Tanlangan filtrlarga mos yozuv yo‘q. Filtrlarni o‘zgartirib ko‘ring.",
                testTag = "report-table",
            )
        }
        if (data.footerLines.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = AppColors.SurfaceAlt, border = BorderStroke(1.dp, AppColors.Divider)) {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp).testTag("report-footer"),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    data.footerLines.forEach { line ->
                        Text(line, fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
                    }
                }
            }
        }
    }
}

/** «Birlik» ustuni (miqdorlar shu birlikda) yoki -1. */
private fun ReportData.unitColumn(): Int = columns.indexOfFirst { it.title == "Birlik" }

/**
 * Katak matni: eksportdagidek; miqdor kataklarida — qatorning birligi bilan ("250 ml"), jadval keng bo‘lib «Birlik»
 * ustuni ko‘rinmay qolsa ham miqdor birliksiz ko‘rinmasin.
 */
private fun ReportData.previewText(row: ReportRow, index: Int, unitColumn: Int): String {
    val text = text(row, index)
    if (columns[index].type != ReportColumnType.QTY || text.isEmpty() || unitColumn < 0) return text
    val unit = text(row, unitColumn)
    return if (unit.isEmpty()) text else "$text $unit"
}

/** Hisobot ustunlari jadvalda: Excel/PDF dagi tartib, matn ko‘rinishi bir xil; saralanmaydi (jami qatorlari joyida qolsin). */
private fun reportColumns(data: ReportData): List<TableColumn<ReportsLine>> {
    val used = HashSet<String>()
    val unitColumn = data.unitColumn()
    return data.columns.mapIndexed { i, column ->
        val id = column.title.takeIf { used.add(it) } ?: "${column.title}-$i"
        val align = when (column.type) {
            ReportColumnType.QTY, ReportColumnType.INT, ReportColumnType.DATE -> ColumnAlign.END
            ReportColumnType.TEXT, ReportColumnType.STATUS -> ColumnAlign.START
        }
        TableColumn(
            id = id,
            title = column.title,
            width = previewWidth(data, i, column, unitColumn),
            align = align,
            sortable = false,
            text = { line -> data.previewText(line.row, i, unitColumn) },
            cell = { line -> ReportsCell(data.previewText(line.row, i, unitColumn), line.row, i, column, align) },
        )
    }
}

/**
 * Ustun kengligi: eng uzun katak matni sig‘adigan (ko‘pi bilan 330 dp — qolgani «…», kenglikni sudrab o‘zgartirsa bo‘ladi),
 * sarlavha ikki qatorga sig‘adigan va turi bo‘yicha eng kami.
 */
private fun previewWidth(data: ReportData, index: Int, column: ReportColumn, unitColumn: Int): Dp {
    val longest = data.rows.maxOfOrNull { row ->
        val chars = data.previewText(row, index, unitColumn).length
        if (row.summary) (chars * 1.08f) else chars.toFloat()
    } ?: 0f
    val content = when (column.type) {
        ReportColumnType.STATUS -> longest * 6.8f + 48f
        else -> longest * 7.6f + 22f
    }
    val minimum = when (column.type) {
        ReportColumnType.DATE -> 100f
        ReportColumnType.QTY -> 76f
        ReportColumnType.INT -> 52f
        ReportColumnType.STATUS -> 120f
        ReportColumnType.TEXT -> 72f
    }
    val header = headerChars(column.title) * 7.8f + 26f
    return maxOf(minimum, header, content.coerceAtMost(330f)).dp
}

/** Sarlavha ikki qatorga bo‘linganda uzunroq qatorining eng kichik uzunligi (belgilar). */
private fun headerChars(title: String): Int {
    val words = title.split(' ').filter { it.isNotEmpty() }
    if (words.size < 2) return title.length
    return (1 until words.size).minOf { cut ->
        maxOf(words.take(cut).joinToString(" ").length, words.drop(cut).joinToString(" ").length)
    }
}

@Composable
private fun ReportsCell(text: String, row: ReportRow, index: Int, column: ReportColumn, align: ColumnAlign) {
    val value = row.cells.getOrNull(index)
    if (column.type == ReportColumnType.STATUS && value is ExpiryState) {
        ExpiryChip(value)
        return
    }
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = if (row.summary) FontWeight.Bold else FontWeight.Normal,
        color = AppColors.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = when (align) {
            ColumnAlign.END -> TextAlign.End
            ColumnAlign.CENTER -> TextAlign.Center
            ColumnAlign.START -> TextAlign.Start
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

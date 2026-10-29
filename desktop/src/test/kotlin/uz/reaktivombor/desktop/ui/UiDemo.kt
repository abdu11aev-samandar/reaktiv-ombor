package uz.reaktivombor.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.BarChart
import uz.reaktivombor.desktop.ui.components.Banner
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ChartBar
import uz.reaktivombor.desktop.ui.components.ChartSegment
import uz.reaktivombor.desktop.ui.components.ChipOption
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.ComboField
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.DateField
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.DonutChart
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.FilterChips
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.FormSection
import uz.reaktivombor.desktop.ui.components.GroupExpansion
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.StatCard
import uz.reaktivombor.desktop.ui.components.StatCardRow
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableGrouping
import uz.reaktivombor.desktop.ui.components.TableLayoutState
import uz.reaktivombor.desktop.ui.components.actionsColumn
import uz.reaktivombor.desktop.ui.components.formatChartNumber
import uz.reaktivombor.desktop.ui.components.rememberGroupExpansion
import uz.reaktivombor.desktop.ui.components.rememberTableLayoutState
import uz.reaktivombor.desktop.ui.components.rowNumberColumn
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import uz.reaktivombor.desktop.ui.theme.statusStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Testlar va skrinshotlar uchun namuna ma’lumot (haqiqiy servislar ulanmaguncha). */
val DEMO_TODAY: LocalDate = LocalDate.parse("2026-10-29")

data class DemoBatch(
    val id: Long,
    val name: String,
    val series: String,
    val place: String,
    val produced: LocalDate?,
    val received: LocalDate,
    val expires: LocalDate?,
    val unit: String,
    val receivedQty: Double,
    val issuedQty: Double,
    val remainder: Double,
    val responsible: String,
) {
    val daysLeft: Long? get() = expires?.let { ChronoUnit.DAYS.between(DEMO_TODAY, it) }

    /** core/Expiry.kt dagi qoidaning nusxasi (90 / 30 / 0 kun). */
    val state: String
        get() = when (val d = daysLeft) {
            null -> "UNKNOWN"
            in 91..Long.MAX_VALUE -> "GREEN"
            in 31..90 -> "YELLOW"
            in 0..30 -> "RED"
            else -> "EXPIRED"
        }
}

private fun d(text: String) = LocalDate.parse(text)

val DEMO_BATCHES: List<DemoBatch> = listOf(
    DemoBatch(1, "Etil spirti 96%", "ES-2401", "Gistologiya / 1-shkaf", d("2026-01-10"), d("2026-02-01"), d("2027-09-01"), "l", 20.0, 6.5, 13.5, "Karimova D."),
    DemoBatch(2, "Etil spirti 96%", "ES-2388", "Gistologiya / 1-shkaf", d("2025-11-02"), d("2025-12-01"), d("2026-12-20"), "l", 10.0, 7.0, 3.0, "Karimova D."),
    DemoBatch(3, "Etil spirti 96%", "ES-2310", "Biologiya / sovutgich", d("2025-08-15"), d("2025-09-01"), d("2026-11-15"), "l", 5.0, 4.2, 0.8, "Rahimov A."),
    DemoBatch(4, "Xloroform", "XL-117", "Sud-kimyo / mo‘rili shkaf", d("2025-03-01"), d("2025-04-10"), d("2026-10-01"), "ml", 1000.0, 680.0, 320.0, "To‘xtayev B."),
    DemoBatch(5, "Xloroform", "XL-131", "Sud-kimyo / mo‘rili shkaf", d("2026-05-12"), d("2026-06-01"), d("2028-05-12"), "ml", 1000.0, 0.0, 1000.0, "To‘xtayev B."),
    DemoBatch(6, "Naftol-2", "", "Sud-kimyo / 3-javon", null, d("2024-03-15"), null, "g", 100.0, 60.0, 40.0, "—"),
    DemoBatch(7, "Shavel kislota", "SK-55", "Morfologiya", d("2026-01-15"), d("2026-02-20"), d("2026-12-25"), "g", 250.0, 69.5, 180.5, "Saidova N."),
    DemoBatch(8, "Shavel kislota", "SK-61", "Morfologiya", d("2026-06-01"), d("2026-07-01"), d("2027-06-01"), "g", 500.0, 0.0, 500.0, "Saidova N."),
    DemoBatch(9, "Kumush nitrat", "KN-09", "Sud-kimyo / seyf", d("2025-10-01"), d("2025-11-05"), d("2026-11-20"), "g", 50.0, 38.0, 12.0, "To‘xtayev B."),
    DemoBatch(10, "Gematoksilin (Mayer)", "GM-302", "Gistologiya / 2-shkaf", d("2026-04-01"), d("2026-04-15"), d("2027-04-01"), "ml", 500.0, 120.0, 380.0, "Karimova D."),
    DemoBatch(11, "Gematoksilin (Mayer)", "GM-318", "Gistologiya / 2-shkaf", d("2026-08-01"), d("2026-08-20"), d("2027-08-01"), "ml", 500.0, 0.0, 500.0, "Karimova D."),
    DemoBatch(12, "Eozin", "EO-77", "Gistologiya / 2-shkaf", d("2026-01-20"), d("2026-02-05"), d("2027-01-10"), "ml", 250.0, 90.0, 160.0, "Karimova D."),
    DemoBatch(13, "Formalin 10%", "FR-201", "Morfologiya / ombor", d("2025-06-01"), d("2025-06-20"), d("2026-09-30"), "l", 25.0, 21.0, 4.0, "Saidova N."),
    DemoBatch(14, "Formalin 10%", "FR-219", "Morfologiya / ombor", d("2025-12-01"), d("2025-12-15"), d("2026-11-10"), "l", 25.0, 9.0, 16.0, "Saidova N."),
)

private fun qty(v: Double) = formatChartNumber(v)

private fun date(v: LocalDate?) = UzDates.format(v)

/** Namuna jadval ustunlari — ReagentsPage tartibida. */
fun demoColumns(onAction: (String, DemoBatch) -> Unit = { _, _ -> }): List<TableColumn<DemoBatch>> = listOf(
    rowNumberColumn(),
    textColumn("name", "Reaktiv nomi", 190.dp) { it.name },
    textColumn("series", "Seriya raqami", 110.dp) { it.series.ifEmpty { "—" } },
    textColumn("place", "Bo‘lim / saqlash joyi", 180.dp) { it.place },
    textColumn("produced", "Ishlab chiqarilgan sana", 124.dp, ColumnAlign.END, sortKey = { it.produced }) { date(it.produced) },
    textColumn("received", "Kelgan sana", 100.dp, ColumnAlign.END, sortKey = { it.received }) { date(it.received) },
    textColumn("expires", "Yaroqlilik muddati", 110.dp, ColumnAlign.END, sortKey = { it.expires }) { date(it.expires) },
    textColumn("days", "Qolgan kunlar", 90.dp, ColumnAlign.END, sortKey = { it.daysLeft }) { it.daysLeft?.toString() ?: "—" },
    TableColumn(
        id = "state", title = "Holati", width = 180.dp, sortKey = { b -> StatusStyles.all.indexOfFirst { it.key == b.state } },
        cell = { b -> StatusChip(statusStyle(b.state)) },
    ),
    textColumn("receivedQty", "Kelgan miqdor", 100.dp, ColumnAlign.END, sortKey = { it.receivedQty }) { qty(it.receivedQty) },
    textColumn("issued", "Jami berilgan miqdor", 110.dp, ColumnAlign.END, sortKey = { it.issuedQty }) { qty(it.issuedQty) },
    textColumn("remainder", "Joriy qoldiq", 100.dp, ColumnAlign.END, sortKey = { it.remainder }) { qty(it.remainder) },
    textColumn("unit", "O‘lchov birligi", 80.dp, ColumnAlign.CENTER) { it.unit },
    textColumn("responsible", "Mas’ul xodim", 130.dp) { it.responsible },
    actionsColumn(width = 150.dp) { b ->
        listOf(
            RowAction("Tahrirlash", AppIcons.Edit) { onAction("edit", b) },
            RowAction("Kirim", AppIcons.Receipts) { onAction("receipt", b) },
            RowAction("Berish", AppIcons.Issues) { onAction("issue", b) },
            RowAction("Tarix", AppIcons.History) { onAction("history", b) },
            RowAction("Arxivlash", AppIcons.Archive) { onAction("archive", b) },
            RowAction("O‘chirish", AppIcons.Delete, danger = true) { onAction("delete", b) },
        )
    },
)

/** Guruh sarlavhasi: nom, partiyalar soni, jami qoldiq va holatlar soni (yashil guruh ichidagi qizil partiya ko‘rinadi). */
@Composable
fun DemoGroupHeader(rows: List<DemoBatch>) {
    val first = rows.first()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(first.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
        Text("${rows.size} ta partiya", fontSize = 12.5.sp, color = AppColors.Muted)
        Text("Jami qoldiq: ${qty(rows.sumOf { it.remainder })} ${first.unit}", fontSize = 12.5.sp, color = AppColors.Text)
        StatusStyles.all.forEach { style ->
            val count = rows.count { it.state == style.key }
            if (count > 0) StatusChip(style, text = "${style.label}: $count")
        }
    }
}

@Composable
fun DemoReagentsTable(
    layout: TableLayoutState,
    modifier: Modifier = Modifier,
    expansion: GroupExpansion = rememberGroupExpansion(),
    rows: List<DemoBatch> = DEMO_BATCHES,
    onAction: (String, DemoBatch) -> Unit = { _, _ -> },
) {
    DataTable(
        rows = rows,
        columns = demoColumns(onAction),
        rowKey = { it.id },
        layout = layout,
        modifier = modifier,
        rowBackground = { statusStyle(it.state).background },
        grouping = TableGrouping(key = { it.name }, header = { DemoGroupHeader(it.rows) }),
        expansion = expansion,
    )
}

/** «Barcha reaktivlar» ko‘rinishidagi namuna sahifa. */
@Composable
fun DemoReagentsPage() {
    val columns = demoColumns()
    val layout = rememberTableLayoutState(columns)
    val expansion = rememberGroupExpansion()
    var search by remember { mutableStateOf("") }
    var department by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf("all") }
    PageScaffold(
        title = "Barcha reaktivlar",
        subtitle = "${DEMO_BATCHES.map { it.name }.distinct().size} ta reaktiv, ${DEMO_BATCHES.size} ta partiya",
        actions = {
            AppButton("Hammasini ochish", onClick = { expansion.expandAll() }, kind = ButtonKind.TEXT, icon = AppIcons.ExpandAll)
            AppButton("Hammasini yopish", onClick = { expansion.collapseAll() }, kind = ButtonKind.TEXT, icon = AppIcons.CollapseAll)
            AppButton("Excelga saqlash", onClick = {}, kind = ButtonKind.SECONDARY, icon = AppIcons.Excel)
            AppButton("Reaktiv qo‘shish", onClick = {}, icon = AppIcons.Add)
        },
    ) {
        FilterBar(onClear = { search = ""; department = null }, activeCount = (if (search.isNotEmpty()) 1 else 0) + (if (department != null) 1 else 0)) {
            AppTextField(search, { search = it }, label = "Qidirish", placeholder = "Nomi yoki seriyasi", leadingIcon = AppIcons.Search, modifier = Modifier.width(240.dp))
            SelectField(department, listOf("Gistologiya", "Biologiya", "Sud-kimyo", "Morfologiya"), { department = it }, label = "Bo‘lim", placeholder = "Hammasi", modifier = Modifier.width(170.dp))
            DateRangeField(null, null, { _, _ -> }, label = "Yaroqlilik muddati", today = DEMO_TODAY, fieldWidth = 136.dp)
        }
        FilterChips(
            options = listOf(ChipOption("all", "Hammasi", DEMO_BATCHES.size)) + StatusStyles.all.map { s ->
                ChipOption(s.key, s.label, DEMO_BATCHES.count { it.state == s.key }, s.accent)
            },
            selected = state,
            onSelect = { state = it },
        )
        DemoReagentsTable(layout, Modifier.fillMaxWidth().weight(1f), expansion)
    }
}

/** Bosh sahifa ko‘rinishidagi namuna: kartalar, banner, halqa va ustunli grafiklar. */
@Composable
fun DemoDashboard(onCardClick: (String) -> Unit = {}) {
    PageScaffold(title = "Bosh sahifa", subtitle = "Ombor holati — ${UzDates.format(DEMO_TODAY)}") {
        Banner(
            "2 ta partiyaning yaroqlilik muddati o‘tgan, omborda qoldig‘i bor. Ularni hisobdan chiqaring yoki arxivlang.",
            severity = Severity.ERROR,
            title = "Muddati o‘tgan reaktivlar",
            actionLabel = "Ko‘rish",
            onAction = {},
        )
        StatCardRow {
            val card = Modifier.weight(1f).fillMaxHeight()
            StatCard("8", "Reaktiv turlari", card, icon = AppIcons.Reagents, onClick = { onCardClick("types") }, testTag = "card-types")
            StatCard("14", "Faol partiyalar", card, icon = AppIcons.Stock, accent = AppColors.Navy)
            StatCard("5", "Muddati yaqinlashgan", card, icon = AppIcons.Expiry, accent = StatusStyles.YELLOW.accent, hint = "3 ta qizil, 2 ta sariq")
            StatCard("2", "Muddati o‘tgan", card, icon = AppIcons.Warning, accent = StatusStyles.EXPIRED.accent)
            StatCard("3", "Minimal qoldiqdan kam", card, icon = AppIcons.Issues, accent = StatusStyles.RED.accent)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSection("Yaroqlilik holati", Modifier.weight(1.1f)) {
                DonutChart(
                    StatusStyles.all.map { s -> ChartSegment(s.label, DEMO_BATCHES.count { it.state == s.key }.toDouble(), s.accent) },
                    centerLabel = "partiya",
                )
            }
            FormSection("Bo‘limlar bo‘yicha qoldiq", Modifier.weight(1f)) {
                BarChart(
                    listOf(
                        ChartBar("Gistologiya", 42.0, "42 ta"),
                        ChartBar("Sud-kimyo", 31.0, "31 ta"),
                        ChartBar("Morfologiya", 18.0, "18 ta"),
                        ChartBar("Biologiya", 9.0, "9 ta"),
                    ),
                    labelWidth = 110.dp,
                )
            }
            FormSection("Tumanlarga berilgan", Modifier.weight(1f)) {
                BarChart(
                    listOf(
                        ChartBar("Chilonzor tumani", 12.5, "12,5 l"),
                        ChartBar("Yunusobod tumani", 8.0, "8 l"),
                        ChartBar("Mirzo Ulug‘bek tumani", 5.25, "5,25 l"),
                        ChartBar("Sergeli tumani", 1.5, "1,5 l"),
                    ),
                    color = AppColors.Navy,
                    labelWidth = 130.dp,
                )
            }
        }
    }
}

/** Shakl maydonlari namunasi (taqvim, avtomatik to‘ldirish, birlik). */
@Composable
fun DemoForm() {
    var name by remember { mutableStateOf("Etil") }
    var dept by remember { mutableStateOf<String?>("Gistologiya") }
    var amount by remember { mutableStateOf("1 250,5") }
    var unit by remember { mutableStateOf("ml") }
    var expires by remember { mutableStateOf<LocalDate?>(LocalDate.parse("2027-10-17")) }
    var produced by remember { mutableStateOf<LocalDate?>(null) }
    var from by remember { mutableStateOf<LocalDate?>(LocalDate.parse("2026-10-01")) }
    var to by remember { mutableStateOf<LocalDate?>(LocalDate.parse("2026-09-01")) }
    PageScaffold(title = "Reaktiv qo‘shish", subtitle = "Yangi partiya kartasi") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSection("Reaktiv kartasi", description = "Majburiy maydonlar * bilan belgilangan") {
                FormRow {
                    ComboField(name, { name = it }, DEMO_BATCHES.map { it.name }, label = "Reaktiv nomi", required = true, allowNew = true, modifier = Modifier.weight(2f), testTag = "demo-name")
                    AppTextField("ES-2402", {}, label = "Seriya raqami", required = true, modifier = Modifier.weight(1f))
                    SelectField(dept, listOf("Gistologiya", "Biologiya", "Sud-kimyo", "Morfologiya"), { dept = it }, label = "Bo‘lim", modifier = Modifier.weight(1f))
                }
                FormRow {
                    QtyField(amount, { amount = it }, unit, { unit = it }, listOf("l", "ml"), label = "Kelgan miqdor", required = true, modifier = Modifier.weight(1f))
                    DateField(produced, { produced = it }, label = "Ishlab chiqarilgan sana", today = DEMO_TODAY, maxDate = DEMO_TODAY, modifier = Modifier.weight(1f), testTag = "demo-produced")
                    DateField(expires, { expires = it }, label = "Yaroqlilik muddati", required = true, today = DEMO_TODAY, modifier = Modifier.weight(1f), testTag = "demo-expires")
                    AppTextField("31.02.2026", {}, label = "Xato namunasi", error = "Sana noto‘g‘ri. Masalan: 29.10.2026", modifier = Modifier.weight(1f))
                }
                FormRow {
                    DateRangeField(from, to, { f, t -> from = f; to = t }, today = DEMO_TODAY, label = "Hisobot davri")
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.padding(top = 20.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton("Bekor qilish", onClick = {}, kind = ButtonKind.SECONDARY)
                            AppButton("Saqlash", onClick = {}, icon = AppIcons.Check)
                        }
                    }
                }
            }
            Banner("Partiya saqlandi: Etil spirti 96%, ES-2402.", severity = Severity.SUCCESS, actionLabel = "Yana qo‘shish", onAction = {}, onDismiss = {})
            Banner("Bu reaktivning muddati 30 kundan kam qolgan.", severity = Severity.WARNING)
            Banner("Excel fayldan 12 ta qator o‘qildi.", severity = Severity.INFO, onDismiss = {})
        }
    }
}

/** Hamma belgilar — ko‘rib chiqish uchun. */
@Composable
fun DemoIcons() {
    val icons = Page.entries.map { it.title to it.icon } + listOf(
        "Add" to AppIcons.Add, "Edit" to AppIcons.Edit, "Delete" to AppIcons.Delete, "Archive" to AppIcons.Archive,
        "Filter" to AppIcons.Filter, "Columns" to AppIcons.Columns, "ExpandAll" to AppIcons.ExpandAll,
        "CollapseAll" to AppIcons.CollapseAll, "Download" to AppIcons.Download, "Upload" to AppIcons.Upload,
        "Folder" to AppIcons.Folder, "Excel" to AppIcons.Excel, "Document" to AppIcons.Document, "Undo" to AppIcons.Undo,
        "Cancel" to AppIcons.Cancel, "Error" to AppIcons.Error, "Warning" to AppIcons.Warning, "Info" to AppIcons.Info,
        "Success" to AppIcons.Success, "Bell" to AppIcons.Bell, "Calendar" to AppIcons.Calendar, "Inbox" to AppIcons.Inbox,
    )
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        icons.chunked(6).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { (name, icon) ->
                    Row(Modifier.width(210.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(icon, null, Modifier.size(48.dp), tint = AppColors.Navy)
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.material3.Icon(icon, null, Modifier.size(20.dp), tint = AppColors.Primary)
                        Spacer(Modifier.width(6.dp))
                        Text(name, fontSize = 12.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.report.ReportFilter
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.IssueItemRow
import uz.reaktivombor.desktop.service.PlaceHistoryKind
import uz.reaktivombor.desktop.service.PlaceHistoryRow
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.service.PlacesQueries
import uz.reaktivombor.desktop.service.ReagentQtyTotal
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.EmptyState
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.GroupInfo
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableGrouping
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import java.time.LocalDate

/** Bo‘lim sahifasidagi varaqlar. */
private enum class DepartmentsPageTab(val title: String) {
    BATCHES("Bo‘limdagi partiyalar"),
    ISSUED("Bo‘limga berilgan reaktivlar"),
    HISTORY("Sarf tarixi"),
}

/**
 * «Bo‘limlar bo‘yicha hisob» (texnik topshiriq §4): chapda bo‘limlar ro‘yxati (qo‘shish, nomini o‘zgartirish,
 * faol/nofaol), o‘ngda tanlangan bo‘lim — davr va reaktiv filtri, jamlar kartalari, bo‘limdagi partiyalar,
 * bo‘limga berilgan reaktivlar (reaktiv bo‘yicha jami + sanali ro‘yxat), sarf tarixi va Excel/PDF hisobot.
 */
@Composable
fun DepartmentsPage(state: AppState) {
    val intentId = remember { (state.intent as? PageIntent.DepartmentOf)?.departmentId }
    var selectedId by remember { mutableStateOf(intentId) }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var reagentText by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(DepartmentsPageTab.BATCHES) }
    var historyBatch by remember { mutableStateOf<Long?>(null) }

    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val places = remember(tick) { state.load(emptyList()) { state.services.directory.departments(includeInactive = true) } }
    val current = places.firstOrNull { it.id == selectedId } ?: places.firstOrNull { it.active } ?: places.firstOrNull()
    // Ro‘yxatdagi izoh: bo‘limga biriktirilgan (arxivlanmagan) partiyalar soni — «Bo‘limdagi partiyalar» kartasi bilan bir xil.
    val ownedCounts = remember(tick, today) {
        state.load(emptyMap()) {
            state.services.queries.batchRows(BatchFilter(), today).groupingBy { it.departmentId }.eachCount()
        }
    }
    val placesQueries = remember(state.services) { PlacesQueries(state.services) }
    // Noto‘g‘ri davr (boshi oxiridan keyin) — DateRangeField xatoni ko‘rsatadi, ro‘yxatlar davrsiz.
    val periodOk = from == null || to == null || from!! <= to!!
    val qFrom = from.takeIf { periodOk }
    val qTo = to.takeIf { periodOk }
    val summary = remember(tick, today, current?.id, qFrom, qTo, reagentText) {
        current?.let { d -> state.load(null) { placesQueries.departmentSummary(d.id, qFrom, qTo, reagentText, today) } }
    }
    val history = remember(tick, current?.id, qFrom, qTo, reagentText) {
        current?.let { d -> state.load(emptyList()) { placesQueries.departmentHistory(d.id, qFrom, qTo, reagentText) } }.orEmpty()
    }

    PageScaffold(
        title = Page.DEPARTMENTS.title,
        subtitle = "Bo‘limlarga biriktirilgan partiyalar va bo‘limlarga berilgan reaktivlar",
        actions = {
            PlacesReportButtons(state, enabled = current != null, testTag = "department-report") { ext ->
                current?.let { d ->
                    placesExport(state, ReportKind.BY_DEPARTMENT, ReportFilter(from, to, name = reagentText.ifBlank { null }, departmentId = d.id), ext)
                }
            }
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlacesListPanel(
                state = state,
                kind = PlaceKind.DEPARTMENT,
                places = places,
                selectedId = current?.id,
                onSelect = { id -> if (id != null) selectedId = id },
                testTag = "departments",
                modifier = Modifier.width(220.dp).fillMaxHeight(),
                secondary = { place -> ownedCounts[place.id]?.let { "$it ta partiya" } ?: "partiya yo‘q" },
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current == null || summary == null) {
                    EmptyState("Bo‘lim tanlanmagan", hint = "Chapdagi ro‘yxatdan bo‘limni tanlang yoki «Qo‘shish» bilan yangisini kiriting.")
                    return@Column
                }
                PlacesDetailHeader(
                    title = current.name,
                    inactive = !current.active,
                    description = "Bo‘limdagi partiyalar — bugungi holat; bo‘limga berilganlar — ${placesPeriodText(qFrom, qTo)}.",
                    testTag = "department-header",
                )
                FilterBar(
                    onClear = {
                        from = null
                        to = null
                        reagentText = ""
                    },
                    activeCount = listOf(from != null || to != null, reagentText.isNotBlank()).count { it },
                ) {
                    DateRangeField(
                        from, to, { f, t -> from = f; to = t },
                        label = "Davr (bo‘limga berilganlar)", today = today, fieldWidth = 128.dp, testTag = "department-period",
                    )
                    AppTextField(
                        reagentText, { reagentText = it },
                        label = "Reaktiv", placeholder = "Nomi bo‘yicha", leadingIcon = AppIcons.Search,
                        modifier = Modifier.width(220.dp), testTag = "department-reagent",
                    )
                }
                DepartmentCards(summary.ownedBatches, summary.issuedTo, summary.history.size)
                PlacesTabs(
                    tabs = listOf(
                        DepartmentsPageTab.BATCHES.title to summary.ownedBatches.size,
                        DepartmentsPageTab.ISSUED.title to summary.history.size,
                        DepartmentsPageTab.HISTORY.title to history.size,
                    ),
                    selected = tab.ordinal,
                    onSelect = { tab = DepartmentsPageTab.entries[it] },
                    testTag = "department-tab",
                )
                val tableModifier = Modifier.fillMaxWidth().weight(1f)
                when (tab) {
                    DepartmentsPageTab.BATCHES -> DepartmentBatchesTable(state, summary.ownedBatches, tableModifier, reagentText.isNotBlank()) { historyBatch = it }
                    DepartmentsPageTab.ISSUED -> DepartmentIssuedTable(state, summary.history, summary.issuedTo, tableModifier) { historyBatch = it }
                    DepartmentsPageTab.HISTORY -> DepartmentHistoryTable(state, history, tableModifier) { historyBatch = it }
                }
            }
        }
    }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

@Composable
private fun DepartmentCards(owned: List<BatchRow>, issuedTo: List<ReagentQtyTotal>, issueCount: Int) {
    val expired = owned.count { it.state == ExpiryState.EXPIRED && it.remainder > 0 }
    val withStock = owned.count { it.remainder > 0 }
    val writtenOff = placesTotals(owned.filter { it.writtenOffQty > 0 }, { it.dimension }, { it.writtenOffQty })
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val card = Modifier.weight(1f).fillMaxHeight()
        PlacesTotalsCard(
            "Bo‘limdagi partiyalar", listOf("${owned.size} ta"), card,
            hint = if (expired > 0) "$expired tasi muddati o‘tgan" else "qoldig‘i bor: $withStock ta",
            hintColor = if (expired > 0) StatusStyles.EXPIRED.foreground else AppColors.Muted,
            testTag = "department-card-batches",
        )
        PlacesTotalsCard(
            "Qabul qilingan", placesTotalsLines(placesTotals(owned, { it.dimension }, { it.receivedQty })), card,
            hint = "bo‘lim partiyalariga", testTag = "department-card-received",
        )
        PlacesTotalsCard(
            "Sof berilgan", placesTotalsLines(placesTotals(owned, { it.dimension }, { it.issuedQty })), card,
            hint = if (writtenOff.isNotEmpty()) "hisobdan chiqarilgan: ${totalsText(writtenOff)}" else "qaytarilgani ayirilgan",
            testTag = "department-card-issued",
        )
        PlacesTotalsCard(
            "Joriy qoldiq", placesTotalsLines(placesTotals(owned, { it.dimension }, { it.remainder })), card,
            hint = "bugungi holat", valueColor = AppColors.Primary, testTag = "department-card-remainder",
        )
        PlacesTotalsCard(
            "Bo‘limga berilgan", placesTotalsLines(placesTotals(issuedTo, { it.dimension }, { it.net })), card,
            hint = "sof · $issueCount ta berish", testTag = "department-card-issued-to",
        )
    }
}

// ---------------------------------------------------------------- bo‘limdagi partiyalar

@Composable
private fun DepartmentBatchesTable(state: AppState, rows: List<BatchRow>, modifier: Modifier, filtered: Boolean, onHistory: (Long) -> Unit) {
    val columns = remember { departmentBatchColumns() }
    val layout = rememberAppTableLayout(state, "department-batches", columns)
    DataTable(
        rows = rows,
        columns = columns,
        rowKey = { it.batchId },
        layout = layout,
        modifier = modifier,
        rowBackground = { it.state.style.background },
        onRowDoubleClick = { onHistory(it.batchId) },
        emptyText = if (filtered) "Filtrga mos partiya yo‘q" else "Bu bo‘limga biriktirilgan partiya yo‘q",
        emptyHint = "Partiya «Reaktiv qo‘shish» yoki tahrirlash oynasida bo‘limga biriktiriladi.",
        testTag = "department-batches",
    )
}

private fun departmentBatchColumns(): List<TableColumn<BatchRow>> = listOf(
    textColumn("name", "Reaktiv nomi", 160.dp) { it.name },
    textColumn("series", "Seriya raqami", 86.dp) { it.series.ifBlank { "—" } },
    textColumn("expires", "Yaroqlilik muddati", 98.dp, ColumnAlign.END, sortKey = { it.expiresOn }) { dateText(it.expiresOn) },
    TableColumn(
        id = "state", title = "Holati", width = 172.dp, sortKey = { it.state.severity }, text = { it.state.label },
        cell = { row -> ExpiryChip(row.state) },
    ),
    textColumn("received", "Kelgan", 84.dp, ColumnAlign.END, sortKey = { it.receivedQty }) { qtyNumber(it.receivedQty, it.unit) },
    // Partiyadan berilgani qaytarilgani ayirilgan holda (kelgan − sof berilgan − hisobdan chiqarilgan = qoldiq).
    textColumn("issued", "Sof berilgan", 92.dp, ColumnAlign.END, sortKey = { it.issuedQty }) { qtyNumber(it.issuedQty, it.unit) },
    TableColumn(
        id = "remainder", title = "Joriy qoldiq", width = 104.dp, align = ColumnAlign.END, sortKey = { it.remainder },
        text = { qtyNumber(it.remainder, it.unit) },
        cell = { row -> PlacesQtyText(qtyNumber(row.remainder, row.unit), color = if (row.remainder == 0L) AppColors.Muted else AppColors.Text) },
    ),
    textColumn("unit", "Birlik", 56.dp, ColumnAlign.CENTER) { it.unit.code },
    textColumn("days", "Qolgan kunlar", 100.dp, ColumnAlign.END, sortKey = { it.daysLeft }, visibleByDefault = false) { daysText(it.daysLeft) },
    textColumn("writtenOff", "Hisobdan chiqarilgan", 100.dp, ColumnAlign.END, sortKey = { it.writtenOffQty }, visibleByDefault = false) {
        qtyNumber(it.writtenOffQty, it.unit)
    },
    textColumn("storage", "Saqlash joyi", 130.dp, visibleByDefault = false) { it.storageLocation.ifBlank { "—" } },
    textColumn("responsible", "Mas’ul xodim", 130.dp, visibleByDefault = false) { it.responsible.ifBlank { "—" } },
)

// ---------------------------------------------------------------- bo‘limga berilgan

private fun issuedGroupKey(reagentId: Long, dimension: uz.reaktivombor.desktop.core.Dimension): String = "$reagentId-${dimension.name}"

@Composable
private fun DepartmentIssuedTable(
    state: AppState,
    items: List<IssueItemRow>,
    totals: List<ReagentQtyTotal>,
    modifier: Modifier,
    onHistory: (Long) -> Unit,
) {
    val columns = remember { departmentIssuedColumns() }
    val layout = rememberAppTableLayout(state, "department-issued", columns)
    // Reaktiv nomi bo‘yicha guruhlar (bir xil nom doim yonma-yon), guruh ichida — yangisi birinchi.
    val rows = remember(items) {
        items.sortedWith(compareBy<IssueItemRow>({ nameKey(it.name) }, { it.unit.dimension.ordinal }).thenByDescending { it.issuedOn }.thenByDescending { it.id })
    }
    val byKey = remember(totals) { totals.associateBy { issuedGroupKey(it.reagentId, it.dimension) } }
    DataTable(
        rows = rows,
        columns = columns,
        rowKey = { it.id },
        layout = layout,
        modifier = modifier,
        grouping = TableGrouping(key = { issuedGroupKey(it.reagentId, it.unit.dimension) }, header = { info -> IssuedGroupHeader(info, byKey[info.key]) }),
        onRowDoubleClick = { onHistory(it.batchId) },
        emptyText = "Bu davrda bo‘limga reaktiv berilmagan",
        emptyHint = "Davr yoki reaktiv filtrini o‘zgartiring.",
        testTag = "department-issued",
    )
}

@Composable
private fun IssuedGroupHeader(info: GroupInfo<IssueItemRow>, total: ReagentQtyTotal?) {
    val first = info.rows.first()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(first.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (total != null) {
            Text("${total.items} ta berish", fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1)
            Text("Berilgan: ${qtyText(total.issued, total.unit)}", fontSize = 12.5.sp, color = AppColors.Text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            if (total.returned > 0) Text("Qaytarilgan: ${qtyText(total.returned, total.unit)}", fontSize = 12.5.sp, color = AppColors.Text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text("Sof berilgan: ${qtyText(total.net, total.unit)}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text(
                if (total.firstDate == total.lastDate) dateText(total.firstDate) else "${dateText(total.firstDate)} — ${dateText(total.lastDate)}",
                fontSize = 12.5.sp,
                color = AppColors.Muted,
                maxLines = 1,
            )
        }
    }
}

private fun departmentIssuedColumns(): List<TableColumn<IssueItemRow>> = listOf(
    textColumn("date", "Sana", 92.dp, ColumnAlign.END, sortKey = { it.issuedOn }) { dateText(it.issuedOn) },
    textColumn("doc", "Hujjat", 84.dp) { it.docNo.ifBlank { "—" } },
    textColumn("series", "Seriya raqami", 96.dp) { it.series.ifBlank { "—" } },
    textColumn("expires", "Yaroqlilik muddati", 98.dp, ColumnAlign.END, sortKey = { it.expiresOn }) { dateText(it.expiresOn) },
    textColumn("qty", "Berilgan", 84.dp, ColumnAlign.END, sortKey = { it.qty }) { Qty.format(it.qty, it.enteredUnit) },
    textColumn("returned", "Qaytarilgan", 116.dp, ColumnAlign.END, sortKey = { it.returnedQty }) {
        if (it.returnedQty > 0) Qty.format(it.returnedQty, it.enteredUnit) else "—"
    },
    TableColumn(
        id = "net", title = "Sof berilgan", width = 96.dp, align = ColumnAlign.END, sortKey = { it.netQty },
        text = { Qty.format(it.netQty, it.enteredUnit) },
        cell = { row -> PlacesQtyText(Qty.format(row.netQty, row.enteredUnit)) },
    ),
    textColumn("unit", "Birlik", 56.dp, ColumnAlign.CENTER) { it.enteredUnit.code },
    textColumn("receiver", "Qabul qiluvchi", 130.dp) { it.receiver.ifBlank { "—" } },
    textColumn("note", "Izoh", 180.dp, visibleByDefault = false) { it.note.ifBlank { it.issueNote.ifBlank { "—" } } },
)

// ---------------------------------------------------------------- sarf tarixi

@Composable
private fun DepartmentHistoryTable(state: AppState, rows: List<PlaceHistoryRow>, modifier: Modifier, onHistory: (Long) -> Unit) {
    val columns = remember { departmentHistoryColumns() }
    val layout = rememberAppTableLayout(state, "department-history", columns)
    DataTable(
        rows = rows,
        columns = columns,
        rowKey = { it.key },
        layout = layout,
        modifier = modifier,
        onRowDoubleClick = { onHistory(it.batchId) },
        emptyText = "Bu davrda bo‘limga berish yoki qaytarish bo‘lmagan",
        testTag = "department-history",
    )
}

private fun departmentHistoryColumns(): List<TableColumn<PlaceHistoryRow>> = listOf(
    textColumn("date", "Sana", 92.dp, ColumnAlign.END, sortKey = { it.date }) { dateText(it.date) },
    TableColumn(
        id = "kind", title = "Operatsiya", width = 110.dp, sortKey = { it.kind.ordinal }, text = { it.kind.label },
        cell = { row ->
            when (row.kind) {
                PlaceHistoryKind.ISSUE -> StatusChip(row.kind.label, AppColors.PrimarySoft, AppColors.Navy, dot = null)
                PlaceHistoryKind.RETURN -> StatusChip(row.kind.label, StatusStyles.GREEN.background, StatusStyles.GREEN.foreground, dot = null)
            }
        },
    ),
    textColumn("name", "Reaktiv nomi", 170.dp) { it.name },
    textColumn("series", "Seriya raqami", 96.dp) { it.series.ifBlank { "—" } },
    textColumn("qty", "Miqdor", 90.dp, ColumnAlign.END, sortKey = { it.qty }) { Qty.format(it.qty, it.unit) },
    textColumn("unit", "Birlik", 56.dp, ColumnAlign.CENTER) { it.unit.code },
    textColumn("doc", "Hujjat", 84.dp) { it.docNo.ifBlank { "—" } },
    textColumn("person", "Xodim", 130.dp) { it.person.ifBlank { "—" } },
    textColumn("note", "Izoh / sabab", 180.dp) { it.note.ifBlank { "—" } },
)

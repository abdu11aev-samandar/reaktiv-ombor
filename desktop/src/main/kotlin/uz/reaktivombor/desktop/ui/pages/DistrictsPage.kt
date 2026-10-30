package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.DuplicateOperationError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.expiryState
import uz.reaktivombor.desktop.report.ReportFilter
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.service.DistrictReagentTotal
import uz.reaktivombor.desktop.service.DistrictSummary
import uz.reaktivombor.desktop.service.IssueItemRow
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.service.PlacesQueries
import uz.reaktivombor.desktop.service.ReturnInput
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.Banner
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.DateField
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.EmptyState
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.actionsColumn
import uz.reaktivombor.desktop.ui.components.rememberTableSelection
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import java.time.LocalDate

/** Tuman sahifasidagi varaqlar. */
private enum class DistrictsPageTab(val title: String) {
    ITEMS("Berishlar"),
    PER_REAGENT("Reaktivlar bo‘yicha jami"),
}

/**
 * «Tumanlarga berilgan reaktivlar» (texnik topshiriq §8): chapda tumanlar ro‘yxati («Barcha tumanlar», qo‘shish,
 * nomini o‘zgartirish, faol/nofaol), o‘ngda davr va reaktiv filtri, tumanlar bo‘yicha jamlar, har bir berish alohida
 * qator (bir tumanga turli kunlardagi berishlar alohida), reaktivlar bo‘yicha jami, «Qaytarish» va Excel/PDF hisobot.
 */
@Composable
fun DistrictsPage(state: AppState) {
    val intentId = remember { (state.intent as? PageIntent.DistrictOf)?.districtId }
    var selectedId by remember { mutableStateOf(intentId) }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var reagentText by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(DistrictsPageTab.ITEMS) }
    var returning by remember { mutableStateOf<Long?>(null) }
    var historyBatch by remember { mutableStateOf<Long?>(null) }
    val selection = rememberTableSelection()

    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val places = remember(tick) { state.load(emptyList()) { state.services.directory.districts(includeInactive = true) } }
    val current = places.firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId, places) { if (selectedId != null && current == null && places.isNotEmpty()) selectedId = null }
    val placesQueries = remember(state.services) { PlacesQueries(state.services) }
    val allTime = remember(tick) { state.load(null) { placesQueries.districtSummary() } }
    val itemCounts = allTime?.districts.orEmpty().associate { it.districtId to it.items }
    val periodOk = from == null || to == null || from!! <= to!!
    val qFrom = from.takeIf { periodOk }
    val qTo = to.takeIf { periodOk }
    val summary = remember(tick, current?.id, qFrom, qTo, reagentText) {
        state.load(DistrictSummary(emptyList(), emptyList(), emptyList())) { placesQueries.districtSummary(current?.id, qFrom, qTo, reagentText) }
    }
    val items = summary.items
    val selected = items.firstOrNull { it.id == selection.key }

    fun actionsFor(item: IssueItemRow): List<RowAction> = listOf(
        RowAction("Qaytarish", AppIcons.Undo, enabled = item.netQty > 0) { returning = item.id },
        RowAction("Tarix", AppIcons.History) { historyBatch = item.batchId },
    )

    PageScaffold(
        title = Page.DISTRICTS.title,
        subtitle = "Tumanlarga berilgan reaktivlar: har bir berish alohida, qaytarilgani ayirilgan",
        actions = {
            PlacesReportButtons(state, enabled = true, testTag = "district-report") { ext ->
                placesExport(state, ReportKind.DISTRICTS, ReportFilter(from, to, name = reagentText.ifBlank { null }, districtId = current?.id), ext)
            }
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlacesListPanel(
                state = state,
                kind = PlaceKind.DISTRICT,
                places = places,
                selectedId = current?.id,
                onSelect = { id ->
                    selectedId = id
                    selection.key = null
                },
                testTag = "districts",
                modifier = Modifier.width(220.dp).fillMaxHeight(),
                allLabel = "Barcha tumanlar",
                allSecondary = "${allTime?.items?.size ?: 0} ta berish",
                secondary = { place -> itemCounts[place.id]?.let { "$it ta berish" } ?: "berish yo‘q" },
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlacesDetailHeader(
                    title = current?.name ?: "Barcha tumanlar",
                    inactive = current?.active == false,
                    description = (if (current == null) "Hamma tumanlarga berilgan reaktivlar" else "Shu tumanga berilgan reaktivlar") + " — ${placesPeriodText(qFrom, qTo)}",
                    testTag = "district-header",
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
                        label = "Berilgan sana", today = today, fieldWidth = 128.dp, testTag = "district-period",
                    )
                    AppTextField(
                        reagentText, { reagentText = it },
                        label = "Reaktiv", placeholder = "Nomi bo‘yicha", leadingIcon = AppIcons.Search,
                        modifier = Modifier.width(220.dp), testTag = "district-reagent",
                    )
                }
                if (current == null) {
                    DistrictCardsRow(summary) { id ->
                        selectedId = id
                        selection.key = null
                    }
                } else {
                    DistrictSingleCards(summary, items)
                }
                PlacesTabs(
                    tabs = listOf(DistrictsPageTab.ITEMS.title to items.size, DistrictsPageTab.PER_REAGENT.title to summary.perReagent.size),
                    selected = tab.ordinal,
                    onSelect = { tab = DistrictsPageTab.entries[it] },
                    testTag = "district-tab",
                )
                when (tab) {
                    DistrictsPageTab.ITEMS -> {
                        SelectionActionBar(
                            title = selected?.let {
                                "${dateText(it.issuedOn)} · ${it.districtName.orEmpty()} · ${batchLabel(it.name, it.series, it.expiresOn)} — " +
                                    qtyText(it.qty, it.enteredUnit)
                            },
                            actions = selected?.let { actionsFor(it) }.orEmpty(),
                            hint = "Berishni tanlang — «Qaytarish» shu yerda. Ikki marta bosish — partiya tarixi.",
                            testTag = "district-actions",
                        )
                        DistrictItemsTable(state, items, today, Modifier.fillMaxWidth().weight(1f), selection, ::actionsFor) { historyBatch = it.batchId }
                    }
                    DistrictsPageTab.PER_REAGENT -> DistrictReagentsTable(state, summary.perReagent, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
    returning?.let { id ->
        items.firstOrNull { it.id == id }?.let { item -> DistrictsReturnDialog(state, item) { returning = null } }
    }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

/** «Barcha tumanlar»: har bir tuman — karta (berishlar soni, sof jami o‘lchov turi bo‘yicha); bosilsa tuman tanlanadi. */
@Composable
private fun DistrictCardsRow(summary: DistrictSummary, onSelect: (Long) -> Unit) {
    if (summary.districts.isEmpty()) {
        PlacesTotalsCard("Tumanlar", emptyList(), Modifier.fillMaxWidth(), hint = "Tanlangan filtr bo‘yicha tumanlarga berish yo‘q", testTag = "district-card-empty")
        return
    }
    val list = rememberLazyListState()
    Column {
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            items(summary.districts, key = { it.districtId }) { d ->
                PlacesTotalsCard(
                    d.name,
                    placesTotalsLines(d.net),
                    Modifier.width(220.dp),
                    hint = "${d.items} ta berish · oxirgisi ${dateText(d.lastDate)}",
                    onClick = { onSelect(d.districtId) },
                    testTag = "district-card-${d.districtId}",
                )
            }
        }
        if (list.canScrollForward || list.canScrollBackward) {
            Spacer(Modifier.height(2.dp))
            HorizontalScrollbar(rememberScrollbarAdapter(list), Modifier.fillMaxWidth())
        }
    }
}

/** Bitta tuman: berishlar soni va davri, jami berilgan, qaytarilgan va sof — o‘lchov turi bo‘yicha. */
@Composable
private fun DistrictSingleCards(summary: DistrictSummary, items: List<IssueItemRow>) {
    val total = summary.districts.firstOrNull()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val card = Modifier.weight(1f).fillMaxHeight()
        PlacesTotalsCard(
            "Berishlar", listOf("${items.size} ta"), card,
            hint = total?.let { if (it.firstDate == it.lastDate) dateText(it.firstDate) else "${dateText(it.firstDate)} — ${dateText(it.lastDate)}" } ?: "berish yo‘q",
            testTag = "district-card-count",
        )
        PlacesTotalsCard("Jami berilgan", placesTotalsLines(placesTotals(items, { it.unit.dimension }, { it.qty })), card, hint = "qaytarilgani bilan", testTag = "district-card-issued")
        PlacesTotalsCard(
            "Qaytarilgan", placesTotalsLines(placesTotals(items.filter { it.returnedQty > 0 }, { it.unit.dimension }, { it.returnedQty })), card,
            hint = "tumandan omborga", testTag = "district-card-returned",
        )
        PlacesTotalsCard(
            "Sof berilgan", placesTotalsLines(total?.net.orEmpty()), card,
            hint = "berilgan − qaytarilgan", valueColor = AppColors.Primary, testTag = "district-card-net",
        )
    }
}

// ---------------------------------------------------------------- jadvallar

@Composable
private fun DistrictItemsTable(
    state: AppState,
    items: List<IssueItemRow>,
    today: LocalDate,
    modifier: Modifier,
    selection: uz.reaktivombor.desktop.ui.components.TableSelection,
    actions: (IssueItemRow) -> List<RowAction>,
    onOpen: (IssueItemRow) -> Unit,
) {
    val columns = districtItemColumns(today, actions)
    val layout = rememberAppTableLayout(state, "district-items", columns)
    DataTable(
        rows = items,
        columns = columns,
        rowKey = { it.id },
        layout = layout,
        modifier = modifier,
        selection = selection,
        onRowDoubleClick = onOpen,
        emptyText = "Tanlangan filtr bo‘yicha tumanlarga berish yo‘q",
        emptyHint = "Tumanga berish «Sarf va berish» sahifasida qilinadi.",
        testTag = "district-items",
    )
}

private fun districtItemColumns(today: LocalDate, actions: (IssueItemRow) -> List<RowAction>): List<TableColumn<IssueItemRow>> = listOf(
    textColumn("date", "Sana", 92.dp, ColumnAlign.END, sortKey = { it.issuedOn }) { dateText(it.issuedOn) },
    textColumn("doc", "Hujjat", 78.dp) { it.docNo.ifBlank { "—" } },
    textColumn("district", "Tuman", 104.dp) { it.districtName.orEmpty() },
    textColumn("name", "Reaktiv nomi", 160.dp) { it.name },
    textColumn("series", "Seriya raqami", 90.dp) { it.series.ifBlank { "—" } },
    TableColumn(
        id = "qty", title = "Miqdor", width = 80.dp, align = ColumnAlign.END, sortKey = { it.qty },
        text = { Qty.format(it.qty, it.enteredUnit) },
        cell = { row -> PlacesQtyText(Qty.format(row.qty, row.enteredUnit)) },
    ),
    textColumn("unit", "Birlik", 58.dp, ColumnAlign.CENTER) { it.enteredUnit.code },
    textColumn("returned", "Qaytarilgan", 116.dp, ColumnAlign.END, sortKey = { it.returnedQty }) {
        if (it.returnedQty > 0) qtyText(it.returnedQty, it.enteredUnit) else "—"
    },
    textColumn("expires", "Yaroqlilik muddati", 98.dp, ColumnAlign.END, sortKey = { it.expiresOn }) { dateText(it.expiresOn) },
    TableColumn(
        id = "state", title = "Holati", width = 172.dp,
        sortKey = { expiryState(it.expiresOn, today).severity }, text = { expiryState(it.expiresOn, today).label },
        cell = { row -> ExpiryChip(expiryState(row.expiresOn, today)) },
    ),
    textColumn("organization", "Tashkilot", 200.dp) { it.organization.ifBlank { "—" } },
    textColumn("receiver", "Qabul qilgan shaxs", 130.dp) { it.receiver.ifBlank { "—" } },
    textColumn("note", "Izoh", 150.dp) { it.note.ifBlank { it.issueNote.ifBlank { "—" } } },
    actionsColumn(width = 84.dp, maxInline = 2, actions = actions),
)

@Composable
private fun DistrictReagentsTable(state: AppState, rows: List<DistrictReagentTotal>, modifier: Modifier) {
    val columns = remember { districtReagentColumns() }
    val layout = rememberAppTableLayout(state, "district-reagents", columns)
    DataTable(
        rows = rows,
        columns = columns,
        rowKey = { "${it.districtId}-${it.total.reagentId}-${it.total.dimension.name}" },
        layout = layout,
        modifier = modifier,
        emptyText = "Tanlangan filtr bo‘yicha tumanlarga berish yo‘q",
        testTag = "district-reagents",
    )
}

private fun districtReagentColumns(): List<TableColumn<DistrictReagentTotal>> = listOf(
    textColumn("district", "Tuman", 110.dp) { it.districtName },
    textColumn("name", "Reaktiv nomi", 180.dp) { it.total.name },
    textColumn("unit", "Birlik", 56.dp, ColumnAlign.CENTER) { it.total.unit.code },
    textColumn("issued", "Jami berilgan", 96.dp, ColumnAlign.END, sortKey = { it.total.issued }) { qtyNumber(it.total.issued, it.total.unit) },
    textColumn("returned", "Qaytarilgan", 92.dp, ColumnAlign.END, sortKey = { it.total.returned }, visibleByDefault = false) {
        qtyNumber(it.total.returned, it.total.unit)
    },
    TableColumn(
        id = "net", title = "Sof berilgan", width = 96.dp, align = ColumnAlign.END, sortKey = { it.total.net },
        text = { qtyNumber(it.total.net, it.total.unit) },
        cell = { row -> PlacesQtyText(qtyNumber(row.total.net, row.total.unit)) },
    ),
    textColumn("times", "Necha marta", 86.dp, ColumnAlign.END, sortKey = { it.total.items }) { "${it.total.items}" },
    textColumn("first", "Birinchi sana", 98.dp, ColumnAlign.END, sortKey = { it.total.firstDate }) { dateText(it.total.firstDate) },
    textColumn("last", "Oxirgi sana", 98.dp, ColumnAlign.END, sortKey = { it.total.lastDate }) { dateText(it.total.lastDate) },
)

// ---------------------------------------------------------------- qaytarish oynasi

/** Tumandan qaytarish: sana, miqdor (berilgan birlik turidagi istalgan birlikda), qabul qilgan xodim va sabab. */
@Composable
private fun DistrictsReturnDialog(state: AppState, item: IssueItemRow, onDismiss: () -> Unit) {
    val today = remember { state.today() }
    val actor = remember { state.load("") { state.services.settings.actorName } }
    val form = remember { FormErrors() }
    var date by remember { mutableStateOf<LocalDate?>(today) }
    var qty by remember { mutableStateOf(Qty.format(item.netQty, item.enteredUnit)) }
    var unit by remember { mutableStateOf(item.enteredUnit) }
    var receivedBy by remember { mutableStateOf(actor) }
    var reason by remember { mutableStateOf("") }
    var generalError by remember { mutableStateOf<String?>(null) }
    val confirm = rememberConfirmState()
    val units = MeasureUnit.of(item.unit.dimension)

    // Takroriy qaytarish (2 daqiqa ichida xuddi shunday) — tasdiqlansa, qayta yuboriladi.
    fun save(confirmDuplicate: Boolean = false) {
        generalError = null
        state.run(
            {
                state.services.stock.returnItem(
                    item.id,
                    ReturnInput(date, qty, unit, reason.ifBlank { null }, receivedBy.ifBlank { null }),
                    confirmDuplicate,
                )
            },
            form = form,
            onError = { e: UserError ->
                when {
                    e is DuplicateOperationError -> {
                        confirm.ask(
                            title = "Takroriy qaytarish",
                            message = e.message,
                            confirmLabel = "Baribir saqlash",
                            dismissLabel = "Orqaga",
                        ) { save(confirmDuplicate = true) }
                        true
                    }
                    e.fields.isEmpty() -> {
                        generalError = e.message
                        true
                    }
                    else -> false
                }
            },
        ) {
            onDismiss()
            val amount = runCatching { qtyText(Qty.parse(qty, unit), unit) }.getOrDefault("$qty ${unit.code}")
            state.success("Qaytarish saqlandi: ${batchQuote(item.name, item.series)} — $amount (${item.districtName} tumani).")
        }
    }

    AppDialog(
        title = "Tumandan qaytarish",
        subtitle = batchLabel(item.name, item.series, item.expiresOn),
        onDismiss = onDismiss,
        width = 640.dp,
        testTag = "return-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton("Qaytarish", onClick = { save() }, icon = AppIcons.Undo, enabled = !state.busy, modifier = Modifier.testTag("return-save"))
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            InfoPair("Tuman", item.districtName.orEmpty())
            InfoPair("Berilgan", "${qtyText(item.qty, item.enteredUnit)}, ${dateText(item.issuedOn)}")
            InfoPair("Avval qaytarilgan", if (item.returnedQty > 0) qtyText(item.returnedQty, item.enteredUnit) else "—")
            InfoPair("Qaytarish mumkin", qtyText(item.netQty, item.enteredUnit), valueColor = AppColors.Primary)
        }
        FormRow {
            DateField(
                date, { date = it; form.clear("date") },
                label = "Qaytarish sanasi", required = true, today = today, minDate = item.issuedOn, maxDate = today,
                error = form["date"], modifier = Modifier.weight(1f), testTag = "return-date",
            )
            QtyField(
                qty, { qty = it; form.clear("qtyText") }, unit.code,
                { code -> MeasureUnit.of(code)?.let { unit = it; form.clear("unit") } }, units.map { it.code },
                label = "Miqdor", required = true, error = form["qtyText"] ?: form["unit"],
                modifier = Modifier.weight(1f), testTag = "return-qty",
            )
        }
        FormRow {
            AppTextField(
                receivedBy, { receivedBy = it; form.clear("receivedBy") },
                label = "Qabul qilgan xodim", error = form["receivedBy"], modifier = Modifier.weight(1f), testTag = "return-received-by",
            )
            AppTextField(
                reason, { reason = it; form.clear("reason") },
                label = "Sabab", placeholder = "Masalan: ortib qoldi", error = form["reason"], modifier = Modifier.weight(1f), testTag = "return-reason",
            )
        }
        generalError?.let { Banner(it, severity = Severity.ERROR, testTag = "return-error") }
        Box(Modifier.padding(top = 2.dp)) {
            Text(
                "Qaytarilgan miqdor partiya qoldig‘iga qo‘shiladi. Hisobdan chiqarilgan miqdor qaytarilmaydi.",
                fontSize = 12.sp, lineHeight = 16.sp, color = AppColors.Muted,
            )
        }
    }
    ConfirmHost(confirm, state.busy)
}

package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uz.reaktivombor.desktop.core.DuplicateOperationError
import uz.reaktivombor.desktop.core.ExpiredBatchError
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.daysLeft
import uz.reaktivombor.desktop.core.expiryState
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchStock
import uz.reaktivombor.desktop.service.IssueConfirm
import uz.reaktivombor.desktop.service.IssueItemRow
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.ReturnInput
import uz.reaktivombor.desktop.service.ReturnRow
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.DateField
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.actionsColumn
import uz.reaktivombor.desktop.ui.components.rememberTableSelection
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import java.time.LocalDate

// «Sarf va berish» sahifasining pastki qismi: berish tarixi (qaytarish, bekor qilish, tuzatish) va qaytarishlar.

const val ISSUE_ITEMS_TABLE_ID = "issue-items"
const val RETURNS_TABLE_ID = "returns"

/** Filtr paneli ikki qatorli — jadval biroz pastroq, tablar bilan birga 1366×768 ekranga sig‘sin. */
private val IssuesHistoryTableHeight = 390.dp

@Immutable
private data class IssuesHistoryFilters(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val text: String = "",
    val recipientType: RecipientType? = null,
    val departmentId: Long? = null,
    val districtId: Long? = null,
    val includeCancelled: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(from != null || to != null, text.isNotBlank(), recipientType != null, departmentId != null, districtId != null, includeCancelled)
            .count { it }

    fun toQuery() = OpFilter(
        from = from,
        to = to,
        text = text.ifBlank { null },
        recipientType = recipientType,
        departmentId = departmentId,
        districtId = districtId,
        includeCancelled = includeCancelled,
    )
}

/** Kimga berilgan: "Bo‘lim: Gistologiya", "Tuman: Chilonzor" yoki "Hisobdan chiqarish". */
private fun recipientText(type: RecipientType, name: String): String = when (type) {
    RecipientType.DEPARTMENT -> "Bo‘lim: $name"
    RecipientType.DISTRICT -> "Tuman: $name"
    RecipientType.WRITE_OFF -> RecipientType.WRITE_OFF.label
}

/** Xabarlar uchun: "Gistologiya bo‘limiga", "Chilonzor tumaniga", "hisobdan chiqarishga". */
private fun recipientDative(type: RecipientType, name: String): String = when (type) {
    RecipientType.DEPARTMENT -> "$name bo‘limiga"
    RecipientType.DISTRICT -> "$name tumaniga"
    RecipientType.WRITE_OFF -> "hisobdan chiqarishga"
}

/** Qatorning miqdori kiritilgan birlikda: "200 ml". */
private fun IssueItemRow.amount(base: Long = qty): String = qtyText(base, enteredUnit)

/** Berish tarixi va qaytarishlar (tablar), umumiy filtrlar, tanlangan qator amallari va oynalar. */
@Composable
internal fun IssuesHistorySection(state: AppState, modifier: Modifier = Modifier) {
    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    var tab by remember { mutableStateOf("issues") }
    var filters by remember { mutableStateOf(IssuesHistoryFilters()) }
    val query = filters.toQuery()
    val items = remember(tick, query) { state.load(emptyList()) { state.services.queries.issueItems(query) } }
    val returns = remember(tick, query) { state.load(emptyList()) { state.services.queries.returns(query) } }
    val districts = remember(tick) { state.load(emptyList()) { state.services.directory.districts(includeInactive = true) } }
    val departments = remember(tick) { state.load(emptyList()) { state.services.directory.departments(includeInactive = true) } }
    val correctedIds = remember(items) { items.mapNotNull { it.correctsId }.toSet() }
    val confirm = rememberConfirmState()
    val reasons = rememberOpsReasonState()
    val itemSelection = rememberTableSelection()
    val returnSelection = rememberTableSelection()
    var returning by remember { mutableStateOf<IssueItemRow?>(null) }
    var correcting by remember { mutableStateOf<IssueItemRow?>(null) }
    var historyBatch by remember { mutableStateOf<Long?>(null) }
    val itemList = rememberLazyListState()
    val returnList = rememberLazyListState()
    // Yangi yozuv qo‘shilsa (yangi berish/qaytarish) — jadval boshiga: aks holda ro‘yxat eski birinchi qatorda qolib,
    // yangi qatorlar ko‘rinadigan joydan yuqorida yashirinib qoladi.
    val newestItem = items.maxOfOrNull { it.id }
    val newestReturn = returns.maxOfOrNull { it.id }
    LaunchedEffect(newestItem) { if (newestItem != null) itemList.scrollToItem(0) }
    LaunchedEffect(newestReturn) { if (newestReturn != null) returnList.scrollToItem(0) }

    fun showReturnsOf(row: IssueItemRow) {
        tab = "returns"
        returnSelection.key = returns.firstOrNull { it.issueItemId == row.id && !it.cancelled }?.id
    }

    fun refuseBecauseOfReturns(row: IssueItemRow, what: String) {
        confirm.ask(
            title = "${what.replaceFirstChar { it.uppercase() }} bo‘lmaydi",
            message = "${batchQuote(row.name, row.series)}: ${dateText(row.issuedOn)} da ${recipientDative(row.recipientType, row.recipientName)} " +
                "berilgan ${row.amount()} dan ${row.amount(row.returnedQty)} qaytarilgan. Qaytarilgan miqdori bor berishni $what " +
                "bo‘lmaydi — avval «Qaytarishlar» bo‘limida shu qaytarishni bekor qiling.",
            confirmLabel = "Qaytarishlarni ko‘rsatish",
            dismissLabel = "Yopish",
        ) { showReturnsOf(row) }
    }

    fun requestCancel(row: IssueItemRow) {
        if (row.returnedQty > 0) {
            refuseBecauseOfReturns(row, "bekor qilib")
            return
        }
        reasons.ask(
            title = "Berishni bekor qilish",
            message = "${batchQuote(row.name, row.series)}: ${dateText(row.issuedOn)} dagi ${row.amount()} → " +
                "${recipientText(row.recipientType, row.recipientName)} bekor qilinadi va miqdor partiya qoldig‘iga qaytadi. " +
                "Yozuv o‘chirilmaydi — tarixda «Bekor qilingan» bo‘lib qoladi.",
            confirmLabel = "Bekor qilish",
            label = "Bekor qilish sababi",
        ) { reason ->
            state.run(
                { state.services.stock.cancelIssueItem(row.id, reason) },
                onError = { e ->
                    confirm.ask("Berishni bekor qilib bo‘lmaydi", e.message, confirmLabel = "Partiya tarixini ko‘rish", dismissLabel = "Yopish") {
                        historyBatch = row.batchId
                    }
                    true
                },
            ) { state.success("Berish bekor qilindi: ${batchQuote(row.name, row.series)}, ${row.amount()}. Miqdor partiyaga qaytdi.") }
        }
    }

    fun requestCorrect(row: IssueItemRow) {
        if (row.returnedQty > 0) refuseBecauseOfReturns(row, "tuzatib") else correcting = row
    }

    fun requestCancelReturn(row: ReturnRow) {
        reasons.ask(
            title = "Qaytarishni bekor qilish",
            message = "${batchQuote(row.name, row.series)}: ${dateText(row.returnedOn)} dagi ${row.enteredQty} ${row.enteredUnit.code} qaytarish " +
                "bekor qilinadi — bu miqdor partiya qoldig‘idan yana ayiriladi. Yozuv o‘chirilmaydi.",
            confirmLabel = "Bekor qilish",
            label = "Bekor qilish sababi",
        ) { reason ->
            state.run(
                { state.services.stock.cancelReturn(row.id, reason) },
                onError = { e ->
                    confirm.ask("Qaytarishni bekor qilib bo‘lmaydi", e.message, confirmLabel = "Partiya tarixini ko‘rish", dismissLabel = "Yopish") {
                        historyBatch = row.batchId
                    }
                    true
                },
            ) { state.success("Qaytarish bekor qilindi: ${batchQuote(row.name, row.series)}, ${row.enteredQty} ${row.enteredUnit.code}.") }
        }
    }

    fun itemActions(row: IssueItemRow): List<RowAction> = listOf(
        RowAction(
            "Qaytarish", AppIcons.Undo,
            enabled = !row.cancelled && row.recipientType != RecipientType.WRITE_OFF && row.netQty > 0,
        ) { returning = row },
        RowAction("Bekor qilish", AppIcons.Cancel, enabled = !row.cancelled, danger = true) { requestCancel(row) },
        RowAction("Tuzatish", AppIcons.Edit, enabled = !row.cancelled) { requestCorrect(row) },
        RowAction("Tarix", AppIcons.History) { historyBatch = row.batchId },
    )

    fun returnActions(row: ReturnRow): List<RowAction> = listOf(
        RowAction("Bekor qilish", AppIcons.Cancel, enabled = !row.cancelled, danger = true) { requestCancelReturn(row) },
        RowAction("Tarix", AppIcons.History) { historyBatch = row.batchId },
    )

    val itemColumns = issueItemColumns(correctedIds) { itemActions(it) }
    val itemLayout = rememberAppTableLayout(state, ISSUE_ITEMS_TABLE_ID, itemColumns)
    val returnColumns = returnColumns { returnActions(it) }
    val returnLayout = rememberAppTableLayout(state, RETURNS_TABLE_ID, returnColumns)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OpsTabs(
            listOf("issues" to "Berish tarixi (${items.size})", "returns" to "Qaytarishlar (${returns.size})"),
            tab,
            { tab = it },
            Modifier.fillMaxWidth(),
        )
        FilterBar(onClear = { filters = IssuesHistoryFilters() }, activeCount = filters.activeCount) {
            DateRangeField(filters.from, filters.to, { f, t -> filters = filters.copy(from = f, to = t) }, label = "Davr", today = today, fieldWidth = 124.dp, testTag = "issues-filter-period")
            AppTextField(
                filters.text, { filters = filters.copy(text = it) }, label = "Qidirish", placeholder = "Reaktiv, seriya, hujjat…",
                leadingIcon = AppIcons.Search, modifier = Modifier.width(250.dp), testTag = "issues-filter-text",
            )
            SelectField(
                value = filters.recipientType,
                options = listOf<RecipientType?>(null) + RecipientType.entries,
                onSelect = { filters = filters.copy(recipientType = it) },
                label = "Kimga",
                placeholder = "Hammasi",
                optionLabel = { it?.label ?: "— Hammasi —" },
                modifier = Modifier.width(170.dp),
                testTag = "issues-filter-type",
            )
            // Filtrda nofaol bo‘lim va tumanlar ham bor: ularga avval berilganlar tarixda qoladi.
            IssuesPlaceFilter(
                places = departments,
                value = filters.departmentId,
                onChange = { filters = filters.copy(departmentId = it) },
                label = "Bo‘lim",
                testTag = "issues-filter-department",
            )
            IssuesPlaceFilter(
                places = districts,
                value = filters.districtId,
                onChange = { filters = filters.copy(districtId = it) },
                label = "Tuman",
                testTag = "issues-filter-district",
            )
            CheckField(filters.includeCancelled, { filters = filters.copy(includeCancelled = it) }, "Bekor qilinganlar", testTag = "issues-filter-cancelled")
        }
        if (tab == "issues") {
            val selected = items.firstOrNull { it.id == itemSelection.key }
            SelectionActionBar(
                title = selected?.let {
                    "${dateText(it.issuedOn)} · ${batchLabel(it.name, it.series, it.expiresOn)} · ${it.amount()} → ${recipientText(it.recipientType, it.recipientName)}"
                },
                actions = selected?.let { itemActions(it) }.orEmpty(),
                hint = "Qatorni tanlang — amallari (qaytarish, bekor qilish, tuzatish, tarix) shu yerda chiqadi.",
                testTag = "issue-item-actions",
            )
            DataTable(
                rows = items,
                columns = itemColumns,
                rowKey = { it.id },
                layout = itemLayout,
                modifier = Modifier.fillMaxWidth().height(IssuesHistoryTableHeight),
                rowBackground = { if (it.cancelled) OpsInactiveRowColor else null },
                selection = itemSelection,
                listState = itemList,
                emptyText = if (filters.activeCount > 0) "Filtrga mos berish topilmadi" else "Hali berish yo‘q",
                emptyHint = if (filters.activeCount > 0) "Filtrlarni o‘zgartiring yoki «Filtrlarni tozalash» tugmasini bosing." else null,
                testTag = "issue-items",
            )
        } else {
            val selected = returns.firstOrNull { it.id == returnSelection.key }
            SelectionActionBar(
                title = selected?.let { "${dateText(it.returnedOn)} · ${batchQuote(it.name, it.series)} · ${it.enteredQty} ${it.enteredUnit.code} ← ${recipientText(it.recipientType, it.recipientName)}" },
                actions = selected?.let { returnActions(it) }.orEmpty(),
                hint = "Qaytarishni tanlang — uni bekor qilish yoki partiya tarixini ko‘rish mumkin.",
                testTag = "return-actions",
            )
            DataTable(
                rows = returns,
                columns = returnColumns,
                rowKey = { it.id },
                layout = returnLayout,
                modifier = Modifier.fillMaxWidth().height(IssuesHistoryTableHeight),
                rowBackground = { if (it.cancelled) OpsInactiveRowColor else null },
                selection = returnSelection,
                listState = returnList,
                emptyText = if (filters.activeCount > 0) "Filtrga mos qaytarish topilmadi" else "Hali qaytarish yo‘q",
                emptyHint = "Qaytarish «Berish tarixi»da qatorni tanlab, «Qaytarish» orqali kiritiladi.",
                testTag = "returns",
            )
        }
    }
    ConfirmHost(confirm, state.busy)
    OpsReasonHost(reasons, state.busy)
    returning?.let { row -> IssuesReturnDialog(state, row) { returning = null } }
    correcting?.let { row -> IssuesCorrectDialog(state, row) { correcting = null } }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

/**
 * Bo‘lim yoki tuman filtri: hammasi — nofaollari ham, «(nofaol)» belgisi bilan (eski berishlarni ham topish uchun).
 * Tanlanganini [Place.id] bo‘yicha qaytaradi; null — «Hammasi».
 */
@Composable
private fun IssuesPlaceFilter(places: List<Place>, value: Long?, onChange: (Long?) -> Unit, label: String, testTag: String) {
    SelectField(
        value = places.firstOrNull { it.id == value },
        options = listOf<Place?>(null) + places,
        onSelect = { onChange(it?.id) },
        label = label,
        placeholder = "Hammasi",
        optionLabel = { place -> place?.let { it.name + if (it.active) "" else " (nofaol)" } ?: "— Hammasi —" },
        modifier = Modifier.width(170.dp),
        testTag = testTag,
    )
}

/**
 * Berish tarixi ustunlari. Bekor qilingan/tuzatilgan qator: sanasi oldida «bekor»/«tuzatilgan» belgisi, miqdori kulrang
 * va ustidan chizilgan (Kirimlar jadvali bilan bir xil).
 */
private fun issueItemColumns(correctedIds: Set<Long>, actions: (IssueItemRow) -> List<RowAction>): List<TableColumn<IssueItemRow>> = listOf(
    TableColumn(
        id = "date", title = "Sana", width = OpsDateColumnWidth, align = ColumnAlign.END, sortKey = { it.issuedOn },
        text = { dateText(it.issuedOn) },
        cell = { row -> OpsDateCell(dateText(row.issuedOn), opsStatus(row.id, row.cancelled, correctedIds)) },
    ),
    textColumn("doc", "Hujjat", 90.dp) { it.docNo.ifBlank { "—" } },
    textColumn("name", "Reaktiv", 170.dp) { it.name },
    textColumn("series", "Seriya", 92.dp) { it.series.ifBlank { "—" } },
    textColumn("expires", "Yaroqlilik muddati", 108.dp, ColumnAlign.END, sortKey = { it.expiresOn }) { dateText(it.expiresOn) },
    TableColumn(
        id = "qty", title = "Miqdor", width = 86.dp, align = ColumnAlign.END, sortKey = { it.qty },
        text = { it.enteredQty },
        cell = { row -> OpsQtyCell(row.enteredQty, opsStatus(row.id, row.cancelled, correctedIds)) },
    ),
    textColumn("unit", "Birlik", 60.dp, ColumnAlign.CENTER) { it.enteredUnit.code },
    textColumn("recipient", "Qabul qiluvchi", 190.dp) { recipientText(it.recipientType, it.recipientName) },
    textColumn("org", "Tashkilot", 190.dp) { it.organization.ifBlank { "—" } },
    textColumn("receiver", "Qabul qilgan shaxs", 140.dp) { it.receiver.ifBlank { "—" } },
    textColumn("issuer", "Beruvchi", 130.dp) { it.issuedBy.ifBlank { "—" } },
    textColumn("returned", "Qaytarilgan", 118.dp, ColumnAlign.END, sortKey = { it.returnedQty }) {
        if (it.returnedQty > 0) Qty.format(it.returnedQty, it.enteredUnit) + " " + it.enteredUnit.code else "—"
    },
    TableColumn(
        id = "flags", title = "Belgilar", width = 220.dp,
        text = { row -> listOfNotNull("FEFO buzilgan".takeIf { row.fefoOverride }, "Muddati o‘tgan".takeIf { row.expiredOverride }).joinToString(", ") },
        sortKey = { row -> (if (row.expiredOverride) 2 else 0) + (if (row.fefoOverride) 1 else 0) },
        cell = { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.fefoOverride) StatusChip("FEFO buzilgan", StatusStyles.YELLOW.background, StatusStyles.YELLOW.foreground)
                if (row.fefoOverride && row.expiredOverride) Spacer(Modifier.width(4.dp))
                if (row.expiredOverride) StatusChip("Muddati o‘tgan", StatusStyles.EXPIRED.background, StatusStyles.EXPIRED.foreground, bold = true)
            }
        },
    ),
    TableColumn(
        id = "status", title = "Holat", width = 230.dp,
        text = { opsStatus(it.id, it.cancelled, correctedIds).label },
        cell = { row -> OpsStatusCell(opsStatus(row.id, row.cancelled, correctedIds), row.cancelReason, row.correctsId != null) },
    ),
    textColumn("note", "Izoh / sabab", 180.dp) { it.note.ifBlank { "—" } },
    actionsColumn(width = 140.dp, maxInline = 4, actions = actions),
)

/** Qaytarishlar ustunlari: bekor qilingan qaytarish — sanasi oldida «bekor», miqdori ustidan chizilgan. */
private fun returnColumns(actions: (ReturnRow) -> List<RowAction>): List<TableColumn<ReturnRow>> = listOf(
    TableColumn(
        id = "date", title = "Qaytarilgan sana", width = OpsDateColumnWidth, align = ColumnAlign.END, sortKey = { it.returnedOn },
        text = { dateText(it.returnedOn) },
        cell = { row -> OpsDateCell(dateText(row.returnedOn), if (row.cancelled) OpsStatus.CANCELLED else OpsStatus.ACTIVE) },
    ),
    textColumn("name", "Reaktiv", 180.dp) { it.name },
    textColumn("series", "Seriya", 96.dp) { it.series.ifBlank { "—" } },
    TableColumn(
        id = "qty", title = "Miqdor", width = 90.dp, align = ColumnAlign.END, sortKey = { it.qty },
        text = { it.enteredQty },
        cell = { row -> OpsQtyCell(row.enteredQty, if (row.cancelled) OpsStatus.CANCELLED else OpsStatus.ACTIVE) },
    ),
    textColumn("unit", "Birlik", 60.dp, ColumnAlign.CENTER) { it.enteredUnit.code },
    textColumn("from", "Kimdan", 190.dp) { recipientText(it.recipientType, it.recipientName) },
    textColumn("issued", "Berilgan sana", 104.dp, ColumnAlign.END, sortKey = { it.issuedOn }) { dateText(it.issuedOn) },
    textColumn("reason", "Sabab", 200.dp) { it.reason.ifBlank { "—" } },
    textColumn("receivedBy", "Qabul qilgan", 140.dp) { it.receivedBy.ifBlank { "—" } },
    TableColumn(
        id = "status", title = "Holat", width = 230.dp,
        text = { if (it.cancelled) OpsStatus.CANCELLED.label else OpsStatus.ACTIVE.label },
        cell = { row -> OpsStatusCell(if (row.cancelled) OpsStatus.CANCELLED else OpsStatus.ACTIVE, row.cancelReason, isCorrection = false) },
    ),
    actionsColumn(width = 90.dp, maxInline = 2, actions = actions),
)

// ---------------------------------------------------------------- qaytarish oynasi

/** Berilgan qatordan qaytarish: miqdor qaytarish mumkin bo‘lganidan (berilgan − avval qaytarilgan) oshmaydi. */
@Composable
private fun IssuesReturnDialog(state: AppState, row: IssueItemRow, onDismiss: () -> Unit) {
    val today = remember { state.today() }
    val actor = remember { state.load("") { state.services.settings.actorName } }
    val form = remember { FormErrors() }
    var date by remember { mutableStateOf<LocalDate?>(today) }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(row.enteredUnit) }
    var reason by remember { mutableStateOf("") }
    var receivedBy by remember { mutableStateOf(actor) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val confirm = rememberConfirmState()
    val (base, parseError) = opsParseQty(qty, unit, "Qaytarilgan miqdor")
    val tooMuch = base != null && base > row.netQty
    val liveError = parseError ?: if (tooMuch) "Ko‘pi bilan ${row.amount(row.netQty)} qaytarish mumkin." else null

    // Takroriy qaytarish (2 daqiqa ichida xuddi shunday) — tasdiqlansa, qayta yuboriladi.
    fun save(confirmDuplicate: Boolean = false) {
        dialogError = null
        state.run(
            {
                state.services.stock.returnItem(
                    row.id,
                    ReturnInput(date = date, qtyText = qty, unit = unit, reason = reason, receivedBy = receivedBy),
                    confirmDuplicate,
                )
            },
            form = form,
            onError = { e ->
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
                        dialogError = e.message
                        true
                    }
                    else -> false
                }
            },
        ) {
            onDismiss()
            val remainder = state.load(null) { state.services.stock.remainder(row.batchId) }
            state.success(
                "Qaytarish saqlandi: ${batchQuote(row.name, row.series)}, $qty ${unit.code}." +
                    (remainder?.let { " Partiya qoldig‘i: ${qtyText(it, row.unit)}." } ?: ""),
            )
        }
    }

    AppDialog(
        title = "Qaytarish",
        subtitle = "${batchLabel(row.name, row.series, row.expiresOn)} · ${recipientText(row.recipientType, row.recipientName)}",
        onDismiss = onDismiss,
        width = 780.dp,
        testTag = "return-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton("Qaytarishni saqlash", onClick = { save() }, icon = AppIcons.Check, enabled = !state.busy && liveError == null, modifier = Modifier.testTag("ret-save"))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                InfoPair("Berilgan sana", dateText(row.issuedOn) + if (row.docNo.isNotBlank()) " · ${row.docNo}" else "")
                InfoPair("Berilgan", row.amount())
                InfoPair("Avval qaytarilgan", if (row.returnedQty > 0) row.amount(row.returnedQty) else "—")
                InfoPair("Qaytarish mumkin", row.amount(row.netQty), valueColor = AppColors.Primary)
            }
            val qtyError = form["qtyText"]
            val unitError = form["unit"]
            FormRow {
                DateField(
                    date, { date = it; form.clear("date") }, label = "Qaytarish sanasi", required = true, today = today,
                    minDate = row.issuedOn, maxDate = today, error = form["date"], modifier = Modifier.weight(0.9f), testTag = "ret-date",
                )
                QtyField(
                    amount = qty,
                    onAmountChange = { qty = it; form.clear("qtyText") },
                    unit = unit.code,
                    onUnitChange = { code -> MeasureUnit.of(code)?.let { unit = it }; form.clear("unit") },
                    units = row.unit.siblings.map { it.code },
                    label = "Qaytarilgan miqdor",
                    required = true,
                    error = qtyError ?: unitError ?: liveError,
                    modifier = Modifier.weight(1f),
                    testTag = "ret-qty",
                )
                AppTextField(receivedBy, { receivedBy = it; form.clear("receivedBy") }, label = "Qabul qilgan xodim", error = form["receivedBy"], modifier = Modifier.weight(1f), testTag = "ret-receiver")
            }
            AppTextField(
                reason, { reason = it; form.clear("reason") }, label = "Sabab", placeholder = "Masalan: ortib qoldi, ishlatilmadi",
                error = form["reason"], modifier = Modifier.fillMaxWidth(), testTag = "ret-reason",
            )
            dialogError?.let { OpsNotice(it, Severity.ERROR, Modifier.fillMaxWidth(), testTag = "ret-error") }
        }
    }
    ConfirmHost(confirm, state.busy)
}

// ---------------------------------------------------------------- tuzatish oynasi

/**
 * Berish qatorini tuzatish: eski qator bekor qilinadi (miqdor partiyaga qaytadi) va o‘rniga yangisi yoziladi —
 * Stock.correctIssueItem, bitta tranzaksiyada. Hujjat ma’lumotlari eskisidan olinadi. Sabab majburiy.
 */
@Composable
private fun IssuesCorrectDialog(state: AppState, row: IssueItemRow, onDismiss: () -> Unit) {
    val today = remember { state.today() }
    val form = remember { FormErrors() }
    val confirm = rememberConfirmState()
    // Shu reaktiv va o‘lchov turidagi qoldig‘i bor partiyalar + hozirgi partiya (bekor qilinganda miqdori qaytadi).
    val options = remember(row.id, state.refreshTick) {
        val list = issuesFefoOrder(state, row.reagentId, today).filter { it.unit.dimension == row.unit.dimension }
        if (list.any { it.batchId == row.batchId }) {
            list
        } else {
            val current = state.load(null) {
                state.services.queries.batchRows(BatchFilter(reagentId = row.reagentId, includeArchived = true), today).firstOrNull { it.batchId == row.batchId }
            }
            current?.let {
                list + BatchStock(it.batchId, it.reagentId, it.series, it.receivedOn, it.expiresOn, it.daysLeft, it.state, it.unit, it.remainder)
            } ?: list
        }
    }
    var batchId by remember { mutableStateOf(row.batchId) }
    var date by remember { mutableStateOf<LocalDate?>(row.issuedOn) }
    var qty by remember { mutableStateOf(row.enteredQty) }
    var unit by remember { mutableStateOf(row.enteredUnit) }
    var type by remember { mutableStateOf(row.recipientType) }
    var departmentId by remember { mutableStateOf(row.departmentId) }
    var districtName by remember { mutableStateOf(row.districtName.orEmpty()) }
    var organization by remember { mutableStateOf(row.organization) }
    var receiver by remember { mutableStateOf(row.receiver) }
    var note by remember { mutableStateOf(row.note) }
    var reason by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val districtNames = remember(state.refreshTick) { state.load(emptyList()) { state.services.directory.districts() }.map { it.name } }
    val batch = options.firstOrNull { it.batchId == batchId }
    val expired = batch?.expiresOn?.let { expires -> date?.let { expires < it } } == true

    fun save(flags: IssueConfirm = IssueConfirm.none) {
        dialogError = null
        val line = IssueLine(
            batchId = batchId,
            qtyText = qty,
            unit = unit,
            recipientType = type,
            departmentId = if (type == RecipientType.DEPARTMENT) departmentId else null,
            districtName = if (type == RecipientType.DISTRICT) districtName else null,
            organization = if (type == RecipientType.WRITE_OFF) "" else organization,
            receiver = if (type == RecipientType.WRITE_OFF) "" else receiver,
            note = note,
        )
        state.run(
            { state.services.stock.correctIssueItem(row.id, line, date, reason, flags) },
            form = form,
            onError = { e ->
                when {
                    e is ExpiredBatchError -> {
                        confirm.ask("Muddati o‘tgan partiyadan berish", e.message, confirmLabel = "Baribir saqlash", danger = true, dismissLabel = "Orqaga") {
                            save(flags.copy(allowExpired = true))
                        }
                        true
                    }
                    e.fields.isEmpty() || e.fields.keys.any { it !in CORRECT_FIELDS } -> {
                        dialogError = e.message
                        true
                    }
                    else -> false
                }
            },
        ) {
            onDismiss()
            state.success("Berish tuzatildi: ${batchQuote(row.name, row.series)} — endi $qty ${unit.code}.")
        }
    }

    AppDialog(
        title = "Berishni tuzatish",
        subtitle = "${dateText(row.issuedOn)} · ${batchLabel(row.name, row.series, row.expiresOn)} · ${row.amount()} → ${recipientText(row.recipientType, row.recipientName)}",
        onDismiss = onDismiss,
        width = 980.dp,
        testTag = "issue-correct-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton("Tuzatishni saqlash", onClick = { save() }, icon = AppIcons.Check, enabled = !state.busy, modifier = Modifier.testTag("ic-save"))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OpsNotice(
                "Eski qator o‘chirilmaydi: u «Tuzatilgan» deb belgilanadi (miqdori partiyaga qaytadi) va o‘rniga to‘g‘ri ma’lumotli yangi qator " +
                    "yoziladi. Hujjat raqami va beruvchi eskisidan olinadi.",
                Severity.INFO,
                Modifier.fillMaxWidth(),
            )
            val qtyError = form["qtyText"]
            val unitError = form["unit"]
            FormRow {
                DateField(
                    date, { date = it; form.clear("date") }, label = "Berish sanasi", required = true, today = today, maxDate = today,
                    error = form["date"], modifier = Modifier.weight(0.8f), testTag = "ic-date",
                )
                OpsRichSelect(
                    value = batch,
                    options = options,
                    onSelect = { b ->
                        batchId = b.batchId
                        if (unit.dimension != b.unit.dimension) unit = b.unit
                    },
                    optionTag = { "ic-batch-option-${it.batchId}" },
                    label = "Partiya",
                    required = true,
                    menuMinWidth = 600.dp,
                    modifier = Modifier.weight(2f),
                    testTag = "ic-batch",
                ) { option, inField -> IssuesBatchItem(option, recommended = false, inField = inField) }
                QtyField(
                    amount = qty,
                    onAmountChange = { qty = it; form.clear("qtyText") },
                    unit = unit.code,
                    onUnitChange = { code -> MeasureUnit.of(code)?.let { unit = it }; form.clear("unit") },
                    units = row.unit.siblings.map { it.code },
                    label = "Miqdor",
                    required = true,
                    error = qtyError ?: unitError,
                    modifier = Modifier.weight(0.9f),
                    testTag = "ic-qty",
                )
            }
            FormRow {
                IssuesRecipientTypeField(type, { type = it }, testTag = "ic-type")
                when (type) {
                    RecipientType.DEPARTMENT -> IssuesDepartmentField(
                        state, departmentId, { departmentId = it; form.clear("departmentId") }, form["departmentId"], Modifier.weight(1f), "ic-department",
                    )
                    RecipientType.DISTRICT -> IssuesDistrictField(
                        districtName, { districtName = it; form.clear("districtName") }, districtNames,
                        form["districtName"] ?: form["districtId"], Modifier.weight(1f), "ic-district",
                        inactiveNames = rememberInactiveDistrictNames(state),
                    )
                    RecipientType.WRITE_OFF -> Unit
                }
                if (type != RecipientType.WRITE_OFF) {
                    AppTextField(organization, { organization = it; form.clear("organization") }, label = "Qabul qiluvchi tashkilot", error = form["organization"], modifier = Modifier.weight(1.1f), testTag = "ic-org")
                    AppTextField(receiver, { receiver = it; form.clear("receiver") }, label = "Qabul qiluvchi xodim", error = form["receiver"], modifier = Modifier.weight(1f), testTag = "ic-receiver")
                }
            }
            FormRow {
                AppTextField(
                    note, { note = it; form.clear("note") },
                    label = if (type == RecipientType.WRITE_OFF) "Hisobdan chiqarish sababi" else "Izoh",
                    required = type == RecipientType.WRITE_OFF, error = form["note"], modifier = Modifier.weight(1f), testTag = "ic-note",
                )
                AppTextField(
                    reason, { reason = it; form.clear("reason") }, label = "Tuzatish sababi", required = true, placeholder = "Masalan: boshqa bo‘limga berilgan edi",
                    error = form["reason"], modifier = Modifier.weight(1f), testTag = "ic-reason",
                )
            }
            if (expired && type != RecipientType.WRITE_OFF && batch != null) {
                OpsNotice(
                    "Muddati o‘tgan partiya: yaroqlilik muddati ${dateText(batch.expiresOn)}. Saqlashda alohida tasdiq so‘raladi.",
                    Severity.ERROR,
                    Modifier.fillMaxWidth(),
                )
            } else if (batch != null && expiryState(batch.expiresOn, today) == ExpiryState.RED) {
                OpsNotice(
                    "Partiya muddati yaqin: ${dateText(batch.expiresOn)} (${daysPhrase(daysLeft(batch.expiresOn, today))}).",
                    Severity.WARNING,
                    Modifier.fillMaxWidth(),
                )
            }
            dialogError?.let { OpsNotice(it, Severity.ERROR, Modifier.fillMaxWidth(), testTag = "ic-error") }
        }
    }
    ConfirmHost(confirm, state.busy)
}

/** Tuzatish oynasida ko‘rsatiladigan maydonlar (boshqa maydon xatosi — oynaning o‘zida umumiy xato sifatida). */
private val CORRECT_FIELDS = setOf("date", "qtyText", "unit", "departmentId", "districtId", "districtName", "organization", "receiver", "note", "reason")

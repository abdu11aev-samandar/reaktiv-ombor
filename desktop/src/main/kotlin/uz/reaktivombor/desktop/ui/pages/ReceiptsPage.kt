package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import uz.reaktivombor.desktop.core.DuplicateOperationError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.matchesQuery
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.ReceiptInput
import uz.reaktivombor.desktop.service.ReceiptRow
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.DateField
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.FieldFrame
import uz.reaktivombor.desktop.ui.components.FieldTextStyle
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.FormSection
import uz.reaktivombor.desktop.ui.components.IconAction
import uz.reaktivombor.desktop.ui.components.LabeledField
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

/** «Kirimlar» jadvalining ustunlar ko‘rinishi shu nom bilan saqlanadi. */
const val RECEIPTS_TABLE_ID = "receipts"

/** Qo‘shimcha kirim shakli. Saqlangandan keyin sana va qabul qilgan xodim qoladi. */
@Stable
private class ReceiptsPageForm(private val today: LocalDate, private val receiver: String) {
    var batchId by mutableStateOf<Long?>(null)
    var query by mutableStateOf("")
    var date by mutableStateOf<LocalDate?>(today)
    var qty by mutableStateOf("")
    var unit by mutableStateOf<MeasureUnit?>(null)
    var receivedBy by mutableStateOf(receiver)
    var storage by mutableStateOf("")
    var docNo by mutableStateOf("")
    var note by mutableStateOf("")

    /** Partiya tanlandi: birlik partiyaniki, saqlash joyi bo‘sh bo‘lsa — partiyaniki. */
    fun select(row: BatchRow) {
        batchId = row.batchId
        query = row.label()
        if (unit?.dimension != row.unit.dimension) unit = row.unit
        if (storage.isBlank()) storage = row.storageLocation
    }

    fun toInput() = ReceiptInput(date = date, qtyText = qty, unit = unit, receivedBy = receivedBy, storageLocation = storage, docNo = docNo, note = note)

    /** Keyingi kirim uchun: sana va qabul qilgan xodim qoladi. */
    fun resetForNext() {
        batchId = null
        query = ""
        qty = ""
        unit = null
        storage = ""
        docNo = ""
        note = ""
    }

    fun clearAll() {
        resetForNext()
        date = today
        receivedBy = receiver
    }
}

@Immutable
private data class ReceiptsPageFilters(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val text: String = "",
    val series: String = "",
    val includeCancelled: Boolean = false,
    val batchId: Long? = null,
    val reagentId: Long? = null,
) {
    val activeCount: Int
        get() = listOf(from != null || to != null, text.isNotBlank(), series.isNotBlank(), includeCancelled, batchId != null, reagentId != null)
            .count { it }

    fun toQuery() = OpFilter(
        from = from,
        to = to,
        text = text.ifBlank { null },
        series = series.ifBlank { null },
        includeCancelled = includeCancelled,
        batchId = batchId,
        reagentId = reagentId,
    )
}

/**
 * «Kirim operatsiyalari»: mavjud partiyaga qo‘shimcha kirim (yangi partiya — «Reaktiv qo‘shish» sahifasida) va kirimlar
 * jadvali: bekor qilish (sabab bilan), tuzatish (eski yozuv o‘rniga yangisi), partiya tarixi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptsPage(state: AppState) {
    val intent = remember { state.intent }
    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val actor = remember { state.load("") { state.services.settings.actorName } }
    // Kirim qilsa bo‘ladigan partiyalar: arxivda bo‘lmaganlari (qoldig‘i tugaganlari ham).
    val batches = remember(tick, today) { state.load(emptyList()) { state.services.queries.batchRows(BatchFilter(), today) } }
    // «Kirim» tugmasi (Barcha reaktivlar) bilan kelinganda — shu partiya oldindan tanlanadi.
    val intentTarget = remember { (intent as? PageIntent.ReceiptFor)?.let { t -> t.batchId to batches.firstOrNull { it.batchId == t.batchId } } }
    var intentNotice by remember {
        mutableStateOf(
            if (intentTarget != null && intentTarget.second == null) {
                "Tanlangan partiya arxivda yoki topilmadi — unga kirim qilib bo‘lmaydi. Kerak bo‘lsa, avval uni arxivdan chiqaring."
            } else {
                null
            },
        )
    }
    val form = remember { ReceiptsPageForm(today, actor).also { f -> intentTarget?.second?.let { f.select(it) } } }
    // Saqlanmagan kirim bilan boshqa sahifaga o‘tilsa — tasdiq so‘raladi (Common.kt, rememberLeaveGuard).
    rememberLeaveGuard(state) { listOf(form.query, form.qty, form.storage, form.docNo, form.note).takeIf { v -> v.any { it.isNotBlank() } } }
    val errors = remember { FormErrors() }
    val confirm = rememberConfirmState()
    val reasons = rememberOpsReasonState()
    var saved by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var filters by remember {
        mutableStateOf((intent as? PageIntent.HistoryOf)?.let { ReceiptsPageFilters(batchId = it.batchId, reagentId = it.reagentId) } ?: ReceiptsPageFilters())
    }
    var historyBatch by remember { mutableStateOf<Long?>(null) }
    var correcting by remember { mutableStateOf<ReceiptRow?>(null) }
    val selection = rememberTableSelection()
    val listRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val selectedBatch = batches.firstOrNull { it.batchId == form.batchId }
    val query = filters.toQuery()
    val rows = remember(tick, query) { state.load(emptyList()) { state.services.queries.receipts(query) } }
    val correctedIds = remember(rows) { rows.mapNotNull { it.correctsId }.toSet() }
    // Yangi kirim qo‘shilsa — jadval boshiga (aks holda yangi qator ko‘rinadigan joydan yuqorida qoladi).
    val tableList = rememberLazyListState()
    val newest = rows.maxOfOrNull { it.id }
    LaunchedEffect(newest) { if (newest != null) tableList.scrollToItem(0) }
    val filterBatchLabel = remember(tick, filters.batchId, filters.reagentId) {
        filters.batchId?.let { id -> state.load(null) { state.services.reagents.batch(id)?.let { b -> state.services.reagents.get(b.reagentId)?.name?.let { batchLabel(it, b.series, b.expiresOn) } } } }
            ?: filters.reagentId?.let { id -> state.load(null) { state.services.reagents.get(id)?.name } }
    }

    fun save(confirmDuplicate: Boolean = false) {
        val batch = selectedBatch
        if (batch == null) {
            errors.show(mapOf("batchId" to "Partiyani tanlang: reaktiv nomi yoki seriya raqamini yozing va ro‘yxatdan tanlang."))
            return
        }
        val input = form.toInput()
        state.run(
            { state.services.stock.addReceipt(batch.batchId, input, confirmDuplicate) },
            form = errors,
            onError = { e ->
                if (e is DuplicateOperationError) {
                    confirm.ask(
                        title = "Takroriy kirim",
                        message = e.message,
                        confirmLabel = "Baribir saqlash",
                        dismissLabel = "Orqaga",
                    ) { save(confirmDuplicate = true) }
                    true
                } else {
                    false
                }
            },
        ) {
            val amount = input.unit?.let { u -> opsParseQty(input.qtyText, u).first?.let { qtyText(it, u) } } ?: ""
            val remainder = state.load(null) { state.services.stock.remainder(batch.batchId) }
            saved = "${batchQuote(batch.name, batch.series)} — $amount, ${dateText(input.date)}." +
                (remainder?.let { " Partiyaning yangi qoldig‘i: ${qtyText(it, batch.unit)}." } ?: "")
            form.resetForNext()
            intentNotice = null
            generation++
        }
    }

    fun requestCancel(row: ReceiptRow) {
        val available = state.load(null) { state.services.stock.available(row.batchId, row.receivedOn) }
        val amount = "${row.enteredQty} ${row.enteredUnit.code}"
        if (available != null && row.qty > available) {
            confirm.ask(
                title = "Kirimni bekor qilib bo‘lmaydi",
                message = "${batchQuote(row.name, row.series)}: ${dateText(row.receivedOn)} dagi $amount kirimdan keyin partiyadan reaktiv " +
                    "berilgan. Bekor qilish uchun partiyada atigi ${qtyText(available, row.unit)} bor — kirim bekor qilinsa, qoldiq manfiy " +
                    "bo‘lib qoladi.\n\nAvval keyingi berishlarni bekor qiling yoki tuzating. Kirim miqdori xato kiritilgan bo‘lsa — " +
                    "«Tuzatish» orqali to‘g‘ri miqdorni yozing.",
                confirmLabel = "Partiya tarixini ko‘rish",
                dismissLabel = "Yopish",
            ) { historyBatch = row.batchId }
            return
        }
        reasons.ask(
            title = "Kirimni bekor qilish",
            message = "${batchQuote(row.name, row.series)}: ${dateText(row.receivedOn)} dagi $amount kirim bekor qilinadi va bu miqdor " +
                "partiya qoldig‘idan ayiriladi. Yozuv o‘chirilmaydi — jadvalda «Bekor qilingan» bo‘lib qoladi.",
            confirmLabel = "Bekor qilish",
            label = "Bekor qilish sababi",
        ) { reason ->
            state.run(
                { state.services.stock.cancelReceipt(row.id, reason) },
                onError = { e ->
                    confirm.ask("Kirimni bekor qilib bo‘lmaydi", e.message, confirmLabel = "Partiya tarixini ko‘rish", dismissLabel = "Yopish") {
                        historyBatch = row.batchId
                    }
                    true
                },
            ) { state.success("Kirim bekor qilindi: ${batchQuote(row.name, row.series)}, $amount.") }
        }
    }

    fun actionsFor(row: ReceiptRow): List<RowAction> = listOf(
        RowAction("Bekor qilish", AppIcons.Cancel, enabled = !row.cancelled, danger = true) { requestCancel(row) },
        RowAction("Tuzatish", AppIcons.Edit, enabled = !row.cancelled) { correcting = row },
        RowAction("Tarix", AppIcons.History) { historyBatch = row.batchId },
    )

    val columns = receiptsColumns(correctedIds) { actionsFor(it) }
    val layout = rememberAppTableLayout(state, RECEIPTS_TABLE_ID, columns)
    val selected = rows.firstOrNull { it.id == selection.key }

    ScrollPage(
        title = Page.RECEIPTS.title,
        subtitle = "Mavjud partiyaga qo‘shimcha kirim va kirimlar ro‘yxati. Yangi reaktiv yoki yangi seriya — «Yangi partiya».",
        actions = {
            AppButton(
                "Kirimlar ro‘yxati",
                onClick = { scope.launch { listRequester.bringIntoView() } },
                kind = ButtonKind.TEXT,
                icon = AppIcons.ChevronDown,
            )
            AppButton("Yangi partiya", onClick = { state.navigate(Page.ADD_REAGENT) }, icon = AppIcons.Add, modifier = Modifier.testTag("receipt-new-batch"))
        },
        testTag = "receipts-page",
    ) {
        saved?.let { text ->
            val requester = remember { BringIntoViewRequester() }
            LaunchedEffect(text, generation) {
                withFrameNanos { }
                requester.bringIntoView()
            }
            Box(Modifier.bringIntoViewRequester(requester)) {
                OpsSuccessPanel("Kirim saqlandi", text, onClose = { saved = null }, testTag = "receipt-success")
            }
        }
        intentNotice?.let { OpsNotice(it, Severity.WARNING, Modifier.fillMaxWidth(), testTag = "receipt-intent-notice") }
        FormSection(
            "Mavjud partiyaga qo‘shimcha kirim",
            description = "Partiyani nomi yoki seriyasi bo‘yicha toping. Miqdor partiya birligida yoki shu turdagi birlikda (l ↔ ml, kg ↔ g).",
        ) {
            key(generation) {
                FormRow {
                    ReceiptsBatchField(
                        query = form.query,
                        onQueryChange = { text ->
                            form.query = text
                            if (selectedBatch != null && text != selectedBatch.label()) form.batchId = null
                            errors.clear("batchId")
                        },
                        selected = selectedBatch,
                        options = batches,
                        onPick = { row ->
                            form.select(row)
                            errors.clear("batchId")
                            errors.clear("unit")
                        },
                        error = errors["batchId"],
                        modifier = Modifier.weight(2.2f),
                    )
                    DateField(
                        form.date, { form.date = it; errors.clear("date") }, label = "Kirim sanasi", required = true, today = today, maxDate = today,
                        error = errors["date"], modifier = Modifier.weight(0.8f), testTag = "receipt-date",
                    )
                    // Ikkalasi ham o‘qiladi — shaklda ko‘rsatilgan maydonlar sifatida belgilanadi.
                    val qtyError = errors["qtyText"]
                    val unitError = errors["unit"]
                    QtyField(
                        amount = form.qty,
                        onAmountChange = { form.qty = it; errors.clear("qtyText") },
                        unit = form.unit?.code,
                        onUnitChange = { code -> form.unit = MeasureUnit.of(code); errors.clear("unit") },
                        units = selectedBatch?.unit?.siblings?.map { it.code }.orEmpty(),
                        label = "Kirim miqdori",
                        required = true,
                        error = qtyError ?: unitError,
                        help = if (selectedBatch == null) "Avval partiyani tanlang" else null,
                        modifier = Modifier.weight(1f),
                        testTag = "receipt-qty",
                    )
                }
                selectedBatch?.let { ReceiptsBatchSummary(it) }
                FormRow {
                    AppTextField(form.receivedBy, { form.receivedBy = it; errors.clear("receivedBy") }, label = "Qabul qilgan xodim", error = errors["receivedBy"], modifier = Modifier.weight(1f), testTag = "receipt-receiver")
                    AppTextField(form.storage, { form.storage = it; errors.clear("storageLocation") }, label = "Saqlash joyi", placeholder = "Masalan: 1-shkaf", error = errors["storageLocation"], modifier = Modifier.weight(1f), testTag = "receipt-storage")
                    AppTextField(form.docNo, { form.docNo = it; errors.clear("docNo") }, label = "Hujjat raqami", placeholder = "Yuk xati, faktura…", error = errors["docNo"], modifier = Modifier.weight(0.9f), testTag = "receipt-doc")
                    AppTextField(form.note, { form.note = it; errors.clear("note") }, label = "Izoh", error = errors["note"], modifier = Modifier.weight(1.5f), testTag = "receipt-note")
                }
            }
            OpsFormButtons(
                message = if (errors.fields.isNotEmpty()) "Saqlanmadi: ${errors.fields.size} ta maydonda xato bor." else null,
                messageTag = "receipt-error-summary",
            ) {
                AppButton(
                    "Tozalash",
                    onClick = {
                        form.clearAll()
                        errors.clear()
                        saved = null
                        generation++
                    },
                    kind = ButtonKind.SECONDARY,
                    icon = AppIcons.Refresh,
                )
                Spacer(Modifier.width(8.dp))
                AppButton("Kirimni saqlash", onClick = { save() }, icon = AppIcons.Check, enabled = !state.busy, modifier = Modifier.testTag("receipt-save"))
            }
        }

        Column(Modifier.fillMaxWidth().bringIntoViewRequester(listRequester), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OpsSectionTitle(
                "Kirimlar ro‘yxati",
                "${rows.size} ta yozuv" + (if (filters.activeCount > 0) " — filtr bo‘yicha" else "") +
                    ". Boshlang‘ich kirimlar «Reaktiv qo‘shish»da, qo‘shimchalari shu sahifada kiritiladi.",
            )
            FilterBar(onClear = { filters = ReceiptsPageFilters() }, activeCount = filters.activeCount) {
                DateRangeField(filters.from, filters.to, { f, t -> filters = filters.copy(from = f, to = t) }, label = "Davr", today = today, fieldWidth = 128.dp, testTag = "receipts-filter-period")
                AppTextField(
                    filters.text, { filters = filters.copy(text = it) }, label = "Qidirish", placeholder = "Nomi, hujjat, xodim, izoh…",
                    leadingIcon = AppIcons.Search, modifier = Modifier.width(240.dp), testTag = "receipts-filter-text",
                )
                AppTextField(filters.series, { filters = filters.copy(series = it) }, label = "Seriya", modifier = Modifier.width(120.dp), testTag = "receipts-filter-series")
                CheckField(filters.includeCancelled, { filters = filters.copy(includeCancelled = it) }, "Bekor qilinganlar", testTag = "receipts-filter-cancelled")
                if (filters.batchId != null || filters.reagentId != null) {
                    Box(Modifier.padding(top = 20.dp)) {
                        AppButton(
                            (if (filters.batchId != null) "Partiya: " else "Reaktiv: ") + (filterBatchLabel ?: "—"),
                            onClick = { filters = filters.copy(batchId = null, reagentId = null) },
                            kind = ButtonKind.SECONDARY,
                            icon = AppIcons.Close,
                        )
                    }
                }
            }
            SelectionActionBar(
                title = selected?.let { "${dateText(it.receivedOn)} · ${batchLabel(it.name, it.series, it.expiresOn)} · ${it.enteredQty} ${it.enteredUnit.code}" },
                actions = selected?.let { actionsFor(it) }.orEmpty(),
                hint = "Kirimni tanlang — amallari (bekor qilish, tuzatish, tarix) shu yerda chiqadi.",
                testTag = "receipt-actions",
            )
            DataTable(
                rows = rows,
                columns = columns,
                rowKey = { it.id },
                layout = layout,
                modifier = Modifier.fillMaxWidth().height(OpsTableHeight),
                rowBackground = { if (it.cancelled) AppColors.SurfaceAlt else null },
                selection = selection,
                listState = tableList,
                onRowDoubleClick = { if (!it.cancelled) correcting = it },
                emptyText = if (filters.activeCount > 0) "Filtrga mos kirim topilmadi" else "Hali kirim yo‘q",
                emptyHint = if (filters.activeCount > 0) "Filtrlarni o‘zgartiring yoki «Filtrlarni tozalash» tugmasini bosing." else null,
                testTag = "receipts",
            )
        }
    }
    ConfirmHost(confirm, state.busy)
    OpsReasonHost(reasons, state.busy)
    correcting?.let { row -> ReceiptCorrectDialog(state, row) { correcting = null } }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

/** Kirimlar jadvali ustunlari. */
private fun receiptsColumns(correctedIds: Set<Long>, actions: (ReceiptRow) -> List<RowAction>): List<TableColumn<ReceiptRow>> = listOf(
    textColumn("date", "Sana", 96.dp, ColumnAlign.END, sortKey = { it.receivedOn }) { dateText(it.receivedOn) },
    textColumn("name", "Reaktiv nomi", 180.dp) { it.name },
    textColumn("series", "Seriya", 96.dp) { it.series.ifBlank { "—" } },
    textColumn("qty", "Miqdor", 90.dp, ColumnAlign.END, sortKey = { it.qty }) { it.enteredQty },
    textColumn("unit", "Birlik", 62.dp, ColumnAlign.CENTER) { it.enteredUnit.code },
    textColumn("type", "Turi", 112.dp) { if (it.isInitial) "Boshlang‘ich" else "Qo‘shimcha" },
    textColumn("receivedBy", "Qabul qilgan", 130.dp) { it.receivedBy.ifBlank { "—" } },
    textColumn("storage", "Saqlash joyi", 130.dp) { it.storageLocation.ifBlank { "—" } },
    textColumn("doc", "Hujjat", 100.dp) { it.docNo.ifBlank { "—" } },
    textColumn("note", "Izoh", 170.dp) { it.note.ifBlank { "—" } },
    TableColumn(
        id = "status", title = "Holat", width = 230.dp,
        text = { opsStatus(it.id, it.cancelled, correctedIds).label },
        cell = { row -> OpsStatusCell(opsStatus(row.id, row.cancelled, correctedIds), row.cancelReason, row.correctsId != null) },
    ),
    actionsColumn(width = 110.dp, maxInline = 3, actions = actions),
)

/** Tanlangan partiya haqida qisqa ma’lumot (shakl ichida). */
@Composable
private fun ReceiptsBatchSummary(row: BatchRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceAlt)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("receipt-batch-summary"),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Holati", fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted)
            Spacer(Modifier.height(2.dp))
            ExpiryChip(row.state)
        }
        InfoPair("Yaroqlilik muddati", dateText(row.expiresOn) + (daysPhrase(row.daysLeft)?.let { " ($it)" } ?: ""))
        InfoPair("Joriy qoldiq", qtyText(row.remainder, row.unit), valueColor = AppColors.Primary)
        InfoPair("Jami kelgan", qtyText(row.receivedQty, row.unit))
        InfoPair("Bo‘lim / saqlash joyi", placeText(row.departmentName, row.storageLocation))
        if (row.responsible.isNotBlank()) InfoPair("Mas’ul xodim", row.responsible)
    }
}

/**
 * Partiyani qidirib tanlash: nomi yoki seriyasi yoziladi, ro‘yxatda har bir partiya nomi, seriyasi, muddati, holati va
 * qoldig‘i bilan. ↑/↓ — tanlash, Enter — olish, Esc — yopish. Tanlangach maydonda partiyaning qisqa nomi turadi.
 */
@Composable
private fun ReceiptsBatchField(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: BatchRow?,
    options: List<BatchRow>,
    onPick: (BatchRow) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    testTag: String = "receipt-batch",
) {
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    val shown = if (field.text == query) field else TextFieldValue(query, TextRange(query.length))
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var highlighted by remember { mutableIntStateOf(-1) }
    var focused by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val filterText = if (showAll || (selected != null && query == selected.label())) "" else query
    val matches = remember(filterText, options) { options.filter { matchesQuery(filterText, it.name, it.series, it.formula) } }
    val visible = matches.take(RECEIPTS_PICKER_LIMIT)
    val open = expanded

    fun close() {
        expanded = false
        showAll = false
        highlighted = -1
    }

    fun pick(row: BatchRow) {
        val text = row.label()
        field = TextFieldValue(text, TextRange(text.length))
        close()
        onPick(row)
    }

    LabeledField(
        "Partiya",
        modifier,
        required = true,
        error = error,
        help = if (selected == null && error == null) "Reaktiv nomi yoki seriya raqamini yozing (masalan: spirt, ES-001)" else null,
    ) {
        Box {
            FieldFrame(
                Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width },
                focused = focused,
                isError = error != null,
                enabled = true,
                padding = PaddingValues(start = 10.dp, end = 3.dp),
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = shown,
                    onValueChange = { next ->
                        val changed = next.text != query
                        field = next
                        if (changed) {
                            onQueryChange(next.text)
                            expanded = true
                            showAll = false
                            highlighted = -1
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(testTag)
                        .onFocusChanged { s ->
                            focused = s.isFocused
                            if (!s.isFocused) close()
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (!open) {
                                        expanded = true
                                        showAll = query.isBlank() || selected != null
                                    } else {
                                        highlighted = (highlighted + 1).coerceAtMost(visible.size - 1)
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (open) highlighted = (highlighted - 1).coerceAtLeast(0)
                                    open
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    if (!open) return@onPreviewKeyEvent false
                                    val target = visible.getOrNull(highlighted) ?: visible.singleOrNull()
                                    if (target != null) pick(target) else close()
                                    true
                                }
                                Key.Escape -> {
                                    if (open) close()
                                    open
                                }
                                Key.Tab -> {
                                    close()
                                    false
                                }
                                else -> false
                            }
                        },
                    singleLine = true,
                    textStyle = FieldTextStyle,
                    cursorBrush = SolidColor(AppColors.Primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("Nomi yoki seriyasi…", style = FieldTextStyle.copy(color = AppColors.Disabled), maxLines = 1)
                            inner()
                        }
                    },
                )
                IconAction(
                    icon = AppIcons.DropDown,
                    label = "Ro‘yxatni ochish",
                    onClick = {
                        if (open) {
                            close()
                        } else {
                            expanded = true
                            showAll = true
                        }
                    },
                    tint = AppColors.Muted,
                    testTag = "$testTag-arrow",
                )
            }
            val scroll = rememberScrollState()
            LaunchedEffect(highlighted) {
                if (highlighted >= 0) {
                    val itemPx = with(density) { 40.dp.roundToPx() }
                    val top = highlighted * itemPx
                    val viewport = scroll.viewportSize
                    when {
                        top < scroll.value -> scroll.scrollTo(top)
                        viewport > 0 && top + itemPx > scroll.value + viewport -> scroll.scrollTo(top + itemPx - viewport)
                    }
                }
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { close() },
                properties = PopupProperties(focusable = false),
                scrollState = scroll,
                modifier = Modifier.width(with(density) { widthPx.toDp() }.coerceAtLeast(700.dp)).heightIn(max = 340.dp),
            ) {
                visible.forEachIndexed { index, row ->
                    ReceiptsBatchItem(row, highlighted = index == highlighted, current = row.batchId == selected?.batchId, onClick = { pick(row) }, tag = "$testTag-option-${row.batchId}")
                }
                when {
                    matches.isEmpty() -> Text(
                        "Mos partiya topilmadi. Yangi reaktiv yoki yangi seriya bo‘lsa — «Yangi partiya».",
                        fontSize = 13.sp,
                        color = AppColors.Muted,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    matches.size > visible.size -> Text(
                        "Yana ${matches.size - visible.size} ta partiya — qidiruvni aniqlashtiring.",
                        fontSize = 12.5.sp,
                        color = AppColors.Muted,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private const val RECEIPTS_PICKER_LIMIT = 60

@Composable
private fun ReceiptsBatchItem(row: BatchRow, highlighted: Boolean, current: Boolean, onClick: () -> Unit, tag: String) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (current) AppColors.Primary else AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(row.series.ifBlank { "seriyasiz" }, fontSize = 13.sp, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(100.dp))
                Text("muddati ${dateText(row.expiresOn)}", fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1, modifier = Modifier.width(146.dp))
                Box(Modifier.width(150.dp)) { ExpiryChip(row.state) }
                Text(
                    qtyText(row.remainder, row.unit),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.remainder == 0L) AppColors.Muted else AppColors.Text,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(90.dp),
                )
            }
        },
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .fillMaxWidth()
            .background(if (highlighted) AppColors.PrimarySoft else AppColors.Surface)
            .testTag(tag),
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

/**
 * Kirimni tuzatish: eski yozuv «Tuzatilgan» bo‘ladi, o‘rniga yangi kirim yoziladi (Stock.correctReceipt — bitta
 * tranzaksiyada). Sabab majburiy.
 */
@Composable
private fun ReceiptCorrectDialog(state: AppState, row: ReceiptRow, onDismiss: () -> Unit) {
    val today = remember { state.today() }
    val form = remember { FormErrors() }
    var date by remember { mutableStateOf<LocalDate?>(row.receivedOn) }
    var qty by remember { mutableStateOf(row.enteredQty) }
    var unit by remember { mutableStateOf(row.enteredUnit) }
    var receivedBy by remember { mutableStateOf(row.receivedBy) }
    var storage by remember { mutableStateOf(row.storageLocation) }
    var docNo by remember { mutableStateOf(row.docNo) }
    var note by remember { mutableStateOf(row.note) }
    var reason by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    fun save() {
        dialogError = null
        state.run(
            {
                state.services.stock.correctReceipt(
                    row.id,
                    ReceiptInput(date = date, qtyText = qty, unit = unit, receivedBy = receivedBy, storageLocation = storage, docNo = docNo, note = note),
                    reason,
                )
            },
            form = form,
            onError = { e ->
                if (e.fields.isEmpty()) {
                    dialogError = e.message
                    true
                } else {
                    false
                }
            },
        ) {
            onDismiss()
            state.success("Kirim tuzatildi: ${batchQuote(row.name, row.series)} — endi $qty ${unit.code}, ${dateText(date)}.")
        }
    }

    AppDialog(
        title = "Kirimni tuzatish",
        subtitle = "${batchLabel(row.name, row.series, row.expiresOn)} · ${dateText(row.receivedOn)} dagi ${row.enteredQty} ${row.enteredUnit.code} kirim",
        onDismiss = onDismiss,
        width = 820.dp,
        testTag = "receipt-correct-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton("Tuzatishni saqlash", onClick = { save() }, icon = AppIcons.Check, enabled = !state.busy, modifier = Modifier.testTag("rc-save"))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OpsNotice(
                "Eski yozuv o‘chirilmaydi: u «Tuzatilgan» deb belgilanadi va o‘rniga to‘g‘ri ma’lumotli yangi kirim yoziladi. " +
                    "Partiya qoldig‘i yangi miqdor bo‘yicha qayta hisoblanadi.",
                Severity.INFO,
                Modifier.fillMaxWidth(),
            )
            val qtyError = form["qtyText"]
            val unitError = form["unit"]
            FormRow {
                DateField(date, { date = it; form.clear("date") }, label = "Kirim sanasi", required = true, today = today, maxDate = today, error = form["date"], modifier = Modifier.weight(1f), testTag = "rc-date")
                QtyField(
                    amount = qty,
                    onAmountChange = { qty = it; form.clear("qtyText") },
                    unit = unit.code,
                    onUnitChange = { code -> MeasureUnit.of(code)?.let { unit = it }; form.clear("unit") },
                    units = row.unit.siblings.map { it.code },
                    label = "Kirim miqdori",
                    required = true,
                    error = qtyError ?: unitError,
                    modifier = Modifier.weight(1f),
                    testTag = "rc-qty",
                )
                AppTextField(receivedBy, { receivedBy = it; form.clear("receivedBy") }, label = "Qabul qilgan xodim", error = form["receivedBy"], modifier = Modifier.weight(1f), testTag = "rc-receiver")
            }
            FormRow {
                AppTextField(storage, { storage = it; form.clear("storageLocation") }, label = "Saqlash joyi", error = form["storageLocation"], modifier = Modifier.weight(1f), testTag = "rc-storage")
                AppTextField(docNo, { docNo = it; form.clear("docNo") }, label = "Hujjat raqami", error = form["docNo"], modifier = Modifier.weight(1f), testTag = "rc-doc")
                AppTextField(note, { note = it; form.clear("note") }, label = "Izoh", error = form["note"], modifier = Modifier.weight(1f), testTag = "rc-note")
            }
            AppTextField(
                reason, { reason = it; form.clear("reason") }, label = "Tuzatish sababi", required = true, placeholder = "Masalan: miqdor xato yozilgan",
                error = form["reason"], modifier = Modifier.fillMaxWidth(), testTag = "rc-reason",
            )
            dialogError?.let { OpsNotice(it, Severity.ERROR, Modifier.fillMaxWidth(), testTag = "rc-error") }
        }
    }
}

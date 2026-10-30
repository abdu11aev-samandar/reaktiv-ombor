package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uz.reaktivombor.desktop.core.DuplicateBatchError
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.report.ReportBuilder
import uz.reaktivombor.desktop.service.BatchEdit
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.MovementKind
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.service.ReagentEdit
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
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.GroupInfo
import uz.reaktivombor.desktop.ui.components.LabeledField
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableGrouping
import uz.reaktivombor.desktop.ui.components.TooltipBox
import uz.reaktivombor.desktop.ui.components.actionsColumn
import uz.reaktivombor.desktop.ui.components.rememberGroupExpansion
import uz.reaktivombor.desktop.ui.components.rememberTableSelection
import uz.reaktivombor.desktop.ui.components.rowNumberColumn
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import java.time.LocalDate

/** «Barcha reaktivlar» jadvalining ustunlar ko‘rinishi shu nom bilan sozlamalarda saqlanadi. */
const val REAGENTS_TABLE_ID = "reagents"

/** Filtr panelining holati (texnik topshiriq §12 — hamma parametrlar). */
@Immutable
private data class ReagentsPageFilters(
    val text: String = "",
    val series: String = "",
    val departmentId: Long? = null,
    val districtId: Long? = null,
    val producedFrom: LocalDate? = null,
    val producedTo: LocalDate? = null,
    val receivedFrom: LocalDate? = null,
    val receivedTo: LocalDate? = null,
    val expiresFrom: LocalDate? = null,
    val expiresTo: LocalDate? = null,
    val states: Set<ExpiryState> = emptySet(),
    val remainderMin: String = "",
    val remainderMax: String = "",
    val remainderUnit: MeasureUnit = MeasureUnit.L,
    val responsible: String = "",
    val includeArchived: Boolean = false,
    val includeEmpty: Boolean = true,
    val reagentId: Long? = null,
) {
    /** «Kengaytirilgan filtr» ichidagilar. */
    val advancedCount: Int
        get() = listOf(
            districtId != null,
            producedFrom != null || producedTo != null,
            receivedFrom != null || receivedTo != null,
            expiresFrom != null || expiresTo != null,
            remainderMin.isNotBlank() || remainderMax.isNotBlank(),
            responsible.isNotBlank(),
        ).count { it }

    val activeCount: Int
        get() = advancedCount + listOf(
            text.isNotBlank(), series.isNotBlank(), departmentId != null, states.isNotEmpty(),
            includeArchived, !includeEmpty, reagentId != null,
        ).count { it }
}

private fun initialFilters(intent: PageIntent?): ReagentsPageFilters = when (intent) {
    is PageIntent.ReagentsFiltered -> ReagentsPageFilters(reagentId = intent.reagentId, states = intent.states, includeEmpty = !intent.withStockOnly)
    else -> ReagentsPageFilters()
}

/** Qoldiq chegarasi: bo‘sh — yo‘q; noto‘g‘ri — xato matni. */
private fun parseBound(text: String, unit: MeasureUnit, label: String): Pair<Long?, String?> =
    if (text.isBlank()) {
        null to null
    } else {
        try {
            Qty.parse(text, unit, label, allowZero = true) to null
        } catch (e: UserError) {
            null to e.message
        }
    }

/**
 * «Barcha reaktivlar»: partiyalar jadvali reaktiv nomi bo‘yicha guruhlangan (bir xil nom doim yonma-yon, guruh ichida
 * FEFO), har bir qator o‘z holati rangida, §12 dagi barcha filtrlar, ustunlar sozlamasi saqlanadi, qator amallari.
 */
@Composable
fun ReagentsPage(state: AppState) {
    val intent = state.intent
    var filters by remember { mutableStateOf(initialFilters(intent)) }
    var advanced by remember { mutableStateOf(filters.advancedCount > 0) }
    var editBatch by remember { mutableStateOf((intent as? PageIntent.EditBatch)?.batchId) }
    var historyBatch by remember { mutableStateOf<Long?>(null) }
    val confirm = rememberConfirmState()
    val expansion = rememberGroupExpansion()
    val selection = rememberTableSelection()
    val listState = rememberLazyListState()

    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val (minBase, minError) = parseBound(filters.remainderMin, filters.remainderUnit, "Qoldiq (dan)")
    val (maxBase, maxError) = parseBound(filters.remainderMax, filters.remainderUnit, "Qoldiq (gacha)")
    val query = BatchFilter(
        text = filters.text.ifBlank { null },
        series = filters.series.ifBlank { null },
        departmentId = filters.departmentId,
        districtId = filters.districtId,
        producedFrom = filters.producedFrom,
        producedTo = filters.producedTo,
        receivedFrom = filters.receivedFrom,
        receivedTo = filters.receivedTo,
        expiresFrom = filters.expiresFrom,
        expiresTo = filters.expiresTo,
        remainderMin = minBase,
        remainderMax = maxBase,
        remainderUnit = if (minBase != null || maxBase != null) filters.remainderUnit else null,
        responsible = filters.responsible.ifBlank { null },
        includeArchived = filters.includeArchived,
        includeEmpty = filters.includeEmpty,
        reagentId = filters.reagentId,
    )
    // Filtr o‘zgarsa jadval boshidan ko‘rsatiladi.
    LaunchedEffect(query, filters.states) { listState.scrollToItem(0) }
    // Holat bo‘yicha filtr jadvalda: tugmalardagi sonlar qolgan filtrlar bo‘yicha hisoblanadi.
    val baseRows = remember(tick, today, query) { state.load(emptyList()) { state.services.queries.batchRows(query, today) } }
    val rows = remember(baseRows, filters.states) { if (filters.states.isEmpty()) baseRows else baseRows.filter { it.state in filters.states } }
    val counts = remember(baseRows) { baseRows.groupingBy { it.state }.eachCount() }
    val districts = remember(tick) { state.load(emptyList()) { state.services.directory.districts(includeInactive = true) } }
    val reagentName = remember(tick, filters.reagentId) {
        filters.reagentId?.let { id -> state.load(null) { state.services.reagents.get(id)?.name } }
    }

    /** Qoldig‘i bor partiya arxivlanmaydi (arxivga o‘tgan qoldiq jami hisobdan tushib qolardi): avval nolga tushiriladi. */
    fun stockBlocksArchive(row: BatchRow): Boolean {
        if (row.remainder <= 0) return false
        state.warning(
            "${batchQuote(row.name, row.series)} partiyasida ${qtyText(row.remainder, row.unit)} qoldiq bor. Arxivlashdan oldin uni nolga " +
                "tushiring: berish («Sarf va berish») yoki yaroqsiz bo‘lsa «Hisobdan chiqarish» («Yaroqlilik muddati nazorati» sahifasida).",
            title = "Arxivlab bo‘lmaydi",
        )
        return true
    }

    fun archive(row: BatchRow) {
        if (stockBlocksArchive(row)) return
        confirm.ask(
            title = "Partiyani arxivlash",
            message = "${batchQuote(row.name, row.series)} arxivga o‘tkaziladi. Arxivdagi partiyadan kirim va berish " +
                "qilinmaydi, tarixi va hisobotlardagi o‘rni saqlanadi. «Arxivdagilar» belgilansa ro‘yxatda ko‘rinadi.",
            confirmLabel = "Arxivlash",
        ) {
            state.run({ state.services.reagents.archiveBatch(row.batchId) }) {
                state.success("Partiya arxivlandi: ${batchQuote(row.name, row.series)}.", actionLabel = "Bekor qilish") {
                    state.run({ state.services.reagents.unarchiveBatch(row.batchId) }) { state.success("Partiya arxivdan chiqarildi.") }
                }
            }
        }
    }

    fun unarchive(row: BatchRow) {
        state.run({
            state.services.db.write {
                if (row.reagentArchived) state.services.reagents.unarchiveReagent(row.reagentId)
                if (row.archived) state.services.reagents.unarchiveBatch(row.batchId)
            }
        }) { state.success("Partiya arxivdan chiqarildi: ${batchQuote(row.name, row.series)}.") }
    }

    fun offerArchive(row: BatchRow, reason: String) {
        if (row.archived) {
            state.warning("$reason Partiya allaqachon arxivda.")
            return
        }
        if (row.remainder > 0) {
            // Arxivlashga ham qoldiq to‘sqinlik qiladi: tushuntiramiz va qoldiqni nolga tushiradigan sahifaga o‘tishni taklif qilamiz.
            confirm.ask(
                title = "O‘chirib bo‘lmaydi",
                message = "$reason\n\nPartiyada ${qtyText(row.remainder, row.unit)} qoldiq bor, shuning uchun uni arxivlab ham bo‘lmaydi. " +
                    "Avval qoldiqni nolga tushiring: berish («Sarf va berish») yoki yaroqsiz bo‘lsa «Hisobdan chiqarish».",
                confirmLabel = "Hisobdan chiqarishga o‘tish",
            ) {
                state.navigate(Page.EXPIRY, PageIntent.FilterExpiry(ExpiryState.entries.toSet(), row.batchId))
            }
            return
        }
        confirm.ask(
            title = "O‘chirib bo‘lmaydi",
            message = "$reason\n\nPartiyani arxivlash mumkin: u ro‘yxatdan yashiriladi, operatsiyalar tarixi va hisobotlardagi o‘rni saqlanadi.",
            confirmLabel = "Arxivlash",
        ) {
            state.run({ state.services.reagents.archiveBatch(row.batchId) }) { state.success("Partiya arxivlandi: ${batchQuote(row.name, row.series)}.") }
        }
    }

    fun requestDelete(row: BatchRow) {
        if (!state.load(false) { canDeleteBatch(state, row.batchId) }) {
            offerArchive(row, "${batchQuote(row.name, row.series)} partiyasining operatsiyalar tarixi bor — o‘chirib bo‘lmaydi.")
            return
        }
        confirm.ask(
            title = "Partiyani o‘chirish",
            message = "${batchQuote(row.name, row.series)} (${qtyText(row.receivedQty, row.unit)}) butunlay o‘chiriladi. " +
                "Bu partiyada boshlang‘ich kirimdan boshqa operatsiya yo‘q, shuning uchun o‘chirishga ruxsat bor. " +
                "Audit jurnalida yozuv qoladi.",
            confirmLabel = "O‘chirish",
            danger = true,
        ) {
            state.run(
                { state.services.reagents.deleteBatch(row.batchId) },
                onError = { e ->
                    if (e.message.contains("tarixi")) {
                        offerArchive(row, e.message)
                        true
                    } else {
                        false
                    }
                },
            ) { state.success("Partiya o‘chirildi: ${batchQuote(row.name, row.series)}.") }
        }
    }

    /**
     * «Eksport»: jadvalda ko‘rinib turgan partiyalar (hamma filtrlar, holat tugmalari ham qo‘llangan) «partiyalar
     * bo‘yicha ro‘yxat» (ReportKind.BATCHES) shaklida; fayl boshida — sahifaning barcha faol filtrlari.
     */
    fun exportBatches(format: ReportsFileFormat) {
        val shown = rows
        val lines = reagentsFilterLines(state, filters, reagentName, districts, minBase, maxBase)
        reportsExportFile(
            state,
            format,
            message = { data, file -> reportsSavedText(data, format, file) + ". " + reagentsExportFiltersText(data.filterLines) },
        ) { ReportBuilder(state.services).buildBatches(shown, lines, state.today()) }
    }

    fun actionsFor(row: BatchRow): List<RowAction> {
        val archived = row.archived || row.reagentArchived
        return listOf(
            RowAction("Tahrirlash", AppIcons.Edit) { editBatch = row.batchId },
            RowAction("Kirim", AppIcons.Receipts, enabled = !archived) { state.navigate(Page.RECEIPTS, PageIntent.ReceiptFor(row.batchId)) },
            RowAction("Berish", AppIcons.Issues, enabled = !archived && row.remainder > 0) {
                state.navigate(Page.ISSUES, PageIntent.IssueFrom(row.batchId))
            },
            RowAction("Tarix", AppIcons.History) { historyBatch = row.batchId },
            if (archived) RowAction("Arxivdan chiqarish", AppIcons.Undo) { unarchive(row) } else RowAction("Arxivlash", AppIcons.Archive) { archive(row) },
            RowAction("O‘chirish", AppIcons.Delete, danger = true) { requestDelete(row) },
        )
    }

    val columns = reagentColumns { actionsFor(it) }
    val selected = rows.firstOrNull { it.batchId == selection.key }
    val layout = rememberAppTableLayout(state, REAGENTS_TABLE_ID, columns)
    val reagentCount = rows.map { it.reagentId }.distinct().size

    PageScaffold(
        title = Page.REAGENTS.title,
        subtitle = "$reagentCount ta reaktiv, ${rows.size} ta partiya" + if (filters.activeCount > 0) " — filtr bo‘yicha" else "",
        actions = {
            ReportsSaveButtons(onSave = { exportBatches(it) }, enabled = !state.busy, testTagPrefix = "reagents-export", compact = true)
            Spacer(Modifier.width(4.dp))
            AppButton("Hammasini ochish", onClick = { expansion.expandAll() }, kind = ButtonKind.TEXT, icon = AppIcons.ExpandAll)
            AppButton("Hammasini yopish", onClick = { expansion.collapseAll() }, kind = ButtonKind.TEXT, icon = AppIcons.CollapseAll)
            AppButton("Reaktiv qo‘shish", onClick = { state.navigate(Page.ADD_REAGENT) }, icon = AppIcons.Add)
        },
    ) {
        FilterPanel(
            filters = filters,
            onChange = { filters = it },
            advanced = advanced,
            onAdvanced = { advanced = it },
            state = state,
            districts = districts,
            today = today,
            minError = minError,
            maxError = maxError,
            reagentName = reagentName,
        )
        StateRow(filters, { filters = it }, counts)
        SelectionActionBar(
            title = selected?.let { batchLabel(it.name, it.series, it.expiresOn) + " — qoldiq " + qtyText(it.remainder, it.unit) },
            actions = selected?.let { actionsFor(it) }.orEmpty(),
            hint = "Partiyani tanlang — amallari shu yerda chiqadi. Ikki marta bosish — tahrirlash.",
            testTag = "reagent-actions",
        )
        DataTable(
            rows = rows,
            columns = columns,
            rowKey = { it.batchId },
            layout = layout,
            modifier = Modifier.fillMaxWidth().weight(1f),
            rowBackground = { it.state.style.background },
            grouping = TableGrouping(key = { it.reagentId.toString() }, header = { GroupHeader(it) }),
            expansion = expansion,
            selection = selection,
            listState = listState,
            onRowDoubleClick = { editBatch = it.batchId },
            emptyText = if (filters.activeCount > 0) "Filtrga mos partiya topilmadi" else "Hali reaktiv kiritilmagan",
            emptyHint = if (filters.activeCount > 0) "Filtrlarni o‘zgartiring yoki «Filtrlarni tozalash» tugmasini bosing." else "«Reaktiv qo‘shish» orqali birinchi partiyani kiriting.",
            testTag = "reagents",
        )
    }
    ConfirmHost(confirm, state.busy)
    editBatch?.let { id -> BatchEditDialog(state, id) { editBatch = null } }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

/**
 * Eksport faylining filtr qatorlari — sahifadagi har bir faol filtr: "Reaktiv: …", "Qidiruv: …", "Seriya: …",
 * "Bo‘lim: …", "Tumanga berilgan: …", sanalar davri, "Holati: …", "Joriy qoldiq: …", "Mas’ul xodim: …",
 * "Arxivdagilar ham", "Qoldig‘i yo‘qlar ko‘rsatilmagan". Filtr bo‘lmasa — "Filtr: yo‘q".
 */
private fun reagentsFilterLines(
    state: AppState,
    f: ReagentsPageFilters,
    reagentName: String?,
    districts: List<Place>,
    minBase: Long?,
    maxBase: Long?,
): List<String> {
    val lines = mutableListOf<String>()
    if (f.reagentId != null) lines += "Reaktiv: ${reagentName ?: "—"}"
    f.text.trim().takeIf { it.isNotEmpty() }?.let { lines += "Qidiruv: $it" }
    f.series.trim().takeIf { it.isNotEmpty() }?.let { lines += "Seriya: $it" }
    f.departmentId?.let { id ->
        val name = state.load(null) { state.services.directory.get(PlaceKind.DEPARTMENT, id)?.name }
        lines += "Bo‘lim: ${name ?: "—"}"
    }
    f.districtId?.let { id -> lines += "Tumanga berilgan: ${districts.firstOrNull { it.id == id }?.name ?: "—"}" }
    if (f.producedFrom != null || f.producedTo != null) lines += "Ishlab chiqarilgan sana: " + ReportBuilder.period(f.producedFrom, f.producedTo)
    if (f.receivedFrom != null || f.receivedTo != null) lines += "Kelgan sana: " + ReportBuilder.period(f.receivedFrom, f.receivedTo)
    if (f.expiresFrom != null || f.expiresTo != null) lines += "Yaroqlilik muddati: " + ReportBuilder.period(f.expiresFrom, f.expiresTo)
    if (f.states.isNotEmpty()) lines += "Holati: " + EXPIRY_ORDER.filter { it in f.states }.joinToString(", ") { it.label }
    if (minBase != null || maxBase != null) {
        val unit = f.remainderUnit
        lines += "Joriy qoldiq: " + when {
            minBase != null && maxBase != null -> "${qtyText(minBase, unit)} — ${qtyText(maxBase, unit)}"
            minBase != null -> "${qtyText(minBase, unit)} dan"
            else -> "${qtyText(maxBase!!, unit)} gacha"
        }
    }
    f.responsible.trim().takeIf { it.isNotEmpty() }?.let { lines += "Mas’ul xodim: $it" }
    if (f.includeArchived) lines += "Arxivdagilar ham"
    if (!f.includeEmpty) lines += "Qoldig‘i yo‘qlar ko‘rsatilmagan"
    if (lines.isEmpty()) lines += "Filtr: yo‘q"
    return lines
}

/** Xabar uchun: "Qo‘llangan filtrlar: Bo‘lim: Gistologiya; Seriya: ES." yoki "Filtrsiz: …". */
private fun reagentsExportFiltersText(filterLines: List<String>): String {
    val applied = filterLines.filter { it != "Filtr: yo‘q" }
    return if (applied.isEmpty()) "Filtrsiz: barcha arxivlanmagan partiyalar." else "Qo‘llangan filtrlar: " + applied.joinToString("; ") + "."
}

/** Partiyani o‘chirish mumkinmi: yagona operatsiyasi — o‘zining bekor qilinmagan boshlang‘ich kirimi (Reagents.deleteBatch qoidasi). */
private fun canDeleteBatch(state: AppState, batchId: Long): Boolean {
    val q = state.services.queries
    val ledger = q.ledger(batchId)
    val receipts = q.receipts(OpFilter(batchId = batchId, includeCancelled = true))
    val items = q.issueItems(OpFilter(batchId = batchId, includeCancelled = true))
    return ledger.size == 1 && ledger[0].kind == MovementKind.RECEIPT &&
        receipts.size == 1 && receipts[0].isInitial && !receipts[0].cancelled && items.isEmpty()
}

@Composable
private fun FilterPanel(
    filters: ReagentsPageFilters,
    onChange: (ReagentsPageFilters) -> Unit,
    advanced: Boolean,
    onAdvanced: (Boolean) -> Unit,
    state: AppState,
    districts: List<Place>,
    today: LocalDate,
    minError: String?,
    maxError: String?,
    reagentName: String?,
) {
    FilterBar(onClear = { onChange(ReagentsPageFilters()) }, activeCount = filters.activeCount) {
        // Qidiruv yozilayotganda har harfda emas, to‘xtagandan keyin (~250 ms) so‘raladi — katta bazada qotmasin.
        DebouncedSearchField(filters.text, { onChange(filters.copy(text = it)) })
        AppTextField(filters.series, { onChange(filters.copy(series = it)) }, label = "Seriya raqami", modifier = Modifier.width(140.dp), testTag = "filter-series")
        DepartmentSelect(
            state, filters.departmentId, { onChange(filters.copy(departmentId = it)) },
            placeholder = "Hammasi", allowAdd = false, modifier = Modifier.width(170.dp), testTag = "filter-department",
            includeInactive = true,
        )
        Box(Modifier.padding(top = 20.dp)) {
            AppButton(
                (if (advanced) "Kamroq filtr" else "Kengaytirilgan filtr") + if (filters.advancedCount > 0) " (${filters.advancedCount})" else "",
                onClick = { onAdvanced(!advanced) },
                kind = ButtonKind.TEXT,
                icon = if (advanced) AppIcons.ChevronUp else AppIcons.ChevronDown,
            )
        }
        if (filters.reagentId != null) {
            Box(Modifier.padding(top = 20.dp)) {
                AppButton(
                    "Reaktiv: ${reagentName ?: "—"}",
                    onClick = { onChange(filters.copy(reagentId = null)) },
                    kind = ButtonKind.SECONDARY,
                    icon = AppIcons.Close,
                )
            }
        }
        if (advanced) {
            SelectField(
                value = districts.firstOrNull { it.id == filters.districtId },
                options = listOf<Place?>(null) + districts,
                onSelect = { onChange(filters.copy(districtId = it?.id)) },
                label = "Tumanga berilgan",
                placeholder = "Hammasi",
                optionLabel = { it?.name ?: "— Hammasi —" },
                modifier = Modifier.width(170.dp),
                testTag = "filter-district",
            )
            AppTextField(filters.responsible, { onChange(filters.copy(responsible = it)) }, label = "Mas’ul xodim", modifier = Modifier.width(160.dp), testTag = "filter-responsible")
            DateRangeField(
                filters.producedFrom, filters.producedTo, { f, t -> onChange(filters.copy(producedFrom = f, producedTo = t)) },
                label = "Ishlab chiqarilgan sana", today = today, fieldWidth = 124.dp, testTag = "filter-produced",
            )
            DateRangeField(
                filters.receivedFrom, filters.receivedTo, { f, t -> onChange(filters.copy(receivedFrom = f, receivedTo = t)) },
                label = "Kelgan sana", today = today, fieldWidth = 124.dp, testTag = "filter-received",
            )
            DateRangeField(
                filters.expiresFrom, filters.expiresTo, { f, t -> onChange(filters.copy(expiresFrom = f, expiresTo = t)) },
                label = "Yaroqlilik muddati", today = today, fieldWidth = 124.dp, testTag = "filter-expires",
            )
            LabeledField("Joriy qoldiq", error = minError ?: maxError) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(filters.remainderMin, { onChange(filters.copy(remainderMin = it)) }, placeholder = "dan", modifier = Modifier.width(84.dp), testTag = "filter-min")
                    Text("—", color = AppColors.Muted, modifier = Modifier.padding(horizontal = 6.dp))
                    AppTextField(filters.remainderMax, { onChange(filters.copy(remainderMax = it)) }, placeholder = "gacha", modifier = Modifier.width(84.dp), testTag = "filter-max")
                    Spacer(Modifier.width(6.dp))
                    SelectField(
                        filters.remainderUnit, MeasureUnit.entries, { onChange(filters.copy(remainderUnit = it)) },
                        optionLabel = { it.code }, modifier = Modifier.width(80.dp), testTag = "filter-unit",
                    )
                }
            }
        }
    }
}

/** Qidiruv maydoni: matn darhol ko‘rinadi, filtrga esa yozish to‘xtagach [SEARCH_DEBOUNCE_MILLIS] dan keyin o‘tadi. */
@Composable
private fun DebouncedSearchField(value: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    // Oxirgi yuborilgan matn: qiymat shundan farq qilsa — u tashqaridan o‘zgargan (masalan, «Filtrlarni tozalash»).
    var sent by remember { mutableStateOf(value) }
    val commit by rememberUpdatedState(onCommit)
    fun send() {
        if (text == sent) return
        sent = text
        commit(text)
    }
    LaunchedEffect(value) {
        if (value != sent) {
            sent = value
            text = value
        }
    }
    LaunchedEffect(text) {
        if (text == sent) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MILLIS)
        send()
    }
    AppTextField(
        text, { text = it },
        label = "Qidirish", placeholder = "Nomi, formula, joyi, izoh…", leadingIcon = AppIcons.Search,
        onEnter = { send() },
        modifier = Modifier.width(250.dp), testTag = "filter-text",
    )
}

/** Qidiruv yozilgach jadval shuncha kutib yangilanadi. */
internal const val SEARCH_DEBOUNCE_MILLIS = 250L

/** Holat tugmalari (chapda, sig‘masa o‘raladi) va «Qoldig‘i yo‘qlar ham» / «Arxivdagilar» belgilari (o‘ngda). */
@Composable
private fun StateRow(filters: ReagentsPageFilters, onChange: (ReagentsPageFilters) -> Unit, counts: Map<ExpiryState, Int>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ExpiryStateFilter(filters.states, { onChange(filters.copy(states = it)) }, counts = counts, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        CheckField(filters.includeEmpty, { onChange(filters.copy(includeEmpty = it)) }, "Qoldig‘i yo‘qlar ham", topPadding = 0.dp, testTag = "filter-empty")
        CheckField(filters.includeArchived, { onChange(filters.copy(includeArchived = it)) }, "Arxivdagilar", topPadding = 0.dp, testTag = "filter-archived")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupHeader(info: GroupInfo<BatchRow>) {
    val rows = info.rows
    val first = rows.first()
    val totals = rows.distinctBy { it.dimension }.sortedBy { it.dimension.ordinal }.joinToString(" · ") { qtyText(it.groupTotal, it.groupUnit) }
    // Sig‘masa keyingi qatorga o‘raladi: holat belgilari va «Minimal qoldiqdan kam» uzun nomda ham ko‘rinib tursin.
    FlowRow(
        Modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val center = Modifier.align(Alignment.CenterVertically)
        Text(first.name, center, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (first.formula.isNotBlank()) Text(first.formula, center, fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1)
        Text("${rows.size} ta partiya", center, fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1)
        Text("Umumiy qoldiq: $totals", center, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy, maxLines = 1)
        EXPIRY_ORDER.forEach { s ->
            val count = rows.count { it.state == s }
            if (count > 0) ExpiryChip(s, center, text = "${s.label}: $count")
        }
        if (rows.any { it.belowMin }) StatusChip("Minimal qoldiqdan kam", StatusStyles.RED.background, StatusStyles.RED.foreground, center, dot = null)
        if (rows.any { it.reagentArchived }) StatusChip("Reaktiv arxivda", AppColors.Background, AppColors.Muted, center, dot = null)
    }
}

/** DESIGN.md §6 dagi tartibda 16 ustun. */
private fun reagentColumns(actions: (BatchRow) -> List<RowAction>): List<TableColumn<BatchRow>> = listOf(
    rowNumberColumn(),
    textColumn("name", "Reaktiv nomi", 190.dp) { it.name },
    textColumn("series", "Seriya raqami", 110.dp) { it.series.ifBlank { "—" } },
    textColumn("place", "Bo‘lim / saqlash joyi", 180.dp) { placeText(it.departmentName, it.storageLocation) },
    textColumn("produced", "Ishlab chiqarilgan sana", 136.dp, ColumnAlign.END, sortKey = { it.producedOn }) { dateText(it.producedOn) },
    textColumn("received", "Kelgan sana", 100.dp, ColumnAlign.END, sortKey = { it.receivedOn }) { dateText(it.receivedOn) },
    textColumn("expires", "Yaroqlilik muddati", 108.dp, ColumnAlign.END, sortKey = { it.expiresOn }) { dateText(it.expiresOn) },
    textColumn("days", "Qolgan kunlar", 108.dp, ColumnAlign.END, sortKey = { it.daysLeft }) { daysText(it.daysLeft) },
    TableColumn(
        id = "state", title = "Holati", width = 186.dp, sortKey = { it.state.severity }, text = { it.state.label },
        cell = { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpiryChip(row.state)
                if (row.archived || row.reagentArchived) {
                    Spacer(Modifier.width(4.dp))
                    StatusChip("Arxiv", AppColors.Background, AppColors.Muted, dot = null)
                }
            }
        },
    ),
    textColumn("receivedQty", "Kelgan miqdor", 108.dp, ColumnAlign.END, sortKey = { it.receivedQty }) { qtyNumber(it.receivedQty, it.unit) },
    TableColumn(
        id = "issued", title = "Jami berilgan miqdor", width = 116.dp, align = ColumnAlign.END, sortKey = { it.issuedQty },
        text = { qtyNumber(it.issuedQty, it.unit) },
        cell = { row -> IssuedCell(row) },
        headerHint = ISSUED_HINT,
    ),
    TableColumn(
        id = "remainder", title = "Joriy qoldiq", width = 104.dp, align = ColumnAlign.END, sortKey = { it.remainder },
        text = { qtyNumber(it.remainder, it.unit) },
        cell = { row ->
            // Raqam minglik bo‘shlig‘ida bo‘linib, qolgani yashirinmasin: sig‘masa «…» bilan.
            Text(
                qtyNumber(row.remainder, row.unit),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (row.remainder == 0L) AppColors.Muted else AppColors.Text,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ),
    textColumn("groupTotal", "Umumiy qoldiq (shu nom)", 124.dp, ColumnAlign.END, sortKey = { it.groupTotal }) { qtyText(it.groupTotal, it.groupUnit) },
    textColumn("unit", "O‘lchov birligi", 84.dp, ColumnAlign.CENTER) { it.unit.code },
    textColumn("responsible", "Mas’ul xodim", 140.dp) { it.responsible.ifBlank { "—" } },
    actionsColumn(width = 170.dp, maxInline = 5, actions = actions),
)

/** «Jami berilgan miqdor» sarlavhasi izohi: qiymat sof — bo‘lim va tumanlardan qaytarilganlar ayirilgan. */
private const val ISSUED_HINT = "Bo‘lim va tumanlarga berilgan miqdor, qaytarilganlar ayirilgan (sof berilgan). " +
    "Hisobdan chiqarilgani bunga kirmaydi — u ⓘ belgisi izohida. Kelgan − berilgan − chiqarilgan = joriy qoldiq."

/**
 * Berilgan miqdor (qaytarilganlar ayirilgan); hisobdan chiqarilgani bo‘lsa — yonida belgi va izoh
 * (kelgan − berilgan − chiqarilgan = qoldiq).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IssuedCell(row: BatchRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (row.writtenOffQty > 0) {
            TooltipArea(
                tooltip = {
                    TooltipBox(
                        "Berilgan (qaytarilganlar ayirilgan): ${qtyText(row.issuedQty, row.unit)}. " +
                            "Bundan tashqari hisobdan chiqarilgan: ${qtyText(row.writtenOffQty, row.unit)}",
                    )
                },
                delayMillis = 300,
                tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
            ) {
                Icon(AppIcons.Info, contentDescription = "Hisobdan chiqarilgan", tint = AppColors.Muted, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(qtyNumber(row.issuedQty, row.unit), fontSize = 13.sp, color = AppColors.Text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- tahrirlash oynasi

/** Tahrirlash oynasi: reaktiv (nomi, formula, minimal qoldiq, izoh) va partiya tavsifi — bitta tranzaksiyada saqlanadi. */
@Composable
private fun BatchEditDialog(state: AppState, batchId: Long, onDismiss: () -> Unit) {
    val loaded = remember(batchId) {
        state.load(null) {
            val batch = state.services.reagents.batch(batchId) ?: throw UserError("Partiya topilmadi.")
            val reagent = state.services.reagents.get(batch.reagentId) ?: throw UserError("Reaktiv topilmadi.")
            batch to reagent
        }
    }
    if (loaded == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val (batch, reagent) = loaded
    val today = remember { state.today() }
    val form = remember { FormErrors() }
    var name by remember { mutableStateOf(reagent.name) }
    var formula by remember { mutableStateOf(reagent.formula) }
    var minStock by remember { mutableStateOf(reagent.minStock?.let { Qty.format(it, reagent.minStockUnit ?: batch.unit) } ?: "") }
    var minUnit by remember { mutableStateOf(reagent.minStockUnit ?: batch.unit) }
    var reagentNote by remember { mutableStateOf(reagent.note) }
    var series by remember { mutableStateOf(batch.series) }
    var manufacturer by remember { mutableStateOf(batch.manufacturer) }
    var producedOn by remember { mutableStateOf(batch.producedOn) }
    var receivedOn by remember { mutableStateOf(batch.receivedOn) }
    var expiresOn by remember { mutableStateOf(batch.expiresOn) }
    var departmentId by remember { mutableStateOf(batch.departmentId) }
    var storage by remember { mutableStateOf(batch.storageLocation) }
    var responsible by remember { mutableStateOf(batch.responsible) }
    var batchNote by remember { mutableStateOf(batch.note) }
    val minUnits = MeasureUnit.of(batch.unit.dimension)

    fun save() {
        state.run(
            {
                state.services.db.write {
                    val fields = LinkedHashMap<String, String>()
                    val messages = mutableListOf<String>()
                    fun collect(e: UserError, prefix: String) {
                        if (e.fields.isEmpty()) messages += e.message
                        e.fields.forEach { (k, v) ->
                            fields["$prefix$k"] = v
                            messages += v
                        }
                    }
                    try {
                        state.services.reagents.updateReagent(
                            reagent.id,
                            ReagentEdit(name, formula, minStock.ifBlank { null }, if (minStock.isBlank()) null else minUnit, reagentNote),
                        )
                    } catch (e: UserError) {
                        collect(e, "reagent.")
                    }
                    try {
                        state.services.reagents.updateBatch(
                            batchId,
                            BatchEdit(series, manufacturer, producedOn, receivedOn, expiresOn, departmentId, storage, responsible, batchNote),
                        )
                    } catch (e: DuplicateBatchError) {
                        fields["batch.series"] = e.message
                        messages += e.message
                    } catch (e: UserError) {
                        collect(e, "batch.")
                    }
                    if (messages.isNotEmpty()) throw UserError(messages.distinct().joinToString("\n"), fields)
                }
            },
            form = form,
        ) {
            onDismiss()
            state.success("O‘zgarishlar saqlandi: ${batchQuote(cleanTitle(name), series)}.")
        }
    }

    AppDialog(
        title = "Partiyani tahrirlash",
        subtitle = batchLabel(reagent.name, batch.series, batch.expiresOn),
        onDismiss = onDismiss,
        width = 900.dp,
        testTag = "edit-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton("Saqlash", onClick = { save() }, icon = AppIcons.Check, enabled = !state.busy, modifier = Modifier.testTag("edit-save"))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Reaktiv", "Nomi, formulasi va minimal qoldig‘i shu nomdagi barcha partiyalar uchun umumiy")
            FormRow {
                AppTextField(name, { name = it; form.clear("reagent.name") }, label = "Reaktiv nomi", required = true, error = form["reagent.name"], modifier = Modifier.weight(2f), testTag = "edit-name")
                AppTextField(formula, { formula = it; form.clear("reagent.formula") }, label = "Formula", error = form["reagent.formula"], modifier = Modifier.weight(1f), testTag = "edit-formula")
                QtyField(
                    minStock, { minStock = it; form.clear("reagent.minStockText") }, minUnit.code,
                    { code -> MeasureUnit.of(code)?.let { minUnit = it } }, minUnits.map { it.code },
                    label = "Minimal qoldiq", error = form["reagent.minStockText"], modifier = Modifier.weight(1f), testTag = "edit-min",
                )
            }
            AppTextField(reagentNote, { reagentNote = it; form.clear("reagent.note") }, label = "Reaktiv bo‘yicha izoh", error = form["reagent.note"], modifier = Modifier.fillMaxWidth(), testTag = "edit-reagent-note")
            SectionTitle("Partiya", "O‘lchov birligi: ${batch.unit.code} (${batch.unit.title}). Birlik va miqdorlar faqat operatsiyalar orqali o‘zgaradi.")
            FormRow {
                AppTextField(
                    series, { series = it; form.clear("batch.series") }, label = "Seriya raqami", required = batch.series.isNotEmpty(),
                    error = form["batch.series"], modifier = Modifier.weight(1f), testTag = "edit-series",
                )
                AppTextField(manufacturer, { manufacturer = it; form.clear("batch.manufacturer") }, label = "Ishlab chiqaruvchi", error = form["batch.manufacturer"], modifier = Modifier.weight(1.4f), testTag = "edit-manufacturer")
                DepartmentSelect(state, departmentId, { departmentId = it; form.clear("batch.departmentId") }, error = form["batch.departmentId"], modifier = Modifier.weight(1f), testTag = "edit-department")
            }
            FormRow {
                DateField(producedOn, { producedOn = it; form.clear("batch.producedOn") }, label = "Ishlab chiqarilgan sana", today = today, maxDate = today, error = form["batch.producedOn"], modifier = Modifier.weight(1f), testTag = "edit-produced")
                DateField(receivedOn, { receivedOn = it; form.clear("batch.receivedOn") }, label = "Kelgan sana", required = batch.receivedOn != null, today = today, maxDate = today, error = form["batch.receivedOn"], modifier = Modifier.weight(1f), testTag = "edit-received")
                DateField(
                    expiresOn, { expiresOn = it; form.clear("batch.expiresOn") }, label = "Yaroqlilik muddati", required = batch.expiresOn != null, today = today,
                    error = form["batch.expiresOn"], help = expiryHelpText(expiresOn, today), modifier = Modifier.weight(1f), testTag = "edit-expires",
                )
            }
            FormRow {
                AppTextField(storage, { storage = it; form.clear("batch.storageLocation") }, label = "Saqlash joyi", error = form["batch.storageLocation"], modifier = Modifier.weight(1f), testTag = "edit-storage")
                AppTextField(responsible, { responsible = it; form.clear("batch.responsible") }, label = "Mas’ul xodim", error = form["batch.responsible"], modifier = Modifier.weight(1f), testTag = "edit-responsible")
            }
            AppTextField(batchNote, { batchNote = it; form.clear("batch.note") }, label = "Partiya bo‘yicha izoh", error = form["batch.note"], modifier = Modifier.fillMaxWidth(), testTag = "edit-note")
        }
    }
}

private fun cleanTitle(name: String): String = name.trim().replace(Regex("\\s+"), " ")

/** Sarlavha va izoh (oyna va shakl bo‘limlari ichida). */
@Composable
private fun SectionTitle(title: String, hint: String?) {
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy)
        if (hint != null) Text(hint, fontSize = 12.sp, lineHeight = 16.sp, color = AppColors.Muted)
    }
}

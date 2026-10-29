package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.DuplicateOperationError
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.IssueConfirm
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
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableSelection
import uz.reaktivombor.desktop.ui.components.TableSort
import uz.reaktivombor.desktop.ui.components.actionsColumn
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import java.time.LocalDate

/** Standart filtr: muddati o‘tgan, qizil va sariq. */
private val EXPIRY_DEFAULT_STATES: Set<ExpiryState> = setOf(ExpiryState.EXPIRED, ExpiryState.RED, ExpiryState.YELLOW)

/** Qolgan kunlar bo‘yicha (muddatsizlar oxirida), so‘ng nomi va seriyasi. */
private val expiryRowOrder = compareBy<BatchRow>({ it.daysLeft == null }, { it.daysLeft }, { nameKey(it.name) }, { it.series.lowercase() }, { it.batchId })

/**
 * «Yaroqlilik muddati nazorati» (texnik topshiriq §9): holatlar bo‘yicha tugmalar (sonlari bilan; standarti — muddati
 * o‘tgan, qizil, sariq), qolgan kunlar bo‘yicha tartiblangan jadval (butun qator holat rangida), muddati o‘tgan qoldiq
 * bo‘lsa qizil ogohlantirish, amallar: Hisobdan chiqarish (sabab majburiy), Arxivlash, Tarix.
 */
@Composable
fun ExpiryPage(state: AppState) {
    val intent = remember { state.intent as? PageIntent.FilterExpiry }
    var states by remember { mutableStateOf(intent?.states ?: EXPIRY_DEFAULT_STATES) }
    var text by remember { mutableStateOf("") }
    var departmentId by remember { mutableStateOf<Long?>(null) }
    var includeEmpty by remember { mutableStateOf(false) }
    var includeArchived by remember { mutableStateOf(false) }
    var writeOffBatch by remember { mutableStateOf<Long?>(null) }
    var historyBatch by remember { mutableStateOf<Long?>(null) }
    val confirm = rememberConfirmState()
    val selection = remember { TableSelection().apply { key = intent?.batchId } }
    val listState = rememberLazyListState()

    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val query = BatchFilter(text = text.ifBlank { null }, departmentId = departmentId, includeEmpty = includeEmpty, includeArchived = includeArchived)
    val baseRows = remember(tick, today, query) { state.load(emptyList()) { state.services.queries.batchRows(query, today).sortedWith(expiryRowOrder) } }
    val counts = remember(baseRows) { baseRows.groupingBy { it.state }.eachCount() }
    val rows = remember(baseRows, states) { if (states.isEmpty()) baseRows else baseRows.filter { it.state in states } }
    val expiredWithStock = remember(tick, today) {
        state.load(0) { state.services.queries.batchRows(BatchFilter(states = setOf(ExpiryState.EXPIRED), includeEmpty = false), today).size }
    }
    val columns = expiryColumns { actionsFor(state, it, confirm, { id -> writeOffBatch = id }, { id -> historyBatch = id }) }
    val layout = rememberAppTableLayout(state, "expiry", columns, defaultSort = TableSort("days"))
    val selected = rows.firstOrNull { it.batchId == selection.key }

    // Bildirishnomadan kelgan partiya: ko‘rinadigan joyga aylantiriladi (bir marta).
    var focused by remember { mutableStateOf(intent?.batchId == null) }
    LaunchedEffect(rows) {
        if (focused) return@LaunchedEffect
        val index = rows.indexOfFirst { it.batchId == intent?.batchId }
        val sort = layout.sort
        if (index >= 0 && (sort == null || (sort.columnId == "days" && !sort.descending))) listState.scrollToItem((index - 2).coerceAtLeast(0))
        focused = true
    }

    PageScaffold(
        title = Page.EXPIRY.title,
        subtitle = "${rows.size} ta partiya" + (if (states.isNotEmpty() && states.size < EXPIRY_ORDER.size) " — tanlangan holatlar bo‘yicha" else "") +
            " · qolgan kunlar bo‘yicha tartiblangan",
    ) {
        if (expiredWithStock > 0) {
            Banner(
                "Muddati o‘tgan reaktivlarni ishlatmang. $expiredWithStock ta partiyaning yaroqlilik muddati o‘tgan va omborda " +
                    "qoldig‘i bor — hisobdan chiqaring yoki arxivlang.",
                severity = Severity.ERROR,
                title = "Muddati o‘tgan reaktivlar",
                actionLabel = if (states != setOf(ExpiryState.EXPIRED)) "Faqat ularni ko‘rsatish" else null,
                onAction = { states = setOf(ExpiryState.EXPIRED) },
                testTag = "expiry-banner",
            )
        }
        FilterBar(
            onClear = {
                text = ""
                departmentId = null
                includeEmpty = false
                includeArchived = false
                states = EXPIRY_DEFAULT_STATES
            },
            activeCount = listOf(text.isNotBlank(), departmentId != null, includeEmpty, includeArchived, states != EXPIRY_DEFAULT_STATES).count { it },
        ) {
            AppTextField(
                text, { text = it },
                label = "Qidirish", placeholder = "Nomi, seriya, joyi…", leadingIcon = AppIcons.Search,
                modifier = Modifier.width(240.dp), testTag = "expiry-text",
            )
            DepartmentSelect(
                state, departmentId, { departmentId = it },
                placeholder = "Hammasi", allowAdd = false, modifier = Modifier.width(170.dp), testTag = "expiry-department",
            )
            CheckField(includeEmpty, { includeEmpty = it }, "Qoldig‘i yo‘qlarni ham ko‘rsatish", testTag = "expiry-empty")
            CheckField(includeArchived, { includeArchived = it }, "Arxivdagilar", testTag = "expiry-archived")
        }
        ExpiryStateChips(states, { states = it }, counts, baseRows.size)
        SelectionActionBar(
            title = selected?.let { "${it.label()} — ${daysPhrase(it.daysLeft) ?: "muddati kiritilmagan"}, qoldiq ${qtyText(it.remainder, it.unit)}" },
            actions = selected?.let { actionsFor(state, it, confirm, { id -> writeOffBatch = id }, { id -> historyBatch = id }) }.orEmpty(),
            hint = "Partiyani tanlang — amallari shu yerda chiqadi. Ikki marta bosish — partiya tarixi.",
            testTag = "expiry-actions",
        )
        DataTable(
            rows = rows,
            columns = columns,
            rowKey = { it.batchId },
            layout = layout,
            modifier = Modifier.fillMaxWidth().weight(1f),
            rowBackground = { it.state.style.background },
            selection = selection,
            listState = listState,
            onRowDoubleClick = { historyBatch = it.batchId },
            emptyText = if (baseRows.isEmpty()) "Partiya topilmadi" else "Tanlangan holatlarda partiya yo‘q",
            emptyHint = if (states == EXPIRY_DEFAULT_STATES && baseRows.isNotEmpty()) "Muddati yaqinlashgan yoki o‘tgan, qoldig‘i bor partiya yo‘q." else "Holat tugmalari yoki filtrlarni o‘zgartiring.",
            testTag = "expiry",
        )
    }
    ConfirmHost(confirm, state.busy)
    writeOffBatch?.let { id ->
        val row = baseRows.firstOrNull { it.batchId == id }
        if (row != null) {
            ExpiryWriteOffDialog(state, row, confirm) { writeOffBatch = null }
        } else {
            LaunchedEffect(id) { writeOffBatch = null }
        }
    }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

/** Qator amallari: Hisobdan chiqarish, Arxivlash (yoki Arxivdan chiqarish), Tarix. */
private fun actionsFor(
    state: AppState,
    row: BatchRow,
    confirm: ConfirmState,
    onWriteOff: (Long) -> Unit,
    onHistory: (Long) -> Unit,
): List<RowAction> {
    val archived = row.archived || row.reagentArchived
    return listOf(
        RowAction("Hisobdan chiqarish", AppIcons.Cancel, enabled = !archived && row.remainder > 0, danger = true) { onWriteOff(row.batchId) },
        if (archived) {
            RowAction("Arxivdan chiqarish", AppIcons.Undo) {
                state.run({
                    state.services.db.write {
                        if (row.reagentArchived) state.services.reagents.unarchiveReagent(row.reagentId)
                        if (row.archived) state.services.reagents.unarchiveBatch(row.batchId)
                    }
                }) { state.success("Partiya arxivdan chiqarildi: ${batchQuote(row.name, row.series)}.") }
            }
        } else {
            RowAction("Arxivlash", AppIcons.Archive) { askArchive(state, row, confirm) }
        },
        RowAction("Tarix", AppIcons.History) { onHistory(row.batchId) },
    )
}

private fun askArchive(state: AppState, row: BatchRow, confirm: ConfirmState) {
    val stockNote = if (row.remainder > 0) {
        " Omborda qoldig‘i bor: ${qtyText(row.remainder, row.unit)} — arxivlash qoldiqni hisobdan chiqarmaydi; kerak bo‘lsa avval «Hisobdan chiqarish»ni bajaring."
    } else {
        ""
    }
    confirm.ask(
        title = "Partiyani arxivlash",
        message = "${batchQuote(row.name, row.series)} arxivga o‘tkaziladi va ro‘yxatlardan yashiriladi.$stockNote " +
            "Arxivdagi partiyadan berish va kirim qilinmaydi; tarix va hisobotlar saqlanadi.",
        confirmLabel = "Arxivlash",
    ) {
        state.run({ state.services.reagents.archiveBatch(row.batchId) }) {
            state.success("Partiya arxivlandi: ${batchQuote(row.name, row.series)}.")
        }
    }
}

/** Holat tugmalari (bir nechtasini tanlash mumkin) + «Hammasi»; har birida son. Test belgilari: `state-chip-<HOLAT>`, `state-chip-ALL`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpiryStateChips(selected: Set<ExpiryState>, onChange: (Set<ExpiryState>) -> Unit, counts: Map<ExpiryState, Int>, total: Int) {
    val all = selected.isEmpty() || selected.containsAll(EXPIRY_ORDER)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EXPIRY_ORDER.forEach { s ->
            val active = !all && s in selected
            val style = s.style
            ExpiryChipButton(
                label = s.label,
                count = counts[s] ?: 0,
                active = active,
                background = style.background,
                foreground = style.foreground,
                accent = style.accent,
                tag = "state-chip-${s.name}",
            ) { onChange(if (all) setOf(s) else if (active) selected - s else selected + s) }
        }
        ExpiryChipButton(
            label = "Hammasi",
            count = total,
            active = all,
            background = AppColors.PrimarySoft,
            foreground = AppColors.Primary,
            accent = AppColors.Primary,
            tag = "state-chip-ALL",
        ) { onChange(emptySet()) }
    }
}

@Composable
private fun ExpiryChipButton(
    label: String,
    count: Int,
    active: Boolean,
    background: Color,
    foreground: Color,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier
            .height(30.dp)
            .clip(shape)
            .background(if (active) background else AppColors.Surface)
            .border(if (active) 1.5.dp else 1.dp, if (active) accent else AppColors.Border, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Icon(AppIcons.Check, contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
        } else {
            androidx.compose.foundation.layout.Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) foreground else AppColors.Text, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) foreground else AppColors.Muted)
    }
}

private fun expiryColumns(actions: (BatchRow) -> List<RowAction>): List<TableColumn<BatchRow>> = listOf(
    textColumn("name", "Reaktiv nomi", 134.dp) { it.name },
    textColumn("series", "Seriya raqami", 76.dp) { it.series.ifBlank { "—" } },
    textColumn("place", "Bo‘lim / joy", 136.dp) { placeText(it.departmentName, it.storageLocation) },
    textColumn("expires", "Yaroqlilik muddati", 96.dp, ColumnAlign.END, sortKey = { it.expiresOn }) { dateText(it.expiresOn) },
    TableColumn(
        id = "days", title = "Qolgan kunlar", width = 122.dp, align = ColumnAlign.END, sortKey = { it.daysLeft },
        text = { daysText(it.daysLeft) },
        cell = { row ->
            Text(
                daysText(row.daysLeft),
                fontSize = 13.sp,
                fontWeight = if (row.state == ExpiryState.EXPIRED || row.state == ExpiryState.RED) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.state == ExpiryState.EXPIRED || row.state == ExpiryState.RED) row.state.style.foreground else AppColors.Text,
                maxLines = 1,
            )
        },
    ),
    TableColumn(
        id = "state", title = "Holati", width = 172.dp, sortKey = { it.state.severity }, text = { it.state.label },
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
    TableColumn(
        id = "remainder", title = "Qoldiq", width = 72.dp, align = ColumnAlign.END, sortKey = { it.remainder },
        text = { qtyNumber(it.remainder, it.unit) },
        cell = { row ->
            Text(
                qtyNumber(row.remainder, row.unit),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (row.remainder == 0L) AppColors.Muted else AppColors.Text,
                maxLines = 1,
            )
        },
    ),
    textColumn("unit", "Birlik", 58.dp, ColumnAlign.CENTER) { it.unit.code },
    textColumn("responsible", "Mas’ul xodim", 100.dp) { it.responsible.ifBlank { "—" } },
    actionsColumn(width = 96.dp, maxInline = 3, actions = actions),
)

// ---------------------------------------------------------------- hisobdan chiqarish oynasi

/**
 * Hisobdan chiqarish: sana (bugun), miqdor (standarti — butun qoldiq; partiya o‘lchov turidagi istalgan birlikda) va
 * sabab (majburiy) → Stock.writeOff. Takroriy operatsiya ogohlantirishi tasdiq bilan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpiryWriteOffDialog(state: AppState, row: BatchRow, confirm: ConfirmState, onDismiss: () -> Unit) {
    val today = remember { state.today() }
    val form = remember { FormErrors() }
    var date by remember { mutableStateOf<LocalDate?>(today) }
    var qty by remember { mutableStateOf(Qty.format(row.remainder, row.unit)) }
    var unit by remember { mutableStateOf(row.unit) }
    var reason by remember { mutableStateOf("") }
    var generalError by remember { mutableStateOf<String?>(null) }
    val units = MeasureUnit.of(row.unit.dimension)

    fun save(allowDuplicate: Boolean = false) {
        generalError = null
        state.run(
            { state.services.stock.writeOff(row.batchId, date, qty, unit, reason, IssueConfirm(allowDuplicate = allowDuplicate)) },
            form = form,
            onError = { e: UserError ->
                when {
                    e is DuplicateOperationError -> {
                        confirm.ask(title = "Takroriy operatsiya", message = e.message, confirmLabel = "Baribir saqlash", dismissLabel = "Orqaga") {
                            save(allowDuplicate = true)
                        }
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
            val left = runCatching { state.services.stock.remainder(row.batchId) }.getOrNull()
            state.success(
                "Hisobdan chiqarildi: ${batchQuote(row.name, row.series)} — $amount." + (left?.let { " Qoldiq: ${qtyText(it, row.unit)}." } ?: ""),
            )
        }
    }

    AppDialog(
        title = "Hisobdan chiqarish",
        subtitle = row.label(),
        onDismiss = onDismiss,
        width = 660.dp,
        testTag = "writeoff-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton(
                "Hisobdan chiqarish",
                onClick = { save() },
                kind = ButtonKind.DANGER,
                icon = AppIcons.Cancel,
                enabled = !state.busy,
                modifier = Modifier.testTag("writeoff-save"),
            )
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text("Holati", fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted)
                Spacer(Modifier.height(2.dp))
                ExpiryChip(row.state)
            }
            InfoPair("Yaroqlilik muddati", dateText(row.expiresOn) + (daysPhrase(row.daysLeft)?.let { " ($it)" } ?: ""))
            InfoPair("Bo‘lim / joy", placeText(row.departmentName, row.storageLocation))
            InfoPair("Joriy qoldiq", qtyText(row.remainder, row.unit), valueColor = AppColors.Primary)
        }
        FormRow {
            DateField(
                date, { date = it; form.clear("date") },
                label = "Sana", required = true, today = today, minDate = row.receivedOn, maxDate = today,
                error = form["date"], modifier = Modifier.weight(1f), testTag = "writeoff-date",
            )
            QtyField(
                qty, { qty = it; form.clear("qtyText") }, unit.code,
                { code -> MeasureUnit.of(code)?.let { unit = it; form.clear("unit") } }, units.map { it.code },
                label = "Miqdor", required = true, error = form["qtyText"] ?: form["unit"],
                help = "Standarti — butun qoldiq", modifier = Modifier.weight(1f), testTag = "writeoff-qty",
            )
        }
        AppTextField(
            reason, { reason = it; form.clear("note") },
            label = "Sabab", required = true, placeholder = "Masalan: yaroqlilik muddati o‘tgan",
            error = form["note"], modifier = Modifier.fillMaxWidth(), testTag = "writeoff-reason",
        )
        generalError?.let { Banner(it, severity = Severity.ERROR, testTag = "writeoff-error") }
        Text(
            "Hisobdan chiqarilgan miqdor qoldiqdan ayiriladi va «Hisobdan chiqarish» operatsiyasi sifatida tarixda qoladi " +
                "(xato bo‘lsa, «Sarf va berish» sahifasida bekor qilinadi).",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = AppColors.Muted,
        )
    }
}

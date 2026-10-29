package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.matchesQuery
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.ReagentEdit
import uz.reaktivombor.desktop.service.StockTotal
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
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableSelection
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles

/** «Umumiy zaxiralar» filtrlari. */
@Immutable
private data class StockPageFilters(
    val text: String = "",
    val belowMinOnly: Boolean = false,
    val includeArchived: Boolean = false,
    val reagentId: Long? = null,
) {
    val activeCount: Int get() = listOf(text.isNotBlank(), belowMinOnly, includeArchived, reagentId != null).count { it }
}

/** Jadval kaliti: reaktiv + o‘lchov turi. */
private val StockTotal.key: String get() = "$reagentId-${dimension.name}"

/** Minimal qoldiq shu qatorning o‘lchov turida belgilanganmi. */
private val StockTotal.hasMin: Boolean get() = minStock != null && minStockUnit?.dimension == dimension

/**
 * «Umumiy zaxiralar» (texnik topshiriq §5, §11): reaktiv + o‘lchov turi bo‘yicha jamlar (kelgan, berilgan, hisobdan
 * chiqarilgan, joriy qoldiq, minimal qoldiq va holat, eng yaqin muddat + eng xavfli holat). Qator → «Partiyalar» oynasi
 * (har bir partiya o‘z holati rangida), «Minimal qoldiqni belgilash».
 */
@Composable
fun StockPage(state: AppState) {
    val intent = remember { state.intent as? PageIntent.StockFiltered }
    var filters by remember { mutableStateOf(StockPageFilters(belowMinOnly = intent?.belowMinOnly ?: false, reagentId = intent?.reagentId)) }
    var batchesOf by remember { mutableStateOf<String?>(null) }
    var minFor by remember { mutableStateOf<String?>(null) }
    var historyBatch by remember { mutableStateOf<Long?>(null) }
    val selection = remember { TableSelection() }

    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val query = BatchFilter(includeArchived = filters.includeArchived, reagentId = filters.reagentId)
    val totals = remember(tick, today, query) { state.load(emptyList()) { state.services.queries.stockTotals(query, today) } }
    val rows = remember(totals, filters.text, filters.belowMinOnly) {
        totals.filter { matchesQuery(filters.text, it.name, it.formula) && (!filters.belowMinOnly || it.belowMin) }
    }
    val belowMinCount = totals.count { it.belowMin }
    val reagentName = remember(tick, filters.reagentId) {
        filters.reagentId?.let { id -> totals.firstOrNull { it.reagentId == id }?.name ?: state.load(null) { state.services.reagents.get(id)?.name } }
    }
    LaunchedEffect(Unit) {
        intent?.reagentId?.let { id -> rows.firstOrNull { it.reagentId == id }?.let { selection.key = it.key } }
    }
    val selected = rows.firstOrNull { it.key == selection.key }

    fun actionsFor(row: StockTotal): List<RowAction> = listOf(
        RowAction("Partiyalar", AppIcons.Stock) { batchesOf = row.key },
        RowAction("Minimal qoldiqni belgilash", AppIcons.Edit) { minFor = row.key },
        RowAction("Barcha reaktivlarda", AppIcons.Reagents) { state.navigate(Page.REAGENTS, PageIntent.ReagentsFiltered(reagentId = row.reagentId)) },
    )

    val columns = remember { stockColumns() }
    val layout = rememberAppTableLayout(state, "stock", columns)

    PageScaffold(
        title = Page.STOCK.title,
        subtitle = "Reaktiv va o‘lchov turi bo‘yicha jami: ${rows.size} ta" +
            (if (belowMinCount > 0) " · minimal qoldiqdan kam: $belowMinCount ta" else "") +
            if (filters.activeCount > 0) " — filtr bo‘yicha" else "",
    ) {
        FilterBar(onClear = { filters = StockPageFilters() }, activeCount = filters.activeCount) {
            AppTextField(
                filters.text, { filters = filters.copy(text = it) },
                label = "Qidirish", placeholder = "Reaktiv nomi yoki formula", leadingIcon = AppIcons.Search,
                modifier = Modifier.width(260.dp), testTag = "stock-text",
            )
            CheckField(filters.belowMinOnly, { filters = filters.copy(belowMinOnly = it) }, "Faqat minimaldan kamlar ($belowMinCount)", testTag = "stock-below-min")
            CheckField(filters.includeArchived, { filters = filters.copy(includeArchived = it) }, "Arxivdagilar ham", testTag = "stock-archived")
            if (filters.reagentId != null) {
                Box(Modifier.padding(top = 20.dp)) {
                    AppButton(
                        "Reaktiv: ${reagentName ?: "—"}",
                        onClick = { filters = filters.copy(reagentId = null) },
                        kind = ButtonKind.SECONDARY,
                        icon = AppIcons.Close,
                        modifier = Modifier.testTag("stock-reagent-chip"),
                    )
                }
            }
        }
        SelectionActionBar(
            title = selected?.let { "${it.name} — qoldiq ${qtyText(it.remainder, it.unit)}" },
            actions = selected?.let { actionsFor(it) }.orEmpty(),
            hint = "Qatorni tanlang — amallari shu yerda chiqadi. Ikki marta bosish — partiyalar ro‘yxati.",
            testTag = "stock-actions",
        )
        DataTable(
            rows = rows,
            columns = columns,
            rowKey = { it.key },
            layout = layout,
            modifier = Modifier.fillMaxWidth().weight(1f),
            rowBackground = { if (it.belowMin) StatusStyles.RED.background else null },
            selection = selection,
            onRowDoubleClick = { batchesOf = it.key },
            rowHeight = 48.dp,
            emptyText = if (filters.activeCount > 0) "Filtrga mos reaktiv topilmadi" else "Hali reaktiv kiritilmagan",
            emptyHint = if (filters.belowMinOnly) "Minimal qoldiqdan kam reaktiv yo‘q." else null,
            testTag = "stock",
        )
    }
    batchesOf?.let { key ->
        val total = totals.firstOrNull { it.key == key }
        if (total != null) {
            StockBatchesDialog(state, total, filters.includeArchived, onHistory = { historyBatch = it }) { batchesOf = null }
        } else {
            LaunchedEffect(key) { batchesOf = null }
        }
    }
    minFor?.let { key ->
        val total = totals.firstOrNull { it.key == key }
        if (total != null) {
            StockMinDialog(state, total) { minFor = null }
        } else {
            LaunchedEffect(key) { minFor = null }
        }
    }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

private fun minText(row: StockTotal): String =
    if (row.hasMin) qtyText(row.minStock!!, row.minStockUnit!!) else "—"

private fun stockColumns(): List<TableColumn<StockTotal>> = listOf(
    TableColumn(
        id = "name", title = "Reaktiv nomi", width = 168.dp, sortKey = { nameKey(it.name) }, text = { it.name },
        cell = { row ->
            Column {
                Text(row.name, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.formula.isNotBlank() || row.reagentArchived) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (row.formula.isNotBlank()) {
                            Text(row.formula, fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        if (row.reagentArchived) {
                            Spacer(Modifier.width(6.dp))
                            StatusChip("Arxiv", AppColors.Background, AppColors.Muted, dot = null)
                        }
                    }
                }
            }
        },
    ),
    textColumn("formula", "Formula", 90.dp, visibleByDefault = false) { it.formula.ifBlank { "—" } },
    textColumn("unit", "Birlik", 56.dp, ColumnAlign.CENTER) { it.unit.code },
    textColumn("batches", "Partiyalar (faol/jami)", 92.dp, ColumnAlign.END, sortKey = { it.activeBatches }) { "${it.activeBatches} / ${it.batches}" },
    textColumn("received", "Kelgan", 76.dp, ColumnAlign.END, sortKey = { it.received }) { qtyNumber(it.received, it.unit) },
    textColumn("issued", "Berilgan", 84.dp, ColumnAlign.END, sortKey = { it.issued }) { qtyNumber(it.issued, it.unit) },
    textColumn("writtenOff", "Hisobdan chiqarilgan", 104.dp, ColumnAlign.END, sortKey = { it.writtenOff }) { qtyNumber(it.writtenOff, it.unit) },
    TableColumn(
        id = "remainder", title = "Joriy qoldiq", width = 86.dp, align = ColumnAlign.END, sortKey = { it.remainder },
        text = { qtyNumber(it.remainder, it.unit) },
        cell = { row ->
            Text(
                qtyNumber(row.remainder, row.unit),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    row.belowMin -> StatusStyles.RED.foreground
                    row.remainder == 0L -> AppColors.Muted
                    else -> AppColors.Text
                },
                maxLines = 1,
            )
        },
    ),
    textColumn("min", "Minimal qoldiq", 84.dp, ColumnAlign.END, sortKey = { if (it.hasMin) it.minStock else null }) { minText(it) },
    TableColumn(
        id = "status", title = "Holat", width = 148.dp, sortKey = { if (it.belowMin) 2 else if (it.hasMin) 1 else 0 },
        text = { if (it.belowMin) "Minimaldan kam" else if (it.hasMin) "Yetarli" else "Belgilanmagan" },
        cell = { row ->
            when {
                row.belowMin -> StatusChip("Minimaldan kam", StatusStyles.RED.background, StatusStyles.RED.foreground, dot = StatusStyles.RED.accent)
                row.hasMin -> StatusChip("Yetarli", StatusStyles.GREEN.background, StatusStyles.GREEN.foreground, dot = StatusStyles.GREEN.accent)
                else -> StatusChip("Belgilanmagan", AppColors.Background, AppColors.Muted, dot = null)
            }
        },
    ),
    TableColumn(
        id = "nearest", title = "Eng yaqin muddat", width = 166.dp, sortKey = { it.nearestExpiry },
        text = { dateText(it.nearestExpiry) + (it.worstState?.let { s -> " · ${s.label}" } ?: "") },
        cell = { row ->
            val worst = row.worstState
            if (worst == null) {
                Text("qoldiq yo‘q", fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1)
            } else {
                Column {
                    if (row.nearestExpiry != null) Text(dateText(row.nearestExpiry), fontSize = 13.sp, lineHeight = 17.sp, color = AppColors.Text, maxLines = 1)
                    ExpiryChip(worst)
                }
            }
        },
    ),
)

// ---------------------------------------------------------------- partiyalar oynasi

/**
 * Bitta reaktiv + o‘lchov turining partiyalari: har biri o‘z holati rangida (yashil guruh ichidagi qizil partiya
 * yashirinmaydi), holatlar bo‘yicha sonlar. Ikki marta bosish — partiya tarixi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockBatchesDialog(state: AppState, total: StockTotal, includeArchived: Boolean, onHistory: (Long) -> Unit, onDismiss: () -> Unit) {
    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val rows = remember(tick, total.key, includeArchived) {
        state.load(emptyList()) {
            state.services.queries.batchRows(BatchFilter(reagentId = total.reagentId, includeArchived = includeArchived), today)
                .filter { it.dimension == total.dimension }
        }
    }
    val columns = remember { stockBatchColumns() }
    val layout = rememberAppTableLayout(state, "stock-batches", columns)
    val live = rows.filter { it.remainder > 0 && !it.archived }
    AppDialog(
        title = "Partiyalar: ${total.name}",
        subtitle = "${total.dimension.title.replaceFirstChar { it.uppercase() }} · jami qoldiq ${qtyText(total.remainder, total.unit)} · " +
            "${rows.size} ta partiya, qoldig‘i bor — ${live.size} ta",
        onDismiss = onDismiss,
        width = 1120.dp,
        testTag = "stock-batches-dialog",
        buttons = {
            AppButton(
                "Barcha reaktivlarda ochish",
                onClick = {
                    onDismiss()
                    state.navigate(Page.REAGENTS, PageIntent.ReagentsFiltered(reagentId = total.reagentId))
                },
                kind = ButtonKind.TEXT,
                icon = AppIcons.Reagents,
            )
            AppButton("Yopish", onClick = onDismiss)
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Qoldig‘i bor partiyalar holati:", fontSize = 12.5.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 2.dp))
            EXPIRY_ORDER.forEach { s ->
                val count = live.count { it.state == s }
                if (count > 0) ExpiryChip(s, Modifier.testTag("stock-batches-state-${s.name}"), text = "${s.label}: $count")
            }
            if (live.isEmpty()) Text("qoldiq yo‘q", fontSize = 12.5.sp, color = AppColors.Muted)
            if (total.belowMin) {
                StatusChip(
                    "Minimal qoldiqdan kam (minimal ${minText(total)})",
                    StatusStyles.RED.background, StatusStyles.RED.foreground, dot = StatusStyles.RED.accent, bold = true,
                )
            }
        }
        DataTable(
            rows = rows,
            columns = columns,
            rowKey = { it.batchId },
            layout = layout,
            modifier = Modifier.fillMaxWidth().height((44 + 34 * rows.size.coerceIn(3, 10) + 14).dp),
            rowBackground = { it.state.style.background },
            onRowDoubleClick = { onHistory(it.batchId) },
            emptyText = "Partiya yo‘q",
            testTag = "stock-batches",
        )
        Text("Ikki marta bosish — partiya harakatlari tarixi.", fontSize = 12.sp, color = AppColors.Muted)
    }
}

private fun stockBatchColumns(): List<TableColumn<BatchRow>> = listOf(
    textColumn("series", "Seriya raqami", 96.dp) { it.series.ifBlank { "—" } },
    textColumn("place", "Bo‘lim / joy", 168.dp) { placeText(it.departmentName, it.storageLocation) },
    textColumn("received", "Kelgan sana", 92.dp, ColumnAlign.END, sortKey = { it.receivedOn }, visibleByDefault = false) { dateText(it.receivedOn) },
    textColumn("expires", "Yaroqlilik muddati", 96.dp, ColumnAlign.END, sortKey = { it.expiresOn }) { dateText(it.expiresOn) },
    textColumn("days", "Qolgan kunlar", 110.dp, ColumnAlign.END, sortKey = { it.daysLeft }) { daysText(it.daysLeft) },
    TableColumn(
        id = "state", title = "Holati", width = 172.dp, sortKey = { it.state.severity }, text = { it.state.label },
        cell = { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpiryChip(row.state)
                if (row.archived) {
                    Spacer(Modifier.width(4.dp))
                    StatusChip("Arxiv", AppColors.Background, AppColors.Muted, dot = null)
                }
            }
        },
    ),
    textColumn("receivedQty", "Kelgan", 76.dp, ColumnAlign.END, sortKey = { it.receivedQty }) { qtyNumber(it.receivedQty, it.unit) },
    textColumn("issued", "Berilgan", 78.dp, ColumnAlign.END, sortKey = { it.issuedQty }) { qtyNumber(it.issuedQty, it.unit) },
    textColumn("writtenOff", "Hisobdan chiqarilgan", 100.dp, ColumnAlign.END, sortKey = { it.writtenOffQty }) { qtyNumber(it.writtenOffQty, it.unit) },
    TableColumn(
        id = "remainder", title = "Qoldiq", width = 84.dp, align = ColumnAlign.END, sortKey = { it.remainder },
        text = { qtyNumber(it.remainder, it.unit) },
        cell = { row ->
            Text(qtyNumber(row.remainder, row.unit), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (row.remainder == 0L) AppColors.Muted else AppColors.Text, maxLines = 1)
        },
    ),
    textColumn("unit", "Birlik", 60.dp, ColumnAlign.CENTER) { it.unit.code },
)

// ---------------------------------------------------------------- minimal qoldiq oynasi

/** Minimal qoldiq (reaktivning boshqa maydonlari o‘zgarmaydi): son + birlik (shu o‘lchov turida); bo‘sh — olib tashlanadi. */
@Composable
private fun StockMinDialog(state: AppState, total: StockTotal, onDismiss: () -> Unit) {
    val reagent = remember(total.reagentId) { state.load(null) { state.services.reagents.get(total.reagentId) } }
    if (reagent == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val sameDimension = reagent.minStock != null && reagent.minStockUnit?.dimension == total.dimension
    val form = remember { FormErrors() }
    var text by remember { mutableStateOf(if (sameDimension) Qty.format(reagent.minStock!!, reagent.minStockUnit!!) else "") }
    var unit by remember { mutableStateOf(if (sameDimension) reagent.minStockUnit!! else total.unit) }
    val units = MeasureUnit.of(total.dimension)

    fun save() {
        state.run(
            {
                state.services.db.write {
                    // Saqlash paytidagi reaktiv: nomi, formulasi va izohi o‘zgarmaydi.
                    val r = state.services.reagents.get(reagent.id) ?: throw UserError("Reaktiv topilmadi.")
                    state.services.reagents.updateReagent(r.id, ReagentEdit(r.name, r.formula, text.ifBlank { null }, if (text.isBlank()) null else unit, r.note))
                }
            },
            form = form,
        ) {
            onDismiss()
            state.success(
                if (text.isBlank()) {
                    "«${reagent.name}»: minimal qoldiq olib tashlandi."
                } else {
                    "«${reagent.name}»: minimal qoldiq ${runCatching { qtyText(Qty.parse(text, unit, allowZero = true), unit) }.getOrDefault("$text ${unit.code}")}."
                },
            )
        }
    }

    AppDialog(
        title = "Minimal qoldiqni belgilash",
        subtitle = reagent.name + if (reagent.formula.isNotBlank()) " · ${reagent.formula}" else "",
        onDismiss = onDismiss,
        width = 560.dp,
        testTag = "stock-min-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton("Saqlash", onClick = { save() }, icon = AppIcons.Check, enabled = !state.busy, modifier = Modifier.testTag("stock-min-save"))
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            InfoPair("Joriy umumiy qoldiq", qtyText(total.remainder, total.unit), valueColor = AppColors.Primary)
            InfoPair("Hozirgi minimal qoldiq", reagent.minStock?.let { qtyText(it, reagent.minStockUnit ?: total.unit) } ?: "belgilanmagan")
            InfoPair("O‘lchov turi", total.dimension.title)
        }
        if (reagent.minStock != null && !sameDimension) {
            Banner(
                "Hozirgi minimal qoldiq boshqa o‘lchov turida belgilangan (${qtyText(reagent.minStock, reagent.minStockUnit ?: total.unit)}). " +
                    "Saqlansa, u shu yerda kiritilgan qiymat bilan almashtiriladi.",
                severity = Severity.WARNING,
            )
        }
        QtyField(
            text, { text = it; form.clear("minStockText") }, unit.code,
            { code -> MeasureUnit.of(code)?.let { unit = it } }, units.map { it.code },
            label = "Minimal qoldiq", error = form["minStockText"],
            modifier = Modifier.width(260.dp), testTag = "stock-min",
        )
        Text(
            "Shu reaktivning umumiy qoldig‘i (${total.dimension.title}) minimal qoldiqdan kam bo‘lsa — «Minimaldan kam» ogohlantirishi " +
                "chiqadi. Maydon bo‘sh qoldirilsa — minimal qoldiq olib tashlanadi.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = AppColors.Muted,
        )
    }
}

package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.report.ReportBuilder
import uz.reaktivombor.desktop.report.ReportFilter
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.IssueItemRow
import uz.reaktivombor.desktop.service.LedgerRow
import uz.reaktivombor.desktop.service.MovementKind
import uz.reaktivombor.desktop.service.OpFilter
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.Reagent
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.ComboField
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.EmptyState
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.GroupInfo
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableGrouping
import uz.reaktivombor.desktop.ui.components.rememberGroupExpansion
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import kotlin.math.abs

// «Operatsiyalar tarixi» → «Reaktiv tarixi»: reaktiv (va ixtiyoriy partiya) harakatlari, har partiya bo‘yicha qoldiq bilan.

/** Jurnal qatori jadval uchun: harakat va uning tushunarli ko‘rinishi. */
@Immutable
internal class HistoryLedgerLine(
    val row: LedgerRow,
    /** «Kirim (boshlang‘ich)», «Bo‘limga berildi», «Bekor qilish: berish» … */
    val type: String,
    /** Qabul qiluvchi (berishda) yoki manba (kirimda). */
    val party: String,
    val note: String,
    val batch: BatchRow?,
) {
    /** Bu harakatning operatsiyasi keyin bekor qilingan. */
    val cancelled: Boolean get() = row.operationCancelled

    val reversal: Boolean get() = row.kind == MovementKind.REVERSAL

    /** "+10 l" / "−3 l" — partiya birligida. */
    val qtyText: String get() = (if (row.qty > 0) "+" else "−") + qtyText(abs(row.qty), row.unit)
}

/** «Reaktiv tarixi» holati: nom maydoni xatolari va Excel/PDF saqlash (tarix hisoboti). */
@Stable
internal class HistoryLedgerState(private val state: AppState) {
    val errors = FormErrors()

    /** Tanlangan reaktiv (partiya tanlangan bo‘lsa — uning seriyasi bilan) tarixini faylga saqlaydi. */
    fun export(format: ReportsFileFormat, choice: HistoryLedgerChoice) {
        val batch = choice.batchId?.let { id -> state.load(null) { state.services.reagents.batch(id) } }
        val series = batch?.series?.ifBlank { null }
        // Hisobotning seriya filtri qism-satr bo‘yicha: "ES-1" tanlansa "ES-10" ham kiradi — xabarda aytiladi.
        val alsoMatching = if (batch != null && series != null) {
            state.load(emptyList()) { state.services.reagents.batchesOf(batch.reagentId) }
                .filter { it.id != batch.id && nameKey(it.series).contains(nameKey(series)) }
                .map { it.series }
        } else {
            emptyList()
        }
        val note = when {
            series != null && alsoMatching.isNotEmpty() ->
                ", partiya $series; seriyasida «$series» bor ${alsoMatching.joinToString(", ")} partiyasi ham kirdi"
            series != null -> ", partiya $series"
            batch != null -> ", partiya seriyasiz — butun reaktiv tarixi"
            else -> ""
        }
        reportsExportFile(
            state,
            format,
            form = errors,
            message = { data, file ->
                "«${reportsReagentOf(data) ?: choice.name}» tarixi ${format.title} fayliga saqlandi " +
                    "(${data.reportsRowCount} ta harakat$note): ${file.fileName}"
            },
        ) {
            ReportBuilder(state.services).build(ReportKind.HISTORY, ReportFilter(name = choice.name.trim().ifEmpty { null }, series = series), state.today())
        }
    }
}

@Composable
internal fun rememberHistoryLedgerState(state: AppState): HistoryLedgerState = remember(state) { HistoryLedgerState(state) }

@Composable
internal fun ColumnScope.HistoryLedger(
    state: AppState,
    ledger: HistoryLedgerState,
    choice: HistoryLedgerChoice,
    onChoice: (HistoryLedgerChoice) -> Unit,
) {
    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val reagents = remember(tick) { state.load(emptyList()) { state.services.reagents.list(includeArchived = true) } }
    val reagent: Reagent? = choice.name.takeIf { it.isNotBlank() }?.let { typed -> reagents.firstOrNull { nameKey(it.name) == nameKey(typed) } }
    val batches = remember(tick, reagent?.id) {
        reagent?.let { r -> state.load(emptyList()) { state.services.queries.batchRows(BatchFilter(reagentId = r.id, includeArchived = true), today) } }
            ?: emptyList()
    }
    val batchId = choice.batchId?.takeIf { id -> batches.any { it.batchId == id } }
    val lines = remember(tick, reagent?.id, batchId, batches) {
        reagent?.let { r -> state.load(emptyList()) { historyLedgerLines(state, r.id, batchId, batches) } } ?: emptyList()
    }
    val nameError = ledger.errors["name"]

    FilterBar(
        onClear = {
            onChoice(HistoryLedgerChoice())
            ledger.errors.clear()
        },
        activeCount = listOf(choice.name.isNotBlank(), batchId != null).count { it },
    ) {
        ComboField(
            value = choice.name,
            onValueChange = {
                onChoice(HistoryLedgerChoice(it, null))
                ledger.errors.clear("name")
            },
            suggestions = reagents.map { it.name },
            label = "Reaktiv",
            required = true,
            placeholder = "Nomini yozing yoki tanlang",
            error = nameError,
            help = when {
                choice.name.isNotBlank() && reagent == null -> "Ro‘yxatdan reaktivni tanlang"
                reagent?.archived == true -> "Reaktiv arxivda"
                else -> null
            },
            modifier = Modifier.width(300.dp),
            testTag = "ledger-reagent",
        )
        SelectField(
            value = batches.firstOrNull { it.batchId == batchId },
            options = listOf<BatchRow?>(null) + batches,
            onSelect = { onChoice(choice.copy(batchId = it?.batchId)) },
            label = "Partiya",
            placeholder = "Barcha partiyalar",
            optionLabel = { b -> b?.let(::historyBatchOption) ?: "— Barcha partiyalar —" },
            enabled = reagent != null,
            modifier = Modifier.width(340.dp),
            testTag = "ledger-batch",
        )
    }

    if (reagent == null) {
        Surface(
            Modifier.fillMaxWidth().weight(1f).testTag("ledger-empty"),
            shape = RoundedCornerShape(10.dp),
            color = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.Divider),
        ) {
            EmptyState(
                "Reaktivni tanlang",
                hint = "Nomini yozing yoki ro‘yxatdan tanlang: barcha partiyalarining kirim, berish, qaytarish va bekor qilish " +
                    "harakatlari har harakatdan keyingi qoldiq bilan ko‘rsatiladi. Partiyani tanlab, faqat uning tarixini ko‘rish mumkin.",
                icon = AppIcons.History,
            )
        }
        return
    }

    val shownBatches = if (batchId != null) batches.filter { it.batchId == batchId } else batches
    HistoryLedgerSummary(reagent, shownBatches, lines)
    val columns = remember { historyLedgerColumns() }
    val layout = rememberAppTableLayout(state, "history-ledger", columns)
    val expansion = rememberGroupExpansion()
    DataTable(
        rows = lines,
        columns = columns,
        rowKey = { it.row.movementId },
        layout = layout,
        modifier = Modifier.fillMaxWidth().weight(1f),
        rowBackground = { if (it.cancelled || it.reversal) AppColors.SurfaceAlt else null },
        grouping = TableGrouping(key = { it.row.batchId.toString() }, header = { HistoryLedgerGroupHeader(it) }),
        expansion = expansion,
        emptyText = "Bu reaktivda harakat yo‘q",
        testTag = "ledger",
    )
}

/** Partiya tanlash ro‘yxatidagi matn: "ES-001 · 01.09.2027 · qoldiq 6 l". */
private fun historyBatchOption(b: BatchRow): String =
    listOf(b.series.ifBlank { "seriyasiz" }, dateText(b.expiresOn), "qoldiq " + qtyText(b.remainder, b.unit)).joinToString(" · ") +
        if (b.archived) " (arxivda)" else ""

@Composable
private fun HistoryLedgerSummary(reagent: Reagent, batches: List<BatchRow>, lines: List<HistoryLedgerLine>) {
    val totals: Map<Dimension, Long> = batches.groupBy { it.dimension }.mapValues { (_, list) -> list.sumOf { it.remainder } }
    val cancelled = lines.count { it.cancelled }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("ledger-summary"),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoPair("Reaktiv", reagent.name + if (reagent.formula.isNotBlank()) " (${reagent.formula})" else "")
        InfoPair(if (batches.size == 1) "Partiya" else "Partiyalar", if (batches.size == 1) batches.single().label() else "${batches.size} ta")
        InfoPair("Harakatlar", "${lines.size} ta" + if (cancelled > 0) " (keyin bekor qilingani: $cancelled)" else "")
        InfoPair("Joriy qoldiq", totalsText(totals), valueColor = AppColors.Primary)
    }
}

@Composable
private fun HistoryLedgerGroupHeader(info: GroupInfo<HistoryLedgerLine>) {
    val first = info.rows.first()
    val batch = first.batch
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            batch?.label() ?: "${first.row.name} · ${first.row.series.ifBlank { "seriyasiz" }}",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (batch != null) ExpiryChip(batch.state)
        Text("${info.rows.size} ta harakat", fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1)
        if (batch != null) {
            Text("Joriy qoldiq: ${qtyText(batch.remainder, batch.unit)}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy, maxLines = 1)
            if (batch.archived) StatusChip("Arxivda", AppColors.Background, AppColors.Muted, dot = null)
        }
    }
}

private fun historyLedgerColumns(): List<TableColumn<HistoryLedgerLine>> = listOf(
    textColumn("date", "Sana", 100.dp, ColumnAlign.END, sortKey = { it.row.movedOn }) { dateText(it.row.movedOn) },
    TableColumn(
        id = "type", title = "Amal turi", width = 250.dp, text = { it.type },
        cell = { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ustun tor bo‘lsa «…», ustiga kelganda to‘liq matn.
                Box(Modifier.weight(1f, fill = false)) {
                    HistoryLongText(line.type, if (line.cancelled) AppColors.Muted else AppColors.Text)
                }
                if (line.cancelled) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip("bekor qilingan", AppColors.Background, AppColors.Muted, dot = null)
                }
            }
        },
    ),
    textColumn("batch", "Partiya", 100.dp) { it.row.series.ifBlank { "seriyasiz" } },
    TableColumn(
        id = "qty", title = "Miqdor (+/−)", width = 112.dp, align = ColumnAlign.END, sortKey = { it.row.qty }, text = { it.qtyText },
        cell = { line ->
            Text(
                line.qtyText,
                fontSize = 13.sp,
                color = when {
                    line.cancelled -> AppColors.Muted
                    line.row.qty > 0 -> AppColors.Success
                    else -> AppColors.Text
                },
                maxLines = 1,
            )
        },
    ),
    TableColumn(
        id = "balance", title = "Qoldiq", width = 104.dp, align = ColumnAlign.END, sortKey = { it.row.balance }, text = { qtyText(it.row.balance, it.row.unit) },
        cell = { line -> Text(qtyText(line.row.balance, line.row.unit), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, maxLines = 1) },
    ),
    TableColumn(
        id = "party", title = "Qabul qiluvchi / manba", width = 230.dp, text = { it.party },
        cell = { line -> HistoryLongText(line.party, if (line.cancelled) AppColors.Muted else AppColors.Text) },
    ),
    textColumn("doc", "Hujjat", 86.dp) { it.row.docNo.ifBlank { "—" } },
    TableColumn(
        id = "note", title = "Izoh", width = 220.dp, text = { it.note },
        cell = { line -> HistoryLongText(line.note, AppColors.Muted) },
    ),
    textColumn("created", "Kiritilgan vaqt", 132.dp, ColumnAlign.END, sortKey = { it.row.createdAt }) { dateTimeText(it.row.createdAt) },
)

/** Bo‘lim/tuman nomi jumlada: "Gistologiya bo‘limi", "Chilonzor tumani", "Hisobdan chiqarish". */
private fun historyPlace(type: RecipientType, name: String): String = when (type) {
    RecipientType.DEPARTMENT -> "$name bo‘limi"
    RecipientType.DISTRICT -> "$name tumani"
    RecipientType.WRITE_OFF -> RecipientType.WRITE_OFF.label
}

private fun historyRecipient(item: IssueItemRow): String {
    val who = listOf(item.organization, item.receiver).filter { it.isNotBlank() }.joinToString(", ")
    return historyPlace(item.recipientType, item.recipientName) + if (who.isNotEmpty()) " — $who" else ""
}

/** Bo‘sh bo‘lmaganlari " · " bilan, birinchi harfi katta: "Biokimyo MChJ · qabul qildi: Karimova D.". */
private fun joinNotes(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ").replaceFirstChar { it.uppercaseChar() }

/** Harakatlarga operatsiya tafsilotlarini (kim, kimga, sabab) qo‘shadi. Partiya tanlangan bo‘lsa — faqat uning harakatlari. */
internal fun historyLedgerLines(state: AppState, reagentId: Long, batchId: Long?, batches: List<BatchRow>): List<HistoryLedgerLine> {
    val q = state.services.queries
    val rows = if (batchId != null) q.ledger(batchId) else q.reagentLedger(reagentId)
    val ops = OpFilter(reagentId = reagentId, batchId = batchId, includeCancelled = true)
    val receipts = q.receipts(ops).associateBy { it.id }
    val items = q.issueItems(ops).associateBy { it.id }
    val returns = q.returns(ops).associateBy { it.id }
    val byBatch = batches.associateBy { it.batchId }
    return rows.map { m ->
        val batch = byBatch[m.batchId]
        val receipt = m.receiptId?.let { receipts[it] }
        val item = m.issueItemId?.let { items[it] }
        val ret = m.returnId?.let { returns[it] }
        val receiptParty = receipt?.let { r ->
            joinNotes(batch?.manufacturer, r.receivedBy.takeIf { it.isNotBlank() }?.let { "qabul qildi: $it" })
        }.orEmpty()
        val returnParty = ret?.let { r ->
            joinNotes("Qaytardi: " + historyPlace(r.recipientType, r.recipientName), r.receivedBy.takeIf { it.isNotBlank() }?.let { "qabul qildi: $it" })
        }.orEmpty()
        val itemParty = item?.let(::historyRecipient).orEmpty()
        when (m.kind) {
            MovementKind.RECEIPT -> HistoryLedgerLine(
                m,
                type = when {
                    receipt?.correctsId != null -> "Kirim (tuzatilgan)"
                    receipt?.isInitial == true -> "Kirim (boshlang‘ich)"
                    else -> "Kirim"
                },
                party = receiptParty,
                note = joinNotes(receipt?.note, receipt?.cancelReason?.let { "Bekor qilish sababi: $it" }),
                batch = batch,
            )
            MovementKind.ISSUE -> HistoryLedgerLine(
                m,
                type = when (item?.recipientType) {
                    RecipientType.DEPARTMENT -> "Bo‘limga berildi"
                    RecipientType.DISTRICT -> "Tumanga berildi"
                    RecipientType.WRITE_OFF -> "Hisobdan chiqarildi"
                    null -> MovementKind.ISSUE.label
                } + if (item?.correctsId != null) " (tuzatilgan)" else "",
                party = itemParty,
                note = joinNotes(
                    item?.note?.let { if (item.recipientType == RecipientType.WRITE_OFF) "Sabab: $it" else it },
                    item?.cancelReason?.let { "Bekor qilish sababi: $it" },
                    if (item?.expiredOverride == true) "Muddati o‘tgan partiyadan (tasdiqlangan)" else null,
                ),
                batch = batch,
            )
            MovementKind.RETURN -> HistoryLedgerLine(
                m,
                type = "Qaytarildi",
                party = returnParty,
                note = joinNotes(ret?.reason, ret?.cancelReason?.let { "Bekor qilish sababi: $it" }),
                batch = batch,
            )
            MovementKind.ADJUSTMENT -> HistoryLedgerLine(m, type = "Moslash", party = "—", note = m.note, batch = batch)
            MovementKind.REVERSAL -> HistoryLedgerLine(
                m,
                type = "Bekor qilish: " + when {
                    m.returnId != null -> "qaytarish"
                    m.issueItemId != null -> if (item?.recipientType == RecipientType.WRITE_OFF) "hisobdan chiqarish" else "berish"
                    m.receiptId != null -> "kirim"
                    else -> "moslash"
                },
                party = when {
                    m.returnId != null -> returnParty
                    m.issueItemId != null -> itemParty
                    m.receiptId != null -> receiptParty
                    else -> "—"
                },
                note = m.note.takeIf { it.isNotBlank() }?.let { "Sabab: $it" }.orEmpty(),
                batch = batch,
            )
        }
    }
}

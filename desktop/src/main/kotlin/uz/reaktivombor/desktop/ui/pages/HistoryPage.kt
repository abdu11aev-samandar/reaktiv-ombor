package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.service.AuditAction
import uz.reaktivombor.desktop.service.AuditEntry
import uz.reaktivombor.desktop.service.AuditFilter
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TooltipBox
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import java.time.LocalDate

/** Sahifa bo‘limlari. */
private enum class HistoryTab(val title: String) {
    AUDIT("Amallar jurnali"),
    LEDGER("Reaktiv tarixi"),
}

/** Amallar jurnali filtrlari. */
@Immutable
private data class HistoryAuditFilters(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val action: AuditAction? = null,
    val text: String = "",
) {
    val activeCount: Int get() = listOf(from != null || to != null, action != null, text.isNotBlank()).count { it }

    fun toFilter(): AuditFilter = AuditFilter(from = from, to = to, action = action, text = text.trim().ifEmpty { null })
}

/** «Reaktiv tarixi» da tanlangan reaktiv (nomi) va partiya — topshiriqdan yoki jurnal yozuvidan. */
@Immutable
internal data class HistoryLedgerChoice(val name: String = "", val batchId: Long? = null)

/** Jurnaldagi reaktiv/partiya nomlari: partiya → "Etil spirti 96% · ES-001", reaktiv → nomi. */
@Immutable
private class HistoryAuditNames(val batches: Map<Long, String>, val reagents: Map<Long, String>) {
    fun subject(entry: AuditEntry): String =
        entry.batchId?.let { batches[it] } ?: entry.reagentId?.let { reagents[it] } ?: "—"
}

/** Bir martada ko‘rsatiladigan jurnal yozuvlari («Ko‘proq ko‘rsatish» yana shuncha qo‘shadi). */
const val HISTORY_AUDIT_PAGE = 100

/**
 * «Operatsiyalar tarixi» (texnik topshiriq §14): «Amallar jurnali» — har bir yozish amali (davr, amal turi, qidiruv,
 * yangisi birinchi) va «Reaktiv tarixi» — reaktiv yoki partiyaning harakatlari, har partiya bo‘yicha qoldiq bilan
 * (bekor qilingan va bekor qilish qatorlari belgilanadi) + Excel/PDF. [PageIntent.HistoryOf] — reaktiv tarixini ochadi.
 */
@Composable
fun HistoryPage(state: AppState) {
    val intent = state.intent as? PageIntent.HistoryOf
    val start = remember { historyChoiceFor(state, intent?.reagentId, intent?.batchId) }
    var tab by remember { mutableStateOf(if (intent != null) HistoryTab.LEDGER else HistoryTab.AUDIT) }
    var choice by remember { mutableStateOf(start) }
    var auditFilters by remember { mutableStateOf(HistoryAuditFilters()) }
    var limit by remember { mutableIntStateOf(HISTORY_AUDIT_PAGE) }
    var detail by remember { mutableStateOf<AuditEntry?>(null) }
    val ledger = rememberHistoryLedgerState(state)

    fun openLedger(reagentId: Long?, batchId: Long?) {
        choice = historyChoiceFor(state, reagentId, batchId)
        ledger.errors.clear()
        tab = HistoryTab.LEDGER
    }

    PageScaffold(
        title = Page.HISTORY.title,
        subtitle = when (tab) {
            HistoryTab.AUDIT -> "Har bir yozish amali: kim, qachon, nima qildi. Yozuvlar o‘zgartirilmaydi va o‘chirilmaydi."
            HistoryTab.LEDGER -> "Reaktiv partiyalarining barcha harakatlari va har harakatdan keyingi qoldiq."
        },
        actions = {
            if (tab == HistoryTab.LEDGER) {
                ReportsSaveButtons(onSave = { ledger.export(it, choice) }, enabled = !state.busy, testTagPrefix = "history-save")
            }
        },
    ) {
        HistoryTabs(tab) { tab = it }
        when (tab) {
            HistoryTab.AUDIT -> HistoryAuditLog(
                state = state,
                filters = auditFilters,
                onFilters = {
                    auditFilters = it
                    limit = HISTORY_AUDIT_PAGE
                },
                limit = limit,
                onMore = { limit += HISTORY_AUDIT_PAGE },
                onOpen = { detail = it },
            )
            HistoryTab.LEDGER -> HistoryLedger(state, ledger, choice) { choice = it }
        }
    }
    detail?.let { entry ->
        HistoryAuditDialog(
            state = state,
            entry = entry,
            onDismiss = { detail = null },
            onLedger = {
                detail = null
                openLedger(entry.reagentId, entry.batchId)
            },
        )
    }
}

/** Topshiriqdagi reaktiv/partiya → tanlov (reaktiv nomi bilan). Topilmasa — bo‘sh tanlov. */
private fun historyChoiceFor(state: AppState, reagentId: Long?, batchId: Long?): HistoryLedgerChoice =
    state.load(HistoryLedgerChoice()) {
        val batch = batchId?.let { state.services.reagents.batch(it) }
        val reagent = (reagentId ?: batch?.reagentId)?.let { state.services.reagents.get(it) }
        if (reagent == null) {
            HistoryLedgerChoice()
        } else {
            HistoryLedgerChoice(reagent.name, batch?.id?.takeIf { batch.reagentId == reagent.id })
        }
    }

@Composable
private fun HistoryTabs(selected: HistoryTab, onSelect: (HistoryTab) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HistoryTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .testTag("history-tab-${tab.name.lowercase()}"),
                ) {
                    Text(
                        tab.title,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) AppColors.Primary else AppColors.Muted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(2.5.dp).background(if (active) AppColors.Primary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = AppColors.Border)
    }
}

// ---------------------------------------------------------------- amallar jurnali

@Composable
private fun ColumnScope.HistoryAuditLog(
    state: AppState,
    filters: HistoryAuditFilters,
    onFilters: (HistoryAuditFilters) -> Unit,
    limit: Int,
    onMore: () -> Unit,
    onOpen: (AuditEntry) -> Unit,
) {
    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val query = filters.toFilter()
    // Bittadan ortiq so‘raladi — «yana bor»mi shundan bilinadi.
    val loaded = remember(tick, query, limit) { state.load(emptyList()) { state.services.queries.auditLogs(query, limit + 1) } }
    val entries = loaded.take(limit)
    val hasMore = loaded.size > limit
    val names = remember(tick) {
        state.load(HistoryAuditNames(emptyMap(), emptyMap())) {
            HistoryAuditNames(
                batches = state.services.queries.batchRows(BatchFilter(includeArchived = true), today)
                    .associate { it.batchId to listOf(it.name, it.series.ifBlank { "seriyasiz" }).joinToString(" · ") },
                reagents = state.services.reagents.list(includeArchived = true).associate { it.id to it.name },
            )
        }
    }
    val columns = remember(names) { auditColumns(names) }
    val layout = rememberAppTableLayout(state, "audit-log", columns)

    FilterBar(onClear = { onFilters(HistoryAuditFilters()) }, activeCount = filters.activeCount) {
        DateRangeField(
            filters.from, filters.to, { f, t -> onFilters(filters.copy(from = f, to = t)) },
            label = "Davr", today = today, fieldWidth = 130.dp, testTag = "audit-period",
        )
        SelectField(
            value = filters.action,
            options = listOf<AuditAction?>(null) + AuditAction.entries,
            onSelect = { onFilters(filters.copy(action = it)) },
            label = "Amal turi",
            placeholder = "Hammasi",
            optionLabel = { it?.label ?: "— Hammasi —" },
            modifier = Modifier.width(220.dp),
            testTag = "audit-action",
        )
        AppTextField(
            filters.text, { onFilters(filters.copy(text = it)) },
            label = "Qidirish", placeholder = "Tafsilot, xodim, miqdor…", leadingIcon = AppIcons.Search,
            modifier = Modifier.width(260.dp), testTag = "audit-text",
        )
    }
    DataTable(
        rows = entries,
        columns = columns,
        rowKey = { it.id },
        layout = layout,
        modifier = Modifier.fillMaxWidth().weight(1f),
        onRowDoubleClick = onOpen,
        emptyText = if (filters.activeCount > 0) "Filtrga mos yozuv topilmadi" else "Jurnal hali bo‘sh",
        emptyHint = if (filters.activeCount > 0) "Filtrlarni o‘zgartiring yoki «Filtrlarni tozalash» tugmasini bosing." else null,
        testTag = "audit",
    )
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (entries.isEmpty()) "" else "Ko‘rsatilmoqda: ${entries.size} ta yozuv, yangisi birinchi" +
                (if (hasMore) " — eskiroqlari ham bor." else ".") + " Yozuvni ikki marta bosing — to‘liq tafsilot.",
            fontSize = 12.5.sp,
            color = AppColors.Muted,
            modifier = Modifier.weight(1f).testTag("audit-count"),
        )
        if (hasMore) {
            AppButton(
                "Ko‘proq ko‘rsatish",
                onClick = onMore,
                kind = ButtonKind.SECONDARY,
                icon = AppIcons.ChevronDown,
                modifier = Modifier.testTag("audit-more"),
            )
        }
    }
}

/** Amal turining rangi (matn bilan birga): kirim — yashil, berish — ko‘k, bekor qilish — qizil, tuzatish — sariq. */
private fun auditChipColors(action: AuditAction?): Pair<Color, Color> = when (action) {
    AuditAction.RECEIPT, AuditAction.BATCH_CREATED, AuditAction.REAGENT_CREATED, AuditAction.RETURN, AuditAction.UNARCHIVED ->
        StatusStyles.GREEN.background to StatusStyles.GREEN.foreground
    AuditAction.ISSUE_DEPARTMENT, AuditAction.ISSUE_DISTRICT -> AppColors.PrimarySoft to AppColors.Primary
    AuditAction.WRITE_OFF, AuditAction.CORRECTED -> StatusStyles.YELLOW.background to StatusStyles.YELLOW.foreground
    AuditAction.CANCELLED, AuditAction.DELETED -> StatusStyles.RED.background to StatusStyles.RED.foreground
    else -> AppColors.Background to AppColors.Muted
}

private fun auditColumns(names: HistoryAuditNames): List<TableColumn<AuditEntry>> = listOf(
    textColumn("at", "Vaqt", 148.dp, sortKey = { it.at }) { dateTimeText(it.at) },
    TableColumn(
        id = "action", title = "Amal", width = 176.dp, text = { it.actionLabel },
        cell = { entry ->
            val (bg, fg) = auditChipColors(entry.action)
            StatusChip(entry.actionLabel, bg, fg, dot = null)
        },
    ),
    textColumn("subject", "Reaktiv / partiya", 210.dp) { names.subject(it) },
    textColumn("qty", "Miqdor", 100.dp, ColumnAlign.END) { it.qtyText.ifBlank { "—" } },
    TableColumn(
        id = "details", title = "Tafsilot", width = 300.dp, text = { it.details },
        cell = { entry -> HistoryLongText(entry.details) },
    ),
    textColumn("actor", "Xodim", 130.dp) { it.actor.ifBlank { "—" } },
)

/** Uzun matn: bir qatorda, sig‘masa «…», ustiga kelganda to‘liq matn. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryLongText(text: String, color: Color = AppColors.Text) {
    TooltipArea(
        tooltip = { if (text.isNotBlank()) Box(Modifier.width(420.dp)) { TooltipBox(text) } },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
    ) {
        Text(text.ifBlank { "—" }, fontSize = 13.sp, lineHeight = 17.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Jurnal yozuvining to‘liq ko‘rinishi; reaktiv/partiya bo‘lsa — uning tarixini ochish. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryAuditDialog(state: AppState, entry: AuditEntry, onDismiss: () -> Unit, onLedger: () -> Unit) {
    val subject = remember(entry) {
        state.load("—") {
            val batch = entry.batchId?.let { state.services.reagents.batch(it) }
            val reagent = (entry.reagentId ?: batch?.reagentId)?.let { state.services.reagents.get(it) }
            when {
                batch != null && reagent != null -> batchLabel(reagent.name, batch.series, batch.expiresOn)
                reagent != null -> reagent.name
                else -> "—"
            }
        }
    }
    AppDialog(
        title = "Jurnal yozuvi",
        subtitle = "${entry.actionLabel} · ${dateTimeText(entry.at)}",
        onDismiss = onDismiss,
        width = 680.dp,
        testTag = "audit-dialog",
        buttons = {
            if (subject != "—") {
                AppButton("Reaktiv tarixini ochish", onClick = onLedger, kind = ButtonKind.TEXT, icon = AppIcons.History, modifier = Modifier.testTag("audit-dialog-ledger"))
            }
            AppButton("Yopish", onClick = onDismiss)
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPair("Vaqt", dateTimeText(entry.at))
                InfoPair("Amal", entry.actionLabel)
                InfoPair("Reaktiv / partiya", subject)
                InfoPair("Miqdor", entry.qtyText.ifBlank { "—" })
                InfoPair("Xodim", entry.actor.ifBlank { "kiritilmagan" })
            }
            Column {
                Text("Tafsilot", fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted)
                Spacer(Modifier.height(2.dp))
                SelectionContainer {
                    Text(entry.details.ifBlank { "—" }, fontSize = 13.5.sp, lineHeight = 19.sp, color = AppColors.Text, modifier = Modifier.testTag("audit-dialog-details"))
                }
            }
            if (entry.actor.isBlank()) {
                Text(
                    "Xodim nomi «Sozlamalar» sahifasida kiritilsa, keyingi yozuvlarda ko‘rsatiladi.",
                    fontSize = 12.sp,
                    color = AppColors.Muted,
                )
            }
        }
    }
}

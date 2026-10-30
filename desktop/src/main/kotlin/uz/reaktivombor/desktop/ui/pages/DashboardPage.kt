package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.service.Dashboard
import uz.reaktivombor.desktop.service.IssueItemRow
import uz.reaktivombor.desktop.service.ReceiptRow
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.UzDates
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.Banner
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ChartBar
import uz.reaktivombor.desktop.ui.components.ChartSegment
import uz.reaktivombor.desktop.ui.components.DonutChart
import uz.reaktivombor.desktop.ui.components.EmptyState
import uz.reaktivombor.desktop.ui.components.FittedBarChart
import uz.reaktivombor.desktop.ui.components.FormSection
import uz.reaktivombor.desktop.ui.components.StatCard
import uz.reaktivombor.desktop.ui.components.StatCardRow
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles

/**
 * Bosh sahifa: ko‘rsatkich kartalari, holatlar halqasi, bo‘limlar va tumanlar grafiklari, so‘nggi kirim va berishlar.
 * Hamma son Queries.dashboard(today) dan; turli o‘lchov turidagi miqdorlar qo‘shilmaydi (tur tanlanadi).
 */
@Composable
fun DashboardPage(state: AppState) {
    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val data = remember(tick, today) { state.load(null) { state.services.queries.dashboard(today) } }
    var historyBatch by remember { mutableStateOf<Long?>(null) }

    ScrollPage(
        title = Page.DASHBOARD.title,
        subtitle = "Ombor holati — ${UzDates.format(today)}, ${UzDates.weekday(today).lowercase()}",
        actions = {
            AppButton("Reaktiv qo‘shish", onClick = { state.navigate(Page.ADD_REAGENT) }, icon = AppIcons.Add)
        },
        testTag = "dashboard",
    ) {
        if (data == null) {
            EmptyState("Ma’lumotlarni o‘qib bo‘lmadi", hint = "Tafsilotlar jurnal faylida.", modifier = Modifier.height(240.dp))
            return@ScrollPage
        }
        if (data.expiredBatches > 0) {
            Banner(
                "${data.expiredBatches} ta partiyaning yaroqlilik muddati o‘tgan va omborda qoldig‘i bor. " +
                    "Ulardan berish faqat alohida tasdiq bilan; hisobdan chiqaring yoki arxivlang.",
                severity = Severity.ERROR,
                title = "Muddati o‘tgan reaktivlar",
                actionLabel = "Muddat nazoratiga o‘tish",
                onAction = { state.navigate(Page.EXPIRY, PageIntent.FilterExpiry(setOf(ExpiryState.EXPIRED))) },
                testTag = "expired-banner",
            )
        }
        StatCards(state, data)
        Row(Modifier.fillMaxWidth().height(IntrinsicChartHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StateDonut(state, data, Modifier.weight(1.25f).fillMaxHeight())
            DepartmentChart(state, data, Modifier.weight(1f).fillMaxHeight())
            DistrictChart(state, data, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecentReceipts(state, data.recentReceipts, Modifier.weight(1f)) { historyBatch = it }
            RecentIssues(state, data.recentIssues, Modifier.weight(1f)) { historyBatch = it }
        }
    }
    historyBatch?.let { id -> BatchHistoryDialog(state, id) { historyBatch = null } }
}

private val IntrinsicChartHeight = 284.dp

@Composable
private fun StatCards(state: AppState, data: Dashboard) {
    val red = data.stateCounts[ExpiryState.RED] ?: 0
    val yellow = data.stateCounts[ExpiryState.YELLOW] ?: 0
    StatCardRow {
        val card = Modifier.weight(1f).fillMaxHeight()
        StatCard(
            data.reagentTypes.toString(), "Reaktiv turlari", card,
            icon = AppIcons.Reagents,
            hint = "Barcha reaktivlar",
            onClick = { state.navigate(Page.REAGENTS) },
            testTag = "card-types",
        )
        StatCard(
            data.activeBatches.toString(), "Faol partiyalar", card,
            icon = AppIcons.Stock, accent = AppColors.Navy,
            hint = "Qoldig‘i bor",
            onClick = { state.navigate(Page.REAGENTS, PageIntent.ReagentsFiltered(withStockOnly = true)) },
            testTag = "card-active",
        )
        StatCard(
            data.expiringBatches.toString(), "Muddati yaqinlashgan", card,
            icon = AppIcons.Expiry, accent = StatusStyles.YELLOW.accent,
            hint = "$red ta qizil, $yellow ta sariq",
            onClick = { state.navigate(Page.EXPIRY, PageIntent.FilterExpiry(setOf(ExpiryState.RED, ExpiryState.YELLOW))) },
            testTag = "card-expiring",
        )
        StatCard(
            data.expiredBatches.toString(), "Muddati o‘tgan", card,
            icon = AppIcons.Warning, accent = StatusStyles.EXPIRED.accent,
            hint = if (data.expiredBatches > 0) "Chora ko‘ring" else "Yo‘q",
            onClick = { state.navigate(Page.EXPIRY, PageIntent.FilterExpiry(setOf(ExpiryState.EXPIRED))) },
            testTag = "card-expired",
        )
        StatCard(
            data.belowMinReagents.toString(), "Minimal qoldiqdan kam", card,
            icon = AppIcons.Issues, accent = StatusStyles.RED.accent,
            hint = "Umumiy zaxiralar",
            onClick = { state.navigate(Page.STOCK, PageIntent.StockFiltered(belowMinOnly = true)) },
            testTag = "card-below-min",
        )
    }
}

@Composable
private fun StateDonut(state: AppState, data: Dashboard, modifier: Modifier) {
    FormSection("Yaroqlilik holati", modifier, description = "Qoldig‘i bor partiyalar") {
        val segments = EXPIRY_ORDER.map { s ->
            ChartSegment(s.label, (data.stateCounts[s] ?: 0).toDouble(), s.style.accent, "${data.stateCounts[s] ?: 0} ta")
        }
        DonutChart(
            segments = segments,
            // 1280×720 da ham izoh (5 qator) halqa yonida qolsin (Charts.kt, LegendMinWidth).
            size = 100.dp,
            thickness = 18.dp,
            centerValue = data.activeBatches.toString(),
            centerLabel = "partiya",
            onSegmentClick = { segment ->
                val target = EXPIRY_ORDER.firstOrNull { it.label == segment.label } ?: return@DonutChart
                state.navigate(Page.REAGENTS, PageIntent.ReagentsFiltered(states = setOf(target), withStockOnly = true))
            },
            testTag = "state-donut",
        )
    }
}

/** Grafikda ko‘rsatiladigan o‘lchov turi: ko‘proq qiymati bor tur (teng bo‘lsa — hajm). */
private fun preferredDimension(maps: List<Map<Dimension, Long>>): Dimension? {
    val used = Dimension.entries.filter { d -> maps.any { (it[d] ?: 0L) != 0L } }
    return used.maxByOrNull { d -> maps.count { (it[d] ?: 0L) != 0L } * 10 - d.ordinal }
}

private fun dimensionTitle(dimension: Dimension): String = dimension.title.replaceFirstChar { it.uppercase() }

private fun bar(label: String, base: Long, dimension: Dimension): ChartBar =
    ChartBar(label, base.toDouble() / MeasureUnit.main(dimension).factor, Qty.formatAuto(base, dimension))

private enum class DashboardDeptMetric(val title: String, val hint: String) {
    REMAINDER("Qoldiq", "Bo‘limga biriktirilgan partiyalardagi joriy qoldiq"),
    ISSUED("Berilgan", "Bo‘limga berilgan (qaytarilgani ayirilgan)"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DepartmentChart(state: AppState, data: Dashboard, modifier: Modifier) {
    var metric by remember { mutableStateOf(DashboardDeptMetric.REMAINDER) }
    val maps = data.byDepartment.map { if (metric == DashboardDeptMetric.REMAINDER) it.remainder else it.issuedTo }
    val dims = Dimension.entries.filter { d -> maps.any { (it[d] ?: 0L) != 0L } }
    var chosen by remember { mutableStateOf<Dimension?>(null) }
    val dimension = chosen?.takeIf { it in dims } ?: preferredDimension(maps)
    FormSection(
        "Bo‘limlar bo‘yicha",
        modifier,
        description = metric.hint,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SegmentedChoice(DashboardDeptMetric.entries, metric, { metric = it }, label = { it.title }, testTag = "dept-metric")
            if (dims.size > 1 && dimension != null) {
                SegmentedChoice(dims, dimension, { chosen = it }, label = { dimensionTitle(it) }, testTag = "dept-dim")
            }
        }
        if (dimension == null) {
            NoChartData(if (metric == DashboardDeptMetric.REMAINDER) "Bo‘limlarga biriktirilgan qoldiq yo‘q" else "Bo‘limlarga hali reaktiv berilmagan")
        } else {
            // Kattasidan kichigiga, 0 lari tushiriladi; sig‘maganlari — «Yana N ta — hammasi» (Bo‘limlar sahifasi).
            val values = data.byDepartment
                .map { it to ((if (metric == DashboardDeptMetric.REMAINDER) it.remainder else it.issuedTo)[dimension] ?: 0L) }
                .filter { it.second != 0L }
                .sortedByDescending { it.second }
            FittedBarChart(
                bars = values.map { (d, v) -> bar(d.name, v, dimension) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                labelWidth = 92.dp,
                valueWidth = 72.dp,
                onBarClick = { b ->
                    data.byDepartment.firstOrNull { it.name == b.label }?.let { state.navigate(Page.DEPARTMENTS, PageIntent.DepartmentOf(it.departmentId)) }
                },
                onMore = { state.navigate(Page.DEPARTMENTS) },
                testTag = "dept-chart",
            )
        }
    }
}

@Composable
private fun DistrictChart(state: AppState, data: Dashboard, modifier: Modifier) {
    val maps = data.byDistrict.map { it.issued }
    val dims = Dimension.entries.filter { d -> maps.any { (it[d] ?: 0L) != 0L } }
    var chosen by remember { mutableStateOf<Dimension?>(null) }
    val dimension = chosen?.takeIf { it in dims } ?: preferredDimension(maps)
    FormSection("Tumanlarga berilgan", modifier, description = "Qaytarilgani ayirilgan, o‘lchov turi bo‘yicha") {
        if (dims.size > 1 && dimension != null) {
            SegmentedChoice(dims, dimension, { chosen = it }, label = { dimensionTitle(it) }, testTag = "district-dim")
        }
        if (dimension == null) {
            NoChartData("Tumanlarga hali reaktiv berilmagan")
        } else {
            val bars = data.byDistrict
                .filter { (it.issued[dimension] ?: 0L) != 0L }
                .sortedByDescending { it.issued[dimension] ?: 0L }
            FittedBarChart(
                bars = bars.map { bar(it.name, it.issued[dimension] ?: 0L, dimension) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = AppColors.Navy,
                labelWidth = 92.dp,
                valueWidth = 72.dp,
                onBarClick = { b ->
                    data.byDistrict.firstOrNull { it.name == b.label }?.let { state.navigate(Page.DISTRICTS, PageIntent.DistrictOf(it.districtId)) }
                },
                onMore = { state.navigate(Page.DISTRICTS) },
                testTag = "district-chart",
            )
        }
    }
}

@Composable
private fun NoChartData(text: String) {
    Text(text, fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun RecentReceipts(state: AppState, rows: List<ReceiptRow>, modifier: Modifier, onOpen: (Long) -> Unit) {
    FormSection(
        "So‘nggi kirimlar",
        modifier,
        actions = { AppButton("Hammasi", onClick = { state.navigate(Page.RECEIPTS) }, kind = ButtonKind.TEXT) },
    ) {
        if (rows.isEmpty()) {
            NoChartData("Hali kirim yo‘q")
        } else {
            Column {
                rows.take(RECENT_LIMIT).forEachIndexed { index, r ->
                    if (index > 0) HorizontalDivider(color = AppColors.Divider)
                    RecentLine(
                        date = r.receivedOn,
                        title = r.name,
                        detail = listOf(r.series.ifBlank { "seriyasiz" }, if (r.isInitial) "yangi partiya" else "qo‘shimcha kirim").joinToString(" · "),
                        amount = "+" + Qty.formatWithUnit(r.qty, r.enteredUnit),
                        cancelled = r.cancelled,
                        tag = "recent-receipt-$index",
                        onClick = { onOpen(r.batchId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentIssues(state: AppState, rows: List<IssueItemRow>, modifier: Modifier, onOpen: (Long) -> Unit) {
    FormSection(
        "So‘nggi berishlar",
        modifier,
        actions = { AppButton("Hammasi", onClick = { state.navigate(Page.ISSUES) }, kind = ButtonKind.TEXT) },
    ) {
        if (rows.isEmpty()) {
            NoChartData("Hali berish yo‘q")
        } else {
            Column {
                rows.take(RECENT_LIMIT).forEachIndexed { index, r ->
                    if (index > 0) HorizontalDivider(color = AppColors.Divider)
                    val recipient = when (r.recipientType) {
                        RecipientType.DEPARTMENT -> "${r.recipientName} bo‘limi"
                        RecipientType.DISTRICT -> "${r.recipientName} tumani"
                        RecipientType.WRITE_OFF -> r.recipientName
                    }
                    RecentLine(
                        date = r.issuedOn,
                        title = r.name,
                        detail = listOf(r.series.ifBlank { "seriyasiz" }, recipient).joinToString(" · ") +
                            if (r.returnedQty > 0) " · qaytarildi ${Qty.formatWithUnit(r.returnedQty, r.enteredUnit)}" else "",
                        amount = "−" + Qty.formatWithUnit(r.qty, r.enteredUnit),
                        cancelled = r.cancelled,
                        tag = "recent-issue-$index",
                        onClick = { onOpen(r.batchId) },
                    )
                }
            }
        }
    }
}

private const val RECENT_LIMIT = 6

@Composable
private fun RecentLine(
    date: java.time.LocalDate,
    title: String,
    detail: String,
    amount: String,
    cancelled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(UzDates.format(date), fontSize = 12.5.sp, color = AppColors.Muted, modifier = Modifier.width(84.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (cancelled) AppColors.Muted else AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (cancelled) "bekor qilingan · " else "") + detail,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = AppColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            amount,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                cancelled -> AppColors.Disabled
                amount.startsWith("+") -> AppColors.Success
                else -> AppColors.Text
            },
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

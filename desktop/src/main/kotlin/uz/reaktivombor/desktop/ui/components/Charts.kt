package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.theme.AppColors
import kotlin.math.roundToLong

/** Gorizontal ustunli grafikning bitta ustuni. [color] null — grafikning umumiy rangi. */
@Immutable
data class ChartBar(val label: String, val value: Double, val valueText: String = formatChartNumber(value), val color: Color? = null)

/** Halqa grafigining bo‘lagi. */
@Immutable
data class ChartSegment(val label: String, val value: Double, val color: Color, val valueText: String = formatChartNumber(value))

/** 1250 → "1 250", 12.5 → "12,5" (grafiklardagi sonlar). */
fun formatChartNumber(value: Double): String {
    val rounded = (value * 10).roundToLong()
    val whole = rounded / 10
    val tenth = kotlin.math.abs(rounded % 10)
    val grouped = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (tenth == 0L) grouped else "$grouped,$tenth"
}

/**
 * Gorizontal ustunlar: chapda nom, o‘rtada ustun (eng kattasiga nisbatan), o‘ngda qiymat. Ustun ustiga kelganda
 * to‘liq nom va qiymat izohda chiqadi; [onBarClick] bo‘lsa qator bosiladi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    color: Color = AppColors.Primary,
    labelWidth: Dp = 150.dp,
    valueWidth: Dp = 76.dp,
    barThickness: Dp = 14.dp,
    emptyText: String = "Ma’lumot yo‘q",
    onBarClick: ((ChartBar) -> Unit)? = null,
    testTag: String = "bar-chart",
) {
    Column(modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (bars.isEmpty()) {
            Text(emptyText, fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(vertical = 12.dp))
            return@Column
        }
        val max = bars.maxOf { it.value }.coerceAtLeast(0.0)
        bars.forEach { bar ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            TooltipArea(
                tooltip = { TooltipBox("${bar.label}: ${bar.valueText}") },
                delayMillis = 300,
                tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(barThickness + 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hovered) AppColors.Background else Color.Transparent)
                        .hoverable(interaction)
                        .let { if (onBarClick != null) it.clickable(role = Role.Button) { onBarClick(bar) } else it }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        bar.label,
                        modifier = Modifier.width(labelWidth),
                        fontSize = 12.5.sp,
                        color = AppColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    val barColor = bar.color ?: color
                    val fraction = if (max > 0) (bar.value / max).toFloat().coerceIn(0f, 1f) else 0f
                    Canvas(Modifier.weight(1f).height(barThickness)) {
                        // Asos (chap) to‘g‘ri burchakli, uchi 4dp yumaloq.
                        drawRect(AppColors.Background, size = size)
                        if (bar.value > 0) {
                            val w = (size.width * fraction).coerceAtLeast(2.dp.toPx())
                            val r = 4.dp.toPx().coerceAtMost(w / 2)
                            val path = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = 0f, top = 0f, right = w, bottom = size.height,
                                        topLeftCornerRadius = CornerRadius.Zero,
                                        topRightCornerRadius = CornerRadius(r, r),
                                        bottomRightCornerRadius = CornerRadius(r, r),
                                        bottomLeftCornerRadius = CornerRadius.Zero,
                                    ),
                                )
                            }
                            drawPath(path, barColor)
                        }
                    }
                    Text(
                        bar.valueText,
                        modifier = Modifier.width(valueWidth),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Text,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Halqa grafigi: bo‘laklar orasida ingichka oq oraliq, markazda jami, yonida (joy yetmasa — ostida) izoh: rang, nom,
 * qiymat, foiz. Izoh qatori ustiga kelganda bo‘lak qalinlashadi. Jami 0 bo‘lsa — kulrang halqa.
 */
@Composable
fun DonutChart(
    segments: List<ChartSegment>,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    thickness: Dp = 22.dp,
    centerValue: String? = null,
    centerLabel: String? = "Jami",
    showLegend: Boolean = true,
    onSegmentClick: ((ChartSegment) -> Unit)? = null,
    testTag: String = "donut-chart",
) {
    val total = segments.sumOf { it.value.coerceAtLeast(0.0) }
    var highlighted by remember { mutableIntStateOf(-1) }
    LaunchedEffect(segments) { highlighted = -1 }
    val ring: @Composable () -> Unit = {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val wide = thickness.toPx() + 6.dp.toPx()
                val stroke = thickness.toPx()
                val radius = (this.size.minDimension - wide) / 2
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)
                if (total <= 0) {
                    drawArc(AppColors.Divider, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    return@Canvas
                }
                val visible = segments.count { it.value > 0 }
                // Bo‘laklar orasidagi 2dp oraliq (burchakda).
                val gap = if (visible > 1) Math.toDegrees((2.dp.toPx() / radius).toDouble()).toFloat() else 0f
                var start = -90f
                segments.forEachIndexed { index, segment ->
                    if (segment.value <= 0) return@forEachIndexed
                    val sweep = (segment.value / total * 360).toFloat()
                    drawArc(
                        color = segment.color,
                        startAngle = start + gap / 2,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(if (index == highlighted) wide else stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue ?: formatChartNumber(total), fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
                if (centerLabel != null) Text(centerLabel, fontSize = 12.sp, lineHeight = 15.sp, color = AppColors.Muted)
            }
        }
    }
    val legend: @Composable (Modifier) -> Unit = { legendModifier ->
        Column(legendModifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            segments.forEachIndexed { index, segment ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                LaunchedEffect(hovered) {
                    if (hovered) highlighted = index else if (highlighted == index) highlighted = -1
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hovered) AppColors.Background else Color.Transparent)
                        .hoverable(interaction)
                        .let { if (onSegmentClick != null) it.clickable(role = Role.Button) { onSegmentClick(segment) } else it }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(segment.color))
                    Spacer(Modifier.width(8.dp))
                    Text(segment.label, fontSize = 12.5.sp, color = AppColors.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(segment.valueText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, textAlign = TextAlign.End, maxLines = 1)
                    val percent = if (total > 0) (segment.value / total * 100).roundToLong() else 0L
                    Text("$percent%", fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.width(44.dp), textAlign = TextAlign.End, maxLines = 1)
                }
            }
        }
    }
    BoxWithConstraints(modifier.testTag(testTag)) {
        when {
            !showLegend -> ring()
            // Yonida izohga joy yetmasa — ostida.
            maxWidth < size + 18.dp + LegendMinWidth -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ring()
                Spacer(Modifier.height(10.dp))
                legend(Modifier.fillMaxWidth())
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                ring()
                Spacer(Modifier.width(18.dp))
                legend(Modifier.weight(1f))
            }
        }
    }
}

private val LegendMinWidth = 230.dp

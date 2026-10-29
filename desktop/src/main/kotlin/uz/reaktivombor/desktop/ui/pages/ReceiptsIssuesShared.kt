package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.components.FieldFrame
import uz.reaktivombor.desktop.ui.components.FieldTextStyle
import uz.reaktivombor.desktop.ui.components.IconAction
import uz.reaktivombor.desktop.ui.components.LabeledField
import uz.reaktivombor.desktop.ui.components.ReasonDialog
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TooltipBox
import uz.reaktivombor.desktop.ui.components.icon
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import uz.reaktivombor.desktop.ui.theme.style

// «Kirim operatsiyalari» va «Sarf va berish» sahifalari uchun umumiy kichik ko‘rinishlar va yordamchilar.

/** Jadval balandligi (sahifa aylanadi, jadval o‘zi ham aylanadi): 1366×768 da filtr bilan birga ekranga sig‘adi. */
internal val OpsTableHeight = 430.dp

/** Ixcham ogohlantirish (forma ichida): belgi va matn — daraja rangida. */
@Composable
internal fun OpsNotice(text: String, severity: Severity, modifier: Modifier = Modifier, testTag: String? = null) {
    val style = severity.style
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(style.background)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(severity.icon, contentDescription = null, tint = style.accent, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, color = style.foreground)
    }
}

/** Operatsiya holati jadvalda: faol, bekor qilingan yoki tuzatilgan (o‘rniga yangi yozuv bor). */
internal enum class OpsStatus(val label: String) {
    ACTIVE("Faol"),
    CANCELLED("Bekor qilingan"),
    CORRECTED("Tuzatilgan"),
}

/** [correctedIds] — boshqa yozuvning `corrects_id` si bo‘lgan (ya’ni tuzatilgan) yozuvlar. */
internal fun opsStatus(id: Long, cancelled: Boolean, correctedIds: Set<Long>): OpsStatus = when {
    !cancelled -> OpsStatus.ACTIVE
    id in correctedIds -> OpsStatus.CORRECTED
    else -> OpsStatus.CANCELLED
}

/** Holat katakchasi: rangli belgi; bekor qilingan/tuzatilganda yonida sababi (to‘liq matni — izohda). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OpsStatusCell(status: OpsStatus, reason: String?, isCorrection: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            OpsStatus.ACTIVE -> StatusChip(
                if (isCorrection) "Faol (tuzatish)" else status.label,
                StatusStyles.GREEN.background,
                StatusStyles.GREEN.foreground,
            )
            OpsStatus.CANCELLED -> StatusChip(status.label, StatusStyles.RED.background, StatusStyles.RED.foreground)
            OpsStatus.CORRECTED -> StatusChip(status.label, StatusStyles.UNKNOWN.background, StatusStyles.UNKNOWN.foreground)
        }
        if (status != OpsStatus.ACTIVE && !reason.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            TooltipArea(
                tooltip = { TooltipBox("Sabab: $reason") },
                delayMillis = 300,
                tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
            ) {
                Text(reason, fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Saqlangandan keyingi yashil panel (sarlavha, matn, tugmalar va ×). */
@Composable
internal fun OpsSuccessPanel(
    title: String,
    text: String,
    onClose: () -> Unit,
    testTag: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val style = Severity.SUCCESS.style
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(style.background)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(style.accent))
        Row(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Success, contentDescription = null, tint = style.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).widthIn(min = 200.dp)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = style.foreground)
                Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = style.foreground)
            }
            actions()
            IconAction(AppIcons.Close, "Yopish", onClick = onClose, tint = style.foreground, size = 28.dp, iconSize = 16.dp)
        }
    }
}

/** Bo‘lim sarlavhasi (forma va jadval bo‘limlari orasida): nom va kulrang izoh. */
@Composable
internal fun OpsSectionTitle(title: String, hint: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
            if (hint != null) Text(hint, fontSize = 12.5.sp, lineHeight = 17.sp, color = AppColors.Muted)
        }
        actions()
    }
}

/** Shakl tugmalari qatori: chapda xato/izoh matni, o‘ngda tugmalar. */
@Composable
internal fun OpsFormButtons(message: String?, messageTag: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (message != null) {
            Text(message, fontSize = 13.sp, lineHeight = 18.sp, color = AppColors.Error, modifier = Modifier.weight(1f).testTag(messageTag))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = { content() })
    }
}

/** Kiritilgan miqdor: bo‘sh — (null, null); to‘g‘ri — (asosiy birlikda, null); noto‘g‘ri — (null, xato). */
internal fun opsParseQty(text: String, unit: MeasureUnit?, label: String = "Miqdor"): Pair<Long?, String?> {
    if (text.isBlank()) return null to null
    if (unit == null) return null to "O‘lchov birligini tanlang."
    return try {
        Qty.parse(text, unit, label) to null
    } catch (e: UserError) {
        null to e.message
    }
}

/** Miqdorni shu o‘lchov turidagi birlikda ko‘rsatish: 3 tadan ko‘p kasr bo‘lsa — kichikroq birlikda ("1,2345 l" emas, "1 234,5 ml"). */
internal fun opsEditableQty(base: Long, preferred: MeasureUnit): Pair<String, MeasureUnit> {
    val text = Qty.format(base, preferred)
    val decimals = text.substringAfter(',', "").length
    if (decimals <= Qty.MAX_DECIMALS) return text to preferred
    val small = preferred.siblings.minByOrNull { it.factor } ?: preferred
    return Qty.format(base, small) to small
}

/**
 * Faqat ro‘yxatdan tanlanadigan maydon, bandlari boy ko‘rinishda ([item] — belgi, holat va qoldiq bilan).
 * Tanlangani maydonning o‘zida ham [item] bilan chiziladi. Enter, probel yoki ↓ — ochadi.
 */
@Composable
internal fun <T> OpsRichSelect(
    value: T?,
    options: List<T>,
    onSelect: (T) -> Unit,
    optionTag: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String = "Tanlang",
    error: String? = null,
    help: String? = null,
    enabled: Boolean = true,
    menuMinWidth: Dp = 160.dp,
    emptyText: String = "Ro‘yxat bo‘sh",
    testTag: String = "rich-select",
    item: @Composable RowScope.(option: T, inField: Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val density = LocalDensity.current

    LabeledField(label, modifier, required, error, help) {
        Box {
            FieldFrame(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { widthPx = it.width }
                    .onPreviewKeyEvent { event ->
                        val opens = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar || event.key == Key.DirectionDown
                        if (enabled && event.type == KeyEventType.KeyDown && opens) {
                            expanded = true
                            true
                        } else {
                            false
                        }
                    }
                    .focusable(enabled, interaction)
                    .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.DropdownList) { expanded = !expanded }
                    .testTag(testTag),
                focused = focused || expanded,
                isError = error != null,
                enabled = enabled,
                padding = PaddingValues(start = 10.dp, end = 6.dp),
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (value != null) {
                        item(value, true)
                    } else {
                        Text(placeholder, style = FieldTextStyle.copy(color = AppColors.Disabled), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(AppIcons.DropDown, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(22.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(density) { widthPx.toDp() }.coerceAtLeast(menuMinWidth)).heightIn(max = 360.dp),
            ) {
                options.forEach { option ->
                    val selected = option == value
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) { item(option, false) } },
                        trailingIcon = if (selected) {
                            { Icon(AppIcons.Check, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .background(if (selected) AppColors.PrimarySoft else AppColors.Surface)
                            .testTag(optionTag(option)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    )
                }
                if (options.isEmpty()) {
                    Text(emptyText, fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

/** «FEFO tavsiyasi» belgisi (partiya tanlash ro‘yxatida va maydonda). */
@Composable
internal fun OpsFefoBadge(modifier: Modifier = Modifier) {
    StatusChip("FEFO tavsiyasi", AppColors.PrimarySoft, AppColors.Primary, modifier, dot = null, bold = true)
}

/** Tab ko‘rinishidagi ikki-uch tugma (masalan, «Berish tarixi | Qaytarishlar»): faoli ko‘k va tagi chizilgan. */
@Composable
internal fun OpsTabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(AppColors.Border))
        Row {
            options.forEach { (id, title) ->
                val active = id == selected
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .clickable(role = Role.Tab) { onSelect(id) }
                        .testTag("tab-$id"),
                ) {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) AppColors.Primary else AppColors.Muted,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) AppColors.Primary else Color.Transparent))
                }
            }
        }
    }
}

/** Sabab so‘raladigan amal (bekor qilish): sarlavha, izoh va tasdiqlash tugmasi. */
internal class OpsReasonRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val label: String,
    val onConfirm: (String) -> Unit,
)

/**
 * Sabab oynasi holati. Common.kt dagi `askReason` dan farqi: qaytish tugmasi «Orqaga» — «Bekor qilish» amali bilan
 * adashtirilmasin (tasdiqlash tugmasi ham «Bekor qilish» bo‘lgani uchun).
 */
@Stable
internal class OpsReasonState {
    var request: OpsReasonRequest? by mutableStateOf(null)
        private set

    fun ask(title: String, message: String, confirmLabel: String, label: String = "Sabab", onConfirm: (String) -> Unit) {
        request = OpsReasonRequest(title, message, confirmLabel, label, onConfirm)
    }

    fun close() {
        request = null
    }
}

@Composable
internal fun rememberOpsReasonState(): OpsReasonState = remember { OpsReasonState() }

/** [OpsReasonState] oynasi (sahifa yoki bo‘lim oxirida bir marta). */
@Composable
internal fun OpsReasonHost(reasons: OpsReasonState, busy: Boolean) {
    val r = reasons.request ?: return
    key(r) {
        ReasonDialog(
            title = r.title,
            message = r.message,
            label = r.label,
            placeholder = "Sababini yozing",
            confirmLabel = r.confirmLabel,
            dismissLabel = "Orqaga",
            danger = true,
            busy = busy,
            onConfirm = { reason ->
                reasons.close()
                r.onConfirm(reason)
            },
            onDismiss = { reasons.close() },
        )
    }
}

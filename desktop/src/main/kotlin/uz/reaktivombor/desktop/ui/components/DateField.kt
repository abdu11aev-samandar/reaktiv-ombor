package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.UzDates
import uz.reaktivombor.desktop.ui.theme.AppColors
import java.time.LocalDate
import java.time.YearMonth

/** Sanani tekshirish natijasi: to‘g‘ri sana (yoki bo‘sh) va xato matni. */
data class DateCheck(val date: LocalDate?, val error: String?)

/**
 * Yozilgan sanani tekshiradi: bo‘sh (majburiy bo‘lsa — xato), noto‘g‘ri yoki [min]/[max] oralig‘idan tashqarida.
 * [today] — «bugundan keyin» deb aniqroq yozish uchun.
 */
fun checkDateText(text: String, required: Boolean, min: LocalDate?, max: LocalDate?, today: LocalDate): DateCheck {
    if (text.isBlank()) return DateCheck(null, if (required) "Sana kiritilishi shart." else null)
    val date = UzDates.parse(text) ?: return DateCheck(null, "Sana noto‘g‘ri. Masalan: ${UzDates.format(today)}")
    if (min != null && date < min) return DateCheck(null, "Sana ${UzDates.format(min)} dan oldin bo‘lishi mumkin emas.")
    if (max != null && date > max) {
        return DateCheck(null, if (max == today) "Sana bugundan keyin bo‘lishi mumkin emas." else "Sana ${UzDates.format(max)} dan keyin bo‘lishi mumkin emas.")
    }
    return DateCheck(date, null)
}

/** Yozish tugaganga o‘xshaydi (kun, oy va yil bor) — shundagina yozish paytida xato ko‘rsatiladi. */
private val COMPLETE = Regex("""\s*\d{1,2}[./\-]\d{1,2}[./\-](\d{4}|\d{2})\s*|\s*\d{4}-\d{1,2}-\d{1,2}\s*""")

/**
 * Sana maydoni: `dd.MM.yyyy` yoziladi (17.10.2026, 7.1.26, 2026-10-17 ham bo‘ladi) yoki taqvimdan tanlanadi
 * (belgi, Alt+↓ yoki F4). Noto‘g‘ri yoki oraliqdan tashqari sana — [onValueChange] ga null va maydon ostida xato;
 * [onErrorChange] shaklga xato borligini bildiradi. Tashqi [error] (masalan, servisdan) ichki xatodan ustun.
 */
@Composable
fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    today: LocalDate = LocalDate.now(),
    error: String? = null,
    help: String? = null,
    onErrorChange: (String?) -> Unit = {},
    placeholder: String = "kk.oo.yyyy",
    testTag: String = "date",
) {
    var field by remember { mutableStateOf(TextFieldValue(UzDates.format(value))) }
    var focused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Tashqaridan o‘zgargan qiymat (taqvim, tozalash, shakl qayta ochildi) matnni almashtiradi.
    // (Oraliqdan tashqari sana ham null — foydalanuvchi yozgan matn o‘chib ketmasin.)
    val parsedText = checkDateText(field.text, required = false, min = minDate, max = maxDate, today = today).date
    val shown = if (parsedText == value) field else {
        TextFieldValue(UzDates.format(value), TextRange(UzDates.format(value).length))
    }
    val check = checkDateText(shown.text, required, minDate, maxDate, today)
    // Yozish paytida chala sana xato emas; maydondan chiqqanda yoki sana to‘liq yozilganda ko‘rsatiladi.
    val showOwnError = check.error != null && ((touched && !focused) || (shown.text.isNotBlank() && COMPLETE.matches(shown.text)))
    val message = error ?: check.error.takeIf { showOwnError }

    fun commitText(next: TextFieldValue) {
        field = next
        val result = checkDateText(next.text, required, minDate, maxDate, today)
        onErrorChange(result.error)
        if (result.date != value) onValueChange(result.date)
    }

    fun pick(date: LocalDate?) {
        val text = UzDates.format(date)
        field = TextFieldValue(text, TextRange(text.length))
        calendarOpen = false
        touched = true
        val result = checkDateText(text, required, minDate, maxDate, today)
        onErrorChange(result.error)
        if (result.date != value || date == null) onValueChange(result.date)
        runCatching { focus.requestFocus() }
    }

    LabeledField(label, modifier, required, message, help) {
        Box {
            FieldFrame(
                Modifier.fillMaxWidth(),
                focused = focused || calendarOpen,
                isError = message != null,
                enabled = enabled,
                padding = PaddingValues(start = 10.dp, end = 3.dp),
            ) {
                BasicTextField(
                    value = shown,
                    onValueChange = { next -> if (next.text != shown.text) commitText(next) else field = next },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .onFocusChanged { state ->
                            if (focused && !state.isFocused) touched = true
                            focused = state.isFocused
                        }
                        .onPreviewKeyEvent { event ->
                            val opens = event.key == Key.F4 || (event.isAltPressed && event.key == Key.DirectionDown)
                            if (enabled && event.type == KeyEventType.KeyDown && opens) {
                                calendarOpen = true
                                true
                            } else {
                                false
                            }
                        }
                        .testTag(testTag),
                    enabled = enabled,
                    singleLine = true,
                    textStyle = FieldTextStyle.copy(color = if (enabled) AppColors.Text else AppColors.Muted),
                    cursorBrush = SolidColor(AppColors.Primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (shown.text.isEmpty()) Text(placeholder, style = FieldTextStyle.copy(color = AppColors.Disabled), maxLines = 1)
                            inner()
                        }
                    },
                )
                IconAction(
                    icon = AppIcons.Calendar,
                    label = "Taqvimni ochish",
                    onClick = { calendarOpen = !calendarOpen },
                    tint = AppColors.Muted,
                    enabled = enabled,
                    iconSize = 18.dp,
                    testTag = "$testTag-calendar",
                )
            }
            DropdownMenu(
                expanded = calendarOpen,
                onDismissRequest = { calendarOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                CalendarPanel(
                    selected = check.date ?: value,
                    today = today,
                    minDate = minDate,
                    maxDate = maxDate,
                    onPick = { pick(it) },
                    onClear = { pick(null) },
                    testTag = "$testTag-cal",
                )
            }
        }
    }
}

/**
 * Taqvim: oy va yil o‘tkazish (‹ ›, « »), oy nomini bosib oylar ro‘yxati, hafta dushanbadan, bugun ramkada,
 * tanlangani ko‘k. Pastda «Bugun» va «Tozalash». [minDate]/[maxDate] dan tashqari kunlarni tanlab bo‘lmaydi.
 */
@Composable
fun CalendarPanel(
    selected: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    testTag: String = "cal",
) {
    var month by remember { mutableStateOf(YearMonth.from(selected ?: today)) }
    var monthMode by remember { mutableStateOf(false) }
    fun allowed(date: LocalDate) = (minDate == null || date >= minDate) && (maxDate == null || date <= maxDate)

    Column(modifier.width(CalendarWidth).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            NavButton("«", "Oldingi yil", "$testTag-prev-year") { month = month.minusYears(1) }
            if (!monthMode) NavButton("‹", "Oldingi oy", "$testTag-prev-month") { month = month.minusMonths(1) }
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { monthMode = !monthMode }
                    .testTag("$testTag-title"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (monthMode) month.year.toString() else UzDates.month(month),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Text,
                )
            }
            if (!monthMode) NavButton("›", "Keyingi oy", "$testTag-next-month") { month = month.plusMonths(1) }
            NavButton("»", "Keyingi yil", "$testTag-next-year") { month = month.plusYears(1) }
        }
        Spacer(Modifier.height(4.dp))
        if (monthMode) {
            // Oylar: 3 ustun × 4 qator
            UzDates.MONTHS.chunked(3).forEachIndexed { rowIndex, names ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    names.forEachIndexed { col, name ->
                        val m = month.withMonth(rowIndex * 3 + col + 1)
                        val current = selected != null && YearMonth.from(selected) == m
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (current) AppColors.Primary else Color.Transparent)
                                .clickable(role = Role.Button) {
                                    month = m
                                    monthMode = false
                                }
                                .testTag("$testTag-month-${m.monthValue}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(name, fontSize = 13.sp, color = if (current) Color.White else AppColors.Text)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                UzDates.WEEKDAYS_SHORT.forEachIndexed { index, day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (index >= 5) AppColors.Error.copy(alpha = 0.75f) else AppColors.Muted,
                    )
                }
            }
            val first = month.atDay(1)
            val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
            // Doim 6 hafta — oy almashganda panel o‘lchami o‘zgarmaydi.
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { day ->
                        val date = start.plusDays((week * 7 + day).toLong())
                        DayCell(
                            date = date,
                            inMonth = YearMonth.from(date) == month,
                            selected = date == selected,
                            isToday = date == today,
                            enabled = allowed(date),
                            onClick = { onPick(date) },
                            modifier = Modifier.weight(1f).testTag("$testTag-day-$date"),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = AppColors.Divider)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppButton("Bugun", onClick = { onPick(today) }, kind = ButtonKind.TEXT, enabled = allowed(today))
            Spacer(Modifier.weight(1f))
            if (onClear != null) AppButton("Tozalash", onClick = onClear, kind = ButtonKind.TEXT)
        }
    }
}

private val CalendarWidth: Dp = 280.dp

@Composable
private fun NavButton(symbol: String, label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 17.sp, lineHeight = 17.sp, color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.height(34.dp).padding(2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(30.dp)
                .clip(shape)
                .background(if (selected) AppColors.Primary else Color.Transparent)
                .let { if (isToday && !selected) it.border(1.5.dp, AppColors.Primary, shape) else it }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    selected -> Color.White
                    !enabled -> AppColors.Divider
                    isToday -> AppColors.Primary
                    !inMonth -> AppColors.Disabled
                    else -> AppColors.Text
                },
            )
        }
    }
}

/**
 * Davr: «dan» va «gacha» sanalari yonma-yon. Boshlanishi tugashidan keyin bo‘lsa — xato matni (qiymatlar baribir
 * beriladi, sahifa qidiruvni to‘xtatishi mumkin).
 */
@Composable
fun DateRangeField(
    from: LocalDate?,
    to: LocalDate?,
    onChange: (from: LocalDate?, to: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Davr",
    today: LocalDate = LocalDate.now(),
    enabled: Boolean = true,
    fieldWidth: Dp = 150.dp,
    testTag: String = "range",
) {
    val wrong = from != null && to != null && from > to
    LabeledField(label, modifier, error = if (wrong) "Boshlanish sanasi tugash sanasidan keyin bo‘lmasligi kerak." else null) {
        Row(verticalAlignment = Alignment.Top) {
            DateField(
                value = from,
                onValueChange = { onChange(it, to) },
                modifier = Modifier.width(fieldWidth),
                today = today,
                enabled = enabled,
                placeholder = "dan",
                testTag = "$testTag-from",
            )
            Text("—", color = AppColors.Muted, modifier = Modifier.padding(horizontal = 6.dp).height(FieldHeight).padding(top = 8.dp))
            DateField(
                value = to,
                onValueChange = { onChange(from, it) },
                modifier = Modifier.width(fieldWidth),
                today = today,
                enabled = enabled,
                placeholder = "gacha",
                testTag = "$testTag-to",
            )
        }
    }
}

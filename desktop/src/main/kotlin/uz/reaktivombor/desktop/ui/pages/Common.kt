package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.daysLeft
import uz.reaktivombor.desktop.core.daysLeftLabel
import uz.reaktivombor.desktop.core.expiryState
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchRow
import uz.reaktivombor.desktop.service.LedgerRow
import uz.reaktivombor.desktop.service.MovementKind
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.UzDates
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.ConfirmDialog
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.IconAction
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.components.ReasonDialog
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.StatusStyle
import uz.reaktivombor.desktop.ui.theme.statusStyle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Sahifalar uchun umumiy yordamchilar. Keyingi sahifalar ularni ishlatadi, lekin o‘zgartirmaydi (DESIGN.md §6.1).

// ---------------------------------------------------------------- sahifa ramkasi

/** Sarlavha, izoh va tugmalar + vertikal aylantiriladigan mazmun (shakl va bosh sahifa uchun; jadvalli sahifa — PageScaffold). */
@Composable
fun ScrollPage(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    testTag: String = "page-scroll",
    content: @Composable ColumnScope.() -> Unit,
) {
    PageScaffold(title = title, subtitle = subtitle, actions = actions) {
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 14.dp, bottom = 8.dp).testTag(testTag),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

// ---------------------------------------------------------------- matn ko‘rinishlari

/** Miqdor birligi bilan: "1 250,5 ml". */
fun qtyText(base: Long, unit: MeasureUnit): String = Qty.formatWithUnit(base, unit)

/** Miqdor birligisiz (jadval kataklari uchun): "1 250,5". */
fun qtyNumber(base: Long, unit: MeasureUnit): String = Qty.format(base, unit)

/** O‘lchov turlari bo‘yicha jamilar, qo‘shilmaydi: "5,5 l · 200 g"; bo‘sh bo‘lsa "—". */
fun totalsText(totals: Map<Dimension, Long>): String = if (totals.isEmpty()) "—" else Qty.formatTotals(totals)

/** Sana: "17.10.2026"; yo‘q bo‘lsa "—". */
fun dateText(date: LocalDate?): String = date?.let { UzDates.format(it) } ?: "—"

private val DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** Sana va vaqt: "17.10.2026 14:05"; yo‘q bo‘lsa "—". */
fun dateTimeText(time: LocalDateTime?): String = time?.format(DATE_TIME) ?: "—"

/** «Qolgan kunlar»: "45", "Bugun tugaydi", "12 kun o‘tgan", "—". */
fun daysText(days: Long?): String = daysLeftLabel(days)

/** Qolgan kunlar jumla bilan: "307 kun qoldi", "bugun tugaydi", "12 kun o‘tgan"; muddat yo‘q — null. */
fun daysPhrase(days: Long?): String? = when {
    days == null -> null
    days > 0 -> "$days kun qoldi"
    days == 0L -> "bugun tugaydi"
    else -> "${-days} kun o‘tgan"
}

/** Yaroqlilik holatining ranglari va matni (Theme.kt dagi StatusStyles). */
val ExpiryState.style: StatusStyle get() = statusStyle(name)

/** Holat belgisi: rang VA matn ("Muddati o‘tgan" — qalin). */
@Composable
fun ExpiryChip(state: ExpiryState, modifier: Modifier = Modifier, text: String = state.label) {
    StatusChip(state.style, modifier, text)
}

/** Yaroqlilik muddati maydoni ostidagi izoh: "Holati: 1–3 oy qoldi (47 kun qoldi)"; sana yo‘q — null. */
fun expiryHelpText(expiresOn: LocalDate?, today: LocalDate): String? {
    expiresOn ?: return null
    return "Holati: ${expiryState(expiresOn, today).label} (${daysPhrase(daysLeft(expiresOn, today))})"
}

/** Partiyaning qisqa nomi (tanlash ro‘yxatlari, sarlavhalar): "Etil spirti 96% · ES-001 · 01.09.2027". */
fun batchLabel(name: String, series: String, expiresOn: LocalDate?): String =
    listOf(name, series.ifBlank { "seriyasiz" }, expiresOn?.let { UzDates.format(it) } ?: "muddatsiz").joinToString(" · ")

/** Xabarlar uchun: "«Etil spirti 96%», seriya ES-001" (seriya bo‘lmasa — faqat nom). */
fun batchQuote(name: String, series: String): String = "«$name»" + if (series.isNotBlank()) ", seriya $series" else ""

/** Jadval qatori partiyasining qisqa nomi. */
fun BatchRow.label(): String = batchLabel(name, series, expiresOn)

/** «Bo‘lim / saqlash joyi»: "Gistologiya / 1-shkaf", bittasi bo‘lsa — o‘zi, ikkalasi yo‘q — "—". */
fun placeText(departmentName: String?, storageLocation: String): String =
    listOfNotNull(departmentName?.takeIf { it.isNotBlank() }, storageLocation.takeIf { it.isNotBlank() })
        .joinToString(" / ").ifEmpty { "—" }

// ---------------------------------------------------------------- kichik ko‘rinishlar

/** Nom va qiymat (oyna sarlavhalari, kartochkalar): kichik kulrang nom ustida qiymat. */
@Composable
fun InfoPair(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = AppColors.Text) {
    Column(modifier) {
        Text(label, fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted)
        Text(value, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Belgilash katakchasi va matni (filtr panelidagi «Arxivdagilarni ko‘rsatish» kabi). [topPadding] — yonidagi maydonlarga tekislash. */
@Composable
fun CheckField(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topPadding: Dp = 20.dp,
    testTag: String? = null,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .padding(top = topPadding)
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }
            .padding(horizontal = 6.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) AppColors.Primary else AppColors.Disabled
        Box(
            Modifier.size(18.dp).clip(shape).background(if (checked) color else AppColors.Surface)
                .border(1.5.dp, if (checked) color else AppColors.FieldBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = if (enabled) AppColors.Text else AppColors.Disabled, maxLines = 1)
    }
}

/** Kichik tugmalar guruhi (bittasi tanlanadi): masalan, «Qoldiq | Berilgan» yoki «l | kg | dona». */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    testTag: String = "segment",
) {
    val shape = RoundedCornerShape(6.dp)
    Row(modifier.clip(shape).border(1.dp, AppColors.Border, shape)) {
        options.forEachIndexed { index, option ->
            val active = option == selected
            if (index > 0) Box(Modifier.width(1.dp).height(26.dp).background(AppColors.Border))
            Box(
                Modifier
                    .height(26.dp)
                    .background(if (active) AppColors.Primary else AppColors.Surface)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(horizontal = 10.dp)
                    .testTag("$testTag-$index"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Color.White else AppColors.Text,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Holatlar bo‘yicha ko‘p tanlovli filtr (Muddati o‘tgan, Qizil, Sariq, Yashil, Muddatsiz): hech biri tanlanmagan —
 * hammasi. [counts] berilsa har birining yonida son. Test belgisi: `state-chip-<HOLAT>`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpiryStateFilter(
    selected: Set<ExpiryState>,
    onChange: (Set<ExpiryState>) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<ExpiryState, Int>? = null,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EXPIRY_ORDER.forEach { state ->
            val active = state in selected
            val style = state.style
            val shape = RoundedCornerShape(15.dp)
            Row(
                Modifier
                    .height(30.dp)
                    .clip(shape)
                    .background(if (active) style.background else AppColors.Surface)
                    .border(if (active) 1.5.dp else 1.dp, if (active) style.accent else AppColors.Border, shape)
                    .clickable(role = Role.Checkbox) { onChange(if (active) selected - state else selected + state) }
                    .padding(horizontal = 10.dp)
                    .testTag("state-chip-${state.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    Icon(AppIcons.Check, contentDescription = null, tint = style.foreground, modifier = Modifier.size(14.dp))
                } else {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(style.accent))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    state.label,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) style.foreground else AppColors.Text,
                    maxLines = 1,
                )
                counts?.let {
                    Spacer(Modifier.width(5.dp))
                    Text((it[state] ?: 0).toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) style.foreground else AppColors.Muted)
                }
            }
        }
    }
}

/** Holatlar jiddiylik tartibida (filtr va izohlar uchun). */
val EXPIRY_ORDER: List<ExpiryState> = listOf(ExpiryState.EXPIRED, ExpiryState.RED, ExpiryState.YELLOW, ExpiryState.GREEN, ExpiryState.UNKNOWN)

/**
 * Tanlangan qator amallari (jadval ustida): chapda tanlangan narsa, o‘ngda tugmalar (jadvaldagi «Amallar» bilan bir xil).
 * Keng jadvalda «Amallar» ustuni ko‘rinmay qolsa ham amallar shu yerda. [title] null — [hint] ko‘rsatiladi.
 * Tugmalar test belgisi: `<testTag>-<tartib raqami>`.
 */
@Composable
fun SelectionActionBar(
    title: String?,
    actions: List<RowAction>,
    hint: String,
    modifier: Modifier = Modifier,
    testTag: String = "selection-bar",
) {
    Row(
        modifier.fillMaxWidth().height(34.dp).testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (title == null) {
            Icon(AppIcons.Info, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(hint, fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@Row
        }
        Text("Tanlangan:", fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions.forEachIndexed { index, action ->
            val color = if (action.danger) AppColors.Error else AppColors.Primary
            TextButton(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = Modifier.height(32.dp).testTag("$testTag-$index"),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = AppColors.Disabled),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                if (action.icon != null) {
                    Icon(action.icon, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(action.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- bo‘lim tanlash

private sealed class CommonDepartmentChoice {
    data object None : CommonDepartmentChoice()
    data object AddNew : CommonDepartmentChoice()
    data class Existing(val place: Place) : CommonDepartmentChoice()
}

/**
 * Bo‘lim tanlash (faol bo‘limlar + hozir tanlangani, nofaol bo‘lsa ham). [allowNone] — «Tanlanmagan» bandi,
 * [allowAdd] — «+ Yangi bo‘lim qo‘shish…» (nomini so‘rab, Directory.addDepartment orqali qo‘shadi va tanlaydi).
 * [includeInactive] — filtrlar uchun: nofaol bo‘limlar ham ro‘yxatda («(nofaol)» belgisi bilan), chunki ularning
 * partiyalari va tarixi qoladi; shakllarda (biriktirish, berish) — faqat faollari.
 */
@Composable
fun DepartmentSelect(
    state: AppState,
    value: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Bo‘lim",
    placeholder: String = "Tanlanmagan",
    allowNone: Boolean = true,
    allowAdd: Boolean = true,
    error: String? = null,
    enabled: Boolean = true,
    testTag: String = "department",
    includeInactive: Boolean = false,
) {
    val all = remember(state.refreshTick) { state.load(emptyList()) { state.services.directory.departments(includeInactive = true) } }
    var adding by remember { mutableStateOf(false) }
    val options = buildList {
        if (allowNone) add(CommonDepartmentChoice.None)
        all.filter { includeInactive || it.active || it.id == value }.forEach { add(CommonDepartmentChoice.Existing(it)) }
        if (allowAdd) add(CommonDepartmentChoice.AddNew)
    }
    val current = all.firstOrNull { it.id == value }?.let { CommonDepartmentChoice.Existing(it) }
    SelectField(
        value = current,
        options = options,
        onSelect = { choice ->
            when (choice) {
                CommonDepartmentChoice.None -> onChange(null)
                CommonDepartmentChoice.AddNew -> adding = true
                is CommonDepartmentChoice.Existing -> onChange(choice.place.id)
            }
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        optionLabel = { choice ->
            when (choice) {
                CommonDepartmentChoice.None -> "— $placeholder —"
                CommonDepartmentChoice.AddNew -> "+ Yangi bo‘lim qo‘shish…"
                is CommonDepartmentChoice.Existing -> choice.place.name + if (choice.place.active) "" else " (nofaol)"
            }
        },
        error = error,
        enabled = enabled,
        testTag = testTag,
    )
    if (adding) {
        NameDialog(
            title = "Yangi bo‘lim",
            label = "Bo‘lim nomi",
            busy = state.busy,
            onDismiss = { adding = false },
            onConfirm = { name, form ->
                state.run({ state.services.directory.addDepartment(name) }, onError = { e ->
                    form(e.fields["name"] ?: e.message)
                    true
                }) { place ->
                    adding = false
                    onChange(place.id)
                    state.success("«${place.name}» bo‘limi qo‘shildi.")
                }
            },
        )
    }
}

/** Bitta nom so‘raydigan oyna (yangi bo‘lim, tuman …): [onConfirm] ga nom va xatoni ko‘rsatish funksiyasi beriladi. */
@Composable
fun NameDialog(
    title: String,
    label: String,
    onConfirm: (name: String, showError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    confirmLabel: String = "Qo‘shish",
    busy: Boolean = false,
) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val submit = {
        if (name.isBlank()) error = "$label kiritilishi shart." else onConfirm(name.trim()) { error = it }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            AppTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = label,
                required = true,
                error = error,
                onEnter = submit,
                focusRequester = focus,
                modifier = Modifier.fillMaxWidth(),
                testTag = "name-dialog-field",
            )
        },
        confirmButton = { AppButton(confirmLabel, onClick = submit, enabled = !busy && name.isNotBlank()) },
        dismissButton = { AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY, enabled = !busy) },
        modifier = Modifier.width(420.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = AppColors.Surface,
    )
}

// ---------------------------------------------------------------- tasdiq va sabab oynalari

private sealed class CommonConfirmRequest {
    class Simple(
        val title: String,
        val message: String,
        val confirmLabel: String,
        val dismissLabel: String,
        val danger: Boolean,
        val onConfirm: () -> Unit,
    ) : CommonConfirmRequest()

    class Reason(
        val title: String,
        val message: String?,
        val label: String,
        val placeholder: String,
        val confirmLabel: String,
        val danger: Boolean,
        val onConfirm: (String) -> Unit,
    ) : CommonConfirmRequest()
}

/**
 * Sahifadagi tasdiq/sabab oynalari: `confirm.ask(...) { … }` yoki `confirm.askReason(...) { reason -> … }`, sahifa
 * oxirida `ConfirmHost(confirm, state.busy)`. Tasdiqlanganda oyna avval yopiladi, so‘ng amal chaqiriladi.
 */
@Stable
class ConfirmState {
    private var request: CommonConfirmRequest? by mutableStateOf(null)

    val isOpen: Boolean get() = request != null

    fun ask(
        title: String,
        message: String,
        confirmLabel: String = "Tasdiqlash",
        danger: Boolean = false,
        dismissLabel: String = "Bekor qilish",
        onConfirm: () -> Unit,
    ) {
        request = CommonConfirmRequest.Simple(title, message, confirmLabel, dismissLabel, danger, onConfirm)
    }

    fun askReason(
        title: String,
        message: String? = null,
        confirmLabel: String = "Tasdiqlash",
        danger: Boolean = false,
        label: String = "Sabab",
        placeholder: String = "Sababini yozing",
        onConfirm: (String) -> Unit,
    ) {
        request = CommonConfirmRequest.Reason(title, message, label, placeholder, confirmLabel, danger, onConfirm)
    }

    fun close() {
        request = null
    }

    @Composable
    internal fun Render(busy: Boolean) {
        when (val r = request) {
            null -> Unit
            is CommonConfirmRequest.Simple -> ConfirmDialog(
                title = r.title,
                message = r.message,
                onConfirm = {
                    close()
                    r.onConfirm()
                },
                onDismiss = { close() },
                confirmLabel = r.confirmLabel,
                dismissLabel = r.dismissLabel,
                danger = r.danger,
                busy = busy,
            )
            is CommonConfirmRequest.Reason -> key(r) {
                ReasonDialog(
                    title = r.title,
                    message = r.message,
                    label = r.label,
                    placeholder = r.placeholder,
                    confirmLabel = r.confirmLabel,
                    danger = r.danger,
                    busy = busy,
                    onConfirm = { reason ->
                        close()
                        r.onConfirm(reason)
                    },
                    onDismiss = { close() },
                )
            }
        }
    }
}

@Composable
fun rememberConfirmState(): ConfirmState = remember { ConfirmState() }

/** [ConfirmState] oynasini chizadi (sahifa mazmunining oxirida bir marta). */
@Composable
fun ConfirmHost(confirm: ConfirmState, busy: Boolean = false) {
    confirm.Render(busy)
}

// ---------------------------------------------------------------- saqlanmagan o‘zgarishlar

/**
 * Shaklning «toza» holatiga nisbatan o‘zgarganini kuzatadi. [snapshot] — shakl maydonlarining qiymati (equals bilan
 * solishtiriladi); null — shakl bo‘sh (saqlanadigan narsa yo‘q). Toza holat — sahifa ochilgandagi qiymat (topshiriq
 * bilan oldindan to‘ldirilgani ham) yoki oxirgi [markClean] dagisi.
 */
@Stable
class UnsavedChanges internal constructor(private val snapshot: () -> Any?) {
    private var clean: Any? = snapshot()

    /** Saqlangandan yoki «Tozalash» dan keyin: hozirgi holat toza deb olinadi. */
    fun markClean() {
        clean = snapshot()
    }

    /** Shaklda saqlanmagan narsa bor. */
    val isDirty: Boolean
        get() = snapshot().let { it != null && it != clean }
}

/**
 * Sahifa shakli uchun chiqish himoyasi: saqlanmagan o‘zgarish bo‘lsa, boshqa sahifaga o‘tishdan oldin App.kt
 * «Saqlanmagan o‘zgarishlar bor…» deb so‘raydi (AppState.navigate). Sahifa funksiyasining boshida, shakl holatidan
 * keyin bir marta chaqiriladi: `rememberLeaveGuard(state) { listOf(form.name, form.qty) }`.
 */
@Composable
fun rememberLeaveGuard(state: AppState, snapshot: () -> Any?): UnsavedChanges {
    val current by rememberUpdatedState(snapshot)
    val changes = remember { UnsavedChanges { current() } }
    DisposableEffect(state, changes) {
        val guard = { changes.isDirty }
        state.setLeaveGuard(guard)
        onDispose { state.clearLeaveGuard(guard) }
    }
    return changes
}

// ---------------------------------------------------------------- katta oyna

/**
 * Katta oyna (tahrirlash, tarix): sarlavha, ×, mazmun va pastda o‘ngda tugmalar. Balandligi oynaga sig‘adi; mazmunni
 * aylantirish kerak bo‘lsa uni chaqiruvchi o‘zi `verticalScroll` qiladi (jadval bo‘lsa — balandligini beradi).
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Dp = 760.dp,
    buttons: @Composable RowScope.() -> Unit = {},
    testTag: String = "dialog",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BoxWithConstraints {
            val maxH = (maxHeight - 32.dp).coerceAtLeast(240.dp)
            val maxW = (maxWidth - 32.dp).coerceAtLeast(320.dp)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Surface,
                shadowElevation = 10.dp,
                modifier = modifier.width(width.coerceAtMost(maxW)).heightIn(max = maxH).testTag(testTag),
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, lineHeight = 17.sp, color = AppColors.Muted)
                        }
                        IconAction(AppIcons.Close, "Yopish", onClick = onDismiss, tint = AppColors.Muted, testTag = "$testTag-close")
                    }
                    HorizontalDivider(color = AppColors.Divider)
                    Column(
                        Modifier.weight(1f, fill = false).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                    HorizontalDivider(color = AppColors.Divider)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = buttons,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- partiya tarixi

/**
 * Partiya harakatlari jurnali (Queries.ledger): sana, operatsiya, hujjat, kirim, chiqim va har biridan keyingi qoldiq.
 * Bekor qilingan operatsiya belgilanadi. «Operatsiyalar tarixida ochish» — HISTORY sahifasiga [PageIntent.HistoryOf].
 */
@Composable
fun BatchHistoryDialog(state: AppState, batchId: Long, onDismiss: () -> Unit) {
    val tick = state.refreshTick
    val row = remember(tick, batchId) {
        state.load(null) {
            val reagentId = state.services.reagents.batch(batchId)?.reagentId
            reagentId?.let { id ->
                state.services.queries.batchRows(BatchFilter(reagentId = id, includeArchived = true), state.today()).firstOrNull { it.batchId == batchId }
            }
        }
    }
    val ledger = remember(tick, batchId) { state.load(emptyList()) { state.services.queries.ledger(batchId) } }
    val unit = row?.unit ?: ledger.firstOrNull()?.unit ?: MeasureUnit.L
    val columns = remember(unit) { ledgerColumns(unit) }
    val layout = rememberAppTableLayout(state, "batch-ledger", columns)
    AppDialog(
        title = "Partiya tarixi",
        subtitle = row?.label(),
        onDismiss = onDismiss,
        width = 1000.dp,
        testTag = "history-dialog",
        buttons = {
            AppButton(
                "Operatsiyalar tarixida ochish",
                onClick = {
                    onDismiss()
                    state.navigate(Page.HISTORY, PageIntent.HistoryOf(batchId = batchId, reagentId = row?.reagentId))
                },
                kind = ButtonKind.TEXT,
                icon = AppIcons.History,
            )
            AppButton("Yopish", onClick = onDismiss)
        },
    ) {
        if (row != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Holati", fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted)
                    Spacer(Modifier.height(2.dp))
                    ExpiryChip(row.state)
                }
                InfoPair("Yaroqlilik muddati", dateText(row.expiresOn) + (daysPhrase(row.daysLeft)?.let { " ($it)" } ?: ""))
                InfoPair("Bo‘lim / saqlash joyi", placeText(row.departmentName, row.storageLocation))
                InfoPair("Kelgan", qtyText(row.receivedQty, row.unit))
                InfoPair("Berilgan", qtyText(row.issuedQty, row.unit))
                if (row.writtenOffQty > 0) InfoPair("Hisobdan chiqarilgan", qtyText(row.writtenOffQty, row.unit))
                InfoPair("Joriy qoldiq", qtyText(row.remainder, row.unit), valueColor = AppColors.Primary)
            }
        }
        DataTable(
            rows = ledger,
            columns = columns,
            rowKey = { it.movementId },
            layout = layout,
            modifier = Modifier.fillMaxWidth().height((44 + 34 * ledger.size.coerceIn(3, 10) + 14).dp),
            rowBackground = { if (it.operationCancelled || it.kind == MovementKind.REVERSAL) AppColors.SurfaceAlt else null },
            emptyText = "Bu partiyada harakat yo‘q",
            testTag = "ledger",
        )
    }
}

private fun ledgerColumns(unit: MeasureUnit): List<TableColumn<LedgerRow>> = listOf(
    textColumn("date", "Sana", 100.dp, ColumnAlign.END, sortKey = { it.movedOn }) { dateText(it.movedOn) },
    TableColumn(
        id = "operation", title = "Operatsiya", width = 280.dp, text = { it.description },
        cell = { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.description,
                    fontSize = 13.sp,
                    color = if (row.operationCancelled) AppColors.Muted else AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.operationCancelled) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip("bekor qilingan", AppColors.Background, AppColors.Muted, dot = null)
                }
            }
        },
    ),
    textColumn("doc", "Hujjat", 90.dp) { it.docNo.ifBlank { "—" } },
    textColumn("in", "Kirim, ${unit.code}", 100.dp, ColumnAlign.END, sortKey = { if (it.qty > 0) it.qty else null }) {
        if (it.qty > 0) "+" + Qty.format(it.qty, it.unit) else ""
    },
    textColumn("out", "Chiqim, ${unit.code}", 100.dp, ColumnAlign.END, sortKey = { if (it.qty < 0) -it.qty else null }) {
        if (it.qty < 0) "−" + Qty.format(-it.qty, it.unit) else ""
    },
    textColumn("balance", "Qoldiq, ${unit.code}", 110.dp, ColumnAlign.END, sortKey = { it.balance }) { Qty.format(it.balance, it.unit) },
    textColumn("created", "Yozilgan vaqt", 142.dp, ColumnAlign.END, sortKey = { it.createdAt }) { dateTimeText(it.createdAt) },
)

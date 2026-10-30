package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.report.ExcelExport
import uz.reaktivombor.desktop.report.PdfExport
import uz.reaktivombor.desktop.report.ReportBuilder
import uz.reaktivombor.desktop.report.ReportFiles
import uz.reaktivombor.desktop.report.ReportFilter
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.service.PlaceKind
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.theme.AppColors
import java.time.LocalDate

// «Bo‘limlar bo‘yicha hisob» va «Tumanlarga berilgan reaktivlar» sahifalarining umumiy qismlari: chapdagi ro‘yxat
// (qo‘shish, nomini o‘zgartirish, faol/nofaol), jamlar kartasi, varaqlar va hisobotni Excel/PDF ga saqlash.

/** Xabarlardagi so‘zlar: «bo‘lim / bo‘limi / Bo‘limlar» yoki «tuman / tumani / Tumanlar». */
internal class PlacesWords(val one: String, val title: String, val possessive: String, val plural: String)

internal val PlaceKind.words: PlacesWords
    get() = when (this) {
        PlaceKind.DEPARTMENT -> PlacesWords("bo‘lim", "Bo‘lim", "bo‘limi", "Bo‘limlar")
        PlaceKind.DISTRICT -> PlacesWords("tuman", "Tuman", "tumani", "Tumanlar")
    }

/** «nofaol» belgisi (ro‘yxat va sarlavhada). */
@Composable
internal fun PlacesInactiveChip() {
    StatusChip("nofaol", AppColors.Background, AppColors.Muted, dot = null)
}

/**
 * Chapdagi bo‘limlar / tumanlar ro‘yxati: faol va nofaollar (belgilangan), «Qo‘shish», tanlanganining «Nomini
 * o‘zgartirish» va «Nofaol qilish / Faollashtirish» (tasdiq bilan). [allLabel] berilsa — eng tepada «hammasi» bandi
 * (tanlansa [onSelect] ga null). Test belgilari: `<testTag>-add`, `-all`, `-item-<id>`, `-rename`, `-toggle`.
 */
@Composable
internal fun PlacesListPanel(
    state: AppState,
    kind: PlaceKind,
    places: List<Place>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    allLabel: String? = null,
    allSecondary: String? = null,
    secondary: (Place) -> String? = { null },
) {
    val w = kind.words
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Place?>(null) }
    val confirm = rememberConfirmState()
    val selected = places.firstOrNull { it.id == selectedId }
    val activeCount = places.count { it.active }
    val inactiveCount = places.size - activeCount

    fun toggle(place: Place) {
        if (place.active) {
            val effect = when (kind) {
                PlaceKind.DEPARTMENT -> "unga yangi berish va yangi partiya biriktirish mumkin bo‘lmaydi. Bo‘limdagi partiyalar,"
                PlaceKind.DISTRICT -> "unga yangi berish mumkin bo‘lmaydi. Berilgan reaktivlar,"
            }
            confirm.ask(
                title = "${w.title}ni nofaol qilish",
                message = "«${place.name}» ${w.possessive} nofaol qilinadi: $effect tarix va hisobotlar saqlanadi. " +
                    "Keyin qayta faollashtirish mumkin.",
                confirmLabel = "Nofaol qilish",
            ) {
                state.run({ state.services.directory.setActive(kind, place.id, false) }) {
                    state.success("«${place.name}» ${w.possessive} nofaol qilindi.")
                }
            }
        } else {
            confirm.ask(
                title = "${w.title}ni faollashtirish",
                message = "«${place.name}» ${w.possessive} yana faol bo‘ladi: unga berish" +
                    (if (kind == PlaceKind.DEPARTMENT) " va partiya biriktirish" else "") + " mumkin bo‘ladi.",
                confirmLabel = "Faollashtirish",
            ) {
                state.run({ state.services.directory.setActive(kind, place.id, true) }) {
                    state.success("«${place.name}» ${w.possessive} faollashtirildi.")
                }
            }
        }
    }

    Surface(modifier, shape = RoundedCornerShape(10.dp), color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Divider)) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(w.plural, style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                    Text(
                        if (inactiveCount > 0) "$activeCount faol, $inactiveCount nofaol" else "$activeCount ta faol",
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = AppColors.Muted,
                    )
                }
                AppButton(
                    "Qo‘shish",
                    onClick = { adding = true },
                    kind = ButtonKind.TEXT,
                    icon = AppIcons.Add,
                    enabled = !state.busy,
                    modifier = Modifier.testTag("$testTag-add"),
                )
            }
            HorizontalDivider(color = AppColors.Divider)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val list = rememberLazyListState()
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                    if (allLabel != null) {
                        item(key = "all") {
                            PlacesListItem(allLabel, allSecondary, active = true, selected = selectedId == null, tag = "$testTag-all") { onSelect(null) }
                        }
                    }
                    items(places, key = { it.id }) { place ->
                        PlacesListItem(place.name, secondary(place), place.active, place.id == selectedId, "$testTag-item-${place.id}") {
                            onSelect(place.id)
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
            }
            HorizontalDivider(color = AppColors.Divider)
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                if (selected == null) {
                    Text(
                        "${w.title}ni tanlang — nomini o‘zgartirish va nofaol qilish shu yerda.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = AppColors.Muted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else {
                    AppButton(
                        "Nomini o‘zgartirish",
                        onClick = { renaming = selected },
                        kind = ButtonKind.TEXT,
                        icon = AppIcons.Edit,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("$testTag-rename"),
                    )
                    AppButton(
                        if (selected.active) "Nofaol qilish" else "Faollashtirish",
                        onClick = { toggle(selected) },
                        kind = ButtonKind.TEXT,
                        icon = if (selected.active) AppIcons.Archive else AppIcons.Undo,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("$testTag-toggle"),
                    )
                }
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "Yangi ${w.one}",
            label = "${w.title} nomi",
            busy = state.busy,
            onDismiss = { adding = false },
            onConfirm = { name, showError ->
                val existing = places.firstOrNull { nameKey(it.name) == nameKey(name) }
                if (existing != null && existing.active) {
                    showError("«${existing.name}» nomli ${w.one} allaqachon bor.")
                } else {
                    state.run({ state.services.directory.add(kind, name) }, onError = { e ->
                        showError(e.fields["name"] ?: e.message)
                        true
                    }) { place ->
                        adding = false
                        onSelect(place.id)
                        state.success(
                            if (existing != null) "«${place.name}» ${w.possessive} qayta faollashtirildi." else "«${place.name}» ${w.possessive} qo‘shildi.",
                        )
                    }
                }
            },
        )
    }
    renaming?.let { place ->
        NameDialog(
            title = "${w.title} nomini o‘zgartirish",
            label = "${w.title} nomi",
            initial = place.name,
            confirmLabel = "Saqlash",
            busy = state.busy,
            onDismiss = { renaming = null },
            onConfirm = { name, showError ->
                state.run({ state.services.directory.rename(kind, place.id, name) }, onError = { e ->
                    showError(e.fields["name"] ?: e.message)
                    true
                }) { renamed ->
                    renaming = null
                    if (renamed.name != place.name) {
                        state.success("${w.title} nomi o‘zgartirildi: «${place.name}» → «${renamed.name}».")
                    }
                }
            },
        )
    }
    ConfirmHost(confirm, state.busy)
}

@Composable
private fun PlacesListItem(name: String, secondary: String?, active: Boolean, selected: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 1.dp)
            .clip(shape)
            .background(if (selected) AppColors.PrimarySoft else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(26.dp).background(if (selected) AppColors.Primary else Color.Transparent))
        Column(Modifier.weight(1f).padding(start = 9.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)) {
            Text(
                name,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    selected -> AppColors.Navy
                    active -> AppColors.Text
                    else -> AppColors.Muted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) {
                Text(secondary, fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!active) {
            PlacesInactiveChip()
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ---------------------------------------------------------------- kartalar va varaqlar

/**
 * Jadvaldagi miqdor katagi (qalin): doim bitta qatorda — raqam minglar bo‘shlig‘ida bo‘linib, qolgani yashirinib
 * qolmasin («125 000,25» → «125» emas). Ustunga sig‘masa «…» bilan qisqaradi. Umumiy zaxiralar sahifasi ham ishlatadi.
 */
@Composable
internal fun PlacesQtyText(
    text: String,
    color: Color = AppColors.Text,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Text(text, fontSize = fontSize, fontWeight = fontWeight, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/** O‘lchov turlari bo‘yicha jamlar — har bir tur alohida qator ("14 l", "12 g"); turlar hech qachon qo‘shilmaydi. */
internal fun placesTotalsLines(totals: Map<Dimension, Long>): List<String> =
    Dimension.entries.filter { totals.containsKey(it) }.map { Qty.formatAuto(totals.getValue(it), it) }

/** (o‘lchov turi, miqdor) juftlaridan — tur bo‘yicha yig‘indi. */
internal fun <T> placesTotals(items: List<T>, dimension: (T) -> Dimension, qty: (T) -> Long): Map<Dimension, Long> =
    items.groupBy(dimension).mapValues { (_, list) -> list.sumOf(qty) }

/** Davr matni: "01.10.2026 — 29.10.2026", "01.10.2026 dan", "29.10.2026 gacha" yoki "butun davr". */
internal fun placesPeriodText(from: LocalDate?, to: LocalDate?): String = when {
    from != null && to != null -> "${dateText(from)} — ${dateText(to)}"
    from != null -> "${dateText(from)} dan"
    to != null -> "${dateText(to)} gacha"
    else -> "butun davr"
}

/**
 * Jamlar kartasi: nomi, har bir o‘lchov turi alohida qatorda (bo‘sh — "—") va izoh. [onClick] bo‘lsa bosiladi,
 * [selected] — ko‘k ramka.
 */
@Composable
internal fun PlacesTotalsCard(
    label: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    hint: String? = null,
    hintColor: Color = AppColors.Muted,
    valueColor: Color = AppColors.Text,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .let { if (testTag != null) it.testTag(testTag) else it },
        shape = shape,
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Divider),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, fontSize = 12.5.sp, lineHeight = 16.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            (lines.ifEmpty { listOf("—") }).forEach { line ->
                Text(line, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hint != null) {
                Text(hint, fontSize = 11.5.sp, lineHeight = 15.sp, color = hintColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Varaqlar qatori (son bilan); tanlangani ko‘k chiziq bilan. Test belgisi: `<testTag>-<tartib raqami>`. */
@Composable
internal fun PlacesTabs(tabs: List<Pair<String, Int?>>, selected: Int, onSelect: (Int) -> Unit, testTag: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index, (title, count) ->
                val active = index == selected
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .testTag("$testTag-$index"),
                ) {
                    Row(Modifier.height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            fontSize = 13.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) AppColors.Primary else AppColors.Muted,
                            maxLines = 1,
                        )
                        if (count != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(9.dp))
                                    .background(if (active) AppColors.PrimarySoft else AppColors.Background)
                                    .padding(horizontal = 7.dp, vertical = 1.dp),
                            ) {
                                Text(count.toString(), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (active) AppColors.Primary else AppColors.Muted)
                            }
                        }
                    }
                    Box(Modifier.height(2.dp).fillMaxWidth().background(if (active) AppColors.Primary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = AppColors.Border)
    }
}

// ---------------------------------------------------------------- hisobot

/** «Hisobot:» Excel va PDF tugmalari (sahifa sarlavhasining o‘ng tomoni). */
@Composable
internal fun PlacesReportButtons(state: AppState, enabled: Boolean, testTag: String, onExport: (extension: String) -> Unit) {
    Text("Hisobot:", fontSize = 13.sp, color = AppColors.Muted)
    AppButton(
        "Excel",
        onClick = { onExport("xlsx") },
        kind = ButtonKind.SECONDARY,
        icon = AppIcons.Excel,
        enabled = enabled && !state.busy,
        modifier = Modifier.testTag("$testTag-excel"),
    )
    AppButton(
        "PDF",
        onClick = { onExport("pdf") },
        kind = ButtonKind.SECONDARY,
        icon = AppIcons.Document,
        enabled = enabled && !state.busy,
        modifier = Modifier.testTag("$testTag-pdf"),
    )
}

/**
 * Hisobotni tuzadi, saqlash oynasini ochadi va faylni yozadi ([extension]: "xlsx" yoki "pdf"). Bekor qilinsa — hech
 * narsa. Natija: «Hisobot saqlandi» xabari va «Papkani ochish».
 */
internal fun placesExport(state: AppState, kind: ReportKind, filter: ReportFilter, extension: String) {
    val data = state.load(null) { ReportBuilder(state.services).build(kind, filter, state.today()) } ?: return
    val format = if (extension == "pdf") "PDF" else "Excel"
    val target = state.dialogs.save("Hisobotni saqlash ($format)", ReportFiles.suggestedFileName(data, extension), extension) ?: return
    state.run(
        { if (extension == "pdf") PdfExport.write(data, target) else ExcelExport.write(data, target) },
        refresh = false,
    ) { saved ->
        state.success("Hisobot saqlandi: ${saved.fileName}", actionLabel = "Papkani ochish") {
            (saved.toAbsolutePath().parent)?.let { state.dialogs.openFolder(it) }
        }
    }
}

/** Sahifa ichidagi bo‘lim/tuman sarlavhasi: nomi, «nofaol» belgisi va qisqa izoh. */
@Composable
internal fun PlacesDetailHeader(title: String, inactive: Boolean, description: String, testTag: String) {
    Row(Modifier.fillMaxWidth().testTag(testTag), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (inactive) {
            Spacer(Modifier.width(8.dp))
            PlacesInactiveChip()
        }
        Spacer(Modifier.width(12.dp))
        Text(description, fontSize = 12.5.sp, lineHeight = 16.sp, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

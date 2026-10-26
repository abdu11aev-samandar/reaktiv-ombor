package uz.reaktivombor.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.ExpiryStatus
import java.awt.Toolkit
import java.time.LocalDate

/** Excel’ga o‘xshash ranglar. */
object SheetColors {
    val Background = Color(0xFFFFFFFF)
    val Grid = Color(0xFFDADCE0)
    val HeaderBg = Color(0xFFF3F4F6)
    val HeaderText = Color(0xFF374151)
    val HeaderSelected = Color(0xFFDCE6FB)
    val Accent = Color(0xFF1D4ED8)
    val Text = Color(0xFF1F2937)
    val Muted = Color(0xFF6B7280)
    val ErrorBg = Color(0xFFFDECEC)
    val Error = Color(0xFFB91C1C)
    val DraftRow = Color(0xFFFFFBEA)
    /** Muddati 1 oydan kam qolgan (yoki o‘tgan) reaktivning butun qatori. */
    val RedRow = Color(0xFFFDE2E2)

    val pill = mapOf(
        ExpiryStatus.GREEN to (Color(0xFF15803D) to Color.White),
        ExpiryStatus.YELLOW to (Color(0xFFFACC15) to Color(0xFF422006)),
        ExpiryStatus.RED to (Color(0xFFDC2626) to Color.White),
    )
    val hint = mapOf(
        ExpiryStatus.GREEN to Color(0xFF15803D),
        ExpiryStatus.YELLOW to Color(0xFF8A5A00),
        ExpiryStatus.RED to Color(0xFFB91C1C),
    )
}

const val ROW_HEADER_WIDTH = 52
const val ROW_HEIGHT = 30

/** Ko‘rinib turgan bitta qator (null — oxiridagi “yangi qator”). */
data class GridLine(val row: SheetRow?, val check: RowCheck?)

/**
 * Oxirgi bosish tanlagan katak. Ikki marta bosish shu katakni ochadi — kursor ostidagisini emas: birinchi bosish
 * ro‘yxatni aylantirgan yoki qizil ogohlantirish yo‘qolib qatorlar siljigan bo‘lishi mumkin (tizim bosishlarni faqat
 * ekrandagi joyi bo‘yicha sanaydi).
 */
class LastPress {
    var cell: Pair<Long?, Col>? = null
    var at = 0L
}

/** Windows’dagi ikki marta bosish oralig‘i (sozlamalardan), bo‘lmasa 500 ms. */
private val MULTI_CLICK_MS: Long = runCatching {
    Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int
}.getOrNull()?.toLong() ?: 500L

/**
 * Jadval. Tanlangan katakda doim bitta (ko‘rinmas) matn maydoni turadi: shunda Excel’dagi kabi
 * katakni tanlab yozishni boshlash mumkin — birinchi harf eski qiymatni almashtiradi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetGrid(
    lines: List<GridLine>,
    today: LocalDate,
    selIndex: Int,
    selCol: Col,
    editing: Boolean,
    editValue: TextFieldValue,
    sort: Pair<Col, Boolean>?,
    listState: LazyListState,
    cellFocus: FocusRequester,
    // Qidiruv maydonida yozilayotganda yangi tanlangan katak fokusni o‘ziga tortib olmasin.
    autoFocus: Boolean,
    // Haqiqatan tanlangan katakni qaytaradi (u bosilgan joydan farq qilishi mumkin — masalan, yangi yaratilgan qator).
    onSelect: (rowId: Long?, col: Col, startEditing: Boolean) -> Pair<Long?, Col>,
    onEditValue: (TextFieldValue) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    onFocusLost: () -> Unit,
    // Tahrirlanayotgan katak ichiga bosildi (Excel: kiritish rejimidan tahrirlash rejimiga).
    onEditPress: () -> Unit,
    onSort: (Col) -> Unit,
    onRowMenu: (rowId: Long) -> Unit,
    // «Bo‘lim» katagida ochiladigan ro‘yxat.
    departments: List<String>,
    // Ochilgan ro‘yxatdan tanlandi (birlik yoki bo‘lim, yangi bo‘lim ham) — qiymat aynan shunday yoziladi.
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontal = rememberScrollState()
    val lastPress = remember { LastPress() }
    val density = LocalDensity.current

    // Tanlangan katak doim to‘liq ko‘rinsin (tor oynada ham, klaviatura bilan yurilganda ham).
    LaunchedEffect(selCol, selIndex, horizontal.viewportSize) {
        if (selCol == Col.NAME) return@LaunchedEffect
        val view = horizontal.viewportSize - with(density) { SCROLLBAR_GAP.dp.roundToPx() }
        if (view <= 0) return@LaunchedEffect
        val before = SCROLLED_COLS.takeWhile { it != selCol }.sumOf { it.widthDp }
        val left = with(density) { before.dp.roundToPx() }
        val right = with(density) { (before + selCol.widthDp).dp.roundToPx() }
        when {
            left < horizontal.value -> horizontal.scrollTo(left)
            right > horizontal.value + view -> horizontal.scrollTo(right - view)
        }
    }

    Box(modifier.background(SheetColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderRow(selCol, sort, onSort, lastPress, horizontal) { (rowId, cellCol) -> onSelect(rowId, cellCol, true) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // Pastdagi gorizontal aylantirish chizig‘i oxirgi qatorni yopmasin.
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                itemsIndexed(lines, key = { _, line -> line.row?.id ?: -1L }) { index, line ->
                    val rowColor = when {
                        line.row == null || line.row.isBlank -> SheetColors.Background
                        line.check?.status(today) == ExpiryStatus.RED -> SheetColors.RedRow
                        line.check?.complete == false -> SheetColors.DraftRow
                        else -> SheetColors.Background
                    }
                    val cell: @Composable (Col) -> Unit = { col ->
                        val selected = index == selIndex && col == selCol
                        DataCell(
                            line = line,
                            col = col,
                            today = today,
                            selected = selected,
                            editing = selected && editing,
                            editValue = editValue,
                            cellFocus = cellFocus,
                            autoFocus = autoFocus,
                            lastPress = lastPress,
                            onTap = { onSelect(line.row?.id, col, false) },
                            onDoubleTap = { (rowId, cellCol) -> onSelect(rowId, cellCol, true) },
                            onEditPress = onEditPress,
                            onEditValue = onEditValue,
                            onKey = onKey,
                            onFocusLost = onFocusLost,
                            departments = departments,
                            onPick = onPick,
                        )
                    }
                    Row(Modifier.height(ROW_HEIGHT.dp).background(rowColor)) {
                        RowHeaderCell(
                            label = if (line.row == null) "+" else "${index + 1}",
                            selected = index == selIndex,
                            onClick = { onSelect(line.row?.id, Col.NAME, false) },
                            onSecondaryClick = { line.row?.let { onRowMenu(it.id) } },
                        )
                        // Reaktiv nomi chapda qotib turadi: o‘ngdagi ustunlarga aylantirilganda ham qaysi reaktiv ekani ko‘rinadi.
                        cell(Col.NAME)
                        Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                            SCROLLED_COLS.forEach { cell(it) }
                            Spacer(Modifier.width(SCROLLBAR_GAP.dp))
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = (HEADER_HEIGHT).dp),
        )
        // Oyna tor bo‘lsa (kichik ekran, 125% masshtab) o‘ngdagi ustunlarga yetish uchun.
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontal),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = (ROW_HEADER_WIDTH + Col.NAME.widthDp).dp),
        )
    }
}

/**
 * Birlik yoki bo‘lim katagida ochiladigan ro‘yxat. Tepadagi band — Enter yozadigan qiymat: bo‘limda aynan mosi,
 * ruscha/inglizcha nomi yoki boshi mos birinchisi; mos bo‘lim bo‘lmasa — «+ yangi bo‘lim» ([newFirst]).
 * [newDepartment] — yozilgan nomni aynan shunday yangi bo‘lim qilish (masalan, «Mor», «Morfologiya» bor bo‘lsa ham).
 */
data class ChoiceMenu(val items: List<String>, val newDepartment: String?, val newFirst: Boolean)

fun choiceMenu(col: Col, typedRaw: String, current: String?, departments: List<String>): ChoiceMenu {
    val choices = if (col == Col.DEPARTMENT) departments else col.choices
    val typedText = cleanName(typedRaw)
    val typed = searchKey(typedText)
    // F2 / ikki marta bosilganda (matn — hozirgi qiymat) hammasi; yozilganda — mos keladiganlari.
    if (typed.isEmpty() || typed == current?.let { searchKey(cleanName(it)) }) return ChoiceMenu(choices, null, newFirst = false)
    val matching = choices.filter { searchKey(cleanName(it)).startsWith(typed) }
    if (col != Col.DEPARTMENT) return ChoiceMenu(matching.sortedBy { searchKey(it) != typed }.ifEmpty { choices }, null, newFirst = false)

    val enter = matchDepartment(typedText, departments, prefix = true)
    val created = matchDepartment(typedText, departments).takeIf { name -> isDepartmentName(name) && departments.none { sameDepartment(it, name) } }
    return when {
        enter in departments -> ChoiceMenu(listOf(enter) + matching.filter { it != enter }, created, newFirst = false)
        created != null -> ChoiceMenu(emptyList(), created, newFirst = true)
        // Harfsiz qiymat (son) bo‘lim bo‘lmaydi — tanlash uchun butun ro‘yxat.
        else -> ChoiceMenu(choices, null, newFirst = false)
    }
}

/** Nomdan keyingi ustunlar — ular gorizontal aylanadi (nom va qator raqami joyida qoladi). */
private val SCROLLED_COLS = Col.entries.filter { it != Col.NAME }

/** O‘ngdagi vertikal aylantirish chizig‘i sonlarni yopmasligi uchun bo‘sh joy. */
private const val SCROLLBAR_GAP = 12

private const val HEADER_HEIGHT = 52

@Composable
private fun HeaderRow(
    selCol: Col,
    sort: Pair<Col, Boolean>?,
    onSort: (Col) -> Unit,
    lastPress: LastPress,
    horizontal: ScrollState,
    onDoubleTap: (Pair<Long?, Col>) -> Unit,
) {
    val doubleTap by rememberUpdatedState(onDoubleTap)
    Row(Modifier.height(HEADER_HEIGHT.dp)) {
        Box(
            Modifier.width(ROW_HEADER_WIDTH.dp).fillMaxHeight().background(SheetColors.HeaderBg).gridLines(),
        )
        HeaderCell(Col.NAME, selCol, sort, onSort, lastPress) { doubleTap(it) }
        Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
            SCROLLED_COLS.forEach { col -> HeaderCell(col, selCol, sort, onSort, lastPress) { doubleTap(it) } }
            Spacer(Modifier.width(SCROLLBAR_GAP.dp).fillMaxHeight().background(SheetColors.HeaderBg))
        }
    }
}

@Composable
private fun HeaderCell(
    col: Col,
    selCol: Col,
    sort: Pair<Col, Boolean>?,
    onSort: (Col) -> Unit,
    lastPress: LastPress,
    onDoubleTap: (Pair<Long?, Col>) -> Unit,
) {
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val arrow = when {
        sort?.first != col -> ""
        sort.second -> "  ▲"
        else -> "  ▼"
    }
    Column(
        Modifier
            .width(col.widthDp.dp)
            .fillMaxHeight()
            .background(if (col == selCol) SheetColors.HeaderSelected else SheetColors.HeaderBg)
            .gridLines()
            // Katakni ikki marta bosishning ikkinchi bosishi sarlavhaga tushdi (birinchi bosish qizil ogohlantirishni
            // chiqarib, jadval pastga siljidi) — saralamaymiz, birinchi bosish tanlagan katakni ochamiz.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press) continue
                        val now = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                        val previous = lastPress.cell?.takeIf { now - lastPress.at <= MULTI_CLICK_MS }
                        lastPress.cell = null
                        val clicks = event.awtEventOrNull?.clickCount ?: 1
                        if (clicks >= 2 && previous != null && !event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            doubleTap(previous)
                        }
                    }
                }
            }
            .pointerInput(col) { detectTapGestures(onTap = { onSort(col) }) }
            .semantics { contentDescription = "${col.title} ustuni bo‘yicha saralash" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Saralash belgisi harf qatorida: uzun sarlavha ikki qatorga sig‘may qisqartirilganda ham ko‘rinadi.
        Text(
            col.letter.toString() + arrow,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = if (arrow.isEmpty()) SheetColors.Muted else SheetColors.HeaderText,
        )
        // Uzun sarlavhalar ("Ishlab chiqarilgan vaqti") ikki qatorga bo‘linadi.
        Text(
            col.title,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = SheetColors.HeaderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun RowHeaderCell(label: String, selected: Boolean, onClick: () -> Unit, onSecondaryClick: () -> Unit) {
    Box(
        Modifier
            .width(ROW_HEADER_WIDTH.dp)
            .fillMaxHeight()
            .background(if (selected) SheetColors.HeaderSelected else SheetColors.HeaderBg)
            .gridLines()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            if (event.buttons.isSecondaryPressed) onSecondaryClick() else onClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, color = SheetColors.HeaderText)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataCell(
    line: GridLine,
    col: Col,
    today: LocalDate,
    selected: Boolean,
    editing: Boolean,
    editValue: TextFieldValue,
    cellFocus: FocusRequester,
    autoFocus: Boolean,
    lastPress: LastPress,
    onTap: () -> Pair<Long?, Col>,
    onDoubleTap: (Pair<Long?, Col>) -> Unit,
    onEditPress: () -> Unit,
    onEditValue: (TextFieldValue) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    onFocusLost: () -> Unit,
    departments: List<String>,
    onPick: (String) -> Unit,
) {
    val row = line.row
    val check = line.check
    val status = check?.status(today)
    val error = if (row != null && !row.isBlank) check?.error(col) else null

    val pill = if (col == Col.NAME && status != null) SheetColors.pill.getValue(status) else null
    val textColor = pill?.second ?: SheetColors.Text

    val display: String
    var displayColor = textColor
    var italic = false
    var bold = col == Col.NAME && status != null
    when {
        row == null -> {
            display = if (col == Col.NAME) "Yangi reaktiv yozing…" else ""
            displayColor = SheetColors.Muted
            italic = true
        }
        col == Col.DAYS_LEFT -> {
            display = cellText(row, col, check, today)
            if (status != null) {
                displayColor = SheetColors.hint.getValue(status)
                bold = status == ExpiryStatus.RED
            }
        }
        col == Col.REMAINDER -> {
            display = cellText(row, col, check, today)
            bold = display.isNotEmpty()
        }
        else -> display = cellText(row, col, check, today)
    }
    val alignEnd = col.alignEnd

    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val editPress by rememberUpdatedState(onEditPress)
    val editingHere by rememberUpdatedState(editing)
    val here by rememberUpdatedState(row?.id to col)

    val cell: @Composable () -> Unit = {
        Box(
            Modifier
                .width(col.widthDp.dp)
                .fillMaxHeight()
                .testTag("cell-${row?.id ?: "new"}-${col.letter}")
                // Xato fonini chiziqlar va qizil burchakdan oldin chizamiz — ular ko‘rinib tursin.
                .then(if (error != null) Modifier.background(SheetColors.ErrorBg) else Modifier)
                .gridLines()
                .then(if (error != null) Modifier.errorCorner() else Modifier)
                // Katak tugma bosilgan zahoti tanlanadi (Excel’dagi kabi): darrov yozilgan matn shu katakka tushadi,
                // avvalgisiga emas. Tahrirlanayotgan katak ichidagi bosishlar matn maydonining o‘ziga qoladi.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Press) continue
                            if (editingHere) {
                                editPress()
                                continue
                            }
                            val now = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                            val previous = lastPress.cell?.takeIf { now - lastPress.at <= MULTI_CLICK_MS }
                            val clicks = event.awtEventOrNull?.clickCount
                                ?: if (previous == here && now - lastPress.at <= viewConfiguration.doubleTapTimeoutMillis) 2 else 1
                            if (clicks >= 2 && previous != null && !event.buttons.isSecondaryPressed) {
                                lastPress.cell = null
                                doubleTap(previous)
                            } else {
                                // Bosish tanlagan katak (bosilgan joy emas): “yangi qator”ga yozib turib bosilsa, bu yangi qator.
                                lastPress.cell = tap()
                                lastPress.at = now
                            }
                        }
                    }
                },
            contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            if (pill != null) {
                Box(Modifier.fillMaxSize().padding(3.dp).background(pill.first, RoundedCornerShape(4.dp)))
            }
            if (!editing) {
                Text(
                    display,
                    color = displayColor,
                    fontSize = 13.sp,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 9.dp),
                )
            }
            if (selected) {
                CellEditor(
                    editing = editing,
                    value = editValue,
                    readOnly = !col.editable,
                    alignEnd = alignEnd,
                    focus = cellFocus,
                    autoFocus = autoFocus,
                    onValue = onEditValue,
                    onKey = onKey,
                    onFocusLost = onFocusLost,
                )
                // Ramka. Tahrirlanmayotganda yashirin matn maydonini sichqonchadan to‘sadi: ikki marta bosish
                // tahrirni ochadi, maydon esa bo‘sh qiymat bilan uning matnini o‘chirib yubormaydi.
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.dp, SheetColors.Accent)
                        .then(if (editing) Modifier else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }),
                )
                if (col.kind == Col.Kind.CHOICE && editing) {
                    val menu = choiceMenu(col, editValue.text, row?.get(col), departments)
                    if (menu.items.isNotEmpty() || menu.newDepartment != null) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = {},
                            offset = DpOffset(0.dp, 2.dp),
                            properties = PopupProperties(focusable = false),
                        ) {
                            val addItem: @Composable () -> Unit = {
                                menu.newDepartment?.let { name ->
                                    DropdownMenuItem(
                                        text = { Text("+ «$name» — yangi bo‘lim", color = SheetColors.Accent) },
                                        onClick = { onPick(name) },
                                        modifier = Modifier.testTag("new-department"),
                                    )
                                }
                            }
                            if (menu.newFirst) addItem()
                            menu.items.forEach { choice ->
                                DropdownMenuItem(text = { Text(choice) }, onClick = { onPick(choice) })
                            }
                            if (!menu.newFirst) addItem()
                        }
                    }
                }
            }
        }
    }

    if (error != null) {
        TooltipArea(
            tooltip = {
                Surface(color = Color(0xFF1F2937), shape = RoundedCornerShape(4.dp), shadowElevation = 4.dp) {
                    Text(error, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
            },
            delayMillis = 300,
            tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(8.dp, 16.dp)),
        ) { cell() }
    } else {
        cell()
    }
}

/**
 * Tanlangan katakning matn maydoni. Ko‘rish rejimida matn ko‘rinmaydi va to‘liq belgilangan bo‘ladi —
 * yozilgan birinchi harf qiymatni almashtiradi (Excel’dagi kabi). Tahrirlash rejimida oddiy maydon.
 */
@Composable
private fun CellEditor(
    editing: Boolean,
    value: TextFieldValue,
    readOnly: Boolean,
    alignEnd: Boolean,
    focus: FocusRequester,
    autoFocus: Boolean,
    onValue: (TextFieldValue) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    onFocusLost: () -> Unit,
) {
    // Yangi maydon birinchi marta “fokus yo‘q” deb xabar beradi — bu tahrirni yopib qo‘ymasin.
    val hadFocus = remember { booleanArrayOf(false) }
    val hiddenSelection = remember { TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent) }
    val visibleSelection = remember { TextSelectionColors(handleColor = SheetColors.Accent, backgroundColor = Color(0x551D4ED8)) }
    CompositionLocalProvider(LocalTextSelectionColors provides if (editing) visibleSelection else hiddenSelection) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            readOnly = readOnly,
            singleLine = true,
            cursorBrush = SolidColor(if (editing) SheetColors.Text else Color.Transparent),
            textStyle = TextStyle(
                color = if (editing) SheetColors.Text else Color.Transparent,
                fontSize = 13.sp,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp)
                .background(if (editing) Color.White else Color.Transparent)
                .focusRequester(focus)
                .testTag("cell-editor")
                .onFocusChanged {
                    if (it.isFocused) {
                        hadFocus[0] = true
                    } else if (hadFocus[0]) {
                        hadFocus[0] = false
                        onFocusLost()
                    }
                }
                .onPreviewKeyEvent(onKey),
        )
    }
    val mayTakeFocus by rememberUpdatedState(autoFocus)
    LaunchedEffect(Unit) { if (mayTakeFocus) runCatching { focus.requestFocus() } }
}

/** Katakning o‘ng va past chizig‘i (Excel to‘ri). */
fun Modifier.gridLines(): Modifier = drawBehind {
    val stroke = 1f
    drawLine(SheetColors.Grid, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
    drawLine(SheetColors.Grid, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}

/** Xatoli katakning burchagidagi qizil uchburchak (Excel’dagi izoh belgisi kabi). */
private fun Modifier.errorCorner(): Modifier = drawBehind {
    val s = 7.dp.toPx()
    val path = Path().apply {
        moveTo(size.width - s, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, s)
        close()
    }
    drawPath(path, SheetColors.Error)
}

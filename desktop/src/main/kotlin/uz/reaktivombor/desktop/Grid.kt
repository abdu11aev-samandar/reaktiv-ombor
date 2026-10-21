package uz.reaktivombor.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import uz.reaktivombor.domain.daysUntil
import uz.reaktivombor.domain.expiryHint
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
    onSelect: (rowId: Long?, col: Col, startEditing: Boolean) -> Unit,
    onEditValue: (TextFieldValue) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    onFocusLost: () -> Unit,
    onSort: (Col) -> Unit,
    onRowMenu: (rowId: Long) -> Unit,
    onPickUnit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontal = rememberScrollState()
    val totalWidth = ROW_HEADER_WIDTH + Col.entries.sumOf { it.widthDp }

    Box(modifier.background(SheetColors.Background)) {
        Column(Modifier.fillMaxSize().horizontalScroll(horizontal)) {
            HeaderRow(selCol, sort, onSort)
            LazyColumn(state = listState, modifier = Modifier.width(totalWidth.dp).weight(1f)) {
                itemsIndexed(lines, key = { _, line -> line.row?.id ?: -1L }) { index, line ->
                    Row(Modifier.height(ROW_HEIGHT.dp).background(if (line.row != null && line.check?.complete == false) SheetColors.DraftRow else SheetColors.Background)) {
                        RowHeaderCell(
                            label = if (line.row == null) "+" else "${index + 1}",
                            selected = index == selIndex,
                            onClick = { onSelect(line.row?.id, Col.NAME, false) },
                            onSecondaryClick = { line.row?.let { onRowMenu(it.id) } },
                        )
                        Col.entries.forEach { col ->
                            val selected = index == selIndex && col == selCol
                            DataCell(
                                line = line,
                                col = col,
                                today = today,
                                selected = selected,
                                editing = selected && editing,
                                editValue = editValue,
                                cellFocus = cellFocus,
                                onTap = { onSelect(line.row?.id, col, false) },
                                onDoubleTap = { onSelect(line.row?.id, col, true) },
                                onEditValue = onEditValue,
                                onKey = onKey,
                                onFocusLost = onFocusLost,
                                onPickUnit = onPickUnit,
                            )
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = (HEADER_HEIGHT).dp),
        )
    }
}

private const val HEADER_HEIGHT = 44

@Composable
private fun HeaderRow(selCol: Col, sort: Pair<Col, Boolean>?, onSort: (Col) -> Unit) {
    Row(Modifier.height(HEADER_HEIGHT.dp)) {
        Box(
            Modifier.width(ROW_HEADER_WIDTH.dp).fillMaxHeight().background(SheetColors.HeaderBg).gridLines(),
        )
        Col.entries.forEach { col ->
            val selected = col == selCol
            val arrow = when {
                sort?.first != col -> ""
                sort.second -> "  ▲"
                else -> "  ▼"
            }
            Column(
                Modifier
                    .width(col.widthDp.dp)
                    .fillMaxHeight()
                    .background(if (selected) SheetColors.HeaderSelected else SheetColors.HeaderBg)
                    .gridLines()
                    .pointerInput(col) { detectTapGestures(onTap = { onSort(col) }) }
                    .semantics { contentDescription = "${col.title} ustuni bo‘yicha saralash" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(col.letter.toString(), fontSize = 10.sp, lineHeight = 12.sp, color = SheetColors.Muted)
                Text(
                    col.title + arrow,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SheetColors.HeaderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
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
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onEditValue: (TextFieldValue) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    onFocusLost: () -> Unit,
    onPickUnit: (String) -> Unit,
) {
    val row = line.row
    val check = line.check
    val status = check?.status(today)
    val error = if (row != null && !row.isBlank) check?.error(col) else null

    val (fill, textColor) = when {
        col == Col.NAME && status != null -> SheetColors.pill.getValue(status)
        error != null -> SheetColors.ErrorBg to SheetColors.Text
        else -> Color.Transparent to SheetColors.Text
    }

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
        col == Col.STATUS -> {
            val values = check?.values
            if (values != null && status != null) {
                display = expiryHint(daysUntil(values.expiresAt, today))
                displayColor = SheetColors.hint.getValue(status)
                bold = status == ExpiryStatus.RED
            } else if (row.isBlank) {
                display = ""
            } else {
                display = "Tugallanmagan: ${check?.errors?.size ?: 0} ta maydon"
                displayColor = SheetColors.Muted
                italic = true
            }
        }
        col == Col.GIVEN || col == Col.STOCK -> display = displayAmount(row[col])
        col == Col.RECEIVED || col == Col.EXPIRES -> display = displayDate(row[col])
        else -> display = row[col]
    }
    val alignEnd = col == Col.GIVEN || col == Col.STOCK || col == Col.RECEIVED || col == Col.EXPIRES

    val cell: @Composable () -> Unit = {
        Box(
            Modifier
                .width(col.widthDp.dp)
                .fillMaxHeight()
                .gridLines()
                .then(if (error != null) Modifier.errorCorner() else Modifier)
                .pointerInput(onTap, onDoubleTap) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() }) },
            contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            if (fill != Color.Transparent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(if (col == Col.NAME && status != null) 3.dp else 0.dp)
                        .background(fill, RoundedCornerShape(if (col == Col.NAME && status != null) 4.dp else 0.dp)),
                )
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
                    onValue = onEditValue,
                    onKey = onKey,
                    onFocusLost = onFocusLost,
                )
                Box(Modifier.matchParentSize().border(2.dp, SheetColors.Accent))
                if (col == Col.UNIT && editing) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = {},
                        offset = DpOffset(0.dp, 2.dp),
                        properties = PopupProperties(focusable = false),
                    ) {
                        Amounts.UNITS.filter { it.startsWith(editValue.text.trim().lowercase()) || editValue.text.isBlank() }
                            .ifEmpty { Amounts.UNITS }
                            .forEach { unit ->
                                DropdownMenuItem(text = { Text(unit) }, onClick = { onPickUnit(unit) })
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
    onValue: (TextFieldValue) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    onFocusLost: () -> Unit,
) {
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
                .onFocusChanged { if (!it.isFocused) onFocusLost() }
                .onPreviewKeyEvent(onKey),
        )
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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

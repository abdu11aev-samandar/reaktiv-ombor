package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.reaktivombor.desktop.searchKey
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.theme.AppColors
import java.awt.Cursor
import java.awt.event.MouseEvent

enum class ColumnAlign { START, CENTER, END }

/** Ustun turi: oddiy ma’lumot, tartib raqami (№ — ko‘rinib turgan tartibda) yoki «Amallar» tugmalari. */
enum class ColumnKind { DATA, ROW_NUMBER, ACTIONS }

/** Qatordagi amal (Tahrirlash, Kirim, Berish …). [icon] bo‘lsa — belgili tugma (izohi [label]), aks holda matnli. */
@Immutable
class RowAction(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Jadval ustuni. Katak [cell] (istalgan composable) yoki [text] (matn) bilan chiziladi. Saralash [sortKey] bo‘yicha,
 * u berilmasa [text] bo‘yicha (katta-kichik harf va tutuq belgisi farqlanmaydi, sonlar son kabi: "Naftol-2" < "Naftol-10").
 * Bo‘sh qiymatlar (null, "") ikkala yo‘nalishda ham oxirida.
 */
@Immutable
class TableColumn<T>(
    val id: String,
    val title: String,
    val width: Dp = 140.dp,
    val minWidth: Dp = 48.dp,
    val align: ColumnAlign = ColumnAlign.START,
    val sortable: Boolean = true,
    val hideable: Boolean = true,
    val visibleByDefault: Boolean = true,
    val text: ((T) -> String)? = null,
    val sortKey: ((T) -> Comparable<*>?)? = null,
    val kind: ColumnKind = ColumnKind.DATA,
    val actions: ((T) -> List<RowAction>)? = null,
    val maxInlineActions: Int = 4,
    val cell: (@Composable (T) -> Unit)? = null,
) {
    /** Saralasa bo‘ladimi: ma’lumot ustuni va kaliti (sortKey yoki text) bor. */
    val isSortable: Boolean get() = sortable && kind == ColumnKind.DATA && (sortKey != null || text != null)

    internal val spec: ColumnSpec get() = ColumnSpec(id, width, minWidth, isSortable, hideable, visibleByDefault)
}

/** Matnli ustun (eng ko‘p ishlatiladigani). */
fun <T> textColumn(
    id: String,
    title: String,
    width: Dp = 140.dp,
    align: ColumnAlign = ColumnAlign.START,
    minWidth: Dp = 48.dp,
    sortKey: ((T) -> Comparable<*>?)? = null,
    visibleByDefault: Boolean = true,
    hideable: Boolean = true,
    text: (T) -> String,
): TableColumn<T> = TableColumn(
    id = id, title = title, width = width, minWidth = minWidth, align = align, text = text, sortKey = sortKey,
    visibleByDefault = visibleByDefault, hideable = hideable,
)

/** «№» — qatorning ko‘rinib turgan tartibdagi raqami (saralash va guruhlashdan keyin). */
fun <T> rowNumberColumn(id: String = "no", title: String = "№", width: Dp = 48.dp): TableColumn<T> =
    TableColumn(id = id, title = title, width = width, minWidth = 36.dp, align = ColumnAlign.END, sortable = false, kind = ColumnKind.ROW_NUMBER)

/** «Amallar» ustuni: ko‘pi bilan [maxInline] ta tugma, qolganlari «⋮» menyusida. */
fun <T> actionsColumn(
    id: String = "actions",
    title: String = "Amallar",
    width: Dp = 150.dp,
    maxInline: Int = 4,
    actions: (T) -> List<RowAction>,
): TableColumn<T> = TableColumn(
    id = id, title = title, width = width, minWidth = 60.dp, sortable = false,
    kind = ColumnKind.ACTIONS, actions = actions, maxInlineActions = maxInline,
)

/** Guruh: kalit, qatorlari (ko‘rinish tartibida) va ochiq/yopiqligi — sarlavha chizish uchun. */
@Immutable
data class GroupInfo<T>(val key: String, val rows: List<T>, val expanded: Boolean)

/**
 * Qatorlarni [key] bo‘yicha guruhlash. Bir guruh qatorlari doim yonma-yon; guruhlar tartibi — saralangan ro‘yxatda
 * birinchi qatori kelgan joy. [header] — guruh sarlavhasi mazmuni (ochish/yopish belgisini jadval o‘zi qo‘yadi).
 */
class TableGrouping<T>(
    val key: (T) -> String,
    val header: (@Composable (GroupInfo<T>) -> Unit)? = null,
)

/** Guruhlarning ochiq/yopiqligi. [expandAll] / [collapseAll] — «Hammasini ochish/yopish». */
@Stable
class GroupExpansion(defaultExpanded: Boolean = true) {
    private var baseExpanded by mutableStateOf(defaultExpanded)
    // Standart holatdan farq qiladigan guruhlar.
    private val exceptions = mutableStateMapOf<String, Unit>()

    fun isExpanded(key: String): Boolean = baseExpanded != (key in exceptions)

    fun toggle(key: String) {
        if (exceptions.remove(key) == null) exceptions[key] = Unit
    }

    fun setExpanded(key: String, expanded: Boolean) {
        if (isExpanded(key) != expanded) toggle(key)
    }

    fun expandAll() {
        baseExpanded = true
        exceptions.clear()
    }

    fun collapseAll() {
        baseExpanded = false
        exceptions.clear()
    }
}

@Composable
fun rememberGroupExpansion(defaultExpanded: Boolean = true): GroupExpansion = remember { GroupExpansion(defaultExpanded) }

/** Tanlangan qator kaliti (sahifa o‘qishi yoki o‘zgartirishi mumkin). */
@Stable
class TableSelection {
    var key: Any? by mutableStateOf(null)
}

@Composable
fun rememberTableSelection(): TableSelection = remember { TableSelection() }

/**
 * Umumiy jadval. Sarlavha doim ko‘rinadi; gorizontal aylantirish sarlavha va qatorlar uchun bitta, vertikal — LazyColumn.
 * Sarlavhani bosish — saralash (o‘sish → kamayish → yo‘q), chegarasini sudrash — kenglik (ikki marta bosish —
 * standart kenglik), sarlavhada o‘ng tugma yoki o‘ng burchakdagi belgi — ustunlar menyusi (ko‘rsatish/yashirish,
 * chapga/o‘ngga, «Standart holatga qaytarish»). Klaviatura: ↑/↓/Home/End — qator tanlash, Enter — [onRowDoubleClick].
 *
 * [rowBackground] — butun qator rangi (masalan, yaroqlilik holati: `statusStyle(row.state.name).background`).
 */
@Composable
fun <T> DataTable(
    rows: List<T>,
    columns: List<TableColumn<T>>,
    rowKey: (T) -> Any,
    layout: TableLayoutState,
    modifier: Modifier = Modifier,
    rowBackground: (T) -> Color? = { null },
    grouping: TableGrouping<T>? = null,
    expansion: GroupExpansion = rememberGroupExpansion(),
    selection: TableSelection = rememberTableSelection(),
    onRowClick: ((T) -> Unit)? = null,
    onRowDoubleClick: ((T) -> Unit)? = null,
    emptyText: String = "Ma’lumot topilmadi",
    emptyHint: String? = null,
    rowHeight: Dp = 34.dp,
    listState: LazyListState = rememberLazyListState(),
    testTag: String = "table",
) {
    val byId = columns.associateBy { it.id }
    val ordered = layout.order.mapNotNull { byId[it] }
    val visible = ordered.filter { layout.isVisible(it.id) }
    val sort = layout.sort?.takeIf { byId[it.columnId]?.isSortable == true }
    val currentColumns by rememberUpdatedState(byId)
    val currentKey by rememberUpdatedState(rowKey)
    val sorted = remember(rows, sort) { sortRows(rows, sort?.let { currentColumns[it.columnId] }, sort?.descending == true, currentKey) }
    // Guruhlarning ochiq/yopiqligi shu yerda o‘qiladi — o‘zgarsa jadval qayta tuziladi.
    val lines = buildLines(sorted, grouping, expansion)
    val horizontal = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val press = remember { PressTracker() }
    val click by rememberUpdatedState(onRowClick)
    val doubleClick by rememberUpdatedState(onRowDoubleClick)

    fun select(row: T) {
        selection.key = rowKey(row)
        click?.invoke(row)
    }

    val shape = RoundedCornerShape(8.dp)
    Column(modifier.clip(shape).background(AppColors.Surface).border(1.dp, AppColors.Border, shape).testTag(testTag)) {
        HeaderRow(visible, ordered, layout, horizontal, testTag)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focus)
                .focusable()
                .testTag("$testTag-body")
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val dataLines = lines.withIndex().filter { it.value is Line.Data<*> }
                    if (dataLines.isEmpty()) return@onKeyEvent false
                    val current = dataLines.indexOfFirst { (it.value as Line.Data<*>).key == selection.key }
                    val target = when (event.key) {
                        Key.DirectionDown -> (current + 1).coerceAtMost(dataLines.size - 1)
                        Key.DirectionUp -> (current - 1).coerceAtLeast(0)
                        Key.MoveHome -> 0
                        Key.MoveEnd -> dataLines.size - 1
                        Key.Enter, Key.NumPadEnter -> {
                            @Suppress("UNCHECKED_CAST")
                            val row = (dataLines.getOrNull(current)?.value as? Line.Data<T>)?.row ?: return@onKeyEvent false
                            doubleClick?.invoke(row)
                            return@onKeyEvent true
                        }
                        else -> return@onKeyEvent false
                    }
                    val (lineIndex, line) = dataLines[target]
                    @Suppress("UNCHECKED_CAST")
                    select((line as Line.Data<T>).row)
                    scope.launch {
                        val items = listState.layoutInfo.visibleItemsInfo
                        val first = items.firstOrNull()?.index ?: 0
                        val last = items.lastOrNull()?.index ?: 0
                        if (lineIndex <= first || lineIndex >= last) listState.scrollToItem((lineIndex - 2).coerceAtLeast(0))
                    }
                    true
                },
        ) {
            if (rows.isEmpty()) {
                EmptyState(emptyText, hint = emptyHint, modifier = Modifier.testTag("$testTag-empty"))
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(lines, key = { it.lazyKey }) { line ->
                        when (line) {
                            is Line.Group<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val group = line as Line.Group<T>
                                GroupHeaderRow(group, grouping?.header, rowHeight, testTag) { expansion.toggle(group.key) }
                            }
                            is Line.Data<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val data = line as Line.Data<T>
                                DataRow(
                                    data = data,
                                    columns = visible,
                                    layout = layout,
                                    horizontal = horizontal,
                                    height = rowHeight,
                                    background = rowBackground(data.row),
                                    selected = data.key == selection.key,
                                    press = press,
                                    tag = "$testTag-row-${data.key}",
                                    onPress = { isDouble ->
                                        runCatching { focus.requestFocus() }
                                        select(data.row)
                                        if (isDouble) doubleClick?.invoke(data.row)
                                    },
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp),
                )
            }
        }
        // Ustunlar ekranga sig‘masa — pastda gorizontal aylantirish chizig‘i.
        if (horizontal.maxValue > 0) {
            Box(Modifier.fillMaxWidth().background(AppColors.SurfaceAlt).padding(end = GutterWidth)) {
                HorizontalScrollbar(adapter = rememberScrollbarAdapter(horizontal), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
            }
        }
    }
}

/** O‘ngdagi bo‘sh joy: sarlavhada ustunlar menyusi belgisi, qatorlarda vertikal aylantirish chizig‘i. */
private val GutterWidth = 30.dp
private val HeaderHeight = 40.dp
private val ResizeCursor = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
private val HeaderLine = Color(0xFF2E4A72)

/** LazyColumn qatori: guruh sarlavhasi yoki ma’lumot qatori. */
private sealed class Line {
    abstract val lazyKey: Any

    class Group<T>(val key: String, val rows: List<T>, val expanded: Boolean) : Line() {
        override val lazyKey: Any get() = "group" to key
    }

    class Data<T>(val row: T, val key: Any, val number: Int) : Line() {
        override val lazyKey: Any get() = "row" to key
    }
}

private class KeyedRow<T>(val row: T, val key: Any)

/** Ikki marta bosishni aniqlash uchun oxirgi bosilgan qator. */
private class PressTracker {
    var key: Any? = null
    var at: Long = 0L
}

private fun <T> buildLines(sorted: List<KeyedRow<T>>, grouping: TableGrouping<T>?, expansion: GroupExpansion): List<Line> {
    if (grouping == null) return sorted.mapIndexed { i, r -> Line.Data(r.row, r.key, i + 1) }
    val groups = LinkedHashMap<String, MutableList<KeyedRow<T>>>()
    sorted.forEach { groups.getOrPut(grouping.key(it.row)) { mutableListOf() } += it }
    val lines = ArrayList<Line>(sorted.size + groups.size)
    var number = 0
    groups.forEach { (key, members) ->
        val expanded = expansion.isExpanded(key)
        lines += Line.Group(key, members.map { it.row }, expanded)
        // Raqamlar yopiq guruhlarda ham sanaladi — guruh yopilganda boshqa qatorlarning № o‘zgarmaydi.
        members.forEach { member ->
            number++
            if (expanded) lines += Line.Data(member.row, member.key, number)
        }
    }
    return lines
}

private fun <T> sortRows(rows: List<T>, column: TableColumn<T>?, descending: Boolean, rowKey: (T) -> Any): List<KeyedRow<T>> {
    val keyed = rows.map { KeyedRow(it, rowKey(it)) }
    val extract: (T) -> Any? = column?.sortKey ?: column?.text ?: return keyed
    val values = keyed.map { it to sortValue(extract(it.row)) }
    val (empty, filled) = values.partition { it.second == null }
    val comparator = Comparator<Pair<KeyedRow<T>, Any?>> { a, b -> compareSortValues(a.second!!, b.second!!) }
    return filled.sortedWith(if (descending) comparator.reversed() else comparator).map { it.first } + empty.map { it.first }
}

private fun sortValue(value: Any?): Any? = when (value) {
    null -> null
    is String -> value.trim().takeIf { it.isNotEmpty() }?.let(::searchKey)
    else -> value
}

/** Saralash taqqoslashi: matnlar “tabiiy” (ichidagi sonlar son kabi), sonlar son kabi, qolganlari Comparable. */
fun compareSortValues(a: Any, b: Any): Int = when {
    a is String && b is String -> naturalCompare(a, b)
    a is Long && b is Long -> a.compareTo(b)
    a is Int && b is Int -> a.compareTo(b)
    a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
    a is Comparable<*> && a::class == b::class -> {
        @Suppress("UNCHECKED_CAST")
        (a as Comparable<Any>).compareTo(b)
    }
    else -> naturalCompare(a.toString(), b.toString())
}

/** "naftol-2" < "naftol-10": raqamlar ketma-ketligi son sifatida taqqoslanadi. */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i].isDigit() && b[j].isDigit()) {
            val si = i
            while (i < a.length && a[i].isDigit()) i++
            val sj = j
            while (j < b.length && b[j].isDigit()) j++
            val na = a.substring(si, i).trimStart('0')
            val nb = b.substring(sj, j).trimStart('0')
            if (na.length != nb.length) return na.length.compareTo(nb.length)
            val c = na.compareTo(nb)
            if (c != 0) return c
        } else {
            val c = a[i].compareTo(b[j])
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i).compareTo(b.length - j)
}

@Composable
private fun <T> HeaderRow(
    visible: List<TableColumn<T>>,
    ordered: List<TableColumn<T>>,
    layout: TableLayoutState,
    horizontal: ScrollState,
    tag: String,
) {
    Row(Modifier.fillMaxWidth().height(HeaderHeight).background(AppColors.Navy)) {
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(horizontal)) {
            visible.forEach { column -> HeaderCell(column, layout, tag) }
        }
        ColumnsMenu(ordered, layout, tag)
    }
}

@Composable
private fun <T> HeaderCell(column: TableColumn<T>, layout: TableLayoutState, tag: String) {
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val sortable = column.isSortable
    val sort = layout.sort?.takeIf { it.columnId == column.id }
    Box(
        Modifier
            .width(layout.widthOf(column.id))
            .fillMaxHeight()
            .background(if (hovered && sortable) AppColors.NavyHover else AppColors.Navy)
            .hoverable(interaction)
            .pointerInput(column.id) {
                awaitPointerEventScope {
                    while (true) {
                        // Initial: o‘ng tugma saralamasin (clickable’gacha ushlab qolinadi).
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            menu = true
                        }
                    }
                }
            }
            .clickable(enabled = sortable, role = Role.Button, onClickLabel = "Saralash") { layout.toggleSort(column.id) }
            .testTag("$tag-th-${column.id}"),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = column.align.arrangement,
        ) {
            Text(
                column.title,
                color = Color.White,
                fontSize = 12.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = column.align.textAlign,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (sort != null) {
                Icon(
                    if (sort.descending) AppIcons.ChevronDown else AppIcons.ChevronUp,
                    contentDescription = if (sort.descending) "Kamayish bo‘yicha saralangan" else "O‘sish bo‘yicha saralangan",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        ResizeHandle(column.id, layout, tag, Modifier.align(Alignment.CenterEnd))
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ColumnMenuItems(column, layout, tag) { menu = false }
        }
    }
}

/** Ustun chegarasi: sudrab kenglikni o‘zgartirish, ikki marta bosish — standart kenglik. */
@Composable
private fun ResizeHandle(columnId: String, layout: TableLayoutState, tag: String, modifier: Modifier) {
    val density = LocalDensity.current
    val lastTap = remember { longArrayOf(0L) }
    Box(
        modifier
            .width(8.dp)
            .fillMaxHeight()
            .pointerHoverIcon(ResizeCursor)
            .pointerInput(columnId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val start = layout.widthOf(columnId)
                    var dx = 0f
                    var moved = false
                    drag(down.id) { change ->
                        dx += change.positionChange().x
                        change.consume()
                        moved = true
                        layout.setWidth(columnId, start + with(density) { dx.toDp() }, persist = false)
                    }
                    if (moved) {
                        layout.persist()
                        lastTap[0] = 0L
                    } else {
                        val now = down.uptimeMillis
                        if (now - lastTap[0] <= viewConfiguration.doubleTapTimeoutMillis) {
                            layout.resetWidth(columnId)
                            lastTap[0] = 0L
                        } else {
                            lastTap[0] = now
                        }
                    }
                }
            }
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(HeaderLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            .testTag("$tag-resize-$columnId"),
    )
}

/** Sarlavhadagi o‘ng tugma menyusi (bitta ustun uchun). */
@Composable
private fun <T> ColumnMenuItems(column: TableColumn<T>, layout: TableLayoutState, tag: String, close: () -> Unit) {
    val sorted = layout.sort?.columnId == column.id
    if (column.isSortable) {
        MenuItem("O‘sish bo‘yicha saralash", AppIcons.ChevronUp) { layout.setSort(TableSort(column.id, false)); close() }
        MenuItem("Kamayish bo‘yicha saralash", AppIcons.ChevronDown) { layout.setSort(TableSort(column.id, true)); close() }
        if (sorted) MenuItem("Saralashni olib tashlash", AppIcons.Close) { layout.setSort(null); close() }
        HorizontalDivider(color = AppColors.Divider)
    }
    MenuItem("Chapga surish", AppIcons.ChevronLeft, enabled = layout.canMove(column.id, -1)) { layout.moveLeft(column.id) }
    MenuItem("O‘ngga surish", AppIcons.ChevronRight, enabled = layout.canMove(column.id, +1)) { layout.moveRight(column.id) }
    MenuItem("Ustunni yashirish", AppIcons.Close, enabled = layout.canHide(column.id), tag = "$tag-hide-${column.id}") {
        layout.setVisible(column.id, false)
        close()
    }
    MenuItem("Kenglikni tiklash", AppIcons.Refresh) { layout.resetWidth(column.id); close() }
    HorizontalDivider(color = AppColors.Divider)
    MenuItem("Standart holatga qaytarish", AppIcons.Refresh) { layout.reset(); close() }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector?, enabled: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 13.sp) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        modifier = Modifier.height(34.dp).let { if (tag != null) it.testTag(tag) else it },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

/** O‘ng burchakdagi «Ustunlar» menyusi: har bir ustun — ko‘rsatish/yashirish va chapga/o‘ngga; oxirida standart holat. */
@Composable
private fun <T> ColumnsMenu(ordered: List<TableColumn<T>>, layout: TableLayoutState, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.width(GutterWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
        IconAction(
            icon = AppIcons.Columns,
            label = "Ustunlar",
            onClick = { open = true },
            tint = Color.White,
            size = 26.dp,
            iconSize = 17.dp,
            testTag = "$tag-columns",
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(300.dp)) {
            Text(
                "Ustunlar",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            ordered.forEach { column ->
                val shown = layout.isVisible(column.id)
                val toggleable = !shown || layout.canHide(column.id)
                DropdownMenuItem(
                    text = { Text(column.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (shown) AppColors.Text else AppColors.Muted) },
                    leadingIcon = { CheckGlyph(shown, toggleable) },
                    trailingIcon = {
                        Row {
                            IconAction(
                                AppIcons.ChevronLeft, "Chapga surish",
                                onClick = { layout.moveLeft(column.id) },
                                enabled = shown && layout.canMove(column.id, -1),
                                size = 26.dp, iconSize = 18.dp, testTag = "$tag-col-left-${column.id}",
                            )
                            IconAction(
                                AppIcons.ChevronRight, "O‘ngga surish",
                                onClick = { layout.moveRight(column.id) },
                                enabled = shown && layout.canMove(column.id, +1),
                                size = 26.dp, iconSize = 18.dp, testTag = "$tag-col-right-${column.id}",
                            )
                        }
                    },
                    onClick = { if (toggleable) layout.setVisible(column.id, !shown) },
                    modifier = Modifier.height(34.dp).testTag("$tag-col-${column.id}"),
                    contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
                )
            }
            HorizontalDivider(color = AppColors.Divider)
            MenuItem("Standart holatga qaytarish", AppIcons.Refresh, tag = "$tag-col-reset") {
                layout.reset()
                open = false
            }
        }
    }
}

@Composable
private fun CheckGlyph(checked: Boolean, enabled: Boolean) {
    val color = if (enabled) AppColors.Primary else AppColors.Disabled
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(16.dp)
            .clip(shape)
            .background(if (checked) color else Color.Transparent)
            .border(1.5.dp, if (checked) color else AppColors.FieldBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun <T> GroupHeaderRow(
    group: Line.Group<T>,
    header: (@Composable (GroupInfo<T>) -> Unit)?,
    rowHeight: Dp,
    tag: String,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight + 4.dp)
            .background(AppColors.GroupHeader)
            .bottomLine(AppColors.Border)
            .clickable(role = Role.Button, onClickLabel = if (group.expanded) "Yopish" else "Ochish", onClick = onToggle)
            .padding(start = 6.dp, end = GutterWidth)
            .testTag("$tag-group-${group.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (group.expanded) AppIcons.ChevronDown else AppIcons.ChevronRight,
            contentDescription = if (group.expanded) "Guruhni yopish" else "Guruhni ochish",
            tint = AppColors.Navy,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            val info = GroupInfo(group.key, group.rows, group.expanded)
            if (header != null) {
                header(info)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.key, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("  ·  ${group.rows.size} ta", fontSize = 12.5.sp, color = AppColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun <T> DataRow(
    data: Line.Data<T>,
    columns: List<TableColumn<T>>,
    layout: TableLayoutState,
    horizontal: ScrollState,
    height: Dp,
    background: Color?,
    selected: Boolean,
    press: PressTracker,
    tag: String,
    onPress: (isDouble: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val onPressState by rememberUpdatedState(onPress)
    val key = data.key
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(background ?: AppColors.Surface)
            .background(
                when {
                    selected -> AppColors.RowSelected
                    hovered -> AppColors.RowHover
                    else -> Color.Transparent
                },
            )
            .bottomLine(AppColors.Divider)
            .drawWithContent {
                drawContent()
                if (selected) {
                    // Ustidan: ko‘k ramka va chapda qalin chiziq (katak chiziqlari va fon ustidan).
                    val line = 1.dp.toPx()
                    drawRect(
                        AppColors.Primary,
                        topLeft = Offset(line / 2, line / 2),
                        size = Size(size.width - line, size.height - line),
                        style = Stroke(line),
                    )
                    drawRect(AppColors.Primary, size = size.copy(width = 3.dp.toPx()))
                }
            }
            .hoverable(interaction)
            .pointerInput(key) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press || event.buttons.isSecondaryPressed) continue
                        val change = event.changes.firstOrNull() ?: continue
                        // Qator ichidagi tugma bosildi — u o‘zi ishlaydi.
                        if (change.isConsumed) continue
                        val now = change.uptimeMillis
                        val clicks = (event.nativeEvent as? MouseEvent)?.clickCount
                        val sameRow = press.key == key
                        val isDouble = sameRow && if (clicks != null) clicks >= 2 else now - press.at <= viewConfiguration.doubleTapTimeoutMillis
                        press.key = if (isDouble) null else key
                        press.at = now
                        onPressState(isDouble)
                    }
                }
            }
            .testTag(tag),
    ) {
        Row(Modifier.fillMaxSize().padding(end = GutterWidth).horizontalScroll(horizontal)) {
            columns.forEach { column ->
                Box(
                    Modifier
                        .width(layout.widthOf(column.id))
                        .fillMaxHeight()
                        .rightLine(AppColors.Divider)
                        .padding(horizontal = 8.dp),
                    contentAlignment = column.align.alignment,
                ) {
                    when (column.kind) {
                        ColumnKind.ROW_NUMBER -> Text(data.number.toString(), fontSize = 12.5.sp, color = AppColors.Muted, maxLines = 1)
                        ColumnKind.ACTIONS -> ActionsCell(column.actions?.invoke(data.row).orEmpty(), column.maxInlineActions)
                        ColumnKind.DATA -> {
                            val cell = column.cell
                            if (cell != null) {
                                cell(data.row)
                            } else {
                                Text(
                                    column.text?.invoke(data.row).orEmpty(),
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    color = AppColors.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = column.align.textAlign,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsCell(actions: List<RowAction>, maxInline: Int) {
    val inline = if (actions.size <= maxInline) actions else actions.take((maxInline - 1).coerceAtLeast(0))
    val overflow = actions.drop(inline.size)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        inline.forEach { action ->
            val tint = if (action.danger) AppColors.Error else AppColors.Primary
            if (action.icon != null) {
                IconAction(action.icon, action.label, onClick = action.onClick, tint = tint, enabled = action.enabled, size = 28.dp, iconSize = 17.dp)
            } else {
                Text(
                    action.label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (action.enabled) tint else AppColors.Disabled,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
                        .semantics { contentDescription = action.label }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        if (overflow.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconAction(AppIcons.More, "Boshqa amallar", onClick = { open = true }, tint = AppColors.Muted, size = 28.dp, iconSize = 18.dp)
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    overflow.forEach { action ->
                        val tint = if (action.danger) AppColors.Error else AppColors.Text
                        DropdownMenuItem(
                            text = { Text(action.label, fontSize = 13.sp, color = if (action.enabled) tint else AppColors.Disabled) },
                            leadingIcon = action.icon?.let { icon ->
                                { Icon(icon, contentDescription = null, tint = if (action.enabled) tint else AppColors.Disabled, modifier = Modifier.size(18.dp)) }
                            },
                            enabled = action.enabled,
                            onClick = {
                                open = false
                                action.onClick()
                            },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.bottomLine(color: Color): Modifier = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

private fun Modifier.rightLine(color: Color): Modifier = drawBehind {
    val x = size.width - 0.5.dp.toPx()
    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
}

private val ColumnAlign.alignment: Alignment
    get() = when (this) {
        ColumnAlign.START -> Alignment.CenterStart
        ColumnAlign.CENTER -> Alignment.Center
        ColumnAlign.END -> Alignment.CenterEnd
    }

private val ColumnAlign.arrangement: Arrangement.Horizontal
    get() = when (this) {
        ColumnAlign.START -> Arrangement.Start
        ColumnAlign.CENTER -> Arrangement.Center
        ColumnAlign.END -> Arrangement.End
    }

private val ColumnAlign.textAlign: TextAlign
    get() = when (this) {
        ColumnAlign.START -> TextAlign.Start
        ColumnAlign.CENTER -> TextAlign.Center
        ColumnAlign.END -> TextAlign.End
    }

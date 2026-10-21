package uz.reaktivombor.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.BackupException
import uz.reaktivombor.domain.ExpiryStatus
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Filter(val label: String, val status: ExpiryStatus?) {
    ALL("Hammasi", null),
    RED(ExpiryStatus.RED.label, ExpiryStatus.RED),
    YELLOW(ExpiryStatus.YELLOW.label, ExpiryStatus.YELLOW),
    GREEN(ExpiryStatus.GREEN.label, ExpiryStatus.GREEN),
    DRAFT("Tugallanmagan", null),
}

/** Fayl tanlash oynalari (testda soxtasi beriladi). */
interface FileDialogs {
    fun openJson(): Path?
    fun saveFile(title: String, defaultName: String): Path?
    fun openFolder(dir: Path)
}

/** Qidiruv: katta-kichik harf va o‘ / o' / o’ farqlanmaydi. */
fun searchKey(value: String): String = value.replace(Regex("['`’ʻʼ]"), "‘").lowercase()

private data class Notice(val text: String, val isError: Boolean = false)

private const val MAX_IMPORT_BYTES = 20L * 1024 * 1024

private val AppColors = lightColorScheme(
    primary = SheetColors.Accent,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFDCE6FB),
    onSecondaryContainer = Color(0xFF0B1F66),
    surface = Color.White,
    background = Color.White,
    error = SheetColors.Error,
)

@Composable
fun ReaktivlarApp(
    state: SheetState,
    store: SheetStore,
    dialogs: FileDialogs,
    startupWarning: String? = null,
    todayProvider: () -> LocalDate = LocalDate::now,
    autoSave: Boolean = true,
    // Testlarda o‘chiriladi: cheksiz taymer test kutishini to‘xtatib qo‘yadi.
    refreshToday: Boolean = true,
) {
    MaterialTheme(colorScheme = AppColors) {
        // Jadvalga mos ixcham qator balandligi (Material’ning standart 24sp emas).
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 13.sp, lineHeight = 18.sp)) {
            AppContent(state, store, dialogs, startupWarning, todayProvider, autoSave, refreshToday)
        }
    }
}

@Composable
private fun AppContent(
    state: SheetState,
    store: SheetStore,
    dialogs: FileDialogs,
    startupWarning: String?,
    todayProvider: () -> LocalDate,
    autoSave: Boolean,
    refreshToday: Boolean,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var today by remember { mutableStateOf(todayProvider()) }
    // Yarim tundan keyin ranglar o‘zi yangilansin.
    LaunchedEffect(refreshToday) {
        while (refreshToday) {
            delay(60_000)
            today = todayProvider()
        }
    }

    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var sort by remember { mutableStateOf<Pair<Col, Boolean>?>(null) }
    // Yangi qo‘shilgan qatorlar filtr/saralashdan qat’i nazar ko‘rinib turadi (yozayotganda “sakrab” ketmasin).
    var pinned by remember { mutableStateOf(setOf<Long>()) }
    // Tahrirlangandan keyin qatorlar tartibi “muzlaydi” (Excel’dagi kabi): qator filtrga mos kelmay qolsa yoki
    // saralash bo‘yicha o‘rni o‘zgarsa ham joyida turadi — Enter/Tab keyingi qatorga o‘tadi, begonasiga emas.
    // Filtr, qidiruv yoki saralash o‘zgarganda qayta hisoblanadi.
    var frozen by remember { mutableStateOf<List<Long>?>(null) }

    var selId by remember { mutableStateOf(state.rows.firstOrNull()?.id) }
    var selCol by remember { mutableStateOf(Col.NAME) }
    var editing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(TextFieldValue("")) }
    // Katakka to‘g‘ridan-to‘g‘ri yozila boshlangan (F2 yoki ikki marta bosish emas) — strelkalar katakdan chiqaradi.
    var typedOver by remember { mutableStateOf(false) }
    // Tab bilan qator bo‘ylab yurilgan bo‘lsa, Enter keyingi qatorning boshlangan ustuniga qaytaradi.
    var tabStartCol by remember { mutableStateOf<Col?>(null) }

    var notice by remember { mutableStateOf<Notice?>(startupWarning?.let { Notice(it, isError = true) }) }
    var importPending by remember { mutableStateOf<SheetStore.Parsed?>(null) }
    var saveText by remember { mutableStateOf(if (state.rows.isEmpty()) "Hali saqlanmagan" else "Fayldan yuklandi") }
    var saveError by remember { mutableStateOf(false) }
    var savedRevision by remember { mutableIntStateOf(0) }
    var fileMenu by remember { mutableStateOf(false) }
    var rowMenuFor by remember { mutableStateOf<Long?>(null) }

    val cellFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusTick) { if (focusTick > 0) runCatching { cellFocus.requestFocus() } }
    val listState = rememberLazyListState()

    // ---------- Ko‘rinish: qidiruv, filtr, saralash ----------
    val checks = remember(state.rows) { state.rows.associate { it.id to it.check() } }
    val visible = visibleRows(state.rows, checks, search, filter, sort, pinned, today, frozen)
    val lines = visible.map { GridLine(it, checks[it.id]) } + GridLine(null, null)

    /** Hodisa paytidagi (yangi) holat — kompozitsiyadagi ro‘yxat eskirgan bo‘lishi mumkin. */
    fun freshVisible(): List<SheetRow> =
        visibleRows(state.rows, state.rows.associate { it.id to it.check() }, search, filter, sort, pinned, today, frozen)

    fun indexIn(rows: List<SheetRow>): Int =
        if (selId == null) rows.size else rows.indexOfFirst { it.id == selId }.let { if (it < 0) 0 else it }
    val selIndex = if (selId == null) lines.lastIndex else lines.indexOfFirst { it.row?.id == selId }.coerceAtLeast(0)

    val counts = state.rows.mapNotNull { checks.getValue(it.id).status(today) }.groupingBy { it }.eachCount()
    val draftCount = state.rows.count { !it.isBlank && !checks.getValue(it.id).complete }

    // Tanlangan qator ko‘rinishdan chiqsa (o‘chirildi, boshqa filtr) — ko‘rinib turganini tanlaymiz. Qatorlar soniga emas,
    // ularning o‘ziga qaraymiz: soni bir xil boshqa filtrda tanlov yashirin qatorda qolib, yozuv unga tushmasin.
    val visibleIds = lines.map { it.row?.id }
    LaunchedEffect(visibleIds, selId) {
        if (selId != null && selId !in visibleIds) selId = visibleIds.firstOrNull()
    }
    // Tanlangan qator ko‘rinib tursin — faqat kerakli darajada aylantiramiz (qisman ko‘ringan qatorni bosganda
    // ro‘yxat sakramasin).
    LaunchedEffect(selIndex) {
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return@LaunchedEffect
        val item = items.firstOrNull { it.index == selIndex }
        val end = info.viewportEndOffset - info.afterContentPadding
        when {
            item == null && selIndex < items.first().index -> listState.scrollToItem(selIndex)
            item == null -> listState.scrollToItem((selIndex - items.size + 2).coerceAtLeast(0))
            item.offset < 0 -> listState.scrollBy(item.offset.toFloat())
            item.offset + item.size > end -> listState.scrollBy((item.offset + item.size - end).toFloat())
        }
    }

    // ---------- Avtomatik saqlash ----------
    LaunchedEffect(state.revision) {
        if (!autoSave || state.revision == savedRevision) return@LaunchedEffect
        delay(400)
        val revision = state.revision
        val snapshot = state.rows
        val result = withContext(Dispatchers.IO) { runCatching { store.save(snapshot, revision) } }
        if (result.isSuccess) {
            savedRevision = revision
            saveError = false
            saveText = "Saqlandi " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        } else {
            saveError = true
            saveText = "Saqlab bo‘lmadi: " + (result.exceptionOrNull()?.message ?: "noma’lum xato")
        }
    }

    fun rawOf(id: Long?, col: Col): String = id?.let { i -> state.rows.firstOrNull { it.id == i }?.get(col) } ?: ""

    fun viewChanged() {
        pinned = emptySet()
        frozen = null
    }

    fun freezeView() {
        if (frozen == null && (filter != Filter.ALL || search.isNotBlank() || sort != null)) frozen = freshVisible().map { it.id }
    }

    fun refocus() {
        focusTick++
    }

    fun setCell(value: String) {
        freezeView()
        val wasNew = selId == null
        val id = state.setCell(selId, selCol, value)
        if (wasNew && id != null) {
            selId = id
            pinned = pinned + id
        }
    }

    fun commitEdit() {
        if (!editing) return
        editing = false
        typedOver = false
        var value = editValue.text.trim()
        // Eski matn keyingi katakdagi yozuv oldiga tushib qolmasin.
        editValue = TextFieldValue("")
        if (selCol == Col.UNIT && value.isNotEmpty()) {
            // "m" → "ml", "k" → "kg" — bitta mos birlik bo‘lsa to‘ldiramiz.
            val normalized = normalizeUnit(value)
            value = Amounts.UNITS.singleOrNull { it == normalized } ?: Amounts.UNITS.filter { it.startsWith(normalized) }.singleOrNull() ?: normalized
        }
        if (value == rawOf(selId, selCol)) return
        setCell(value)
    }
    // Oyna yopilayotganda yozib turilgan katak yo‘qolmasin.
    SideEffect { state.flushPendingEdit = { commitEdit() } }

    fun cancelEdit() {
        editing = false
        typedOver = false
        editValue = TextFieldValue("")
    }

    /** [typed] — Excel’ning “kiritish” rejimi: strelkalar katakdan chiqaradi (F2 bilan ochilganda — matn ichida yuradi). */
    fun startEditing(text: String = rawOf(selId, selCol), typed: Boolean = false) {
        if (!selCol.editable) return
        editing = true
        typedOver = typed
        editValue = TextFieldValue(text, TextRange(text.length))
        refocus()
    }

    /** [id] null — oxiridagi “yangi qator”. [keepTab] — Tab bilan yurish davom etyapti. */
    fun select(id: Long?, col: Col, edit: Boolean = false, keepTab: Boolean = false) {
        commitEdit()
        if (!keepTab) tabStartCol = null
        selId = id?.takeIf { wanted -> state.rows.any { it.id == wanted } }
        selCol = col
        editValue = TextFieldValue("")
        if (edit) startEditing() else refocus()
    }

    fun move(dRow: Int, dCol: Int, keepTab: Boolean = false) {
        commitEdit()
        val rows = freshVisible()
        val index = (indexIn(rows) + dRow).coerceIn(0, rows.size)
        val cols = Col.entries
        select(rows.getOrNull(index)?.id, cols[(selCol.ordinal + dCol).coerceIn(0, cols.lastIndex)], keepTab = keepTab)
    }

    fun tab(back: Boolean) {
        if (tabStartCol == null) tabStartCol = selCol
        move(0, if (back) -1 else 1, keepTab = true)
    }

    fun enter(back: Boolean) {
        val start = tabStartCol
        if (start != null && !back) move(1, start.ordinal - selCol.ordinal) else move(if (back) -1 else 1, 0)
    }

    fun deleteRow(id: Long?) {
        if (id == null) return
        commitEdit()
        val before = freshVisible()
        val index = before.indexOfFirst { it.id == id }
        state.deleteRow(id)
        pinned = pinned - id
        val remaining = before.filterNot { it.id == id }
        selId = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id
        notice = Notice("Qator o‘chirildi. Tiklash uchun Ctrl+Z ni bosing.")
        refocus()
    }

    /**
     * Bekor qilish/qayta bajarishdan keyin — Excel’dagi kabi — tanlov o‘zgargan katakka qaytadi va u ko‘rinib turadi
     * (filtrga mos kelmasa ham). Aks holda tanlov boshqa qatorga o‘tib, keyingi yozuv begona reaktivga tushardi.
     */
    fun showChange(before: List<SheetRow>) {
        tabStartCol = null
        val (id, col) = changedSpot(before, state.rows) ?: return
        if (state.rows.none { it.id == id }) {
            // Bekor qilingan yangi qator — tanlov oxiridagi “yangi qator”ga.
            selId = null
            return
        }
        if (freshVisible().none { it.id == id }) pinned = pinned + id
        selId = id
        if (col != null) selCol = col
    }

    fun undo() {
        cancelEdit()
        val before = state.rows
        if (state.undo()) {
            showChange(before)
            notice = Notice("Oxirgi o‘zgarish bekor qilindi.")
        }
        refocus()
    }

    fun redo() {
        cancelEdit()
        val before = state.rows
        if (state.redo()) {
            showChange(before)
            notice = Notice("O‘zgarish qayta bajarildi.")
        }
        refocus()
    }

    fun paste() {
        val text = clipboard.getText()?.text ?: return
        if (text.isEmpty()) return
        val block = text.trimEnd('\r', '\n')
        if (block.contains('\t') || block.contains('\n')) {
            freezeView()
            val before = state.rows.map { it.id }.toSet()
            val rows = freshVisible()
            val monthFirst = DateText.isMonthFirst(clipboardDates(readClipboard(block), selCol))
            val count = state.paste(block, rows.map { it.id }, indexIn(rows), selCol)
            val created = state.rows.map { it.id }.filterNot { it in before }
            pinned = pinned + created
            if (selId == null) selId = created.firstOrNull()
            notice = when {
                count == 0 -> Notice("Hech narsa o‘zgarmadi: nusxadagi qiymatlar jadvaldagi bilan bir xil yoki ularni bu ustunga qo‘yib bo‘lmaydi.", isError = true)
                monthFirst -> Notice("$count qator qo‘yildi. Sanalar kompyuter sozlamasi bo‘yicha oy/kun/yil tartibida o‘qildi — tekshirib oling. Bekor qilish: Ctrl+Z.")
                else -> Notice("$count qator qo‘yildi. Bekor qilish: Ctrl+Z.")
            }
        } else if (selCol.editable) {
            val value = block.trim()
            val isDate = selCol == Col.RECEIVED || selCol == Col.EXPIRES
            setCell(if (isDate && DateText.isMonthFirst(listOf(value))) DateText.fromMonthFirst(value) else value)
        }
        refocus()
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val cmd = event.isCtrlPressed || event.isMetaPressed
        val shift = event.isShiftPressed

        if (editing) {
            return when (event.key) {
                Key.Enter, Key.NumPadEnter -> { enter(back = shift); true }
                Key.Tab -> { tab(back = shift); true }
                Key.Escape -> { cancelEdit(); refocus(); true }
                Key.DirectionUp -> { move(-1, 0); true }
                Key.DirectionDown -> { move(1, 0); true }
                // Excel: F2 — matn ichida yurish rejimi (strelkalar katakdan chiqarmaydi).
                Key.F2 -> { typedOver = false; true }
                Key.DirectionLeft -> if (typedOver) { move(0, -1); true } else false
                Key.DirectionRight -> if (typedOver) { move(0, 1); true } else false
                else -> false
            }
        }

        if (cmd) {
            return when (event.key) {
                Key.Z -> { if (shift) redo() else undo(); true }
                Key.Y -> { redo(); true }
                Key.C -> { clipboard.setText(AnnotatedString(rawOf(selId, selCol))); true }
                Key.X -> {
                    clipboard.setText(AnnotatedString(rawOf(selId, selCol)))
                    if (selCol.editable && selId != null) setCell("")
                    true
                }
                Key.V -> { paste(); true }
                Key.F -> { runCatching { searchFocus.requestFocus() }; true }
                Key.Minus -> { deleteRow(selId); true }
                Key.Semicolon -> {
                    if (selCol == Col.RECEIVED || selCol == Col.EXPIRES) setCell(DateText.format(todayProvider()))
                    true
                }
                Key.DirectionUp, Key.MoveHome -> {
                    select(freshVisible().firstOrNull()?.id, if (event.key == Key.MoveHome) Col.NAME else selCol)
                    true
                }
                Key.DirectionDown, Key.MoveEnd -> { select(null, selCol); true }
                else -> false
            }
        }

        return when (event.key) {
            Key.DirectionUp -> { move(-1, 0); true }
            Key.DirectionDown -> { move(1, 0); true }
            Key.DirectionLeft -> { move(0, -1); true }
            Key.DirectionRight -> { move(0, 1); true }
            Key.Tab -> { tab(back = shift); true }
            Key.Enter, Key.NumPadEnter -> { enter(back = shift); true }
            Key.F2 -> { if (selCol.editable) startEditing(); true }
            Key.Delete -> { if (selCol.editable && selId != null) setCell(""); true }
            Key.Backspace -> { startEditing("", typed = true); true }
            Key.MoveHome -> { select(selId, Col.NAME); true }
            Key.MoveEnd -> { select(selId, Col.STATUS); true }
            Key.PageUp -> { move(-15, 0); true }
            Key.PageDown -> { move(15, 0); true }
            Key.Escape -> true
            else -> false
        }
    }

    fun onEditValue(value: TextFieldValue) {
        if (editing) {
            editValue = value
        } else if (value.text.isNotEmpty() && selCol.editable) {
            // Ko‘rish rejimida yozila boshlandi — Excel’dagi kabi eski qiymat almashtiriladi.
            editing = true
            typedOver = true
            editValue = value
        }
    }

    // ---------- Fayl amallari ----------
    fun importFile() {
        commitEdit()
        val path = dialogs.openJson() ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (Files.size(path) > MAX_IMPORT_BYTES) throw BackupException("Fayl juda katta — bu zaxira nusxa emas.")
                    SheetStore.parse(Files.readString(path, StandardCharsets.UTF_8))
                }
            }
            result.onSuccess { parsed ->
                if (parsed.rows.isEmpty()) notice = Notice("Faylda reaktiv topilmadi.", isError = true) else importPending = parsed
            }.onFailure { e ->
                notice = Notice(if (e is BackupException) e.message ?: "" else "Faylni o‘qib bo‘lmadi: ${e.message}", isError = true)
            }
        }
    }

    fun exportBackup() {
        commitEdit()
        val path = dialogs.saveFile("Telefon uchun zaxira nusxa", "reaktivlar-zaxira-${todayProvider()}.json") ?: return
        val rows = state.rows
        val items = rows.mapNotNull { it.toBackupItem() }
        val skipped = rows.count { !it.isBlank } - items.size
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Tugallanmagan qatorlar ham ("drafts"): telefon ularni o‘qimaydi, kompyuterda ochilsa tiklanadi.
                    Files.writeString(path, SheetStore.toJson(rows, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), StandardCharsets.UTF_8)
                }
            }
            notice = result.fold(
                onSuccess = {
                    val extra = if (skipped > 0) " Tugallanmagan $skipped ta qator ham saqlandi (telefonga o‘tmaydi, kompyuterda ochilsa tiklanadi)." else ""
                    Notice("Zaxira saqlandi: ${items.size} ta reaktiv.$extra Telefonda: ⋮ → «Zaxiradan tiklash».")
                },
                onFailure = { Notice("Faylni saqlab bo‘lmadi: ${it.message}", isError = true) },
            )
        }
    }

    fun exportCsv() {
        commitEdit()
        val path = dialogs.saveFile("Excel uchun CSV", "reaktivlar-${todayProvider()}.csv") ?: return
        val rows = freshVisible().filterNot { it.isBlank }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Files.writeString(path, SheetStore.toCsv(rows, todayProvider()), StandardCharsets.UTF_8) }
            }
            notice = result.fold(
                onSuccess = { Notice("Excel uchun saqlandi: ${rows.size} ta qator (jadvalda ko‘rinib turgan tartibda).") },
                onFailure = { Notice("Faylni saqlab bo‘lmadi: ${it.message}", isError = true) },
            )
        }
    }

    // ---------- Ekran ----------
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Toolbar(
            search = search,
            onSearch = { search = it; viewChanged() },
            searchFocus = searchFocus,
            onSearchFocus = { searchFocused = it },
            filter = filter,
            onFilter = { commitEdit(); filter = it; viewChanged(); refocus() },
            counts = counts,
            total = state.rows.count { !it.isBlank },
            draftCount = draftCount,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onAdd = {
                commitEdit()
                tabStartCol = null
                selId = null
                selCol = Col.NAME
                startEditing("", typed = true)
            },
            onDelete = { deleteRow(selId) },
            canDelete = selId != null,
            onUndo = ::undo,
            onRedo = ::redo,
            fileMenu = fileMenu,
            onFileMenu = { fileMenu = it },
            onImport = { fileMenu = false; importFile() },
            onExportBackup = { fileMenu = false; exportBackup() },
            onExportCsv = { fileMenu = false; exportCsv() },
            onOpenFolder = { fileMenu = false; dialogs.openFolder(store.file.parent) },
        )

        val red = counts[ExpiryStatus.RED] ?: 0
        if (red > 0 && filter != Filter.RED) {
            Banner(
                text = "$red ta reaktivning muddati 1 oydan kam qoldi yoki o‘tib ketgan.",
                action = "Ko‘rsatish",
                onAction = { commitEdit(); filter = Filter.RED; viewChanged(); refocus() },
                warning = true,
            )
        }
        notice?.let { n ->
            Banner(text = n.text, action = "Yopish", onAction = { notice = null; refocus() }, warning = n.isError)
        }

        FormulaBar(
            ref = "${selCol.letter}${if (selId == null) "+" else (selIndex + 1).toString()}",
            content = if (editing) editValue.text else lines.getOrNull(selIndex)?.row?.let { row ->
                when (selCol) {
                    Col.STATUS -> lines[selIndex].check?.let { c -> c.status(today)?.label } ?: ""
                    else -> row[selCol]
                }
            } ?: "",
            hint = when (selCol) {
                Col.RECEIVED, Col.EXPIRES -> "Sana: kk.oo.yyyy (masalan 17.10.2026). Ctrl+; — bugungi sana"
                Col.UNIT -> "Birlik: gr, ml, kg, l yoki dona"
                Col.GIVEN, Col.STOCK -> "Son: 500 yoki 2,5"
                else -> null
            },
            error = lines.getOrNull(selIndex)?.let { line -> if (line.row != null && !line.row.isBlank) line.check?.error(selCol) else null },
        )

        SheetGrid(
            lines = lines,
            today = today,
            selIndex = selIndex,
            selCol = selCol,
            editing = editing,
            editValue = editValue,
            sort = sort,
            listState = listState,
            cellFocus = cellFocus,
            autoFocus = !searchFocused,
            onSelect = { id, col, edit -> select(id, col, edit) },
            onEditValue = ::onEditValue,
            onKey = ::handleKey,
            onFocusLost = { commitEdit() },
            // Yozib turgan katak ichiga bosildi — xatoni tuzatish uchun: strelkalar endi matn ichida yuradi.
            onEditPress = { typedOver = false },
            onSort = { col ->
                commitEdit()
                sort = when {
                    sort?.first != col -> col to true
                    sort?.second == true -> col to false
                    else -> null
                }
                viewChanged()
                refocus()
            },
            onRowMenu = { id -> rowMenuFor = id },
            onPickUnit = { unit ->
                editValue = TextFieldValue(unit, TextRange(unit.length))
                commitEdit()
                refocus()
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        StatusBar(
            total = state.rows.count { !it.isBlank },
            counts = counts,
            drafts = draftCount,
            saveText = saveText,
            saveError = saveError,
            file = store.file,
            onOpenFolder = { dialogs.openFolder(store.file.parent) },
        )
    }

    rowMenuFor?.let { id ->
        val name = state.rows.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: "Nomsiz qator"
        AlertDialog(
            onDismissRequest = { rowMenuFor = null },
            title = { Text("Qatorni o‘chirish") },
            text = { Text("«$name» qatori o‘chirilsinmi? Keyin Ctrl+Z bilan tiklash mumkin.") },
            confirmButton = { Button(onClick = { rowMenuFor = null; deleteRow(id) }) { Text("O‘chirish") } },
            dismissButton = { TextButton(onClick = { rowMenuFor = null; refocus() }) { Text("Bekor qilish") } },
        )
    }

    importPending?.let { parsed ->
        val current = state.rows.count { !it.isBlank }
        val incomplete = parsed.rows.count { !it.check().complete }
        AlertDialog(
            onDismissRequest = { importPending = null },
            title = { Text("Fayldan yuklash") },
            text = {
                Text(
                    buildString {
                        append("Faylda ${parsed.rows.size} ta qator bor")
                        if (incomplete > 0) append(" (shundan $incomplete tasi tugallanmagan)")
                        append(". ")
                        if (parsed.skipped > 0) append("${parsed.skipped} ta buzuq qator tashlab ketildi. ")
                        append("Jadvalda hozir $current ta qator bor.\n\nAlmashtirilsa, hozirgi jadval fayldagisi bilan almashadi (Ctrl+Z bilan tiklash mumkin).")
                    },
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        importPending = null
                        state.appendAll(parsed.rows)
                        viewChanged()
                        notice = Notice("${parsed.rows.size} ta qator jadval oxiriga qo‘shildi.")
                        refocus()
                    }) { Text("Oxiriga qo‘shish") }
                    Button(onClick = {
                        importPending = null
                        state.replaceAll(parsed.rows)
                        viewChanged()
                        selId = state.rows.firstOrNull()?.id
                        notice = Notice("Jadval fayldagi ${parsed.rows.size} ta qator bilan almashtirildi.")
                        refocus()
                    }) { Text("Almashtirish") }
                }
            },
            dismissButton = { TextButton(onClick = { importPending = null; refocus() }) { Text("Bekor qilish") } },
        )
    }
}

/**
 * Qidiruv, rang filtri va saralashdan o‘tgan qatorlar; yangi qo‘shilganlar ([pinned]) oxirida turadi.
 * [frozen] berilsa — ko‘rinish shu tartibda qoladi (o‘chirilganlari tushib qoladi, yangilari oxiriga qo‘shiladi).
 */
fun visibleRows(
    rows: List<SheetRow>,
    checks: Map<Long, RowCheck>,
    search: String,
    filter: Filter,
    sort: Pair<Col, Boolean>?,
    pinned: Set<Long>,
    today: LocalDate,
    frozen: List<Long>? = null,
): List<SheetRow> {
    if (frozen != null) {
        val byId = rows.associateBy { it.id }
        val shown = frozen.toSet()
        return frozen.mapNotNull(byId::get) + rows.filter { it.id in pinned && it.id !in shown }
    }
    val needle = searchKey(search.trim())
    val matching = rows.filter { row ->
        val check = checks.getValue(row.id)
        val searchOk = needle.isEmpty() || searchKey(row.name).contains(needle)
        val filterOk = when (filter) {
            Filter.ALL -> true
            Filter.DRAFT -> !check.complete && !row.isBlank
            else -> check.status(today) == filter.status
        }
        searchOk && filterOk && row.id !in pinned
    }
    val sorted = sort?.let { (col, asc) -> matching.sortedWith(rowComparator(col, asc)) } ?: matching
    return sorted + rows.filter { it.id in pinned }
}

private fun rowComparator(col: Col, ascending: Boolean): Comparator<SheetRow> {
    val base: Comparator<SheetRow> = when (col) {
        Col.NAME -> compareBy { searchKey(it.name.trim()) }
        Col.RECEIVED -> compareBy(nullsLast()) { DateText.parse(it.receivedAt) }
        Col.EXPIRES, Col.STATUS -> compareBy(nullsLast()) { DateText.parse(it.expiresAt) }
        Col.GIVEN -> compareBy(nullsLast()) { Amounts.parse(it.givenAmount) }
        Col.STOCK -> compareBy(nullsLast()) { Amounts.parse(it.stockAmount) }
        Col.UNIT -> compareBy { it.unit }
    }
    return if (ascending) base else base.reversed()
}

@Composable
private fun Toolbar(
    search: String,
    onSearch: (String) -> Unit,
    searchFocus: FocusRequester,
    onSearchFocus: (Boolean) -> Unit,
    filter: Filter,
    onFilter: (Filter) -> Unit,
    counts: Map<ExpiryStatus, Int>,
    total: Int,
    draftCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    fileMenu: Boolean,
    onFileMenu: (Boolean) -> Unit,
    onImport: () -> Unit,
    onExportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Reaktivlar hisobi", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SheetColors.Text)
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yangi qator")
            }
            OutlinedButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Qatorni o‘chirish")
            }
            TextButton(onClick = onUndo, enabled = canUndo) { Text("↶ Bekor qilish") }
            TextButton(onClick = onRedo, enabled = canRedo) { Text("↷ Qayta bajarish") }
            Spacer(Modifier.weight(1f))
            Box {
                OutlinedButton(onClick = { onFileMenu(true) }) {
                    Text("Fayl")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = fileMenu, onDismissRequest = { onFileMenu(false) }) {
                    DropdownMenuItem(text = { Text("Telefon zaxirasini ochish…") }, onClick = onImport)
                    DropdownMenuItem(text = { Text("Telefon uchun zaxira saqlash…") }, onClick = onExportBackup)
                    DropdownMenuItem(text = { Text("Excel uchun saqlash (CSV)…") }, onClick = onExportCsv)
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Ma’lumotlar papkasini ochish") }, onClick = onOpenFolder)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBox(search, onSearch, searchFocus, onSearchFocus)
            Spacer(Modifier.width(4.dp))
            Filter.entries.forEach { option ->
                val count = when (option) {
                    Filter.ALL -> total
                    Filter.DRAFT -> draftCount
                    else -> counts[option.status] ?: 0
                }
                if (option == Filter.DRAFT && draftCount == 0 && filter != Filter.DRAFT) return@forEach
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(if (filter == option && option != Filter.ALL) Filter.ALL else option) },
                    label = { Text("${option.label}  $count", fontSize = 13.sp, maxLines = 1, softWrap = false) },
                    leadingIcon = option.status?.let { status ->
                        { Box(Modifier.size(11.dp).background(SheetColors.pill.getValue(status).first, CircleShape)) }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SheetColors.Accent,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        HorizontalDivider(color = SheetColors.Grid)
    }
}

@Composable
private fun SearchBox(value: String, onValue: (String) -> Unit, focus: FocusRequester, onFocus: (Boolean) -> Unit) {
    Row(
        Modifier
            .width(280.dp)
            .height(34.dp)
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFCBD2DB), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = SheetColors.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Nomi bo‘yicha qidirish (Ctrl+F)", color = SheetColors.Muted, fontSize = 13.sp)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = SheetColors.Text),
                cursorBrush = SolidColor(SheetColors.Text),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { onFocus(it.isFocused) }.testTag("search").onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onValue(""); true } else false
                },
            )
        }
        if (value.isNotEmpty()) {
            Text("✕", color = SheetColors.Muted, modifier = Modifier.clickable { onValue("") }.padding(4.dp))
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit, warning: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(if (warning) Color(0xFFFEF3C7) else Color(0xFFE8F0FE))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (warning) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 13.sp, color = if (warning) Color(0xFF713F12) else Color(0xFF1E3A8A), modifier = Modifier.weight(1f))
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text(action) }
    }
}

@Composable
private fun FormulaBar(ref: String, content: String, hint: String?, error: String?) {
    Row(
        Modifier.fillMaxWidth().height(32.dp).background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(ROW_HEADER_WIDTH.dp + 30.dp).fillMaxHeight().gridLines(),
            contentAlignment = Alignment.Center,
        ) {
            Text(ref, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetColors.HeaderText)
        }
        Text("fx", fontSize = 13.sp, color = SheetColors.Muted, modifier = Modifier.padding(horizontal = 10.dp))
        Text(
            content,
            fontSize = 13.sp,
            color = SheetColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            error != null -> Text(error, fontSize = 12.5.sp, color = SheetColors.Error, modifier = Modifier.padding(horizontal = 12.dp))
            hint != null -> Text(hint, fontSize = 12.sp, color = SheetColors.Muted, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
    HorizontalDivider(color = SheetColors.Grid)
}

@Composable
private fun StatusBar(
    total: Int,
    counts: Map<ExpiryStatus, Int>,
    drafts: Int,
    saveText: String,
    saveError: Boolean,
    file: Path,
    onOpenFolder: () -> Unit,
) {
    HorizontalDivider(color = SheetColors.Grid)
    Row(
        Modifier.fillMaxWidth().height(28.dp).background(Color(0xFFF3F4F6)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Jami: $total", fontSize = 12.sp, color = SheetColors.HeaderText)
        ExpiryStatus.entries.forEach { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(SheetColors.pill.getValue(status).first, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text("${counts[status] ?: 0}", fontSize = 12.sp, color = SheetColors.HeaderText)
            }
        }
        if (drafts > 0) Text("Tugallanmagan: $drafts", fontSize = 12.sp, color = SheetColors.Muted)
        Spacer(Modifier.weight(1f))
        Text(saveText, fontSize = 12.sp, color = if (saveError) SheetColors.Error else SheetColors.Muted)
        Text(
            file.toString(),
            fontSize = 12.sp,
            color = SheetColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(360.dp).clickable(onClick = onOpenFolder),
        )
    }
}

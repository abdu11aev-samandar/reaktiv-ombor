package uz.reaktivombor.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Jadval holati: qatorlar ro‘yxati va Excel’dagi kabi bekor qilish (Ctrl+Z) / qaytarish (Ctrl+Y).
 * Har bir o‘zgarish butun ro‘yxatning yangi nusxasi — tarix oddiy va ishonchli.
 */
class SheetState(initial: List<SheetRow>, private val clock: () -> Long = System::currentTimeMillis) {
    var rows: List<SheetRow> by mutableStateOf(initial)
        private set

    /** Har o‘zgarishda oshadi — avtomatik saqlash shunga qaraydi. */
    var revision: Int by mutableStateOf(0)
        private set

    /** Katakda hali yozilayotgan qiymatni jadvalga o‘tkazadi (oyna yopilishidan oldin). Ekran o‘rnatadi. */
    var flushPendingEdit: () -> Unit = {}

    private val undoStack = ArrayDeque<List<SheetRow>>()
    private val redoStack = ArrayDeque<List<SheetRow>>()
    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    // Yaratilish vaqti doim o‘sib boradi — fayldan qayta ochilganda qatorlar tartibi o‘zgarmaydi.
    private var lastCreated: Long = initial.maxOfOrNull { it.createdAt } ?: Long.MIN_VALUE

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun commit(newRows: List<SheetRow>) {
        if (newRows == rows) return
        undoStack.addLast(rows)
        if (undoStack.size > HISTORY) undoStack.removeFirst()
        redoStack.clear()
        rows = newRows
        revision++
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(rows)
        rows = previous
        revision++
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(rows)
        rows = next
        revision++
        return true
    }

    private fun stamp(): Long = maxOf(clock(), lastCreated + 1).also { lastCreated = it }

    fun newRow(): SheetRow = SheetRow(id = nextId++, createdAt = stamp())

    /** Katakni o‘zgartiradi. [id] null bo‘lsa — oxiriga yangi qator qo‘shiladi; uning id’si qaytariladi. */
    fun setCell(id: Long?, col: Col, value: String): Long? {
        if (!col.editable) return id
        if (id == null) {
            if (value.isBlank()) return null
            val row = newRow().with(col, value)
            commit(rows + row)
            return row.id
        }
        commit(rows.map { if (it.id == id) it.with(col, value) else it })
        return id
    }

    fun deleteRow(id: Long) = commit(rows.filterNot { it.id == id })

    fun replaceAll(newRows: List<SheetRow>) {
        newRows.maxOfOrNull { it.createdAt }?.let { lastCreated = maxOf(lastCreated, it) }
        commit(newRows.map { it.copy(id = nextId++) })
    }

    /** Qo‘shilgan qatorlar jadval oxirida turadi — qayta ochilganda ham (yaratilish vaqti yangilanadi). */
    fun appendAll(newRows: List<SheetRow>) = commit(rows + newRows.map { it.copy(id = nextId++, createdAt = stamp()) })

    /**
     * Excel’dan nusxalangan blokni (tab va yangi qator bilan ajratilgan) [startIndex]-qatorning [startCol] katagidan boshlab qo‘yadi.
     * Kerak bo‘lsa yangi qatorlar qo‘shiladi. [targetIds] — ko‘rinib turgan tartibdagi qatorlar (filtr/saralashga mos).
     * Haqiqatan o‘zgargan yoki qo‘shilgan qatorlar sonini qaytaradi.
     */
    fun paste(
        text: String,
        targetIds: List<Long>,
        startIndex: Int,
        startCol: Col,
        pcMonthFirst: Boolean = DateText.pcMonthFirst,
    ): Int {
        val lines = readClipboard(text)
        if (lines.size == 1 && lines[0].all { it.isBlank() }) return 0
        val cols = Col.entries
        fun colAt(cellIndex: Int): Col? = cols.getOrNull(startCol.ordinal + cellIndex)
        fun isDate(col: Col?) = col?.isDate == true
        val monthFirst = DateText.isMonthFirst(clipboardDates(lines, startCol), pcMonthFirst)

        val updated = rows.toMutableList()
        var touched = 0
        lines.forEachIndexed { lineIndex, cells ->
            val existing = targetIds.getOrNull(startIndex + lineIndex)
                ?.let { id -> updated.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
            var row = existing?.let { updated[it] } ?: newRow()
            cells.forEachIndexed { cellIndex, raw ->
                val col = colAt(cellIndex)
                if (col != null && col.editable) {
                    val value = raw.trim()
                    row = row.with(col, if (monthFirst && isDate(col)) DateText.fromMonthFirst(value) else value)
                }
            }
            when {
                existing != null -> if (updated[existing] != row) {
                    updated[existing] = row
                    touched++
                }
                !row.isBlank -> {
                    updated += row
                    touched++
                }
            }
        }
        commit(updated)
        return touched
    }

    private companion object {
        const val HISTORY = 200
    }
}

/** Nusxa (Windows/Mac/Linux qator oxirlari bilan) → qatorlar va kataklar. */
fun readClipboard(text: String): List<List<String>> =
    parseClipboard(text.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n'))

/** Nusxaning [startCol] dan boshlab qo‘yilganda sana ustunlariga tushadigan qiymatlari. */
fun clipboardDates(lines: List<List<String>>, startCol: Col): List<String> = lines.flatMap { cells ->
    cells.filterIndexed { i, _ -> Col.entries.getOrNull(startCol.ordinal + i)?.isDate == true }
}

/**
 * Ikki holat orasida o‘zgargan birinchi qator va (bitta katak o‘zgargan bo‘lsa) ustun.
 * Bekor qilish/qaytarishdan keyin tanlov Excel’dagi kabi shu katakka qaytadi.
 */
fun changedSpot(before: List<SheetRow>, after: List<SheetRow>): Pair<Long, Col?>? {
    val old = before.associateBy { it.id }
    after.firstOrNull { old[it.id] != it }?.let { row ->
        val previous = old[row.id] ?: return row.id to null
        return row.id to Col.entries.filter { it.editable && previous[it] != row[it] }.singleOrNull()
    }
    val now = after.mapTo(HashSet()) { it.id }
    return before.firstOrNull { it.id !in now }?.let { it.id to null }
}

/**
 * Excel/Google Sheets nusxasini kataklarga ajratadi. Ichida Alt+Enter (yangi qator) bo‘lgan katakni
 * Excel qo‘shtirnoqqa oladi ("Natriy xlorid\n(osh tuzi)") — bunday katak bitta katak bo‘lib qoladi, qator bo‘sh joy bilan birlashadi.
 */
internal fun parseClipboard(text: String): List<List<String>> {
    val lines = mutableListOf(mutableListOf<String>())
    var i = 0
    while (true) {
        val quoteEnd = if (text.getOrNull(i) == '"') closingQuote(text, i) else null
        if (quoteEnd != null) {
            lines.last() += text.substring(i + 1, quoteEnd)
                .replace("\"\"", "\"")
                .replace(Regex("""\s*\n\s*"""), " ")
                .replace('\t', ' ')
            i = quoteEnd + 1
        } else {
            var end = i
            while (end < text.length && text[end] != '\t' && text[end] != '\n') end++
            lines.last() += text.substring(i, end)
            i = end
        }
        if (i >= text.length) break
        if (text[i] == '\n') lines += mutableListOf<String>()
        i++
    }
    return lines
}

/** [start]dagi qo‘shtirnoqning jufti; undan keyin tab, yangi qator yoki matn oxiri bo‘lmasa — bu oddiy belgi. */
private fun closingQuote(text: String, start: Int): Int? {
    var i = start + 1
    while (i < text.length) {
        if (text[i] == '"') {
            if (text.getOrNull(i + 1) == '"') {
                i += 2
                continue
            }
            val next = text.getOrNull(i + 1)
            return if (next == null || next == '\t' || next == '\n') i else null
        }
        i++
    }
    return null
}

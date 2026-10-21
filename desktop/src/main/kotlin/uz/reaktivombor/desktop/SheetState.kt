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

    private val undoStack = ArrayDeque<List<SheetRow>>()
    private val redoStack = ArrayDeque<List<SheetRow>>()
    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

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

    fun newRow(): SheetRow = SheetRow(id = nextId++, createdAt = clock())

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

    fun replaceAll(newRows: List<SheetRow>) = commit(newRows.map { it.copy(id = nextId++) })

    fun appendAll(newRows: List<SheetRow>) = commit(rows + newRows.map { it.copy(id = nextId++) })

    /**
     * Excel’dan nusxalangan blokni (tab va yangi qator bilan ajratilgan) [startId] qatorining [startCol] katagidan boshlab qo‘yadi.
     * Kerak bo‘lsa yangi qatorlar qo‘shiladi. [targetIds] — ko‘rinib turgan tartibdagi qatorlar (filtr/saralashga mos).
     */
    fun paste(text: String, targetIds: List<Long>, startIndex: Int, startCol: Col): Int {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n').split('\n')
        if (lines.isEmpty() || (lines.size == 1 && lines[0].isEmpty())) return 0
        val cols = Col.entries
        val updated = rows.toMutableList()
        var created = 0
        lines.forEachIndexed { lineIndex, line ->
            val cells = line.split('\t')
            val targetIndex = startIndex + lineIndex
            var row = targetIds.getOrNull(targetIndex)?.let { id -> updated.firstOrNull { it.id == id } }
            val isNew = row == null
            if (row == null) row = newRow().also { created++ }
            cells.forEachIndexed { cellIndex, value ->
                val col = cols.getOrNull(startCol.ordinal + cellIndex)
                if (col != null && col.editable) row = row!!.with(col, value.trim())
            }
            if (isNew) {
                if (!row!!.isBlank) updated += row!!
            } else {
                val index = updated.indexOfFirst { it.id == row!!.id }
                updated[index] = row!!
            }
        }
        commit(updated)
        return lines.size
    }

    private companion object {
        const val HISTORY = 200
    }
}

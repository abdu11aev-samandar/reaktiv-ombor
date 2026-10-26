package uz.reaktivombor.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Jadval holati: qatorlar, bo‘limlar ro‘yxati va Excel’dagi kabi bekor qilish (Ctrl+Z) / qaytarish (Ctrl+Y).
 * Har bir o‘zgarish butun holatning yangi nusxasi — tarix oddiy va ishonchli.
 */
class SheetState(
    initial: List<SheetRow>,
    initialDepartments: List<String> = DEFAULT_DEPARTMENTS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var rows: List<SheetRow> by mutableStateOf(emptyList())
        private set

    /** «Bo‘lim» ustunida tanlanadigan bo‘limlar. Jadvalda yozilgan har bir bo‘lim shu ro‘yxatda bo‘ladi. */
    var departments: List<String> by mutableStateOf(emptyList())
        private set

    init {
        val (opened, list) = withDepartmentsInList(initial, initialDepartments)
        rows = opened
        departments = list
    }

    /** Har o‘zgarishda oshadi — avtomatik saqlash shunga qaraydi. */
    var revision: Int by mutableStateOf(0)
        private set

    /** Katakda hali yozilayotgan qiymatni jadvalga o‘tkazadi (oyna yopilishidan oldin). Ekran o‘rnatadi. */
    var flushPendingEdit: () -> Unit = {}

    private data class Snapshot(val rows: List<SheetRow>, val departments: List<String>)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    // Yaratilish vaqti doim o‘sib boradi — fayldan qayta ochilganda qatorlar tartibi o‘zgarmaydi.
    private var lastCreated: Long = initial.maxOfOrNull { it.createdAt } ?: Long.MIN_VALUE

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun commit(newRows: List<SheetRow>, newDepartments: List<String> = departments) {
        if (newRows == rows && newDepartments == departments) return
        undoStack.addLast(Snapshot(rows, departments))
        if (undoStack.size > HISTORY) undoStack.removeFirst()
        redoStack.clear()
        rows = newRows
        departments = newDepartments
        revision++
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(Snapshot(rows, departments))
        restore(previous)
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(Snapshot(rows, departments))
        restore(next)
        return true
    }

    private fun restore(snapshot: Snapshot) {
        rows = snapshot.rows
        departments = snapshot.departments
        revision++
    }

    private fun stamp(): Long = maxOf(clock(), lastCreated + 1).also { lastCreated = it }

    fun newRow(): SheetRow = SheetRow(id = nextId++, createdAt = stamp())

    /**
     * Katakni o‘zgartiradi. [id] null bo‘lsa — oxiriga yangi qator qo‘shiladi; uning id’si qaytariladi.
     * Bo‘limlar ro‘yxatida yo‘q bo‘lim yozilsa, u ro‘yxatga qo‘shiladi (Ctrl+Z ikkalasini birga bekor qiladi).
     */
    fun setCell(id: Long?, col: Col, value: String): Long? {
        if (!col.editable) return id
        val list = departments.toMutableList()
        val stored = if (col == Col.DEPARTMENT) resolveDepartment(value, list) else value
        if (id == null) {
            if (stored.isBlank()) return null
            val row = newRow().with(col, stored)
            commit(rows + row, list)
            return row.id
        }
        commit(rows.map { if (it.id == id) it.with(col, stored) else it }, list)
        return id
    }

    fun deleteRow(id: Long) = commit(rows.filterNot { it.id == id })

    /** Fayldagi jadval: qatorlar va o‘sha faylning bo‘limlar ro‘yxati. */
    fun replaceAll(newRows: List<SheetRow>, newDepartments: List<String> = DEFAULT_DEPARTMENTS) {
        newRows.maxOfOrNull { it.createdAt }?.let { lastCreated = maxOf(lastCreated, it) }
        val (opened, list) = withDepartmentsInList(newRows, newDepartments)
        commit(opened.map { it.copy(id = nextId++) }, list)
    }

    /**
     * Qo‘shilgan qatorlar jadval oxirida turadi — qayta ochilganda ham (yaratilish vaqti yangilanadi).
     * Ularning bo‘limlari (va fayldagi ro‘yxat, [newDepartments]) bu jadval ro‘yxatiga qo‘shiladi.
     */
    fun appendAll(newRows: List<SheetRow>, newDepartments: List<String> = emptyList()) {
        val (added, list) = withDepartmentsInList(newRows, departments + newDepartments)
        commit(rows + added.map { it.copy(id = nextId++, createdAt = stamp()) }, list)
    }

    private fun SheetRow.isIn(name: String) = department.isNotBlank() && sameDepartment(department, name)

    /** Bo‘lim nechta qatorda yozilgan. */
    fun usage(name: String): Int = rows.count { it.isIn(name) }

    /** Bo‘limlar oynasidagi amal natijasi: [ok] bo‘lmasa — [message] sababni aytadi. */
    data class Change(val ok: Boolean, val message: String)

    fun addDepartment(raw: String): Change {
        val name = matchDepartment(raw, departments)
        return when {
            name.isEmpty() -> Change(false, "Bo‘lim nomini yozing.")
            !isDepartmentName(name) -> Change(false, DEPARTMENT_NAME_ERROR)
            departments.any { sameDepartment(it, name) } -> Change(false, "«$name» ro‘yxatda bor.")
            else -> {
                commit(rows, departments + name)
                Change(true, "«$name» qo‘shildi.")
            }
        }
    }

    /**
     * Bo‘lim nomini o‘zgartiradi — u yozilgan qatorlarda ham. Ro‘yxatdagi boshqa bo‘lim nomi berilsa, ikkalasi
     * birlashadi (masalan, xato yozilgan «Biologya» → «Biologiya»).
     */
    fun renameDepartment(old: String, raw: String): Change {
        if (old !in departments) return Change(false, "«$old» ro‘yxatda yo‘q.")
        val text = cleanName(raw)
        if (text.isEmpty()) return Change(false, "Bo‘lim nomini yozing.")
        val used = usage(old)
        val other = departments.firstOrNull { it != old && sameDepartment(it, text) }
        if (other != null) {
            commit(rows.map { if (it.isIn(old)) it.copy(department = other) else it }, departments - old)
            return Change(true, "«$old» «$other» bilan birlashtirildi" + if (used > 0) " ($used ta qator)." else ".")
        }
        val name = text.replaceFirstChar { it.titlecase() }
        if (!isDepartmentName(name)) return Change(false, DEPARTMENT_NAME_ERROR)
        if (name == old) return Change(true, "")
        commit(
            rows.map { if (it.isIn(old)) it.copy(department = name) else it },
            departments.map { if (it == old) name else it },
        )
        return Change(true, "«$old» → «$name»" + if (used > 0) " ($used ta qatorda ham)." else ".")
    }

    /** Bo‘limni ro‘yxatdan olib tashlaydi — faqat u hech bir qatorda yozilmagan bo‘lsa (yozuvlar yo‘qolmasin). */
    fun removeDepartment(name: String): Change {
        val used = usage(name)
        if (used > 0) {
            return Change(
                false,
                "«$name» $used ta qatorda yozilgan. Avval o‘sha qatorlarda boshqa bo‘lim tanlang yoki nomini " +
                    "o‘zgartirib, boshqa bo‘lim bilan birlashtiring.",
            )
        }
        commit(rows, departments - name)
        return Change(true, "«$name» olib tashlandi.")
    }

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
        val list = departments.toMutableList()
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
                    val stored = when {
                        monthFirst && isDate(col) -> DateText.fromMonthFirst(value)
                        col == Col.DEPARTMENT -> resolveDepartment(value, list)
                        else -> value
                    }
                    row = row.with(col, stored)
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
        commit(updated, list)
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

package uz.reaktivombor.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Saralash: ustun va yo‘nalish. */
@Immutable
data class TableSort(val columnId: String, val descending: Boolean = false)

/**
 * Jadval ko‘rinishi: ustunlar tartibi, yashirilganlari, foydalanuvchi o‘zgartirgan kengliklar (dp) va saralash.
 * JSON bo‘lib sozlamalarda saqlanadi (Settings.setTableLayout).
 */
@Immutable
data class TableLayout(
    val order: List<String>,
    val hidden: Set<String> = emptySet(),
    val widths: Map<String, Float> = emptyMap(),
    val sort: TableSort? = null,
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("version", VERSION)
        json.put("order", JSONArray(order))
        json.put("hidden", JSONArray(hidden.sorted()))
        val w = JSONObject()
        widths.toSortedMap().forEach { (id, dp) -> w.put(id, Math.round(dp * 10) / 10.0) }
        json.put("widths", w)
        sort?.let { json.put("sort", JSONObject().put("column", it.columnId).put("desc", it.descending)) }
        return json.toString()
    }

    companion object {
        const val VERSION = 1

        /** O‘qiydi; buzuq yoki boshqa versiyadagi matn — null (jadval standart holatda ochiladi). */
        fun fromJson(text: String?): TableLayout? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(text)
                if (json.optInt("version", VERSION) != VERSION) return null
                // Tartibsiz yozuv — bizning jadval ko‘rinishi emas.
                val orderArray = json.optJSONArray("order") ?: return null
                val order = (0 until orderArray.length()).map { orderArray.getString(it) }
                val hidden = json.optJSONArray("hidden")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                val widths = json.optJSONObject("widths")?.let { o ->
                    o.keySet().associateWith { o.getDouble(it).toFloat() }.filterValues { it.isFinite() && it > 0f }
                } ?: emptyMap()
                val sort = json.optJSONObject("sort")?.let { s ->
                    s.optString("column").takeIf { it.isNotEmpty() }?.let { TableSort(it, s.optBoolean("desc", false)) }
                }
                TableLayout(order, hidden, widths, sort)
            }.getOrNull()
        }
    }
}

/** Ustunning ko‘rinishga oid ma’lumotlari (TableColumn’dan olinadi). */
@Immutable
data class ColumnSpec(
    val id: String,
    val width: Dp,
    val minWidth: Dp,
    val sortable: Boolean,
    val hideable: Boolean,
    val visibleByDefault: Boolean,
)

/** Kenglikning yuqori chegarasi. */
val MaxColumnWidth: Dp = 1000.dp

/**
 * Jadval ko‘rinishining holati. Har bir o‘zgarish (saralash, yashirish, surish, kenglik, standart holat)
 * [onSave] ga JSON bo‘lib beriladi; kenglik sudralayotganda — faqat qo‘yib yuborilganda.
 */
@Stable
class TableLayoutState(
    columns: List<ColumnSpec>,
    val defaultSort: TableSort? = null,
    private val onSave: ((String) -> Unit)? = null,
) {
    private val specs: Map<String, ColumnSpec> = columns.associateBy { it.id }
    private val defaultOrder: List<String> = columns.map { it.id }

    /** Standart holat: ustunlar e’lon qilingan tartibda, yashirilgani faqat visibleByDefault=false. */
    val defaults: TableLayout = TableLayout(
        order = defaultOrder,
        hidden = columns.filter { !it.visibleByDefault && it.hideable }.map { it.id }.toSet(),
        sort = defaultSort?.takeIf { specs[it.columnId]?.sortable == true },
    )

    var layout: TableLayout by mutableStateOf(defaults)
        private set

    val order: List<String> get() = layout.order
    val hidden: Set<String> get() = layout.hidden
    val sort: TableSort? get() = layout.sort
    val visibleIds: List<String> get() = layout.order.filter { it !in layout.hidden }

    fun isVisible(id: String): Boolean = id in specs && id !in layout.hidden

    fun widthOf(id: String): Dp {
        val spec = specs[id] ?: return 120.dp
        return layout.widths[id]?.dp?.coerceIn(spec.minWidth, MaxColumnWidth) ?: spec.width
    }

    /** Sarlavha bosilganda: saralanmagan → o‘sish → kamayish → saralanmagan. */
    fun toggleSort(id: String) {
        if (specs[id]?.sortable != true) return
        val current = layout.sort
        val next = when {
            current?.columnId != id -> TableSort(id, descending = false)
            !current.descending -> TableSort(id, descending = true)
            else -> null
        }
        update(layout.copy(sort = next))
    }

    fun setSort(sort: TableSort?) {
        if (sort != null && specs[sort.columnId]?.sortable != true) return
        update(layout.copy(sort = sort))
    }

    /** Ustunni ko‘rsatish/yashirish. Oxirgi ko‘rinib turgan ustun yashirilmaydi. */
    fun setVisible(id: String, visible: Boolean) {
        val spec = specs[id] ?: return
        if (visible) {
            if (id in layout.hidden) update(layout.copy(hidden = layout.hidden - id))
        } else {
            if (!spec.hideable || id in layout.hidden || visibleIds.size <= 1) return
            val sort = layout.sort?.takeIf { it.columnId != id }
            update(layout.copy(hidden = layout.hidden + id, sort = sort))
        }
    }

    fun canHide(id: String): Boolean = specs[id]?.hideable == true && (id in layout.hidden || visibleIds.size > 1)

    /** Ko‘rinib turgan chapdagi qo‘shnisi bilan o‘rin almashadi (yashirin ustunlar ustidan sakraydi). */
    fun moveLeft(id: String) = move(id, -1)

    fun moveRight(id: String) = move(id, +1)

    fun canMove(id: String, direction: Int): Boolean {
        val visible = visibleIds
        val index = visible.indexOf(id)
        return index >= 0 && index + direction in visible.indices
    }

    private fun move(id: String, direction: Int) {
        if (!canMove(id, direction)) return
        val visible = visibleIds
        val neighbour = visible[visible.indexOf(id) + direction]
        val order = layout.order.toMutableList()
        val a = order.indexOf(id)
        val b = order.indexOf(neighbour)
        order[a] = neighbour
        order[b] = id
        update(layout.copy(order = order))
    }

    /** Kenglik (dp). [persist] = false — sudralayotganda; oxirida [persist] chaqiriladi. */
    fun setWidth(id: String, width: Dp, persist: Boolean = true) {
        val spec = specs[id] ?: return
        val clamped = width.coerceIn(spec.minWidth, MaxColumnWidth)
        val widths = if (clamped == spec.width) layout.widths - id else layout.widths + (id to clamped.value)
        layout = layout.copy(widths = widths)
        if (persist) persist()
    }

    fun resetWidth(id: String) {
        if (id in layout.widths) update(layout.copy(widths = layout.widths - id))
    }

    /** «Standart holatga qaytarish». */
    fun reset() = update(defaults)

    fun toJson(): String = layout.toJson()

    /** Saqlangan JSON’ni qo‘llaydi (saqlamaydi). Noma’lum ustunlar tashlanadi, yangi ustunlar standart joyiga qo‘yiladi. */
    fun applyJson(json: String?) {
        layout = merge(TableLayout.fromJson(json) ?: return)
    }

    fun persist() {
        onSave?.invoke(layout.toJson())
    }

    private fun update(next: TableLayout) {
        if (next == layout) return
        layout = next
        persist()
    }

    private fun merge(saved: TableLayout): TableLayout {
        val known = saved.order.filter { it in specs }.distinct()
        val missing = defaultOrder.filter { it !in known }
        val order = known.toMutableList()
        // Saqlangandan keyin qo‘shilgan ustun — standart tartibdagi oldingi qo‘shnisidan keyin.
        missing.forEach { id ->
            val before = defaultOrder.subList(0, defaultOrder.indexOf(id)).lastOrNull { it in order }
            order.add(if (before == null) 0 else order.indexOf(before) + 1, id)
        }
        var hidden = saved.hidden.filter { specs[it]?.hideable == true }.toSet() +
            missing.filter { specs.getValue(it).let { s -> !s.visibleByDefault && s.hideable } }
        if (order.all { it in hidden }) hidden = defaults.hidden
        val widths = saved.widths
            .filterKeys { it in specs }
            .mapValues { (id, w) -> w.dp.coerceIn(specs.getValue(id).minWidth, MaxColumnWidth).value }
        val sort = saved.sort?.takeIf { specs[it.columnId]?.sortable == true && it.columnId !in hidden }
            ?: if (saved.sort == null) null else defaults.sort
        return TableLayout(order, hidden, widths, sort)
    }
}

/**
 * Sahifadagi jadval uchun holat: [load] — saqlangan JSON (masalan, `services.settings.tableLayout("reagents")`),
 * [save] — saqlash (`services.settings.setTableLayout("reagents", it)`). Buzuq JSON e’tiborsiz qoldiriladi.
 */
@Composable
fun rememberTableLayoutState(
    columns: List<TableColumn<*>>,
    load: () -> String? = { null },
    save: (String) -> Unit = {},
    defaultSort: TableSort? = null,
): TableLayoutState {
    val currentSave by rememberUpdatedState(save)
    val ids = columns.map { it.id }
    return remember(ids, defaultSort) {
        TableLayoutState(columns.map { it.spec }, defaultSort) { json -> runCatching { currentSave(json) } }
            .also { state -> state.applyJson(runCatching(load).getOrNull()) }
    }
}

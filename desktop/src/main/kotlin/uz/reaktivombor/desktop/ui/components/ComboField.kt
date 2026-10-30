package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.searchKey
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.theme.AppColors

/**
 * Yozish bilan ro‘yxatdan tanlash (avtomatik to‘ldirish) va erkin matn. Yozilganda mos takliflar ochiladi:
 * ↑/↓ — tanlash, Enter — yozish, Esc — yopish. [allowNew] bo‘lsa ro‘yxatda yo‘q qiymat uchun «+ «X» qo‘shish» bandi
 * chiqadi (tanlansa [onNewItem], u berilmagan bo‘lsa [onPick] chaqiriladi).
 */
@Composable
fun ComboField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String? = null,
    error: String? = null,
    help: String? = null,
    enabled: Boolean = true,
    allowNew: Boolean = false,
    newItemText: (String) -> String = { "+ «$it» qo‘shish" },
    onNewItem: ((String) -> Unit)? = null,
    onPick: ((String) -> Unit)? = null,
    maxItems: Int = 30,
    testTag: String = "combo",
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Tashqaridan o‘zgargan qiymat (shakl tozalandi, ro‘yxatdan tanlandi) — kursor oxirida.
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var highlighted by remember { mutableIntStateOf(-1) }
    var focused by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val items = remember(value, showAll, suggestions, maxItems) { filterSuggestions(if (showAll) "" else value, suggestions, maxItems) }
    val newValue = if (allowNew) comboNewValue(value, suggestions) else null
    val entryCount = items.size + if (newValue != null) 1 else 0
    val open = expanded && enabled && entryCount > 0

    fun close() {
        expanded = false
        showAll = false
        highlighted = -1
    }

    fun pick(text: String, isNew: Boolean) {
        field = TextFieldValue(text, TextRange(text.length))
        close()
        if (text != value) onValueChange(text)
        if (isNew) (onNewItem ?: onPick)?.invoke(text) else onPick?.invoke(text)
    }

    fun pickIndex(index: Int) {
        if (index < items.size) pick(items[index], isNew = false) else if (newValue != null) pick(newValue, isNew = true)
    }

    LabeledField(label, modifier, required, error, help) {
        Box {
            FieldFrame(
                Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width },
                focused = focused,
                isError = error != null,
                enabled = enabled,
                padding = PaddingValues(start = 10.dp, end = 3.dp),
            ) {
                BasicTextField(
                    value = shown,
                    onValueChange = { next ->
                        val changed = next.text != value
                        field = next
                        if (changed) {
                            onValueChange(next.text)
                            expanded = true
                            showAll = false
                            highlighted = -1
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(testTag)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (!state.isFocused) close()
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (!open) {
                                        expanded = true
                                        showAll = value.isBlank()
                                    } else {
                                        highlighted = (highlighted + 1).coerceAtMost(entryCount - 1)
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (open) highlighted = (highlighted - 1).coerceAtLeast(0)
                                    open
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    if (!open) return@onPreviewKeyEvent false
                                    val exact = items.indexOfFirst { searchKey(cleanName(it)) == searchKey(cleanName(value)) }
                                    when {
                                        highlighted >= 0 -> pickIndex(highlighted)
                                        exact >= 0 -> pickIndex(exact)
                                        newValue != null -> pick(newValue, isNew = true)
                                        else -> close()
                                    }
                                    true
                                }
                                Key.Escape -> {
                                    if (open) close()
                                    open
                                }
                                Key.Tab -> {
                                    close()
                                    false
                                }
                                else -> false
                            }
                        },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = FieldTextStyle.copy(color = if (enabled) AppColors.Text else AppColors.Muted),
                    cursorBrush = SolidColor(AppColors.Primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = FieldTextStyle.copy(color = AppColors.Disabled), maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
                IconAction(
                    icon = AppIcons.DropDown,
                    label = "Ro‘yxatni ochish",
                    onClick = {
                        if (open) {
                            close()
                        } else {
                            expanded = true
                            showAll = true
                        }
                    },
                    tint = AppColors.Muted,
                    enabled = enabled,
                    testTag = "$testTag-arrow",
                )
            }

            val scroll = rememberScrollState()
            // Bandlar balandligi har xil (uzun nom 2 qatorda): ↑/↓ bilan tanlanganini ko‘rinadigan joyga aylantirish uchun.
            val itemBounds = remember { mutableStateMapOf<Int, IntRange>() }
            LaunchedEffect(highlighted) {
                val bounds = itemBounds[highlighted] ?: return@LaunchedEffect
                val viewport = scroll.viewportSize
                when {
                    bounds.first < scroll.value -> scroll.scrollTo(bounds.first)
                    viewport > 0 && bounds.last > scroll.value + viewport -> scroll.scrollTo(bounds.last - viewport)
                }
            }
            val fieldWidth = with(density) { widthPx.toDp() }.coerceAtLeast(160.dp)
            DropdownMenu(
                expanded = open,
                onDismissRequest = { close() },
                // Fokus matn maydonida qoladi — yozish davom etadi.
                properties = PopupProperties(focusable = false),
                scrollState = scroll,
                // Ro‘yxat maydondan kengroq bo‘lishi mumkin (uzun nomlar bir-biridan ajralib tursin), lekin ComboMenuMaxWidth gacha.
                modifier = Modifier.widthIn(min = fieldWidth, max = maxOf(fieldWidth, ComboMenuMaxWidth)).heightIn(max = 300.dp),
            ) {
                fun Modifier.bounds(index: Int) = onPlaced { c ->
                    val top = c.positionInParent().y.toInt()
                    itemBounds[index] = top..(top + c.size.height)
                }
                items.forEachIndexed { index, item ->
                    ComboItem(
                        text = item,
                        highlighted = index == highlighted,
                        onClick = { pick(item, isNew = false) },
                        modifier = Modifier.bounds(index).testTag("$testTag-item-$index"),
                    )
                }
                if (newValue != null) {
                    ComboItem(
                        text = newItemText(newValue),
                        highlighted = highlighted == items.size,
                        onClick = { pick(newValue, isNew = true) },
                        accent = true,
                        modifier = Modifier.bounds(items.size).testTag("$testTag-new"),
                    )
                }
            }
        }
    }
}

private val ComboItemHeight = 34.dp

/** Takliflar ro‘yxatining eng katta kengligi (maydon bundan keng bo‘lsa — maydon kengligida). */
private val ComboMenuMaxWidth = 560.dp

/** Taklif bandi: qisqa nom — bir qator, uzun nom — 2 qatorga o‘raladi (oxiri «…»). */
@Composable
private fun ComboItem(text: String, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = ComboItemHeight)
            .background(if (highlighted) AppColors.PrimarySoft else AppColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            fontSize = 13.5.sp,
            lineHeight = 17.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (accent) AppColors.Primary else AppColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Takliflar: avval boshi mos keladiganlar, keyin ichida uchraydiganlar (katta-kichik harf va o‘/o'/o’ farqlanmaydi).
 * Bo‘sh so‘rov — hammasi.
 */
fun filterSuggestions(query: String, items: List<String>, limit: Int = 30): List<String> {
    val key = searchKey(cleanName(query))
    val unique = items.distinctBy { searchKey(cleanName(it)) }
    if (key.isEmpty()) return unique.take(limit)
    val starts = mutableListOf<String>()
    val contains = mutableListOf<String>()
    unique.forEach { item ->
        val itemKey = searchKey(cleanName(item))
        when {
            itemKey.startsWith(key) -> starts += item
            key in itemKey -> contains += item
        }
    }
    return (starts + contains).take(limit)
}

/** Ro‘yxatda aynan shunday qiymat bo‘lmasa — yangi qiymat sifatida qo‘shiladigan (tozalangan) matn, aks holda null. */
fun comboNewValue(query: String, items: List<String>): String? {
    val text = cleanName(query)
    if (text.isEmpty()) return null
    val key = searchKey(text)
    return if (items.any { searchKey(cleanName(it)) == key }) null else text
}

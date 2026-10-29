package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.theme.AppColors

/**
 * Faqat ro‘yxatdan tanlanadigan maydon (yozib bo‘lmaydi). Enter, probel yoki ↓ — ochadi.
 * [value] null bo‘lsa [placeholder] ko‘rinadi.
 */
@Composable
fun <T> SelectField(
    value: T?,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String = "Tanlang",
    optionLabel: (T) -> String = { it.toString() },
    error: String? = null,
    help: String? = null,
    enabled: Boolean = true,
    testTag: String = "select",
) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val density = LocalDensity.current

    LabeledField(label, modifier, required, error, help) {
        Box {
            FieldFrame(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { widthPx = it.width }
                    .onPreviewKeyEvent { event ->
                        val opens = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar || event.key == Key.DirectionDown
                        if (enabled && event.type == KeyEventType.KeyDown && opens) {
                            expanded = true
                            true
                        } else {
                            false
                        }
                    }
                    .focusable(enabled, interaction)
                    .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.DropdownList) { expanded = !expanded }
                    .testTag(testTag),
                focused = focused || expanded,
                isError = error != null,
                enabled = enabled,
                padding = PaddingValues(start = 10.dp, end = 6.dp),
            ) {
                Text(
                    text = value?.let(optionLabel) ?: placeholder,
                    style = FieldTextStyle.copy(color = if (value == null || !enabled) AppColors.Disabled else AppColors.Text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(AppIcons.DropDown, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(22.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(density) { widthPx.toDp() }.coerceAtLeast(160.dp)).heightIn(max = 360.dp),
            ) {
                options.forEachIndexed { index, option ->
                    val selected = option == value
                    DropdownMenuItem(
                        text = {
                            Text(
                                optionLabel(option),
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) AppColors.Primary else AppColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = if (selected) {
                            { Icon(AppIcons.Check, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        modifier = Modifier.height(34.dp).testTag("$testTag-option-$index"),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    )
                }
                if (options.isEmpty()) {
                    Text("Ro‘yxat bo‘sh", fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

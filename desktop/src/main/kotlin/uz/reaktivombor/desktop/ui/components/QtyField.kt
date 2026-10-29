package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.theme.AppColors

/**
 * Miqdor va birlik: son yoziladi ("1 250,5"), birlik faqat [units] dan tanlanadi (masalan, partiya o‘lchamiga mos
 * l / ml). Son tekshiruvi (Qty.parse) sahifada — xato [error] orqali ko‘rsatiladi. Maydonga faqat raqam, bo‘shliq,
 * vergul va nuqta yoziladi.
 */
@Composable
fun QtyField(
    amount: String,
    onAmountChange: (String) -> Unit,
    unit: String?,
    onUnitChange: (String) -> Unit,
    units: List<String>,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String = "0",
    unitLabel: (String) -> String = { it },
    error: String? = null,
    help: String? = null,
    enabled: Boolean = true,
    testTag: String = "qty",
) {
    var focused by remember { mutableStateOf(false) }
    var unitsOpen by remember { mutableStateOf(false) }
    LabeledField(label, modifier, required, error, help) {
        FieldFrame(
            Modifier.fillMaxWidth(),
            focused = focused,
            isError = error != null,
            enabled = enabled,
            padding = PaddingValues(start = 10.dp),
        ) {
            BasicTextField(
                value = amount,
                onValueChange = { next -> onAmountChange(next.filter { it.isDigit() || it in QTY_CHARS }) },
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }.testTag(testTag),
                enabled = enabled,
                singleLine = true,
                textStyle = FieldTextStyle.copy(textAlign = TextAlign.End, color = if (enabled) AppColors.Text else AppColors.Muted),
                cursorBrush = SolidColor(AppColors.Primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (amount.isEmpty()) Text(placeholder, style = FieldTextStyle.copy(color = AppColors.Disabled))
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(1.dp).height(FieldHeight - 12.dp).background(AppColors.Divider))
            Box {
                val choosable = enabled && units.size > 1
                Row(
                    Modifier
                        .height(FieldHeight)
                        .widthIn(min = 64.dp)
                        .clickable(enabled = choosable, role = Role.DropdownList) { unitsOpen = true }
                        .padding(start = 10.dp, end = if (choosable) 4.dp else 10.dp)
                        .testTag("$testTag-unit"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        unit?.let(unitLabel) ?: "—",
                        style = FieldTextStyle.copy(fontWeight = FontWeight.Medium, color = if (unit != null && unit in units) AppColors.Text else AppColors.Error),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (choosable) Icon(AppIcons.DropDown, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = unitsOpen, onDismissRequest = { unitsOpen = false }) {
                    units.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    unitLabel(option),
                                    fontSize = 13.5.sp,
                                    fontWeight = if (option == unit) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (option == unit) AppColors.Primary else AppColors.Text,
                                )
                            },
                            onClick = {
                                unitsOpen = false
                                onUnitChange(option)
                            },
                            modifier = Modifier.height(34.dp).widthIn(min = 96.dp).testTag("$testTag-unit-$index"),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Sondagi ruxsat etilgan belgilar: bo‘shliqlar (oddiy, bo‘linmaydigan, ingichka), vergul va nuqta. */
private const val QTY_CHARS = "    ,."

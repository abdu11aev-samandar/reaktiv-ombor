package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.theme.AppColors

/** Maydon balandligi: ixcham (1366×768 da ko‘p maydon sig‘sin). */
val FieldHeight: Dp = 36.dp

internal val FieldTextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = AppColors.Text)

/** Maydon ramkasi: oq fon, chegara (fokusda ko‘k, xatoda qizil), ichida qator. */
@Composable
internal fun FieldFrame(
    modifier: Modifier,
    focused: Boolean,
    isError: Boolean,
    enabled: Boolean,
    padding: PaddingValues = PaddingValues(start = 10.dp, end = 10.dp),
    minHeight: Dp = FieldHeight,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val color = when {
        !enabled -> AppColors.Divider
        isError -> AppColors.Error
        focused -> AppColors.Primary
        else -> AppColors.FieldBorder
    }
    Row(
        modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (enabled) AppColors.Surface else AppColors.Background)
            .border(if (focused || isError) 1.5.dp else 1.dp, color, shape)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Oddiy matn maydoni (nom, seriya, izoh …) — boshqa maydonlar bilan bir xil ko‘rinishda. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String? = null,
    error: String? = null,
    help: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    textAlign: TextAlign = TextAlign.Start,
    focusRequester: FocusRequester? = null,
    testTag: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    LabeledField(label, modifier, required, error, help) {
        FieldFrame(
            Modifier.fillMaxWidth(),
            focused = focused,
            isError = error != null,
            enabled = enabled,
            padding = PaddingValues(start = 10.dp, end = if (trailing != null) 3.dp else 10.dp, top = if (singleLine) 0.dp else 8.dp, bottom = if (singleLine) 0.dp else 8.dp),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (onEnter != null && singleLine && event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            onEnter(); true
                        } else {
                            false
                        }
                    }
                    .let { if (testTag != null) it.testTag(testTag) else it },
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = FieldTextStyle.copy(textAlign = textAlign, color = if (enabled) AppColors.Text else AppColors.Muted),
                cursorBrush = SolidColor(AppColors.Primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = FieldTextStyle.copy(color = AppColors.Disabled, textAlign = textAlign), maxLines = 1, modifier = Modifier.fillMaxWidth())
                        }
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

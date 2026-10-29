package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.theme.AppColors

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, TEXT }

/** Dasturdagi hamma tugmalar shu ko‘rinishda: bir xil balandlik, shrift va burchak. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(6.dp)
    val padding = PaddingValues(start = if (icon != null) 10.dp else 14.dp, end = 14.dp)
    val sized = modifier.height(ButtonHeight)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (kind) {
        ButtonKind.PRIMARY, ButtonKind.DANGER -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (kind == ButtonKind.DANGER) AppColors.Error else AppColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = AppColors.Divider,
                disabledContentColor = AppColors.Disabled,
            ),
            contentPadding = padding,
            content = content,
        )
        ButtonKind.SECONDARY -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AppColors.Surface,
                contentColor = AppColors.Primary,
                disabledContentColor = AppColors.Disabled,
            ),
            border = BorderStroke(1.dp, if (enabled) AppColors.Border else AppColors.Divider),
            contentPadding = padding,
            content = content,
        )
        ButtonKind.TEXT -> TextButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Primary, disabledContentColor = AppColors.Disabled),
            contentPadding = PaddingValues(horizontal = 10.dp),
            content = content,
        )
    }
}

internal val ButtonHeight = 34.dp

/**
 * Kichik belgili tugma (jadvaldagi amallar, maydon ichidagi tugmalar). [label] — ustiga kelganda chiqadigan izoh va
 * ekran o‘quvchi/test uchun nom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.Primary,
    enabled: Boolean = true,
    size: Dp = 30.dp,
    iconSize: Dp = 18.dp,
    testTag: String? = null,
) {
    TooltipArea(
        tooltip = { TooltipBox(label) },
        delayMillis = 450,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
    ) {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .let { if (testTag != null) it.testTag(testTag) else it },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) tint else AppColors.Disabled, modifier = Modifier.size(iconSize))
        }
    }
}

/** Qora izoh qutisi (tooltip). */
@Composable
fun TooltipBox(text: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = AppColors.Text, shadowElevation = 4.dp) {
        Text(text, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

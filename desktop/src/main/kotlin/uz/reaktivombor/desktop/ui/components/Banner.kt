package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.style

/** Darajaga mos belgi. */
val Severity.icon: ImageVector
    get() = when (this) {
        Severity.INFO -> AppIcons.Info
        Severity.SUCCESS -> AppIcons.Success
        Severity.WARNING -> AppIcons.Warning
        Severity.ERROR -> AppIcons.Error
    }

/**
 * Xabar paneli (natija, ogohlantirish, xato): chapda rangli chiziq va belgi, matn, ixtiyoriy tugma ([actionLabel]) va
 * yopish (×, [onDismiss] berilsa).
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    severity: Severity = Severity.INFO,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    testTag: String = "banner",
) {
    val style = severity.style
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(style.background)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(style.accent))
        Row(
            Modifier.weight(1f).heightIn(min = 40.dp).padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(severity.icon, contentDescription = null, tint = style.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = style.foreground)
                Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = style.foreground)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                AppButton(actionLabel, onClick = onAction, kind = ButtonKind.TEXT)
            }
            if (onDismiss != null) {
                IconAction(AppIcons.Close, "Yopish", onClick = onDismiss, tint = style.foreground, size = 28.dp, iconSize = 16.dp)
            }
        }
    }
}

/** Bo‘sh ro‘yxat yoki jadval o‘rnida: belgi, matn, izoh va ixtiyoriy tugma. */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = AppIcons.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(AppColors.Background), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 12.5.sp, color = AppColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            AppButton(actionLabel, onClick = onAction, kind = ButtonKind.SECONDARY)
        }
    }
}

package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.theme.AppColors

/**
 * Kartalar qatori: hammasi bir xil balandlikda. Ichidagi kartalarga `Modifier.weight(1f).fillMaxHeight()` beriladi.
 */
@Composable
fun StatCardRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/**
 * Bosh sahifadagi ko‘rsatkich kartasi: belgi (rangli kvadratda), katta son va nomi. [onClick] bo‘lsa karta bosiladi
 * (masalan, tegishli sahifaga o‘tadi). [hint] — son ostidagi qisqa izoh.
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = AppColors.Primary,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .let { if (testTag != null) it.testTag(testTag) else it },
        shape = shape,
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Divider),
    ) {
        Row(Modifier.heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(value, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Text, maxLines = 1)
                Text(label, fontSize = 12.5.sp, lineHeight = 16.sp, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (hint != null) {
                    Text(hint, fontSize = 11.5.sp, lineHeight = 15.sp, color = accent, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

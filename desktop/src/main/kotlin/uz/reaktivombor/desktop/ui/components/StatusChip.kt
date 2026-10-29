package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.theme.StatusStyle

/** Holat belgisi: rangli fon, nuqta va matn (holat doim rang VA matn bilan). */
@Composable
fun StatusChip(style: StatusStyle, modifier: Modifier = Modifier, text: String = style.label) {
    StatusChip(text, style.background, style.foreground, modifier, dot = style.accent, bold = style.bold)
}

@Composable
fun StatusChip(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    dot: Color? = foreground,
    bold: Boolean = false,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .padding(start = if (dot != null) 7.dp else 9.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = foreground,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

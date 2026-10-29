package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.theme.AppColors

/**
 * Sahifa ramkasi: sarlavha (20sp), izoh va o‘ngda tugmalar; ostida sahifa mazmuni. Aylantirishni sahifa o‘zi tanlaydi
 * (jadvalli sahifada jadval o‘zi aylanadi, shakl sahifasida — verticalScroll).
 */
@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Oq karta: sarlavha, izoh, o‘ngda tugmalar va ichida maydonlar. */
@Composable
fun FormSection(
    title: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Divider),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null || description != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
            content()
        }
    }
}

/** Maydonlar qatori — bir xil oraliq, tepadan tekislangan (xato matni chiqqanda qo‘shnilar siljimaydi). */
@Composable
fun FormRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top, content = content)
}

/** Maydon nomi (majburiy bo‘lsa — qizil yulduzcha), maydonning o‘zi va ostida xato yoki izoh. */
@Composable
fun LabeledField(
    label: String?,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    help: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        if (label != null) {
            Row {
                Text(label, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = FieldLabelColor)
                if (required) Text(" *", fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Error)
            }
            Spacer(Modifier.height(4.dp))
        }
        content()
        when {
            error != null -> FieldMessage(error, AppColors.Error)
            help != null -> FieldMessage(help, AppColors.Muted)
        }
    }
}

@Composable
internal fun FieldMessage(text: String, color: Color) {
    Text(text, fontSize = 12.sp, lineHeight = 15.sp, color = color, modifier = Modifier.padding(top = 3.dp))
}

internal val FieldLabelColor = Color(0xFF39424E)

/**
 * Filtrlar paneli: maydonlar bir nechta qatorga o‘raladi, oxirida «Filtrlarni tozalash».
 * [activeCount]: null — son noma’lum (tugma doim faol); 0 — filtr yo‘q (tugma o‘chiq); > 0 — tugma yonida son.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBar(
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    activeCount: Int? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Divider),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            Icon(AppIcons.Filter, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.padding(top = 24.dp).size(18.dp))
            Spacer(Modifier.width(10.dp))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
            if (onClear != null) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.padding(top = 20.dp)) {
                    AppButton(
                        text = if (activeCount != null && activeCount > 0) "Filtrlarni tozalash ($activeCount)" else "Filtrlarni tozalash",
                        onClick = onClear,
                        kind = ButtonKind.TEXT,
                        icon = AppIcons.Close,
                        enabled = activeCount != 0,
                    )
                }
            }
        }
    }
}

/** Tez filtr tugmasi: nom, son va (ixtiyoriy) holat rangi nuqtasi. */
@Immutable
data class ChipOption(val id: String, val label: String, val count: Int? = null, val color: Color? = null)

/** Bitta tanlanadigan tez filtrlar qatori (masalan, Muddati o‘tgan / Qizil / Sariq / Yashil / Hammasi). */
@Composable
fun FilterChips(
    options: List<ChipOption>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { option ->
            val active = option.id == selected
            Row(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) AppColors.Primary else AppColors.Surface)
                    .border(1.dp, if (active) AppColors.Primary else AppColors.Border, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab) { onSelect(option.id) }
                    .padding(horizontal = 12.dp)
                    .testTag("chip-${option.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (option.color != null) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(option.color))
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    option.label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Color.White else AppColors.Text,
                )
                if (option.count != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        option.count.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White.copy(alpha = 0.85f) else AppColors.Muted,
                    )
                }
            }
        }
    }
}

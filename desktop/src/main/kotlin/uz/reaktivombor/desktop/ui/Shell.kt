package uz.reaktivombor.desktop.ui

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.style
import java.time.LocalDate

/** Dastur sahifalari — chap paneldagi tartibda (DESIGN.md §6). */
enum class Page(val title: String) {
    DASHBOARD("Bosh sahifa"),
    REAGENTS("Barcha reaktivlar"),
    ADD_REAGENT("Reaktiv qo‘shish"),
    RECEIPTS("Kirim operatsiyalari"),
    ISSUES("Sarf va berish"),
    DEPARTMENTS("Bo‘limlar bo‘yicha hisob"),
    DISTRICTS("Tumanlarga berilgan reaktivlar"),
    EXPIRY("Yaroqlilik muddati nazorati"),
    STOCK("Umumiy zaxiralar"),
    REPORTS("Hisobotlar"),
    HISTORY("Operatsiyalar tarixi"),
    BACKUP("Ma’lumotlarni zaxiralash"),
    SETTINGS("Sozlamalar"),
    ;

    val icon: ImageVector
        get() = when (this) {
            DASHBOARD -> AppIcons.Dashboard
            REAGENTS -> AppIcons.Reagents
            ADD_REAGENT -> AppIcons.AddReagent
            RECEIPTS -> AppIcons.Receipts
            ISSUES -> AppIcons.Issues
            DEPARTMENTS -> AppIcons.Departments
            DISTRICTS -> AppIcons.Districts
            EXPIRY -> AppIcons.Expiry
            STOCK -> AppIcons.Stock
            REPORTS -> AppIcons.Reports
            HISTORY -> AppIcons.History
            BACKUP -> AppIcons.Backup
            SETTINGS -> AppIcons.Settings
        }
}

/** Qo‘ng‘iroqchadagi bitta bildirishnoma: matn, daraja (nuqta rangi) va bosilganda ochiladigan sahifa. */
@Immutable
data class ShellNotification(val text: String, val severity: Severity, val page: Page)

const val APP_NAME = "Reaktiv Control"
const val APP_SUBTITLE = "Forensic Laboratory Management System"

/**
 * Dastur ramkasi: tepada nom, sana va bildirishnomalar, chapda 13 bo‘limli navigatsiya, o‘rtada [content].
 * Bildirishnoma bosilsa [onNotificationClick] (standarti — uning sahifasiga o‘tish).
 * [badgeCount] — qo‘ng‘iroqcha ustidagi son (standarti — hamma bildirishnomalar; dasturda: muddati o‘tgan + qizil +
 * minimal qoldiqdan kam, sariqlar ro‘yxatda bor, lekin sanalmaydi). [AppTheme] ichida chaqiriladi.
 */
@Composable
fun AppShell(
    current: Page,
    onNavigate: (Page) -> Unit,
    notifications: List<ShellNotification>,
    onNotificationClick: (ShellNotification) -> Unit = { onNavigate(it.page) },
    today: LocalDate,
    modifier: Modifier = Modifier,
    badgeCount: Int = notifications.size,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        TopBar(today, notifications, badgeCount, onNotificationClick)
        Row(Modifier.fillMaxWidth().weight(1f)) {
            NavRail(current, onNavigate)
            Box(Modifier.weight(1f).fillMaxHeight().testTag("page-content")) { content() }
        }
    }
}

private val RailWidth = 228.dp
private val TopBarHeight = 56.dp

@Composable
private fun TopBar(today: LocalDate, notifications: List<ShellNotification>, badgeCount: Int, onNotificationClick: (ShellNotification) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(AppColors.Surface)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(AppColors.Border, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(AppColors.Navy),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Reagents, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(APP_NAME, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = AppColors.Navy)
            Text(APP_SUBTITLE, fontSize = 12.sp, lineHeight = 15.sp, color = AppColors.Muted)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("today")) {
            Icon(AppIcons.Calendar, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(UzDates.format(today), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
                Text(UzDates.weekday(today), fontSize = 12.sp, lineHeight = 15.sp, color = AppColors.Muted)
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(1.dp).height(28.dp).background(AppColors.Divider))
        Spacer(Modifier.width(8.dp))
        NotificationBell(notifications, badgeCount, onNotificationClick)
    }
}

@Composable
private fun NotificationBell(notifications: List<ShellNotification>, count: Int, onClick: (ShellNotification) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (open) AppColors.PrimarySoft else Color.Transparent)
                .clickable(role = Role.Button, onClickLabel = "Bildirishnomalar") { open = !open }
                .semantics { contentDescription = "Bildirishnomalar: $count" }
                .testTag("notifications"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bell, contentDescription = null, tint = AppColors.Navy, modifier = Modifier.size(24.dp))
            if (count > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 3.dp)
                        .heightIn(min = 18.dp)
                        .widthIn(min = 18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AppColors.Error)
                        .border(1.5.dp, AppColors.Surface, RoundedCornerShape(9.dp))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 99) "99+" else count.toString(),
                        color = Color.White,
                        fontSize = 10.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("notification-badge"),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(400.dp).heightIn(max = 480.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bildirishnomalar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, modifier = Modifier.weight(1f))
                if (notifications.isNotEmpty()) Text("${notifications.size} ta", fontSize = 12.sp, color = AppColors.Muted)
            }
            HorizontalDivider(color = AppColors.Divider)
            if (notifications.isEmpty()) {
                Text(
                    "Hozircha bildirishnoma yo‘q.",
                    fontSize = 13.sp,
                    color = AppColors.Muted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
            notifications.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(item.severity.style.accent))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(item.text, fontSize = 13.sp, lineHeight = 17.sp, color = AppColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(item.page.title, fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Primary)
                            }
                        }
                    },
                    onClick = {
                        open = false
                        onClick(item)
                    },
                    modifier = Modifier.testTag("notification-$index"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun NavRail(current: Page, onNavigate: (Page) -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.width(RailWidth).fillMaxHeight().background(AppColors.Navy)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .selectableGroup()
                .testTag("nav-rail"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Page.entries.forEach { page ->
                NavItem(page, page == current) { onNavigate(page) }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 1.dp),
            style = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = 6.dp,
                shape = RoundedCornerShape(3.dp),
                hoverDurationMillis = 200,
                unhoverColor = Color.White.copy(alpha = 0.25f),
                hoverColor = Color.White.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun NavItem(page: Page, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(8.dp)
    val content = if (selected) Color.White else AppColors.NavyText
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(
                when {
                    selected -> AppColors.Primary
                    hovered || focused -> AppColors.NavyHover
                    else -> Color.Transparent
                },
            )
            .let { if (focused) it.border(1.5.dp, Color.White.copy(alpha = 0.7f), shape) else it }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        onClick()
                        true
                    }
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Next)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Previous)
                    else -> false
                }
            }
            .hoverable(interaction)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("nav-${page.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(page.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(11.dp))
        Text(
            page.title,
            color = content,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

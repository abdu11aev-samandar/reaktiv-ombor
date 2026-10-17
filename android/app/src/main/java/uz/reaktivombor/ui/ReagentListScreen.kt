package uz.reaktivombor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.reaktivombor.data.Reagent
import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.ExpiryStatus
import uz.reaktivombor.domain.daysUntil
import uz.reaktivombor.domain.expiryHint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Qidiruv: katta-kichik harf va o‘ / o' / o’ farqlanmaydi. */
fun searchKey(value: String): String =
    value.replace(Regex("['`’ʻʼ]"), "‘").lowercase()

private class ListRow(val reagent: Reagent, val number: Int, val status: ExpiryStatus, val days: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReagentListScreen(
    reagents: List<Reagent>?,
    today: LocalDate,
    snackbar: SnackbarHostState,
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Reagent) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortByExpiry by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val needle = searchKey(search.trim())
    val all = (reagents ?: emptyList())
        .filter { needle.isEmpty() || searchKey(it.name).contains(needle) }
        .map { r -> ListRow(r, 0, ExpiryStatus.of(r.expiresAt, today), daysUntil(r.expiresAt, today)) }
    val counts = all.groupingBy { it.status }.eachCount()
    val selected = statusFilter?.let { runCatching { ExpiryStatus.valueOf(it) }.getOrNull() }
    // № — ko‘rinib turgan tartib bo‘yicha (web ilovadagi kabi).
    val visible = all
        .filter { selected == null || it.status == selected }
        .let { rows -> if (sortByExpiry) rows.sortedBy { it.reagent.expiresAt } else rows }
        .mapIndexed { index, row -> ListRow(row.reagent, index + 1, row.status, row.days) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Reaktivlar hisobi", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Bugun: ${today.format(DATE_FORMAT)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menyu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Zaxira nusxa olish") },
                                onClick = { menuOpen = false; onExport() },
                            )
                            DropdownMenuItem(
                                text = { Text("Zaxiradan tiklash") },
                                onClick = { menuOpen = false; onImport() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                // TalkBack faqat “Tugma” demasligi uchun.
                modifier = Modifier.semantics { contentDescription = "Reaktiv qo‘shish" },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Reaktiv qo‘shish") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (reagents == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = inner.calculateStartPadding(direction) + 16.dp,
                end = inner.calculateEndPadding(direction) + 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notificationsEnabled) {
                item(key = "notify") { NotificationsBanner(onEnableNotifications) }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nomi bo‘yicha qidirish…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Qidiruvni tozalash")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selected == null,
                        onClick = { statusFilter = null },
                        label = { Text("Hammasi  ${all.size}") },
                        colors = selectedChipColors(),
                    )
                    ExpiryStatus.entries.forEach { status ->
                        FilterChip(
                            selected = selected == status,
                            onClick = { statusFilter = if (selected == status) null else status.name },
                            leadingIcon = { StatusDot(status) },
                            label = { Text("${status.label}  ${counts[status] ?: 0}") },
                            colors = selectedChipColors(),
                        )
                    }
                    FilterChip(
                        selected = sortByExpiry,
                        onClick = { sortByExpiry = !sortByExpiry },
                        label = { Text(if (sortByExpiry) "▲ Muddati yaqinlari birinchi" else "↕ Muddati yaqinlari birinchi") },
                        colors = selectedChipColors(),
                    )
                }
            }

            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        text = if (reagents.isEmpty()) "Hozircha reaktiv kiritilmagan." else "Bu filtr bo‘yicha reaktiv topilmadi.",
                        actionLabel = if (reagents.isEmpty()) "Birinchi reaktivni qo‘shish" else null,
                        onAction = onAdd,
                    )
                }
            } else {
                items(visible, key = { it.reagent.id }) { row ->
                    ReagentCard(row.reagent, row.number, row.status, row.days, onClick = { onEdit(row.reagent) })
                }
            }
        }
    }
}

@Composable
private fun ReagentCard(reagent: Reagent, number: Int, status: ExpiryStatus, days: Long, onClick: () -> Unit) {
    val palette = LocalStatusPalette.current
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NamePill(reagent.name, status) }
                Spacer(Modifier.width(12.dp))
                Text(
                    "№ $number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            InfoRow("Kelgan vaqt", reagent.receivedAt.format(DATE_FORMAT))
            InfoRow("Berilgan miqdor", Amounts.format(reagent.givenAmount, reagent.unit))
            InfoRow("Yaroqlilik muddati", reagent.expiresAt.format(DATE_FORMAT)) {
                Text(
                    expiryHint(days),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.hint.getValue(status),
                    fontWeight = if (status == ExpiryStatus.RED) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                )
            }
            InfoRow("Ombordagi miqdor", Amounts.format(reagent.stockAmount, reagent.unit))
        }
    }
}

@Composable
fun NamePill(name: String, status: ExpiryStatus, modifier: Modifier = Modifier) {
    val (background, foreground) = LocalStatusPalette.current.pill.getValue(status)
    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.semantics { contentDescription = "$name, ${status.label}" },
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Tanlangan chip aniq ko‘rinsin (standart rang fon bilan deyarli bir xil edi). */
@Composable
fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
fun StatusDot(status: ExpiryStatus) {
    val (color, _) = LocalStatusPalette.current.pill.getValue(status)
    Box(Modifier.size(12.dp).background(color, CircleShape))
}

@Composable
private fun InfoRow(label: String, value: String, extra: (@Composable () -> Unit)? = null) {
    // Katta shriftda ham ikkala ustun o‘z joyida o‘raladi (bir-birini siqib chiqarmaydi).
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f).padding(end = 12.dp),
        )
        Column(Modifier.weight(0.55f), horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
            extra?.invoke()
        }
    }
}

@Composable
private fun NotificationsBanner(onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                "Eslatmalar o‘chirilgan. Muddat yaqinlashganda xabar olish uchun ruxsat bering.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) { Text("Yoqish") }
        }
    }
}

@Composable
private fun EmptyState(text: String, actionLabel: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.service.BackupFile
import uz.reaktivombor.desktop.service.BackupKind
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.theme.AppColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// «Ma’lumotlarni zaxiralash» va «Sozlamalar» sahifalarining umumiy kichik qismlari.

/** Fayl hajmi: "812 bayt", "152,4 KB", "1,2 MB" (kasr — vergul bilan). */
internal fun backupSizeText(bytes: Long): String {
    if (bytes < 1024) return "$bytes bayt"
    val units = listOf("KB", "MB", "GB")
    var value = BigDecimal(bytes).divide(BigDecimal(1024), 6, RoundingMode.HALF_UP)
    var index = 0
    while (value >= BigDecimal(1024) && index < units.lastIndex) {
        value = value.divide(BigDecimal(1024), 6, RoundingMode.HALF_UP)
        index++
    }
    val scale = if (value >= BigDecimal(100)) 0 else 1
    return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',') + " " + units[index]
}

/**
 * Oxirgi avtomatik zaxira sanasi: sozlamadagi sana yoki backups papkasidagi eng yangi `auto-YYYY-MM-DD.db` — qaysi biri
 * keyinroq (tiklangan bazada sozlama eskirgan bo‘lishi mumkin, fayl esa joyida).
 */
internal fun backupLastAutoDate(setting: LocalDate?, files: List<BackupFile>): LocalDate? {
    val fromFiles = files.filter { it.kind == BackupKind.AUTO }
        .mapNotNull { runCatching { LocalDate.parse(it.path.fileName.toString().removePrefix("auto-").removeSuffix(".db")) }.getOrNull() }
        .maxOrNull()
    return listOfNotNull(setting, fromFiles).maxOrNull()
}

/** Dastur versiyasi: o‘rnatilgan dasturda jpackage beradi, ishlab chiqishda — yo‘q. */
internal fun backupAppVersion(): String = System.getProperty("jpackage.app-version")?.takeIf { it.isNotBlank() } ?: "ishlab chiqish nusxasi"

/**
 * Oq karta (FormSection kabi), lekin ichidagi ustun kartaning butun balandligini egallaydi: yonma-yon kartalar bir xil
 * balandlikda bo‘lsa, pastdagi tugmalar `Spacer(Modifier.weight(1f))` bilan pastga tekislanadi.
 */
@Composable
internal fun BackupCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.let { if (testTag != null) it.testTag(testTag) else it },
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Divider),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                    if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            content()
        }
    }
}

/**
 * Nom va qiymat qatori (papka yo‘li, versiya …): chapda kichik kulrang nom ustida qiymat (belgilab nusxa olsa bo‘ladi),
 * o‘ngda ixtiyoriy «Papkani ochish» tugmasi.
 */
@Composable
internal fun BackupInfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    openLabel: String = "Papkani ochish",
    openIcon: ImageVector = AppIcons.Folder,
    onOpen: (() -> Unit)? = null,
    testTag: String? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 36.dp).let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted)
            SelectionContainer {
                Text(value, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onOpen != null) {
            Spacer(Modifier.width(8.dp))
            AppButton(openLabel, onClick = onOpen, kind = ButtonKind.TEXT, icon = openIcon)
        }
    }
}

/** Kichik izoh matni (kartalar ichida). */
@Composable
internal fun BackupNote(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, color = AppColors.Muted, modifier = modifier)
}

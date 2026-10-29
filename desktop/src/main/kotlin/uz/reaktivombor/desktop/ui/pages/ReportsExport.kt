package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.report.ExcelExport
import uz.reaktivombor.desktop.report.PdfExport
import uz.reaktivombor.desktop.report.ReportData
import uz.reaktivombor.desktop.report.ReportFiles
import uz.reaktivombor.desktop.report.ReportKind
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.theme.AppColors
import java.nio.file.Path

// Hisobotni faylga saqlash — «Hisobotlar», «Operatsiyalar tarixi» va «Barcha reaktivlar» sahifalari uchun umumiy.

/** Hisobot fayli turi: kengaytmasi, nomi va tugma matni. */
enum class ReportsFileFormat(val extension: String, val title: String, val saveLabel: String) {
    EXCEL("xlsx", "Excel", "Excelga saqlash"),
    PDF("pdf", "PDF", "PDFga saqlash"),
}

/** Saqlash natijasi: tuzilgan hisobot va yozilgan fayl (saqlash oynasi bekor qilinsa — null). */
class ReportsSaveResult(val data: ReportData, val file: Path?)

/**
 * Saqlash oynasi uchun taklif qilinadigan nom: "Kirim-operatsiyalari-2026-10-29.xlsx". Tarix hisobotida — reaktiv nomi
 * bilan: "Etil-spirti-96%-operatsiyalar-tarixi-2026-10-29.xlsx". Windows’da taqiqlangan belgilar olib tashlanadi.
 */
fun reportsFileName(data: ReportData, extension: String): String {
    val reagent = reportsReagentOf(data)
    val base = if (data.kind == ReportKind.HISTORY && !reagent.isNullOrEmpty()) "$reagent operatsiyalar tarixi" else data.title
    return ReportFiles.suggestedFileName(data.copy(title = base), extension).replace(Regex("\\s+"), "-")
}

private const val REAGENT_LINE = "Reaktiv: "

/** Hisobot filtridagi reaktiv nomi ("Reaktiv: …" qatori; tarix hisobotida — topilgan to‘liq nom) yoki null. */
fun reportsReagentOf(data: ReportData): String? =
    data.filterLines.firstOrNull { it.startsWith(REAGENT_LINE) }?.removePrefix(REAGENT_LINE)?.trim()?.ifEmpty { null }

/** Hisobotdagi ma’lumot qatorlari soni (jami qatorlarisiz). */
val ReportData.reportsRowCount: Int get() = rows.count { !it.summary }

/**
 * Hisobotni tuzadi ([build]), saqlash oynasini ochadi va Excel yoki PDF faylni yozadi — hammasi bitta `state.run`
 * ichida (yozish tugmalari band bo‘ladi, xato maydon yonida yoki pastda chiqadi). Muvaffaqiyatda [onBuilt] (masalan,
 * ko‘rib chiqish jadvalini yangilash) va «Papkani ochish» tugmali xabar. Oyna bekor qilinsa — fayl yozilmaydi, xabar yo‘q.
 */
fun reportsExportFile(
    state: AppState,
    format: ReportsFileFormat,
    form: FormErrors? = null,
    onBuilt: (ReportData) -> Unit = {},
    message: (ReportData, Path) -> String = { data, file -> reportsSavedText(data, format, file) },
    build: () -> ReportData,
): Boolean = state.run(
    action = {
        val data = build()
        val target = state.dialogs.save("${format.title} faylini saqlash", reportsFileName(data, format.extension), format.extension)
        val file = target?.let { path ->
            when (format) {
                ReportsFileFormat.EXCEL -> ExcelExport.write(data, path)
                ReportsFileFormat.PDF -> PdfExport.write(data, path)
            }
        }
        ReportsSaveResult(data, file)
    },
    form = form,
    refresh = false,
) { result ->
    onBuilt(result.data)
    val file = result.file
    if (file != null) {
        state.success(message(result.data, file), actionLabel = "Papkani ochish") {
            file.parent?.let { state.dialogs.openFolder(it) }
        }
    }
}

/** "«Kirim operatsiyalari» Excel fayliga saqlandi (12 ta qator): Kirim-operatsiyalari-2026-10-29.xlsx". */
fun reportsSavedText(data: ReportData, format: ReportsFileFormat, file: Path): String =
    "«${data.title}» ${format.title} fayliga saqlandi (${data.reportsRowCount} ta qator): ${file.fileName}"

/** Ikki tugma: «Excelga saqlash» va «PDFga saqlash» (yoki qisqa: «Excel», «PDF» — [compact]). */
@Composable
fun RowScope.ReportsSaveButtons(
    onSave: (ReportsFileFormat) -> Unit,
    enabled: Boolean,
    testTagPrefix: String,
    compact: Boolean = false,
) {
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Eksport:", fontSize = 13.sp, color = AppColors.Muted, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
    }
    ReportsFileFormat.entries.forEach { format ->
        AppButton(
            if (compact) format.title else format.saveLabel,
            onClick = { onSave(format) },
            kind = ButtonKind.SECONDARY,
            icon = if (format == ReportsFileFormat.EXCEL) AppIcons.Excel else AppIcons.Document,
            enabled = enabled,
            modifier = Modifier.testTag("$testTagPrefix-${format.name.lowercase()}"),
        )
    }
}

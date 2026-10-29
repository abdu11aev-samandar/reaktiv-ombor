package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.service.ImportResult
import uz.reaktivombor.desktop.service.ImportRow
import uz.reaktivombor.desktop.service.Place
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.Banner
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.FormSection
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TooltipBox
import uz.reaktivombor.desktop.ui.components.rememberTableSelection
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import java.nio.file.Path

/** Tekshirilgan fayl va uning qatorlari (ko‘rib chiqish jadvali uchun). */
@Immutable
private class AddReagentImportPreview(val file: Path, val rows: List<ImportRow>) {
    val valid: Int = rows.count { it.valid }
    /** Shablondagi namuna qator(lar) — xato emas, shunchaki import qilinmaydi. */
    val examples: Int = rows.count { it.input == null }
    val invalid: Int = rows.size - valid - examples
    val warned: Int = rows.count { it.valid && it.warnings.isNotEmpty() }
}

/** Oxirgi muvaffaqiyatli import. */
@Immutable
private class AddReagentImportDone(val file: Path, val result: ImportResult)

/** Ko‘rib chiqish jadvalining ustunlar ko‘rinishi shu nom bilan saqlanadi. */
private const val IMPORT_TABLE_ID = "excel-import-preview"

/**
 * «Reaktiv qo‘shish» sahifasidagi «Exceldan import»: shablonni yuklab olish → to‘ldirilgan faylni tanlash → ko‘rib chiqish
 * (har qatorning xato va ogohlantirishlari, bazaga hech narsa yozilmaydi) → «Import qilish» (xatosiz qatorlar bitta
 * tranzaksiyada: birortasi saqlanmasa — hech biri saqlanmaydi). Xatoli qator bo‘lsa — avval tasdiq so‘raladi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AddReagentExcelImport(state: AppState, confirm: ConfirmState, modifier: Modifier = Modifier) {
    val services = state.services
    var preview by remember { mutableStateOf<AddReagentImportPreview?>(null) }
    var done by remember { mutableStateOf<AddReagentImportDone?>(null) }
    var onlyErrors by remember { mutableStateOf(false) }
    val selection = rememberTableSelection()
    val previewView = remember { BringIntoViewRequester() }
    val departments = remember(state.refreshTick) {
        state.load(emptyList()) { services.directory.departments(includeInactive = true) }.associateBy { it.id }
    }

    fun downloadTemplate() {
        val target = state.dialogs.save("Import shablonini saqlash", "reaktivlar-import-shabloni.xlsx", "xlsx") ?: return
        state.run({ services.excelImport.template(target) }, refresh = false) { path ->
            state.success("Shablon saqlandi: ${path.fileName}. Excelda to‘ldirib, «Faylni tanlash» orqali yuklang.", "Papkani ochish") {
                state.dialogs.openFolder(path.toAbsolutePath().parent)
            }
        }
    }

    fun check(file: Path) {
        state.run(
            { services.excelImport.preview(file) },
            refresh = false,
            onError = { e ->
                state.showMessage("${e.message}\nBazaga hech narsa yozilmadi.", Severity.ERROR, title = "Faylni o‘qib bo‘lmadi", key = "excel-import-error")
                true
            },
        ) { rows ->
            preview = AddReagentImportPreview(file, rows)
            done = null
            onlyErrors = false
            selection.key = null
        }
    }

    fun pickFile() {
        val file = state.dialogs.open("Excel faylni tanlang (.xlsx)", listOf("xlsx")) ?: return
        check(file)
    }

    fun runImport(p: AddReagentImportPreview) {
        state.run(
            { services.excelImport.import(p.rows, p.file) },
            onError = { e ->
                state.showMessage(e.message, Severity.ERROR, title = "Import qilinmadi", key = "excel-import-error")
                true
            },
        ) { result ->
            preview = null
            done = AddReagentImportDone(p.file, result)
            state.success("Exceldan ${result.created} ta partiya qo‘shildi.", "Ro‘yxatda ko‘rish") { state.navigate(Page.REAGENTS) }
        }
    }

    fun requestImport(p: AddReagentImportPreview) {
        if (p.invalid == 0) {
            runImport(p)
            return
        }
        confirm.ask(
            title = "Xatoli qatorlar import qilinmaydi",
            message = "Faylda ${p.invalid} ta qatorda xato bor — ular o‘tkazib yuboriladi. ${p.valid} ta to‘g‘ri qator import qilinadi.\n\n" +
                "Import bitta amalda bajariladi: birorta qator saqlanmasa, hech biri saqlanmaydi. Xatoli qatorlarni faylda tuzatib, " +
                "keyin yana import qilish mumkin — qo‘shilganlari takror qo‘shilmaydi.",
            confirmLabel = "${p.valid} ta qatorni import qilish",
        ) { runImport(p) }
    }

    FormSection(
        "Exceldan import",
        modifier.testTag("excel-import"),
        description = "Ko‘p partiyani birdaniga qo‘shish: shablonni to‘ldiring, faylni tanlang, natijani ko‘rib chiqib import qiling",
        actions = {
            AppButton(
                "Shablonni yuklab olish",
                onClick = { downloadTemplate() },
                kind = ButtonKind.SECONDARY,
                icon = AppIcons.Download,
                enabled = !state.busy,
                modifier = Modifier.testTag("import-template"),
            )
            AppButton("Faylni tanlash…", onClick = { pickFile() }, icon = AppIcons.Excel, enabled = !state.busy, modifier = Modifier.testTag("import-pick"))
        },
    ) {
        done?.let { d ->
            Banner(
                "«${d.file.fileName}» faylidan ${d.result.created} ta partiya qo‘shildi" +
                    if (d.result.skipped > 0) ", ${d.result.skipped} ta qator o‘tkazib yuborildi (xatoli yoki namuna qator)." else ".",
                severity = Severity.SUCCESS,
                title = "Import qilindi",
                actionLabel = "Ro‘yxatda ko‘rish",
                onAction = { state.navigate(Page.REAGENTS) },
                onDismiss = { done = null },
                testTag = "import-done",
            )
        }
        val p = preview
        if (p == null) {
            AddReagentImportSteps()
        } else {
            LaunchedEffect(p) {
                // Avval jadval joylashsin (bir kadr), so‘ng ko‘rib chiqish qismi ekranga aylantiriladi.
                withFrameNanos { }
                runCatching { previewView.bringIntoView() }
            }
            Column(Modifier.bringIntoViewRequester(previewView), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AddReagentImportHeader(
                    preview = p,
                    onlyErrors = onlyErrors,
                    onOnlyErrors = { onlyErrors = it },
                    busy = state.busy,
                    onRecheck = { check(p.file) },
                    onCancel = { preview = null },
                    onImport = { requestImport(p) },
                )
                val rows = if (onlyErrors) p.rows.filter { !it.valid } else p.rows
                val columns = remember(departments) { importColumns(departments) }
                val layout = rememberAppTableLayout(state, IMPORT_TABLE_ID, columns)
                DataTable(
                    rows = rows,
                    columns = columns,
                    rowKey = { it.rowNumber },
                    layout = layout,
                    modifier = Modifier.fillMaxWidth().height((40 + ImportRowHeight.value.toInt() * rows.size.coerceIn(2, 8) + 14).dp),
                    rowBackground = { row ->
                        when {
                            row.input == null -> AppColors.SurfaceAlt
                            !row.valid -> StatusStyles.RED.background
                            row.warnings.isNotEmpty() -> StatusStyles.YELLOW.background
                            else -> null
                        }
                    },
                    selection = selection,
                    rowHeight = ImportRowHeight,
                    emptyText = "Faylda ma’lumotli qator topilmadi",
                    emptyHint = "Shablonning «Reaktivlar» varag‘iga har bir partiyani alohida qatorga yozing.",
                    testTag = "import-preview",
                )
                val selected = p.rows.firstOrNull { it.rowNumber == selection.key }
                if (selected != null && (selected.errors.isNotEmpty() || selected.warnings.isNotEmpty())) {
                    AddReagentImportDetails(selected)
                } else if (p.rows.any { it.errors.isNotEmpty() || it.warnings.isNotEmpty() }) {
                    BackupNote("Qatorni tanlang — uning barcha xato va ogohlantirishlari shu yerda to‘liq ko‘rinadi.")
                }
                BackupNote(
                    "Import — hammasi yoki hech biri: xatosiz qatorlar bitta amalda saqlanadi, xatoli qatorlar o‘tkazib yuboriladi. " +
                        "Faylni Excelda tuzatgan bo‘lsangiz — «Qayta tekshirish».",
                )
            }
        }
    }
}

private val ImportRowHeight = 44.dp

/** Hali fayl tanlanmagan: to‘rt qadam. */
@Composable
private fun AddReagentImportSteps() {
    val steps = listOf(
        "«Shablonni yuklab olish» — ustunlar va to‘ldirish yo‘riqnomasi bilan Excel fayl",
        "Har bir partiyani alohida qatorga yozing (* — majburiy ustunlar), namuna qatorni o‘chiring",
        "«Faylni tanlash» — fayl tekshiriladi, har qatordagi xatolar ko‘rsatiladi; bazaga hali hech narsa yozilmaydi",
        "«Import qilish» — xatosiz qatorlar bitta amalda yangi partiya bo‘lib qo‘shiladi",
    )
    Row(Modifier.fillMaxWidth().testTag("import-steps"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { index, text ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(AppColors.PrimarySoft), contentAlignment = Alignment.Center) {
                    Text((index + 1).toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                }
                Spacer(Modifier.width(8.dp))
                Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, color = AppColors.Text)
            }
        }
    }
}

/** Fayl nomi, sonlar (to‘g‘ri / xato / ogohlantirish) va tugmalar. */
@Composable
private fun AddReagentImportHeader(
    preview: AddReagentImportPreview,
    onlyErrors: Boolean,
    onOnlyErrors: (Boolean) -> Unit,
    busy: Boolean,
    onRecheck: () -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Excel, contentDescription = null, tint = StatusStyles.GREEN.foreground, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                preview.file.fileName.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildAnnotatedString {
                    append("${preview.rows.size} ta qator: ")
                    withStyle(SpanStyle(color = StatusStyles.GREEN.foreground, fontWeight = FontWeight.SemiBold)) { append("${preview.valid} ta to‘g‘ri") }
                    append(", ")
                    withStyle(SpanStyle(color = if (preview.invalid > 0) AppColors.Error else AppColors.Muted, fontWeight = FontWeight.SemiBold)) {
                        append("${preview.invalid} ta xato")
                    }
                    if (preview.examples > 0) {
                        append(" · ")
                        withStyle(SpanStyle(color = AppColors.Muted, fontWeight = FontWeight.SemiBold)) {
                            append("${preview.examples} ta namuna qator (import qilinmaydi)")
                        }
                    }
                    if (preview.warned > 0) {
                        append(" · ")
                        withStyle(SpanStyle(color = AppColors.Warning, fontWeight = FontWeight.SemiBold)) { append("${preview.warned} ta ogohlantirish") }
                    }
                },
                fontSize = 13.sp,
                color = AppColors.Muted,
                modifier = Modifier.testTag("import-counts"),
            )
        }
        if (preview.invalid > 0) {
            CheckField(onlyErrors, onOnlyErrors, "Faqat xatoli qatorlar", topPadding = 0.dp, testTag = "import-only-errors")
            Spacer(Modifier.width(4.dp))
        }
        AppButton("Qayta tekshirish", onClick = onRecheck, kind = ButtonKind.TEXT, icon = AppIcons.Refresh, enabled = !busy)
        AppButton("Bekor qilish", onClick = onCancel, kind = ButtonKind.TEXT, icon = AppIcons.Close, enabled = !busy)
        Spacer(Modifier.width(6.dp))
        AppButton(
            if (preview.valid > 0) "Import qilish (${preview.valid} ta)" else "Import qilish",
            onClick = onImport,
            icon = AppIcons.Check,
            enabled = !busy && preview.valid > 0,
            modifier = Modifier.testTag("import-run"),
        )
    }
}

/** Tanlangan qatorning hamma xato va ogohlantirishlari (jadvalda ikki qatorga sig‘maganlari ham). */
@Composable
private fun AddReagentImportDetails(row: ImportRow) {
    Surface(
        Modifier.fillMaxWidth().testTag("import-details"),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.SurfaceAlt,
        border = BorderStroke(1.dp, AppColors.Divider),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${row.rowNumber}-qator" + (row.input?.name?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Text,
            )
            row.errors.forEach { Text("• $it", fontSize = 12.5.sp, lineHeight = 17.sp, color = AppColors.Error) }
            row.warnings.forEach { Text("• $it", fontSize = 12.5.sp, lineHeight = 17.sp, color = AppColors.Warning) }
        }
    }
}

private fun importProblems(row: ImportRow): String = (row.errors + row.warnings).joinToString("; ")

/** Fayldagi miqdor partiya birligida, formatlangan ("2,5"); o‘qib bo‘lmasa — yozilganicha. */
private fun importQty(row: ImportRow): String {
    val input = row.input ?: return "—"
    val unit = input.unit
    val formatted = unit?.let { u -> runCatching { Qty.format(Qty.parse(input.initialQtyText, u), u) }.getOrNull() }
    return formatted ?: input.initialQtyText.ifBlank { "—" }
}

private fun importDepartment(row: ImportRow, departments: Map<Long, Place>): String {
    val input = row.input ?: return "—"
    input.departmentId?.let { id -> departments[id]?.let { return it.name } }
    row.departmentName?.let { return "$it (yangi)" }
    return "—"
}

@OptIn(ExperimentalFoundationApi::class)
private fun importColumns(departments: Map<Long, Place>): List<TableColumn<ImportRow>> = listOf(
    textColumn("row", "Qator №", 64.dp, ColumnAlign.END, sortKey = { it.rowNumber }) { it.rowNumber.toString() },
    TableColumn(
        id = "name", title = "Reaktiv nomi", width = 170.dp,
        text = { it.input?.name ?: "Namuna qator" },
        cell = { row ->
            Text(
                row.input?.name?.ifBlank { "—" } ?: "Namuna qator",
                fontSize = 13.sp,
                color = if (row.input == null) AppColors.Muted else AppColors.Text,
                fontWeight = if (row.input == null) FontWeight.Normal else FontWeight.Medium,
                maxLines = 2,
                lineHeight = 16.sp,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ),
    textColumn("series", "Seriya", 90.dp) { it.input?.series?.ifBlank { "—" } ?: "—" },
    textColumn("qty", "Miqdor", 80.dp, ColumnAlign.END) { importQty(it) },
    textColumn("unit", "Birlik", 60.dp) { it.input?.unit?.code ?: "—" },
    textColumn("expires", "Yaroqlilik muddati", 110.dp, ColumnAlign.END, sortKey = { it.input?.expiresOn }) { dateText(it.input?.expiresOn) },
    TableColumn(
        id = "department", title = "Bo‘lim", width = 150.dp,
        text = { importDepartment(it, departments) },
        cell = { row ->
            Text(importDepartment(row, departments), fontSize = 13.sp, lineHeight = 16.sp, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
    ),
    TableColumn(
        id = "problems", title = "Xatolar / ogohlantirishlar", width = 290.dp, minWidth = 120.dp,
        text = { importProblems(it) },
        cell = { row ->
            when {
                row.errors.isEmpty() && row.warnings.isEmpty() ->
                    StatusChip("To‘g‘ri", StatusStyles.GREEN.background, StatusStyles.GREEN.foreground, dot = StatusStyles.GREEN.accent)
                else -> {
                    val full = (row.errors + row.warnings).joinToString("\n") { "• $it" }
                    TooltipArea(
                        tooltip = { TooltipBox(full) },
                        delayMillis = 400,
                        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
                    ) {
                        Text(
                            importProblems(row),
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = when {
                                row.input == null -> AppColors.Muted
                                row.errors.isNotEmpty() -> AppColors.Error
                                else -> AppColors.Warning
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ),
)

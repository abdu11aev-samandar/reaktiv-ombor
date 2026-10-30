package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.service.BackupCheck
import uz.reaktivombor.desktop.service.BackupFile
import uz.reaktivombor.desktop.service.BackupKind
import uz.reaktivombor.desktop.service.BackupQueries
import uz.reaktivombor.desktop.service.DatabaseInfo
import uz.reaktivombor.desktop.service.LegacyResult
import uz.reaktivombor.desktop.service.Settings
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.Banner
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ColumnAlign
import uz.reaktivombor.desktop.ui.components.DataTable
import uz.reaktivombor.desktop.ui.components.RowAction
import uz.reaktivombor.desktop.ui.components.StatusChip
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableSort
import uz.reaktivombor.desktop.ui.components.actionsColumn
import uz.reaktivombor.desktop.ui.components.textColumn
import uz.reaktivombor.desktop.ui.rememberAppTableLayout
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.StatusStyles
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tekshiruvdan o‘tgan, tiklash uchun tanlangan fayl (tasdiq oynasi uchun). */
@Immutable
private class BackupRestoreRequest(val path: Path, val check: BackupCheck, val size: Long, val modifiedAt: LocalDateTime?, val kind: BackupKind?)

/** Shu sahifada yaratilgan oxirgi nusxa. */
@Immutable
private class BackupCreated(val path: Path, val size: Long)

/** Oxirgi tiklash: qaysi fayldan va oldingi holat qaysi faylga saqlangani. */
@Immutable
private class BackupRestored(val source: Path, val preRestore: Path, val check: BackupCheck)

private val BACKUP_FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
private const val BACKUP_ERROR_KEY = "backup-page-error"

/** Oxirgi ko‘chirishning barcha izohlari (app_settings, har qatorda bittadan) — ishga tushishdagi xabar yopilgach ham ko‘rinsin. */
private const val LEGACY_NOTES_SETTING = "legacy_import_notes"

/** Zaxira nusxa turi — jadvalda va tasdiq oynasida. */
private val BackupKind.title: String
    get() = when (this) {
        BackupKind.AUTO -> "Avtomatik"
        BackupKind.PRE_RESTORE -> "Tiklashdan oldin"
        BackupKind.MANUAL -> "Qo‘lda"
    }

@Composable
private fun BackupKindChip(kind: BackupKind) {
    when (kind) {
        BackupKind.AUTO -> StatusChip(kind.title, AppColors.PrimarySoft, AppColors.Primary)
        BackupKind.PRE_RESTORE -> StatusChip(kind.title, StatusStyles.YELLOW.background, StatusStyles.YELLOW.foreground, dot = StatusStyles.YELLOW.accent)
        BackupKind.MANUAL -> StatusChip(kind.title, StatusStyles.GREEN.background, StatusStyles.GREEN.foreground, dot = StatusStyles.GREEN.accent)
    }
}

/**
 * «Ma’lumotlarni zaxiralash» (texnik topshiriq §15): zaxira nusxa yaratish, fayldan yoki ro‘yxatdan tiklash (tekshirish →
 * tasdiq → joriy bazaning avtomatik nusxasi → tiklash), backups papkasidagi nusxalar, ma’lumotlar joylashuvi va eski
 * dasturdan ko‘chirish. Har bir amal state.run orqali (band holati, xato xabari).
 */
@Composable
fun BackupPage(state: AppState) {
    val services = state.services
    val tick = state.refreshTick
    // Avtomatik zaxira fonda olinadi — ro‘yxatni qo‘lda yangilash uchun.
    var reload by remember { mutableIntStateOf(0) }
    val queries = remember { BackupQueries(services) }
    val backups = remember(tick, reload) { state.load(emptyList()) { services.backup.list() } }
    val dbInfo = remember(tick, reload) { state.load(null) { queries.databaseInfo() } }
    val lastAutoSetting = remember(tick, reload) { state.load(null) { services.settings.lastAutoBackup } }
    val lastAuto = backupLastAutoDate(lastAutoSetting, backups)
    val keep = remember(tick) { state.load(Settings.DEFAULT_AUTO_BACKUP_KEEP) { services.settings.autoBackupKeep } }
    val legacyFile = remember(tick) { state.load(null) { services.legacy.findLegacyFile() } }
    val legacyLast = remember(tick) { state.load(null) { queries.lastLegacyImport() } }
    val legacyLastNotes = remember(tick) {
        state.load(emptyList()) { services.settings.get(LEGACY_NOTES_SETTING).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() } }
    }
    // Topilgan fayl allaqachon ko‘chirilgan bo‘lsa — «Topilgan faylni ko‘chirish» ko‘rsatilmaydi (baribir rad etiladi).
    val legacyFileDone = remember(tick, legacyFile) { legacyFile?.let { f -> state.load(false) { queries.legacyAlreadyImported(f) } } ?: false }
    var created by remember { mutableStateOf<BackupCreated?>(null) }
    var restored by remember { mutableStateOf<BackupRestored?>(null) }
    var restoreRequest by remember { mutableStateOf<BackupRestoreRequest?>(null) }
    var legacyResult by remember { mutableStateOf<LegacyResult?>(null) }
    val confirm = rememberConfirmState()
    val backupDir = services.backup.dir

    fun openFolder(dir: Path) = state.dialogs.openFolder(dir)

    fun createBackup() {
        val stamp = LocalDateTime.now(services.clock).format(BACKUP_FILE_STAMP)
        val target = state.dialogs.save("Zaxira nusxani saqlash", "reaktiv-control-$stamp.db", "db") ?: return
        state.run({ services.backup.create(target) }) { path ->
            val size = runCatching { Files.size(path) }.getOrDefault(0L)
            created = BackupCreated(path, size)
            state.success("Zaxira nusxa saqlandi: ${path.fileName} (${backupSizeText(size)}).", "Papkani ochish") {
                openFolder(path.toAbsolutePath().parent)
            }
        }
    }

    /** Tiklashdan oldin: faylni tekshiradi va nimalar borligini tasdiq oynasida ko‘rsatadi. Hali hech narsa o‘zgarmaydi. */
    fun checkBeforeRestore(path: Path, listed: BackupFile? = null) {
        if (dbInfo != null && runCatching { path.toAbsolutePath().normalize() == dbInfo.path.toAbsolutePath().normalize() }.getOrDefault(false)) {
            state.showMessage(
                "Bu fayl — dastur hozir ishlatayotgan bazaning o‘zi. Boshqa zaxira faylini tanlang. Joriy ma’lumotlar o‘zgartirilmadi.",
                Severity.ERROR, title = "Bu fayldan tiklab bo‘lmaydi", key = BACKUP_ERROR_KEY,
            )
            return
        }
        state.run(
            { services.backup.validate(path) },
            refresh = false,
            onError = { e ->
                state.showMessage("${e.message}\nJoriy ma’lumotlar o‘zgartirilmadi.", Severity.ERROR, title = "Bu fayldan tiklab bo‘lmaydi", key = BACKUP_ERROR_KEY)
                true
            },
        ) { check ->
            val size = listed?.size ?: runCatching { Files.size(path) }.getOrDefault(0L)
            val modified = listed?.modifiedAt ?: runCatching {
                LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault())
            }.getOrNull()
            restoreRequest = BackupRestoreRequest(path, check, size, modified, listed?.kind)
        }
    }

    fun pickAndRestore() {
        val path = state.dialogs.open("Zaxira nusxani tanlang", listOf("db")) ?: return
        checkBeforeRestore(path)
    }

    fun restore(request: BackupRestoreRequest) {
        restoreRequest = null
        state.run(
            { services.backup.restore(request.path) },
            onError = { e ->
                // Xizmat xatosi (tekshiruv, oldingi holat nusxasi, almashtirish) — hammasida joriy baza joyida qoladi.
                val text = e.message.let { if ("joyida qoldi" in it) it else "$it\nJoriy ma’lumotlar o‘zgartirilmadi." }
                state.showMessage(text, Severity.ERROR, title = "Zaxiradan tiklanmadi", key = BACKUP_ERROR_KEY)
                true
            },
        ) { result ->
            restored = BackupRestored(request.path, result.preRestoreBackup, result.check)
            state.showMessage(
                "«${request.path.fileName}» dagi ma’lumotlar tiklandi. Tiklashdan oldingi holat saqlandi: " +
                    "${result.preRestoreBackup.fileName} (backups papkasida).",
                Severity.SUCCESS,
                title = "Ma’lumotlar tiklandi",
                actionLabel = "Papkani ochish",
                onAction = { openFolder(backupDir) },
                autoHide = false,
            )
        }
    }

    fun importLegacy(path: Path) {
        confirm.ask(
            title = "Eski dasturdan ko‘chirish",
            message = "«${path.fileName}» faylidagi reaktivlar joriy bazaga qo‘shiladi; mavjud ma’lumotlar o‘chirilmaydi, eski fayl " +
                "o‘zgartirilmaydi.\n\nKo‘chirish bitta amalda bajariladi: xato bo‘lsa hech narsa qo‘shilmaydi. Bir fayl ikki marta ko‘chirilmaydi.",
            confirmLabel = "Ko‘chirish",
        ) {
            state.run(
                { services.legacy.importFile(path) },
                onError = { e ->
                    state.showMessage("${e.message}\nBazaga hech narsa qo‘shilmadi.", Severity.ERROR, title = "Ko‘chirilmadi", key = BACKUP_ERROR_KEY)
                    true
                },
            ) { result ->
                legacyResult = result
                state.success(
                    "Eski dasturdan ${result.imported} ta partiya ko‘chirildi" +
                        if (result.skipped > 0) ", ${result.skipped} ta qator o‘tkazib yuborildi." else ".",
                )
            }
        }
    }

    fun pickLegacy() {
        val path = state.dialogs.open("Eski dastur faylini tanlang (.json)", listOf("json")) ?: return
        importLegacy(path)
    }

    ScrollPage(
        title = Page.BACKUP.title,
        subtitle = "Zaxira nusxa, tiklash va eski dasturdan ko‘chirish. Hamma narsa shu kompyuterda — hech qayerga yuborilmaydi.",
        actions = {
            AppButton("Papkani ochish", onClick = { openFolder(backupDir) }, kind = ButtonKind.SECONDARY, icon = AppIcons.Folder)
        },
        testTag = "backup-page",
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackupCard(
                "Zaxira nusxa yaratish",
                Modifier.weight(1f).fillMaxHeight(),
                description = "Butun bazaning to‘liq nusxasi bitta .db faylga yoziladi",
                testTag = "backup-create-card",
            ) {
                BackupNote(
                    "Nusxani flesh-disk yoki boshqa kompyuterga ham saqlab qo‘ying: kompyuter buzilsa, ma’lumotlar shu fayldan tiklanadi. " +
                        "Yozilgan nusxa darhol tekshiriladi.",
                )
                created?.let { c ->
                    Banner(
                        "${c.path.fileName} · ${backupSizeText(c.size)}\n${c.path.toAbsolutePath().parent ?: ""}",
                        severity = Severity.SUCCESS,
                        title = "Zaxira nusxa saqlandi",
                        actionLabel = "Papkani ochish",
                        onAction = { openFolder(c.path.toAbsolutePath().parent) },
                        onDismiss = { created = null },
                        testTag = "backup-created",
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppButton(
                        "Zaxira nusxa yaratish…",
                        onClick = { createBackup() },
                        icon = AppIcons.Download,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("backup-create"),
                    )
                }
            }
            BackupCard(
                "Zaxiradan tiklash",
                Modifier.weight(1f).fillMaxHeight(),
                description = "Tanlangan nusxadagi ma’lumotlar joriy ma’lumotlar o‘rniga qo‘yiladi",
                testTag = "backup-restore-card",
            ) {
                BackupNote(
                    "Avval fayl tekshiriladi va unda nimalar borligi ko‘rsatiladi. Tasdiqlangandan keyin joriy ma’lumotlar avtomatik " +
                        "ravishda backups papkasiga saqlanadi, so‘ng tiklanadi — kerak bo‘lsa oldingi holatga qaytish mumkin.",
                )
                restored?.let { r ->
                    Banner(
                        "${r.source.fileName}: ${r.check.reagents} ta reaktiv, ${r.check.batches} ta partiya. " +
                            "Oldingi holat: ${r.preRestore.fileName}",
                        severity = Severity.SUCCESS,
                        title = "Ma’lumotlar tiklandi",
                        actionLabel = "Papkani ochish",
                        onAction = { openFolder(backupDir) },
                        onDismiss = { restored = null },
                        testTag = "backup-restored",
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppButton(
                        "Fayldan tiklash…",
                        onClick = { pickAndRestore() },
                        kind = ButtonKind.SECONDARY,
                        icon = AppIcons.Upload,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("backup-restore-file"),
                    )
                }
            }
        }

        BackupList(
            state = state,
            backups = backups,
            busy = state.busy,
            onRestore = { checkBeforeRestore(it.path, it) },
            onOpenFolder = { openFolder(backupDir) },
            onReload = { reload++ },
            folder = backupDir,
        )

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackupLocations(
                state = state,
                dbInfo = dbInfo,
                lastAuto = lastAuto?.let { dateText(it) },
                keep = keep,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            BackupLegacyCard(
                busy = state.busy,
                legacyFile = legacyFile,
                legacyFileDone = legacyFileDone,
                lastImport = legacyLast?.let { info ->
                    listOfNotNull(info.at?.let { dateTimeText(it) }, info.fileName.ifBlank { null }).joinToString(" — ")
                },
                lastNotes = legacyLastNotes,
                result = legacyResult,
                onPick = { pickLegacy() },
                onImportFound = { legacyFile?.let { importLegacy(it) } },
                onDismissResult = { legacyResult = null },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    ConfirmHost(confirm, state.busy)
    restoreRequest?.let { request ->
        BackupRestoreDialog(
            request = request,
            current = dbInfo,
            busy = state.busy,
            onConfirm = { restore(request) },
            onDismiss = { restoreRequest = null },
        )
    }
}

/** backups papkasidagi nusxalar: turi, vaqti, nomi, hajmi va «Tiklash». */
@Composable
private fun BackupList(
    state: AppState,
    backups: List<BackupFile>,
    busy: Boolean,
    onRestore: (BackupFile) -> Unit,
    onOpenFolder: () -> Unit,
    onReload: () -> Unit,
    folder: Path,
) {
    val columns = remember {
        listOf<TableColumn<BackupFile>>(
            TableColumn(id = "kind", title = "Turi", width = 160.dp, text = { it.kind.title }, cell = { BackupKindChip(it.kind) }),
            textColumn("time", "Sana va vaqt", 140.dp, ColumnAlign.END, sortKey = { it.modifiedAt }) { dateTimeText(it.modifiedAt) },
            textColumn("name", "Fayl nomi", 330.dp) { it.path.fileName.toString() },
            textColumn("size", "Hajmi", 100.dp, ColumnAlign.END, sortKey = { it.size }) { backupSizeText(it.size) },
        )
    }
    val actions = actionsColumn<BackupFile>(width = 110.dp, maxInline = 1) { file ->
        listOf(RowAction("Tiklash", enabled = !busy) { onRestore(file) })
    }
    val all = columns + actions
    val layout = rememberAppTableLayout(state, "backups", all, TableSort("time", descending = true))
    val counts = backups.groupingBy { it.kind }.eachCount()
    BackupCard(
        "Zaxira nusxalar",
        description = "$folder — " + if (backups.isEmpty()) {
            "hali nusxa yo‘q"
        } else {
            "${backups.size} ta: " + BackupKind.entries.filter { (counts[it] ?: 0) > 0 }.joinToString(", ") { "${it.title.lowercase()} ${counts[it]}" }
        },
        actions = {
            AppButton("Yangilash", onClick = onReload, kind = ButtonKind.TEXT, icon = AppIcons.Refresh)
            AppButton("Papkani ochish", onClick = onOpenFolder, kind = ButtonKind.TEXT, icon = AppIcons.Folder)
        },
        testTag = "backup-list-card",
    ) {
        if (backups.isEmpty()) {
            BackupEmptyList()
        } else {
            DataTable(
                rows = backups,
                columns = all,
                rowKey = { it.path.fileName.toString() },
                layout = layout,
                modifier = Modifier.fillMaxWidth().height((40 + 34 * backups.size.coerceIn(2, 7) + 14).dp),
                testTag = "backups",
            )
        }
        BackupNote(
            "«Avtomatik» — har kuni dastur ochilganda olinadi; «Tiklashdan oldin» — tiklashdan oldingi holat; «Qo‘lda» — shu papkaga " +
                "qo‘lda saqlangan nusxa. Boshqa joyga saqlangan nusxadan tiklash uchun «Fayldan tiklash…» tugmasini bosing.",
        )
    }
}

/** Papkada hali nusxa yo‘q (jadval o‘rnida ixcham izoh). */
@Composable
private fun BackupEmptyList() {
    Surface(
        Modifier.fillMaxWidth().testTag("backups-empty"),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.SurfaceAlt,
        border = BorderStroke(1.dp, AppColors.Divider),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Inbox, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Hali zaxira nusxa yo‘q", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
                Text(
                    "Dastur har kuni birinchi ochilganda bu papkaga avtomatik nusxa oladi. Hozir nusxa olish — «Zaxira nusxa yaratish…».",
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = AppColors.Muted,
                )
            }
        }
    }
}

/** Ma’lumotlar, baza va jurnallar joylashuvi + avtomatik zaxira holati. */
@Composable
private fun BackupLocations(state: AppState, dbInfo: DatabaseInfo?, lastAuto: String?, keep: Int, modifier: Modifier) {
    val dataDir = state.services.dataDir.toAbsolutePath()
    BackupCard("Ma’lumotlar joylashuvi", modifier, description = "Barcha ma’lumotlar faqat shu kompyuterda", testTag = "backup-locations") {
        BackupInfoLine("Ma’lumotlar papkasi", dataDir.toString(), onOpen = { state.dialogs.openFolder(dataDir) }, testTag = "backup-data-dir")
        BackupInfoLine(
            "Baza fayli" + (dbInfo?.let { " · ${backupSizeText(it.size)}" } ?: ""),
            (dbInfo?.path ?: DataDir.dbFile(dataDir)).toString(),
            testTag = "backup-db-file",
        )
        val logs = DataDir.logsDir(dataDir)
        BackupInfoLine("Jurnallar papkasi (xatolar tafsiloti)", logs.toString(), onOpen = { state.dialogs.openFolder(logs) }, testTag = "backup-logs-dir")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Avtomatik zaxira", fontSize = 11.5.sp, lineHeight = 15.sp, color = AppColors.Muted)
                Text(
                    "Oxirgisi: ${lastAuto ?: "hali olinmagan"} · eng yangi $keep tasi saqlanadi",
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Text,
                    modifier = Modifier.testTag("backup-auto-info"),
                )
            }
            Spacer(Modifier.width(8.dp))
            AppButton("Sozlamalar", onClick = { state.navigate(Page.SETTINGS) }, kind = ButtonKind.TEXT, icon = AppIcons.Settings)
        }
    }
}

/** Eski «Reaktivlar» dasturining JSON faylidan ko‘chirish. */
@Composable
private fun BackupLegacyCard(
    busy: Boolean,
    legacyFile: Path?,
    legacyFileDone: Boolean,
    lastImport: String?,
    result: LegacyResult?,
    onPick: () -> Unit,
    onImportFound: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier,
    lastNotes: List<String> = emptyList(),
) {
    var showLastNotes by remember { mutableStateOf(false) }
    BackupCard(
        "Eski dasturdan ko‘chirish",
        modifier,
        description = "Eski «Reaktivlar» dasturining fayli (reaktivlar-jadval.json, reaktivlar.json)",
        testTag = "backup-legacy",
    ) {
        BackupNote(
            "Fayldagi reaktivlar yangi bazaga partiya sifatida qo‘shiladi; eski fayl o‘zgartirilmaydi. Bir fayl ikki marta " +
                "ko‘chirilmaydi — qayta tanlansa, rad etiladi.",
        )
        if (lastImport != null) {
            // Izohlar — shu yerda ham (ishga tushishdagi xabar yopilgandan keyin). Hozirgina ko‘chirilgan bo‘lsa — natijada bor.
            val notesHere = lastNotes.isNotEmpty() && result == null
            BackupInfoLine(
                "Oxirgi ko‘chirish",
                lastImport,
                openLabel = "Izohlar (${lastNotes.size} ta)",
                openIcon = AppIcons.Document,
                onOpen = if (notesHere) ({ showLastNotes = true }) else null,
                testTag = "backup-legacy-last",
                openTestTag = "backup-legacy-last-notes",
            )
        }
        if (legacyFile != null) {
            BackupInfoLine(
                "Shu kompyuterda topilgan eski fayl" + if (legacyFileDone) " — allaqachon ko‘chirilgan" else "",
                legacyFile.toString(),
                testTag = "backup-legacy-found",
            )
        }
        result?.let { BackupLegacyResult(it, onDismissResult) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (legacyFile != null && !legacyFileDone) {
                AppButton("Topilgan faylni ko‘chirish", onClick = onImportFound, kind = ButtonKind.TEXT, enabled = !busy, modifier = Modifier.testTag("backup-legacy-found-import"))
            }
            AppButton("JSON faylni tanlash…", onClick = onPick, kind = ButtonKind.SECONDARY, icon = AppIcons.Upload, enabled = !busy, modifier = Modifier.testTag("backup-legacy-pick"))
        }
    }
    if (showLastNotes) {
        LegacyNotesDialog("${lastImport.orEmpty()} — ${lastNotes.size} ta izoh", lastNotes) { showLastNotes = false }
    }
}

@Composable
private fun BackupLegacyResult(result: LegacyResult, onDismiss: () -> Unit) {
    var showNotes by remember { mutableStateOf(false) }
    val severity = when {
        result.imported == 0 -> Severity.WARNING
        result.skipped > 0 -> Severity.WARNING
        else -> Severity.SUCCESS
    }
    Column(Modifier.testTag("backup-legacy-result"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Banner(
            "Ko‘chirildi: ${result.imported} ta partiya · o‘tkazib yuborildi: ${result.skipped} ta qator" +
                (result.file?.let { "\nFayl: ${it.fileName}" } ?: ""),
            severity = severity,
            title = "Ko‘chirish natijasi",
            onDismiss = onDismiss,
        )
        if (result.notes.isNotEmpty()) {
            // Kartada — birinchi izohlar; hammasi alohida oynada (eski faylda yuzlab qator bo‘lishi mumkin).
            result.notes.take(LEGACY_NOTES_INLINE).forEach { note ->
                Text("• $note", fontSize = 12.5.sp, lineHeight = 17.sp, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AppButton(
                "Barcha izohlar (${result.notes.size} ta)",
                onClick = { showNotes = true },
                kind = ButtonKind.TEXT,
                icon = AppIcons.Document,
                modifier = Modifier.testTag("backup-legacy-notes"),
            )
        }
    }
    if (showNotes) {
        LegacyNotesDialog("${result.file?.fileName ?: ""} — ${result.notes.size} ta izoh", result.notes) { showNotes = false }
    }
}

/** Ko‘chirish izohlarining hammasi — aylantiriladigan ro‘yxat (eski faylda yuzlab qator bo‘lishi mumkin). */
@Composable
private fun LegacyNotesDialog(subtitle: String, notes: List<String>, onDismiss: () -> Unit) {
    AppDialog(
        title = "Ko‘chirish izohlari",
        subtitle = subtitle,
        onDismiss = onDismiss,
        width = 760.dp,
        testTag = "legacy-notes-dialog",
        buttons = { AppButton("Yopish", onClick = onDismiss) },
    ) {
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                notes.forEach { note -> Text("• $note", fontSize = 13.sp, lineHeight = 18.sp, color = AppColors.Text) }
            }
            Box(Modifier.matchParentSize()) {
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

private const val LEGACY_NOTES_INLINE = 3

/** Tiklashni tasdiqlash: faylda nima bor, hozir nima bor va oldin nima bo‘lishi (joriy holat avtomatik saqlanadi). */
@Composable
private fun BackupRestoreDialog(
    request: BackupRestoreRequest,
    current: DatabaseInfo?,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "Zaxiradan tiklash",
        subtitle = request.path.toAbsolutePath().toString(),
        onDismiss = { if (!busy) onDismiss() },
        width = 680.dp,
        testTag = "restore-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY, enabled = !busy, modifier = Modifier.testTag("restore-cancel"))
            AppButton("Tiklash", onClick = onConfirm, kind = ButtonKind.DANGER, icon = AppIcons.Undo, enabled = !busy, modifier = Modifier.testTag("restore-confirm"))
        },
    ) {
        Banner(
            "Hozirgi ma’lumotlar zaxiradagilar bilan almashtiriladi. Tiklashdan oldin ular avtomatik ravishda backups papkasiga " +
                "«before-restore-…» nomli nusxa sifatida saqlanadi — kerak bo‘lsa o‘sha nusxadan qaytarish mumkin.",
            severity = Severity.WARNING,
            title = "Diqqat",
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackupCountsBox(
                title = "Zaxira faylida",
                lines = listOfNotNull(
                    "Reaktivlar" to "${request.check.reagents} ta",
                    "Partiyalar" to "${request.check.batches} ta",
                    "Ombor harakatlari" to "${request.check.movements} ta",
                    "Baza versiyasi" to request.check.userVersion.toString(),
                    request.kind?.let { "Turi" to it.title },
                    request.modifiedAt?.let { "Sana" to dateTimeText(it) },
                    "Hajmi" to backupSizeText(request.size),
                ),
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("restore-file-counts"),
            )
            BackupCountsBox(
                title = "Hozir dasturda",
                lines = if (current == null) {
                    listOf("Holati" to "o‘qib bo‘lmadi")
                } else {
                    listOf(
                        "Reaktivlar" to "${current.reagents} ta",
                        "Partiyalar" to "${current.batches} ta",
                        "Ombor harakatlari" to "${current.movements} ta",
                        "Baza versiyasi" to current.userVersion.toString(),
                        "Hajmi" to backupSizeText(current.size),
                    )
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Text(
            "Zaxira olingandan keyin kiritilgan operatsiyalar tiklangan bazada bo‘lmaydi. Tiklash muvaffaqiyatsiz bo‘lsa, " +
                "joriy ma’lumotlar o‘zgarmaydi.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = AppColors.Text,
        )
    }
}

@Composable
private fun BackupCountsBox(title: String, lines: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = AppColors.SurfaceAlt, border = BorderStroke(1.dp, AppColors.Divider)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
            lines.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 12.5.sp, color = AppColors.Muted, modifier = Modifier.width(130.dp))
                    Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.Text)
                }
            }
        }
    }
}

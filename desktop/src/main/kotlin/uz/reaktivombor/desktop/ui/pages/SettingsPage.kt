package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.cleanName
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.GREEN_AFTER_DAYS
import uz.reaktivombor.desktop.core.RED_UNTIL_DAYS
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.service.BackupKind
import uz.reaktivombor.desktop.service.BackupQueries
import uz.reaktivombor.desktop.service.Settings
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.theme.AppColors

/**
 * «Sozlamalar» (texnik topshiriq §16): tashkilot nomi (hisobot sarlavhasi), standart xodim ismi (formalar va audit),
 * avtomatik zaxiralar soni, yaroqlilik chegaralari (o‘zgarmas, faqat ko‘rsatiladi), jadval ustunlarini qaytarish va
 * dastur haqida. Har bo‘lim o‘z «Saqlash» tugmasi bilan; noto‘g‘ri qiymat — xatosi maydon yonida.
 */
@Composable
fun SettingsPage(state: AppState) {
    val services = state.services
    val settings = services.settings
    val tick = state.refreshTick
    val queries = remember { BackupQueries(services) }
    val savedOrg = remember(tick) { state.load("") { settings.orgName } }
    val savedActor = remember(tick) { state.load("") { settings.actorName } }
    val savedKeep = remember(tick) { state.load(Settings.DEFAULT_AUTO_BACKUP_KEEP) { settings.autoBackupKeep } }
    val backups = remember(tick) { state.load(emptyList()) { services.backup.list() } }
    val lastAuto = backupLastAutoDate(remember(tick) { state.load(null) { settings.lastAutoBackup } }, backups)
    val autoCount = backups.count { it.kind == BackupKind.AUTO }
    val layoutCount = remember(tick) { state.load(0) { queries.savedTableLayouts() } }
    val dbInfo = remember(tick) { state.load(null) { queries.databaseInfo() } }

    var org by remember { mutableStateOf(savedOrg) }
    var actor by remember { mutableStateOf(savedActor) }
    var keepText by remember { mutableStateOf(savedKeep.toString()) }
    // Saqlangandan keyin tugma yonida «Saqlandi» (pastdagi xabar bir necha soniyada yopiladi).
    var peopleSaved by remember { mutableStateOf(false) }
    var keepSaved by remember { mutableStateOf(false) }
    val peopleErrors = remember { FormErrors() }
    val keepErrors = remember { FormErrors() }
    val confirm = rememberConfirmState()

    fun savePeople() {
        state.run(
            {
                // Ikkala maydon ham tekshiriladi (xatolar birga ko‘rsatiladi); bittasi xato bo‘lsa — hech biri saqlanmaydi.
                services.db.write {
                    val errors = linkedMapOf<String, String>()
                    fun attempt(field: String, block: () -> Unit) {
                        try {
                            block()
                        } catch (e: UserError) {
                            errors[field] = e.fields[field] ?: e.message
                        }
                    }
                    attempt("orgName") { settings.orgName = org }
                    attempt("actorName") { settings.actorName = actor }
                    if (errors.isNotEmpty()) throw UserError(errors.values.joinToString("\n"), errors)
                }
            },
            form = peopleErrors,
        ) {
            org = settings.orgName
            actor = settings.actorName
            peopleSaved = true
            state.success("Tashkilot va xodim ma’lumotlari saqlandi.")
        }
    }

    fun saveKeep() {
        state.run(
            {
                val value = keepText.trim().toIntOrNull()
                    ?: throw UserError(
                        "Avtomatik zaxiralar soni butun son bo‘lishi kerak (${Settings.AUTO_BACKUP_KEEP_RANGE.first} dan ${Settings.AUTO_BACKUP_KEEP_RANGE.last} gacha).",
                        "autoBackupKeep",
                    )
                settings.autoBackupKeep = value
                value
            },
            form = keepErrors,
        ) { value ->
            keepText = value.toString()
            keepSaved = true
            state.success(
                "Saqlandi: eng yangi $value ta avtomatik nusxa saqlanadi." +
                    if (autoCount > value) " Hozir $autoCount ta bor — ortiqcha eskilari dastur keyingi ochilganda o‘chiriladi." else "",
            )
        }
    }

    fun resetLayouts() {
        if (layoutCount == 0) {
            state.info("Hamma jadvallarning ustunlari allaqachon standart holatda.")
            return
        }
        confirm.ask(
            title = "Jadval ustunlarini qaytarish",
            message = "$layoutCount ta jadvalda o‘zgartirilgan ustunlar (tartibi, kengligi, yashirilganlari va saralash) standart holatga " +
                "qaytariladi. Ma’lumotlarga ta’sir qilmaydi.",
            confirmLabel = "Standart holatga qaytarish",
        ) {
            state.run({ settings.resetTableLayouts() }) {
                state.success("Jadval ustunlari standart holatga qaytarildi ($layoutCount ta jadval).")
            }
        }
    }

    val peopleDirty = cleanName(org) != savedOrg || cleanName(actor) != savedActor
    val keepDirty = keepText.trim() != savedKeep.toString()

    ScrollPage(
        title = Page.SETTINGS.title,
        subtitle = "Tashkilot, xodim, avtomatik zaxira va dastur haqida ma’lumot",
        testTag = "settings-page",
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackupCard(
                "Tashkilot va xodim",
                Modifier.weight(1.35f).fillMaxHeight(),
                description = "Hisobotlar sarlavhasi va formalarda taklif qilinadigan ism",
                testTag = "settings-people",
            ) {
                AppTextField(
                    org,
                    {
                        org = it
                        peopleSaved = false
                        peopleErrors.clear("orgName")
                    },
                    label = "Tashkilot nomi",
                    placeholder = "Masalan: Respublika sud-tibbiy ekspertiza ilmiy-amaliy markazi",
                    error = peopleErrors["orgName"],
                    help = "Excel va PDF hisobotlarining sarlavhasida chiqadi (200 belgigacha).",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings-org",
                )
                AppTextField(
                    actor,
                    {
                        actor = it
                        peopleSaved = false
                        peopleErrors.clear("actorName")
                    },
                    label = "Xodim ismi (standart)",
                    placeholder = "Masalan: Karimova D.",
                    error = peopleErrors["actorName"],
                    help = "Formalarda «qabul qilgan / beruvchi» sifatida oldindan yoziladi va audit jurnalida amalni bajargan xodim " +
                        "sifatida saqlanadi (100 belgigacha).",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings-actor",
                )
                Spacer(Modifier.weight(1f))
                SettingsSaveRow(dirty = peopleDirty, saved = peopleSaved, busy = state.busy, onSave = { savePeople() }, testTag = "settings-people-save", modifier = Modifier.fillMaxWidth())
            }
            BackupCard(
                "Avtomatik zaxira",
                Modifier.weight(1f).fillMaxHeight(),
                description = "Har kuni dastur birinchi ochilganda baza nusxasi olinadi",
                testTag = "settings-backup",
            ) {
                AppTextField(
                    keepText,
                    {
                        keepText = it.filter { ch -> !ch.isWhitespace() }.take(4)
                        keepSaved = false
                        keepErrors.clear("autoBackupKeep")
                    },
                    label = "Saqlanadigan avtomatik nusxalar soni",
                    required = true,
                    error = keepErrors["autoBackupKeep"],
                    help = "${Settings.AUTO_BACKUP_KEEP_RANGE.first} dan ${Settings.AUTO_BACKUP_KEEP_RANGE.last} gacha; eng yangilari qoladi, " +
                        "eskilari o‘chiriladi.",
                    onEnter = { saveKeep() },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings-keep",
                )
                BackupInfoLine(
                    "Holati",
                    "Oxirgisi: ${lastAuto?.let { dateText(it) } ?: "hali olinmagan"} · hozir $autoCount ta saqlangan",
                    openLabel = "Zaxiralash",
                    openIcon = AppIcons.Backup,
                    onOpen = { state.navigate(Page.BACKUP) },
                    testTag = "settings-backup-info",
                )
                Spacer(Modifier.weight(1f))
                SettingsSaveRow(dirty = keepDirty, saved = keepSaved, busy = state.busy, onSave = { saveKeep() }, testTag = "settings-keep-save", modifier = Modifier.fillMaxWidth())
            }
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackupCard(
                "Yaroqlilik muddati chegaralari",
                Modifier.weight(1.35f).fillMaxHeight(),
                description = "Holat faqat yaroqlilik muddatigacha qolgan kunlar bo‘yicha hisoblanadi",
                testTag = "settings-expiry",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsThreshold(ExpiryState.GREEN, "> $GREEN_AFTER_DAYS kun", "yashil — 3 oydan ko‘p qolgan")
                    SettingsThreshold(ExpiryState.YELLOW, "${RED_UNTIL_DAYS + 1}–$GREEN_AFTER_DAYS kun", "sariq — 1–3 oy qolgan")
                    SettingsThreshold(ExpiryState.RED, "0–$RED_UNTIL_DAYS kun", "qizil — bugun tugaydigani ham shu yerda")
                    SettingsThreshold(ExpiryState.EXPIRED, "< 0 kun", "to‘q qizil — berish faqat alohida tasdiq bilan")
                    SettingsThreshold(ExpiryState.UNKNOWN, "—", "kulrang — muddati yozilmagan")
                }
                BackupNote("Chegaralar topshiriq bo‘yicha o‘zgarmas: jadvallar, bosh sahifa, eslatmalar va hisobotlarda bir xil ishlatiladi.")
            }
            BackupCard(
                "Jadval ustunlari",
                Modifier.weight(1f).fillMaxHeight(),
                description = "Har bir jadvalning ko‘rinishi alohida saqlanadi",
                testTag = "settings-layouts",
            ) {
                BackupNote(
                    "Ustunlar tartibi, kengligi, yashirilganlari va saralash har bir jadval uchun eslab qolinadi. Ko‘rinish chalkashib " +
                        "ketsa — hammasini birdan standart holatga qaytaring.",
                )
                Text(
                    if (layoutCount == 0) "Hozir hamma jadvallar standart holatda." else "Hozir $layoutCount ta jadvalning ko‘rinishi o‘zgartirilgan.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Text,
                    modifier = Modifier.testTag("settings-layout-count"),
                )
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppButton(
                        "Jadval ustunlarini standart holatga qaytarish",
                        onClick = { resetLayouts() },
                        kind = ButtonKind.SECONDARY,
                        icon = AppIcons.Columns,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("settings-reset-layouts"),
                    )
                }
            }
        }

        SettingsAbout(state, dbInfo)
    }
    ConfirmHost(confirm, state.busy)
}

/** Bo‘lim pastidagi «Saqlash» va saqlanmagan o‘zgarish belgisi. */
@Composable
private fun SettingsSaveRow(dirty: Boolean, saved: Boolean, busy: Boolean, onSave: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        when {
            dirty -> Text("Saqlanmagan o‘zgarish bor", fontSize = 12.5.sp, color = AppColors.Warning, modifier = Modifier.testTag("$testTag-state"))
            saved -> Row(Modifier.testTag("$testTag-state"), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Check, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Saqlandi", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Success)
            }
        }
        AppButton("Saqlash", onClick = onSave, icon = AppIcons.Check, enabled = !busy, modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun SettingsThreshold(state: ExpiryState, range: String, text: String) {
    Row(Modifier.heightIn(min = 26.dp).testTag("threshold-${state.name}"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(176.dp)) { ExpiryChip(state) }
        Text(range, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, modifier = Modifier.width(88.dp))
        Text(text, fontSize = 13.sp, lineHeight = 17.sp, color = AppColors.Muted)
    }
}

/** Dastur haqida: versiya, Java, SQLite, OT va ma’lumotlar joylashuvi. */
@Composable
private fun SettingsAbout(state: AppState, dbInfo: uz.reaktivombor.desktop.service.DatabaseInfo?) {
    val dataDir = state.services.dataDir.toAbsolutePath()
    val logs = DataDir.logsDir(dataDir)
    val backups = state.services.backup.dir
    val java = listOfNotNull(
        System.getProperty("java.runtime.version") ?: System.getProperty("java.version"),
        System.getProperty("java.vendor")?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val os = listOfNotNull(System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch")?.let { "($it)" }).joinToString(" ")
    BackupCard(
        "Dastur haqida",
        description = "Reaktiv Control — Forensic Laboratory Management System. Internetsiz ishlaydi, ma’lumotlar hech qayerga yuborilmaydi.",
        testTag = "settings-about",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BackupInfoLine("Dastur versiyasi", backupAppVersion(), testTag = "about-version")
                BackupInfoLine("Java", java.ifBlank { "—" }, testTag = "about-java")
                BackupInfoLine("SQLite", dbInfo?.sqliteVersion?.ifBlank { null } ?: "—", testTag = "about-sqlite")
                BackupInfoLine("Operatsion tizim", os.ifBlank { "—" }, testTag = "about-os")
            }
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BackupInfoLine("Ma’lumotlar papkasi", dataDir.toString(), onOpen = { state.dialogs.openFolder(dataDir) }, testTag = "about-data-dir")
                BackupInfoLine(
                    "Baza fayli" + (dbInfo?.let { " · ${backupSizeText(it.size)} · sxema versiyasi ${it.userVersion}" } ?: ""),
                    (dbInfo?.path ?: DataDir.dbFile(dataDir)).toString(),
                    testTag = "about-db",
                )
                BackupInfoLine("Jurnallar papkasi", logs.toString(), onOpen = { state.dialogs.openFolder(logs) }, testTag = "about-logs")
                BackupInfoLine("Zaxira nusxalar papkasi", backups.toString(), onOpen = { state.dialogs.openFolder(backups) }, testTag = "about-backups")
            }
        }
        HorizontalDivider(color = AppColors.Divider)
        BackupNote(
            "Xato yuz bersa, tafsilotlari jurnallar papkasidagi reaktiv-control.0.log fayliga yoziladi — muammoni aniqlash uchun " +
                "shu fayl kerak bo‘ladi.",
        )
    }
}

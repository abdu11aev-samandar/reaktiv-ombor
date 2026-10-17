package uz.reaktivombor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.reaktivombor.domain.Backup
import uz.reaktivombor.notify.ExpiryNotifications
import uz.reaktivombor.ui.ReaktivTheme
import uz.reaktivombor.ui.ReagentFormScreen
import uz.reaktivombor.ui.ReagentListScreen
import uz.reaktivombor.ui.ReagentViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReaktivTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(vm: ReagentViewModel = viewModel()) {
    val context = LocalContext.current
    val reagents by vm.reagents.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var formOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var formKey by rememberSaveable { mutableIntStateOf(0) }
    var pendingRestore by remember { mutableStateOf<Backup.Parsed?>(null) }

    // “Bugun” ilova qayta ochilganda yangilanadi (ranglar yarim tundan keyin o‘zgaradi).
    var today by remember { mutableStateOf(LocalDate.now()) }
    var notificationsEnabled by remember { mutableStateOf(ExpiryNotifications.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        notificationsEnabled = ExpiryNotifications.canNotify(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = ExpiryNotifications.canNotify(context)
    }
    // Birinchi ochilishda bir marta ruxsat so‘raymiz (Android 13+).
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            if (!ExpiryNotifications.canNotify(context)) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.readBackup(uri) { pendingRestore = it }
    }

    if (formOpen) {
        BackHandler { formOpen = false }
        val list = reagents
        val editing = editingId?.let { id -> list?.firstOrNull { it.id == id } }

        // Tahrirlanayotgan qator o‘chib ketgan bo‘lsa — ro‘yxatga qaytamiz.
        LaunchedEffect(editingId, list) {
            if (editingId != null && list != null && editing == null) formOpen = false
        }

        if (editingId == null || editing != null) {
            key(editingId, formKey) {
                ReagentFormScreen(
                    reagent = editing,
                    knownNames = list.orEmpty().map { it.name }.distinct().sorted(),
                    snackbar = snackbar,
                    onBack = { formOpen = false },
                    onSave = { values, andNew ->
                        vm.save(editingId, values) {
                            if (andNew) formKey++ else formOpen = false
                        }
                    },
                    onDelete = { reagent -> vm.delete(reagent) { formOpen = false } },
                )
            }
        }
    } else {
        ReagentListScreen(
            reagents = reagents,
            today = today,
            snackbar = snackbar,
            notificationsEnabled = notificationsEnabled,
            onEnableNotifications = { openNotificationSettings(context) },
            onAdd = {
                editingId = null
                formKey++
                formOpen = true
            },
            onEdit = { reagent ->
                editingId = reagent.id
                formOpen = true
            },
            onExport = { exportLauncher.launch(vm.backupFileName()) },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        )
    }

    pendingRestore?.let { parsed ->
        val currentCount = reagents?.size ?: 0
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Zaxiradan tiklansinmi?") },
            text = {
                val skipped = if (parsed.skipped > 0) " ${parsed.skipped} ta buzuq qator tashlab ketiladi." else ""
                Text(
                    "Fayldagi ${parsed.reagents.size} ta reaktiv tiklanadi. " +
                        "Hozirgi ro‘yxat ($currentCount ta) o‘rniga yoziladi.$skipped",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.restore(parsed); pendingRestore = null }) { Text("Tiklash") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Bekor qilish") } },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

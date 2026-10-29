package uz.reaktivombor.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.service.Notification
import uz.reaktivombor.desktop.service.NotificationKind
import uz.reaktivombor.desktop.service.NotificationTarget
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.components.Banner
import uz.reaktivombor.desktop.ui.components.ErrorDialog
import uz.reaktivombor.desktop.ui.components.TableColumn
import uz.reaktivombor.desktop.ui.components.TableLayoutState
import uz.reaktivombor.desktop.ui.components.TableSort
import uz.reaktivombor.desktop.ui.components.rememberTableLayoutState
import uz.reaktivombor.desktop.ui.pages.AddReagentPage
import uz.reaktivombor.desktop.ui.pages.BackupPage
import uz.reaktivombor.desktop.ui.pages.DashboardPage
import uz.reaktivombor.desktop.ui.pages.DepartmentsPage
import uz.reaktivombor.desktop.ui.pages.DistrictsPage
import uz.reaktivombor.desktop.ui.pages.ExpiryPage
import uz.reaktivombor.desktop.ui.pages.HistoryPage
import uz.reaktivombor.desktop.ui.pages.IssuesPage
import uz.reaktivombor.desktop.ui.pages.ReagentsPage
import uz.reaktivombor.desktop.ui.pages.ReceiptsPage
import uz.reaktivombor.desktop.ui.pages.ReportsPage
import uz.reaktivombor.desktop.ui.pages.SettingsPage
import uz.reaktivombor.desktop.ui.pages.StockPage
import uz.reaktivombor.desktop.ui.theme.AppTheme
import uz.reaktivombor.desktop.ui.theme.Severity
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.time.LocalDate
import java.util.logging.Level
import java.util.logging.Logger

// Dastur ildizi va sahifalar shartnomasi (DESIGN.md §6.1). Sahifalar faqat AppState orqali ishlaydi.

/** Fayl tanlash oynalari. Dasturda — AWT FileDialog (Main.kt), testlarda — soxta. */
interface FileDialogs {
    /** Saqlash oynasi; [extension] nuqtasiz ("xlsx") — nomda bo‘lmasa qo‘shiladi. Bekor qilinsa null. */
    fun save(title: String, defaultName: String, extension: String): Path?

    /** Ochish oynasi; [extensions] nuqtasiz ("db", "json", "xlsx"). Bekor qilinsa null. */
    fun open(title: String, extensions: List<String>): Path?

    /** Papkani tizimning fayl boshqaruvchisida ochadi. */
    fun openFolder(dir: Path)
}

/**
 * Sahifaga o‘tishda beriladigan topshiriq. Sahifa `state.intent as? PageIntent.X` ni o‘zining boshlang‘ich holatiga
 * qo‘llaydi (har bir o‘tishda sahifa yangidan tuziladi). Tushunmagan topshirig‘ini e’tiborsiz qoldiradi.
 */
sealed interface PageIntent {
    /** «Kirim operatsiyalari»: shu partiyaga qo‘shimcha kirim shakli ochiq. */
    data class ReceiptFor(val batchId: Long) : PageIntent

    /** «Sarf va berish»: birinchi qatorda shu partiya tanlangan. */
    data class IssueFrom(val batchId: Long) : PageIntent

    /** «Operatsiyalar tarixi»: partiya ([batchId]) yoki reaktiv ([reagentId]) jurnali. */
    data class HistoryOf(val batchId: Long? = null, val reagentId: Long? = null) : PageIntent

    /** «Barcha reaktivlar»: shu partiyaning tahrirlash oynasi ochiq. */
    data class EditBatch(val batchId: Long) : PageIntent

    /** «Yaroqlilik muddati nazorati»: holatlar bo‘yicha filtr; [batchId] — ajratib ko‘rsatiladigan partiya. */
    data class FilterExpiry(val states: Set<ExpiryState>, val batchId: Long? = null) : PageIntent

    /** «Barcha reaktivlar»: reaktiv, holatlar va faqat qoldig‘i borlar bo‘yicha filtr. */
    data class ReagentsFiltered(
        val reagentId: Long? = null,
        val states: Set<ExpiryState> = emptySet(),
        val withStockOnly: Boolean = false,
    ) : PageIntent

    /** «Umumiy zaxiralar»: reaktiv yoki faqat minimal qoldiqdan kamlar. */
    data class StockFiltered(val reagentId: Long? = null, val belowMinOnly: Boolean = false) : PageIntent

    /** «Bo‘limlar bo‘yicha hisob»: shu bo‘lim tanlangan. */
    data class DepartmentOf(val departmentId: Long) : PageIntent

    /** «Tumanlarga berilgan reaktivlar»: shu tuman tanlangan. */
    data class DistrictOf(val districtId: Long) : PageIntent
}

/** Joriy sahifa, unga berilgan topshiriq va o‘tish raqami (har bir [AppState.navigate] da oshadi). */
@Immutable
data class Navigation(val page: Page, val intent: PageIntent?, val serial: Long)

/** Pastdagi xabar (natija, ogohlantirish, xato). [key] bir xil yangi xabar eskisining o‘rnini oladi. */
@Immutable
class AppMessage(
    val id: Long,
    val text: String,
    val severity: Severity,
    val title: String?,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val autoHide: Boolean,
    val key: String?,
)

/** Kutilmagan xato oynasi uchun: foydalanuvchi matni va texnik tafsilot. */
@Immutable
data class UnexpectedError(val message: String, val details: String)

/**
 * Shakl maydonlari xatolari (UserError.fields): kalit — servis maydon nomi (`name`, `series`, `receivedOn` …).
 * [AppState.run] ga berilsa, xato bo‘lganda to‘ldiriladi, muvaffaqiyatda tozalanadi.
 */
@Stable
class FormErrors {
    var fields: Map<String, String> by mutableStateOf(emptyMap())
        private set

    // Shaklda chizilgan (o‘qilgan) maydonlar: xatosi shulardan tashqarida bo‘lsa, run() uni pastki xabar qilib chiqaradi.
    private val rendered = mutableSetOf<String>()

    /** Maydon xatosi; chaqirilishi maydon shaklda borligini ham bildiradi. */
    operator fun get(field: String): String? {
        rendered += field
        return fields[field]
    }

    /** Shaklda ko‘rsatilmaydigan maydonlar xatolari. */
    fun unshown(errors: Map<String, String>): Map<String, String> = errors.filterKeys { it !in rendered }

    fun show(errors: Map<String, String>) {
        fields = errors
    }

    /** Foydalanuvchi maydonni o‘zgartirsa — uning xatosi olib tashlanadi. */
    fun clear(field: String) {
        if (field in fields) fields = fields - field
    }

    fun clear() {
        fields = emptyMap()
    }
}

/**
 * Dastur holati — hamma sahifalar uchun bitta. Ichida: xizmatlar, fayl oynalari, joriy sahifa va topshiriq,
 * yangilanish hisoblagichi ([refreshTick]), xabarlar, yozish band ekani ([busy]) va kutilmagan xato oynasi.
 */
@Stable
class AppState(
    val services: Services,
    val dialogs: FileDialogs,
    startPage: Page = Page.DASHBOARD,
) {
    private val log: Logger = Logger.getLogger("uz.reaktivombor.ui.AppState")

    // ---------------------------------------------------------------- navigatsiya

    var navigation: Navigation by mutableStateOf(Navigation(startPage, null, 0))
        private set

    val page: Page get() = navigation.page

    val intent: PageIntent? get() = navigation.intent

    /** Sahifaga o‘tish (bir sahifaning o‘ziga ham — u yangidan tuziladi va [intent] ni oladi). */
    fun navigate(page: Page, intent: PageIntent? = null) {
        navigation = Navigation(page, intent, navigation.serial + 1)
    }

    // ---------------------------------------------------------------- yangilanish

    /** Har bir muvaffaqiyatli yozishdan keyin oshadi: sahifalar so‘rovlarini shu kalit bilan `remember` qiladi. */
    var refreshTick: Int by mutableIntStateOf(0)
        private set

    fun refresh() {
        refreshTick++
    }

    /** Bugungi sana (xizmatlar soati bo‘yicha — testlarda qat’iy sana). */
    fun today(): LocalDate = services.today()

    // ---------------------------------------------------------------- xabarlar

    private val messageList = mutableStateListOf<AppMessage>()
    private var nextMessageId = 1L

    val messages: List<AppMessage> get() = messageList

    /**
     * Xabar ko‘rsatadi va uning raqamini qaytaradi. [autoHide] — bir necha soniyadan keyin o‘zi yopiladi (standarti —
     * faqat muvaffaqiyat xabarlari). Bir vaqtda ko‘pi bilan [MAX_MESSAGES] ta (eskisi tushib qoladi).
     */
    fun showMessage(
        text: String,
        severity: Severity = Severity.INFO,
        title: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        autoHide: Boolean = severity == Severity.SUCCESS,
        key: String? = null,
    ): Long {
        val id = nextMessageId++
        if (key != null) messageList.removeAll { it.key == key }
        messageList += AppMessage(id, text, severity, title, actionLabel, onAction, autoHide, key)
        while (messageList.size > MAX_MESSAGES) messageList.removeAt(0)
        return id
    }

    fun success(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null): Long =
        showMessage(text, Severity.SUCCESS, actionLabel = actionLabel, onAction = onAction)

    fun info(text: String, title: String? = null): Long = showMessage(text, Severity.INFO, title)

    fun warning(text: String, title: String? = null): Long = showMessage(text, Severity.WARNING, title)

    fun error(text: String, title: String? = null): Long = showMessage(text, Severity.ERROR, title)

    fun dismissMessage(id: Long) {
        messageList.removeAll { it.id == id }
    }

    // ---------------------------------------------------------------- yozish va xatolar

    /** Yozish amali ketyapti: yozadigan tugmalar `enabled = !state.busy` bilan o‘chiriladi (ikki marta bosilmasin). */
    var busy: Boolean by mutableStateOf(false)
        internal set

    // Band holati keyingi kadrda bo‘shatiladi: amal paytida navbatda turgan ikkinchi bosish e’tiborsiz qoladi.
    internal var releaseRequests: Int by mutableIntStateOf(0)
    internal var hostAttached = false

    var unexpectedError: UnexpectedError? by mutableStateOf(null)
        private set

    /**
     * Yozish amalini bajaradi (bitta foydalanuvchi amali = bitta servis chaqiruvi, UI oqimida). Natija:
     * - muvaffaqiyat: [form] tozalanadi, [refresh] bo‘lsa [refresh()], so‘ng [onSuccess]; true qaytadi;
     * - [UserError] (jumladan DuplicateBatchError, DuplicateOperationError, ExpiredBatchError, InsufficientStockError):
     *   avval [onError] ga beriladi — u true qaytarsa (masalan, tasdiq oynasini ochdi) boshqa hech narsa qilinmaydi;
     *   aks holda maydon xatolari [form] ga (shaklda ko‘rsatilmaydigan maydonniki va maydonsiz xato — pastda qizil xabar);
     * - boshqa har qanday xato: jurnalga yoziladi va «Kutilmagan xato» oynasi chiqadi.
     * [busy] paytida chaqirilsa hech narsa qilmaydi (false). [onSuccess] ichidan ikkinchi [run] chaqirmang.
     */
    fun <T> run(
        action: () -> T,
        form: FormErrors? = null,
        onError: ((UserError) -> Boolean)? = null,
        refresh: Boolean = true,
        onSuccess: (T) -> Unit = {},
    ): Boolean {
        if (busy) return false
        busy = true
        val result = try {
            action()
        } catch (e: UserError) {
            release()
            if (onError?.invoke(e) == true) return false
            if (form != null && e.fields.isNotEmpty()) {
                // Maydon yonida ko‘rinadigan xatolar pastda takrorlanmaydi; shaklda yo‘q maydonniki — xabar bo‘lib chiqadi.
                form.show(e.fields)
                val unshown = form.unshown(e.fields)
                if (unshown.isNotEmpty()) showMessage(unshown.values.distinct().joinToString("\n"), Severity.ERROR, key = RUN_ERROR_KEY)
            } else {
                showMessage(e.message, Severity.ERROR, key = RUN_ERROR_KEY)
            }
            return false
        } catch (e: Throwable) {
            release()
            reportUnexpected(e, "action")
            return false
        }
        release()
        form?.clear()
        messageList.removeAll { it.key == RUN_ERROR_KEY }
        if (refresh) refresh()
        try {
            onSuccess(result)
        } catch (e: Throwable) {
            reportUnexpected(e, "onSuccess")
        }
        return true
    }

    /**
     * O‘qish (so‘rov) — xato bo‘lsa [fallback] qaytadi: UserError xabari ko‘rsatiladi, boshqa xato jurnalga yoziladi va
     * «Kutilmagan xato» oynasi chiqadi. Sahifalarda: `remember(state.refreshTick, filtr) { state.load(emptyList()) { … } }`.
     */
    fun <T> load(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: UserError) {
        showMessage(e.message, Severity.ERROR, key = LOAD_ERROR_KEY)
        fallback
    } catch (e: Throwable) {
        reportUnexpected(e, "load")
        fallback
    }

    /** Kutilmagan xato: jurnalga yoziladi, foydalanuvchiga umumiy matn va «Batafsil» ostida tafsilot. */
    fun reportUnexpected(error: Throwable, context: String) {
        runCatching { log.log(Level.SEVERE, "Kutilmagan xato ($context)", error) }
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        unexpectedError = UnexpectedError(UNEXPECTED_TEXT, trace.lineSequence().take(40).joinToString("\n"))
    }

    fun dismissUnexpected() {
        unexpectedError = null
    }

    private fun release() {
        if (hostAttached) releaseRequests++ else busy = false
    }

    // ---------------------------------------------------------------- jadval ko‘rinishi

    /** Saqlangan ustunlar ko‘rinishi (JSON) yoki null. Xato bo‘lsa — jurnalga yoziladi, standart ko‘rinish. */
    fun loadTableLayout(id: String): String? = try {
        services.settings.tableLayout(id)
    } catch (e: Throwable) {
        log.log(Level.WARNING, "table layout read failed: $id", e)
        null
    }

    /** Ustunlar ko‘rinishini saqlaydi (null — standart holat). Xato bo‘lsa faqat jurnalga yoziladi. */
    fun saveTableLayout(id: String, json: String?) {
        try {
            services.settings.setTableLayout(id, json)
        } catch (e: Throwable) {
            log.log(Level.WARNING, "table layout save failed: $id", e)
        }
    }

    companion object {
        const val MAX_MESSAGES = 4
        const val AUTO_HIDE_MILLIS = 6_000L
        const val UNEXPECTED_TEXT = "Kutilmagan xato yuz berdi — tafsilotlari jurnalga yozildi (logs papkasi). Ma’lumotlar o‘zgartirilmadi."
        private const val RUN_ERROR_KEY = "run-error"
        private const val LOAD_ERROR_KEY = "load-error"
    }
}

/** Sahifa jadvali uchun ko‘rinish holati: ustunlar tartibi, kengligi, yashirilgani va saralash sozlamalarda saqlanadi. */
@Composable
fun rememberAppTableLayout(
    state: AppState,
    id: String,
    columns: List<TableColumn<*>>,
    defaultSort: TableSort? = null,
): TableLayoutState = rememberTableLayoutState(
    columns = columns,
    load = { state.loadTableLayout(id) },
    save = { state.saveTableLayout(id, it) },
    defaultSort = defaultSort,
)

/** Bildirishnomaning darajasi va sahifasi. */
private fun Notification.toShell(): ShellNotification = ShellNotification(
    text = text,
    severity = when (kind) {
        NotificationKind.EXPIRED, NotificationKind.RED -> Severity.ERROR
        NotificationKind.BELOW_MIN, NotificationKind.YELLOW -> Severity.WARNING
    },
    page = when (target) {
        NotificationTarget.EXPIRY -> Page.EXPIRY
        NotificationTarget.STOCK -> Page.STOCK
    },
)

/** Bildirishnoma bosilganda ochiladigan sahifa topshirig‘i. */
private fun Notification.toIntent(): PageIntent = when (target) {
    NotificationTarget.EXPIRY -> PageIntent.FilterExpiry(
        states = setOf(
            when (kind) {
                NotificationKind.EXPIRED -> ExpiryState.EXPIRED
                NotificationKind.RED -> ExpiryState.RED
                else -> ExpiryState.YELLOW
            },
        ),
        batchId = batchId,
    )
    NotificationTarget.STOCK -> PageIntent.StockFiltered(reagentId = reagentId, belowMinOnly = true)
}

/** Dastur ildizi: mavzu, ramka (chap menyu, yuqori panel, bildirishnomalar), joriy sahifa, xabarlar va xato oynasi. */
@Composable
fun ReaktivControlApp(state: AppState) {
    AppTheme {
        DisposableEffect(state) {
            state.hostAttached = true
            onDispose {
                state.hostAttached = false
                state.busy = false
            }
        }
        LaunchedEffect(state.releaseRequests) { state.busy = false }

        val tick = state.refreshTick
        val today = remember(tick) { state.today() }
        val notifications = remember(tick, today) { state.load(emptyList()) { state.services.queries.notifications(today) } }
        val shellItems = remember(notifications) { notifications.map { it.toShell() } }
        AppShell(
            current = state.page,
            onNavigate = { state.navigate(it) },
            notifications = shellItems,
            onNotificationClick = { item ->
                val source = notifications.getOrNull(shellItems.indexOf(item))
                state.navigate(item.page, source?.toIntent())
            },
            today = today,
            badgeCount = notifications.count { it.counted },
        ) {
            Box(Modifier.fillMaxSize()) {
                key(state.navigation.serial) { PageRouter(state) }
                MessageHost(state, Modifier.align(Alignment.BottomCenter))
            }
        }
        state.unexpectedError?.let { error ->
            ErrorDialog(
                message = error.message,
                title = "Kutilmagan xato",
                details = error.details,
                onDismiss = { state.dismissUnexpected() },
            )
        }
    }
}

/** Har bir sahifa — ui/pages ichidagi o‘z faylida. */
@Composable
private fun PageRouter(state: AppState) {
    when (state.page) {
        Page.DASHBOARD -> DashboardPage(state)
        Page.REAGENTS -> ReagentsPage(state)
        Page.ADD_REAGENT -> AddReagentPage(state)
        Page.RECEIPTS -> ReceiptsPage(state)
        Page.ISSUES -> IssuesPage(state)
        Page.DEPARTMENTS -> DepartmentsPage(state)
        Page.DISTRICTS -> DistrictsPage(state)
        Page.EXPIRY -> ExpiryPage(state)
        Page.STOCK -> StockPage(state)
        Page.REPORTS -> ReportsPage(state)
        Page.HISTORY -> HistoryPage(state)
        Page.BACKUP -> BackupPage(state)
        Page.SETTINGS -> SettingsPage(state)
    }
}

/** Pastki o‘rtadagi xabarlar (soya bilan, sahifa ustida); muvaffaqiyat xabarlari bir necha soniyada yopiladi. */
@Composable
private fun MessageHost(state: AppState, modifier: Modifier = Modifier) {
    val messages = state.messages
    if (messages.isEmpty()) return
    Column(
        modifier.padding(bottom = 16.dp, start = 24.dp, end = 24.dp).widthIn(max = 600.dp).testTag("messages"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        messages.forEach { message ->
            key(message.id) {
                if (message.autoHide) {
                    LaunchedEffect(message.id) {
                        delay(AppState.AUTO_HIDE_MILLIS)
                        state.dismissMessage(message.id)
                    }
                }
                Surface(shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                    Banner(
                        text = message.text,
                        severity = message.severity,
                        title = message.title,
                        actionLabel = message.actionLabel,
                        onAction = message.onAction?.let { action ->
                            {
                                state.dismissMessage(message.id)
                                action()
                            }
                        },
                        onDismiss = { state.dismissMessage(message.id) },
                        testTag = "message-${message.severity.name.lowercase()}",
                    )
                }
            }
        }
    }
}

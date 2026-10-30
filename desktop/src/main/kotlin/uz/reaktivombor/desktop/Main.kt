package uz.reaktivombor.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.sqlite.NativeLibraryNotFoundException
import uz.reaktivombor.desktop.core.AppLogging
import uz.reaktivombor.desktop.core.DataDir
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.service.Backup
import uz.reaktivombor.desktop.service.LegacyResult
import uz.reaktivombor.desktop.service.RecoveryResult
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.APP_NAME
import uz.reaktivombor.desktop.ui.APP_SUBTITLE
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FileDialogs
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.ReaktivControlApp
import uz.reaktivombor.desktop.ui.theme.Severity
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import uz.reaktivombor.desktop.selftest.runSelfTest
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private const val WINDOW_TITLE = "$APP_NAME — $APP_SUBTITLE"
private const val DEFAULT_WIDTH = 1366
private const val DEFAULT_HEIGHT = 768
private const val MIN_WIDTH = 1280
private const val MIN_HEIGHT = 720

private val log: Logger = Logger.getLogger("uz.reaktivombor.Main")

/** Ikkinchi nusxa bitta bazaga yozmasligi uchun (ma’lumotlar papkasidagi .lock). */
private var instanceLock: FileLock? = null

fun main(args: Array<String>) {
    // Hammasidan oldin (self-test’dan ham): SQLite DLL’i chiqariladigan papka Windows yo‘l kodlashiga sig‘sin.
    val sqliteNative = SqliteNative.configure()

    // O‘zini tekshirish (CI va o‘rnatilgan dastur uchun): oynasiz, vaqtinchalik papkada; foydalanuvchi bazasiga tegmaydi.
    if (args.firstOrNull() == "--self-test") {
        val output = args.getOrNull(1)?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("java.io.tmpdir"), "reaktiv-control-selftest.txt")
        exitProcess(runSelfTest(output))
    }

    val dataDir = DataDir.default()
    try {
        Files.createDirectories(dataDir)
    } catch (e: Exception) {
        fatal("Ma’lumotlar papkasini yaratib bo‘lmadi:\n$dataDir\n\n${e.message ?: e}")
    }

    instanceLock = acquireLock(DataDir.lockFile(dataDir))
    if (instanceLock == null) {
        JOptionPane.showMessageDialog(
            null,
            "«$APP_NAME» dasturi allaqachon ochiq.\nIkkinchi nusxani ochib bo‘lmaydi — ochiq oynadan foydalaning.",
            APP_NAME,
            JOptionPane.INFORMATION_MESSAGE,
        )
        return
    }

    // Jurnal yozilmasa ham dastur ishlayveradi (masalan, papka faqat o‘qish uchun).
    runCatching { AppLogging.setup(dataDir) }.onFailure { System.err.println("Jurnalni yoqib bo‘lmadi: ${it.message}") }
    AppLogging.installUncaughtHandler { _, _ -> showErrorLater(AppLogging.GENERIC_ERROR) }
    log.info("start, data dir=$dataDir")
    sqliteNative?.let { log.log(if (it.dir != null) Level.INFO else Level.WARNING, it.describe()) }

    // Baza ochilmasa (yangiroq versiyadan boshqa sabab — masalan, fayl buzilgan): oxirgi yaroqli zaxiradan tiklashni taklif qilamiz.
    var recovered: RecoveryResult? = null
    val services = try {
        Services.open(dataDir)
    } catch (e: UserError) {
        log.log(Level.WARNING, "database refused", e)
        fatal(e.message)
    } catch (e: Exception) {
        // SQLite kutubxonasi yuklanmagan bo‘lsa, baza ham, zaxiralar ham «ochilmaydi» — ular aybdor emas, tiklash taklif qilinmaydi.
        if (SqliteNative.isLoadFailure(e)) sqliteLoadFatal(dataDir, e)
        log.log(Level.SEVERE, "database open failed", e)
        recovered = offerRecovery(dataDir)
        try {
            Services.open(dataDir)
        } catch (again: Exception) {
            log.log(Level.SEVERE, "database open failed after recovery", again)
            fatal("Zaxiradan tiklangan bazani ham ochib bo‘lmadi:\n${DataDir.dbFile(dataDir)}\n\nTafsilotlar jurnal faylida (logs papkasi).")
        }
    } catch (e: UnsatisfiedLinkError) {
        sqliteLoadFatal(dataDir, e)
    }

    // Eski jadvaldan bir martalik ko‘chirish — oyna ochilishidan oldin (ma’lumot darhol ko‘rinsin).
    val legacy: LegacyResult? = try {
        services.legacy.autoImportIfNeeded()
    } catch (e: Exception) {
        log.log(Level.WARNING, "legacy import failed", e)
        LegacyResult(0, 0, listOf("Eski ma’lumotlarni ko‘chirib bo‘lmadi (tafsiloti jurnalda)."))
    }

    // Kunlik avtomatik zaxira — fonda; xato bo‘lsa faqat jurnalga yoziladi.
    Thread({
        try {
            services.backup.autoBackupIfDue(services.today())?.let { log.info("auto backup: ${it.fileName}") }
        } catch (e: Exception) {
            log.log(Level.WARNING, "auto backup failed", e)
        }
    }, "auto-backup").apply { isDaemon = true }.start()

    val dialogs = AwtFileDialogs()
    val state = AppState(services, dialogs)
    recovered?.let { showRecovery(state, it) }
    legacy?.let { showLegacyResult(state, it) }

    // Windows o‘chirilganda oyna yopilish xabarini olmaydi — baza baribir to‘g‘ri yopilsin.
    Runtime.getRuntime().addShutdownHook(Thread { closeServices(services) })

    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val fits = screen.width >= DEFAULT_WIDTH && screen.height >= DEFAULT_HEIGHT

    application {
        val windowState = rememberWindowState(
            placement = if (fits) WindowPlacement.Floating else WindowPlacement.Maximized,
            width = minOf(DEFAULT_WIDTH, screen.width).dp,
            height = minOf(DEFAULT_HEIGHT, screen.height).dp,
            position = WindowPosition(Alignment.Center),
        )
        val icon = remember {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { BitmapPainter(loadImageBitmap(it)) }
        }
        Window(
            onCloseRequest = {
                closeServices(services)
                exitApplication()
            },
            title = WINDOW_TITLE,
            icon = icon,
            state = windowState,
        ) {
            LaunchedEffect(Unit) {
                dialogs.frame = window
                window.minimumSize = Dimension(minOf(MIN_WIDTH, screen.width), minOf(MIN_HEIGHT, screen.height))
            }
            // Yarim tunda sana o‘zgarsa — holatlar va eslatmalar qayta hisoblanadi.
            LaunchedEffect(Unit) {
                var day = state.today()
                while (true) {
                    delay(60_000)
                    val now = runCatching { state.today() }.getOrNull() ?: continue
                    if (now != day) {
                        day = now
                        state.refresh()
                    }
                }
            }
            ReaktivControlApp(state)
        }
    }
}

/**
 * Baza ochilmadi: oxirgi yaroqli zaxira bo‘lsa — «Oxirgi zaxira nusxadan tiklash» / «Chiqish». Tiklansa buzilgan fayl
 * o‘chirilmaydi (reaktiv-control-buzuq-<vaqt>.db). Zaxira yo‘q yoki foydalanuvchi chiqsa — dastur yopiladi.
 */
private fun offerRecovery(dataDir: Path): RecoveryResult {
    val dbFile = DataDir.dbFile(dataDir)
    val backup = runCatching { Backup.latestValid(dataDir) }.onFailure { log.log(Level.WARNING, "backup lookup failed", it) }.getOrNull()
        ?: fatal(
            "Ma’lumotlar bazasini ochib bo‘lmadi:\n$dbFile\n\nYaroqli zaxira nusxa topilmadi (${DataDir.backupsDir(dataDir)}).\n" +
                "Tafsilotlar jurnal faylida (logs papkasi).",
        )
    val modified = runCatching {
        LocalDateTime.ofInstant(Files.getLastModifiedTime(backup).toInstant(), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }.getOrDefault("")
    val options = arrayOf("Oxirgi zaxira nusxadan tiklash", "Chiqish")
    val answer = runCatching {
        JOptionPane.showOptionDialog(
            null,
            "Ma’lumotlar bazasini ochib bo‘lmadi — fayl buzilgan bo‘lishi mumkin:\n$dbFile\n\n" +
                "Oxirgi yaroqli zaxira nusxa: ${backup.fileName}" + (if (modified.isNotEmpty()) " ($modified)" else "") + ".\n" +
                "Tiklansa, buzilgan fayl o‘chirilmaydi — shu papkada «reaktiv-control-buzuq-…db» nomi bilan qoladi.\n" +
                "Zaxira olingandan keyin kiritilgan ma’lumotlarni qayta kiritish kerak bo‘ladi.",
            APP_NAME,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[0],
        )
    }.getOrDefault(-1)
    if (answer != 0) {
        runCatching { instanceLock?.release() }
        exitProcess(1)
    }
    return try {
        Backup.recoverFrom(dataDir, backup)
    } catch (e: UserError) {
        log.log(Level.SEVERE, "recovery refused", e)
        fatal(e.message)
    } catch (e: Exception) {
        log.log(Level.SEVERE, "recovery failed", e)
        fatal("Zaxiradan tiklab bo‘lmadi: ${e.message ?: e.javaClass.simpleName}\n\nTafsilotlar jurnal faylida (logs papkasi).")
    }
}

/** Baza zaxiradan tiklandi — oyna ochilgach yopilmaydigan ogohlantirish. */
private fun showRecovery(state: AppState, result: RecoveryResult) {
    state.showMessage(
        "Ma’lumotlar bazasi ochilmadi (buzilgan) — «${result.backup.fileName}» zaxira nusxasidan tiklandi. " +
            (result.brokenCopy?.let { "Buzilgan fayl o‘chirilmadi: ${it.fileName}. " } ?: "") +
            "Shu nusxadan keyin kiritilgan operatsiyalarni tekshirib, qayta kiriting.",
        severity = Severity.WARNING,
        title = "Baza zaxiradan tiklandi",
        autoHide = false,
    )
}

/** Ishga tushishdagi xabarda nechta izoh ko‘rsatiladi (qolganlari — «Ma’lumotlarni zaxiralash» sahifasida). */
internal const val LEGACY_NOTES_IN_MESSAGE = 3

/**
 * Eski jadvaldan ko‘chirish natijasi — bir marta, xabar sifatida. Izohlar ko‘p bo‘lsa — nechtasi qolgani aytiladi va
 * «Barcha izohlar» tugmasi «Ma’lumotlarni zaxiralash» sahifasini ochadi (u yerda oxirgi ko‘chirishning hamma izohlari).
 */
internal fun showLegacyResult(state: AppState, result: LegacyResult) {
    val more = result.notes.size - LEGACY_NOTES_IN_MESSAGE
    val notes = result.notes.take(LEGACY_NOTES_IN_MESSAGE).joinToString(" ") + if (more > 0) " … yana $more ta izoh." else ""
    val actionLabel = if (more > 0) "Barcha izohlar (${result.notes.size} ta)" else null
    val onAction: (() -> Unit)? = if (more > 0) ({ state.navigate(Page.BACKUP) }) else null
    when {
        result.imported > 0 -> state.showMessage(
            "Eski jadvaldan ${result.imported} ta partiya ko‘chirildi" +
                (if (result.skipped > 0) ", ${result.skipped} ta qator o‘tkazib yuborildi" else "") + ". " +
                "Eski fayl o‘zgartirilmadi." + if (notes.isNotEmpty()) " $notes" else "",
            severity = if (result.skipped > 0) Severity.WARNING else Severity.INFO,
            title = "Eski ma’lumotlar ko‘chirildi",
            actionLabel = actionLabel,
            onAction = onAction,
            autoHide = false,
        )
        notes.isNotEmpty() -> state.showMessage(
            notes,
            Severity.WARNING,
            title = "Eski ma’lumotlar",
            actionLabel = actionLabel,
            onAction = onAction,
            autoHide = false,
        )
    }
}

/** SQLite kutubxonasi yuklanmadi: aniq sabab aytiladi, baza va zaxiralarga tegilmaydi, dastur yopiladi. */
private fun sqliteLoadFatal(dataDir: Path, e: Throwable): Nothing {
    log.log(Level.SEVERE, "sqlite native library could not be loaded", e)
    fatal(SqliteNative.failureMessage(DataDir.dbFile(dataDir), SqliteNative.extractDir(), DataDir.logsDir(dataDir)))
}

private fun closeServices(services: Services) {
    runCatching { if (services.db.isOpen) services.db.close() }.onFailure { log.log(Level.WARNING, "db close failed", it) }
    runCatching { instanceLock?.release() }
}

private fun fatal(message: String): Nothing {
    // Jurnalga ham (oyna ko‘rinmasa — masalan, ekransiz ishga tushirilganda — sabab baribir qoladi).
    runCatching { log.severe("fatal: $message") }
    runCatching { JOptionPane.showMessageDialog(null, message, APP_NAME, JOptionPane.ERROR_MESSAGE) }
    runCatching { instanceLock?.release() }
    exitProcess(1)
}

private fun showErrorLater(message: String) {
    SwingUtilities.invokeLater {
        runCatching { JOptionPane.showMessageDialog(null, message, APP_NAME, JOptionPane.ERROR_MESSAGE) }
    }
}

private fun acquireLock(path: Path): FileLock? = runCatching {
    val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    channel.tryLock() ?: run {
        channel.close()
        null
    }
}.getOrNull()

/** Tizimning o‘z fayl tanlash oynalari (AWT FileDialog). [frame] oyna ochilgach beriladi. */
class AwtFileDialogs(var frame: Frame? = null) : FileDialogs {
    override fun save(title: String, defaultName: String, extension: String): Path? {
        val ext = "." + extension.removePrefix(".")
        val dialog = FileDialog(frame, title, FileDialog.SAVE)
        dialog.file = if (defaultName.endsWith(ext, ignoreCase = true)) defaultName else defaultName + ext
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val directory = dialog.directory ?: return null
        if (name.endsWith(ext, ignoreCase = true)) return Paths.get(directory, name)
        // Windows oynasi kengaytmani o‘zi qo‘shmaydi. Qo‘shilgan nom bilan fayl bo‘lsa — oyna buni so‘ramagan, biz so‘raymiz.
        val path = Paths.get(directory, name + ext)
        if (Files.exists(path)) {
            val options = arrayOf("Ustidan yozish", "Bekor qilish")
            val answer = JOptionPane.showOptionDialog(
                frame,
                "«${path.fileName}» fayli allaqachon bor. Ustidan yozilsinmi?",
                title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1],
            )
            if (answer != 0) return null
        }
        return path
    }

    override fun open(title: String, extensions: List<String>): Path? {
        val exts = extensions.map { it.removePrefix(".").lowercase() }
        val dialog = FileDialog(frame, title, FileDialog.LOAD)
        // Windows filtrni nom shablonidan oladi, boshqa tizimlar — filenameFilter’dan.
        if (exts.isNotEmpty()) {
            dialog.file = exts.joinToString(";") { "*.$it" }
            dialog.filenameFilter = java.io.FilenameFilter { _: File, name: String -> exts.any { name.lowercase().endsWith(".$it") } }
        }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val directory = dialog.directory ?: return null
        return Paths.get(directory, name)
    }

    override fun openFolder(dir: Path) {
        runCatching {
            Files.createDirectories(dir)
            Desktop.getDesktop().open(dir.toFile())
        }.onFailure { log.log(Level.WARNING, "open folder failed: $dir", it) }
    }
}

/**
 * sqlite-jdbc o‘z kutubxonasini (sqlitejdbc.dll) vaqtinchalik papkaga — java.io.tmpdir, ya’ni %TEMP% — chiqarib, System.load
 * bilan yuklaydi. Windows’da JVM DLL yo‘lini tizim kod sahifasiga (sun.jnu.encoding, masalan Cp1252) o‘giradi: foydalanuvchi
 * papkasi nomida unga sig‘maydigan harf bo‘lsa (kirill, Ғ/Қ/Ҳ, ʻ), yuklash yiqiladi (JDK-8195129) va baza ochilmaydi.
 * Shunday bo‘lsa yoki %TEMP% ga yozib bo‘lmasa — kutubxona ASCII papkaga chiqariladi (org.sqlite.tmpdir):
 * %ProgramData%\ReaktivControl\native\<foydalanuvchi>, bo‘lmasa %PUBLIC%\…. Hech biri yaroqsiz bo‘lsa — hech narsa o‘zgarmaydi.
 */
internal object SqliteNative {
    const val TMPDIR_PROPERTY = "org.sqlite.tmpdir"
    private const val MAX_CAUSES = 16

    /** [dir] — kutubxona chiqariladigan papka (null — kerak edi, lekin yaroqli papka topilmadi); [reason] — nima uchun. */
    data class Choice(val dir: Path?, val reason: String) {
        fun describe(): String =
            if (dir != null) "sqlite native dir: $dir ($reason)" else "sqlite native dir not changed, no usable ASCII folder ($reason)"
    }

    /** main() ning birinchi qatori: kerak bo‘lsa [TMPDIR_PROPERTY] ni o‘rnatadi. Hech qachon xato bermaydi. */
    fun configure(): Choice? {
        val choice = runCatching {
            choose(
                osName = System.getProperty("os.name").orEmpty(),
                jnuEncoding = System.getProperty("sun.jnu.encoding"),
                tmpDir = System.getProperty("java.io.tmpdir").orEmpty(),
                current = System.getProperty(TMPDIR_PROPERTY),
                userName = System.getProperty("user.name").orEmpty(),
                env = System::getenv,
            )
        }.getOrNull() ?: return null
        choice.dir?.let { System.setProperty(TMPDIR_PROPERTY, it.toString()) }
        return choice
    }

    /**
     * Qaror (sinash uchun — tizim xususiyatlari parametr sifatida). null — o‘zgartirish kerak emas: Windows emas, papka
     * allaqachon berilgan yoki %TEMP% yo‘li kodlashga sig‘adi va unga yozish mumkin. [usable] papkani yaratadi va yozib ko‘radi.
     */
    fun choose(
        osName: String,
        jnuEncoding: String?,
        tmpDir: String,
        current: String?,
        userName: String,
        env: (String) -> String?,
        usable: (Path) -> Boolean = ::prepare,
    ): Choice? {
        if (!current.isNullOrBlank() || !osName.startsWith("Windows", ignoreCase = true)) return null
        val reason = when {
            !encodable(tmpDir, jnuEncoding) -> "java.io.tmpdir=$tmpDir is not representable in $jnuEncoding"
            !runCatching { usable(Paths.get(tmpDir)) }.getOrDefault(false) -> "java.io.tmpdir=$tmpDir is not writable"
            else -> return null
        }
        val user = userFolder(userName)
        val dir = listOf("ProgramData", "ALLUSERSPROFILE", "PUBLIC")
            .mapNotNull { env(it)?.takeIf(String::isNotBlank) }
            .distinct()
            .mapNotNull { runCatching { Paths.get(it, DataDir.APP_DIR, "native", user) }.getOrNull() }
            .filter { encodable(it.toString(), jnuEncoding) }
            .firstOrNull { runCatching { usable(it) }.getOrDefault(false) }
        return Choice(dir, reason)
    }

    /** Yo‘lni JVM tizim kodlashiga (sun.jnu.encoding) yo‘qotishsiz o‘gira oladimi; kodlash noma’lum bo‘lsa — faqat ASCII. */
    fun encodable(path: String, encoding: String?): Boolean {
        val charset = encoding?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: return path.all { it.code < 128 }
        return charset.newEncoder().canEncode(path)
    }

    /** Har foydalanuvchiga alohida ASCII papka (bir kompyuterda bir nechta Windows foydalanuvchisi bo‘lsa, huquqlar to‘qnashmasin). */
    fun userFolder(userName: String): String {
        val ascii = userName.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }.take(24)
        return if (ascii.isNotEmpty() && ascii == userName) ascii else "%s-%08x".format(ascii.ifEmpty { "user" }, userName.hashCode())
    }

    /** Baza ochilmasligining sababi — SQLite kutubxonasi yuklanmagani (sabablar zanjirida). */
    fun isLoadFailure(e: Throwable): Boolean =
        generateSequence(e) { it.cause?.takeIf { cause -> cause !== it } }
            .take(MAX_CAUSES)
            .any { it is UnsatisfiedLinkError || it is NativeLibraryNotFoundException }

    /** sqlite-jdbc kutubxonani qaysi papkaga chiqaradi (xabar va jurnal uchun). */
    fun extractDir(): String = System.getProperty(TMPDIR_PROPERTY)?.takeIf { it.isNotBlank() } ?: System.getProperty("java.io.tmpdir").orEmpty()

    fun failureMessage(dbFile: Path, extractDir: String, logsDir: Path): String =
        "SQLite kutubxonasini (sqlitejdbc) yuklab bo‘lmadi — usiz ma’lumotlar bazasini ochib bo‘lmaydi.\n" +
            "Kutubxona shu papkaga chiqariladi: $extractDir\n" +
            "Odatda sababi: bu papkaga yozib bo‘lmaydi, antivirus undagi DLL faylni bloklagan yoki papka yo‘lida " +
            "lotin bo‘lmagan harflar bor.\n\n" +
            "Xato baza fayliga bog‘liq emas: baza va zaxira nusxalar o‘zgartirilmadi.\n$dbFile\n\n" +
            "Tafsilotlar jurnal faylida: $logsDir"

    private fun prepare(dir: Path): Boolean {
        Files.createDirectories(dir)
        Files.deleteIfExists(Files.createTempFile(dir, "probe-", ".tmp"))
        return true
    }
}

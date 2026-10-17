package uz.reaktivombor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.reaktivombor.data.AppDatabase
import uz.reaktivombor.data.Reagent
import uz.reaktivombor.domain.AlertStage
import uz.reaktivombor.domain.Backup
import uz.reaktivombor.domain.BackupException
import uz.reaktivombor.domain.ReagentValues
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReagentViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).reagentDao()

    /** null — hali yuklanmoqda. */
    val reagents: StateFlow<List<Reagent>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Tasdiq kutayotgan zaxira (ekran aylansa ham yo‘qolmaydi). */
    private val _pendingRestore = MutableStateFlow<Backup.Parsed?>(null)
    val pendingRestore: StateFlow<Backup.Parsed?> = _pendingRestore.asStateFlow()

    fun save(id: Long?, values: ReagentValues, onSaved: () -> Unit) {
        viewModelScope.launch {
            // Hozirgi bosqich “xabar berilgan” deb belgilanadi: o‘zingiz kiritgan narsa haqida eslatma kelmaydi.
            val stage = AlertStage.of(values.expiresAt, LocalDate.now()).ordinal
            val existing = id?.let { dao.getById(it) }
            if (existing == null) {
                dao.insert(
                    Reagent(
                        name = values.name,
                        receivedAt = values.receivedAt,
                        givenAmount = values.givenAmount,
                        unit = values.unit,
                        expiresAt = values.expiresAt,
                        stockAmount = values.stockAmount,
                        createdAt = System.currentTimeMillis(),
                        notifiedStage = stage,
                    ),
                )
                _messages.emit("«${values.name}» qo‘shildi.")
            } else {
                dao.update(
                    existing.copy(
                        name = values.name,
                        receivedAt = values.receivedAt,
                        givenAmount = values.givenAmount,
                        unit = values.unit,
                        expiresAt = values.expiresAt,
                        stockAmount = values.stockAmount,
                        // Faqat muddat o‘zgarganda bosqich yangilanadi: miqdorni tahrirlash kutilayotgan eslatmani “yutib” yubormaydi.
                        notifiedStage = if (existing.expiresAt == values.expiresAt) existing.notifiedStage else stage,
                    ),
                )
                _messages.emit("«${values.name}» saqlandi.")
            }
            onSaved()
        }
    }

    fun delete(reagent: Reagent, onDeleted: () -> Unit) {
        viewModelScope.launch {
            dao.delete(reagent)
            _messages.emit("«${reagent.name}» o‘chirildi.")
            onDeleted()
        }
    }

    fun backupFileName(): String = "reaktivlar-zaxira-${LocalDate.now()}.json"

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val items = dao.getAll()
                val json = Backup.toJson(items, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    // Ba’zi provayderlar (masalan, Google Drive) "wt" rejimini qo‘llamaydi.
                    val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: resolver.openOutputStream(uri, "w")
                        ?: throw IllegalStateException()
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
                "Zaxira nusxa saqlandi: ${items.size} ta reaktiv."
            } catch (e: Exception) {
                "Faylni saqlab bo‘lmadi. Boshqa joyni tanlab ko‘ring."
            }
            _messages.emit(message)
        }
    }

    /** Faylni o‘qiydi; tiklashni foydalanuvchi tasdiqlagandan keyin [confirmRestore] bajaradi. */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val stream = resolver.openInputStream(uri) ?: throw BackupException("Faylni ochib bo‘lmadi.")
                    stream.use { input ->
                        // Katta faylni butunlay xotiraga o‘qimaslik uchun bo‘lib-bo‘lib o‘qiymiz.
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            if (buffer.size() + read > MAX_BACKUP_BYTES) throw BackupException("Fayl juda katta — bu zaxira nusxa emas.")
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toString(Charsets.UTF_8.name())
                    }
                }
                val parsed = Backup.fromJson(text, LocalDate.now())
                if (parsed.reagents.isEmpty()) {
                    _messages.emit("Zaxira faylida tiklanadigan reaktiv topilmadi.")
                } else {
                    _pendingRestore.value = parsed
                }
            } catch (e: BackupException) {
                _messages.emit(e.message ?: "Bu fayl zaxira nusxa emas.")
            } catch (e: Exception) {
                _messages.emit("Faylni o‘qib bo‘lmadi.")
            }
        }
    }

    fun dismissRestore() {
        _pendingRestore.value = null
    }

    fun confirmRestore() {
        val parsed = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            dao.replaceAll(parsed.reagents)
            val skipped = if (parsed.skipped > 0) " ${parsed.skipped} ta buzuq qator tashlab ketildi." else ""
            _messages.emit("${parsed.reagents.size} ta reaktiv tiklandi.$skipped")
        }
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
    }
}

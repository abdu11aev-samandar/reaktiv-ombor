package uz.reaktivombor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.reaktivombor.data.AppDatabase
import uz.reaktivombor.data.Reagent
import uz.reaktivombor.domain.AlertStage
import uz.reaktivombor.domain.Backup
import uz.reaktivombor.domain.BackupException
import uz.reaktivombor.domain.ReagentValues
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
                        notifiedStage = stage,
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
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
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

    /** Faylni o‘qiydi; tiklashni foydalanuvchi tasdiqlagandan keyin [restore] bajaradi. */
    fun readBackup(uri: Uri, onParsed: (Backup.Parsed) -> Unit) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val stream = resolver.openInputStream(uri) ?: throw BackupException("Faylni ochib bo‘lmadi.")
                    stream.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > MAX_BACKUP_BYTES) throw BackupException("Fayl juda katta — bu zaxira nusxa emas.")
                        bytes.toString(Charsets.UTF_8)
                    }
                }
                val parsed = Backup.fromJson(text, LocalDate.now())
                if (parsed.reagents.isEmpty()) {
                    _messages.emit("Zaxira faylida tiklanadigan reaktiv topilmadi.")
                } else {
                    onParsed(parsed)
                }
            } catch (e: BackupException) {
                _messages.emit(e.message ?: "Bu fayl zaxira nusxa emas.")
            } catch (e: Exception) {
                _messages.emit("Faylni o‘qib bo‘lmadi.")
            }
        }
    }

    fun restore(parsed: Backup.Parsed) {
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

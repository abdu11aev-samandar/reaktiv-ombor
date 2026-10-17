package uz.reaktivombor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uz.reaktivombor.data.Reagent
import uz.reaktivombor.domain.Amounts
import uz.reaktivombor.domain.ExpiryStatus
import uz.reaktivombor.domain.Field
import uz.reaktivombor.domain.ReagentInput
import uz.reaktivombor.domain.ReagentValidator
import uz.reaktivombor.domain.ReagentValues
import uz.reaktivombor.domain.Validation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class DateTarget { RECEIVED, EXPIRES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReagentFormScreen(
    reagent: Reagent?,
    knownNames: List<String>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSave: (values: ReagentValues, andNew: Boolean) -> Unit,
    onDelete: (Reagent) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(reagent?.name ?: "") }
    var receivedEpoch by rememberSaveable { mutableStateOf(reagent?.receivedAt?.toEpochDay()) }
    var expiresEpoch by rememberSaveable { mutableStateOf(reagent?.expiresAt?.toEpochDay()) }
    var given by rememberSaveable { mutableStateOf(reagent?.givenAmount?.let(Amounts::forInput) ?: "") }
    var stock by rememberSaveable { mutableStateOf(reagent?.stockAmount?.let(Amounts::forInput) ?: "") }
    var unit by rememberSaveable { mutableStateOf(reagent?.unit?.takeIf { it in Amounts.UNITS } ?: "gr") }
    var pickerTarget by rememberSaveable { mutableStateOf<DateTarget?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Bir marta «Saqlash» bosilgach xatolar ko‘rsatiladi va yozilgan sari yangilanadi (ekran aylansa ham saqlanadi).
    var submitted by rememberSaveable { mutableStateOf(false) }
    // Ikki marta tez bosilganda ikki qator qo‘shilmasin.
    var saving by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val receivedAt = receivedEpoch?.let(LocalDate::ofEpochDay)
    val expiresAt = expiresEpoch?.let(LocalDate::ofEpochDay)
    val validation = ReagentValidator.validate(ReagentInput(name, receivedAt, expiresAt, given, stock, unit))
    val errors = if (submitted) (validation as? Validation.Invalid)?.errors.orEmpty() else emptyMap()

    fun submit(andNew: Boolean) {
        if (saving) return
        focus.clearFocus()
        submitted = true
        if (validation is Validation.Valid) {
            saving = true
            onSave(validation.values, andNew)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reagent == null) "Reaktiv qo‘shish" else "Tahrirlash", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Ro‘yxatga qaytish")
                    }
                },
                actions = {
                    if (reagent != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "O‘chirish", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reaktiv nomi *") },
                isError = Field.NAME in errors,
                supportingText = errors[Field.NAME]?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )

            val typed = searchKey(name.trim())
            val suggestions = if (typed.isEmpty()) {
                emptyList()
            } else {
                knownNames.filter { searchKey(it).contains(typed) && it != name.trim() }.take(4)
            }
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(onClick = { name = suggestion }, label = { Text(suggestion) })
                    }
                }
            }

            DateField("Kelgan vaqt", receivedAt, errors[Field.RECEIVED_AT]) { pickerTarget = DateTarget.RECEIVED }
            DateField("Yaroqlilik muddati", expiresAt, errors[Field.EXPIRES_AT]) { pickerTarget = DateTarget.EXPIRES }

            Text("O‘lchov birligi *", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Amounts.UNITS.forEach { option ->
                    FilterChip(
                        selected = unit == option,
                        onClick = { unit = option },
                        label = { Text(option) },
                        colors = selectedChipColors(),
                    )
                }
            }

            AmountField("Berilgan miqdor *", given, unit, errors[Field.GIVEN_AMOUNT], "masalan: 500") { given = it }
            AmountField("Ombordagi miqdor *", stock, unit, errors[Field.STOCK_AMOUNT], "masalan: 250") { stock = it }

            Text(
                "Rang muddatga qarab avtomatik qo‘yiladi: 🟢 3 oydan ko‘p, 🟡 1–3 oy, 🔴 1 oydan kam yoki o‘tgan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            expiresAt?.let { date ->
                val status = ExpiryStatus.of(date, LocalDate.now())
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ro‘yxatda shunday ko‘rinadi:", style = MaterialTheme.typography.bodySmall)
                    NamePill(name.trim().ifEmpty { "Reaktiv nomi" }, status)
                }
            }

            if (errors.isNotEmpty()) {
                Text(
                    "Saqlanmadi: qizil bilan belgilangan maydonlarni to‘g‘rilang.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = { submit(andNew = false) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text("Saqlash")
            }
            if (reagent == null) {
                OutlinedButton(onClick = { submit(andNew = true) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("Saqlash va yana qo‘shish")
                }
            }
        }
    }

    pickerTarget?.let { target ->
        key(target) {
            val current = if (target == DateTarget.RECEIVED) receivedAt else expiresAt
            val state = rememberDatePickerState(
                initialSelectedDateMillis = current?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { pickerTarget = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let { millis ->
                                // DatePicker UTC bilan ishlaydi.
                                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                                if (target == DateTarget.RECEIVED) receivedEpoch = picked else expiresEpoch = picked
                            }
                            pickerTarget = null
                        },
                    ) { Text("Tanlash") }
                },
                dismissButton = { TextButton(onClick = { pickerTarget = null }) { Text("Bekor qilish") } },
            ) {
                DatePicker(
                    state = state,
                    title = {
                        Text(
                            if (target == DateTarget.RECEIVED) "Kelgan vaqt" else "Yaroqlilik muddati",
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                        )
                    },
                )
            }
        }
    }

    if (confirmDelete && reagent != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("O‘chirilsinmi?") },
            text = { Text("«${reagent.name}» ro‘yxatdan butunlay o‘chiriladi.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(reagent) }) {
                    Text("Ha, o‘chirish", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Bekor qilish") } },
        )
    }
}

@Composable
private fun DateField(label: String, value: LocalDate?, error: String?, onClick: () -> Unit) {
    val spoken = buildString {
        append(label).append(", majburiy: ")
        append(value?.format(DATE_FORMAT) ?: "tanlanmagan")
        if (error != null) append(". ").append(error)
    }
    Box {
        OutlinedTextField(
            value = value?.format(DATE_FORMAT) ?: "",
            onValueChange = {},
            readOnly = true,
            // TalkBack uchun bitta element qoladi — pastdagi bosiladigan qatlam.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            label = { Text("$label *") },
            placeholder = { Text("Sanani tanlang") },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
        )
        // TextField bosilishini o‘zi “yutib” yuboradi, shuning uchun ustiga shaffof qatlam qo‘yamiz.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = "Sanani tanlash", role = Role.Button, onClick = onClick)
                .semantics { contentDescription = spoken },
        )
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    unit: String,
    error: String?,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        suffix = { Text(unit) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

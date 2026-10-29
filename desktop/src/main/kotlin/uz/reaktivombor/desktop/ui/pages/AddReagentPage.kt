package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.reaktivombor.desktop.core.DuplicateBatchError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.Reagent
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ComboField
import uz.reaktivombor.desktop.ui.components.DateField
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.FormSection
import uz.reaktivombor.desktop.ui.components.IconAction
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.style
import java.time.LocalDate

/** «Reaktiv qo‘shish» shakli maydonlari. Saqlangandan keyin bir xil bo‘ladiganlari (sana, bo‘lim, xodimlar) qoladi. */
@Stable
private class AddReagentForm(private val today: LocalDate, private val receiver: String) {
    var name by mutableStateOf("")
    var formula by mutableStateOf("")
    var manufacturer by mutableStateOf("")
    var series by mutableStateOf("")
    var producedOn by mutableStateOf<LocalDate?>(null)
    var receivedOn by mutableStateOf<LocalDate?>(today)
    var qty by mutableStateOf("")
    var unit by mutableStateOf<MeasureUnit?>(null)
    var expiresOn by mutableStateOf<LocalDate?>(null)
    var departmentId by mutableStateOf<Long?>(null)
    var storage by mutableStateOf("")
    var responsible by mutableStateOf("")
    var receivedBy by mutableStateOf(receiver)
    var minStock by mutableStateOf("")
    var minUnit by mutableStateOf<MeasureUnit?>(null)
    var note by mutableStateOf("")

    fun toInput() = NewBatch(
        name = name,
        formula = formula,
        manufacturer = manufacturer,
        series = series,
        producedOn = producedOn,
        receivedOn = receivedOn,
        initialQtyText = qty,
        unit = unit,
        expiresOn = expiresOn,
        departmentId = departmentId,
        storageLocation = storage,
        responsible = responsible,
        minStockText = minStock.ifBlank { null },
        minStockUnit = if (minStock.isBlank()) null else minUnit ?: unit,
        note = note,
        receivedBy = receivedBy,
    )

    /** Keyingi partiya uchun: reaktiv va partiya maydonlari tozalanadi, sana / bo‘lim / joy / xodimlar qoladi. */
    fun resetForNext() {
        name = ""
        formula = ""
        manufacturer = ""
        series = ""
        producedOn = null
        qty = ""
        unit = null
        expiresOn = null
        minStock = ""
        minUnit = null
        note = ""
    }

    fun clearAll() {
        resetForNext()
        receivedOn = today
        departmentId = null
        storage = ""
        responsible = ""
        receivedBy = receiver
    }
}

@Immutable
private data class AddReagentSaved(val batchId: Long, val reagentId: Long, val text: String)

/**
 * «Reaktiv qo‘shish»: reaktiv kartasi (texnik topshiriq §3) — yangi partiya va uning boshlang‘ich kirimi.
 * Xatolar maydon yonida; bir xil partiya bo‘lsa — mavjud partiyaga qo‘shimcha kirim taklif qilinadi.
 * Shakl ostida — «Exceldan import» (AddReagentImport.kt).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddReagentPage(state: AppState) {
    val today = remember { state.today() }
    val actor = remember { state.load("") { state.services.settings.actorName } }
    val form = remember { AddReagentForm(today, actor) }
    val errors = remember { FormErrors() }
    val confirm = rememberConfirmState()
    var saved by remember { mutableStateOf<AddReagentSaved?>(null) }
    // Shakl tozalanganda maydonlar yangidan tuziladi (sana maydonlarining «tegilgan» holati ham tozalansin).
    var generation by remember { mutableIntStateOf(0) }
    val tick = state.refreshTick
    // Sarlavhadagi «Exceldan import» — pastdagi import bo‘limiga aylantiradi.
    val importView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val suggestions = remember(tick, form.name) { state.load(emptyList()) { state.services.reagents.searchNames(form.name, 30) } }
    val allReagents = remember(tick) { state.load(emptyList()) { state.services.reagents.list(includeArchived = true) } }
    val existing: Reagent? = form.name.takeIf { it.isNotBlank() }?.let { typed -> allReagents.firstOrNull { nameKey(it.name) == nameKey(typed) } }

    fun pickName(picked: String) {
        val reagent = allReagents.firstOrNull { nameKey(it.name) == nameKey(picked) } ?: return
        if (form.formula.isBlank()) form.formula = reagent.formula
        if (form.minStock.isBlank() && reagent.minStock != null && reagent.minStockUnit != null) {
            form.minStock = Qty.format(reagent.minStock, reagent.minStockUnit)
            form.minUnit = reagent.minStockUnit
        }
    }

    fun save(confirmDuplicate: Boolean = false) {
        val input = form.toInput()
        state.run(
            { state.services.reagents.createBatch(input, confirmDuplicate) },
            form = errors,
            onError = { e ->
                when {
                    e is DuplicateBatchError && e.exact -> {
                        confirm.ask(
                            title = "Bu partiya allaqachon bor",
                            message = e.message,
                            confirmLabel = "Mavjud partiyaga qo‘shimcha kirim",
                            dismissLabel = "Orqaga",
                        ) { state.navigate(Page.RECEIPTS, PageIntent.ReceiptFor(e.existingBatchId)) }
                        true
                    }
                    e is DuplicateBatchError -> {
                        confirm.ask(
                            title = "Shu seriyali partiya bor",
                            message = e.message,
                            confirmLabel = "Alohida partiya sifatida saqlash",
                            dismissLabel = "Orqaga",
                        ) { save(confirmDuplicate = true) }
                        true
                    }
                    else -> false
                }
            },
        ) { batchId ->
            val reagentId = state.services.reagents.batch(batchId)?.reagentId ?: 0L
            val amount = input.unit?.let { unit -> runCatching { qtyText(Qty.parse(input.initialQtyText, unit), unit) }.getOrNull() }
            saved = AddReagentSaved(
                batchId,
                reagentId,
                batchQuote(input.name.trim(), input.series.trim()) +
                    (amount?.let { " — $it" } ?: "") +
                    (input.expiresOn?.let { ", muddati ${dateText(it)}" } ?: "") + ".",
            )
            form.resetForNext()
            generation++
        }
    }

    val unitCodes = MeasureUnit.entries.map { it.code }
    // Ikkalasi ham o‘qiladi — shaklda ko‘rsatilgan maydonlar sifatida belgilanadi.
    val qtyError = errors["initialQtyText"]
    val unitError = errors["unit"]
    val minUnits = form.unit?.siblings ?: MeasureUnit.entries
    ScrollPage(
        title = Page.ADD_REAGENT.title,
        subtitle = "Yangi partiya va uning boshlang‘ich kirimi. Majburiy maydonlar * bilan belgilangan.",
        actions = {
            AppButton(
                "Exceldan import",
                onClick = { scope.launch { runCatching { importView.bringIntoView() } } },
                kind = ButtonKind.TEXT,
                icon = AppIcons.Excel,
                modifier = Modifier.testTag("add-goto-import"),
            )
            AppButton("Barcha reaktivlar", onClick = { state.navigate(Page.REAGENTS) }, kind = ButtonKind.SECONDARY, icon = AppIcons.Reagents)
        },
        testTag = "add-reagent",
    ) {
        saved?.let { info ->
            AddReagentSuccess(
                text = info.text,
                onMore = { saved = null },
                onList = { state.navigate(Page.REAGENTS, PageIntent.ReagentsFiltered(reagentId = info.reagentId)) },
                onClose = { saved = null },
            )
        }
        key(generation) {
            FormSection("Reaktiv", description = "Bir xil nomdagi partiyalar bitta reaktiv sifatida jamlanadi") {
                FormRow {
                    ComboField(
                        value = form.name,
                        onValueChange = {
                            form.name = it
                            errors.clear("name")
                        },
                        suggestions = suggestions,
                        label = "Reaktiv nomi",
                        required = true,
                        placeholder = "Masalan: Etil spirti 96%",
                        error = errors["name"],
                        help = existing?.let { "Mavjud reaktiv — yangi partiya shu nom ostiga qo‘shiladi" + if (it.archived) " (arxivdan chiqariladi)" else "" },
                        onPick = { pickName(it) },
                        modifier = Modifier.weight(2f),
                        testTag = "add-name",
                    )
                    AppTextField(form.formula, { form.formula = it; errors.clear("formula") }, label = "Formula", placeholder = "C₂H₅OH", error = errors["formula"], modifier = Modifier.weight(1f), testTag = "add-formula")
                    AppTextField(form.manufacturer, { form.manufacturer = it; errors.clear("manufacturer") }, label = "Ishlab chiqaruvchi", error = errors["manufacturer"], modifier = Modifier.weight(1.3f), testTag = "add-manufacturer")
                }
            }
            FormSection("Partiya va kirim", description = "Nom + seriya + yaroqlilik muddati + birlik — bitta partiya") {
                FormRow {
                    AppTextField(form.series, { form.series = it; errors.clear("series") }, label = "Seriya raqami", required = true, error = errors["series"], modifier = Modifier.weight(1f), testTag = "add-series")
                    DateField(
                        form.producedOn, { form.producedOn = it; errors.clear("producedOn") }, label = "Ishlab chiqarilgan sana", today = today, maxDate = today,
                        error = errors["producedOn"], modifier = Modifier.weight(1f), testTag = "add-produced",
                    )
                    DateField(
                        form.receivedOn, { form.receivedOn = it; errors.clear("receivedOn") }, label = "Kelgan sana", required = true, today = today, maxDate = today,
                        error = errors["receivedOn"], modifier = Modifier.weight(1f), testTag = "add-received",
                    )
                    DateField(
                        form.expiresOn, { form.expiresOn = it; errors.clear("expiresOn") }, label = "Yaroqlilik muddati", required = true, today = today,
                        error = errors["expiresOn"], help = expiryHelpText(form.expiresOn, today), modifier = Modifier.weight(1f), testTag = "add-expires",
                    )
                }
                FormRow {
                    QtyField(
                        amount = form.qty,
                        onAmountChange = { form.qty = it; errors.clear("initialQtyText") },
                        unit = form.unit?.code,
                        onUnitChange = { code ->
                            val unit = MeasureUnit.of(code)
                            form.unit = unit
                            if (form.minUnit?.dimension != unit?.dimension) form.minUnit = unit
                            errors.clear("unit")
                        },
                        units = unitCodes,
                        label = "Kelgan miqdor va o‘lchov birligi",
                        required = true,
                        error = qtyError ?: unitError,
                        help = if (form.unit == null) "Birlikni tanlang: l, ml, kg, g yoki dona" else null,
                        modifier = Modifier.weight(1.2f),
                        testTag = "add-qty",
                    )
                    AppTextField(form.receivedBy, { form.receivedBy = it; errors.clear("receivedBy") }, label = "Qabul qilgan xodim", error = errors["receivedBy"], modifier = Modifier.weight(1f), testTag = "add-receiver")
                    QtyField(
                        amount = form.minStock,
                        onAmountChange = { form.minStock = it; errors.clear("minStockText") },
                        unit = (form.minUnit ?: form.unit)?.code,
                        onUnitChange = { code -> form.minUnit = MeasureUnit.of(code) },
                        units = minUnits.map { it.code },
                        label = "Minimal qoldiq",
                        error = errors["minStockText"],
                        help = existing?.minStock?.let { min ->
                            "Hozirgi: ${qtyText(min, existing.minStockUnit ?: MeasureUnit.L)} (bo‘sh qoldirilsa o‘zgarmaydi)"
                        } ?: "Umumiy qoldiq shundan kam bo‘lsa ogohlantiriladi",
                        modifier = Modifier.weight(1f),
                        testTag = "add-min",
                    )
                }
            }
            FormSection("Saqlash va mas’uliyat") {
                FormRow {
                    DepartmentSelect(state, form.departmentId, { form.departmentId = it; errors.clear("departmentId") }, error = errors["departmentId"], modifier = Modifier.weight(1f), testTag = "add-department")
                    AppTextField(form.storage, { form.storage = it; errors.clear("storageLocation") }, label = "Saqlash joyi", placeholder = "Masalan: 1-shkaf, sovutgich", error = errors["storageLocation"], modifier = Modifier.weight(1.2f), testTag = "add-storage")
                    AppTextField(form.responsible, { form.responsible = it; errors.clear("responsible") }, label = "Mas’ul xodim", error = errors["responsible"], modifier = Modifier.weight(1f), testTag = "add-responsible")
                }
                AppTextField(form.note, { form.note = it; errors.clear("note") }, label = "Izoh", error = errors["note"], modifier = Modifier.fillMaxWidth(), testTag = "add-note")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (errors.fields.isNotEmpty()) {
                Text(
                    "Saqlanmadi: ${errors.fields.size} ta maydonda xato bor.",
                    fontSize = 13.sp,
                    color = AppColors.Error,
                    modifier = Modifier.weight(1f).testTag("add-error-summary"),
                )
            }
            AppButton(
                "Tozalash",
                onClick = {
                    form.clearAll()
                    errors.clear()
                    saved = null
                    generation++
                },
                kind = ButtonKind.SECONDARY,
                icon = AppIcons.Refresh,
            )
            AppButton("Saqlash", onClick = { save() }, icon = AppIcons.Check, enabled = !state.busy, modifier = Modifier.testTag("add-save"))
        }
        AddReagentExcelImport(state, confirm, Modifier.fillMaxWidth().bringIntoViewRequester(importView))
    }
    ConfirmHost(confirm, state.busy)
}

/** Saqlangandan keyingi yashil panel: «Yana qo‘shish» va «Ro‘yxatda ko‘rish». */
@Composable
private fun AddReagentSuccess(text: String, onMore: () -> Unit, onList: () -> Unit, onClose: () -> Unit) {
    val style = Severity.SUCCESS.style
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(style.background)
            .testTag("add-success"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(style.accent))
        Row(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Success, contentDescription = null, tint = style.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).widthIn(min = 200.dp)) {
                Text("Partiya saqlandi", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = style.foreground)
                Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = style.foreground)
            }
            AppButton("Yana qo‘shish", onClick = onMore, kind = ButtonKind.TEXT, icon = AppIcons.Add)
            AppButton("Ro‘yxatda ko‘rish", onClick = onList, kind = ButtonKind.TEXT, icon = AppIcons.Reagents)
            IconAction(AppIcons.Close, "Yopish", onClick = onClose, tint = style.foreground, size = 28.dp, iconSize = 16.dp)
        }
    }
}

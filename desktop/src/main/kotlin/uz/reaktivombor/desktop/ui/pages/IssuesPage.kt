package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.DuplicateOperationError
import uz.reaktivombor.desktop.core.ExpiredBatchError
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.InsufficientStockError
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.BatchStock
import uz.reaktivombor.desktop.service.IssueConfirm
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.FormErrors
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.PageIntent
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.DateField
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.FormSection
import uz.reaktivombor.desktop.ui.theme.AppColors
import java.time.LocalDate

/** Berish shaklining bitta qatori (partiya, miqdor, kimga). [errors] — maydon xatolari, [message] — qatorning umumiy xatosi. */
@Stable
internal class IssuesLineDraft(val key: Long) {
    var reagentText by mutableStateOf("")
    var reagentId by mutableStateOf<Long?>(null)
    var batchId by mutableStateOf<Long?>(null)
    var qty by mutableStateOf("")
    var unit by mutableStateOf<MeasureUnit?>(null)
    var type by mutableStateOf(RecipientType.DEPARTMENT)
    var departmentId by mutableStateOf<Long?>(null)
    var districtName by mutableStateOf("")
    var organization by mutableStateOf("")
    var receiver by mutableStateOf("")
    var note by mutableStateOf("")
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    var message by mutableStateOf<String?>(null)

    fun clearError(field: String) {
        if (field in errors) errors = errors - field
        message = null
    }

    /** Hech narsa kiritilmagan (taqsimlashda o‘rniga yangi qatorlar qo‘yiladi). */
    val isBlank: Boolean
        get() = reagentText.isBlank() && qty.isBlank() && organization.isBlank() && receiver.isBlank() && note.isBlank() &&
            departmentId == null && districtName.isBlank()

    fun toLine(): IssueLine = IssueLine(
        batchId = batchId ?: 0L,
        qtyText = qty,
        unit = unit,
        recipientType = type,
        departmentId = if (type == RecipientType.DEPARTMENT) departmentId else null,
        districtName = if (type == RecipientType.DISTRICT) districtName else null,
        organization = if (type == RecipientType.WRITE_OFF) "" else organization,
        receiver = if (type == RecipientType.WRITE_OFF) "" else receiver,
        note = note,
    )
}

/** Berish shakli: sarlavha (sana, hujjat, beruvchi, izoh) va qatorlar. */
@Stable
private class IssuesPageForm(private val today: LocalDate, private val issuer: String) {
    var date by mutableStateOf<LocalDate?>(today)
    var docNo by mutableStateOf("")
    var issuedBy by mutableStateOf(issuer)
    var note by mutableStateOf("")
    val lines = mutableStateListOf<IssuesLineDraft>()
    val header = FormErrors()
    private var nextKey = 1L

    fun addLine(): IssuesLineDraft = IssuesLineDraft(nextKey++).also { lines += it }

    /** Saqlangandan keyin: sana va beruvchi qoladi, qolgani tozalanadi. */
    fun resetForNext() {
        docNo = ""
        note = ""
        lines.clear()
        addLine()
        header.clear()
    }

    fun clearAll() {
        resetForNext()
        date = today
        issuedBy = issuer
    }
}

/** Qoldig‘i bor reaktiv (berish shaklidagi tanlov uchun): o‘lchov turlari bo‘yicha jami qoldiq. */
@Immutable
internal data class IssuesReagentOption(val id: Long, val name: String, val totals: Map<Dimension, Long>, val batches: Int)

@Immutable
private data class IssuesSaved(val issueId: Long, val text: String)

/** Qatorning xato maydonlari (servis maydon nomlari). */
private val ISSUE_LINE_FIELDS = setOf("qtyText", "unit", "departmentId", "districtId", "districtName", "organization", "receiver", "note")

/** Reaktivning qoldig‘i bor partiyalari FEFO tartibida (hamma o‘lchov turlari). */
internal fun issuesFefoOrder(state: AppState, reagentId: Long, today: LocalDate): List<BatchStock> =
    state.load(emptyList()) { Dimension.entries.flatMap { state.services.stock.fefoOrder(reagentId, it, today) } }

/**
 * «Sarf va berish»: bir hujjat bilan istalgancha qator (reaktiv → FEFO bo‘yicha partiya, miqdor, kimga) — hammasi bitta
 * operatsiyada saqlanadi yoki hech biri. Qoldiq har bir qatorda jonli tekshiriladi; FEFO va muddati o‘tgan partiya
 * ogohlantirishlari; pastda berish tarixi va qaytarishlar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IssuesPage(state: AppState) {
    val intent = remember { state.intent }
    val tick = state.refreshTick
    val today = remember(tick) { state.today() }
    val actor = remember { state.load("") { state.services.settings.actorName } }
    val stockRows = remember(tick, today) { state.load(emptyList()) { state.services.queries.batchRows(BatchFilter(includeEmpty = false), today) } }
    val reagentOptions = remember(stockRows) {
        stockRows.groupBy { it.reagentId }.map { (id, rows) ->
            IssuesReagentOption(
                id = id,
                name = rows.first().name,
                totals = rows.groupBy { it.dimension }.mapValues { (_, list) -> list.sumOf { it.remainder } },
                batches = rows.size,
            )
        }.sortedBy { nameKey(it.name) }
    }
    val reagentNames = remember(reagentOptions) { reagentOptions.map { it.name } }
    val districtNames = remember(tick) { state.load(emptyList()) { state.services.directory.districts() }.map { it.name } }

    val form = remember {
        IssuesPageForm(today, actor).also { f ->
            val line = f.addLine()
            (intent as? PageIntent.IssueFrom)?.let { target ->
                val row = stockRows.firstOrNull { it.batchId == target.batchId }
                if (row != null) {
                    line.reagentText = row.name
                    line.reagentId = row.reagentId
                    line.batchId = row.batchId
                    line.unit = row.unit
                } else {
                    line.message = "Tanlangan partiyada qoldiq yo‘q yoki u arxivda — boshqa partiyani tanlang."
                }
            }
        }
    }
    // Saqlanmagan qatorlar bilan boshqa sahifaga o‘tilsa — tasdiq so‘raladi (Common.kt, rememberLeaveGuard).
    rememberLeaveGuard(state) {
        val lines = form.lines.filterNot { it.isBlank }
            .map { listOf(it.reagentText, it.batchId, it.qty, it.unit, it.type, it.departmentId, it.districtName, it.organization, it.receiver, it.note) }
        if (lines.isEmpty() && form.docNo.isBlank() && form.note.isBlank()) null else listOf(form.docNo, form.note, lines)
    }
    val confirm = rememberConfirmState()
    var splitOpen by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<IssuesSaved?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val historyRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val issueDate = form.date ?: today
    val reagentIds = form.lines.mapNotNull { it.reagentId }.toSet()
    val fefo = remember(tick, today, reagentIds) { reagentIds.associateWith { issuesFefoOrder(state, it, today) } }
    val batchIds = form.lines.mapNotNull { it.batchId }.toSet()
    val available = remember(tick, issueDate, batchIds) {
        batchIds.associateWith { id -> state.load(0L) { state.services.stock.available(id, issueDate) } }
    }
    val checks = issuesLineChecks(form.lines, fefo, available, issueDate)
    val invalidLines = checks.indices.filter { checks[it].invalid }

    fun chooseBatch(line: IssuesLineDraft, batch: BatchStock) {
        line.batchId = batch.batchId
        if (line.unit?.dimension != batch.unit.dimension) line.unit = batch.unit
        line.clearError("batchId")
        line.clearError("unit")
    }

    /** Reaktiv nomi o‘zgardi: ro‘yxatdagi nomga to‘liq mos kelsa — reaktiv tanlanadi va FEFO tavsiyasi qo‘yiladi. */
    fun setReagent(line: IssuesLineDraft, text: String) {
        line.reagentText = text
        line.clearError("reagent")
        val option = reagentOptions.firstOrNull { nameKey(it.name) == nameKey(text) }
        if (option?.id == line.reagentId) return
        line.reagentId = option?.id
        line.batchId = null
        if (option != null) {
            val recommended = issuesFefoOrder(state, option.id, today).firstOrNull { it.state != ExpiryState.EXPIRED }
            if (recommended != null) chooseBatch(line, recommended)
        }
    }

    fun validate(): Boolean {
        val headerErrors = linkedMapOf<String, String>()
        if (form.date == null) headerErrors["date"] = "Berish sanasi kiritilishi shart."
        form.header.show(headerErrors)
        var ok = headerErrors.isEmpty()
        form.lines.forEachIndexed { i, line ->
            val e = linkedMapOf<String, String>()
            when {
                line.reagentText.isBlank() -> e["reagent"] = "Reaktivni tanlang."
                line.reagentId == null -> e["reagent"] = "Bunday nomli, omborda qoldig‘i bor reaktiv topilmadi."
                line.batchId == null -> e["batchId"] = "Partiyani tanlang."
            }
            when {
                line.qty.isBlank() -> e["qtyText"] = "Miqdor kiritilishi shart."
                line.unit == null -> e["unit"] = "O‘lchov birligini tanlang."
            }
            when (line.type) {
                RecipientType.DEPARTMENT -> if (line.departmentId == null) e["departmentId"] = "Bo‘limni tanlang."
                RecipientType.DISTRICT -> if (line.districtName.isBlank()) e["districtName"] = "Tumanni tanlang yoki yozing."
                RecipientType.WRITE_OFF -> if (line.note.isBlank()) e["note"] = "Hisobdan chiqarish sababini yozing."
            }
            line.errors = e
            line.message = null
            if (e.isNotEmpty() || checks.getOrNull(i)?.invalid == true) ok = false
        }
        return ok
    }

    fun lineNumbers(indices: List<Int>): String = indices.joinToString(", ") { "${it + 1}" } + if (indices.size > 1) "-qatorlarda" else "-qatorda"

    /** Servis xatosini tegishli qator yoki maydonga qo‘yadi ("2-qator: …" — 2-qatorga). */
    fun placeError(e: UserError) {
        val prefix = Regex("""^(\d+)-qator: """).find(e.message)
        val index = prefix?.groupValues?.get(1)?.toIntOrNull()?.minus(1)
        val field = e.fields.keys.firstOrNull()
        val text = if (prefix != null) e.message.removePrefix(prefix.value).replaceFirstChar { it.uppercase() } else e.message
        val line = when {
            index != null -> form.lines.getOrNull(index)
            form.lines.size == 1 && field in ISSUE_LINE_FIELDS -> form.lines.first()
            else -> null
        }
        when {
            line != null && field in ISSUE_LINE_FIELDS -> line.errors = line.errors + (field!! to text)
            line != null -> line.message = text
            field in setOf("date", "docNo", "issuedBy", "note") -> form.header.show(form.header.fields + (field!! to e.message))
            else -> state.error(e.message)
        }
        summary = "Saqlanmadi — xatoni tuzating. Hech narsa saqlanmadi."
    }

    fun submit(flags: IssueConfirm = IssueConfirm.none) {
        summary = null
        if (!validate()) {
            val bad = form.lines.indices.filter { form.lines[it].errors.isNotEmpty() || checks.getOrNull(it)?.invalid == true }
            summary = if (bad.isEmpty()) "Saqlanmadi: sanani tekshiring." else "Saqlanmadi: ${lineNumbers(bad)} xato bor."
            return
        }
        if (!flags.allowExpired) {
            val expired = form.lines.indices.filter { checks[it].expired && form.lines[it].type != RecipientType.WRITE_OFF }
            if (expired.isNotEmpty()) {
                val details = expired.joinToString("\n") { i ->
                    val b = checks[i].batch!!
                    "• ${i + 1}-qator: ${batchQuote(form.lines[i].reagentText, b.series)} — muddati ${dateText(b.expiresOn)} da o‘tgan"
                }
                confirm.ask(
                    title = "Muddati o‘tgan partiyadan berish",
                    message = "$details\n\nYaroqlilik muddati o‘tgan reaktivni berish tavsiya etilmaydi. Baribir berilsa, operatsiya tarixida " +
                        "«muddati o‘tgan partiyadan berildi (tasdiqlangan)» deb belgilanadi.",
                    confirmLabel = "Baribir berish",
                    danger = true,
                    dismissLabel = "Orqaga",
                ) { submit(flags.copy(allowExpired = true)) }
                return
            }
        }
        val input = IssueInput(date = form.date, docNo = form.docNo, issuedBy = form.issuedBy, note = form.note, lines = form.lines.map { it.toLine() })
        val totals = issuesTotalsText(form.lines, checks, reagentOptions)
        val lineCount = form.lines.size
        state.run(
            { state.services.stock.issue(input, flags) },
            onError = { e ->
                when (e) {
                    is ExpiredBatchError -> confirm.ask(
                        title = "Muddati o‘tgan partiyadan berish",
                        message = e.message,
                        confirmLabel = "Baribir berish",
                        danger = true,
                        dismissLabel = "Orqaga",
                    ) { submit(flags.copy(allowExpired = true)) }
                    is DuplicateOperationError -> confirm.ask(
                        title = "Takroriy berish",
                        message = e.message,
                        confirmLabel = "Baribir saqlash",
                        dismissLabel = "Orqaga",
                    ) { submit(flags.copy(allowDuplicate = true)) }
                    is InsufficientStockError -> {
                        form.lines.filter { it.batchId == e.batchId }.forEach { it.message = e.message }
                        summary = "Saqlanmadi: qoldiq yetarli emas. Hech narsa saqlanmadi."
                        // Qoldiq boshqa joyda o‘zgargan bo‘lishi mumkin — jonli tekshiruv yangilansin.
                        state.refresh()
                    }
                    else -> placeError(e)
                }
                true
            },
        ) { issueId ->
            val doc = input.docNo?.trim().orEmpty()
            saved = IssuesSaved(
                issueId,
                "$lineCount ta qator" + (if (doc.isNotEmpty()) ", hujjat $doc" else "") + ", ${dateText(input.date)}. $totals",
            )
            form.resetForNext()
            summary = null
            generation++
        }
    }

    fun applySplit(result: IssuesSplitResult) {
        form.lines.removeAll { it.isBlank || it.reagentId == result.reagentId }
        result.parts.forEach { (batchId, base) ->
            val line = form.addLine()
            line.reagentText = result.reagentName
            line.reagentId = result.reagentId
            line.batchId = batchId
            val (text, unit) = opsEditableQty(base, result.unit)
            line.qty = text
            line.unit = unit
            line.type = result.type
            line.departmentId = result.departmentId
            line.districtName = result.districtName
            line.organization = result.organization
            line.receiver = result.receiver
            line.note = result.note
        }
        if (form.lines.isEmpty()) form.addLine()
        summary = null
    }

    ScrollPage(
        title = Page.ISSUES.title,
        subtitle = "Bir hujjat bilan bir nechta qator — hammasi bitta operatsiyada saqlanadi. Partiya FEFO bo‘yicha tavsiya etiladi.",
        actions = {
            AppButton(
                "Berish tarixi",
                onClick = { scope.launch { historyRequester.bringIntoView() } },
                kind = ButtonKind.TEXT,
                icon = AppIcons.ChevronDown,
                modifier = Modifier.testTag("issue-goto-history"),
            )
        },
        testTag = "issues-page",
    ) {
        saved?.let { info ->
            val requester = remember { BringIntoViewRequester() }
            // Saqlash tugmasi sahifa pastida — natija paneli ko‘rinadigan joyga aylantiriladi.
            LaunchedEffect(info) {
                withFrameNanos { }
                requester.bringIntoView()
            }
            Box(Modifier.bringIntoViewRequester(requester)) {
                OpsSuccessPanel(
                    "Berish №${info.issueId} saqlandi",
                    info.text,
                    onClose = { saved = null },
                    testTag = "issue-success",
                )
            }
        }
        key(generation) {
            FormSection("Hujjat", description = "Talabnoma yoki dalolatnoma ma’lumotlari — barcha qatorlar uchun umumiy") {
                FormRow {
                    DateField(
                        form.date,
                        {
                            form.date = it
                            form.header.clear("date")
                        },
                        label = "Berish sanasi", required = true, today = today, maxDate = today, error = form.header["date"],
                        modifier = Modifier.weight(0.8f), testTag = "issue-date",
                    )
                    AppTextField(form.docNo, { form.docNo = it; form.header.clear("docNo") }, label = "Hujjat / talabnoma raqami", placeholder = "Masalan: T-55", error = form.header["docNo"], modifier = Modifier.weight(1f), testTag = "issue-doc")
                    AppTextField(form.issuedBy, { form.issuedBy = it; form.header.clear("issuedBy") }, label = "Beruvchi mas’ul xodim", error = form.header["issuedBy"], modifier = Modifier.weight(1f), testTag = "issue-issuer")
                    AppTextField(form.note, { form.note = it; form.header.clear("note") }, label = "Izoh", error = form.header["note"], modifier = Modifier.weight(1.6f), testTag = "issue-note")
                }
            }
            FormSection(
                "Qatorlar",
                description = "Har bir qator — bitta partiyadan bitta qabul qiluvchiga. Miqdor istalgan mos birlikda (masalan, litrli partiyadan 200 ml).",
                actions = {
                    AppButton(
                        "FEFO bo‘yicha taqsimlash",
                        onClick = { splitOpen = true },
                        kind = ButtonKind.SECONDARY,
                        icon = AppIcons.Stock,
                        modifier = Modifier.testTag("issue-fefo-split"),
                    )
                },
            ) {
                form.lines.forEachIndexed { index, line ->
                    key(line.key) {
                        IssuesLineCard(
                            index = index,
                            line = line,
                            check = checks[index],
                            fefoList = line.reagentId?.let { fefo[it] }.orEmpty(),
                            reagent = reagentOptions.firstOrNull { it.id == line.reagentId },
                            reagentNames = reagentNames,
                            districtNames = districtNames,
                            state = state,
                            issueDate = issueDate,
                            canRemove = form.lines.size > 1,
                            onReagent = { setReagent(line, it) },
                            onBatch = { chooseBatch(line, it) },
                            onRemove = { form.lines.remove(line) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppButton("Qator qo‘shish", onClick = { form.addLine() }, kind = ButtonKind.SECONDARY, icon = AppIcons.Add, modifier = Modifier.testTag("issue-add-line"))
                    Spacer(Modifier.width(16.dp))
                    val totals = issuesTotalsText(form.lines, checks, reagentOptions)
                    if (totals.isNotEmpty()) {
                        Text("Jami: $totals", fontSize = 13.sp, color = AppColors.Navy, modifier = Modifier.weight(1f).testTag("issue-totals"))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        val liveProblem = if (invalidLines.isNotEmpty()) "Saqlab bo‘lmaydi: ${lineNumbers(invalidLines)} qoldiq yoki miqdor xato." else null
        OpsFormButtons(message = summary ?: liveProblem, messageTag = "issue-error-summary") {
            AppButton(
                "Tozalash",
                onClick = {
                    form.clearAll()
                    saved = null
                    summary = null
                    generation++
                },
                kind = ButtonKind.SECONDARY,
                icon = AppIcons.Refresh,
            )
            Spacer(Modifier.width(8.dp))
            AppButton(
                "Berishni saqlash",
                onClick = { submit() },
                icon = AppIcons.Check,
                enabled = !state.busy && invalidLines.isEmpty(),
                modifier = Modifier.testTag("issue-save"),
            )
        }
        IssuesHistorySection(state, Modifier.fillMaxWidth().bringIntoViewRequester(historyRequester))
    }
    ConfirmHost(confirm, state.busy)
    if (splitOpen) {
        IssuesFefoSplitDialog(
            state = state,
            reagentOptions = reagentOptions,
            districtNames = districtNames,
            today = today,
            onApply = { result ->
                splitOpen = false
                applySplit(result)
            },
            onDismiss = { splitOpen = false },
        )
    }
}

/** Qatorlar jami — reaktiv va o‘lchov turi bo‘yicha alohida (turli turlar qo‘shilmaydi): "Etil spirti 96% — 750 ml". */
private fun issuesTotalsText(lines: List<IssuesLineDraft>, checks: List<IssuesLineCheck>, options: List<IssuesReagentOption>): String {
    val sums = LinkedHashMap<Pair<Long, Dimension>, Long>()
    lines.forEachIndexed { i, line ->
        val check = checks.getOrNull(i) ?: return@forEachIndexed
        val qty = check.qtyBase ?: return@forEachIndexed
        val batch = check.batch ?: return@forEachIndexed
        val id = line.reagentId ?: return@forEachIndexed
        sums.merge(id to batch.unit.dimension, qty, Long::plus)
    }
    return sums.entries.joinToString("; ") { (key, sum) ->
        val name = options.firstOrNull { it.id == key.first }?.name ?: "—"
        "$name — ${Qty.formatAuto(sum, key.second)}"
    }
}

package uz.reaktivombor.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.daysLeft
import uz.reaktivombor.desktop.core.nameKey
import uz.reaktivombor.desktop.service.BatchStock
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.components.AppButton
import uz.reaktivombor.desktop.ui.components.AppTextField
import uz.reaktivombor.desktop.ui.components.ButtonKind
import uz.reaktivombor.desktop.ui.components.ComboField
import uz.reaktivombor.desktop.ui.components.FieldHeight
import uz.reaktivombor.desktop.ui.components.FormRow
import uz.reaktivombor.desktop.ui.components.IconAction
import uz.reaktivombor.desktop.ui.components.LabeledField
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.comboNewValue
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import java.time.LocalDate

// «Sarf va berish» shaklining qatorlari: jonli tekshiruv (qoldiq, FEFO, muddat), qator kartasi va FEFO taqsimlash oynasi.

/**
 * Qatorning jonli holati. [invalid] — saqlab bo‘lmaydi (miqdor xato yoki qoldiq yetmaydi). [fefoWarning] — muddati
 * yaqinroq partiya bor (sariq ogohlantirish), [expired] — tanlangan partiyaning muddati berish sanasida o‘tgan.
 */
@Immutable
internal class IssuesLineCheck(
    val batch: BatchStock?,
    val recommended: BatchStock?,
    val qtyBase: Long?,
    val qtyError: String?,
    val stockError: String?,
    val available: Long?,
    val fefoWarning: String?,
    val expired: Boolean,
) {
    val invalid: Boolean get() = qtyError != null || stockError != null
}

private fun seriesName(batch: BatchStock): String = batch.series.ifBlank { "seriyasiz" }

/**
 * Hamma qatorlar birga tekshiriladi: bir partiyadagi qatorlar qo‘shiladi va [available] (berish sanasida chiqarish mumkin
 * bo‘lgan qoldiq) bilan solishtiriladi. FEFO ogohlantirishi servis qoidasi bilan bir xil: shu reaktiv va o‘lchov turidagi,
 * muddati o‘tmagan, muddati ertaroq tugaydigan, shu operatsiyada olinmagan qoldig‘i bor partiya bo‘lsa.
 */
internal fun issuesLineChecks(
    lines: List<IssuesLineDraft>,
    fefo: Map<Long, List<BatchStock>>,
    available: Map<Long, Long>,
    date: LocalDate,
): List<IssuesLineCheck> {
    val batches = lines.map { line -> line.batchId?.let { id -> line.reagentId?.let { fefo[it] }?.firstOrNull { it.batchId == id } } }
    val parsed = lines.mapIndexed { i, line ->
        val batch = batches[i]
        val unit = line.unit
        if (batch != null && unit != null && unit.dimension != batch.unit.dimension) {
            null to "Birlik partiya birligiga mos emas: ${batch.unit.siblings.joinToString(" yoki ") { it.code }} tanlang."
        } else {
            opsParseQty(line.qty, unit)
        }
    }
    val requested = HashMap<Long, Long>()
    val counts = HashMap<Long, Int>()
    lines.indices.forEach { i ->
        val batch = batches[i] ?: return@forEach
        val qty = parsed[i].first ?: return@forEach
        requested.merge(batch.batchId, qty, Long::plus)
        counts.merge(batch.batchId, 1, Int::plus)
    }
    return lines.mapIndexed { i, line ->
        val batch = batches[i]
        val qty = parsed[i].first
        val avail = batch?.let { available[it.batchId] }
        val total = batch?.let { requested[it.batchId] }
        val stockError = if (batch != null && qty != null && avail != null && total != null && total > avail) {
            "Yetarli emas: mavjud ${qtyText(avail, batch.unit)}" +
                if ((counts[batch.batchId] ?: 0) > 1) " (bu partiyadan jami ${qtyText(total, batch.unit)} so‘ralgan)" else ""
        } else {
            null
        }
        val list = line.reagentId?.let { fefo[it] }.orEmpty()
        val recommended = list.firstOrNull { it.state != ExpiryState.EXPIRED && (batch == null || it.unit.dimension == batch.unit.dimension) }
        val expired = batch?.expiresOn?.let { it < date } == true
        val fefoWarning = if (batch != null && line.type != RecipientType.WRITE_OFF && !expired) {
            val earlier = list.filter { other ->
                val expires = other.expiresOn
                other.batchId != batch.batchId && other.unit.dimension == batch.unit.dimension &&
                    expires != null && expires >= date && (batch.expiresOn == null || expires < batch.expiresOn) &&
                    other.remainder - (requested[other.batchId] ?: 0L) > 0
            }.minByOrNull { it.expiresOn!! }
            earlier?.let {
                "FEFO tavsiyasi — ${seriesName(it)} partiyasi (muddati ${dateText(it.expiresOn)}, qoldig‘i " +
                    "${qtyText(it.remainder - (requested[it.batchId] ?: 0L), it.unit)}): muddati yaqinroq partiyadan berish tavsiya etiladi. " +
                    "Boshqa partiya tanlansa, tarixda «FEFO buzilgan» deb belgilanadi."
            }
        } else {
            null
        }
        IssuesLineCheck(batch, recommended, qty, parsed[i].second, stockError, avail, fefoWarning, expired)
    }
}

/** Partiya bandi: seriya, muddati, holati, qoldig‘i va (tavsiya bo‘lsa) «FEFO tavsiyasi» belgisi. */
@Composable
internal fun RowScope.IssuesBatchItem(batch: BatchStock, recommended: Boolean, inField: Boolean) {
    Text(seriesName(batch), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text, maxLines = 1)
    Spacer(Modifier.width(8.dp))
    Text(
        (if (inField) "" else "muddati ") + dateText(batch.expiresOn),
        fontSize = 12.5.sp,
        color = AppColors.Muted,
        maxLines = 1,
    )
    Spacer(Modifier.width(8.dp))
    ExpiryChip(batch.state)
    Spacer(Modifier.width(8.dp))
    Text(
        qtyText(batch.remainder, batch.unit),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
    if (recommended) {
        Spacer(Modifier.width(8.dp))
        OpsFefoBadge()
    }
}

/** «Kimga» tanlovi (bo‘lim / tuman / hisobdan chiqarish) maydonlar qatoriga tekislangan. */
@Composable
internal fun IssuesRecipientTypeField(value: RecipientType, onChange: (RecipientType) -> Unit, testTag: String) {
    LabeledField("Kimga", required = true) {
        Box(Modifier.height(FieldHeight), contentAlignment = Alignment.CenterStart) {
            SegmentedChoice(RecipientType.entries, value, onChange, label = { it.label }, testTag = testTag)
        }
    }
}

/** Tuman: ro‘yxatdan tanlanadi yoki yangi nom yoziladi (saqlanganda servis yaratadi). */
@Composable
internal fun IssuesDistrictField(
    value: String,
    onChange: (String) -> Unit,
    districtNames: List<String>,
    error: String?,
    modifier: Modifier,
    testTag: String,
) {
    val isNew = value.isNotBlank() && comboNewValue(value, districtNames) != null
    ComboField(
        value = value,
        onValueChange = onChange,
        suggestions = districtNames,
        label = "Tuman",
        required = true,
        placeholder = "Tanlang yoki yozing",
        error = error,
        help = if (isNew && error == null) "Yangi tuman qo‘shiladi" else null,
        allowNew = true,
        newItemText = { "+ «$it» — yangi tuman" },
        modifier = modifier,
        testTag = testTag,
    )
}

/** Bo‘lim (faqat faollari) — maydon nomi yulduzcha bilan. */
@Composable
internal fun IssuesDepartmentField(state: AppState, value: Long?, onChange: (Long?) -> Unit, error: String?, modifier: Modifier, testTag: String) {
    LabeledField("Bo‘lim", modifier, required = true) {
        DepartmentSelect(
            state, value, onChange,
            label = null, placeholder = "Bo‘limni tanlang", allowNone = false, allowAdd = false, error = error, testTag = testTag,
        )
    }
}

/** Shakldagi bitta qator: reaktiv → partiya (FEFO tartibida) → miqdor; kimga, tashkilot, qabul qiluvchi, izoh; ogohlantirishlar. */
@Composable
internal fun IssuesLineCard(
    index: Int,
    line: IssuesLineDraft,
    check: IssuesLineCheck,
    fefoList: List<BatchStock>,
    reagent: IssuesReagentOption?,
    reagentNames: List<String>,
    districtNames: List<String>,
    state: AppState,
    issueDate: LocalDate,
    canRemove: Boolean,
    onReagent: (String) -> Unit,
    onBatch: (BatchStock) -> Unit,
    onRemove: () -> Unit,
) {
    val tag = "issue-line-${index + 1}"
    val hasError = check.invalid || line.errors.isNotEmpty() || line.message != null
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppColors.SurfaceAlt,
        border = BorderStroke(1.dp, if (hasError) AppColors.Error.copy(alpha = 0.6f) else AppColors.Divider),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}-qator", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy)
                if (check.batch != null && reagent != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${reagent.name}: jami qoldiq ${totalsText(reagent.totals)}",
                        fontSize = 12.5.sp,
                        color = AppColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (canRemove) {
                    IconAction(AppIcons.Delete, "Qatorni olib tashlash", onClick = onRemove, tint = AppColors.Error, testTag = "$tag-remove")
                } else {
                    Spacer(Modifier.height(30.dp))
                }
            }
            FormRow {
                ComboField(
                    value = line.reagentText,
                    onValueChange = onReagent,
                    suggestions = reagentNames,
                    label = "Reaktiv",
                    required = true,
                    placeholder = "Nomini yozing",
                    error = line.errors["reagent"],
                    onPick = onReagent,
                    modifier = Modifier.weight(1.1f),
                    testTag = "$tag-reagent",
                )
                OpsRichSelect(
                    value = check.batch,
                    options = fefoList,
                    onSelect = onBatch,
                    optionTag = { "$tag-batch-option-${it.batchId}" },
                    label = "Partiya (FEFO tartibida)",
                    required = true,
                    placeholder = if (line.reagentId == null) "Avval reaktivni tanlang" else "Partiyani tanlang",
                    error = line.errors["batchId"],
                    enabled = line.reagentId != null,
                    menuMinWidth = 600.dp,
                    emptyText = "Bu reaktivning qoldig‘i bor partiyasi yo‘q",
                    modifier = Modifier.weight(2.1f),
                    testTag = "$tag-batch",
                ) { option, inField -> IssuesBatchItem(option, option.batchId == check.recommended?.batchId, inField) }
                val qtyError = line.errors["qtyText"] ?: line.errors["unit"] ?: check.qtyError ?: check.stockError
                QtyField(
                    amount = line.qty,
                    onAmountChange = {
                        line.qty = it
                        line.clearError("qtyText")
                    },
                    unit = line.unit?.code,
                    onUnitChange = { code ->
                        line.unit = MeasureUnit.of(code)
                        line.clearError("unit")
                    },
                    units = check.batch?.unit?.siblings?.map { it.code }.orEmpty(),
                    label = "Miqdor",
                    required = true,
                    error = qtyError,
                    help = if (check.batch != null && check.available != null) "Berish mumkin: ${qtyText(check.available, check.batch.unit)}" else null,
                    modifier = Modifier.weight(0.9f),
                    testTag = "$tag-qty",
                )
            }
            FormRow {
                IssuesRecipientTypeField(
                    line.type,
                    {
                        line.type = it
                        line.errors = line.errors - setOf("departmentId", "districtId", "districtName", "note")
                    },
                    testTag = "$tag-type",
                )
                when (line.type) {
                    RecipientType.DEPARTMENT -> IssuesDepartmentField(
                        state, line.departmentId,
                        {
                            line.departmentId = it
                            line.clearError("departmentId")
                        },
                        error = line.errors["departmentId"], modifier = Modifier.weight(1f), testTag = "$tag-department",
                    )
                    RecipientType.DISTRICT -> IssuesDistrictField(
                        line.districtName,
                        {
                            line.districtName = it
                            line.clearError("districtName")
                            line.clearError("districtId")
                        },
                        districtNames, error = line.errors["districtName"] ?: line.errors["districtId"], modifier = Modifier.weight(1f), testTag = "$tag-district",
                    )
                    RecipientType.WRITE_OFF -> Unit
                }
                if (line.type != RecipientType.WRITE_OFF) {
                    AppTextField(
                        line.organization, { line.organization = it; line.clearError("organization") },
                        label = "Qabul qiluvchi tashkilot", error = line.errors["organization"], modifier = Modifier.weight(1.15f), testTag = "$tag-org",
                    )
                    AppTextField(
                        line.receiver, { line.receiver = it; line.clearError("receiver") },
                        label = "Qabul qiluvchi xodim", error = line.errors["receiver"], modifier = Modifier.weight(1f), testTag = "$tag-receiver",
                    )
                }
                val writeOff = line.type == RecipientType.WRITE_OFF
                AppTextField(
                    line.note, { line.note = it; line.clearError("note") },
                    label = if (writeOff) "Hisobdan chiqarish sababi" else "Izoh",
                    required = writeOff,
                    placeholder = if (writeOff) "Masalan: muddati o‘tgan, idish singan" else null,
                    error = line.errors["note"],
                    modifier = Modifier.weight(if (writeOff) 3.15f else 1f),
                    testTag = "$tag-note",
                )
            }
            check.fefoWarning?.let { OpsNotice(it, Severity.WARNING, Modifier.fillMaxWidth(), testTag = "$tag-fefo") }
            if (check.expired && line.type != RecipientType.WRITE_OFF) {
                val batch = check.batch!!
                OpsNotice(
                    "Muddati o‘tgan partiya: yaroqlilik muddati ${dateText(batch.expiresOn)} " +
                        "(${daysPhrase(daysLeft(batch.expiresOn, issueDate)) ?: ""}). " +
                        "Bunday partiyadan berish uchun saqlashda alohida tasdiq so‘raladi. Yaroqsiz reaktivni «Hisobdan chiqarish» orqali chiqaring.",
                    Severity.ERROR,
                    Modifier.fillMaxWidth(),
                    testTag = "$tag-expired",
                )
            }
            line.message?.let { OpsNotice(it, Severity.ERROR, Modifier.fillMaxWidth(), testTag = "$tag-message") }
        }
    }
}

// ---------------------------------------------------------------- FEFO bo‘yicha taqsimlash

/** Taqsimlash natijasi: reaktiv, partiyalar (asosiy birlikda miqdor) va hamma qatorga bir xil qabul qiluvchi. */
@Immutable
internal data class IssuesSplitResult(
    val reagentId: Long,
    val reagentName: String,
    val unit: MeasureUnit,
    val parts: List<Pair<Long, Long>>,
    val type: RecipientType,
    val departmentId: Long?,
    val districtName: String,
    val organization: String,
    val receiver: String,
    val note: String,
)

/**
 * «FEFO bo‘yicha taqsimlash»: reaktiv va jami miqdor kiritiladi, Stock.fefoSplit uni muddati o‘tmagan partiyalarga
 * muddati yaqinidan boshlab taqsimlaydi. Natija shakldagi shu reaktiv qatorlari va bo‘sh qatorlar o‘rniga qo‘yiladi.
 */
@Composable
internal fun IssuesFefoSplitDialog(
    state: AppState,
    reagentOptions: List<IssuesReagentOption>,
    districtNames: List<String>,
    today: LocalDate,
    onApply: (IssuesSplitResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var reagentText by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf<MeasureUnit?>(null) }
    var type by remember { mutableStateOf(RecipientType.DEPARTMENT) }
    var departmentId by remember { mutableStateOf<Long?>(null) }
    var districtName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val reagent = reagentOptions.firstOrNull { nameKey(it.name) == nameKey(reagentText) }
    val units = reagent?.totals?.keys?.sortedBy { it.ordinal }?.flatMap { MeasureUnit.of(it) }.orEmpty()
    // Tanlangan birlik shu reaktivga mos bo‘lmasa — reaktivning asosiy birligi (l, kg yoki dona).
    val chosenUnit = unit?.takeIf { it in units } ?: reagent?.totals?.keys?.minByOrNull { it.ordinal }?.let { MeasureUnit.main(it) }
    val (qtyBase, qtyError) = opsParseQty(qty, chosenUnit, "Jami miqdor")
    val dimension: Dimension? = chosenUnit?.dimension
    val fefo = remember(reagent?.id, state.refreshTick) { reagent?.let { issuesFefoOrder(state, it.id, today) }.orEmpty() }
    val split: Result<List<Pair<Long, Long>>>? = remember(reagent?.id, dimension, qtyBase, state.refreshTick) {
        if (reagent == null || dimension == null || qtyBase == null) {
            null
        } else {
            try {
                Result.success(state.services.stock.fefoSplit(reagent.id, dimension, qtyBase, today))
            } catch (e: UserError) {
                Result.failure(e)
            } catch (e: Throwable) {
                state.reportUnexpected(e, "fefoSplit")
                null
            }
        }
    }
    val parts = split?.getOrNull()

    AppDialog(
        title = "FEFO bo‘yicha taqsimlash",
        subtitle = "Jami miqdor muddati o‘tmagan partiyalarga — muddati eng yaqinidan boshlab — taqsimlanadi",
        onDismiss = onDismiss,
        width = 900.dp,
        testTag = "split-dialog",
        buttons = {
            AppButton("Bekor qilish", onClick = onDismiss, kind = ButtonKind.SECONDARY)
            AppButton(
                "Qatorlarga qo‘shish",
                onClick = {
                    val r = reagent ?: return@AppButton
                    val u = chosenUnit ?: return@AppButton
                    val p = parts ?: return@AppButton
                    onApply(IssuesSplitResult(r.id, r.name, u, p, type, departmentId, districtName, organization, receiver, note))
                },
                icon = AppIcons.Check,
                enabled = parts != null,
                modifier = Modifier.testTag("split-apply"),
            )
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormRow {
                ComboField(
                    value = reagentText,
                    onValueChange = { reagentText = it },
                    suggestions = reagentOptions.map { it.name },
                    label = "Reaktiv",
                    required = true,
                    placeholder = "Nomini yozing",
                    help = reagent?.let { "Jami qoldiq: ${totalsText(it.totals)}" },
                    error = if (reagentText.isNotBlank() && reagent == null) "Bunday nomli, qoldig‘i bor reaktiv topilmadi." else null,
                    modifier = Modifier.weight(1.4f),
                    testTag = "split-reagent",
                )
                QtyField(
                    amount = qty,
                    onAmountChange = { qty = it },
                    unit = chosenUnit?.code,
                    onUnitChange = { code -> unit = MeasureUnit.of(code) },
                    units = units.map { it.code },
                    label = "Jami miqdor",
                    required = true,
                    error = qtyError,
                    modifier = Modifier.weight(1f),
                    testTag = "split-qty",
                )
            }
            FormRow {
                IssuesRecipientTypeField(type, { type = it }, testTag = "split-type")
                when (type) {
                    RecipientType.DEPARTMENT -> IssuesDepartmentField(state, departmentId, { departmentId = it }, null, Modifier.weight(1f), "split-department")
                    RecipientType.DISTRICT -> IssuesDistrictField(districtName, { districtName = it }, districtNames, null, Modifier.weight(1f), "split-district")
                    RecipientType.WRITE_OFF -> Unit
                }
                if (type != RecipientType.WRITE_OFF) {
                    AppTextField(organization, { organization = it }, label = "Qabul qiluvchi tashkilot", modifier = Modifier.weight(1.1f), testTag = "split-org")
                    AppTextField(receiver, { receiver = it }, label = "Qabul qiluvchi xodim", modifier = Modifier.weight(1f), testTag = "split-receiver")
                } else {
                    AppTextField(note, { note = it }, label = "Hisobdan chiqarish sababi", required = true, modifier = Modifier.weight(2f), testTag = "split-note")
                }
            }
            Text("Taqsimot", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Navy)
            when {
                reagent == null || qtyBase == null -> Text(
                    "Reaktiv va jami miqdorni kiriting — partiyalar bo‘yicha taqsimot shu yerda ko‘rinadi.",
                    fontSize = 13.sp,
                    color = AppColors.Muted,
                )
                split?.isFailure == true -> OpsNotice(split.exceptionOrNull()?.message.orEmpty(), Severity.ERROR, Modifier.fillMaxWidth(), testTag = "split-error")
                parts != null -> Column(
                    Modifier.fillMaxWidth().background(AppColors.SurfaceAlt, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    parts.forEachIndexed { i, (batchId, base) ->
                        val batch = fefo.firstOrNull { it.batchId == batchId }
                        Row(Modifier.fillMaxWidth().testTag("split-part-$i"), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.width(24.dp))
                            if (batch != null) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { IssuesBatchItem(batch, recommended = false, inField = false) }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            val (text, u) = opsEditableQty(base, chosenUnit ?: MeasureUnit.L)
                            Text(
                                "→ $text ${u.code}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.Primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                }
            }
            OpsNotice(
                "«Qatorlarga qo‘shish» bosilganda shakldagi shu reaktiv qatorlari va bo‘sh qatorlar taqsimlangan qatorlar bilan almashtiriladi. " +
                    "Qatorlarni keyin alohida o‘zgartirish mumkin.",
                Severity.INFO,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

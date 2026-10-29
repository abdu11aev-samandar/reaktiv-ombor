package uz.reaktivombor.desktop.service

import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.nameKey
import java.time.LocalDate
import java.time.LocalDateTime

// «Bo‘limlar bo‘yicha hisob» va «Tumanlarga berilgan reaktivlar» sahifalari uchun qo‘shimcha o‘qish modellari:
// reaktiv nomi bo‘yicha filtr (hisobotdagi «Reaktiv» filtri bilan bir xil) va bo‘lim sarf tarixi (berish + qaytarish).

/** Sarf tarixidagi operatsiya turi. */
enum class PlaceHistoryKind(val label: String) {
    ISSUE("Berildi"),
    RETURN("Qaytarildi"),
}

/**
 * Bo‘lim sarf tarixi qatori: bo‘limga berish yoki bo‘limdan qaytarish. [qty] asosiy birlikda, [unit] — kiritilgan birlik.
 * [person] — berishda qabul qiluvchi, qaytarishda qabul qilgan xodim; [note] — izoh yoki qaytarish sababi.
 */
data class PlaceHistoryRow(
    val kind: PlaceHistoryKind,
    val date: LocalDate,
    val issueItemId: Long,
    val returnId: Long?,
    val batchId: Long,
    val reagentId: Long,
    val name: String,
    val series: String,
    val expiresOn: LocalDate?,
    val qty: Long,
    val unit: MeasureUnit,
    val docNo: String,
    val person: String,
    val note: String,
    val createdAt: LocalDateTime,
) {
    /** Jadval kaliti: bitta berish va uning qaytarishlari alohida qator. */
    val key: String get() = if (returnId != null) "r$returnId" else "i$issueItemId"
}

/** Bo‘lim va tuman sahifalari uchun o‘qish modellari (yozmaydi). Asos — [Queries]. */
class PlacesQueries(private val queries: Queries) {

    constructor(services: Services) : this(services.queries)

    /**
     * [Queries.departmentSummary] + reaktiv nomi bo‘yicha filtr ([reagentText] — nomning bir qismi, katta-kichik harf va
     * tutuq belgisi farqsiz). Bo‘limga berilganlar jamlari filtrlangan berishlardan qayta hisoblanadi.
     */
    fun departmentSummary(
        departmentId: Long,
        from: LocalDate? = null,
        to: LocalDate? = null,
        reagentText: String? = null,
        today: LocalDate,
    ): DepartmentSummary {
        val base = queries.departmentSummary(departmentId, from, to, today)
        if (reagentText.isNullOrBlank()) return base
        val history = base.history.filter { reagentMatches(reagentText, it.name) }
        return DepartmentSummary(
            department = base.department,
            ownedBatches = base.ownedBatches.filter { reagentMatches(reagentText, it.name) },
            issuedTo = totalsByReagent(history),
            history = history,
        )
    }

    /**
     * Bo‘lim sarf tarixi: davr ichidagi bo‘limga berishlar va bo‘limdan qaytarishlar (bekor qilinganlarsiz), yangisi
     * birinchi. Qaytarishning hujjat raqami — u qaytarilgan berishniki.
     */
    fun departmentHistory(
        departmentId: Long,
        from: LocalDate? = null,
        to: LocalDate? = null,
        reagentText: String? = null,
    ): List<PlaceHistoryRow> {
        val allItems = queries.issueItems(OpFilter(departmentId = departmentId, recipientType = RecipientType.DEPARTMENT))
        val byId = allItems.associateBy { it.id }
        val issues = allItems.filter { inRange(it.issuedOn, from, to) }.map { item ->
            PlaceHistoryRow(
                kind = PlaceHistoryKind.ISSUE,
                date = item.issuedOn,
                issueItemId = item.id,
                returnId = null,
                batchId = item.batchId,
                reagentId = item.reagentId,
                name = item.name,
                series = item.series,
                expiresOn = item.expiresOn,
                qty = item.qty,
                unit = item.enteredUnit,
                docNo = item.docNo,
                person = item.receiver,
                note = item.note,
                createdAt = item.createdAt,
            )
        }
        val returns = queries.returns(
            OpFilter(from = from, to = to, departmentId = departmentId, recipientType = RecipientType.DEPARTMENT),
        ).map { r ->
            val item = byId[r.issueItemId]
            PlaceHistoryRow(
                kind = PlaceHistoryKind.RETURN,
                date = r.returnedOn,
                issueItemId = r.issueItemId,
                returnId = r.id,
                batchId = r.batchId,
                reagentId = r.reagentId,
                name = r.name,
                series = r.series,
                expiresOn = item?.expiresOn,
                qty = r.qty,
                unit = r.enteredUnit,
                docNo = item?.docNo.orEmpty(),
                person = r.receivedBy,
                note = r.reason,
                createdAt = r.createdAt,
            )
        }
        return (issues + returns)
            .filter { reagentText.isNullOrBlank() || reagentMatches(reagentText, it.name) }
            .sortedWith(compareByDescending<PlaceHistoryRow> { it.date }.thenByDescending { it.createdAt }.thenByDescending { it.key })
    }

    /**
     * [Queries.districtSummary] + reaktiv nomi bo‘yicha filtr. [districtId] null — barcha tumanlar. Tuman va reaktiv
     * jamlari filtrlangan berishlardan qayta hisoblanadi (o‘lchov turlari qo‘shilmaydi).
     */
    fun districtSummary(
        districtId: Long? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
        reagentText: String? = null,
    ): DistrictSummary {
        val base = queries.districtSummary(districtId, from, to)
        if (reagentText.isNullOrBlank()) return base
        val items = base.items.filter { reagentMatches(reagentText, it.name) }
        val byDistrict = items.groupBy { it.districtId!! }
        val districts = byDistrict.map { (id, list) ->
            DistrictTotal(
                districtId = id,
                name = list.first().districtName.orEmpty(),
                items = list.size,
                firstDate = list.minOfOrNull { it.issuedOn },
                lastDate = list.maxOfOrNull { it.issuedOn },
                net = list.groupBy { it.unit.dimension }.mapValues { (_, l) -> l.sumOf { it.netQty } },
            )
        }.sortedWith(compareBy({ nameKey(it.name) }, { it.districtId }))
        val perReagent = byDistrict.flatMap { (id, list) ->
            totalsByReagent(list).map { DistrictReagentTotal(id, list.first().districtName.orEmpty(), it) }
        }.sortedWith(compareBy({ nameKey(it.districtName) }, { nameKey(it.total.name) }, { it.total.dimension.ordinal }))
        return DistrictSummary(districts, perReagent, items)
    }

    companion object {
        /** Reaktiv nomi filtri: hisobotlardagi «Reaktiv» filtri bilan bir xil (kalit bo‘yicha qism-satr). */
        fun reagentMatches(text: String?, name: String): Boolean {
            val key = nameKey(text ?: "")
            return key.isEmpty() || nameKey(name).contains(key)
        }

        private fun inRange(date: LocalDate, from: LocalDate?, to: LocalDate?): Boolean =
            (from == null || date >= from) && (to == null || date <= to)

        /** Reaktiv + o‘lchov turi bo‘yicha jamlar (Queries dagi bilan bir xil qoida). */
        internal fun totalsByReagent(items: List<IssueItemRow>): List<ReagentQtyTotal> =
            items.groupBy { it.reagentId to it.unit.dimension }.map { (key, list) ->
                ReagentQtyTotal(
                    reagentId = key.first,
                    name = list.first().name,
                    dimension = key.second,
                    unit = MeasureUnit.largest(list.map { it.unit })!!,
                    issued = list.sumOf { it.qty },
                    returned = list.sumOf { it.returnedQty },
                    items = list.size,
                    firstDate = list.minOfOrNull { it.issuedOn },
                    lastDate = list.maxOfOrNull { it.issuedOn },
                )
            }.sortedWith(compareBy({ nameKey(it.name) }, { it.dimension.ordinal }))
    }
}

package uz.reaktivombor.desktop.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.Qty
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.PlaceHistoryKind
import uz.reaktivombor.desktop.service.PlacesQueries
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.ReturnInput
import java.math.BigDecimal
import java.time.LocalDate

class ReportsTest : ReportTestBase() {

    private val builder: ReportBuilder get() = ReportBuilder(services)

    private fun build(kind: ReportKind, filter: ReportFilter = ReportFilter()) = builder.build(kind, filter, today)

    /** Katakdagi son matni: BigDecimal("19") → "19", 0,75 → "0.75". */
    private fun num(value: Any?): String = (value as BigDecimal).stripTrailingZeros().toPlainString()

    private fun ReportData.column(title: String): Int = columns.indexOfFirst { it.title == title }.also { assertTrue(title, it >= 0) }

    private fun ReportData.cell(row: ReportRow, title: String): Any? = row.cells[column(title)]

    private fun ReportData.rowsOf(name: String): List<ReportRow> = rows.filter { !it.summary && cell(it, "Reaktiv nomi") == name }

    @Test
    fun everyKindBuildsWithConsistentRows() {
        seed()
        val expectedRows = mapOf(
            ReportKind.ALL_REAGENTS to 6, ReportKind.BATCHES to 10, ReportKind.GROUP_TOTALS to 4, ReportKind.BY_DEPARTMENT to 4,
            ReportKind.DISTRICTS to 2, ReportKind.RECEIPTS to 6, ReportKind.ISSUES to 5, ReportKind.EXPIRING to 3,
            ReportKind.EXPIRED to 1, ReportKind.BELOW_MIN to 1, ReportKind.MOVEMENT to 6, ReportKind.HISTORY to 7,
        )
        assertEquals(12, ReportKind.entries.size)
        for (kind in ReportKind.entries) {
            val filter = if (kind.needsName) ReportFilter(name = "Etil spirti") else ReportFilter()
            val data = build(kind, filter)
            assertEquals(kind, data.kind)
            assertEquals(kind.title, data.title)
            assertEquals("Yaratilgan: 29.10.2026 09:00", data.generatedLine)
            assertTrue(kind.name, data.columns.isNotEmpty())
            assertEquals(kind.name, expectedRows.getValue(kind), data.rows.size)
            data.rows.forEach { row ->
                assertEquals(kind.name, data.columns.size, row.cells.size)
                data.columns.indices.forEach { c -> data.text(row, c) }
            }
            assertTrue(kind.name, data.footerLines.isNotEmpty())
            val expectedFilter = if (kind.needsName) listOf("Reaktiv: Etil spirti") else listOf("Filtr: yo‘q")
            assertEquals(kind.name, expectedFilter, data.filterLines)
        }
    }

    @Test
    fun reportTitlesAreTheUzbekTitlesOfTheSpecification() {
        assertEquals(
            listOf(
                "Barcha reaktivlar ro‘yxati", "Reaktivlarning partiyalar bo‘yicha ro‘yxati", "Bir xil nomdagi reaktivlarning umumiy qoldig‘i",
                "Bo‘limlar bo‘yicha reaktivlar qoldig‘i", "Tumanlarga berilgan reaktivlar hisoboti", "Kirim operatsiyalari",
                "Sarf operatsiyalari", "Yaroqlilik muddati yaqinlashgan reaktivlar", "Muddati o‘tgan reaktivlar",
                "Minimal qoldiqdan kamaygan reaktivlar", "Davr uchun reaktivlar harakati", "Reaktivning to‘liq operatsiyalar tarixi",
            ),
            ReportKind.entries.map { it.title },
        )
    }

    @Test
    fun etilSpirtiNumbers() {
        seed()
        val totals = build(ReportKind.GROUP_TOTALS)
        val etil = totals.rowsOf("Etil spirti").single()
        assertEquals(3L, totals.cell(etil, "Partiyalar"))
        assertEquals("19", num(totals.cell(etil, "Kelgan jami")))
        assertEquals("5", num(totals.cell(etil, "Berilgan jami")))
        assertEquals("14", num(totals.cell(etil, "Umumiy qoldiq")))
        assertEquals("l", totals.cell(etil, "Birlik"))
        assertEquals(ExpiryState.RED, totals.cell(etil, "Holati")) // eng xavflisi
        assertEquals(ExpiryState.RED, etil.state)

        val all = build(ReportKind.ALL_REAGENTS)
        val rows = all.rowsOf("Etil spirti")
        assertEquals(listOf("ES-001", "ES-002", "ES-003"), rows.map { all.cell(it, "Seriya raqami") })
        assertEquals(listOf(ExpiryState.RED, ExpiryState.YELLOW, ExpiryState.GREEN), rows.map { it.state })
        assertEquals(listOf("5", "10", "4"), rows.map { num(all.cell(it, "Kelgan miqdor")) })
        assertEquals(listOf("3", "7", "4"), rows.map { num(all.cell(it, "Joriy qoldiq")) })
        assertEquals(listOf("14 l", "14 l", "14 l"), rows.map { all.cell(it, "Umumiy qoldiq (shu nom)") })
        assertEquals(listOf(20L, 60L, 200L), rows.map { all.cell(it, "Qolgan kunlar") })

        val batches = build(ReportKind.BATCHES)
        val subtotal = batches.rows.single { it.summary && batches.cell(it, "Reaktiv nomi") == "Jami: Etil spirti (3 ta partiya)" }
        assertEquals("19", num(batches.cell(subtotal, "Kelgan")))
        assertEquals("5", num(batches.cell(subtotal, "Berilgan")))
        assertEquals("14", num(batches.cell(subtotal, "Qoldiq")))
        assertNull(subtotal.state)
        // Jami qatori o‘z guruhidan keyin keladi.
        val index = batches.rows.indexOf(subtotal)
        assertEquals("ES-003", batches.cell(batches.rows[index - 1], "Seriya raqami"))
    }

    @Test
    fun quantitiesStayInTheRowUnitAndTotalsNeverMixDimensions() {
        seed()
        val all = build(ReportKind.ALL_REAGENTS)
        val natriy = all.rowsOf("Natriy xlorid").single()
        assertEquals("kg", all.cell(natriy, "Birlik"))
        assertEquals("0.25", num(all.cell(natriy, "Jami berilgan miqdor")))
        assertEquals("0.75", num(all.cell(natriy, "Joriy qoldiq")))
        assertEquals("0,75", all.text(natriy, all.column("Joriy qoldiq")))
        assertEquals("Jami qoldiq: 16,4 l · 750 g", all.footerLines[1])

        val districts = build(ReportKind.DISTRICTS)
        val chilonzor = districts.rows.single { districts.cell(it, "Tuman") == "Chilonzor" }
        assertEquals("g", districts.cell(chilonzor, "Birlik")) // kiritilgan birlikda
        assertEquals(listOf("200", "50", "150"), listOf("Berilgan", "Qaytarilgan", "Sof berilgan").map { num(districts.cell(chilonzor, it)) })
        assertEquals("T-15", districts.cell(chilonzor, "Hujjat raqami"))
        assertEquals("Chilonzor SEB", districts.cell(chilonzor, "Tashkilot"))
        assertTrue(districts.footerLines.toString(), districts.footerLines.contains("Chilonzor: 1 ta berish, sof 150 g"))
        // Umumiy jami — bitta qator (ikkiga bo‘linib qolmaydi).
        assertEquals(
            listOf("Chilonzor: 1 ta berish, sof 150 g", "Yunusobod: 1 ta berish, sof 100 g", "Jami: 2 ta berish, sof 250 g"),
            districts.footerLines,
        )
        assertEquals(listOf("Ma’lumot topilmadi.", "Jami: 0 ta berish, sof 0"), build(ReportKind.DISTRICTS, ReportFilter(name = "Benzol")).footerLines)

        val issues = build(ReportKind.ISSUES)
        assertEquals("Jami sarf (qaytarilgani ayirilgan): 5,1 l · 250 g", issues.footerLines[1])
        assertTrue(issues.rows.all { it.state == null }) // operatsiya qatorlari holat rangisiz
    }

    @Test
    fun filtersAreAppliedAndDescribed() {
        seed()
        val october = ReportFilter(from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31))

        val receipts = build(ReportKind.RECEIPTS, october)
        assertEquals(listOf("Davr: 01.10.2026 — 31.10.2026"), receipts.filterLines)
        assertEquals(listOf("Aseton"), receipts.rows.map { receipts.cell(it, "Reaktiv nomi") }) // bekor qilingan kirim yo‘q

        val issues = build(ReportKind.ISSUES, ReportFilter(name = " etil ", departmentId = deptId("Biologiya")))
        assertEquals(listOf("Reaktiv: etil", "Bo‘lim: Biologiya"), issues.filterLines)
        assertEquals(listOf("ES-001"), issues.rows.map { issues.cell(it, "Seriya raqami") })

        val chilonzor = services.directory.districts().single { it.name == "Chilonzor" }
        val districts = build(ReportKind.DISTRICTS, ReportFilter(districtId = chilonzor.id))
        assertEquals(listOf("Tuman: Chilonzor"), districts.filterLines)
        assertEquals(1, districts.rows.size)

        val expiring = build(ReportKind.EXPIRING, ReportFilter(series = "es-00"))
        assertEquals(listOf("Seriya: es-00"), expiring.filterLines)
        assertEquals(listOf("ES-001", "ES-002"), expiring.rows.map { expiring.cell(it, "Seriya raqami") })

        val received = build(ReportKind.ALL_REAGENTS, ReportFilter(from = LocalDate.of(2026, 10, 1)))
        assertEquals(listOf("Kelgan sana: 01.10.2026 dan"), received.filterLines)
        assertEquals(listOf("Aseton"), received.rows.map { received.cell(it, "Reaktiv nomi") })

        val expiresUntil = build(ReportKind.EXPIRING, ReportFilter(to = LocalDate.of(2026, 11, 30)))
        assertEquals(listOf("Yaroqlilik muddati: 30.11.2026 gacha"), expiresUntil.filterLines)
        assertEquals(listOf("A-1", "ES-001"), expiresUntil.rows.map { expiresUntil.cell(it, "Seriya raqami") })

        // Joriy holat hisobotida davr hisobga olinmaydi va filtr qatorida ko‘rinmaydi.
        val totals = build(ReportKind.GROUP_TOTALS, october.copy(departmentId = deptId("Sud-kimyo")))
        assertEquals(listOf("Bo‘lim: Sud-kimyo"), totals.filterLines)
        assertEquals(listOf("Aseton", "Natriy xlorid"), totals.rows.map { totals.cell(it, "Reaktiv nomi") })
        val belowMin = build(ReportKind.BELOW_MIN, ReportFilter(departmentId = deptId("Biologiya"), series = "X"))
        assertEquals(listOf("Filtr: yo‘q"), belowMin.filterLines)

        val error = assertThrows(UserError::class.java) {
            build(ReportKind.RECEIPTS, ReportFilter(from = LocalDate.of(2026, 10, 31), to = LocalDate.of(2026, 10, 1)))
        }
        assertTrue(error.message, error.message.contains("Davr boshi"))
        assertThrows(UserError::class.java) { build(ReportKind.ISSUES, ReportFilter(departmentId = 9999)) }
    }

    @Test
    fun historyWithoutANameIsRefused() {
        seed()
        newBatch("Metil spirti", "M-1", today.plusDays(100), "1")
        val missing = assertThrows(UserError::class.java) { build(ReportKind.HISTORY) }
        assertTrue(missing.message, missing.message.contains("reaktiv nomini kiriting"))
        assertEquals("name", missing.fields.keys.single())
        assertThrows(UserError::class.java) { build(ReportKind.HISTORY, ReportFilter(name = "   ")) }
        val unknown = assertThrows(UserError::class.java) { build(ReportKind.HISTORY, ReportFilter(name = "Benzol")) }
        assertTrue(unknown.message, unknown.message.contains("topilmadi"))
        val ambiguous = assertThrows(UserError::class.java) { build(ReportKind.HISTORY, ReportFilter(name = "spirti")) }
        assertTrue(ambiguous.message, ambiguous.message.contains("bir nechta"))
        // To‘liq nom (katta-kichik harf farqsiz) — aniq topiladi.
        val history = build(ReportKind.HISTORY, ReportFilter(name = "ETIL SPIRTI"))
        assertEquals(listOf("Reaktiv: Etil spirti"), history.filterLines)
        // Bitta mos — qisman nom ham yetadi.
        assertEquals(listOf("Reaktiv: Natriy xlorid"), build(ReportKind.HISTORY, ReportFilter(name = "natriy")).filterLines)
    }

    @Test
    fun historyListsEveryOperationWithARunningBalance() {
        seed()
        val history = build(ReportKind.HISTORY, ReportFilter(name = "Etil spirti"))
        assertEquals(7, history.rows.size)
        val es3 = history.rows.filter { history.cell(it, "Seriya raqami") == "ES-003" }
        assertEquals(listOf("Kirim (boshlang‘ich)", "Kirim", "Bekor qilindi (kirim): Xato kiritildi"), es3.map { history.cell(it, "Operatsiya") })
        // Keyin bekor qilingan kirim va uning teskarisi qoldiqni o‘zgartirmaydi (ikkalasi ro‘yxatda qoladi).
        assertEquals(listOf("4", "4", "4"), es3.map { num(history.cell(it, "Partiya qoldig‘i")) })
        assertEquals("Keyin bekor qilingan", history.cell(es3[1], "Izoh"))
        assertEquals("1", num(history.cell(es3[2], "Chiqim")))
        assertEquals(LocalDate.of(2026, 10, 26), history.cell(es3[2], "Sana")) // bekor qilish asl sana bilan
        assertEquals("Joriy qoldiq: 14 l", history.footerLines.last())

        // Davr: boshidagi qoldiq har bir partiya uchun, keyin davrdagi operatsiyalar.
        val period = build(ReportKind.HISTORY, ReportFilter(name = "Etil spirti", from = LocalDate.of(2026, 10, 20), to = LocalDate.of(2026, 10, 31)))
        val opening = period.rows.filter { it.summary }
        assertEquals(listOf("3", "7", "4"), opening.map { num(period.cell(it, "Partiya qoldig‘i")) })
        assertTrue(opening.all { period.cell(it, "Operatsiya") == "Davr boshidagi qoldiq" })
        assertEquals(2, period.rows.count { !it.summary })
        assertEquals(listOf("Davr: 20.10.2026 — 31.10.2026", "Reaktiv: Etil spirti"), period.filterLines)
        val early = build(ReportKind.HISTORY, ReportFilter(name = "Etil spirti", to = LocalDate.of(2026, 10, 15)))
        assertEquals("Davr oxiridagi qoldiq: 17 l", early.footerLines.last())
    }

    @Test
    fun movementShowsOpeningFlowsAndClosingPerBatch() {
        val s = seed()
        // Gistologiyaga berilgan 3 l bekor qilinadi: teskari harakat asl sana (19.10) bilan — davr hisobidan chiqadi.
        val item = services.queries.issueItems().single { it.batchId == s.es2 }
        services.stock.cancelIssueItem(item.id, "Xato")
        val october = build(ReportKind.MOVEMENT, ReportFilter(from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31)))
        assertFalse(october.columns.any { it.title == "Moslash" })
        fun flows(series: String): List<String> {
            val row = october.rows.single { october.cell(it, "Seriya raqami") == series }
            return listOf("Davr boshidagi qoldiq", "Kirim", "Berilgan", "Qaytarilgan", "Hisobdan chiqarilgan", "Davr oxiridagi qoldiq")
                .map { num(october.cell(row, it)) }
        }
        assertEquals(listOf("5", "0", "2", "0", "0", "3"), flows("ES-001"))
        assertEquals(listOf("10", "0", "0", "0", "0", "10"), flows("ES-002"))
        assertEquals(listOf("4", "0", "0", "0", "0", "4"), flows("ES-003")) // +1 −1 (bekor qilingan kirim)
        assertEquals(listOf("0", "2", "0", "0", "0", "2"), flows("A-1"))
        assertEquals(listOf("500", "0", "0", "0", "100", "400"), flows("X-1"))
        assertEquals(listOf("1", "0", "0.3", "0.05", "0", "0.75"), flows("N-1"))
        // Har qatorda: boshida + kirim − berilgan + qaytarilgan − hisobdan chiqarilgan = oxirida.
        october.rows.forEach { row ->
            val v = listOf("Davr boshidagi qoldiq", "Kirim", "Berilgan", "Qaytarilgan", "Hisobdan chiqarilgan", "Davr oxiridagi qoldiq")
                .map { october.cell(row, it) as BigDecimal }
            assertEquals(0, (v[0] + v[1] - v[2] + v[3] - v[4]).compareTo(v[5]))
        }
        assertEquals("Davr oxirida: 19,4 l · 750 g", october.footerLines.last())

        // Davr oxiridan keyingi harakatlar hisobga olinmaydi.
        val early = build(ReportKind.MOVEMENT, ReportFilter(to = LocalDate.of(2026, 10, 20)))
        val x = early.rows.single { early.cell(it, "Seriya raqami") == "X-1" }
        assertEquals("500", num(early.cell(x, "Davr oxiridagi qoldiq")))
        assertTrue(early.rows.none { early.cell(it, "Seriya raqami") == "N-1" && num(early.cell(it, "Berilgan")) != "0" })
    }

    @Test
    fun expiringExpiredAndBelowMinLists() {
        seed()
        val expiring = build(ReportKind.EXPIRING)
        assertEquals(listOf("A-1", "ES-001", "ES-002"), expiring.rows.map { expiring.cell(it, "Seriya raqami") })
        assertEquals(listOf(10L, 20L, 60L), expiring.rows.map { expiring.cell(it, "Qolgan kunlar") })
        assertEquals(listOf(ExpiryState.RED, ExpiryState.RED, ExpiryState.YELLOW), expiring.rows.map { it.state })
        assertTrue(expiring.footerLines.first(), expiring.footerLines.first().contains("qizil (30 kun va kam): 2, sariq (31–90 kun): 1"))

        val expired = build(ReportKind.EXPIRED)
        val x = expired.rows.single()
        assertEquals(2L, expired.cell(x, "O‘tgan kunlar"))
        assertEquals("400", num(expired.cell(x, "Qoldiq")))
        assertEquals("ml", expired.cell(x, "Birlik"))
        assertEquals(ExpiryState.EXPIRED, expired.cell(x, "Holati"))

        val belowMin = build(ReportKind.BELOW_MIN)
        val aseton = belowMin.rows.single()
        assertEquals("Aseton", belowMin.cell(aseton, "Reaktiv nomi"))
        assertEquals(listOf("2", "2", "3", "1"), listOf("Umumiy qoldiq", "Yaroqli qoldiq", "Minimal qoldiq", "Yetishmaydi").map { num(belowMin.cell(aseton, it)) })
        assertEquals("l", belowMin.cell(aseton, "Birlik"))
    }

    @Test
    fun belowMinUsesNonExpiredStock() {
        // Faqat muddati o‘tgan zaxira (50 g) minimal 20 g ni qoplamaydi: yaroqli qoldiq 0 — 20 g yetishmaydi.
        newBatch("Kumush nitrat", "KN-09", today.minusDays(5), "50", unit = G, received = today.minusDays(200), minStock = "20", minStockUnit = G)
        val data = build(ReportKind.BELOW_MIN)
        val row = data.rows.single()
        assertEquals(
            listOf("50", "0", "20", "20"),
            listOf("Umumiy qoldiq", "Yaroqli qoldiq", "Minimal qoldiq", "Yetishmaydi").map { num(data.cell(row, it)) },
        )
        assertEquals(ExpiryState.EXPIRED, data.cell(row, "Holati"))
    }

    @Test
    fun allReagentsQuantitiesAddUp() {
        seed()
        val all = build(ReportKind.ALL_REAGENTS)
        all.rows.forEach { row ->
            val v = listOf("Kelgan miqdor", "Jami berilgan miqdor", "Hisobdan chiqarilgan", "Joriy qoldiq").map { all.cell(row, it) as BigDecimal }
            assertEquals(all.cell(row, "Seriya raqami").toString(), 0, (v[0] - v[1] - v[2]).compareTo(v[3]))
        }
        val x = all.rowsOf("Xloroform").single()
        assertEquals(listOf("500", "0", "100", "400"), listOf("Kelgan miqdor", "Jami berilgan miqdor", "Hisobdan chiqarilgan", "Joriy qoldiq").map { num(all.cell(x, it)) })
        // Ustun «Jami berilgan miqdor» va «Joriy qoldiq» orasida.
        assertEquals(all.column("Jami berilgan miqdor") + 1, all.column("Hisobdan chiqarilgan"))
        assertEquals(all.column("Hisobdan chiqarilgan") + 1, all.column("Joriy qoldiq"))
    }

    @Test
    fun issuesWithBothDepartmentAndDistrictMeanEither() {
        seed()
        val chilonzor = services.directory.districts().single { it.name == "Chilonzor" }
        val data = build(ReportKind.ISSUES, ReportFilter(departmentId = deptId("Biologiya"), districtId = chilonzor.id))
        assertEquals(listOf("Bo‘lim: Biologiya yoki Tuman: Chilonzor"), data.filterLines)
        assertEquals(listOf("Biologiya", "Chilonzor"), data.rows.map { data.cell(it, "Kimga") })
        // Bittasi tanlansa — avvalgidek.
        assertEquals(listOf("Tuman: Chilonzor"), build(ReportKind.ISSUES, ReportFilter(districtId = chilonzor.id)).filterLines)
        assertEquals(1, build(ReportKind.ISSUES, ReportFilter(districtId = chilonzor.id)).rows.size)
    }

    @Test
    fun longTextColumnsAreWideEnough() {
        assertEquals(26, build(ReportKind.DISTRICTS).columns.single { it.title == "Tashkilot" }.width)
        assertEquals(18, build(ReportKind.ISSUES).columns.single { it.title == "Turi" }.width)
    }

    @Test
    fun buildBatchesFormatsExactlyTheGivenRows() {
        seed()
        // Butun ro‘yxat — BATCHES hisoboti bilan bir xil.
        val full = builder.buildBatches(services.queries.batchRows(today = today), emptyList(), today)
        val report = build(ReportKind.BATCHES)
        assertEquals(ReportKind.BATCHES, full.kind)
        assertEquals(report.title, full.title)
        assertEquals(report.columns, full.columns)
        assertEquals(report.rows, full.rows)
        assertEquals(report.footerLines, full.footerLines)
        assertEquals(listOf("Filtr: yo‘q"), full.filterLines)
        // Ekrandagi tanlov (seriya «ES» + faqat yaroqli) — aynan shu qatorlar va berilgan filtr qatorlari.
        val shown = services.queries.batchRows(BatchFilter(series = "ES"), today).filter { it.state == ExpiryState.GREEN }
        val data = builder.buildBatches(shown, listOf("Seriya: ES", "Holati: Yaroqli"), today)
        assertEquals(listOf("Seriya: ES", "Holati: Yaroqli"), data.filterLines)
        assertEquals(listOf("ES-003"), data.rows.filter { !it.summary }.map { data.cell(it, "Seriya raqami") })
        assertEquals(listOf("Jami: Etil spirti (1 ta partiya)"), data.rows.filter { it.summary }.map { data.cell(it, "Reaktiv nomi") })
        assertEquals("Jami: 1 ta reaktiv, 1 ta partiya", data.footerLines.first())
        assertEquals("Yaratilgan: 29.10.2026 09:00", data.generatedLine)
    }

    @Test
    fun periodReportsCountReturnsLikeTheMovementReport() {
        // Aseton: 01.08 da 10 l keldi; 15.09 da Chilonzorga 2 l; 03.10 da 0,5 l qaytdi. Biologiyaga 20.08 da 1 l, 10.09 da 0,25 l qaytdi.
        fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
        val aseton = newBatch("Aseton", "A-1", d(12, 31).plusYears(1), "10", received = d(8, 1))
        services.stock.issue(
            IssueInput(
                d(9, 15), docNo = "T-7",
                lines = listOf(IssueLine(aseton, "2", L, RecipientType.DISTRICT, districtName = "Chilonzor")),
            ),
        )
        issueTo("Biologiya", aseton, "1", L, date = d(8, 20))
        val bio = services.queries.issueItems().single { it.recipientName == "Biologiya" }
        services.stock.returnItem(bio.id, ReturnInput(d(9, 10), "250", ML))
        val september = ReportFilter(from = d(9, 1), to = d(9, 30))
        val october = ReportFilter(from = d(10, 1), to = d(10, 31))
        val before = build(ReportKind.DISTRICTS, september)
        val chilonzor = services.queries.issueItems().single { it.districtName == "Chilonzor" }
        services.stock.returnItem(chilonzor.id, ReturnInput(d(10, 3), "0,5", L))

        // Sentyabr hisoboti 03.10 dagi qaytarishdan keyin ham o‘zgarmaydi.
        val sepDistricts = build(ReportKind.DISTRICTS, september)
        assertEquals(before.rows, sepDistricts.rows)
        assertEquals(before.footerLines, sepDistricts.footerLines)
        val row = sepDistricts.rows.single()
        assertEquals(listOf("2", "0", "2"), listOf("Berilgan", "Qaytarilgan", "Sof berilgan").map { num(sepDistricts.cell(row, it)) })
        assertEquals("Jami: 1 ta berish, sof 2 l", sepDistricts.footerLines.last())

        // Hisobot qatorlari o‘z birligida — asosiy birlikka o‘tkazib qo‘shiladi.
        fun sum(data: ReportData, title: String): Long = data.rows.sumOf { r ->
            (data.cell(r, title) as BigDecimal?)?.let { Qty.fromDecimal(it, MeasureUnit.of(data.cell(r, "Birlik") as String)!!) } ?: 0L
        }
        for ((filter, expected) in listOf(september to (litres("2") to ml("250")), october to (0L to ml("500")))) {
            val movement = build(ReportKind.MOVEMENT, filter)
            val issued = sum(movement, "Berilgan")
            val returned = sum(movement, "Qaytarilgan")
            assertEquals(expected, issued to returned)
            val districts = build(ReportKind.DISTRICTS, filter)
            val departments = build(ReportKind.BY_DEPARTMENT, filter)
            // Tumanlar + bo‘limlar = davrdagi harakat (hisobdan chiqarish yo‘q).
            assertEquals(issued, sum(districts, "Berilgan") + sum(departments, "Bo‘limga berilgan"))
            assertEquals(returned, sum(districts, "Qaytarilgan") + sum(departments, "Qaytarilgan"))
            val issues = build(ReportKind.ISSUES, filter)
            assertEquals(issued, sum(issues, "Miqdor"))
            assertEquals(returned, sum(issues, "Qaytarilgan"))
        }

        // Oktyabr: sentyabrdagi berishning qaytarishi — alohida qator (berilgan bo‘sh, sof manfiy).
        val octDistricts = build(ReportKind.DISTRICTS, october)
        val back = octDistricts.rows.single()
        assertNull(octDistricts.cell(back, "Berilgan"))
        assertEquals(listOf("0.5", "-0.5"), listOf("Qaytarilgan", "Sof berilgan").map { num(octDistricts.cell(back, it)) })
        assertEquals(d(10, 3), octDistricts.cell(back, "Sana"))
        assertEquals("T-7", octDistricts.cell(back, "Hujjat raqami"))
        assertTrue(octDistricts.cell(back, "Tashkilot").toString(), octDistricts.cell(back, "Tashkilot").toString().contains("15.09.2026 dagi berishdan"))
        assertEquals("Jami: 0 ta berish, oldingi berishlardan 1 ta qaytarish, sof -500 ml", octDistricts.footerLines.last())
        val sepIssues = build(ReportKind.ISSUES, september)
        val returnRow = sepIssues.rows.single { sepIssues.cell(it, "Turi") == "Qaytarish" }
        assertEquals("Biologiya", sepIssues.cell(returnRow, "Kimga"))
        assertEquals("Jami sarf (qaytarilgani ayirilgan): 1,75 l", sepIssues.footerLines.last())
        val sepDept = build(ReportKind.BY_DEPARTMENT, september)
        val bioRow = sepDept.rows.single()
        assertEquals(listOf("0", "0.25", "-0.25"), listOf("Bo‘limga berilgan", "Qaytarilgan", "Sof berilgan").map { num(sepDept.cell(bioRow, it)) })

        // Sahifalar jamlari ham shu qoida bilan.
        val places = PlacesQueries(services)
        val sepSummary = places.districtSummary(from = d(9, 1), to = d(9, 30))
        assertEquals(litres("2"), sepSummary.districts.single().net.values.single())
        assertEquals(0L, sepSummary.perReagent.single().total.returned)
        assertEquals(0L, sepSummary.items.single().returnedQty)
        assertEquals(litres("0,5"), sepSummary.items.single().returnedTotal) // qaytarish oynasi uchun — hammasi
        val octSummary = places.districtSummary(from = d(10, 1), to = d(10, 31))
        assertEquals(-litres("0,5"), octSummary.districts.single().net.values.single())
        assertEquals(0, octSummary.districts.single().items)
        assertEquals(litres("0,5"), octSummary.perReagent.single().total.returned)
        assertEquals(1, octSummary.earlierReturns.size)
        assertEquals(litres("1,5"), places.districtSummary().districts.single().net.values.single()) // davrsiz — hammasi
        val bioId = deptId("Biologiya")
        val sepBio = places.departmentSummary(bioId, from = d(9, 1), to = d(9, 30), today = today)
        val total = sepBio.issuedTo.single()
        assertEquals(0L, total.issued)
        assertEquals(ml("250"), total.returned)
        assertEquals(0, total.items)
        assertEquals(
            places.departmentHistory(bioId, from = d(9, 1), to = d(9, 30)).filter { it.kind == PlaceHistoryKind.RETURN }.sumOf { it.qty },
            total.returned,
        )
    }

    @Test
    fun byDepartmentShowsOwnedRemainderAndIssuedTotals() {
        seed()
        val data = build(ReportKind.BY_DEPARTMENT)
        val sud = data.rows.filter { data.cell(it, "Bo‘lim") == "Sud-kimyo" }
        assertEquals(listOf("Aseton", "Natriy xlorid"), sud.map { data.cell(it, "Reaktiv nomi") })
        assertEquals(listOf("2", "0.75"), sud.map { num(data.cell(it, "Bo‘limdagi qoldiq")) })
        val bio = data.rows.single { data.cell(it, "Bo‘lim") == "Biologiya" }
        assertEquals("Etil spirti", data.cell(bio, "Reaktiv nomi"))
        assertEquals("0", num(data.cell(bio, "Bo‘limdagi qoldiq")))
        assertEquals("2", num(data.cell(bio, "Sof berilgan")))
        assertNull(bio.state)
        assertTrue(data.footerLines.toString(), data.footerLines.contains("Sud-kimyo: qoldiq 2 l · 750 g; berilgan (sof) 0"))
        // Davr faqat berilganlarga qo‘llanadi.
        val late = build(ReportKind.BY_DEPARTMENT, ReportFilter(from = LocalDate.of(2026, 10, 15), departmentId = deptId("Biologiya")))
        assertEquals(listOf("Davr: 15.10.2026 dan", "Bo‘lim: Biologiya"), late.filterLines)
        assertTrue(late.rows.isEmpty())
        assertEquals(listOf("Ma’lumot topilmadi."), late.footerLines)
    }

    @Test
    fun decimalFormattingAndSuggestedFileName() {
        assertEquals("1 250,5", ReportFormat.decimal(BigDecimal("1250.500")))
        assertEquals("-0,25", ReportFormat.decimal(BigDecimal("-0.25")))
        assertEquals("400", ReportFormat.decimal(BigDecimal("4E+2")))
        assertEquals("12 345", ReportFormat.cell(ReportColumnType.INT, 12345L))
        assertEquals("Muddati o‘tgan", ReportFormat.cell(ReportColumnType.STATUS, ExpiryState.EXPIRED))
        assertEquals("05.10.2026", ReportFormat.cell(ReportColumnType.DATE, LocalDate.of(2026, 10, 5)))
        assertEquals("", ReportFormat.cell(ReportColumnType.QTY, null))
        seed()
        val data = build(ReportKind.EXPIRED)
        assertEquals("Muddati o‘tgan reaktivlar 2026-10-29.pdf", ReportFiles.suggestedFileName(data, "pdf"))
        assertEquals(
            "Hisobot a b 2026-10-29.xlsx",
            ReportFiles.suggestedFileName(data.copy(title = "Hisobot: a/b"), ".xlsx"),
        )
    }
}

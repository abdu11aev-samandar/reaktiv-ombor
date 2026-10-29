package uz.reaktivombor.desktop.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.ExpiryState

class QueriesServiceTest : ServiceTestBase() {

    /** Har xil holatdagi partiyalar: yashil, sariq, qizil, muddati o‘tgan, bo‘sh muddati o‘tgan, minimal qoldiqdan kam. */
    private fun sample(): Map<String, Long> {
        val green = newBatch("Etil spirti", "G-1", today.plusDays(200), "5", departmentId = deptId("Biologiya"))
        val yellow = newBatch("Etil spirti", "Y-1", today.plusDays(60), "1")
        val red = newBatch("Aseton", "R-1", today.plusDays(10), "2", minStock = "3", minStockUnit = L)
        val expired = newBatch("Xloroform", "E-1", today.minusDays(2), "500", unit = ML, received = today.minusDays(200))
        val emptyExpired = newBatch("Xloroform", "E-0", today.minusDays(30), "100", unit = ML, received = today.minusDays(200))
        services.stock.writeOff(emptyExpired, today, "100", ML, "Muddati o‘tgan")
        return mapOf("green" to green, "yellow" to yellow, "red" to red, "expired" to expired, "emptyExpired" to emptyExpired)
    }

    @Test
    fun dashboardCounts() {
        val b = sample()
        issueTo("Gistologiya", b.getValue("green"), "1", L)
        services.stock.issue(
            IssueInput(today, lines = listOf(IssueLine(b.getValue("yellow"), "200", ML, RecipientType.DISTRICT, districtName = "Chilonzor"))),
        )
        val d = services.queries.dashboard(today)
        assertEquals(3, d.reagentTypes)
        assertEquals(4, d.activeBatches) // bo‘sh partiya hisoblanmaydi
        assertEquals(2, d.expiringBatches) // sariq + qizil
        assertEquals(1, d.expiredBatches)
        assertEquals(1, d.belowMinReagents) // Aseton: 2 l < 3 l
        assertEquals(1, d.stateCounts[ExpiryState.GREEN])
        assertEquals(1, d.stateCounts[ExpiryState.YELLOW])
        assertEquals(1, d.stateCounts[ExpiryState.RED])
        assertEquals(1, d.stateCounts[ExpiryState.EXPIRED])
        assertEquals(0, d.stateCounts[ExpiryState.UNKNOWN])
        val bio = d.byDepartment.single { it.name == "Biologiya" }
        assertEquals(1, bio.ownedBatches)
        assertEquals(mapOf(Dimension.VOLUME to litres("4")), bio.remainder)
        val gist = d.byDepartment.single { it.name == "Gistologiya" }
        assertEquals(mapOf(Dimension.VOLUME to litres("1")), gist.issuedTo)
        assertEquals(1, gist.issuedItems)
        assertEquals(mapOf(Dimension.VOLUME to ml("200")), d.byDistrict.single().issued)
        assertEquals(5, d.recentReceipts.size)
        assertEquals(3, d.recentIssues.size) // hisobdan chiqarish ham operatsiya
    }

    @Test
    fun notificationsListExpiredRedBelowMinAndYellow() {
        sample()
        val list = services.queries.notifications(today)
        assertEquals(
            listOf(NotificationKind.EXPIRED, NotificationKind.RED, NotificationKind.BELOW_MIN, NotificationKind.YELLOW),
            list.map { it.kind },
        )
        assertTrue(list[0].text, list[0].text.contains("2 kun oldin o‘tgan"))
        assertTrue(list[1].text, list[1].text.contains("10 kun qoldi"))
        assertTrue(list[2].text, list[2].text.contains("minimal 3 l dan kam"))
        assertEquals(NotificationTarget.STOCK, list[2].target)
        assertEquals(NotificationTarget.EXPIRY, list[0].target)
        assertEquals(3, services.queries.notificationCount(today)) // qo‘ng‘iroqcha: muddati o‘tgan + qizil + minimal
    }

    @Test
    fun ledgerShowsARunningBalancePerBatch() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1", received = today.minusDays(5))
        services.stock.addReceipt(batch, ReceiptInput(today.minusDays(3), "500", ML, docNo = "YX-7"))
        issueTo("Biologiya", batch, "300", ML, date = today.minusDays(2))
        val item = services.queries.issueItems().single()
        services.stock.returnItem(item.id, ReturnInput(today, "100", ML))
        val ledger = services.queries.ledger(batch)
        assertEquals(listOf(litres("1"), litres("1,5"), litres("1,2"), litres("1,3")), ledger.map { it.balance })
        assertEquals(listOf(MovementKind.RECEIPT, MovementKind.RECEIPT, MovementKind.ISSUE, MovementKind.RETURN), ledger.map { it.kind })
        assertEquals("Kirim (boshlang‘ich)", ledger[0].description)
        assertEquals("YX-7", ledger[1].docNo)
        assertEquals("Berildi: Biologiya bo‘limi", ledger[2].description)
        assertEquals("Qaytarildi: Biologiya bo‘limi", ledger[3].description)
        // Reaktiv bo‘yicha jurnal: har partiya o‘z qoldig‘i bilan.
        val other = newBatch("Etil spirti", "ES-002", today.plusDays(300), "2")
        val reagentLedger = services.queries.reagentLedger(services.reagents.list().single().id)
        assertEquals(5, reagentLedger.size)
        assertEquals(litres("2"), reagentLedger.single { it.batchId == other }.balance)
    }

    @Test
    fun ledgerBalanceStaysNonNegativeWhenAnIssueIsMovedToAnEarlierDate() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "100", unit = ML, received = today.minusDays(30))
        issueTo("Biologiya", batch, "100", ML, date = today.minusDays(10))
        val item = services.queries.issueItems().single()
        services.stock.correctIssueItem(item.id, toDepartment(batch, "100", ML, "Biologiya"), today.minusDays(20), "Sana xato")
        val ledger = services.queries.ledger(batch)
        // Bekor qilingan berish va uning teskarisi ro‘yxatda qoladi, lekin qoldiqni o‘zgartirmaydi.
        assertEquals(
            listOf(MovementKind.RECEIPT, MovementKind.ISSUE, MovementKind.ISSUE, MovementKind.REVERSAL),
            ledger.map { it.kind },
        )
        assertEquals(listOf(ml("100"), 0L, 0L, 0L), ledger.map { it.balance })
        assertTrue(ledger[2].operationCancelled)
        assertEquals(services.stock.remainder(batch), ledger.last().balance)

        // Bekor qilib, ertaroq sana bilan qayta kiritish ham.
        val other = newBatch("Aseton", "A-1", today.plusDays(200), "100", unit = ML, received = today.minusDays(30))
        issueTo("Gistologiya", other, "60", ML, date = today.minusDays(5))
        services.stock.cancelIssueItem(services.queries.issueItems(OpFilter(batchId = other)).single().id, "Sana xato")
        issueTo("Gistologiya", other, "60", ML, date = today.minusDays(15))
        val second = services.queries.ledger(other)
        assertTrue(second.map { it.balance }.toString(), second.all { it.balance >= 0 })
        assertEquals(listOf(ml("100"), ml("40"), ml("40"), ml("40")), second.map { it.balance })
        // Reaktiv va to‘liq jurnal ham xuddi shunday.
        assertTrue(services.queries.fullLedger().all { it.balance >= 0 })
    }

    @Test
    fun departmentSummaryShowsOwnedBatchesAndIssuedTotals() {
        val owned = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5", departmentId = deptId("Sud-kimyo"))
        val other = newBatch("Aseton", "A-1", today.plusDays(200), "1")
        issueTo("Sud-kimyo", other, "300", ML, date = today.minusDays(3))
        issueTo("Sud-kimyo", other, "200", ML)
        val item = services.queries.issueItems(OpFilter(departmentId = deptId("Sud-kimyo"))).first()
        services.stock.returnItem(item.id, ReturnInput(today, "50", ML))
        val summary = services.queries.departmentSummary(deptId("Sud-kimyo"), today = today)
        assertEquals(listOf(owned), summary.ownedBatches.map { it.batchId })
        val total = summary.issuedTo.single()
        assertEquals("Aseton", total.name)
        assertEquals(ml("500"), total.issued)
        assertEquals(ml("50"), total.returned)
        assertEquals(ml("450"), total.net)
        assertEquals(2, total.items)
        assertEquals(today.minusDays(3), total.firstDate)
        assertEquals(2, summary.history.size)
        assertEquals(1, services.queries.departmentSummary(deptId("Sud-kimyo"), from = today, today = today).history.size)
    }

    @Test
    fun batchFiltersWorkOnEveryParameter() {
        val b = sample()
        val q = services.queries
        assertEquals(listOf(b["red"]), q.batchRows(BatchFilter(text = "ASETON"), today).map { it.batchId })
        assertEquals(listOf(b["yellow"]), q.batchRows(BatchFilter(series = "y-"), today).map { it.batchId })
        assertEquals(listOf(b["green"]), q.batchRows(BatchFilter(departmentId = deptId("Biologiya")), today).map { it.batchId })
        assertEquals(listOf(b["expired"]), q.batchRows(BatchFilter(states = setOf(ExpiryState.EXPIRED), includeEmpty = false), today).map { it.batchId })
        assertEquals(2, q.batchRows(BatchFilter(states = setOf(ExpiryState.EXPIRED)), today).size)
        assertEquals(
            listOf(b["yellow"], b["green"]), // bir nom ichida — muddati bo‘yicha
            q.batchRows(BatchFilter(expiresFrom = today.plusDays(50), expiresTo = today.plusDays(250)), today).map { it.batchId },
        )
        assertEquals(
            listOf(b["red"], b["green"]),
            q.batchRows(BatchFilter(remainderMin = litres("2"), remainderUnit = L), today).map { it.batchId },
        )
        assertEquals(
            listOf(b["expired"]),
            q.batchRows(BatchFilter(remainderMin = 1, remainderMax = ml("600"), remainderUnit = ML), today).map { it.batchId },
        )
        services.stock.issue(
            IssueInput(today, lines = listOf(IssueLine(b.getValue("yellow"), "200", ML, RecipientType.DISTRICT, districtName = "Chilonzor"))),
        )
        val chilonzor = services.directory.districts().single().id
        assertEquals(listOf(b["yellow"]), q.batchRows(BatchFilter(districtId = chilonzor), today).map { it.batchId })
        // Filtr guruh jamiga ta’sir qilmaydi.
        assertEquals(litres("5,8"), q.batchRows(BatchFilter(series = "Y-1"), today).single().groupTotal)
    }

    @Test
    fun stockTotalsAndAuditLog() {
        sample()
        val totals = services.queries.stockTotals(today = today)
        assertEquals(listOf("Aseton", "Etil spirti", "Xloroform"), totals.map { it.name })
        val aseton = totals.first()
        assertTrue(aseton.belowMin)
        assertEquals(litres("3"), aseton.minStock)
        val xloroform = totals.last()
        assertEquals(2, xloroform.batches)
        assertEquals(1, xloroform.activeBatches)
        assertEquals(ml("100"), xloroform.writtenOff)
        assertEquals(ExpiryState.EXPIRED, xloroform.worstState)
        assertEquals(ML, xloroform.unit)

        val log = services.queries.auditLogs()
        assertTrue(log.first().at >= log.last().at)
        assertEquals(1, services.queries.auditLogs(AuditFilter(action = AuditAction.WRITE_OFF)).size)
        assertEquals(1, services.queries.auditLogs(AuditFilter(text = "muddati o‘tgan", action = AuditAction.WRITE_OFF)).size)
        assertTrue(services.queries.auditLogs(AuditFilter(from = today.plusDays(1))).isEmpty())
        assertEquals(5, services.queries.auditLogs(AuditFilter(action = AuditAction.BATCH_CREATED)).size)
    }
}

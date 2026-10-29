package uz.reaktivombor.desktop.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.DuplicateBatchError
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.UserError

class ReagentsServiceTest : ServiceTestBase() {

    @Test
    fun newReagentCreatesReagentBatchAndInitialReceipt() {
        val id = services.reagents.createBatch(
            NewBatch(
                name = "  Etil   spirti ",
                formula = "C2H5OH",
                manufacturer = "Kimyo zavodi",
                series = " ES-001 ",
                producedOn = today.minusDays(60),
                receivedOn = today.minusDays(5),
                initialQtyText = "5",
                unit = L,
                expiresOn = today.plusDays(200),
                departmentId = deptId("Biologiya"),
                storageLocation = "2-shkaf",
                responsible = "Karimova N.",
                receivedBy = "Aliyev A.",
            ),
        )
        val reagent = services.reagents.list().single()
        assertEquals("Etil spirti", reagent.name) // ortiqcha bo‘shliqlarsiz
        assertEquals("C2H5OH", reagent.formula)
        val batch = services.reagents.batch(id)!!
        assertEquals("ES-001", batch.series)
        assertEquals(L, batch.unit)
        assertEquals(deptId("Biologiya"), batch.departmentId)
        val receipt = services.queries.receipts().single()
        assertTrue(receipt.isInitial)
        assertEquals(litres("5"), receipt.qty)
        assertEquals("Aliyev A.", receipt.receivedBy)
        assertEquals(litres("5"), services.stock.remainder(id))
        assertEquals(1L, count("stock_movements"))
        val row = row(id)
        assertEquals(ExpiryState.GREEN, row.state)
        assertEquals(200L, row.daysLeft)
        assertEquals("Biologiya", row.departmentName)
        assertEquals(litres("5"), row.receivedQty)
        val actions = services.queries.auditLogs().map { it.action }
        assertTrue(AuditAction.REAGENT_CREATED in actions)
        assertTrue(AuditAction.BATCH_CREATED in actions)
    }

    @Test
    fun sameNameDifferentSeriesCreatesSeparateBatches() {
        val a = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val b = newBatch("etil  SPIRTI", "ES-002", today.plusDays(200), "10")
        assertNotEquals(a, b)
        assertEquals(1, services.reagents.list().size) // bitta reaktiv
        assertEquals(2, services.reagents.batchesOf(services.reagents.list().single().id).size)
        assertEquals(litres("5"), services.stock.remainder(a))
        assertEquals(litres("10"), services.stock.remainder(b))
    }

    @Test
    fun differentExpiriesAreSeparateBatchesWithTheirOwnState() {
        val red = newBatch("Aseton", "A-1", today.plusDays(10), "1")
        val yellow = newBatch("Aseton", "A-2", today.plusDays(45), "1")
        val green = newBatch("Aseton", "A-3", today.plusDays(120), "1")
        val expired = newBatch("Aseton", "A-4", today.minusDays(3), "1", received = today.minusDays(100))
        assertEquals(ExpiryState.RED, row(red).state)
        assertEquals(ExpiryState.YELLOW, row(yellow).state)
        assertEquals(ExpiryState.GREEN, row(green).state)
        assertEquals(ExpiryState.EXPIRED, row(expired).state)
        assertEquals(-3L, row(expired).daysLeft)
        // Hammasi bitta guruhda, har biri alohida qator.
        assertEquals(litres("4"), row(green).groupTotal)
    }

    @Test
    fun groupTotalOfTheEtilSpirtiExample() {
        val es1 = newBatch("Etil spirti", "ES-001", today.plusDays(20), "5")
        val es2 = newBatch("Etil spirti", "ES-002", today.plusDays(60), "10")
        val es3 = newBatch("Etil spirti", "ES-003", today.plusDays(200), "4")
        issueTo("Biologiya", es1, "2", L)
        issueTo("Gistologiya", es2, "3", L)

        val rows = services.queries.batchRows(today = today)
        assertEquals(listOf("ES-001", "ES-002", "ES-003"), rows.map { it.series })
        assertEquals(listOf(litres("5"), litres("10"), litres("4")), rows.map { it.receivedQty })
        assertEquals(listOf(litres("3"), litres("7"), litres("4")), rows.map { it.remainder })
        rows.forEach { row ->
            assertEquals(litres("19"), row.groupReceived)
            assertEquals(litres("14"), row.groupTotal)
            assertEquals(L, row.groupUnit)
        }
        assertEquals("14 l", uz.reaktivombor.desktop.core.Qty.formatWithUnit(rows[0].groupTotal, rows[0].groupUnit))
        // Har bir partiya o‘z holati bilan.
        assertEquals(listOf(ExpiryState.RED, ExpiryState.YELLOW, ExpiryState.GREEN), rows.map { it.state })
        val total = services.queries.stockTotals(today = today).single()
        assertEquals(litres("19"), total.received)
        assertEquals(litres("14"), total.remainder)
        assertEquals(litres("5"), total.issued)
        assertEquals(3, total.batches)
        assertEquals(ExpiryState.RED, total.worstState)
        assertEquals(today.plusDays(20), total.nearestExpiry)
        assertEquals(es1, rows[0].batchId)
        assertEquals(es3, rows[2].batchId)
        assertEquals(es2, rows[1].batchId)
    }

    @Test
    fun rowsOfTheSameNameAreAdjacentAndOrderedByExpiry() {
        newBatch("Etil spirti", "ES-003", today.plusDays(300), "1")
        newBatch("Xloroform", "X-1", today.plusDays(50), "1")
        newBatch("Aseton", "A-1", today.plusDays(400), "1")
        newBatch("etil spirti", "ES-001", today.plusDays(30), "1")
        newBatch("Natriy xlorid", "N-1", today.plusDays(100), "500", unit = G)
        newBatch("ETIL SPIRTI", "ES-002", today.plusDays(90), "1")
        newBatch("O‘yuvchi natriy", "O-1", today.plusDays(10), "1", unit = KG)
        newBatch("o'yuvchi natriy", "O-2", today.plusDays(5), "1", unit = KG) // boshqa tutuq belgisi — o‘sha nom

        val rows = services.queries.batchRows(today = today)
        assertEquals(
            listOf("Aseton", "Etil spirti", "Etil spirti", "Etil spirti", "Natriy xlorid", "O‘yuvchi natriy", "O‘yuvchi natriy", "Xloroform"),
            rows.map { it.name },
        )
        assertEquals(listOf("ES-001", "ES-002", "ES-003"), rows.filter { it.name == "Etil spirti" }.map { it.series })
        assertEquals(listOf("O-2", "O-1"), rows.filter { it.name == "O‘yuvchi natriy" }.map { it.series })
        assertEquals(5, services.reagents.list().size)
    }

    @Test
    fun exactDuplicateBatchIsRefusedAndPointsToTheExistingBatch() {
        val first = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val error = assertThrows(DuplicateBatchError::class.java) {
            newBatch("etil spirti", "es-001", today.plusDays(200), "3")
        }
        assertEquals(first, error.existingBatchId)
        assertTrue(error.exact)
        assertTrue(error.message.contains("qo‘shimcha kirim"))
        // Hech narsa qo‘shilmadi.
        assertEquals(1L, count("batches"))
        assertEquals(1L, count("receipts"))
        // Taklif qilingan yo‘l: shu partiyaga qo‘shimcha kirim.
        services.stock.addReceipt(first, ReceiptInput(today, "3", L))
        assertEquals(litres("8"), services.stock.remainder(first))
    }

    @Test
    fun sameSeriesWithAnotherExpiryNeedsConfirmation() {
        val first = newBatch("Etil spirti", "ES-001", today.plusDays(200), "5")
        val input = NewBatch(
            name = "Etil spirti", series = "ES-001", receivedOn = today, initialQtyText = "2", unit = L, expiresOn = today.plusDays(300),
        )
        val error = assertThrows(DuplicateBatchError::class.java) { services.reagents.createBatch(input) }
        assertFalse(error.exact)
        assertEquals(first, error.existingBatchId)
        assertEquals(1L, count("batches"))
        val second = services.reagents.createBatch(input, confirmDuplicate = true)
        assertNotEquals(first, second)
        assertEquals(2L, count("batches"))
    }

    @Test
    fun missingRequiredFieldsGiveUzbekMessages() {
        val error = assertThrows(UserError::class.java) {
            services.reagents.createBatch(
                NewBatch(name = "  ", series = "", receivedOn = null, initialQtyText = "", unit = null, expiresOn = null),
            )
        }
        assertEquals("Reaktiv nomi kiritilishi shart.", error.fields["name"])
        assertEquals("Seriya raqami kiritilishi shart.", error.fields["series"])
        assertEquals("Kelgan sana kiritilishi shart.", error.fields["receivedOn"])
        assertEquals("Yaroqlilik muddati kiritilishi shart.", error.fields["expiresOn"])
        assertEquals("O‘lchov birligini tanlang.", error.fields["unit"])
        assertTrue(error.message.contains("Reaktiv nomi kiritilishi shart."))
        val noQty = assertThrows(UserError::class.java) {
            services.reagents.createBatch(
                NewBatch(name = "A", series = "1", receivedOn = today, initialQtyText = " ", unit = L, expiresOn = today.plusDays(10)),
            )
        }
        assertEquals("Kelgan miqdor kiritilishi shart.", noQty.fields["initialQtyText"])
        assertEquals(0L, count("reagents"))
        assertEquals(0L, count("batches"))
    }

    @Test
    fun invalidDatesAreRefused() {
        fun attempt(produced: java.time.LocalDate?, received: java.time.LocalDate?, expires: java.time.LocalDate?) =
            assertThrows(UserError::class.java) {
                services.reagents.createBatch(
                    NewBatch(
                        name = "Aseton", series = "A-1", producedOn = produced, receivedOn = received, initialQtyText = "1",
                        unit = L, expiresOn = expires,
                    ),
                )
            }
        val future = attempt(null, today.plusDays(1), today.plusDays(100))
        assertEquals("Kelgan sana (30.10.2026) bugundan keyin bo‘lishi mumkin emas.", future.fields["receivedOn"])
        val producedAfterReceived = attempt(today.minusDays(1), today.minusDays(5), today.plusDays(100))
        assertEquals("Ishlab chiqarilgan sana kelgan sanadan keyin bo‘lishi mumkin emas.", producedAfterReceived.fields["producedOn"])
        val expiresBeforeProduced = attempt(today.minusDays(10), today.minusDays(5), today.minusDays(20))
        assertEquals("Yaroqlilik muddati ishlab chiqarilgan sanadan oldin bo‘lishi mumkin emas.", expiresBeforeProduced.fields["expiresOn"])
        val tooOld = attempt(null, java.time.LocalDate.of(1989, 12, 31), today.plusDays(100))
        assertEquals("Kelgan sana 01.01.1990 – 31.12.2199 oralig‘ida bo‘lishi kerak.", tooOld.fields["receivedOn"])
        val tooFar = attempt(null, today, java.time.LocalDate.of(2200, 1, 1))
        assertTrue(tooFar.fields.getValue("expiresOn").contains("oralig‘ida"))
        val producedInFuture = attempt(today.plusDays(2), today, today.plusDays(100))
        assertTrue(producedInFuture.fields.getValue("producedOn").contains("bugundan keyin"))
        assertEquals(0L, count("batches"))
    }

    @Test
    fun invalidQuantitiesAreRefused() {
        fun attempt(text: String) = assertThrows(UserError::class.java) {
            newBatch("Aseton", "A-1", today.plusDays(100), text)
        }.fields.getValue("initialQtyText")
        assertTrue(attempt("abc").contains("son bo‘lishi kerak"))
        assertEquals("Kelgan miqdor manfiy bo‘lishi mumkin emas.", attempt("-5"))
        assertEquals("Kelgan miqdor noldan katta bo‘lishi kerak.", attempt("0"))
        assertTrue(attempt("1,2345").contains("3 ta raqam"))
        assertTrue(attempt("1e3").contains("son bo‘lishi kerak"))
        assertTrue(attempt("1000000000").contains("juda katta"))
        val badMin = assertThrows(UserError::class.java) {
            newBatch("Aseton", "A-1", today.plusDays(100), "1", minStock = "x", minStockUnit = L)
        }
        assertTrue(badMin.fields.getValue("minStockText").contains("Minimal qoldiq"))
        assertEquals(0L, count("batches"))
    }

    @Test
    fun renamingAReagentToAnotherReagentsNameIsRefused() {
        newBatch("Aseton", "A-1", today.plusDays(100), "1")
        newBatch("Etil spirti", "E-1", today.plusDays(100), "1")
        val etil = services.reagents.list().first { it.name == "Etil spirti" }
        val error = assertThrows(UserError::class.java) { services.reagents.updateReagent(etil.id, ReagentEdit(name = "ASETON")) }
        assertTrue(error.message.contains("allaqachon bor"))
        services.reagents.updateReagent(etil.id, ReagentEdit(name = "Etil spirti 96%", formula = "C2H5OH", minStockText = "2", minStockUnit = L))
        val renamed = services.reagents.get(etil.id)!!
        assertEquals("Etil spirti 96%", renamed.name)
        assertEquals(litres("2"), renamed.minStock)
        assertTrue(services.queries.auditLogs().any { it.action == AuditAction.REAGENT_UPDATED })
    }

    @Test
    fun updateBatchChangesDescriptiveFieldsOnly() {
        val id = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        services.reagents.updateBatch(
            id,
            BatchEdit(series = "A-1b", receivedOn = today.minusDays(10), expiresOn = today.plusDays(20), storageLocation = "Muzlatgich"),
        )
        val row = row(id)
        assertEquals("A-1b", row.series)
        assertEquals(ExpiryState.RED, row.state)
        assertEquals("Muzlatgich", row.storageLocation)
        assertEquals(litres("1"), row.remainder)
        // Berilgandan keyin kelgan sanani undan keyinga surib bo‘lmaydi.
        issueTo("Biologiya", id, "100", ML, date = today.minusDays(2))
        val error = assertThrows(UserError::class.java) {
            services.reagents.updateBatch(id, BatchEdit(series = "A-1b", receivedOn = today.minusDays(1), expiresOn = today.plusDays(20)))
        }
        assertTrue(error.message.contains("kelgan sana undan keyin"))
    }

    @Test
    fun deleteIsAllowedOnlyWithoutHistory() {
        val fresh = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        val used = newBatch("Etil spirti", "E-1", today.plusDays(100), "1")
        issueTo("Biologiya", used, "100", ML)

        val refused = assertThrows(UserError::class.java) { services.reagents.deleteBatch(used) }
        assertEquals("Partiyaning operatsiyalar tarixi bor — o‘chirib bo‘lmaydi, arxivlang.", refused.message)
        val etil = services.reagents.list().first { it.name == "Etil spirti" }
        val reagentRefused = assertThrows(UserError::class.java) { services.reagents.deleteReagent(etil.id) }
        assertTrue(reagentRefused.message.contains("o‘chirib bo‘lmaydi, arxivlang"))

        // Faqat boshlang‘ich kirimi bor (xato kiritilgan) partiya o‘chadi; audit yozuvi qoladi.
        services.reagents.deleteBatch(fresh)
        assertNull(services.reagents.batch(fresh))
        val aseton = services.reagents.list().first { it.name == "Aseton" }
        services.reagents.deleteReagent(aseton.id)
        assertNull(services.reagents.get(aseton.id))
        assertEquals(2, services.queries.auditLogs(AuditFilter(action = AuditAction.DELETED)).size)
        assertEquals(litres("0,9"), services.stock.remainder(used))
    }

    @Test
    fun archivedBatchLeavesTheGroupTotalAndCannotBeUsed() {
        val a = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        val b = newBatch("Aseton", "A-2", today.plusDays(200), "2")
        services.reagents.archiveBatch(a)
        assertEquals(listOf(b), services.queries.batchRows(today = today).map { it.batchId })
        assertEquals(litres("2"), row(b).groupTotal)
        assertTrue(row(a).archived)
        assertEquals(litres("1"), row(a).remainder) // qoldiq o‘chmaydi
        val error = assertThrows(UserError::class.java) { issueTo("Biologiya", a, "100", ML) }
        assertTrue(error.message.contains("arxivda"))
        services.reagents.unarchiveBatch(a)
        assertEquals(litres("3"), row(b).groupTotal)
    }

    @Test
    fun nameSuggestionsComeFromExistingReagents() {
        newBatch("Etil spirti", "E-1", today.plusDays(100), "1")
        newBatch("Metil spirti", "M-1", today.plusDays(100), "1")
        newBatch("Aseton", "A-1", today.plusDays(100), "1")
        assertEquals(listOf("Etil spirti", "Metil spirti"), services.reagents.searchNames("spirt"))
        assertEquals(listOf("Metil spirti"), services.reagents.searchNames("met"))
        // Shu bilan boshlanadigani birinchi.
        assertEquals(listOf("Etil spirti", "Metil spirti"), services.reagents.searchNames("ETI"))
    }
}

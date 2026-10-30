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
import uz.reaktivombor.desktop.db.update

class ReagentsServiceTest : ServiceTestBase() {

    /** 2026-yil mart (kirim hisoboti davri). */
    private val march = java.time.LocalDate.of(2026, 3, 1) to java.time.LocalDate.of(2026, 3, 31)

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
        // Qoldig‘i bor partiya arxivlanmaydi — xabar avval hisobdan chiqarishni aytadi (yangi qoida, #2).
        assertEquals(
            "Partiyaning operatsiyalar tarixi bor — o‘chirib bo‘lmaydi, arxivlang. " +
                "Arxivlash uchun avval qoldiqni (0,9 l) «Hisobdan chiqarish» bilan chiqaring.",
            refused.message,
        )
        assertEquals(refused.message, services.reagents.deleteBatchRefusal(used))
        val etil = services.reagents.list().first { it.name == "Etil spirti" }
        val reagentRefused = assertThrows(UserError::class.java) { services.reagents.deleteReagent(etil.id) }
        assertTrue(reagentRefused.message.contains("o‘chirib bo‘lmaydi, arxivlang"))

        // Faqat boshlang‘ich kirimi bor (bugun xato kiritilgan) partiya o‘chadi; audit yozuvi qoladi. Reaktivning oxirgi
        // partiyasi edi — reaktiv ham o‘chadi (nom takliflarida qolib ketmaydi).
        assertNull(services.reagents.deleteBatchRefusal(fresh))
        val asetonId = row(fresh).reagentId
        services.reagents.deleteBatch(fresh)
        assertNull(services.reagents.batch(fresh))
        assertNull(services.reagents.get(asetonId))
        val deleted = services.queries.auditLogs(AuditFilter(action = AuditAction.DELETED))
        assertEquals(listOf("batch", "reagent"), deleted.map { it.entity }.reversed())
        assertTrue(deleted.first().details, deleted.first().details.contains("«Aseton» o‘chirildi (partiyasi qolmadi)"))
        assertEquals(litres("0,9"), services.stock.remainder(used))
    }

    @Test
    fun aBatchEnteredOnAnEarlierDayIsNotDeletedEvenWithoutOtherOperations() {
        val old = newBatch("Etil spirtti", "X-1", today.plusDays(100), "5", received = java.time.LocalDate.of(2026, 3, 1))
        assertEquals(1, services.queries.receipts(OpFilter(from = march.first, to = march.second)).size)
        // Ertasi kuni: kirim hisobotlarda bor — o‘chirilmaydi, xabarda «tarixi» (UI arxivlashni taklif qiladi).
        clock.advanceSeconds(24 * 3600)
        val refusal = services.reagents.deleteBatchRefusal(old)
        assertTrue(refusal, refusal!!.contains("tarixi"))
        assertTrue(refusal, refusal.contains("29.10.2026 da kiritilgan"))
        assertTrue(refusal, refusal.contains("01.03.2026 da kelgan"))
        val error = assertThrows(UserError::class.java) { services.reagents.deleteBatch(old) }
        assertEquals(refusal, error.message)
        assertEquals(1, services.queries.receipts(OpFilter(from = march.first, to = march.second)).size)
        assertTrue(services.queries.auditLogs(AuditFilter(action = AuditAction.DELETED)).isEmpty())
        // Tarix saqlanadigan yo‘l: kirimni sabab bilan bekor qilish, so‘ng arxivlash.
        val receipt = services.queries.receipts(OpFilter(batchId = old)).single()
        services.stock.cancelReceipt(receipt.id, "Xato kiritilgan")
        services.reagents.archiveBatch(old)
        assertTrue(row(old).archived)
        assertEquals(1, services.queries.receipts(OpFilter(batchId = old, includeCancelled = true)).size)
    }

    @Test
    fun deletingOneOfSeveralBatchesKeepsTheReagent() {
        val keep = newBatch("Etil spirti", "E-1", today.plusDays(100), "1")
        val typo = newBatch("Etil spirti", "E-2", today.plusDays(200), "1")
        services.reagents.deleteBatch(typo)
        assertEquals(row(keep).reagentId, services.reagents.list().single().id)
        assertEquals(listOf("Etil spirti"), services.reagents.searchNames("etil"))
        // Xato nom bilan kiritilgan yagona partiya o‘chirilsa — nom takliflarda qolmaydi.
        val wrong = newBatch("Etil spirtti", "X-1", today.plusDays(100), "5")
        assertEquals(listOf("Etil spirti", "Etil spirtti"), services.reagents.searchNames("etil"))
        services.reagents.deleteBatch(wrong)
        assertEquals(listOf("Etil spirti"), services.reagents.searchNames("etil"))
        assertEquals(listOf("Etil spirti"), services.reagents.list(includeArchived = true).map { it.name })
    }

    @Test
    fun archivedBatchLeavesTheGroupTotalAndCannotBeUsed() {
        val a = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        val b = newBatch("Aseton", "A-2", today.plusDays(200), "2")
        // Qoldig‘i bor partiya arxivlanmaydi (aks holda qoldiq jamilar va minimal nazoratdan harakatsiz yo‘qolardi).
        val refused = assertThrows(UserError::class.java) { services.reagents.archiveBatch(a) }
        assertTrue(refused.message, refused.message.contains("1 l qoldiq bor"))
        assertTrue(refused.message, refused.message.contains("«Hisobdan chiqarish»"))
        assertFalse(row(a).archived)
        assertEquals(litres("3"), row(b).groupTotal)
        // Avval hisobdan chiqariladi (harakat yoziladi), keyin arxivlanadi.
        services.stock.writeOff(a, today, "1", L, "Ifloslangan")
        services.reagents.archiveBatch(a)
        assertEquals(listOf(b), services.queries.batchRows(today = today).map { it.batchId })
        assertEquals(litres("2"), row(b).groupTotal)
        assertTrue(row(a).archived)
        assertEquals(0L, row(a).remainder)
        assertEquals(litres("1"), row(a).writtenOffQty)
        val error = assertThrows(UserError::class.java) { issueTo("Biologiya", a, "100", ML) }
        assertTrue(error.message.contains("arxivda"))
        services.reagents.unarchiveBatch(a)
        assertFalse(row(a).archived)
        assertEquals(litres("2"), row(b).groupTotal)
    }

    @Test
    fun aReagentWithStockIsNotArchived() {
        val a = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        val b = newBatch("Aseton", "A-2", today.plusDays(200), "250", unit = ML)
        val reagent = row(a).reagentId
        val refused = assertThrows(UserError::class.java) { services.reagents.archiveReagent(reagent) }
        assertTrue(refused.message, refused.message.contains("2 ta partiyasida qoldiq bor (A-1 — 1 l, A-2 — 250 ml)"))
        assertTrue(refused.message, refused.message.contains("«Hisobdan chiqarish»"))
        assertFalse(services.reagents.get(reagent)!!.archived)
        services.stock.writeOff(a, today, "1", L, "Muddati o‘tgan")
        issueTo("Biologiya", b, "250", ML)
        services.reagents.archiveReagent(reagent)
        assertTrue(services.reagents.get(reagent)!!.archived)
        assertTrue(services.queries.batchRows(today = today).isEmpty())
    }

    @Test
    fun editAuditKeepsOldAndNewValues() {
        val id = newBatch("Etil spirti", "ES-001", today.plusDays(100), "1", departmentId = deptId("Gistologiya"))
        services.reagents.updateBatch(
            id,
            BatchEdit(
                series = "ES-001", manufacturer = "Merck", receivedOn = today.minusDays(10), expiresOn = today.plusDays(100),
                departmentId = deptId("Biologiya"), storageLocation = "Seyf 2", responsible = "Rahimov A.", note = "x".repeat(120),
            ),
        )
        services.reagents.updateBatch(
            id,
            BatchEdit(
                series = "ES-001", manufacturer = "Merck", receivedOn = today.minusDays(10), expiresOn = today.plusDays(100),
                departmentId = null, storageLocation = "Seyf 2", responsible = "Karimova D.", note = "",
            ),
        )
        val batchAudit = services.queries.auditLogs(AuditFilter(action = AuditAction.BATCH_UPDATED)).reversed().map { it.details }
        val first = batchAudit[0]
        listOf(
            "ishlab chiqaruvchi: «—» → «Merck»", "bo‘lim: «Gistologiya» → «Biologiya»", "saqlash joyi: «—» → «Seyf 2»",
            "mas’ul xodim: «—» → «Rahimov A.»", "izoh: «—» → «" + "x".repeat(Reagents.AUDIT_TEXT_MAX) + "…»",
        ).forEach { assertTrue(first, first.contains(it)) }
        val second = batchAudit[1]
        listOf("bo‘lim: «Biologiya» → «—»", "mas’ul xodim: «Rahimov A.» → «Karimova D.»", "izoh: «" + "x".repeat(Reagents.AUDIT_TEXT_MAX) + "…» → «—»")
            .forEach { assertTrue(second, second.contains(it)) }

        val reagent = row(id).reagentId
        services.reagents.updateReagent(reagent, ReagentEdit(name = "Etil spirti", minStockText = "20", minStockUnit = L, note = "Yonuvchan"))
        services.reagents.updateReagent(reagent, ReagentEdit(name = "Etil spirti", minStockText = "30", minStockUnit = L, note = "Yonuvchan modda"))
        val reagentAudit = services.queries.auditLogs(AuditFilter(action = AuditAction.REAGENT_UPDATED)).reversed().map { it.details }
        assertTrue(reagentAudit[0], reagentAudit[0].contains("minimal qoldiq: yo‘q → 20 l"))
        assertTrue(reagentAudit[0], reagentAudit[0].contains("izoh: «—» → «Yonuvchan»"))
        assertTrue(reagentAudit[1], reagentAudit[1].contains("minimal qoldiq: 20 l → 30 l"))
        assertTrue(reagentAudit[1], reagentAudit[1].contains("izoh: «Yonuvchan» → «Yonuvchan modda»"))
        // Yangi partiya bilan minimum o‘zgarsa ham — eski → yangi.
        newBatch("Etil spirti", "ES-002", today.plusDays(200), "1", minStock = "40", minStockUnit = L)
        val viaBatch = services.queries.auditLogs(AuditFilter(action = AuditAction.REAGENT_UPDATED)).first().details
        assertTrue(viaBatch, viaBatch.contains("minimal qoldiq: 30 l → 40 l"))
    }

    @Test
    fun theReceivedDateCannotMoveAfterAnExtraReceipt() {
        val id = newBatch("Aseton", "A-1", today.plusDays(100), "1", received = today.minusDays(20))
        services.stock.addReceipt(id, ReceiptInput(today.minusDays(10), "500", ML))
        val error = assertThrows(UserError::class.java) {
            services.reagents.updateBatch(id, BatchEdit(series = "A-1", receivedOn = today.minusDays(5), expiresOn = today.plusDays(100)))
        }
        assertTrue(error.message, error.message.contains("qo‘shimcha kirim"))
        assertTrue(error.fields.containsKey("receivedOn"))
        assertEquals(today.minusDays(20), services.reagents.batch(id)!!.receivedOn)
        // Qo‘shimcha kirim kunigacha surish mumkin.
        services.reagents.updateBatch(id, BatchEdit(series = "A-1", receivedOn = today.minusDays(10), expiresOn = today.plusDays(100)))
        assertEquals(today.minusDays(10), services.reagents.batch(id)!!.receivedOn)
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

    @Test
    fun changingTheBatchReceivedDateMovesTheInitialReceipt() {
        val id = newBatch("Aseton", "A-1", today.plusDays(100), "1", received = today.minusDays(2))
        services.reagents.updateBatch(id, BatchEdit(series = "A-1", receivedOn = today.minusDays(10), expiresOn = today.plusDays(100)))
        assertEquals(today.minusDays(10), services.reagents.batch(id)!!.receivedOn)
        // Boshlang‘ich kirim ham shu sanada (bekor qilish + yangi yozuv, tarix saqlanadi).
        val active = services.queries.receipts(OpFilter(batchId = id))
        assertEquals(listOf(today.minusDays(10)), active.map { it.receivedOn })
        assertTrue(active.single().isInitial)
        val cancelled = services.queries.receipts(OpFilter(batchId = id, includeCancelled = true)).single { it.cancelled }
        assertEquals(Stock.RECEIVED_DATE_REASON, cancelled.cancelReason)
        assertEquals(cancelled.id, active.single().correctsId)
        // 5 kun oldingi berish endi «mavjud 0» bilan rad etilmaydi.
        assertEquals(litres("1"), services.stock.available(id, today.minusDays(5)))
        issueTo("Biologiya", id, "100", ML, date = today.minusDays(5))
        assertEquals(ml("900"), services.stock.remainder(id))
        assertEquals(today.minusDays(10), services.queries.ledger(id).first().movedOn)
    }

    @Test
    fun movingTheReceivedDateLaterIsRefusedWhenItWouldMakeEarlierStockNegative() {
        // Eski jadvaldan: 05.09 da 1000 ml kelgan, omborda 750 ml (250 ml moslash yozuvi o‘sha sanada).
        val file = tmp.root.toPath().resolve("reaktivlar-jadval.json")
        java.nio.file.Files.writeString(
            file,
            uz.reaktivombor.desktop.SheetStore.toJson(
                listOf(uz.reaktivombor.desktop.SheetRow(1, "Etil spirti", "", "1000", "ml", "01.09.2027", "750", "", "", createdAt = 10, legacyReceivedAt = "05.09.2026")),
                "2026-10-20T10:00:00",
            ),
        )
        services.legacy.importFile(file)
        val id = services.queries.batchRows(today = today).single().batchId
        val error = assertThrows(UserError::class.java) {
            services.reagents.updateBatch(id, BatchEdit(series = "", receivedOn = today.minusDays(40), expiresOn = java.time.LocalDate.of(2027, 9, 1)))
        }
        assertTrue(error.message, error.fields.containsKey("receivedOn"))
        assertEquals(java.time.LocalDate.of(2026, 9, 5), services.reagents.batch(id)!!.receivedOn)
        assertEquals(listOf(java.time.LocalDate.of(2026, 9, 5)), services.queries.receipts(OpFilter(batchId = id)).map { it.receivedOn })
        assertEquals(ml("750"), services.stock.remainder(id))
        // Oldinga (ertaroq sanaga) surish mumkin.
        services.reagents.updateBatch(id, BatchEdit(series = "", receivedOn = java.time.LocalDate.of(2026, 9, 1), expiresOn = java.time.LocalDate.of(2027, 9, 1)))
        assertEquals(listOf(java.time.LocalDate.of(2026, 9, 1)), services.queries.receipts(OpFilter(batchId = id)).map { it.receivedOn })
        assertEquals(ml("750"), services.stock.remainder(id))
        assertTrue(services.queries.ledger(id).all { it.balance >= 0 })
    }

    @Test
    fun minimumStockInAnotherDimensionIsRefusedWhenItCanNeverApply() {
        val error = assertThrows(UserError::class.java) {
            newBatch("Aseton", "A-1", today.plusDays(100), "100", unit = ML, minStock = "500", minStockUnit = G)
        }
        assertTrue(error.message, error.fields.containsKey("minStockText"))
        assertTrue(error.fields.getValue("minStockText"), error.fields.getValue("minStockText").contains("l yoki ml"))
        assertEquals(0L, count("batches"))
        // Reaktivning massa partiyasi bor — hajm partiyasini qo‘shishda grammdagi minimum unga qo‘llanadi.
        newBatch("Natriy xlorid", "N-1", today.plusDays(100), "1", unit = KG)
        newBatch("Natriy xlorid", "N-2", today.plusDays(100), "1", unit = L, minStock = "500", minStockUnit = G)
        val natriy = services.reagents.list().single { it.name == "Natriy xlorid" }
        assertEquals(G, natriy.minStockUnit)

        val aseton = services.reagents.get(newBatch("Aseton", "A-1", today.plusDays(100), "100", unit = ML).let { row(it).reagentId })!!
        val refused = assertThrows(UserError::class.java) {
            services.reagents.updateReagent(aseton.id, ReagentEdit(name = "Aseton", minStockText = "5", minStockUnit = KG))
        }
        assertTrue(refused.message, refused.fields.containsKey("minStockText"))
        services.reagents.updateReagent(aseton.id, ReagentEdit(name = "Aseton", minStockText = "50", minStockUnit = ML))
        assertEquals(ML, services.reagents.get(aseton.id)!!.minStockUnit)

        // Avvaldan mos kelmaydigan minimum saqlangan bo‘lsa ham reaktivning boshqa maydonlarini tahrirlash mumkin.
        db.write { it.update("UPDATE reagents SET min_stock = 5000, min_stock_unit = 'g' WHERE id = ?", aseton.id) }
        services.reagents.updateReagent(aseton.id, ReagentEdit(name = "Aseton", formula = "C3H6O", minStockText = "5", minStockUnit = G))
        assertEquals("C3H6O", services.reagents.get(aseton.id)!!.formula)
    }
}

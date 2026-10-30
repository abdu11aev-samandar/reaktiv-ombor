package uz.reaktivombor.desktop.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.Dimension
import uz.reaktivombor.desktop.core.DuplicateOperationError
import uz.reaktivombor.desktop.core.ExpiredBatchError
import uz.reaktivombor.desktop.core.InsufficientStockError
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.db.update
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StockServiceTest : ServiceTestBase() {

    @Test
    fun receiptAndIssueMath() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        services.stock.addReceipt(batch, ReceiptInput(today, "500", ML, docNo = "Yuk xati 15"))
        issueTo("Biologiya", batch, "300", ML)
        val row = row(batch)
        assertEquals(litres("1,5"), row.receivedQty)
        assertEquals(ml("300"), row.issuedQty)
        assertEquals(litres("1,2"), row.remainder)
        assertEquals(row.remainder, services.stock.remainder(batch))
        assertEquals(movementSum(batch), row.remainder)
        assertEquals(row.receivedQty - row.issuedQty - row.writtenOffQty, row.remainder)
        assertEquals(2, services.queries.receipts(OpFilter(batchId = batch)).size)
    }

    @Test
    fun oneReagentToFourDepartmentsAtOnceInOneTransaction() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        val before = services.stock.remainder(batch)
        val issueId = services.stock.issue(
            IssueInput(
                date = today,
                docNo = "T-12",
                issuedBy = "Aliyev A.",
                lines = listOf(
                    toDepartment(batch, "200", ML, "Gistologiya"),
                    toDepartment(batch, "300", ML, "Biologiya"),
                    toDepartment(batch, "150", ML, "Sud-kimyo"),
                    toDepartment(batch, "100", ML, "Morfologiya"),
                ),
            ),
        )
        assertEquals(litres("0,75"), before - services.stock.remainder(batch))
        assertEquals(ml("250"), services.stock.remainder(batch))
        assertEquals(1L, count("issues"))
        val items = services.queries.issueItems()
        assertEquals(4, items.size)
        assertTrue(items.all { it.issueId == issueId && it.docNo == "T-12" })
        assertEquals(
            mapOf("Gistologiya" to ml("200"), "Biologiya" to ml("300"), "Sud-kimyo" to ml("150"), "Morfologiya" to ml("100")),
            items.associate { it.recipientName to it.qty },
        )
        assertEquals(listOf("200", "300", "150", "100").sorted(), items.map { it.enteredQty }.sorted())
        assertEquals(ml("750"), row(batch).issuedQty)
        // Har bir qator — bitta harakat.
        assertEquals(5L, count("stock_movements"))
        assertEquals(4, services.queries.auditLogs(AuditFilter(action = AuditAction.ISSUE_DEPARTMENT)).size)
    }

    @Test
    fun issueToADistrictCreatesTheDistrictByName() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(200), "2")
        services.stock.issue(
            IssueInput(
                date = today,
                lines = listOf(
                    IssueLine(batch, "250", ML, RecipientType.DISTRICT, districtName = " chilonzor ", organization = "Tuman SEO", receiver = "Rahimov"),
                ),
            ),
        )
        val district = services.directory.districts().single()
        assertEquals("chilonzor", district.name)
        val item = services.queries.issueItems(OpFilter(districtId = district.id)).single()
        assertEquals(RecipientType.DISTRICT, item.recipientType)
        assertEquals("Tuman SEO", item.organization)
        assertEquals("Rahimov", item.receiver)
        assertEquals(ml("250"), item.qty)
        // Xuddi shu nom yana yozilsa — yangi tuman yaratilmaydi.
        services.stock.issue(
            IssueInput(date = today, lines = listOf(IssueLine(batch, "100", ML, RecipientType.DISTRICT, districtName = "CHILONZOR"))),
        )
        assertEquals(1, services.directory.districts().size)
        assertEquals(litres("1,65"), services.stock.remainder(batch))
    }

    @Test
    fun severalIssuesToOneDistrictOnDifferentDates() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(200), "5", received = today.minusDays(60))
        val district = services.directory.addDistrict("Yunusobod")
        val dates = listOf(today.minusDays(40), today.minusDays(20), today)
        dates.forEachIndexed { i, date ->
            services.stock.issue(
                IssueInput(date = date, docNo = "D-$i", lines = listOf(IssueLine(batch, "${i + 1}00", ML, RecipientType.DISTRICT, districtId = district.id))),
            )
        }
        val summary = services.queries.districtSummary(district.id)
        assertEquals(3, summary.items.size)
        val total = summary.districts.single()
        assertEquals(3, total.items)
        assertEquals(today.minusDays(40), total.firstDate)
        assertEquals(today, total.lastDate)
        assertEquals(mapOf(Dimension.VOLUME to ml("600")), total.net)
        val perReagent = summary.perReagent.single().total
        assertEquals(ml("600"), perReagent.net)
        assertEquals(3, perReagent.items)
        // Davr bo‘yicha.
        assertEquals(2, services.queries.districtSummary(district.id, from = today.minusDays(25)).items.size)
        assertEquals(litres("4,4"), services.stock.remainder(batch))
    }

    @Test
    fun insufficientStockBlocksTheWholeMultiLineOperation() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        val other = newBatch("Aseton", "A-1", today.plusDays(200), "1")
        val counts = listOf("issues", "issue_items", "stock_movements", "audit_logs", "districts").associateWith(::count)
        val error = assertThrows(InsufficientStockError::class.java) {
            services.stock.issue(
                IssueInput(
                    date = today,
                    lines = listOf(
                        toDepartment(other, "100", ML, "Biologiya"),
                        IssueLine(batch, "600", ML, RecipientType.DISTRICT, districtName = "Yangi tuman"),
                        toDepartment(batch, "500", ML, "Gistologiya"), // bir partiyadagi qatorlar qo‘shiladi: 1,1 l > 1 l
                    ),
                ),
            )
        }
        assertEquals(batch, error.batchId)
        assertEquals(litres("1"), error.available)
        assertEquals(litres("1,1"), error.requested)
        assertTrue(error.message, error.message.contains("Hech narsa saqlanmadi"))
        // Hech narsa saqlanmadi — tuman ham yaratilmadi, qoldiqlar o‘zgarmadi.
        assertEquals(counts, listOf("issues", "issue_items", "stock_movements", "audit_logs", "districts").associateWith(::count))
        assertEquals(litres("1"), services.stock.remainder(batch))
        assertEquals(litres("1"), services.stock.remainder(other))
    }

    @Test
    fun returnPutsStockBackIntoTheBatch() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        issueTo("Biologiya", batch, "400", ML)
        val item = services.queries.issueItems().single()
        services.stock.returnItem(item.id, ReturnInput(today, "150", ML, reason = "Ishlatilmadi"))
        assertEquals(ml("750"), services.stock.remainder(batch))
        val updated = services.queries.issueItems().single()
        assertEquals(ml("150"), updated.returnedQty)
        assertEquals(ml("250"), updated.netQty)
        assertEquals(ml("250"), row(batch).issuedQty) // berilgan − qaytarilgan
        assertEquals(1, services.queries.returns().size)
        assertEquals(movementSum(batch), services.stock.remainder(batch))
    }

    @Test
    fun returningMoreThanWasIssuedIsRefused() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        issueTo("Biologiya", batch, "400", ML)
        val item = services.queries.issueItems().single()
        val tooMuch = assertThrows(UserError::class.java) { services.stock.returnItem(item.id, ReturnInput(today, "0,5", L)) }
        assertTrue(tooMuch.message.contains("berilganidan ko‘p"))
        services.stock.returnItem(item.id, ReturnInput(today, "300", ML))
        // Jami qaytarilgan ham berilganidan oshmaydi.
        assertThrows(UserError::class.java) { services.stock.returnItem(item.id, ReturnInput(today, "101", ML)) }
        services.stock.returnItem(item.id, ReturnInput(today, "100", ML))
        assertEquals(litres("1"), services.stock.remainder(batch))
        // Qaytarish sanasi berilgan sanadan oldin bo‘lmaydi.
        val early = assertThrows(UserError::class.java) { services.stock.returnItem(item.id, ReturnInput(today.minusDays(1), "1", ML)) }
        assertTrue(early.message, early.fields.containsKey("date"))
    }

    @Test
    fun cancelAndCorrectKeepHistoryAndRecomputeTheRemainder() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        val receipt = services.stock.addReceipt(batch, ReceiptInput(today, "500", ML))
        issueTo("Biologiya", batch, "200", ML)
        val item = services.queries.issueItems().single()

        // Kirimni tuzatish: 500 ml emas, 400 ml edi.
        val corrected = services.stock.correctReceipt(receipt, ReceiptInput(today, "400", ML, docNo = "Yuk xati 16"), "Miqdor xato yozilgan")
        assertEquals(litres("1,2"), services.stock.remainder(batch))
        val receipts = services.queries.receipts(OpFilter(includeCancelled = true, batchId = batch))
        assertEquals(3, receipts.size) // boshlang‘ich, bekor qilingan, yangi
        assertTrue(receipts.single { it.id == receipt }.cancelled)
        assertEquals("Miqdor xato yozilgan", receipts.single { it.id == receipt }.cancelReason)
        assertEquals(receipt, receipts.single { it.id == corrected }.correctsId)
        assertEquals(2, services.queries.receipts(OpFilter(batchId = batch)).size) // faollari

        // Berishni boshqa bo‘limga tuzatish.
        val newItem = services.stock.correctIssueItem(item.id, toDepartment(batch, "250", ML, "Gistologiya"), today, "Bo‘lim adashgan")
        assertEquals(litres("1,15"), services.stock.remainder(batch))
        val items = services.queries.issueItems(OpFilter(includeCancelled = true))
        assertEquals(2, items.size)
        assertTrue(items.single { it.id == item.id }.cancelled)
        assertEquals(item.id, items.single { it.id == newItem }.correctsId)
        assertEquals("Gistologiya", items.single { it.id == newItem }.recipientName)

        // Bekor qilish.
        services.stock.cancelIssueItem(newItem, "Berilmadi")
        assertEquals(litres("1,4"), services.stock.remainder(batch))
        assertThrows(UserError::class.java) { services.stock.cancelIssueItem(newItem, "Yana") }
        assertThrows(UserError::class.java) { services.stock.cancelIssueItem(item.id, "  ") }

        // Hech narsa o‘chmagan: 5 ta asl harakat + 3 ta teskari.
        val ledger = services.queries.ledger(batch)
        assertEquals(8, ledger.size)
        assertEquals(3, ledger.count { it.kind == MovementKind.REVERSAL })
        assertEquals(services.stock.remainder(batch), ledger.last().balance)
        assertEquals(movementSum(batch), services.stock.remainder(batch))
        assertEquals(2, services.queries.auditLogs(AuditFilter(action = AuditAction.CORRECTED)).size)
        assertEquals(1, services.queries.auditLogs(AuditFilter(action = AuditAction.CANCELLED)).size)
    }

    @Test
    fun cancellingAReceiptThatWouldMakeStockNegativeIsRefused() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        val extra = services.stock.addReceipt(batch, ReceiptInput(today, "1", L))
        issueTo("Biologiya", batch, "1,5", L)
        val error = assertThrows(UserError::class.java) { services.stock.cancelReceipt(extra, "Xato") }
        assertTrue(error.message, error.message.contains("manfiy"))
        assertEquals(ml("500"), services.stock.remainder(batch))
        assertFalse(services.queries.receipts().single { it.id == extra }.cancelled)
        // Berish bekor qilingach — kirimni bekor qilish mumkin.
        services.stock.cancelIssueItem(services.queries.issueItems().single().id, "Xato")
        services.stock.cancelReceipt(extra, "Xato")
        assertEquals(litres("1"), services.stock.remainder(batch))
    }

    @Test
    fun cancellingAnIssueWithActiveReturnsIsRefused() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        issueTo("Biologiya", batch, "400", ML)
        val item = services.queries.issueItems().single()
        val ret = services.stock.returnItem(item.id, ReturnInput(today, "100", ML))
        assertThrows(UserError::class.java) { services.stock.cancelIssueItem(item.id, "Xato") }
        services.stock.cancelReturn(ret, "Xato")
        assertEquals(ml("600"), services.stock.remainder(batch))
        services.stock.cancelIssueItem(item.id, "Xato")
        assertEquals(litres("1"), services.stock.remainder(batch))
        assertEquals(1, services.queries.returns(OpFilter(includeCancelled = true)).size)
        assertTrue(services.queries.returns().isEmpty())
    }

    @Test
    fun fefoRecommendationAndOverrideFlag() {
        val late = newBatch("Etil spirti", "ES-LATE", today.plusDays(300), "1")
        val early = newBatch("Etil spirti", "ES-EARLY", today.plusDays(40), "1")
        val expired = newBatch("Etil spirti", "ES-OLD", today.minusDays(5), "1", received = today.minusDays(100))
        val reagent = services.reagents.list().single().id

        assertEquals(listOf(early, late, expired), services.stock.fefoOrder(reagent, Dimension.VOLUME, today).map { it.batchId })
        assertEquals(early, services.stock.fefoRecommendation(reagent, Dimension.VOLUME, today))
        assertEquals(
            listOf(early to litres("1"), late to ml("500")),
            services.stock.fefoSplit(reagent, Dimension.VOLUME, litres("1,5"), today),
        )
        // Muddati o‘tgani taqsimlanmaydi.
        assertThrows(UserError::class.java) { services.stock.fefoSplit(reagent, Dimension.VOLUME, litres("2,5"), today) }

        // Kechroq partiyadan berildi — ogohlantirish belgisi.
        issueTo("Biologiya", late, "100", ML)
        assertTrue(services.queries.issueItems().single().fefoOverride)
        // FEFO bo‘yicha — belgisiz.
        issueTo("Biologiya", early, "100", ML)
        assertFalse(services.queries.issueItems(OpFilter(batchId = early)).single().fefoOverride)
        // Bitta operatsiyada ertangi partiya to‘liq olinsa, keyingisidan olish FEFO buzilishi emas.
        services.stock.issue(
            IssueInput(today, lines = listOf(toDepartment(early, "900", ML, "Gistologiya"), toDepartment(late, "100", ML, "Gistologiya"))),
        )
        assertFalse(services.queries.issueItems(OpFilter(batchId = late, departmentId = deptId("Gistologiya"))).single().fefoOverride)
        // Bir operatsiyada ertangi partiyadan bir qismi olinib, qolgani bo‘la turib kechrog‘idan olinsa — belgi qo‘yiladi.
        val early2 = newBatch("Etil spirti", "ES-EARLY2", today.plusDays(50), "1")
        services.stock.issue(
            IssueInput(today, lines = listOf(toDepartment(early2, "500", ML, "Sud-kimyo"), toDepartment(late, "50", ML, "Sud-kimyo"))),
        )
        val sudKimyo = services.queries.issueItems(OpFilter(departmentId = deptId("Sud-kimyo")))
        assertFalse(sudKimyo.single { it.batchId == early2 }.fefoOverride)
        assertTrue(sudKimyo.single { it.batchId == late }.fefoOverride)
        assertEquals(early2, services.stock.fefoRecommendation(reagent, Dimension.VOLUME, today))
        services.stock.issue(IssueInput(today, lines = listOf(toDepartment(early2, "500", ML, "Morfologiya"))))
        assertEquals(late, services.stock.fefoRecommendation(reagent, Dimension.VOLUME, today))
        assertNull(services.stock.fefoRecommendation(reagent, Dimension.MASS, today))
    }

    @Test
    fun expiredBatchNeedsExplicitConfirmation() {
        val expired = newBatch("Aseton", "A-OLD", today.minusDays(1), "1", received = today.minusDays(100))
        val error = assertThrows(ExpiredBatchError::class.java) { issueTo("Biologiya", expired, "100", ML) }
        assertEquals(expired, error.batchId)
        assertTrue(error.message.contains("muddati o‘tgan"))
        assertEquals(0L, count("issue_items"))
        issueTo("Biologiya", expired, "100", ML, confirm = IssueConfirm(allowExpired = true))
        val item = services.queries.issueItems().single()
        assertTrue(item.expiredOverride)
        assertTrue(services.queries.auditLogs().first { it.action == AuditAction.ISSUE_DEPARTMENT }.details.contains("tasdiqlangan"))
        // Muddati o‘tganni hisobdan chiqarish tasdiq talab qilmaydi, lekin sabab majburiy.
        assertThrows(UserError::class.java) { services.stock.writeOff(expired, today, "100", ML, " ") }
        services.stock.writeOff(expired, today, "900", ML, "Muddati o‘tgan")
        assertEquals(0L, services.stock.remainder(expired))
        assertEquals(ml("900"), row(expired).writtenOffQty)
        assertEquals(ml("100"), row(expired).issuedQty)
        // O‘tgan sana bilan (muddati hali o‘tmagan kun) berish — tasdiqsiz.
        val soon = newBatch("Aseton", "A-2", today.minusDays(1), "1", received = today.minusDays(10))
        issueTo("Biologiya", soon, "100", ML, date = today.minusDays(2))
        assertFalse(services.queries.issueItems(OpFilter(batchId = soon)).single().expiredOverride)
    }

    @Test
    fun duplicateGuardWithinTwoMinutes() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(200), "2")
        issueTo("Biologiya", batch, "100", ML)
        assertThrows(DuplicateOperationError::class.java) { issueTo("Biologiya", batch, "100", ML) }
        assertEquals(1L, count("issue_items"))
        // Boshqa bo‘lim yoki boshqa miqdor — takror emas.
        issueTo("Gistologiya", batch, "100", ML)
        issueTo("Biologiya", batch, "101", ML)
        // Tasdiq bilan saqlanadi.
        issueTo("Biologiya", batch, "100", ML, confirm = IssueConfirm(allowDuplicate = true))
        assertEquals(4L, count("issue_items"))
        // 120 soniyadan keyin — takror hisoblanmaydi.
        clock.advanceSeconds(121)
        issueTo("Biologiya", batch, "100", ML)
        assertEquals(5L, count("issue_items"))

        services.stock.addReceipt(batch, ReceiptInput(today, "1", L))
        assertThrows(DuplicateOperationError::class.java) { services.stock.addReceipt(batch, ReceiptInput(today, "1000", ML)) }
        services.stock.addReceipt(batch, ReceiptInput(today, "1", L), confirmDuplicate = true)
        assertEquals(3, services.queries.receipts(OpFilter(batchId = batch)).size)
    }

    @Test
    fun issueDateRules() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(200), "1", received = today.minusDays(10))
        val future = assertThrows(UserError::class.java) { issueTo("Biologiya", batch, "100", ML, date = today.plusDays(1)) }
        assertTrue(future.message.contains("bugundan keyin"))
        val beforeArrival = assertThrows(UserError::class.java) { issueTo("Biologiya", batch, "100", ML, date = today.minusDays(11)) }
        assertTrue(beforeArrival.message.contains("partiya kelgan sanadan"))
        val missing = assertThrows(UserError::class.java) {
            services.stock.issue(IssueInput(date = null, lines = listOf(toDepartment(batch, "1", ML, "Biologiya"))))
        }
        assertEquals("Berish sanasi kiritilishi shart.", missing.message)
        assertThrows(UserError::class.java) { services.stock.issue(IssueInput(date = today, lines = emptyList())) }
        val noDepartment = assertThrows(UserError::class.java) {
            services.stock.issue(IssueInput(today, lines = listOf(IssueLine(batch, "1", ML, RecipientType.DEPARTMENT))))
        }
        assertTrue(noDepartment.message.contains("bo‘limga"))

        // O‘tgan sana bilan berish keyingi kunlardagi qoldiqni manfiy qilmasligi kerak: 5 kun oldin faqat 1 l bor edi.
        services.stock.addReceipt(batch, ReceiptInput(today.minusDays(2), "1", L))
        val backdated = assertThrows(InsufficientStockError::class.java) {
            issueTo("Biologiya", batch, "1,5", L, date = today.minusDays(5))
        }
        assertEquals(litres("1"), backdated.available)
        issueTo("Biologiya", batch, "1,5", L, date = today.minusDays(1))
        assertEquals(ml("500"), services.stock.remainder(batch))
        assertTrue(services.queries.ledger(batch).all { it.balance >= 0 })
    }

    @Test
    fun unitsConvertInsideADimensionAndDimensionsAreNeverSummed() {
        val litreBatch = newBatch("Natriy xlorid", "N-1", today.plusDays(200), "1")
        val kgBatch = newBatch("Natriy xlorid", "N-2", today.plusDays(200), "2", unit = KG)
        issueTo("Biologiya", litreBatch, "250", ML)
        issueTo("Biologiya", kgBatch, "500", G)
        assertEquals(ml("750"), services.stock.remainder(litreBatch))
        assertEquals(1_500_000L, services.stock.remainder(kgBatch)) // 1,5 kg mg da
        val wrong = assertThrows(UserError::class.java) { issueTo("Biologiya", litreBatch, "10", G) }
        assertTrue(wrong.message.contains("Birlik partiya birligiga mos emas"))
        assertThrows(UserError::class.java) { services.stock.addReceipt(kgBatch, ReceiptInput(today, "1", L)) }

        val rows = services.queries.batchRows(today = today)
        assertEquals(ml("750"), rows.single { it.batchId == litreBatch }.groupTotal)
        assertEquals(1_500_000L, rows.single { it.batchId == kgBatch }.groupTotal)
        val totals = services.queries.stockTotals(today = today)
        assertEquals(2, totals.size)
        assertEquals(setOf(Dimension.VOLUME, Dimension.MASS), totals.map { it.dimension }.toSet())
        val item = services.queries.issueItems(OpFilter(batchId = litreBatch)).single()
        assertEquals("250", item.enteredQty)
        assertEquals(ML, item.enteredUnit)
        assertEquals(ml("250"), item.qty)
    }

    @Test
    fun concurrentOperationsKeepStockExactAndNeverNegative() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "10")
        val threads = 8
        val perThread = 20
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val saved = AtomicInteger()
        val refused = AtomicInteger()
        val unexpected = Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(threads) { t ->
            pool.submit {
                start.await()
                repeat(perThread) {
                    try {
                        services.stock.issue(
                            IssueInput(today, lines = listOf(toDepartment(batch, "70", ML, if (t % 2 == 0) "Biologiya" else "Gistologiya"))),
                            IssueConfirm(allowDuplicate = true),
                        )
                        saved.incrementAndGet()
                    } catch (e: InsufficientStockError) {
                        refused.incrementAndGet()
                    } catch (e: Throwable) {
                        unexpected += e
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
        assertEquals(emptyList<Throwable>(), unexpected)
        // 10 l / 70 ml = 142 ta to‘liq berish, 60 ml qoladi.
        assertEquals(142, saved.get())
        assertEquals(threads * perThread - 142, refused.get())
        assertEquals(ml("60"), services.stock.remainder(batch))
        assertEquals(movementSum(batch), services.stock.remainder(batch))
        assertEquals(142L, count("issue_items"))
        assertEquals(litres("9,94"), services.queries.issueItems().sumOf { it.qty })
        assertTrue(services.queries.ledger(batch).all { it.balance >= 0 })
    }

    @Test
    fun invalidOperationInputsGiveUzbekMessages() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(200), "1")
        val noUnit = assertThrows(UserError::class.java) { services.stock.addReceipt(batch, ReceiptInput(today, "1", null)) }
        assertEquals("O‘lchov birligini tanlang.", noUnit.fields["unit"])
        val noQty = assertThrows(UserError::class.java) { services.stock.addReceipt(batch, ReceiptInput(today, "", L)) }
        assertEquals("Kirim miqdori kiritilishi shart.", noQty.fields["qtyText"])
        val noDate = assertThrows(UserError::class.java) { services.stock.addReceipt(batch, ReceiptInput(null, "1", L)) }
        assertEquals("Kirim sanasi kiritilishi shart.", noDate.fields["date"])
        val badQty = assertThrows(UserError::class.java) { issueTo("Biologiya", batch, "bir litr", L) }
        assertTrue(badQty.message.contains("son bo‘lishi kerak"))
        val multiLine = assertThrows(UserError::class.java) {
            services.stock.issue(IssueInput(today, lines = listOf(toDepartment(batch, "1", ML, "Biologiya"), toDepartment(batch, "-1", ML, "Biologiya"))))
        }
        assertEquals("2-qator: miqdor manfiy bo‘lishi mumkin emas.", multiLine.message)
        val unknownBatch = assertThrows(UserError::class.java) { issueTo("Biologiya", 999, "1", ML) }
        assertEquals("Partiya topilmadi.", unknownBatch.message)
        assertEquals(0L, count("issue_items"))
        assertEquals(litres("1"), services.stock.remainder(batch))
    }

    @Test
    fun returnsHaveTheSameDuplicateGuardAsReceiptsAndIssues() {
        val batch = newBatch("Etil spirti", "ES-001", today.plusDays(200), "1")
        issueTo("Biologiya", batch, "400", ML)
        val item = services.queries.issueItems().single()
        services.stock.returnItem(item.id, ReturnInput(today, "100", ML))
        // Ikki marta bosilgan: o‘sha qator, o‘sha miqdor (boshqa birlikda yozilgan bo‘lsa ham), o‘sha sana — 120 s ichida.
        assertThrows(DuplicateOperationError::class.java) { services.stock.returnItem(item.id, ReturnInput(today, "0,1", L)) }
        assertEquals(1, services.queries.returns().size)
        assertEquals(ml("700"), services.stock.remainder(batch))
        // Boshqa miqdor — takror emas; tasdiq bilan — saqlanadi.
        services.stock.returnItem(item.id, ReturnInput(today, "50", ML))
        services.stock.returnItem(item.id, ReturnInput(today, "100", ML), confirmDuplicate = true)
        assertEquals(ml("850"), services.stock.remainder(batch))
        // 120 soniyadan keyin — takror hisoblanmaydi.
        clock.advanceSeconds(121)
        services.stock.returnItem(item.id, ReturnInput(today, "50", ML))
        assertEquals(4, services.queries.returns().size)
        assertEquals(movementSum(batch), services.stock.remainder(batch))
    }

    @Test
    fun aReturnIntoAnArchivedBatchUnarchivesIt() {
        val a = newBatch("Etil spirti", "ES-A", today.plusDays(300), "100", unit = ML)
        val b = newBatch("Etil spirti", "ES-B", today.plusDays(200), "50", unit = ML)
        issueTo("Biologiya", a, "100", ML)
        services.reagents.archiveBatch(a)
        val reagent = services.reagents.list().single().id
        assertEquals(listOf(b), services.stock.fefoOrder(reagent, Dimension.VOLUME, today).map { it.batchId })

        val item = services.queries.issueItems().single()
        services.stock.returnItem(item.id, ReturnInput(today, "60", ML))
        // Qaytgan 60 ml yashirin qolmaydi: partiya arxivdan chiqdi, jami va FEFO da ko‘rinadi, undan berish mumkin.
        assertFalse(row(a).archived)
        assertEquals(ml("110"), row(b).groupTotal)
        assertEquals(listOf(b, a), services.stock.fefoOrder(reagent, Dimension.VOLUME, today).map { it.batchId })
        val audit = services.queries.auditLogs(AuditFilter(action = AuditAction.UNARCHIVED))
        assertEquals(1, audit.size)
        assertEquals(a, audit.single().batchId)
        assertTrue(audit.single().details, audit.single().details.contains(Stock.UNARCHIVE_REASON))
        issueTo("Gistologiya", a, "60", ML)
        assertEquals(0L, services.stock.remainder(a))
    }

    @Test
    fun cancellingOrCorrectingAnIssueOfAnArchivedBatchUnarchivesBatchAndReagent() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(300), "1")
        issueTo("Biologiya", batch, "1", L)
        val reagent = services.reagents.list().single().id
        services.reagents.archiveBatch(batch)
        services.reagents.archiveReagent(reagent)
        services.stock.cancelIssueItem(services.queries.issueItems().single().id, "Xato kiritilgan")
        assertFalse(row(batch).archived)
        assertFalse(row(batch).reagentArchived)
        assertEquals(litres("1"), row(batch).groupTotal)
        assertEquals(listOf("reagent", "batch"), services.queries.auditLogs(AuditFilter(action = AuditAction.UNARCHIVED)).map { it.entity }.reversed())

        // Tuzatish ham (eski qator bekor qilinadi — miqdor arxivdagi partiyaga qaytadi).
        issueTo("Gistologiya", batch, "1", L)
        services.reagents.archiveBatch(batch)
        val item = services.queries.issueItems(OpFilter(departmentId = deptId("Gistologiya"))).single()
        services.stock.correctIssueItem(item.id, toDepartment(batch, "400", ML, "Gistologiya"), today, "Miqdor xato")
        assertFalse(row(batch).archived)
        assertEquals(ml("600"), services.stock.remainder(batch))
        assertEquals(ml("600"), row(batch).groupTotal)
        assertEquals(3, services.queries.auditLogs(AuditFilter(action = AuditAction.UNARCHIVED)).size)
    }

    @Test
    fun fefoFlagUsesStockAsOfTheIssueDate() {
        val a = newBatch("Etil spirti", "ES-A", today.plusDays(400), "1", received = today.minusDays(60))
        val b = newBatch("Etil spirti", "ES-B", today.plusDays(100), "1", received = today.minusDays(5))
        // 20 kun oldin B hali kelmagan edi — A dan berish FEFO buzilishi emas.
        issueTo("Biologiya", a, "100", ML, date = today.minusDays(20))
        val backdated = services.queries.issueItems().single()
        assertFalse(backdated.fefoOverride)
        assertTrue(services.queries.auditLogs(AuditFilter(action = AuditAction.ISSUE_DEPARTMENT)).none { it.details.contains("FEFO") })
        // Bugun B bor — belgi qo‘yiladi.
        issueTo("Gistologiya", a, "100", ML)
        assertTrue(services.queries.issueItems(OpFilter(departmentId = deptId("Gistologiya"))).single().fefoOverride)
        assertEquals(litres("1"), services.stock.remainder(b))
    }

    @Test
    fun expiredConfirmationNamesEveryExpiredLineOfTheOperation() {
        val aseton = newBatch("Aseton", "A-OLD", today.minusDays(1), "1", received = today.minusDays(100))
        val xloroform = newBatch("Xloroform", "X-OLD", today.minusDays(5), "1", received = today.minusDays(100))
        val fresh = newBatch("Etanol", "E-1", today.plusDays(100), "1")
        val input = IssueInput(
            today,
            lines = listOf(
                toDepartment(fresh, "100", ML, "Biologiya"),
                toDepartment(aseton, "100", ML, "Biologiya"),
                toDepartment(xloroform, "100", ML, "Gistologiya"),
            ),
        )
        val error = assertThrows(ExpiredBatchError::class.java) { services.stock.issue(input) }
        // Foydalanuvchi ko‘radigan xabarda ikkala muddati o‘tgan partiya ham bor — bitta tasdiq aynan shularni qamraydi.
        assertTrue(error.message, error.message.contains("2-qator: «Aseton» (seriya A-OLD)"))
        assertTrue(error.message, error.message.contains("3-qator: «Xloroform» (seriya X-OLD)"))
        assertTrue(error.message, error.message.contains("Baribir berish uchun tasdiqlang"))
        assertEquals(aseton, error.batchId)
        assertEquals(listOf(aseton, xloroform), error.batchIds)
        assertEquals(0L, count("issue_items"))
        services.stock.issue(input, IssueConfirm(allowExpired = true))
        val items = services.queries.issueItems()
        assertEquals(setOf(aseton, xloroform), items.filter { it.expiredOverride }.map { it.batchId }.toSet())
    }

    @Test
    fun correctingTheInitialReceiptDateAlsoMovesTheBatchReceivedDate() {
        // Kelgan sana xato yozilgan: 20.10 o‘rniga 02.10 (bugun 29.10).
        val batch = newBatch("Aseton", "A-1", today.plusDays(300), "1", received = today.minusDays(9), produced = today.minusDays(40))
        val initial = services.queries.receipts().single().id
        // Ishlab chiqarilgandan oldin bo‘lolmaydi (partiyaning kelgan sanasi ham shu).
        val beforeProduced = assertThrows(UserError::class.java) {
            services.stock.correctReceipt(initial, ReceiptInput(today.minusDays(41), "1", L), "Sana xato")
        }
        assertTrue(beforeProduced.message, beforeProduced.fields.containsKey("date"))
        val corrected = services.stock.correctReceipt(initial, ReceiptInput(today.minusDays(27), "1", L), "Sana xato")
        assertEquals(today.minusDays(27), services.reagents.batch(batch)!!.receivedOn)
        assertTrue(services.queries.receipts().single { it.id == corrected }.isInitial)
        // Endi haqiqiy berish (10.10) qabul qilinadi.
        issueTo("Biologiya", batch, "100", ML, date = today.minusDays(19))
        assertEquals(ml("900"), services.stock.remainder(batch))
        assertTrue(services.queries.auditLogs(AuditFilter(action = AuditAction.BATCH_UPDATED)).any { it.details.contains("kelgan sana") })
        // Birinchi berishdan keyingi sanaga tuzatib bo‘lmaydi.
        val afterIssue = assertThrows(UserError::class.java) {
            services.stock.correctReceipt(corrected, ReceiptInput(today.minusDays(10), "1", L), "Sana xato")
        }
        assertTrue(afterIssue.message, afterIssue.fields.containsKey("date"))
        assertEquals(today.minusDays(27), services.reagents.batch(batch)!!.receivedOn)
        // Qo‘shimcha (boshlang‘ich bo‘lmagan) kirimni tuzatish partiya sanasini o‘zgartirmaydi.
        val extra = services.stock.addReceipt(batch, ReceiptInput(today.minusDays(3), "1", L))
        services.stock.correctReceipt(extra, ReceiptInput(today.minusDays(2), "1", L), "Sana xato")
        assertEquals(today.minusDays(27), services.reagents.batch(batch)!!.receivedOn)
    }

    @Test
    fun anExtraReceiptCannotBeDatedBeforeTheBatchArrived() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(300), "1", received = java.time.LocalDate.of(2026, 1, 1))
        // Yil xato yozilgan (2025): partiya hali kelmagan edi — rad etiladi, hech narsa saqlanmaydi.
        val error = assertThrows(UserError::class.java) {
            services.stock.addReceipt(batch, ReceiptInput(java.time.LocalDate.of(2025, 2, 1), "2", L))
        }
        assertEquals(
            "Kirim sanasi (01.02.2025) partiya kelgan sanadan (01.01.2026) oldin bo‘lishi mumkin emas.",
            error.fields.getValue("date"),
        )
        assertEquals(1, services.queries.receipts().size)
        assertEquals(0L, services.stock.available(batch, java.time.LocalDate.of(2025, 3, 1)))
        // Kelgan kuni va undan keyin — mumkin.
        val sameDay = services.stock.addReceipt(batch, ReceiptInput(java.time.LocalDate.of(2026, 1, 1), "500", ML))
        // Tuzatishda ham o‘sha qoida (qo‘shimcha kirim kelgan sanadan oldinga surilmaydi).
        val correction = assertThrows(UserError::class.java) {
            services.stock.correctReceipt(sameDay, ReceiptInput(java.time.LocalDate.of(2025, 12, 31), "500", ML), "Sana xato")
        }
        assertTrue(correction.fields.containsKey("date"))
        assertEquals(litres("1,5"), services.stock.remainder(batch))

        // Eski ma’lumot: kelgan sana yo‘q — chegara ishlab chiqarilgan sana.
        val old = newBatch("Fenol", "F-1", today.plusDays(300), "1", received = today.minusDays(10), produced = today.minusDays(100))
        db.write { it.update("UPDATE batches SET received_on = NULL WHERE id = ?", old) }
        val beforeProduced = assertThrows(UserError::class.java) {
            services.stock.addReceipt(old, ReceiptInput(today.minusDays(101), "1", L))
        }
        assertTrue(beforeProduced.fields.getValue("date"), beforeProduced.fields.getValue("date").contains("ishlab chiqarilgan sanadan"))
        services.stock.addReceipt(old, ReceiptInput(today.minusDays(100), "1", L))
    }

    @Test
    fun anInactiveDistrictTypedByNameIsRefusedAndStaysInactive() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(300), "2")
        val yashnobod = services.directory.addDistrict("Yashnobod")
        services.stock.issue(
            IssueInput(today, lines = listOf(IssueLine(batch, "100", ML, RecipientType.DISTRICT, districtName = "Yashnobod"))),
        )
        services.directory.setActive(PlaceKind.DISTRICT, yashnobod.id, false)
        val error = assertThrows(UserError::class.java) {
            services.stock.issue(
                IssueInput(today, lines = listOf(IssueLine(batch, "200", ML, RecipientType.DISTRICT, districtName = "yashnobod"))),
            )
        }
        assertTrue(error.message, error.message.contains("«Yashnobod» tumani nofaol"))
        assertEquals(setOf("districtName"), error.fields.keys)
        assertFalse(services.directory.get(PlaceKind.DISTRICT, yashnobod.id)!!.active)
        assertEquals(1, services.queries.issueItems().size)
        // Tuzatishda ham — nofaol tumanga yangi qator yozilmaydi (eski qator bekor qilinmay qoladi).
        val item = services.queries.issueItems().single()
        val correction = assertThrows(UserError::class.java) {
            services.stock.correctIssueItem(
                item.id, IssueLine(batch, "150", ML, RecipientType.DISTRICT, districtName = "Yashnobod"), today, "Miqdor xato",
            )
        }
        assertEquals(setOf("districtName"), correction.fields.keys)
        assertFalse(services.queries.issueItems().single().cancelled)
        assertFalse(services.directory.get(PlaceKind.DISTRICT, yashnobod.id)!!.active)
        // Yangi nom — yangi tuman (faol) yaratiladi; faollashtirilgandan keyin — eski tumanga ham beriladi.
        services.stock.issue(
            IssueInput(today, lines = listOf(IssueLine(batch, "50", ML, RecipientType.DISTRICT, districtName = "Bektemir"))),
        )
        assertTrue(services.directory.districts().any { it.name == "Bektemir" })
        services.directory.setActive(PlaceKind.DISTRICT, yashnobod.id, true)
        services.stock.issue(
            IssueInput(today, lines = listOf(IssueLine(batch, "200", ML, RecipientType.DISTRICT, districtName = "yashnobod"))),
        )
        assertEquals(ml("1650"), services.stock.remainder(batch))
    }
}

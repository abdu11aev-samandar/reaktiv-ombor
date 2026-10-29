package uz.reaktivombor.desktop.report

import uz.reaktivombor.desktop.service.IssueInput
import uz.reaktivombor.desktop.service.IssueLine
import uz.reaktivombor.desktop.service.ReceiptInput
import uz.reaktivombor.desktop.service.RecipientType
import uz.reaktivombor.desktop.service.ReturnInput
import uz.reaktivombor.desktop.service.ServiceTestBase

/** Hisobot testlari uchun namunaviy ma’lumotlar («bugun» = 29.10.2026). */
abstract class ReportTestBase : ServiceTestBase() {

    protected class Sample(
        val es1: Long,
        val es2: Long,
        val es3: Long,
        val aseton: Long,
        val xloroform: Long,
        val natriy: Long,
        val cancelledReceipt: Long,
    )

    /**
     * Etil spirti: ES-001 5 l (20 kun), ES-002 10 l (60 kun), ES-003 4 l (200 kun) — 19 l kelgan, Biologiyaga 2 l,
     * Gistologiyaga 3 l → qoldiq 14 l. Aseton — minimal qoldiqdan kam; Xloroform — muddati o‘tgan (100 ml hisobdan
     * chiqarilgan); Natriy xlorid (kg) — Chilonzor va Yunusobodga berilgan, 50 g qaytarilgan.
     */
    protected fun seed(): Sample {
        val es1 = newBatch("Etil spirti", "ES-001", today.plusDays(20), "5", received = today.minusDays(40))
        val es2 = newBatch("Etil spirti", "ES-002", today.plusDays(60), "10", received = today.minusDays(40))
        val es3 = newBatch("Etil spirti", "ES-003", today.plusDays(200), "4", received = today.minusDays(40))
        issueTo("Biologiya", es1, "2", L, date = today.minusDays(20))
        issueTo("Gistologiya", es2, "3", L, date = today.minusDays(10))
        val aseton = newBatch("Aseton", "A-1", today.plusDays(10), "2", minStock = "3", minStockUnit = L, departmentId = deptId("Sud-kimyo"))
        val xloroform = newBatch("Xloroform", "X-1", today.minusDays(2), "500", unit = ML, received = today.minusDays(200))
        services.stock.writeOff(xloroform, today, "100", ML, "Muddati o‘tgan")
        val natriy = newBatch(
            "Natriy xlorid", "N-1", today.plusDays(300), "1", unit = KG, received = today.minusDays(30), departmentId = deptId("Sud-kimyo"),
        )
        services.stock.issue(
            IssueInput(
                today.minusDays(5), docNo = "T-15",
                lines = listOf(IssueLine(natriy, "200", G, RecipientType.DISTRICT, districtName = "Chilonzor", organization = "Chilonzor SEB")),
            ),
        )
        services.stock.issue(
            IssueInput(today, lines = listOf(IssueLine(natriy, "100", G, RecipientType.DISTRICT, districtName = "Yunusobod"))),
        )
        val item = services.queries.issueItems().single { it.districtName == "Chilonzor" }
        services.stock.returnItem(item.id, ReturnInput(today, "50", G, reason = "Ortiqcha"))
        // Xato kiritilgan qo‘shimcha kirim — bekor qilingan (tarixda qoladi, qoldiqqa ta’sir qilmaydi).
        val receipt = services.stock.addReceipt(es3, ReceiptInput(today.minusDays(3), "1", L, docNo = "YX-9"))
        services.stock.cancelReceipt(receipt, "Xato kiritildi")
        return Sample(es1, es2, es3, aseton, xloroform, natriy, receipt)
    }
}

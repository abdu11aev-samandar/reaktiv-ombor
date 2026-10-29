package uz.reaktivombor.desktop.service

import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.core.Xlsx
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class ExcelImportTest : ServiceTestBase() {

    private val import get() = services.excelImport

    private fun file(name: String): Path = tmp.root.toPath().resolve(name)

    /** Fayl yozadi: [headers] — 1-qatordagi sarlavhalar, [rows] — qiymatlar (LocalDate — Excel sanasi, Number — son). */
    private fun write(name: String, headers: List<String>, rows: List<List<Any?>>, headerRow: Int = 0): Path {
        val path = file(name)
        Files.newOutputStream(path).use { out ->
            Xlsx.write(out) { wb ->
                val ws = wb.newWorksheet("Ma’lumot")
                if (headerRow > 0) ws.value(0, 0, "Laboratoriya reaktivlari ro‘yxati")
                headers.forEachIndexed { c, h -> ws.value(headerRow, c, h) }
                rows.forEachIndexed { r, values ->
                    values.forEachIndexed { c, v ->
                        when (v) {
                            null -> Unit
                            is LocalDate -> ws.value(headerRow + 1 + r, c, v)
                            is Number -> ws.value(headerRow + 1 + r, c, v)
                            else -> ws.value(headerRow + 1 + r, c, v.toString())
                        }
                    }
                }
            }
        }
        return path
    }

    private val headers = listOf(
        "reaktiv NOMI", "Seriya raqami", "Kelgan sana", "Yaroqlilik muddati", "Kelgan miqdor", "O'lchov birligi", "bo`lim", "Izoh",
        "Ishlab chiqarilgan sana", "Mas'ul xodim",
    )

    private fun row(
        name: String?,
        series: Any?,
        received: Any?,
        expires: Any?,
        qty: Any?,
        unit: String?,
        department: String? = null,
        note: String? = null,
        produced: Any? = null,
    ): List<Any?> = listOf(name, series, received, expires, qty, unit, department, note, produced, "Karimova D.")

    @Test
    fun templateHasTheExactHeadersAnExampleRowAndAGuideSheet() {
        val template = import.template(file("shablon/shablon.xlsx"))
        ReadableWorkbook(template.toFile()).use { wb ->
            val sheets = wb.sheets.toList()
            assertEquals(listOf(ExcelImport.SHEET, ExcelImport.GUIDE_SHEET), sheets.map { it.name })
            val rows = sheets[0].read()
            assertEquals(ExcelImport.HEADERS, (0 until rows[0].cellCount).map { rows[0].getCellText(it) })
            assertEquals(
                listOf(
                    "Reaktiv nomi", "Kimyoviy formula", "Ishlab chiqaruvchi", "Seriya raqami", "Ishlab chiqarilgan sana", "Kelgan sana",
                    "Yaroqlilik muddati", "Kelgan miqdor", "O‘lchov birligi", "Bo‘lim", "Saqlash joyi", "Mas’ul xodim",
                    "Qabul qilgan xodim", "Minimal qoldiq", "Minimal qoldiq birligi", "Izoh",
                ),
                ExcelImport.HEADERS,
            )
            assertEquals("Etil spirti", rows[1].getCellText(0))
            assertEquals(ExcelImport.EXAMPLE_SERIES, rows[1].getCellText(3))
            assertEquals(today.minusDays(7).atStartOfDay(), rows[1].getCell(5).asDate()) // haqiqiy sana katagi
            val guide = sheets[1].read().map { r -> (0 until r.cellCount).map { r.getCellText(it) } }
            ExcelImport.HEADERS.forEach { header -> assertTrue(header, guide.any { it.firstOrNull() == header }) }
            assertTrue(guide.any { it.firstOrNull() == "Seriya raqami" && it.getOrNull(1) == "Ha" })
            assertTrue(guide.any { it.firstOrNull() == "Izoh" && it.getOrNull(1) == "Yo‘q" })
        }
        // O‘zgartirilmagan shablon: namuna qator import qilinmaydi.
        val preview = import.preview(template)
        assertEquals(1, preview.size)
        assertEquals(2, preview.single().rowNumber)
        assertNull(preview.single().input)
        assertTrue(preview.single().errors.single().contains("namuna"))
        assertThrows(UserError::class.java) { import.import(preview) }
    }

    @Test
    fun previewReportsErrorsPerRowAndWritesNothing() {
        val path = write(
            "xatolar.xlsx", headers,
            listOf(
                row("Etil spirti", "ES-1", today.minusDays(5), today.plusDays(300), 2.5, "litr", "biologiya"), // 2: to‘g‘ri
                row("Aseton", null, "05.10.2026", "05.10.2027", "1 250,5", "ml"), // 3: seriya yo‘q
                row("Xloroform", "X-1", "31.02.2026", today.plusDays(100), 1, "l"), // 4: sana emas
                row("Ammiak", "AM-1", today.minusDays(5), today.plusDays(100), 1, "xyz"), // 5: birlik noma’lum
                row("Benzol", "B-1", today.minusDays(5), today.minusDays(400), 1, "l", produced = today.minusDays(300)), // 6: muddat < ishlab chiqarilgan
                row("Fenol", "F-1", today.minusDays(5), today.plusDays(100), "abc", "kg"), // 7: miqdor son emas
                row("Glitserin", "G-1", today.plusDays(3), today.plusDays(100), 1, "l"), // 8: kelgan sana kelajakda
                emptyList(), // 9: bo‘sh qator — e’tiborga olinmaydi
                row("Yod", "Y-1", today.minusDays(1), today.plusDays(200), "0,5", "kg", "Toksikologiya"), // 10: yangi bo‘lim
            ),
        )
        val preview = import.preview(path)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 10), preview.map { it.rowNumber })
        val byRow = preview.associateBy { it.rowNumber }
        assertTrue(byRow.getValue(2).errors.toString(), byRow.getValue(2).valid)
        assertEquals(deptId("Biologiya"), byRow.getValue(2).input!!.departmentId)
        assertEquals("2.5", byRow.getValue(2).input!!.initialQtyText)
        assertEquals(listOf("Seriya raqami kiritilishi shart."), byRow.getValue(3).errors)
        assertEquals(listOf("Kelgan sana: «31.02.2026» — sana emas (kun.oy.yil, masalan 05.10.2026)."), byRow.getValue(4).errors)
        assertEquals(listOf("O‘lchov birligi: «xyz» noma’lum (l, ml, kg, g yoki dona)."), byRow.getValue(5).errors)
        assertEquals(listOf("Yaroqlilik muddati ishlab chiqarilgan sanadan oldin bo‘lishi mumkin emas."), byRow.getValue(6).errors)
        assertTrue(byRow.getValue(7).errors.toString(), byRow.getValue(7).errors.single().contains("son bo‘lishi kerak"))
        assertTrue(byRow.getValue(8).errors.toString(), byRow.getValue(8).errors.single().contains("bugundan keyin"))
        val yod = byRow.getValue(10)
        assertTrue(yod.errors.toString(), yod.valid)
        assertEquals("Toksikologiya", yod.departmentName)
        assertNull(yod.input!!.departmentId)
        assertEquals(listOf("Yangi bo‘lim qo‘shiladi: «Toksikologiya»."), yod.warnings)
        assertEquals("Karimova D.", yod.input!!.responsible)
        // Ko‘rib chiqish bazaga hech narsa yozmaydi.
        assertEquals(0L, count("batches"))
        assertEquals(0L, count("reagents"))
        assertEquals(0L, count("audit_logs"))
        assertFalse(services.directory.departments().any { it.name == "Toksikologiya" })
    }

    @Test
    fun importIsAllOrNothing() {
        val path = write(
            "import.xlsx", headers,
            listOf(
                row("Aseton", "A-1", "20.10.2026", "20.10.2027", "1 250,5", "ml", "Toksikologiya"),
                row("Etil spirti", "ES-1", today.minusDays(5), today.plusDays(300), 5, "l", "Biologiya"),
                row("Fenol", null, today.minusDays(5), today.plusDays(100), 1, "kg"), // xato — o‘tkazib yuboriladi
            ),
        )
        val preview = import.preview(path)
        assertEquals(listOf(true, true, false), preview.map { it.valid })

        // Ko‘rib chiqishdan keyin bazada 3-qatordagi partiya paydo bo‘ldi: 2-qator (va yangi bo‘lim) ham saqlanmaydi.
        newBatch("Etil spirti", "ES-1", today.plusDays(300), "1", received = today.minusDays(5))
        val batches = count("batches")
        val error = assertThrows(UserError::class.java) { import.import(preview, path) }
        assertTrue(error.message, error.message.startsWith("3-qator: "))
        assertTrue(error.message, error.message.contains("Hech narsa import qilinmadi"))
        assertEquals(batches, count("batches"))
        assertFalse(services.directory.departments().any { it.name == "Toksikologiya" })
        assertTrue(services.queries.auditLogs(AuditFilter(action = AuditAction.EXCEL_IMPORTED)).isEmpty())

        // Yangidan tekshirilsa — o‘sha qator xato deb ko‘rsatiladi, qolgani import qilinadi.
        val again = import.preview(path)
        assertTrue(again[1].errors.single().contains("bazada allaqachon bor"))
        val result = import.import(again, path)
        assertEquals(ImportResult(created = 1, skipped = 2), result)
        val aseton = services.queries.batchRows(BatchFilter(text = "Aseton"), today).single()
        assertEquals(1_250_500L, aseton.remainder) // 1 250,5 ml µl da
        assertEquals("Toksikologiya", aseton.departmentName)
        assertEquals(LocalDate.of(2026, 10, 20), aseton.receivedOn)
        assertEquals("Karimova D.", aseton.responsible)
        val audit = services.queries.auditLogs(AuditFilter(action = AuditAction.EXCEL_IMPORTED)).single()
        assertTrue(audit.details, audit.details.contains("import.xlsx") && audit.details.contains("qo‘shildi: 1 ta"))
    }

    @Test
    fun duplicatesAreReportedPerRow() {
        newBatch("Aseton", "A-1", today.plusDays(100), "1")
        val path = write(
            "takror.xlsx", headers,
            listOf(
                row("Etil spirti", "ES-1", today.minusDays(5), today.plusDays(300), 5, "l"), // 2
                row("etil  SPIRTI", "es-1", today.minusDays(2), today.plusDays(300), 1, "litr"), // 3: 2-qator bilan bir xil partiya
                row("Aseton", "A-1", today.minusDays(5), today.plusDays(100), 2, "l"), // 4: bazada bor
                row("Etil spirti", "ES-1", today.minusDays(5), today.plusDays(400), 1, "l"), // 5: boshqa muddat — ogohlantirish
                row("Aseton", "A-1", today.minusDays(5), today.plusDays(150), 1, "l"), // 6: bazadagi bilan seriya bir xil — ogohlantirish
            ),
        )
        val preview = import.preview(path).associateBy { it.rowNumber }
        assertTrue(preview.getValue(2).valid)
        assertEquals(listOf("Fayldagi 2-qator bilan bir xil partiya (nom, seriya, muddat va birlik bir xil)."), preview.getValue(3).errors)
        assertTrue(preview.getValue(4).errors.single().contains("bazada allaqachon bor"))
        assertTrue(preview.getValue(5).valid)
        assertTrue(preview.getValue(5).warnings.single().contains("2-qatordagi partiya bilan seriyasi bir xil"))
        assertTrue(preview.getValue(6).valid)
        assertTrue(preview.getValue(6).warnings.single().contains("bazada boshqa muddat"))
        assertEquals(1L, count("batches"))

        val result = import.import(preview.values.sortedBy { it.rowNumber })
        assertEquals(ImportResult(created = 3, skipped = 2), result)
        assertEquals(4L, count("batches"))
        assertEquals(2, services.reagents.list().size)
    }

    @Test
    fun headersAreFoundBelowATitleAndMissingColumnsAreNamed() {
        // Sarlavha 2-qatorda (1-qatorda jadval nomi), ustunlar boshqa tartibda va tutuq belgisining boshqa ko‘rinishida.
        val shifted = write(
            "siljigan.xlsx",
            listOf("Izoh", "O`lchov birligi", "Kelgan miqdor", "YAROQLILIK MUDDATI", "Kelgan sana", "Seriya", "Reaktiv nomi"),
            listOf(listOf("—", "dona", 12, today.plusDays(50), today.minusDays(1), "P-1", "Petri kosachasi")),
            headerRow = 1,
        )
        val preview = import.preview(shifted).single()
        assertEquals(3, preview.rowNumber)
        assertTrue(preview.errors.toString(), preview.valid)
        assertEquals("12", preview.input!!.initialQtyText)
        assertEquals("Petri kosachasi", preview.input!!.name)

        val missing = write("yetishmaydi.xlsx", listOf("Reaktiv nomi", "Kelgan sana", "Kelgan miqdor"), listOf(listOf("A", today, 1)))
        val error = assertThrows(UserError::class.java) { import.preview(missing) }
        assertTrue(error.message, error.message.contains("«Seriya raqami»") && error.message.contains("«O‘lchov birligi»"))
        assertTrue(error.message, error.message.contains("«Yaroqlilik muddati»"))

        val noHeader = write("sarlavhasiz.xlsx", listOf("A", "B"), listOf(listOf("x", "y")))
        assertTrue(assertThrows(UserError::class.java) { import.preview(noHeader) }.message.contains("«Reaktiv nomi»"))

        val notExcel = file("matn.xlsx").also { Files.writeString(it, "bu Excel emas") }
        assertTrue(assertThrows(UserError::class.java) { import.preview(notExcel) }.message.contains(".xlsx"))
        assertThrows(UserError::class.java) { import.preview(file("yoq.xlsx")) }
    }

    @Test
    fun excelNumbersAreReadExactly() {
        val path = write(
            "sonlar.xlsx", headers,
            listOf(
                row("Etil spirti", 12345, today.minusDays(5), today.plusDays(300), 0.1 + 0.2, "l"), // 0,30000000000000004 → 0,3
                row("Aseton", "A-2", today.minusDays(5), today.plusDays(300), BigDecimal("1.2345"), "l"), // 4 xona — xato
            ),
        )
        val preview = import.preview(path)
        assertEquals("12345", preview[0].input!!.series)
        assertEquals("0.3", preview[0].input!!.initialQtyText)
        assertTrue(preview[0].valid)
        assertTrue(preview[1].errors.single().contains("3 ta raqam"))
    }
}

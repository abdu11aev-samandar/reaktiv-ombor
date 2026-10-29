package uz.reaktivombor.desktop.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.reaktivombor.desktop.core.UserError

class DirectoryServiceTest : ServiceTestBase() {

    @Test
    fun seededDepartmentsInSpecOrder() {
        assertEquals(listOf("Gistologiya", "Biologiya", "Sud-kimyo", "Morfologiya"), services.directory.departments().map { it.name })
        assertTrue(services.directory.districts().isEmpty())
    }

    @Test
    fun addDepartmentDedupesByKey() {
        val existing = services.directory.addDepartment("  biologiya ")
        assertEquals(deptId("Biologiya"), existing.id)
        assertEquals("Biologiya", existing.name)
        val kimyo = services.directory.addDepartment("Kimyo  laboratoriyasi")
        assertEquals("Kimyo laboratoriyasi", kimyo.name)
        assertEquals(kimyo.id, services.directory.addDepartment("KIMYO LABORATORIYASI").id)
        assertEquals(5, services.directory.departments().size)
        assertEquals("Kimyo laboratoriyasi", services.directory.departments().last().name) // oxiriga qo‘shiladi
    }

    @Test
    fun addDistrictDedupesByKeyAndReactivates() {
        val first = services.directory.addDistrict("Mirzo Ulug‘bek")
        assertEquals(first.id, services.directory.addDistrict("mirzo ulug'bek").id) // tutuq belgisi farqsiz
        services.directory.setActive(PlaceKind.DISTRICT, first.id, false)
        assertTrue(services.directory.districts().isEmpty())
        assertEquals(1, services.directory.districts(includeInactive = true).size)
        val again = services.directory.addDistrict("MIRZO ULUG‘BEK")
        assertEquals(first.id, again.id)
        assertTrue(again.active)
        assertEquals(1, services.directory.districts().size)
        assertEquals(first.id, services.directory.resolveDistrict("Mirzo Ulug‘bek").id)
        services.directory.resolveDistrict("Chilonzor")
        assertEquals(listOf("Mirzo Ulug‘bek", "Chilonzor"), services.directory.districts().map { it.name })
    }

    @Test
    fun renameToAnotherExistingNameIsRefused() {
        val bio = deptId("Biologiya")
        val error = assertThrows(UserError::class.java) { services.directory.rename(PlaceKind.DEPARTMENT, bio, "gistologiya") }
        assertEquals("«Gistologiya» nomli bo‘lim allaqachon bor.", error.message)
        // O‘z nomining yozilishini o‘zgartirish mumkin.
        assertEquals("BIOLOGIYA", services.directory.rename(PlaceKind.DEPARTMENT, bio, "BIOLOGIYA").name)
        assertEquals("Biologiya bo‘limi", services.directory.rename(PlaceKind.DEPARTMENT, bio, "Biologiya bo‘limi").name)
        assertTrue(services.queries.auditLogs(AuditFilter(action = AuditAction.DEPARTMENT_CHANGED)).isNotEmpty())
    }

    @Test
    fun deactivatedDepartmentIsHiddenAndCannotReceiveNewIssues() {
        val batch = newBatch("Aseton", "A-1", today.plusDays(100), "1")
        issueTo("Morfologiya", batch, "100", ML)
        val morf = deptId("Morfologiya")
        services.directory.setActive(PlaceKind.DEPARTMENT, morf, false)
        assertFalse(services.directory.departments().any { it.id == morf })
        assertFalse(services.directory.get(PlaceKind.DEPARTMENT, morf)!!.active)
        val error = assertThrows(UserError::class.java) { issueTo("Morfologiya", batch, "200", ML) }
        assertTrue(error.message.contains("nofaol"))
        // Eski operatsiyalar joyida.
        assertEquals("Morfologiya", services.queries.issueItems().single().recipientName)
        services.directory.setActive(PlaceKind.DEPARTMENT, morf, true)
        issueTo("Morfologiya", batch, "200", ML)
    }

    @Test
    fun invalidPlaceNamesAreRefused() {
        assertEquals("Bo‘lim nomi kiritilishi shart.", assertThrows(UserError::class.java) { services.directory.addDepartment("  ") }.message)
        assertEquals(
            "Tuman nomi harf bilan yozilishi kerak (60 belgigacha).",
            assertThrows(UserError::class.java) { services.directory.addDistrict("12345") }.message,
        )
        assertThrows(UserError::class.java) { services.directory.addDistrict("A".repeat(61)) }
        assertEquals("A".repeat(60), services.directory.addDistrict("A".repeat(60)).name)
    }
}

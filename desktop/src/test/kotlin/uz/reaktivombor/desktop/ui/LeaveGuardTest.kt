package uz.reaktivombor.desktop.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.service.Services

/** AppState: saqlanmagan o‘zgarish bo‘lsa sahifadan chiqish tasdiq kutadi (UI’siz). */
class LeaveGuardTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var state: AppState

    @Before
    fun setUp() {
        services = DemoData.open(tmp.newFolder("data").toPath())
        state = AppState(services, FakeFileDialogs(), Page.ADD_REAGENT)
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    @Test
    fun withoutAGuardNavigationIsImmediate() {
        state.navigate(Page.REAGENTS)
        assertEquals(Page.REAGENTS, state.page)
        assertNull(state.pendingNavigation)
    }

    @Test
    fun dirtyFormWaitsForConfirmation() {
        var dirty = true
        val guard = { dirty }
        state.setLeaveGuard(guard)
        val serial = state.navigation.serial

        state.navigate(Page.REAGENTS, PageIntent.EditBatch(7))
        assertEquals(Page.ADD_REAGENT, state.page)
        assertEquals(serial, state.navigation.serial)
        assertEquals(Page.REAGENTS, state.pendingNavigation?.page)

        // «Sahifada qolish»
        state.cancelLeave()
        assertNull(state.pendingNavigation)
        assertEquals(Page.ADD_REAGENT, state.page)

        // «Saqlamasdan chiqish»
        state.navigate(Page.REAGENTS, PageIntent.EditBatch(7))
        state.confirmLeave()
        assertEquals(Page.REAGENTS, state.page)
        assertEquals(PageIntent.EditBatch(7), state.intent)
        assertTrue(state.navigation.serial > serial)
        assertNull(state.pendingNavigation)
        // Eski sahifaning tekshiruvi endi ishlamaydi.
        assertFalse(state.hasUnsavedChanges())

        // Toza shakl — so‘ralmaydi.
        dirty = false
        state.setLeaveGuard(guard)
        state.navigate(Page.STOCK)
        assertEquals(Page.STOCK, state.page)
    }

    @Test
    fun forcedNavigationSkipsTheQuestionAndStaleGuardsAreNotRemovedByOthers() {
        val first = { true }
        val second = { true }
        state.setLeaveGuard(first)
        state.navigate(Page.RECEIPTS, force = true)
        assertEquals(Page.RECEIPTS, state.page)
        // Yangi sahifa o‘zinikini qo‘ydi; eskisining onDispose i uni olib tashlamaydi.
        state.setLeaveGuard(second)
        state.clearLeaveGuard(first)
        assertTrue(state.hasUnsavedChanges())
        state.clearLeaveGuard(second)
        assertFalse(state.hasUnsavedChanges())
    }

    @Test
    fun aFailingGuardNeverBlocksNavigation() {
        state.setLeaveGuard { error("buzilgan") }
        state.navigate(Page.HISTORY)
        assertEquals(Page.HISTORY, state.page)
    }
}

package uz.reaktivombor.desktop.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.reaktivombor.desktop.core.DuplicateBatchError
import uz.reaktivombor.desktop.core.ExpiryState
import uz.reaktivombor.desktop.core.MeasureUnit
import uz.reaktivombor.desktop.core.UserError
import uz.reaktivombor.desktop.service.BatchFilter
import uz.reaktivombor.desktop.service.NewBatch
import uz.reaktivombor.desktop.service.Services
import uz.reaktivombor.desktop.ui.theme.Severity

/** AppState shartnomasi (UI’siz): navigatsiya, yangilanish, xabarlar, run() xatolari va jadval ko‘rinishi. */
class AppStateTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var services: Services
    private lateinit var ids: DemoData.Ids
    private lateinit var state: AppState

    @Before
    fun setUp() {
        val (s, i) = DemoData.seeded(tmp.newFolder("data").toPath())
        services = s
        ids = i
        state = AppState(services, FakeFileDialogs())
    }

    @After
    fun tearDown() {
        services.db.close()
    }

    @Test
    fun demoDataMatchesItsDescription() {
        val dash = services.queries.dashboard(DemoData.TODAY)
        assertEquals(7, dash.reagentTypes)
        assertEquals(10, dash.activeBatches)
        assertEquals(4, dash.expiringBatches)
        assertEquals(1, dash.expiredBatches)
        assertEquals(1, dash.belowMinReagents)
        assertEquals(5, dash.stateCounts[ExpiryState.GREEN])
        assertEquals(3, dash.stateCounts[ExpiryState.YELLOW])
        assertEquals(4, dash.byDepartment.count { it.issuedItems > 0 })
        assertEquals(setOf("Chilonzor", "Yunusobod"), dash.byDistrict.map { it.name }.toSet())
        val etil = services.queries.batchRows(BatchFilter(reagentId = ids.etilReagent), DemoData.TODAY)
        assertEquals(listOf("ES-003", "ES-002", "ES-001"), etil.map { it.series })
        assertEquals(14_000_000L, etil.first().groupTotal)
        assertEquals(MeasureUnit.L, etil.first().groupUnit)
        assertEquals(6_000_000L, etil.single { it.batchId == ids.es001 }.remainder)
        assertEquals(ExpiryState.EXPIRED, services.queries.batchRows(BatchFilter(), DemoData.TODAY).single { it.batchId == ids.xl117 }.state)
    }

    @Test
    fun navigateCarriesIntentAndBumpsSerial() {
        assertEquals(Page.DASHBOARD, state.page)
        val before = state.navigation.serial
        state.navigate(Page.REAGENTS, PageIntent.ReagentsFiltered(reagentId = ids.etilReagent))
        assertEquals(Page.REAGENTS, state.page)
        assertEquals(PageIntent.ReagentsFiltered(reagentId = ids.etilReagent), state.intent)
        assertEquals(before + 1, state.navigation.serial)
        state.navigate(Page.REAGENTS)
        assertNull(state.intent)
    }

    @Test
    fun successfulRunRefreshesAndCallsOnSuccess() {
        val tick = state.refreshTick
        var got: Long? = null
        val ok = state.run({ services.reagents.archiveBatch(ids.eo77); 42L }) { got = it }
        assertTrue(ok)
        assertEquals(42L, got)
        assertEquals(tick + 1, state.refreshTick)
        assertFalse("UI ulanmagan — band holati darhol bo‘shaydi", state.busy)
    }

    @Test
    fun userErrorGoesToFormAndMessage() {
        val form = FormErrors()
        val ok = state.run({ services.reagents.createBatch(NewBatch(name = "", series = "", receivedOn = null, initialQtyText = "", unit = null, expiresOn = null)) }, form = form)
        assertFalse(ok)
        assertEquals("Reaktiv nomi kiritilishi shart.", form["name"])
        assertNotNull(form["series"])
        assertNotNull(form["expiresOn"])
        // Shaklda chizilmagan maydonlar xatosi pastki xabar bo‘lib chiqadi.
        val message = state.messages.single()
        assertEquals(Severity.ERROR, message.severity)
        assertTrue(message.text, message.text.contains("Reaktiv nomi kiritilishi shart."))
        assertFalse(state.busy)
        // Keyingi muvaffaqiyatli amal eski xato xabarini va maydon xatolarini olib tashlaydi.
        assertTrue(state.run({ services.reagents.archiveBatch(ids.eo77) }, form = form))
        assertTrue(form.fields.isEmpty())
        assertTrue(state.messages.none { it.severity == Severity.ERROR })
    }

    @Test
    fun fieldErrorsShownInTheFormAreNotRepeatedBelow() {
        val form = FormErrors()
        listOf("name", "series", "receivedOn", "expiresOn", "initialQtyText", "unit").forEach { form[it] }
        state.run({ services.reagents.createBatch(NewBatch(name = "", series = "", receivedOn = null, initialQtyText = "", unit = null, expiresOn = null)) }, form = form)
        assertEquals("Reaktiv nomi kiritilishi shart.", form["name"])
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun confirmableErrorsReachTheCallerFirst() {
        var seen: UserError? = null
        val input = NewBatch(
            name = "Etil spirti 96%", series = "ES-001", receivedOn = DemoData.TODAY, initialQtyText = "1",
            unit = MeasureUnit.L, expiresOn = java.time.LocalDate.of(2027, 9, 1),
        )
        val ok = state.run({ services.reagents.createBatch(input) }, onError = { seen = it; true })
        assertFalse(ok)
        val error = seen as DuplicateBatchError
        assertTrue(error.exact)
        assertEquals(ids.es001, error.existingBatchId)
        assertTrue("tasdiqni chaqiruvchi hal qiladi — xabar chiqmaydi", state.messages.isEmpty())
    }

    @Test
    fun unexpectedErrorIsLoggedAndShownInDialog() {
        val ok = state.run({ throw IllegalStateException("boom") })
        assertFalse(ok)
        val error = state.unexpectedError!!
        assertEquals(AppState.UNEXPECTED_TEXT, error.message)
        assertTrue(error.details.contains("boom"))
        state.dismissUnexpected()
        assertNull(state.unexpectedError)
        assertEquals(emptyList<String>(), state.load(emptyList<String>()) { throw RuntimeException("read failed") })
        assertNotNull(state.unexpectedError)
    }

    @Test
    fun messagesAreCappedAndKeyed() {
        repeat(6) { state.info("xabar $it") }
        assertEquals(AppState.MAX_MESSAGES, state.messages.size)
        assertEquals("xabar 5", state.messages.last().text)
        state.showMessage("bir", key = "k")
        state.showMessage("ikki", key = "k")
        assertEquals(1, state.messages.count { it.key == "k" })
        val id = state.success("saqlandi")
        assertTrue(state.messages.first { it.id == id }.autoHide)
        state.dismissMessage(id)
        assertTrue(state.messages.none { it.id == id })
    }

    @Test
    fun tableLayoutRoundTripsThroughSettings() {
        assertNull(state.loadTableLayout("reagents"))
        state.saveTableLayout("reagents", """{"version":1,"order":["a"]}""")
        assertEquals("""{"version":1,"order":["a"]}""", state.loadTableLayout("reagents"))
        state.saveTableLayout("reagents", null)
        assertNull(state.loadTableLayout("reagents"))
    }
}

package uz.reaktivombor.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uz.reaktivombor.desktop.ui.components.Banner
import uz.reaktivombor.desktop.ui.components.ComboField
import uz.reaktivombor.desktop.ui.components.ConfirmDialog
import uz.reaktivombor.desktop.ui.components.FilterBar
import uz.reaktivombor.desktop.ui.components.DateField
import uz.reaktivombor.desktop.ui.components.DateRangeField
import uz.reaktivombor.desktop.ui.components.QtyField
import uz.reaktivombor.desktop.ui.components.ReasonDialog
import uz.reaktivombor.desktop.ui.components.SelectField
import uz.reaktivombor.desktop.ui.components.checkDateText
import uz.reaktivombor.desktop.ui.components.comboNewValue
import uz.reaktivombor.desktop.ui.components.filterSuggestions
import uz.reaktivombor.desktop.ui.theme.AppTheme
import uz.reaktivombor.desktop.ui.theme.Severity
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
class FieldsUiTest {
    private val today = DEMO_TODAY

    private fun SemanticsNodeInteraction.key(k: Key) = performKeyInput { pressKey(k) }

    private fun SemanticsNodeInteraction.retype(text: String) {
        performTextClearance()
        performTextInput(text)
    }

    @Test
    fun dateFieldParsesTypedInput() = runComposeUiTest {
        var value by mutableStateOf<LocalDate?>(null)
        var error: String? = null
        setContent {
            AppTheme {
                DateField(value, { value = it }, label = "Kelgan sana", today = today, maxDate = today, onErrorChange = { error = it }, modifier = Modifier.width(220.dp))
            }
        }
        val field = onNodeWithTag("date")
        field.performTextInput("17.10.2026")
        waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 17), value)
        assertNull(error)

        field.retype("7.1.26")
        waitForIdle()
        assertEquals(LocalDate.of(2026, 1, 7), value)
        field.assertTextEquals("7.1.26") // yozilgani o‘zgartirilmaydi

        field.retype("2026-03-05")
        waitForIdle()
        assertEquals(LocalDate.of(2026, 3, 5), value)

        field.retype("31.02.2026") // bunday sana yo‘q
        waitForIdle()
        assertNull(value)
        onNodeWithText("Sana noto‘g‘ri", substring = true).assertExists()
        assertEquals("Sana noto‘g‘ri. Masalan: 29.10.2026", error)

        field.retype("01.01.2030") // kelajak — maxDate = bugun
        waitForIdle()
        assertNull(value)
        onNodeWithText("Sana bugundan keyin bo‘lishi mumkin emas.").assertExists()
        field.assertTextEquals("01.01.2030") // matn o‘chib ketmaydi

        field.retype("12.1") // chala — yozish paytida xato ko‘rsatilmaydi
        waitForIdle()
        onNodeWithText("Sana noto‘g‘ri", substring = true).assertDoesNotExist()
    }

    @Test
    fun calendarPicksTodayAMonthAndClears() = runComposeUiTest {
        var value by mutableStateOf<LocalDate?>(null)
        setContent {
            AppTheme { DateField(value, { value = it }, today = today, modifier = Modifier.width(220.dp)) }
        }
        onNodeWithTag("date-calendar").performClick()
        onNodeWithText("Oktabr 2026").assertExists()
        listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya").forEach { onNodeWithText(it).assertExists() }
        onNodeWithTag("date-cal-day-2026-10-15").performClick()
        waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 15), value)
        onNodeWithTag("date").assertTextEquals("15.10.2026")
        onNodeWithText("Oktabr 2026").assertDoesNotExist() // tanlangach yopiladi

        // Keyingi oy, keyin oylar ro‘yxatidan mart va keyingi yil.
        onNodeWithTag("date-calendar").performClick()
        onNodeWithTag("date-cal-next-month").performClick()
        onNodeWithText("Noyabr 2026").assertExists()
        onNodeWithTag("date-cal-title").performClick()
        onNodeWithText("Dekabr").assertExists()
        onNodeWithTag("date-cal-month-3").performClick()
        onNodeWithTag("date-cal-next-year").performClick()
        onNodeWithText("Mart 2027").assertExists()
        onNodeWithTag("date-cal-day-2027-03-08").performClick()
        waitForIdle()
        assertEquals(LocalDate.of(2027, 3, 8), value)

        onNodeWithTag("date-calendar").performClick()
        onNodeWithText("Bugun").performClick()
        waitForIdle()
        assertEquals(today, value)

        onNodeWithTag("date-calendar").performClick()
        onNodeWithText("Tozalash").performClick()
        waitForIdle()
        assertNull(value)
        onNodeWithTag("date").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun calendarDoesNotPickOutOfRangeDays() = runComposeUiTest {
        var value by mutableStateOf<LocalDate?>(null)
        setContent {
            AppTheme { DateField(value, { value = it }, today = today, maxDate = today, modifier = Modifier.width(220.dp)) }
        }
        onNodeWithTag("date-calendar").performClick()
        onNodeWithTag("date-cal-day-2026-10-30").performClick() // ertaga — tanlanmaydi
        waitForIdle()
        assertNull(value)
        onNodeWithTag("date-cal-day-2026-10-28").performClick()
        waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 28), value)
    }

    @Test
    fun dateRangeWarnsWhenReversed() = runComposeUiTest {
        var from by mutableStateOf<LocalDate?>(null)
        var to by mutableStateOf<LocalDate?>(null)
        setContent {
            AppTheme { DateRangeField(from, to, { f, t -> from = f; to = t }, today = today) }
        }
        onNodeWithTag("range-from").performTextInput("10.10.2026")
        onNodeWithTag("range-to").performTextInput("01.10.2026")
        waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 10), from)
        assertEquals(LocalDate.of(2026, 10, 1), to)
        onNodeWithText("Boshlanish sanasi tugash sanasidan keyin bo‘lmasligi kerak.").assertExists()
    }

    private val names = listOf("Etil spirti", "Metil spirti", "Xloroform", "O‘simlik moyi", "Etilen glikol")

    private fun ComposeUiTest.combo(allowNew: Boolean = true): Triple<() -> String, MutableList<String>, MutableList<String>> {
        var value by mutableStateOf("")
        val picked = mutableListOf<String>()
        val created = mutableListOf<String>()
        setContent {
            AppTheme {
                Column {
                    ComboField(
                        value = value,
                        onValueChange = { value = it },
                        suggestions = names,
                        allowNew = allowNew,
                        onPick = { picked += it },
                        onNewItem = { created += it },
                        modifier = Modifier.width(300.dp),
                    )
                }
            }
        }
        return Triple({ value }, picked, created)
    }

    @Test
    fun comboFiltersAndPicksWithTheKeyboard() = runComposeUiTest {
        val (value, picked, _) = combo()
        val field = onNodeWithTag("combo")
        field.performTextInput("spir")
        waitForIdle()
        onNodeWithTag("combo-item-0").assertTextEquals("Etil spirti")
        onNodeWithTag("combo-item-1").assertTextEquals("Metil spirti")
        onNodeWithText("Xloroform").assertDoesNotExist()
        onNodeWithTag("combo-new").assertTextEquals("+ «spir» qo‘shish")

        field.retype("etil")
        waitForIdle()
        // Boshi mos keladiganlar birinchi.
        onNodeWithTag("combo-item-0").assertTextEquals("Etil spirti")
        onNodeWithTag("combo-item-1").assertTextEquals("Etilen glikol")
        onNodeWithTag("combo-item-2").assertTextEquals("Metil spirti")
        field.key(Key.DirectionDown)
        field.key(Key.DirectionDown)
        field.key(Key.Enter)
        waitForIdle()
        assertEquals("Etilen glikol", value())
        assertEquals(listOf("Etilen glikol"), picked)
        onNodeWithTag("combo-item-0").assertDoesNotExist() // yopildi

        // Enter aniq mos qiymatni ro‘yxatdagi yozilishiga keltiradi; tutuq belgisi farqlanmaydi.
        field.retype("o'simlik moyi")
        waitForIdle()
        onNodeWithTag("combo-item-0").assertTextEquals("O‘simlik moyi")
        onNodeWithTag("combo-new").assertDoesNotExist()
        field.key(Key.Enter)
        waitForIdle()
        assertEquals("O‘simlik moyi", value())

        // Esc ro‘yxatni yopadi, matn qoladi.
        field.retype("xlo")
        onNodeWithTag("combo-item-0").assertExists()
        field.key(Key.Escape)
        waitForIdle()
        onNodeWithTag("combo-item-0").assertDoesNotExist()
        assertEquals("xlo", value())
    }

    @Test
    fun comboOffersTheNewItem() = runComposeUiTest {
        val (value, _, created) = combo()
        onNodeWithTag("combo").performTextInput("  Kumush   nitrat ")
        waitForIdle()
        onNodeWithTag("combo-new").assertTextEquals("+ «Kumush nitrat» qo‘shish")
        onNodeWithTag("combo-new").performClick()
        waitForIdle()
        assertEquals(listOf("Kumush nitrat"), created)
        assertEquals("Kumush nitrat", value())
    }

    @Test
    fun comboWithoutNewItemsAndTheArrowShowsAll() = runComposeUiTest {
        combo(allowNew = false)
        onNodeWithTag("combo").performTextInput("kumush")
        waitForIdle()
        onNodeWithTag("combo-new").assertDoesNotExist()
        onNodeWithTag("combo-arrow").performClick()
        waitForIdle()
        names.forEachIndexed { i, name -> onNodeWithTag("combo-item-$i").assertTextEquals(name) }
    }

    @Test
    fun suggestionFilterAndNewValue() {
        assertEquals(listOf("Etil spirti", "Etilen glikol", "Metil spirti"), filterSuggestions("ETIL", names))
        assertEquals(names, filterSuggestions("  ", names))
        assertEquals(listOf("O‘simlik moyi"), filterSuggestions("o`sim", names))
        assertEquals(listOf("Etil spirti"), filterSuggestions("e", names, limit = 1))
        assertNull(comboNewValue("etil  SPIRTI", names))
        assertNull(comboNewValue("   ", names))
        assertEquals("Natriy xlorid", comboNewValue(" Natriy  xlorid", names))
    }

    @Test
    fun selectAndQtyFields() = runComposeUiTest {
        var dept by mutableStateOf<String?>(null)
        var amount by mutableStateOf("")
        var unit by mutableStateOf("l")
        setContent {
            AppTheme {
                Column {
                    SelectField(dept, listOf("Gistologiya", "Biologiya", "Sud-kimyo"), { dept = it }, label = "Bo‘lim", modifier = Modifier.width(220.dp))
                    QtyField(amount, { amount = it }, unit, { unit = it }, listOf("l", "ml"), label = "Miqdor", modifier = Modifier.width(220.dp))
                }
            }
        }
        onNodeWithText("Tanlang").assertExists()
        onNodeWithTag("select").performClick()
        onNodeWithTag("select-option-1").performClick()
        waitForIdle()
        assertEquals("Biologiya", dept)

        onNodeWithTag("qty").performTextInput("1 250,5 ml")
        waitForIdle()
        assertEquals("1 250,5 ", amount) // faqat son belgilari
        onNodeWithTag("qty-unit").performClick()
        onNodeWithTag("qty-unit-1").performClick()
        waitForIdle()
        assertEquals("ml", unit)
        onNodeWithText("kg").assertDoesNotExist() // ro‘yxat faqat berilgan birliklar
    }

    @Test
    fun reasonDialogRequiresAReason() = runComposeUiTest {
        var reason: String? = null
        setContent {
            AppTheme {
                ReasonDialog(title = "Bekor qilish", onConfirm = { reason = it }, onDismiss = {}, confirmLabel = "Tasdiqlash")
            }
        }
        onNodeWithText("Tasdiqlash").assertIsNotEnabled()
        onNodeWithTag("reason").performTextInput("   ")
        onNodeWithText("Tasdiqlash").assertIsNotEnabled()
        onNodeWithTag("reason").key(Key.Enter)
        waitForIdle()
        onNodeWithText("Sabab kiritilishi shart.").assertExists()
        assertNull(reason)
        onNodeWithTag("reason").performTextInput("Xato kiritilgan ")
        onNodeWithText("Tasdiqlash").assertIsEnabled()
        onNodeWithText("Tasdiqlash").performClick()
        waitForIdle()
        assertEquals("Xato kiritilgan", reason)
    }

    @Test
    fun filterBarBannerAndConfirmDialog() = runComposeUiTest {
        var active by mutableStateOf(2)
        var dialog by mutableStateOf(false) // oyna modal — avval qolganlari bosiladi
        val events = mutableListOf<String>()
        setContent {
            AppTheme {
                Column {
                    FilterBar(onClear = { events += "clear"; active = 0 }, activeCount = active) {
                        Text("Filtr maydoni")
                    }
                    Banner("Saqlandi", severity = Severity.SUCCESS, actionLabel = "Ko‘rish", onAction = { events += "action" }, onDismiss = { events += "dismiss"; dialog = true })
                    if (dialog) ConfirmDialog("O‘chirilsinmi?", "Qaytarib bo‘lmaydi.", onConfirm = { events += "yes" }, onDismiss = { events += "no" }, confirmLabel = "Ha, o‘chirish", danger = true)
                }
            }
        }
        onNodeWithText("Filtrlarni tozalash (2)").performClick()
        waitForIdle()
        onNodeWithText("Filtrlarni tozalash").assertIsNotEnabled()
        onNodeWithText("Ko‘rish").performClick()
        onNodeWithContentDescription("Yopish").performClick()
        onNodeWithText("Ha, o‘chirish").performClick()
        onNodeWithText("Bekor qilish").performClick()
        waitForIdle()
        assertEquals(listOf("clear", "action", "dismiss", "yes", "no"), events)
    }

    @Test
    fun dateTextChecks() {
        assertEquals(LocalDate.of(2026, 10, 17), checkDateText("17/10/2026", false, null, null, today).date)
        assertEquals("Sana kiritilishi shart.", checkDateText(" ", true, null, null, today).error)
        assertNull(checkDateText("", false, null, null, today).error)
        assertEquals(
            "Sana 01.01.1990 dan oldin bo‘lishi mumkin emas.",
            checkDateText("31.12.1989", false, LocalDate.of(1990, 1, 1), null, today).error,
        )
        assertEquals("Payshanba", UzDates.weekday(today))
        assertEquals("Dushanba", UzDates.weekday(LocalDate.of(2026, 10, 26)))
        assertEquals("Yanvar", UzDates.MONTHS.first())
        assertEquals("Dekabr", UzDates.MONTHS.last())
    }
}

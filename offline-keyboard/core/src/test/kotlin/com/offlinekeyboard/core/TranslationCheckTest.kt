package com.offlinekeyboard.core

import com.offlinekeyboard.core.translation.TranslationCheck
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cases taken from the real OPUS-MT output on the project's phrase set. */
class TranslationCheckTest {

    @Test
    fun `finds the values the model changed`() {
        assertEquals(listOf("22:00"), TranslationCheck.missing("Zmiana B zaczyna się o 22:00.", "Зміна Б починається о 10:00."))
        assertEquals(listOf("8:00", "16:00"), TranslationCheck.missing("Pracuję od 8:00 do 16:00.", "Я працюю від восьми до п'ятої години."))
        assertEquals(listOf("PESEL"), TranslationCheck.missing("Do umowy potrzebny jest numer PESEL.", "У угоду потрібен номер ESEL."))
        assertEquals(listOf("SSCC"), TranslationCheck.missing("Numer SSCC jest na etykiecie.", "Номер SSC на етиці."))
        assertEquals(listOf("ABC-123"), TranslationCheck.missing("Nie znaleziono kodu towaru ABC-123.", "Кода АБС-123 не знайдено."))
        assertEquals(listOf("IBAN"), TranslationCheck.missing("Wypłata przyjdzie na konto IBAN.", "ІБАН буде виплачено."))
        assertEquals(listOf("6"), TranslationCheck.missing("Потрібно 40 коробок по 6 штук.", "Potrzebujemy 40 pudełek do sześciu."))
    }

    @Test
    fun `passes when values are kept`() {
        assertEquals(emptyList<String>(), TranslationCheck.missing("Spotkanie o 14:30.", "Зустріч о 14:30."))
        assertEquals(emptyList<String>(), TranslationCheck.missing("Termin ważności do 30.06.2026.", "Термін придатності до 30.06.2026."))
        assertEquals(emptyList<String>(), TranslationCheck.missing("Sprawdź status w systemie WMS.", "Перевірте стан у системі WMS."))
        assertEquals(emptyList<String>(), TranslationCheck.missing("Mamy 12 palet i 3 kontenery.", "У нас 12 палет і 3 контейнери."))
        assertEquals(emptyList<String>(), TranslationCheck.missing("Привіт, як справи?", "Cześć, co słychać?"))
    }

    @Test
    fun `token boundaries and single letters`() {
        assertEquals(listOf("12"), TranslationCheck.missing("12 palet", "120 palet"))
        // A single capital letter (sector B, sentence start) is not a code.
        assertEquals(emptyList<String>(), TranslationCheck.protectedTokens("Sektor B. Jutro"))
        assertEquals(listOf("45821", "2026"), TranslationCheck.protectedTokens("Zamówienie 45821, rok 2026."))
    }
}

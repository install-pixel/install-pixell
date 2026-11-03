package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.qr.FieldType
import com.blacklaunch.qrcodes.qr.FormField
import com.blacklaunch.qrcodes.qr.ScanForm
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFormTest {
    private val day = java.util.GregorianCalendar(2026, 10, 3).time

    private val form = ScanForm(
        id = "f",
        name = "Прийом",
        fields = listOf(
            FormField("SKU", FieldType.SCAN),
            FormField("Рампа", FieldType.CHOICE, options = listOf("R1", "R2")),
            FormField("Дата", FieldType.DATE, datePattern = "ddMM yyyy"),
            FormField("Pack", FieldType.DERIVED, source = 0, suffix = "P"),
            FormField("К-сть", FieldType.NUMBER, suffix = "E"),
        ),
        separator = 2,
        custom = "|",
        endEnter = true,
    )

    @Test
    fun buildsContentWithDerivedSuffixesAndDate() {
        val c = form.content(listOf("12345", "R2", "", "", "24"), List(5) { true }, day)
        assertEquals("12345|R2|0311 2026|12345P|24E\n", c)
    }

    @Test
    fun switchedOffFieldStaysAsSkip() {
        val c = form.content(listOf("12345", "R2", "", "", "24"), listOf(true, true, false, true, true), day)
        assertEquals("12345|R2||12345P|24E\n", c)
    }

    @Test
    fun jsonRoundTrips() {
        val back = ScanForm.fromJson(form.toJson())!!
        assertEquals(form, back)
    }
}

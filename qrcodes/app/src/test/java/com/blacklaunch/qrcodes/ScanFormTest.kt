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
    fun stepsSkipSwitchedOffFields() {
        val form = ScanForm("t", "t", listOf(FormField("a", FieldType.TEXT, "A"), FormField("b", FieldType.TEXT, "B"), FormField("c", FieldType.TEXT, "C")))
        val active = form.activeSteps(listOf("", "", ""), listOf(true, false, true), java.util.Date(0))
        assertEquals(listOf(0, 2), active)
        assertEquals(0, form.skipBefore(active, 0))
        assertEquals(1, form.skipBefore(active, 1))
    }

    @Test
    fun terminalDownArrowSeparator() {
        val form = ScanForm("t", "t", listOf(FormField("a", FieldType.TEXT, "A"), FormField("b", FieldType.TEXT, "B")), separator = 3, endEnter = false)
        assertEquals("A\u001B[BB", form.content(listOf("", ""), listOf(true, true), java.util.Date(0)))
    }
}

package com.blacklaunch.launcher.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {
    @Test fun lengthInThreeLanguages() {
        assertEquals("6.21371 миль", UnitConverter.convert("10 км в милі"))
        assertEquals("16.0934 km", UnitConverter.convert("10 miles to km"))
        assertEquals("30.48 cm", UnitConverter.convert("1 ft in cm"))
        assertEquals("2.54 см", UnitConverter.convert("1 дюйм в см"))
    }

    @Test fun temperature() {
        assertEquals("86 °F", UnitConverter.convert("30 c в f"))
        assertEquals("100 °C", UnitConverter.convert("212 F to C"))
        assertEquals("273.15 K", UnitConverter.convert("0 °c to k"))
    }

    @Test fun massVolumeSpeed() {
        assertEquals("11.0231 lb", UnitConverter.convert("5 kg to lb"))
        assertEquals("1.5 кг", UnitConverter.convert("1500 г в кг"))
        assertEquals("3.78541 л", UnitConverter.convert("1 галон в літри"))
        assertEquals("62.1371 mph", UnitConverter.convert("100 км/год в mph"))
        assertEquals("27.7778 m/s", UnitConverter.convert("100 km/h to m/s"))
    }

    @Test fun decimalCommaAndNoSpace() {
        assertEquals("1.60934 km", UnitConverter.convert("1mi в km"))
        assertEquals("2500 м", UnitConverter.convert("2,5 км в метри"))
    }

    @Test fun notAConversion() {
        assertNull(UnitConverter.convert("телеграм"))
        assertNull(UnitConverter.convert("10 км в кг"))
        assertNull(UnitConverter.convert("10 км"))
        assertNull(UnitConverter.convert("маршрут до км"))
    }
}

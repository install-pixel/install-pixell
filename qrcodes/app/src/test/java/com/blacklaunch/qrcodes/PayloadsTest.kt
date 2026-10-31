package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.qr.Payloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadsTest {
    @Test
    fun wifiEscapesSpecialCharacters() {
        assertEquals("WIFI:T:WPA;S:Склад 1;P:a\\;b\\:c\\\\d;;", Payloads.wifi("Склад 1", "a;b:c\\d", Payloads.WifiSecurity.WPA, hidden = false))
        assertEquals("WIFI:T:nopass;S:Guest;H:true;;", Payloads.wifi("Guest", "ignored", Payloads.WifiSecurity.NONE, hidden = true))
        assertEquals("WIFI:T:SAE;S:Home;P:pass1234;;", Payloads.wifi("Home", "pass1234", Payloads.WifiSecurity.WPA3, hidden = false))
    }

    @Test
    fun contactIsVcardWithoutEmptyFields() {
        val v = Payloads.contact("Петро, склад", "+48 600 100 200", "", "")
        assertTrue(v.startsWith("BEGIN:VCARD\nVERSION:3.0\n"))
        assertTrue(v.contains("FN:Петро\\, склад\n"))
        assertTrue(v.contains("TEL:+48600100200\n"))
        assertTrue(!v.contains("EMAIL"))
        assertTrue(v.endsWith("END:VCARD"))
    }

    @Test
    fun urlGetsSchemeAndShortName() {
        assertEquals("https://example.com/a", Payloads.url(" example.com/a "))
        assertEquals("http://x.pl", Payloads.url("http://x.pl"))
        assertEquals("example.com", Payloads.siteName("https://www.example.com/path?q=1"))
    }
}

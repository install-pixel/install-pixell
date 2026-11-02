package com.blacklaunch.qrcodes

import com.blacklaunch.qrcodes.qr.ContentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentActionTest {
    @Test
    fun detectsCommonContent() {
        assertEquals(ContentAction.OpenUrl("https://x.pl/a"), ContentAction.detect("https://x.pl/a"))
        assertEquals(ContentAction.OpenUrl("https://www.x.pl"), ContentAction.detect("www.x.pl"))
        assertEquals(ContentAction.Dial("+48123"), ContentAction.detect("tel:+48123"))
        assertTrue(ContentAction.detect("BEGIN:VCARD\nFN:A\nEND:VCARD") is ContentAction.Contact)
        assertNull(ContentAction.detect("PLTTI ROZLADKONT"))
    }

    @Test
    fun parsesWifiWithEscapes() {
        val w = ContentAction.detect("WIFI:T:WPA;S:Мій\\;дім;P:pa\\:ss;H:true;;") as ContentAction.Wifi
        assertEquals("Мій;дім", w.ssid)
        assertEquals("pa:ss", w.password)
        assertEquals("WPA", w.type)
        assertTrue(w.hidden)
    }
}

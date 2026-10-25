package com.blacklaunch.launcher.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsParserTest {
    @Test fun settingWordFirstOrLast() {
        assertEquals(AppSettingKind.NOTIFICATIONS to "telegram", AppSettingsParser.parse("Сповіщення Telegram"))
        assertEquals(AppSettingKind.BATTERY to "spotify", AppSettingsParser.parse("батарея spotify"))
        assertEquals(AppSettingKind.INFO to "камера", AppSettingsParser.parse("дозволи камера"))
        assertEquals(AppSettingKind.NOTIFICATIONS to "telegram", AppSettingsParser.parse("telegram powiadomienia"))
        assertEquals(AppSettingKind.DATA to "youtube", AppSettingsParser.parse("data YouTube"))
    }

    @Test fun aloneOrUnrelatedIsNotAppSetting() {
        assertNull(AppSettingsParser.parse("сповіщення"))
        assertNull(AppSettingsParser.parse("telegram"))
        assertNull(AppSettingsParser.parse("напиши смс мамі"))
    }
}

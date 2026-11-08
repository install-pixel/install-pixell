package com.blacklaunch.diary

import com.blacklaunch.diary.data.Backup
import com.blacklaunch.diary.data.Day
import com.blacklaunch.diary.data.DiaryInfo
import com.blacklaunch.diary.data.Strike
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupTest {
    private val info = DiaryInfo("abc", "Мій щоденник", 123L, locked = true, salt = "AAAA", tint = 3)

    private fun make(photoSize: Int): Pair<ByteArray, ByteArray> {
        val photo = Random(1).nextBytes(photoSize)
        val out = ByteArrayOutputStream()
        val days = listOf(
            Day(20000, place = "Київ", sealed = "Висохле ", fresh = "свіже", freshSince = 5, strikes = listOf(Strike(0, 7)), photo = true),
            Day(20001, sealed = "Другий день"),
        )
        Backup.write(out, "пароль-123", info, days, photo = { if (it == 20000L) photo else null }, audio = { null }, coverGlass = byteArrayOf(1, 2), coverSheet = byteArrayOf(3))
        return out.toByteArray() to photo
    }

    @Test
    fun roundTrip() {
        // Фото більше за одну частину шифрування (256 КБ) — перевіряє склеювання частин.
        val (file, photo) = make(700_000)
        val c = Backup.read(ByteArrayInputStream(file), "пароль-123")
        assertEquals("Мій щоденник", c.name)
        assertEquals(3, c.tint)
        assertEquals(2, c.days.size)
        assertEquals("Висохле свіже", c.days[0].text)
        assertEquals(listOf(Strike(0, 7)), c.days[0].strikes)
        assertArrayEquals(photo, c.photos[20000L])
        assertArrayEquals(byteArrayOf(1, 2), c.coverGlass)
    }

    @Test
    fun wrongPassword() {
        val (file, _) = make(10)
        try {
            Backup.read(ByteArrayInputStream(file), "інший")
            fail()
        } catch (_: Backup.WrongPassword) {
        }
    }

    @Test
    fun truncatedFileRejected() {
        val (file, _) = make(600_000)
        try {
            Backup.read(ByteArrayInputStream(file.copyOf(file.size - 300_000)), "пароль-123")
            fail()
        } catch (_: java.io.IOException) {
        }
    }

    @Test
    fun notABackup() {
        try {
            Backup.read(ByteArrayInputStream("hello world, not a diary".toByteArray()), "x")
            fail()
        } catch (_: Backup.BadFile) {
        }
    }
}

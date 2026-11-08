package com.blacklaunch.diary.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Резервна копія щоденника — один файл, зашифрований паролем копії (не кодом замка й не ключем телефона: на новому
 * телефоні їх уже не буде). Усередині — zip (опис, дні, фото, аудіо, обкладинка), зашифрований частинами AES-GCM:
 * великий щоденник із фото не мусить уміщатися в пам'ять, а обрізаний чи підмінений файл не відкриється.
 */
object Backup {
    private val MAGIC = "BLDIARY1".toByteArray()
    private const val ITERATIONS = 210_000
    private const val CHUNK = 256 * 1024

    class WrongPassword : IOException()
    class BadFile : IOException()

    /** Що лежить у копії (дні вже розшифровані; медіа — як є). */
    class Contents(
        val name: String,
        val created: Long,
        val tint: Int,
        val days: List<Day>,
        val photos: Map<Long, ByteArray>,
        val audios: Map<Long, ByteArray>,
        val coverGlass: ByteArray?,
        val coverSheet: ByteArray?,
    )

    fun write(
        out: OutputStream,
        password: String,
        info: DiaryInfo,
        days: List<Day>,
        photo: (Long) -> ByteArray?,
        audio: (Long) -> ByteArray?,
        coverGlass: ByteArray?,
        coverSheet: ByteArray?,
    ) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(DiaryKeys.pbkdf2(password, salt, ITERATIONS), "AES")
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.write(salt)
        data.writeInt(ITERATIONS)
        val sealed = ChunkedOut(data, key)
        ZipOutputStream(sealed).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("info.json", JSONObject().put("name", info.name).put("created", info.created).put("tint", info.tint).put("version", 1).toString().toByteArray())
            val arr = JSONArray()
            days.forEach { arr.put(dayToJson(it)) }
            put("days.json", arr.toString().toByteArray())
            days.forEach { d ->
                if (d.photo) photo(d.day)?.let { put("media/photo_${d.day}.jpg", it) }
                if (d.audio) audio(d.day)?.let { put("media/audio_${d.day}.m4a", it) }
            }
            coverGlass?.let { put("cover_glass.jpg", it) }
            coverSheet?.let { put("cover_sheet.jpg", it) }
        }
    }

    fun read(input: InputStream, password: String): Contents {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        runCatching { data.readFully(magic) }.onFailure { throw BadFile() }
        if (!magic.contentEquals(MAGIC)) throw BadFile()
        val salt = ByteArray(16).also { data.readFully(it) }
        val iterations = data.readInt()
        if (iterations !in 10_000..5_000_000) throw BadFile()
        val key = SecretKeySpec(DiaryKeys.pbkdf2(password, salt, iterations), "AES")
        var name = ""
        var created = 0L
        var tint = 0
        var days = emptyList<Day>()
        val photos = HashMap<Long, ByteArray>()
        val audios = HashMap<Long, ByteArray>()
        var coverGlass: ByteArray? = null
        var coverSheet: ByteArray? = null
        ZipInputStream(ChunkedIn(data, key)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                when {
                    e.name == "info.json" -> JSONObject(String(bytes)).let {
                        name = it.optString("name")
                        created = it.optLong("created")
                        tint = it.optInt("tint")
                    }
                    e.name == "days.json" -> JSONArray(String(bytes)).let { a -> days = List(a.length()) { dayFromJson(a.getJSONObject(it)) } }
                    e.name.startsWith("media/photo_") -> e.name.removePrefix("media/photo_").removeSuffix(".jpg").toLongOrNull()?.let { photos[it] = bytes }
                    e.name.startsWith("media/audio_") -> e.name.removePrefix("media/audio_").removeSuffix(".m4a").toLongOrNull()?.let { audios[it] = bytes }
                    e.name == "cover_glass.jpg" -> coverGlass = bytes
                    e.name == "cover_sheet.jpg" -> coverSheet = bytes
                }
            }
        }
        return Contents(name, created, tint, days, photos, audios, coverGlass, coverSheet)
    }

    private fun dayToJson(d: Day) = JSONObject()
        .put("day", d.day)
        .put("place", d.place)
        .put("sealed", d.sealed)
        .put("fresh", d.fresh)
        .put("freshSince", d.freshSince)
        .put("strikes", DayCodec.strikesToJson(d.strikes))
        .put("ink", DayCodec.inkToJson(d.ink))
        .put("photo", d.photo)
        .put("caption", d.caption)
        .put("audio", d.audio)
        .put("audioMs", d.audioMs)
        .put("wave", DayCodec.waveToString(d.wave))
        .put("updated", d.updated)

    private fun dayFromJson(o: JSONObject) = Day(
        day = o.getLong("day"),
        place = o.optString("place"),
        sealed = o.optString("sealed"),
        fresh = o.optString("fresh"),
        freshSince = o.optLong("freshSince"),
        strikes = DayCodec.strikesFromJson(o.optString("strikes")),
        ink = DayCodec.inkFromJson(o.optString("ink")),
        photo = o.optBoolean("photo"),
        caption = o.optString("caption"),
        audio = o.optBoolean("audio"),
        audioMs = o.optLong("audioMs"),
        wave = DayCodec.waveFromString(o.optString("wave")),
        updated = o.optLong("updated"),
    )

    /**
     * Частина: [довжина][IV 12][шифротекст]. Номер частини й ознака «остання» — у додаткових даних GCM, тож переставити
     * чи відрізати частини непомітно не вийде.
     */
    private class ChunkedOut(private val out: DataOutputStream, private val key: SecretKeySpec) : OutputStream() {
        private val buf = ByteArrayOutputStream(CHUNK)
        private var index = 0L
        private var closed = false

        override fun write(b: Int) {
            buf.write(b)
            if (buf.size() >= CHUNK) emit(false)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var left = len
            while (left > 0) {
                val n = minOf(left, CHUNK - buf.size())
                buf.write(b, o, n)
                o += n
                left -= n
                if (buf.size() >= CHUNK) emit(false)
            }
        }

        private fun emit(last: Boolean) {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.updateAAD(aad(index, last))
            val enc = c.doFinal(buf.toByteArray())
            out.writeInt(enc.size)
            out.write(iv)
            out.write(enc)
            buf.reset()
            index++
        }

        override fun close() {
            if (closed) return
            closed = true
            emit(true)
            out.flush()
        }
    }

    private class ChunkedIn(private val input: DataInputStream, private val key: SecretKeySpec) : InputStream() {
        private var chunk = ByteArray(0)
        private var pos = 0
        private var index = 0L
        private var done = false

        private fun next(): Boolean {
            if (done) return false
            val len = runCatching { input.readInt() }.getOrElse { throw BadFile() }
            if (len !in 16..(CHUNK + 64)) throw BadFile()
            val iv = ByteArray(12).also { input.readFully(it) }
            val enc = ByteArray(len).also { input.readFully(it) }
            // Спершу пробуємо як звичайну частину, потім як останню.
            chunk = decrypt(iv, enc, false) ?: decrypt(iv, enc, true)?.also { done = true }
                ?: throw if (index == 0L) WrongPassword() else BadFile()
            pos = 0
            index++
            return true
        }

        private fun decrypt(iv: ByteArray, enc: ByteArray, last: Boolean): ByteArray? = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.updateAAD(aad(index, last))
            c.doFinal(enc)
        }.getOrNull()

        override fun read(): Int {
            while (pos >= chunk.size) if (!next()) return -1
            return chunk[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (pos >= chunk.size) if (!next()) return -1
            val n = minOf(len, chunk.size - pos)
            System.arraycopy(chunk, pos, b, off, n)
            pos += n
            return n
        }
    }

    private fun aad(index: Long, last: Boolean): ByteArray =
        ByteArray(9).also { a ->
            for (i in 0 until 8) a[i] = (index ushr (8 * i)).toByte()
            a[8] = if (last) 1 else 0
        }
}

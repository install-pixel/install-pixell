package com.blacklaunch.diary.data

import android.content.ContentValues
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.Closeable
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Зашифрована база одного щоденника (SQLCipher) і його зашифровані фото та аудіо.
 *
 * Фото й аудіо шифруються окремим ключем [mediaKey], що лежить у самій базі: зміна коду перешифровує лише базу.
 */
class DiaryDb private constructor(private val db: SQLiteDatabase, val dir: File) : Closeable {

    private val mediaKey: SecretKey

    init {
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS day(
                day INTEGER PRIMARY KEY,
                place TEXT NOT NULL DEFAULT '',
                sealed TEXT NOT NULL DEFAULT '',
                fresh TEXT NOT NULL DEFAULT '',
                fresh_since INTEGER NOT NULL DEFAULT 0,
                strikes TEXT NOT NULL DEFAULT '',
                ink TEXT NOT NULL DEFAULT '',
                photo INTEGER NOT NULL DEFAULT 0,
                audio INTEGER NOT NULL DEFAULT 0,
                audio_ms INTEGER NOT NULL DEFAULT 0,
                wave TEXT NOT NULL DEFAULT '',
                updated INTEGER NOT NULL DEFAULT 0)""",
        )
        val stored = meta("media_key")
        val raw = if (stored != null) {
            android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        } else {
            ByteArray(32).also { SecureRandom().nextBytes(it) }.also {
                setMeta("media_key", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP))
            }
        }
        mediaKey = SecretKeySpec(raw, "AES")
    }

    fun meta(key: String): String? =
        db.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun setMeta(key: String, value: String?) {
        if (value == null) {
            db.delete("meta", "k = ?", arrayOf(key))
        } else {
            db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", key); put("v", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun days(): List<Day> = db.rawQuery(
        "SELECT day, place, sealed, fresh, fresh_since, strikes, ink, photo, audio, audio_ms, wave, updated FROM day ORDER BY day",
        emptyArray<String>(),
    ).use { c ->
        val out = ArrayList<Day>(c.count)
        while (c.moveToNext()) {
            out += Day(
                day = c.getLong(0),
                place = c.getString(1),
                sealed = c.getString(2),
                fresh = c.getString(3),
                freshSince = c.getLong(4),
                strikes = DayCodec.strikesFromJson(c.getString(5)),
                ink = DayCodec.inkFromJson(c.getString(6)),
                photo = c.getInt(7) != 0,
                audio = c.getInt(8) != 0,
                audioMs = c.getLong(9),
                wave = DayCodec.waveFromString(c.getString(10)),
                updated = c.getLong(11),
            )
        }
        out
    }

    /** Зберегти день (порожній — прибрати зі щоденника: порожні дні не зберігаються). */
    fun save(d: Day) {
        if (d.isEmpty) {
            db.delete("day", "day = ?", arrayOf(d.day.toString()))
            return
        }
        val v = ContentValues().apply {
            put("day", d.day)
            put("place", d.place)
            put("sealed", d.sealed)
            put("fresh", d.fresh)
            put("fresh_since", d.freshSince)
            put("strikes", DayCodec.strikesToJson(d.strikes))
            put("ink", DayCodec.inkToJson(d.ink))
            put("photo", if (d.photo) 1 else 0)
            put("audio", if (d.audio) 1 else 0)
            put("audio_ms", d.audioMs)
            put("wave", DayCodec.waveToString(d.wave))
            put("updated", d.updated)
        }
        db.insertWithOnConflict("day", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Новий ключ бази (поставили, змінили чи зняли код). */
    fun rekey(passphrase: ByteArray) {
        db.changePassword(rawKey(passphrase))
    }

    // ——— Фото й аудіо: AES-GCM, у файлі — 12 байт IV і шифротекст ———

    fun photoFile(day: Long) = File(dir, "photo_$day.bin")
    fun audioFile(day: Long) = File(dir, "audio_$day.bin")

    fun writeMedia(file: File, plain: ByteArray) {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, mediaKey, GCMParameterSpec(128, iv))
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { out ->
            out.write(iv)
            out.write(cipher.doFinal(plain))
        }
        tmp.renameTo(file)
    }

    fun readMedia(file: File): ByteArray? {
        if (!file.exists()) return null
        return runCatching {
            val all = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, mediaKey, GCMParameterSpec(128, all, 0, 12))
            cipher.doFinal(all, 12, all.size - 12)
        }.getOrNull()
    }

    override fun close() = db.close()

    companion object {
        @Volatile private var loaded = false

        private fun load() {
            if (!loaded) {
                System.loadLibrary("sqlcipher")
                loaded = true
            }
        }

        /**
         * Ключ уже «розтягнутий» ([DiaryKeys]) — передаємо його SQLCipher як готовий ключ (формат x'…'), щоб він не
         * проганяв свій повільний KDF ще раз на кожне відкриття.
         */
        private fun rawKey(key: ByteArray): ByteArray =
            ("x'" + key.joinToString("") { "%02X".format(it) } + "'").toByteArray(Charsets.US_ASCII)

        /** Відкрити (чи створити) базу. Неправильний ключ — виняток. */
        fun open(dir: File, passphrase: ByteArray): DiaryDb {
            load()
            dir.mkdirs()
            val file = File(dir, "diary.db")
            val db = SQLiteDatabase.openOrCreateDatabase(file, rawKey(passphrase), null, null, null)
            // Перевірка ключа: з неправильним ключем перший же запит падає.
            db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { it.moveToFirst() }
            return DiaryDb(db, dir)
        }
    }
}

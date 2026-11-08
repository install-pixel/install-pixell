package com.blacklaunch.diary.audio

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Запис голосу дня. Пишеться в тимчасовий файл у кеші, після зупинки — зашифровується в щоденник, а тимчасовий
 * стирається ([finish]).
 */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L

    /** Обриси звуку (0…1) під час запису — кожні ~80 мс. */
    val wave = ArrayList<Float>()

    fun start(): Boolean {
        val f = File(context.cacheDir, "rec_${System.nanoTime()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
            recorder = r
            file = f
            started = System.currentTimeMillis()
            wave.clear()
            true
        }.getOrElse {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    /** Поточна гучність 0…1 (викликати періодично, поки пишемо). */
    fun sample(): Float {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val v = (kotlin.math.sqrt(amp / 32767f)).coerceIn(0f, 1f)
        wave += v
        return v
    }

    val elapsed: Long get() = if (recorder == null) 0L else System.currentTimeMillis() - started

    /** Зупинити: байти запису й тривалість (null — не вийшло, напр. запис коротший за секунду). */
    fun finish(): Pair<ByteArray, Long>? {
        val r = recorder ?: return null
        val f = file
        val ms = elapsed
        recorder = null
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        val bytes = if (ok && f != null && f.exists()) f.readBytes() else null
        f?.delete()
        return bytes?.let { it to ms }
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.delete()
        file = null
    }
}

/** Програвання з пам'яті (розшифроване не лягає на диск). */
class Player {
    private var player: MediaPlayer? = null

    val playing: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)
    val position: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    val duration: Int get() = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    fun play(bytes: ByteArray, from: Int, onDone: () -> Unit): Boolean {
        stop()
        val p = MediaPlayer()
        return runCatching {
            p.setDataSource(BytesSource(bytes))
            p.setOnCompletionListener { onDone() }
            p.prepare()
            if (from > 0) p.seekTo(from)
            p.start()
            player = p
            true
        }.getOrElse {
            runCatching { p.release() }
            false
        }
    }

    fun pause() = runCatching { player?.pause() }
    fun resume() = runCatching { player?.start() }
    fun seek(ms: Int) = runCatching { player?.seekTo(ms) }

    fun stop() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    private class BytesSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val n = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, n)
            return n
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() = Unit
    }
}

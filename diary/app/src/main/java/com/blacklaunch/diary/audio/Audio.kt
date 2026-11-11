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

    /** Швидкість програвання: перемотка вперед під час слухання — звук біжить швидше. */
    fun speed(k: Float) = runCatching { player?.let { it.playbackParams = it.playbackParams.setSpeed(k) } }

    fun stop() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    internal class BytesSource(private val data: ByteArray) : MediaDataSource() {
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

/**
 * Дописати запис у кінець касети: два файли m4a (AAC) склеюються в один без перекодування — семпли другого йдуть одразу
 * після першого. Тимчасові файли — лише в кеші програми й одразу стираються.
 */
object Tape {
    /** Справжня тривалість запису (мс) — з самого файлу, а не з годинника. */
    fun durationMs(bytes: ByteArray): Long? = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(object : android.media.MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position >= bytes.size) return -1
                    val n = minOf(size.toLong(), bytes.size - position).toInt()
                    System.arraycopy(bytes, position.toInt(), buffer, offset, n)
                    return n
                }
                override fun getSize(): Long = bytes.size.toLong()
                override fun close() = Unit
            })
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            r.release()
        }
    }.getOrNull()

    fun concat(a: ByteArray, b: ByteArray, cache: File): ByteArray? {
        val fa = File.createTempFile("tape_a", ".m4a", cache)
        val fb = File.createTempFile("tape_b", ".m4a", cache)
        val out = File.createTempFile("tape_o", ".m4a", cache)
        return try {
            fa.writeBytes(a)
            fb.writeBytes(b)
            val muxer = android.media.MediaMuxer(out.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            var offset = 0L
            val buf = java.nio.ByteBuffer.allocate(1 shl 20)
            val info = android.media.MediaCodec.BufferInfo()
            for (f in listOf(fa, fb)) {
                val ex = android.media.MediaExtractor()
                ex.setDataSource(f.path)
                val ti = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ex.selectTrack(ti)
                if (track < 0) {
                    track = muxer.addTrack(ex.getTrackFormat(ti))
                    muxer.start()
                }
                var last = offset
                // Час семплів у файлі може починатися не з нуля — рахуємо від першого, щоб між шматками не було тиші.
                var first = -1L
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    if (first < 0) first = ex.sampleTime
                    info.offset = 0
                    info.size = n
                    info.presentationTimeUs = offset + (ex.sampleTime - first).coerceAtLeast(0)
                    info.flags = android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                    muxer.writeSampleData(track, buf, info)
                    last = info.presentationTimeUs
                    ex.advance()
                }
                // Наступний — через один кадр AAC (1024 семпли при 44,1 кГц).
                offset = last + 23_220
                ex.release()
            }
            muxer.stop()
            muxer.release()
            out.readBytes()
        } catch (e: Exception) {
            null
        } finally {
            fa.delete()
            fb.delete()
            out.delete()
        }
    }
}

/**
 * Перемотка назад під час слухання — звук задом наперед і прискорено (як на магнітофоні, коли тримаєш «назад»).
 * Шматками по 4 с від [fromMs] до початку: кожен розкодовується, перевертається й іде в динамік утричі швидше.
 */
class ReverseCue(private val bytes: ByteArray, private val fromMs: Long, private val speed: Int = 3) {
    @Volatile private var running = true

    fun start() {
        Thread { runCatching { run() } }.apply { isDaemon = true; start() }
    }

    fun stop() { running = false }

    private fun run() {
        var endUs = fromMs * 1000
        var track: android.media.AudioTrack? = null
        try {
            while (running && endUs > 0) {
                val startUs = (endUs - 4_000_000L).coerceAtLeast(0L)
                val dec = decode(startUs, endUs) ?: break
                val (pcm, rate, ch) = dec
                if (track == null) {
                    val cfg = if (ch == 1) android.media.AudioFormat.CHANNEL_OUT_MONO else android.media.AudioFormat.CHANNEL_OUT_STEREO
                    val min = android.media.AudioTrack.getMinBufferSize(rate, cfg, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    track = android.media.AudioTrack.Builder()
                        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(cfg).build())
                        .setBufferSizeInBytes(min * 2)
                        .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                        .build()
                    track.play()
                }
                val frames = pcm.size / ch
                val outFrames = frames / speed
                val out = ShortArray(outFrames * ch)
                for (i in 0 until outFrames) {
                    val src = frames - 1 - i * speed
                    for (c in 0 until ch) out[i * ch + c] = pcm[src * ch + c]
                }
                var off = 0
                while (running && off < out.size) {
                    val n = minOf(2048, out.size - off)
                    track.write(out, off, n)
                    off += n
                }
                endUs = startUs
            }
        } finally {
            track?.let { t -> runCatching { t.pause(); t.flush(); t.stop() }; runCatching { t.release() } }
        }
    }

    /** Розкодувати [fromUs, toUs) у PCM 16 біт: семпли, частота, канали. */
    private fun decode(fromUs: Long, toUs: Long): Triple<ShortArray, Int, Int>? {
        val ex = android.media.MediaExtractor()
        var codec: android.media.MediaCodec? = null
        try {
            ex.setDataSource(Player.BytesSource(bytes))
            val ti = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(ti)
            val fmt = ex.getTrackFormat(ti)
            var rate = fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            val c = android.media.MediaCodec.createDecoderByType(fmt.getString(android.media.MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            ex.seekTo(fromUs, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val out = java.io.ByteArrayOutputStream()
            val info = android.media.MediaCodec.BufferInfo()
            var inDone = false
            while (running) {
                if (!inDone) {
                    val ii = c.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime >= toUs) {
                            c.queueInputBuffer(ii, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 5000)
                if (oi == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    rate = c.outputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    ch = c.outputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                } else if (oi >= 0) {
                    if (info.size > 0 && info.presentationTimeUs >= fromUs && info.presentationTimeUs < toUs) {
                        val ob = c.getOutputBuffer(oi)!!
                        val arr = ByteArray(info.size)
                        ob.position(info.offset)
                        ob.get(arr, 0, info.size)
                        out.write(arr)
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            val raw = out.toByteArray()
            val shorts = ShortArray(raw.size / 2)
            java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return Triple(shorts, rate, ch.coerceAtLeast(1))
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }
}

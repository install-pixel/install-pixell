package com.blacklaunch.diary.audio

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Запис голосу дня — сам, без системного диктофона: мікрофон → обробка ([VoiceDsp]) → AAC у тимчасовий файл у кеші;
 * після зупинки файл зашифровується в щоденник, а тимчасовий стирається ([finish]).
 * [start] з режимом крутілки: 0 — «як є» (усе довкола), 1 — «голос», 2 — «шумодав».
 * Можна ставити на паузу (як «запис + пауза» на магнітофоні): на паузі нічого не пишеться, а час не йде.
 */
class Recorder(private val context: Context) {
    private var record: android.media.AudioRecord? = null
    private var thread: Thread? = null
    private var file: File? = null
    private val effects = ArrayList<android.media.audiofx.AudioEffect>()
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var failed = false
    @Volatile private var level = 0f
    @Volatile private var frames = 0L
    private val rate = 44_100
    // Справжній час запису без пауз: більше звуку, ніж минуло часу, не пишемо (мікрофон інколи віддає накопичене пачкою).
    @Volatile private var activeNs = 0L
    @Volatile private var activeSince = 0L
    private fun activeNow(): Long = activeNs + (if (!paused && activeSince > 0) System.nanoTime() - activeSince else 0L)

    /** Обриси звуку (0…1) під час запису — кожні ~80 мс. */
    val wave = ArrayList<Float>()

    val isPaused: Boolean get() = paused

    @android.annotation.SuppressLint("MissingPermission")
    fun start(mode: Int = 0): Boolean {
        if (running) return false
        val src = if (mode == 0) android.media.MediaRecorder.AudioSource.MIC else android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
        val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val ar = runCatching { android.media.AudioRecord(src, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192) * 2) }.getOrNull() ?: return false
        if (ar.state != android.media.AudioRecord.STATE_INITIALIZED) { ar.release(); return false }
        // Вбудовані в телефон фільтри: шумозаглушення (з «голосу») і вирівнювання гучності (на «шумодаві»).
        if (mode >= 1 && android.media.audiofx.NoiseSuppressor.isAvailable()) {
            runCatching { android.media.audiofx.NoiseSuppressor.create(ar.audioSessionId)?.also { it.enabled = true; effects += it } }
        }
        if (mode >= 2 && android.media.audiofx.AutomaticGainControl.isAvailable()) {
            runCatching { android.media.audiofx.AutomaticGainControl.create(ar.audioSessionId)?.also { it.enabled = true; effects += it } }
        }
        val fmt = android.media.MediaFormat.createAudioFormat(android.media.MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(android.media.MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = runCatching {
            android.media.MediaCodec.createEncoderByType(android.media.MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(fmt, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        }.getOrNull()
        if (codec == null) { releaseEffects(); ar.release(); return false }
        val f = File(context.cacheDir, "rec_${System.nanoTime()}.m4a")
        val muxer = runCatching { android.media.MediaMuxer(f.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }.getOrNull()
        if (muxer == null || runCatching { ar.startRecording() }.isFailure) {
            runCatching { codec.stop() }; codec.release(); muxer?.release(); releaseEffects(); ar.release(); f.delete()
            return false
        }
        record = ar
        file = f
        frames = 0
        level = 0f
        failed = false
        paused = false
        activeNs = 0L
        activeSince = System.nanoTime()
        running = true
        wave.clear()
        val dsp = VoiceDsp(mode, rate)
        thread = Thread {
            val buf = ShortArray(1024)
            val info = android.media.MediaCodec.BufferInfo()
            var track = -1
            var started = false
            fun drain(eos: Boolean) {
                while (true) {
                    val oi = codec.dequeueOutputBuffer(info, if (eos) 10_000 else 0)
                    when {
                        oi == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true
                        }
                        oi >= 0 -> {
                            val ob = codec.getOutputBuffer(oi)
                            if (ob != null && started && info.size > 0 && info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                muxer.writeSampleData(track, ob, info)
                            }
                            codec.releaseOutputBuffer(oi, false)
                            if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                        else -> if (!eos) return
                    }
                }
            }
            try {
                while (running) {
                    if (paused) { Thread.sleep(20); drain(false); continue }
                    val n = ar.read(buf, 0, buf.size)
                    if (n <= 0 || paused) continue
                    if ((frames + n) * 1_000_000_000L / rate > activeNow() + 400_000_000L) continue
                    dsp.process(buf, n)
                    level = dsp.level
                    var off = 0
                    while (off < n) {
                        val ii = codec.dequeueInputBuffer(10_000)
                        if (ii < 0) { drain(false); continue }
                        val ib = codec.getInputBuffer(ii)!!
                        ib.clear()
                        val cnt = minOf(n - off, ib.capacity() / 2)
                        ib.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer().put(buf, off, cnt)
                        codec.queueInputBuffer(ii, 0, cnt * 2, frames * 1_000_000L / rate, 0)
                        frames += cnt
                        off += cnt
                    }
                    drain(false)
                }
                var ii = -1
                for (k in 0 until 50) { ii = codec.dequeueInputBuffer(10_000); if (ii >= 0) break; drain(false) }
                if (ii >= 0) codec.queueInputBuffer(ii, 0, 0, frames * 1_000_000L / rate, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
            } catch (e: Exception) {
                failed = true
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                runCatching { if (started) muxer.stop() }
                runCatching { muxer.release() }
                runCatching { ar.stop() }
                runCatching { ar.release() }
                releaseEffects()
            }
        }.apply { isDaemon = true; start() }
        return true
    }

    private fun releaseEffects() {
        effects.forEach { runCatching { it.release() } }
        effects.clear()
    }

    /** Поточна гучність 0…1 (викликати періодично, поки пишемо). */
    fun sample(): Float {
        val v = if (paused) 0f else level
        if (!paused) wave += v
        return v
    }

    /** Скільки вже записано (без пауз). */
    val elapsed: Long get() = frames * 1000L / rate

    fun pause() {
        if (!running || paused) return
        activeNs += System.nanoTime() - activeSince
        paused = true
        runCatching { record?.stop() }
    }

    fun resume() {
        if (!running || !paused) return
        runCatching { record?.startRecording() }
        activeSince = System.nanoTime()
        paused = false
    }

    /** Зупинити: байти запису й тривалість (null — не вийшло). */
    fun finish(): Pair<ByteArray, Long>? {
        if (!running) return null
        val ms = elapsed
        if (paused) runCatching { record?.startRecording() }
        paused = false
        running = false
        runCatching { thread?.join(4000) }
        thread = null
        record = null
        val f = file
        file = null
        val bytes = if (!failed && f != null && f.exists() && f.length() > 0) f.readBytes() else null
        f?.delete()
        return bytes?.let { it to ms }
    }

    fun cancel() {
        if (!running) return
        running = false
        paused = false
        runCatching { thread?.join(1500) }
        thread = null
        record = null
        file?.delete()
        file = null
    }
}

/**
 * Обробка голосу просто під час запису (усе на телефоні, без мережі):
 * - зріз низького гулу (холодильник, машини, вітер) — фільтр високих частот;
 * - «шумові ворота»: між словами фон тихішає;
 * - на «шумодаві» ще й стиснення гучності: тихі слова підтягуються, гучні не ріжуть вухо.
 * Режим 0 — нічого не змінює, лише міряє гучність для стрілки.
 */
class VoiceDsp(private val mode: Int, rate: Int) {
    var level = 0f
        private set
    // Фільтр високих частот (біквад Баттерворта).
    private val b0: Float; private val b1: Float; private val b2: Float; private val a1: Float; private val a2: Float
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
    private var env = 0f
    private var gain = 1f
    private val attack: Float
    private val release: Float
    private val envAtk: Float
    private val envRel: Float

    init {
        val fc = if (mode >= 2) 120.0 else 85.0
        val w0 = 2.0 * Math.PI * fc / rate
        val q = 0.7071
        val alpha = kotlin.math.sin(w0) / (2 * q)
        val cs = kotlin.math.cos(w0)
        val a0 = 1 + alpha
        b0 = ((1 + cs) / 2 / a0).toFloat()
        b1 = (-(1 + cs) / a0).toFloat()
        b2 = ((1 + cs) / 2 / a0).toFloat()
        a1 = (-2 * cs / a0).toFloat()
        a2 = ((1 - alpha) / a0).toFloat()
        attack = (1 - kotlin.math.exp(-1.0 / (0.005 * rate))).toFloat()
        release = (1 - kotlin.math.exp(-1.0 / (0.18 * rate))).toFloat()
        envAtk = (1 - kotlin.math.exp(-1.0 / (0.002 * rate))).toFloat()
        envRel = (1 - kotlin.math.exp(-1.0 / (0.08 * rate))).toFloat()
    }

    fun process(buf: ShortArray, n: Int) {
        var peak = 0f
        val gateOpen = if (mode >= 2) 0.022f else 0.012f
        val floor = if (mode >= 2) 0.12f else 0.3f
        for (i in 0 until n) {
            var x = buf[i] / 32768f
            if (mode >= 1) {
                val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x; y2 = y1; y1 = y
                x = y
                val a = kotlin.math.abs(x)
                env += (a - env) * (if (a > env) envAtk else envRel)
                val target = if (env > gateOpen) 1f else floor
                gain += (target - gain) * (if (target > gain) attack else release)
                x *= gain
                if (mode >= 2) {
                    // Стиснення понад поріг 3:1 і підтягування, м'яке обмеження на краях.
                    val t = 0.2f
                    val ax = kotlin.math.abs(x)
                    if (ax > t) x = kotlin.math.sign(x) * (t + (ax - t) / 3f)
                    x *= 1.7f
                    x = x / (1f + kotlin.math.abs(x) * 0.25f)
                }
                buf[i] = (x * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            }
            val a = kotlin.math.abs(x)
            if (a > peak) peak = a
        }
        level = kotlin.math.sqrt(peak).coerceIn(0f, 1f)
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
    /** Обрізати запис до [endMs] (те, що далі, стирається — як запис поверх на магнітофоні). */
    fun trim(bytes: ByteArray, endMs: Long, cache: File): ByteArray? {
        val fa = File.createTempFile("tape_t", ".m4a", cache)
        val out = File.createTempFile("tape_to", ".m4a", cache)
        return try {
            fa.writeBytes(bytes)
            val ex = android.media.MediaExtractor()
            ex.setDataSource(fa.path)
            val ti = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(ti)
            val muxer = android.media.MediaMuxer(out.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val track = muxer.addTrack(ex.getTrackFormat(ti))
            muxer.start()
            val buf = java.nio.ByteBuffer.allocate(1 shl 20)
            val info = android.media.MediaCodec.BufferInfo()
            var first = -1L
            while (true) {
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                if (first < 0) first = ex.sampleTime
                val t = ex.sampleTime - first
                if (t >= endMs * 1000) break
                info.offset = 0; info.size = n; info.presentationTimeUs = t; info.flags = android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                muxer.writeSampleData(track, buf, info)
                ex.advance()
            }
            ex.release()
            muxer.stop(); muxer.release()
            out.readBytes()
        } catch (e: Exception) {
            null
        } finally {
            fa.delete(); out.delete()
        }
    }

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
                val out = reverseFast(pcm, ch, rate)
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

    /**
     * Задом наперед і в [speed] разів швидше, але без «писку»: висота голосу та сама, як і при перемотці вперед
     * (там програвач теж прискорює, не змінюючи висоти). Перевернутий звук стискається накладанням коротких шматочків
     * (по 40 мс, з плавними краями) — береться кожен [speed]-й, а не кожен [speed]-й семпл.
     */
    private fun reverseFast(pcm: ShortArray, ch: Int, rate: Int): ShortArray {
        val frames = pcm.size / ch
        val n = (rate * 0.04f).toInt().coerceAtLeast(64)
        val hs = n / 2
        val ha = hs * speed
        if (frames <= n) return ShortArray(0)
        val grains = (frames - n) / ha + 1
        val outFrames = (grains - 1) * hs + n
        val acc = FloatArray(outFrames * ch)
        val win = FloatArray(n) { i -> (0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * i / n).toFloat()) }
        for (k in 0 until grains) {
            val inPos = k * ha
            val outPos = k * hs
            for (i in 0 until n) {
                // Читаємо перевернуте: кадр (frames - 1 - (inPos + i)).
                val src = frames - 1 - (inPos + i)
                if (src < 0) break
                val w = win[i]
                for (c in 0 until ch) acc[(outPos + i) * ch + c] += pcm[src * ch + c] * w
            }
        }
        return ShortArray(acc.size) { acc[it].coerceIn(-32768f, 32767f).toInt().toShort() }
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

package com.blacklaunch.launcher.ui

import android.os.BatteryManager
import android.os.SystemClock
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.settings.MonitorLook
import com.blacklaunch.launcher.settings.MonitorTile
import com.blacklaunch.launcher.system.DeviceInfo
import com.blacklaunch.launcher.system.NetworkType
import com.blacklaunch.launcher.system.SystemSnapshot
import com.blacklaunch.launcher.system.SystemStats
import com.blacklaunch.launcher.system.UsageInfo
import com.blacklaunch.launcher.ui.theme.Oled
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Приглушене тепле для тепла — у тон акцентів, не яскраве. */
private val Warm = Color(0xFFC9956A)
private val Track = Color(0x1FFFFFFF)
/** Розряджені поділки шкали — рівні й помітні, щоб не здавалось, ніби частину кола щось закриває. */
private val DialTrack = Color(0x40FFFFFF)

/** Наш шрифт, але цифри однакової ширини — щоб значення не «стрибали» щосекунди. */
@Composable
private fun num(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) =
    LocalTextStyle.current.merge(TextStyle(fontSize = size, color = color, fontWeight = weight, fontFeatureSettings = "tnum", letterSpacing = 0.sp))

/**
 * Моніторинг системи у дусі htop: шкала батареї, під нею окремі картки — батарея з графіком струму, RAM, диск,
 * тепло, мережа з живою швидкістю, час екрана. Дотик на картці — плашки з усіма даними.
 * Дані — раз на 2 с і лише поки екран на виду.
 */
@Composable
internal fun MonitorScreen(
    look: MonitorLook,
    stats: SystemStats,
    appLabel: (String) -> String?,
    /** Назва профілю (або «МОНІТОРИНГ», якщо відкрито командою) — замість окремої плашки. */
    title: String,
    modifier: Modifier = Modifier,
    /** Дотик до назви режиму — наступний режим (дашборд не має «зациклювати»). */
    onTitleClick: (() -> Unit)? = null,
) {
    val accent = Color(look.accent.argb)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var snap by remember { mutableStateOf<SystemSnapshot?>(null) }
    var usage by remember { mutableStateOf<UsageInfo?>(null) }
    var device by remember { mutableStateOf<DeviceInfo?>(null) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    /** Швидкість мережі зараз, байт/с: отримання й надсилання. */
    var rate by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val currentHistory = remember { mutableStateListOf<Float>() }
    val memoryHistory = remember { mutableStateListOf<Float>() }
    val tempHistory = remember { mutableStateListOf<Float>() }
    val downHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(lifecycle, stats) {
        if (device == null) device = withContext(Dispatchers.IO) { runCatching { stats.device() }.getOrNull() }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var tick = 0
            var last: Triple<Long, Long, Long>? = null
            while (true) {
                val next = withContext(Dispatchers.IO) { stats.snapshot() }
                snap = next
                next.battery.currentMa?.let { currentHistory.push(it.toFloat()) }
                next.battery.temperatureC?.let { tempHistory.push(it) }
                memoryHistory.push(1f - next.memory.availableBytes.toFloat() / next.memory.totalBytes.coerceAtLeast(1))
                // Швидкість мережі — різниця лічильників між знімками.
                val now = SystemClock.elapsedRealtime()
                val rx = next.network.rxBytes
                val tx = next.network.txBytes
                if (rx != null && tx != null) {
                    last?.let { (t, r, x) ->
                        val seconds = ((now - t) / 1000f).coerceAtLeast(0.5f)
                        val down = ((rx - r).coerceAtLeast(0) / seconds).toLong()
                        val up = ((tx - x).coerceAtLeast(0) / seconds).toLong()
                        rate = down to up
                        downHistory.push(down.toFloat())
                    }
                    last = Triple(now, rx, tx)
                }
                if (MonitorTile.USAGE in look.tiles && tick % USAGE_EVERY == 0) {
                    usage = withContext(Dispatchers.IO) { runCatching { stats.usage(appLabel) }.getOrNull() }
                }
                tick++
                delay(REFRESH_MS)
            }
        }
    }
    val s = snap ?: return
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    val memUsed = 1f - s.memory.availableBytes.toFloat() / s.memory.totalBytes.coerceAtLeast(1)
    val storageUsed = 1f - s.storage.freeBytes.toFloat() / s.storage.totalBytes.coerceAtLeast(1)
    val tiles = look.tiles
    val headroomPct = s.thermal.headroom?.let { ((1f - it.coerceIn(0f, 1f)) * 100).roundToInt() }
    val signal = s.network.signalDbm?.let { signalLevel(it, s.network.type) }
    val currentText = s.battery.currentMa?.let { stringResource(R.string.mon_u_ma, signed(it)) }
    val powerText = s.battery.powerW?.let { stringResource(R.string.mon_u_w, "%.1f".format(abs(it))) }
    val voltageText = s.battery.voltageMv?.let { stringResource(R.string.mon_u_v, "%.2f".format(it / 1000f)) }
    val stateText = stringResource(if (s.battery.charging) R.string.mon_charging else R.string.mon_discharging)
    // Стрілки з підписом: скільки телефон отримує й надсилає щосекунди.
    val rateText = rate?.let { (down, up) ->
        stringResource(R.string.mon_net_now, stringResource(R.string.mon_u_rate, size(down)), stringResource(R.string.mon_u_rate, size(up)))
    }

    Column(
        modifier.fillMaxSize().fadingEdges().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Oled.TextSecondary,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            )
            // Характеристики — за кнопкою-телефоном, щоб не займати місце на екрані.
            if (MonitorTile.DEVICE in tiles && device != null) {
                Box(
                    Modifier.size(40.dp)
                        .then(if (LocalSheet.current) Modifier.pencilFrame(20.dp, seed = 61).clip(RoundedCornerShape(20.dp)) else Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Oled.Glint, RoundedCornerShape(20.dp)))
                        .clickable { detail = Detail.DEVICE },
                    contentAlignment = Alignment.Center,
                ) {
                    PhoneGlyph(accent, Modifier.size(width = 12.dp, height = 20.dp))
                }
            }
        }

        if (MonitorTile.BATTERY in tiles) {
            Box(
                Modifier.size(176.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(88.dp)).clickable { detail = Detail.BATTERY },
                contentAlignment = Alignment.Center,
            ) {
                BatteryDial(s.battery.percent / 100f, s.battery.charging, accent, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.battery.percent}%", style = num(40.sp, Oled.TextPrimary, FontWeight.ExtraLight))
                    currentText?.let { Text(it, style = num(12.sp, accent)) }
                    Text(stateText, style = num(11.sp, Oled.TextSecondary))
                }
            }
        }

        // Батарея — одразу під шкалою: напруга й потужність, графік струму, мін/сер/макс.
        // Картка є з першого знімка, щоб не з'являлась пізніше й не зсувала все вниз; графік домалюється.
        if (MonitorTile.CURRENT_GRAPH in tiles && currentHistory.isNotEmpty()) {
            val minText = stringResource(R.string.mon_u_ma, signed(currentHistory.min().roundToInt()))
            val avgText = stringResource(R.string.mon_u_ma, signed(currentHistory.average().roundToInt()))
            val maxText = stringResource(R.string.mon_u_ma, signed(currentHistory.max().roundToInt()))
            MetricCard(stringResource(R.string.mon_battery), listOfNotNull(voltageText, powerText).joinToString(" · "), accent, { detail = Detail.BATTERY }) {
                Sparkline(currentHistory, accent, null, Modifier.fillMaxWidth().height(52.dp))
                Footnote(stringResource(R.string.mon_minmax, minText, avgText, maxText))
            }
        }

        if (MonitorTile.MEMORY in tiles) {
            MetricCard(
                stringResource(R.string.mon_row_ram),
                "${size(s.memory.totalBytes - s.memory.availableBytes)} / ${size(s.memory.totalBytes)} · ${(memUsed * 100).roundToInt()}%",
                accent,
                { detail = Detail.MEMORY },
            ) {
                Segments(memUsed, accent)
                // Пам'ять — у межах 0–100%, щоб висота лінії й була зайнятою часткою.
                if (MonitorTile.MEMORY_GRAPH in tiles && memoryHistory.size > 1) Sparkline(memoryHistory, accent, 0f to 1f, Modifier.fillMaxWidth().height(40.dp))
                Footnote(stringResource(R.string.mon_free, size(s.memory.availableBytes)))
            }
        }

        if (MonitorTile.STORAGE in tiles) {
            MetricCard(
                stringResource(R.string.mon_row_disk),
                "${size(s.storage.totalBytes - s.storage.freeBytes)} / ${size(s.storage.totalBytes)} · ${(storageUsed * 100).roundToInt()}%",
                accent,
                { detail = Detail.STORAGE },
            ) {
                Segments(storageUsed, accent)
                Footnote(
                    listOfNotNull(
                        stringResource(R.string.mon_free, size(s.storage.freeBytes)),
                        usage?.appsBytes?.let { stringResource(R.string.mon_apps_size, size(it)) },
                    ).joinToString(" · "),
                )
            }
        }

        val temp = s.battery.temperatureC
        if (MonitorTile.THERMAL in tiles && temp != null) {
            val state = s.thermal.status?.let { stringResource(thermalLabel(it)) }
            val headroomText = headroomPct?.let { stringResource(R.string.mon_headroom_full, stringResource(R.string.mon_headroom_label), it) }
            MetricCard(stringResource(R.string.mon_row_heat), listOfNotNull("%.1f °C".format(temp), state).joinToString(" · "), Warm, { detail = Detail.THERMAL }) {
                // Смуга — температура батареї від 20 до 45 °C.
                Segments(((temp - 20f) / 25f).coerceIn(0f, 1f), Warm)
                if (tempHistory.size > 1) Sparkline(tempHistory, Warm, null, Modifier.fillMaxWidth().height(32.dp))
                headroomText?.let { Footnote(it) }
            }
        }

        if (MonitorTile.NETWORK in tiles) {
            // Швидкість з'єднання Wi‑Fi (як у налаштуваннях) — точніша за оцінку Android; оцінка — лише без неї.
            val linkText = s.network.wifiRxMbps?.let { stringResource(R.string.mon_wifi_link, it) }
                ?: s.network.downKbps?.let { stringResource(R.string.mon_link, it / 1000) }
            MetricCard(
                stringResource(R.string.mon_row_net),
                listOfNotNull(stringResource(s.network.type.label()), signal?.let { stringResource(R.string.mon_signal_pct, (it * 100).roundToInt()) }).joinToString(" · "),
                accent,
                { detail = Detail.NETWORK },
            ) {
                // Смуга — сила сигналу (повна = 100%), графік — швидкість отримання зараз.
                signal?.let { Segments(it, accent) }
                if (downHistory.size > 1) Sparkline(downHistory, accent, 0f to maxOf(downHistory.max(), 1f), Modifier.fillMaxWidth().height(32.dp))
                rateText?.let { Footnote(it) }
                Footnote(listOfNotNull(linkText, stringResource(R.string.mon_net_graph).takeIf { downHistory.size > 1 }).joinToString(" · "))
            }
        }

        if (MonitorTile.USAGE in tiles) {
            val u = usage
            if (u == null) {
                Text(stringResource(R.string.mon_usage_off), color = Oled.TextSecondary, fontSize = 11.sp)
            } else {
                val wifiToday = u.wifiBytes?.let { stringResource(R.string.mon_wifi_today, size(it)) }
                val mobileToday = u.mobileBytes?.let { stringResource(R.string.mon_mobile_today, size(it)) }
                Glass {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.mon_screen_today, duration(u.screenMs)), style = num(12.sp, Oled.TextPrimary))
                        u.unlocks?.let { Text(stringResource(R.string.mon_unlocks, it), style = num(11.sp, Oled.TextSecondary)) }
                    }
                    u.top.forEachIndexed { i, (name, ms) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = num(11.sp, Oled.TextSecondary))
                            Text(name, style = num(12.sp, Oled.TextPrimary), modifier = Modifier.weight(1f), maxLines = 1)
                            Text(duration(ms), style = num(11.sp, Oled.TextSecondary))
                        }
                    }
                    Footnote(listOfNotNull(wifiToday, mobileToday).joinToString(" · "))
                }
            }
        }

        // Час без перезапуску — внизу, дрібно.
        Text(
            stringResource(R.string.mon_uptime, duration(s.uptimeMs)),
            style = num(11.sp, Oled.TextSecondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        detail?.let { which ->
            val plates: List<Plate> = when (which) {
                Detail.BATTERY -> listOfNotNull(
                    Plate(stringResource(R.string.mon_level), "${s.battery.percent}%"),
                    Plate(stringResource(R.string.mon_state), stateText),
                    currentText?.let { Plate(stringResource(R.string.mon_current_label), it) },
                    powerText?.let { Plate(stringResource(R.string.mon_power), it) },
                    voltageText?.let { Plate(stringResource(R.string.mon_voltage), it) },
                    temp?.let { Plate(stringResource(R.string.mon_temperature), "%.1f °C".format(it)) },
                    healthLabel(s.battery.health)?.let { Plate(stringResource(R.string.mon_health), stringResource(it)) },
                    s.battery.cycles?.let { Plate(stringResource(R.string.mon_cycles), "$it") },
                    s.battery.toFullMs?.let { Plate(stringResource(R.string.mon_to_full), duration(it)) },
                )
                Detail.MEMORY -> listOf(
                    Plate(stringResource(R.string.mon_total), size(s.memory.totalBytes)),
                    Plate(stringResource(R.string.mon_used), "${size(s.memory.totalBytes - s.memory.availableBytes)} · ${(memUsed * 100).roundToInt()}%"),
                    Plate(stringResource(R.string.mon_available), size(s.memory.availableBytes)),
                    Plate(stringResource(R.string.mon_pressure), stringResource(if (s.memory.low) R.string.mon_yes else R.string.mon_no)),
                )
                Detail.STORAGE -> listOfNotNull(
                    Plate(stringResource(R.string.mon_total), size(s.storage.totalBytes)),
                    Plate(stringResource(R.string.mon_used), "${size(s.storage.totalBytes - s.storage.freeBytes)} · ${(storageUsed * 100).roundToInt()}%"),
                    Plate(stringResource(R.string.mon_available), size(s.storage.freeBytes)),
                    usage?.appsBytes?.let { Plate(stringResource(R.string.mon_apps_label), size(it)) },
                )
                Detail.THERMAL -> listOfNotNull(
                    temp?.let { Plate(stringResource(R.string.mon_temperature), "%.1f °C".format(it)) },
                    s.thermal.status?.let { Plate(stringResource(R.string.mon_thermal_state), stringResource(thermalLabel(it))) },
                    headroomPct?.let { Plate(stringResource(R.string.mon_headroom_label), "$it%", wide = true) },
                    headroomPct?.let { Plate("", stringResource(R.string.mon_headroom_hint), wide = true, note = true) },
                )
                Detail.NETWORK -> listOfNotNull(
                    Plate(stringResource(R.string.mon_type), stringResource(s.network.type.label())),
                    s.network.signalDbm?.let { dbm ->
                        Plate(
                            stringResource(R.string.mon_signal),
                            "${(signalLevel(dbm, s.network.type) * 100).roundToInt()}%\n" + stringResource(R.string.mon_u_dbm, dbm),
                        )
                    },
                    rate?.let { Plate(stringResource(R.string.mon_down), stringResource(R.string.mon_u_rate, size(it.first))) },
                    rate?.let { Plate(stringResource(R.string.mon_up), stringResource(R.string.mon_u_rate, size(it.second))) },
                    s.network.wifiRxMbps?.let { Plate(stringResource(R.string.mon_wifi_rx), stringResource(R.string.mon_u_mbps, it)) },
                    s.network.wifiTxMbps?.let { Plate(stringResource(R.string.mon_wifi_tx), stringResource(R.string.mon_u_mbps, it)) },
                    s.network.downKbps?.let { Plate(stringResource(R.string.mon_speed), stringResource(R.string.mon_u_mbps, it / 1000)) },
                    s.network.upKbps?.let { Plate(stringResource(R.string.mon_link_up), stringResource(R.string.mon_u_mbps, it / 1000)) },
                    usage?.wifiBytes?.let { Plate(stringResource(R.string.mon_wifi_label), size(it)) },
                    usage?.mobileBytes?.let { Plate(stringResource(R.string.mon_mobile_label), size(it)) },
                )
                Detail.DEVICE -> device?.let { d ->
                    val cores = (
                        listOf(stringResource(R.string.mon_cores, d.cores)) +
                            d.clusters.map { (count, mhz) -> "$count × " + stringResource(R.string.mon_ghz, "%.2f".format(mhz / 1000f)) }
                        ).joinToString("\n")
                    val screen = listOfNotNull(d.screenPx, d.screenInches?.let { "%.1f″".format(it) }, d.refreshHz?.let { stringResource(R.string.mon_u_hz, it) }).joinToString("\n")
                    listOfNotNull(
                        Plate(stringResource(R.string.mon_model), d.model, wide = true),
                        d.soc?.let { Plate(stringResource(R.string.mon_chip), it, wide = true) },
                        Plate(stringResource(R.string.mon_cpu), cores),
                        Plate(stringResource(R.string.mon_screen), screen),
                        Plate(stringResource(R.string.mon_row_ram), size(d.ramBytes)),
                        Plate(stringResource(R.string.mon_row_disk), size(s.storage.totalBytes)),
                        d.cameras.takeIf { it.isNotEmpty() }?.let { cams -> Plate(stringResource(R.string.mon_cameras), cameraText(cams), wide = true) },
                        Plate(stringResource(R.string.mon_android), d.android),
                        d.securityPatch?.let { Plate(stringResource(R.string.mon_patch), it) },
                        d.kernel?.let { Plate(stringResource(R.string.mon_kernel), it) },
                    )
                }.orEmpty()
            }
            DetailSheet(stringResource(which.title), plates, if (which == Detail.THERMAL) Warm else accent) { detail = null }
        }
    }
}

/** Що розгорнуто у вікні деталей. */
private enum class Detail(@StringRes val title: Int) {
    BATTERY(R.string.mon_battery_title), MEMORY(R.string.mtile_memory), STORAGE(R.string.mtile_storage),
    THERMAL(R.string.mtile_thermal), NETWORK(R.string.mtile_network), DEVICE(R.string.mon_device_title),
}

/** Плашка у вікні деталей: [wide] — на всю ширину, [note] — пояснення дрібним текстом без рамки. */
private class Plate(val label: String, val value: String, val wide: Boolean = false, val note: Boolean = false)

/** Камери: мегапікселі — лише коли телефон дав повну роздільність кожної; інакше чесна кількість. */
@Composable
private fun cameraText(cams: List<Pair<Boolean, Float?>>): String {
    val back = cams.filter { !it.first }.map { it.second }
    val front = cams.filter { it.first }.map { it.second }
    val backPart = cameraPart(back)
    val frontPart = cameraPart(front)
    return listOfNotNull(
        backPart?.let { stringResource(R.string.mon_cam_back, it) },
        frontPart?.let { stringResource(R.string.mon_cam_front, it) },
    ).joinToString("\n")
}

@Composable
private fun cameraPart(mp: List<Float?>): String? = when {
    mp.isEmpty() -> null
    mp.all { it != null } -> stringResource(R.string.mon_u_mp, mp.filterNotNull().sortedDescending().joinToString(", ") { "%.0f".format(it) })
    else -> "${mp.size}"
}

/** Вікно деталей у нашому стилі: скляна панель із плашками по дві в ряд; дотик будь-де — закрити. */
@Composable
private fun DetailSheet(title: String, plates: List<Plate>, accent: Color, onClose: () -> Unit) {
    // Широкі плашки — окремим рядком, вузькі — парами.
    val rows = remember(plates) {
        val out = mutableListOf<List<Plate>>()
        var pending: Plate? = null
        for (p in plates) {
            val waiting = pending
            when {
                p.wide -> {
                    if (waiting != null) out.add(listOf(waiting))
                    pending = null
                    out.add(listOf(p))
                }
                waiting == null -> pending = p
                else -> {
                    out.add(listOf(waiting, p))
                    pending = null
                }
            }
        }
        pending?.let { out.add(listOf(it)) }
        out
    }
    Dialog(onDismissRequest = onClose) {
        BlurBehindDialog()
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .glassBox(24.dp, seed = 67, fill = SolidColor(Color(0xFF0A0A0A)), paper = Color.Black)
                .clickable(onClick = onClose)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                Text(title.uppercase(), style = num(12.sp, Oled.TextSecondary).copy(letterSpacing = 2.sp))
            }
            rows.forEach { row ->
                // Плашки в ряду — однієї висоти, навіть якщо в одній текст у два рядки.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p -> PlateView(p, accent, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun PlateView(p: Plate, accent: Color, modifier: Modifier) {
    if (p.note) {
        Text(p.value, style = num(11.sp, Oled.TextSecondary).copy(lineHeight = 16.sp), modifier = modifier.padding(horizontal = 4.dp))
        return
    }
    Column(
        modifier
            .glassBox(14.dp, seed = p.label.length + 71)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(p.label.uppercase(), style = num(9.sp, Oled.TextSecondary).copy(letterSpacing = 1.sp, lineHeight = 12.sp), maxLines = 2)
        // Довге слово («розряджається») — дрібніше, щоб не рвалось посередині.
        val longest = p.value.lines().maxOfOrNull { it.length } ?: 0
        val valueSize = if (longest > LONG_VALUE) 13.sp else 15.sp
        Text(p.value, style = num(valueSize, lerp(accent, Color.White, 0.6f)).copy(lineHeight = valueSize * 1.3f))
    }
}

/** Скляна картка, як у налаштуваннях Black Glass. */
@Composable
private fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassBox(18.dp, seed = currentCompositeKeyHash)
            .then(modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Картка показника: назва й головне значення зверху, далі шкала, графік і дрібний підсумок; дотик — деталі. */
@Composable
private fun MetricCard(label: String, value: String, color: Color, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Glass(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Text(label, style = num(10.sp, Oled.TextSecondary).copy(letterSpacing = 1.5.sp), maxLines = 1, modifier = Modifier.weight(1f))
            Text(value, style = num(12.sp, Oled.TextPrimary), maxLines = 1)
        }
        content()
    }
}

@Composable
private fun Footnote(text: String) {
    if (text.isNotEmpty()) Text(text, style = num(10.sp, Oled.TextSecondary), maxLines = 1)
}

/** Сегментна шкала, як у htop: скляні сегменти, світліший відблиск зверху. */
@Composable
private fun Segments(level: Float, color: Color) {
    val lit = (level.coerceIn(0f, 1f) * SEGMENTS).roundToInt()
    val glass = Brush.verticalGradient(listOf(lerp(color, Color.White, 0.45f), color, color.copy(alpha = 0.6f)))
    val sheet = LocalSheet.current
    Canvas(Modifier.fillMaxWidth().height(if (sheet) 12.dp else 8.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
        // «Аркуш»: шкала — рядок рисок олівцем («паличок»): заповнені — кольором, решта — ледь помітні.
        if (sheet) {
            repeat(SEGMENTS) { i ->
                val x = i * (w + gap) + w / 2
                val lean = w * 0.35f
                val ink = if (i < lit) color else Track
                pencilTick(Offset(x - lean, size.height), Offset(x + lean, 0f), if (i < lit) 2.2.dp.toPx() else 1.4.dp.toPx(), ink, i)
            }
            return@Canvas
        }
        val corner = CornerRadius(2.dp.toPx())
        repeat(SEGMENTS) { i ->
            val topLeft = Offset(i * (w + gap), 0f)
            val cell = Size(w, size.height)
            if (i < lit) drawRoundRect(glass, topLeft, cell, corner) else drawRoundRect(Track, topLeft, cell, corner)
        }
    }
}

/** Значок телефона: корпус із заокругленими кутами й риска внизу. */
@Composable
private fun PhoneGlyph(color: Color, modifier: Modifier) {
    val sheet = LocalSheet.current
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        if (sheet) {
            drawPath(taperedPath(pencilLoop(size, 3.dp.toPx(), 5, 0.4.dp.toPx()), 1.8.dp.toPx()), color)
            pencilTick(Offset(size.width * 0.35f, size.height * 0.82f), Offset(size.width * 0.65f, size.height * 0.8f), 1.6.dp.toPx(), color, 3)
            return@Canvas
        }
        drawRoundRect(color, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        drawLine(color, Offset(size.width * 0.35f, size.height * 0.82f), Offset(size.width * 0.65f, size.height * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Шкала батареї з поділками: світлі — заряд, тьмяні — розряджена частина; під час заряджання світлом біжить хвиля. */
@Composable
private fun BatteryDial(battery: Float, charging: Boolean, accent: Color, modifier: Modifier) {
    // Хвиля — лише поки заряджається й екран на виду (анімація не йде у фоні).
    val wave = if (charging) {
        rememberInfiniteTransition(label = "charge")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(CHARGE_WAVE_MS, easing = LinearEasing)), label = "wave").value
    } else {
        -1f
    }
    val sheet = LocalSheet.current
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val lit = (battery * TICKS).roundToInt()
        // «Аркуш»: поділки — штрихи олівцем (без світіння), коло всередині — намальоване від руки.
        if (sheet) {
            repeat(TICKS) { i ->
                val boost = if (wave >= 0f && i < lit) (1f - abs(i - wave * (lit + WAVE_TAIL)) / WAVE_TAIL).coerceIn(0f, 1f) else 0f
                val tint = if (i < lit) lerp(accent, Color.White, 0.6f * boost) else DialTrack
                val angle = (i * 360f / TICKS - 90f) * (Math.PI.toFloat() / 180f)
                val dir = Offset(kotlin.math.cos(angle), kotlin.math.sin(angle))
                val outer = r - 3.dp.toPx()
                val inner = r - (if (i % 5 == 0) 14.dp else 10.dp).toPx()
                pencilTick(c + dir * outer, c + dir * inner, if (i < lit) 2.4.dp.toPx() else 1.5.dp.toPx(), tint, i)
            }
            val ring = r - 22.dp.toPx()
            if (ring > 0f) {
                translate(c.x - ring, c.y - ring) {
                    drawPath(taperedPath(pencilLoop(Size(ring * 2, ring * 2), ring, 73, 1.2.dp.toPx()), 1.6.dp.toPx()), Track)
                }
            }
            return@Canvas
        }
        repeat(TICKS) { i ->
            // Неон: тонка світла лінія над м'яким світінням; колір плавно світлішає вздовж кола.
            // Яскравість хвилі на цій поділці: найсильніше в її центрі, згасає за ~4 поділки.
            val boost = if (wave >= 0f && i < lit) (1f - abs(i - wave * (lit + WAVE_TAIL)) / WAVE_TAIL).coerceIn(0f, 1f) else 0f
            val tint = lerp(lerp(accent, Color.White, 0.35f * i / TICKS), Color.White, 0.6f * boost)
            rotate(i * 360f / TICKS, c) {
                val start = Offset(c.x, c.y - r + 3.dp.toPx())
                val end = Offset(c.x, c.y - r + (if (i % 5 == 0) 13.dp else 9.dp).toPx())
                if (i < lit) drawLine(tint.copy(alpha = 0.22f + 0.4f * boost), start, end, strokeWidth = (5f + 3f * boost).dp.toPx(), cap = StrokeCap.Round)
                drawLine(if (i < lit) tint else DialTrack, start, end, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        // Тонке скляне коло навколо цифр.
        drawCircle(Track, r - 22.dp.toPx(), c, style = Stroke(1.dp.toPx()))
    }
}

/** Лінія з м'якою заливкою під нею; [range] — фіксовані межі (інакше від найменшого до найбільшого). */
@Composable
private fun Sparkline(values: List<Float>, color: Color, range: Pair<Float, Float>?, modifier: Modifier) {
    val sheet = LocalSheet.current
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = range?.first ?: values.min()
        val max = range?.second ?: values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        // Тонкі напрямні: верх, середина, низ.
        listOf(0f, 0.5f, 1f).forEach { f -> drawLine(Track, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f) }
        val line = Path()
        values.forEachIndexed { i, v ->
            // На всю ширину одразу, а не лише коли назбирається 5 хвилин.
            val x = size.width * i / (values.size - 1).toFloat()
            val y = size.height - ((v - min) / span).coerceIn(0f, 1f) * size.height
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        if (sheet) {
            // «Аркуш»: лінія графіка — штрихом олівця, загострена на кінцях, без заливки.
            val points = values.mapIndexed { i, v ->
                Offset(size.width * i / (values.size - 1).toFloat(), size.height - ((v - min) / span).coerceIn(0f, 1f) * size.height)
            }
            drawPath(taperedPath(points, 2.2.dp.toPx()), color)
            return@Canvas
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun MutableList<Float>.push(value: Float) {
    add(value)
    while (size > HISTORY) removeAt(0)
}

private fun signed(ma: Int) = if (ma > 0) "+$ma" else "$ma"

@Composable
private fun duration(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> stringResource(R.string.dur_dh, days.toInt(), (hours % 24).toInt())
        hours > 0 -> stringResource(R.string.dur_hm, hours.toInt(), (minutes % 60).toInt())
        else -> stringResource(R.string.dur_m, minutes.toInt())
    }
}

/** Сигнал у дБм → 0..1 (Wi‑Fi від −90 до −40, мобільна від −120 до −70). */
private fun signalLevel(dbm: Int, type: NetworkType): Float = when {
    type == NetworkType.NONE -> 0f
    type == NetworkType.MOBILE -> ((dbm + 120) / 50f).coerceIn(0f, 1f)
    else -> ((dbm + 90) / 50f).coerceIn(0f, 1f)
}

@StringRes
private fun NetworkType.label(): Int = when (this) {
    NetworkType.NONE -> R.string.mon_net_none
    NetworkType.WIFI -> R.string.mon_net_wifi
    NetworkType.MOBILE -> R.string.mon_net_mobile
    NetworkType.ETHERNET -> R.string.mon_net_ethernet
    NetworkType.OTHER -> R.string.mon_net_other
}

@StringRes
private fun thermalLabel(status: Int): Int = when (status) {
    0 -> R.string.mon_thermal_0
    1 -> R.string.mon_thermal_1
    2 -> R.string.mon_thermal_2
    3 -> R.string.mon_thermal_3
    else -> R.string.mon_thermal_4
}

/** Стан батареї; «невідомо» не показуємо. */
private fun healthLabel(health: Int): Int? = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> R.string.mon_health_good
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.mon_health_hot
    BatteryManager.BATTERY_HEALTH_DEAD -> R.string.mon_health_dead
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.mon_health_voltage
    BatteryManager.BATTERY_HEALTH_COLD -> R.string.mon_health_cold
    else -> null
}

private const val REFRESH_MS = 2_000L
private const val USAGE_EVERY = 15
private const val HISTORY = 150
private const val TICKS = 60
private const val SEGMENTS = 20
private const val LONG_VALUE = 11
private const val CHARGE_WAVE_MS = 2_200
private const val WAVE_TAIL = 4f

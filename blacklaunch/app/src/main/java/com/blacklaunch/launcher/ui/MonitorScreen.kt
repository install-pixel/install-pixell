package com.blacklaunch.launcher.ui

import android.os.BatteryManager
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.settings.MonitorLook
import com.blacklaunch.launcher.settings.MonitorTile
import com.blacklaunch.launcher.system.NetworkType
import com.blacklaunch.launcher.system.SystemSnapshot
import com.blacklaunch.launcher.system.SystemStats
import com.blacklaunch.launcher.system.UsageInfo
import com.blacklaunch.launcher.ui.theme.Oled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val Mono = FontFamily.Monospace
private val Warm = Color(0xFFF2A541)
private val Track = Color(0xFF1A1E24)
private val Panel = Color(0xFF07090B)
private val Line = Color(0xFF1C2026)

/**
 * Монітор системи (етап 12b): велика шкала батареї з кільцями пам'яті й тепла, панелі, графіки, таблиця й топ програм.
 * Дані читаються раз на 2 с і лише поки екран на виду (RESUMED); використання — раз на 30 с.
 */
@Composable
internal fun MonitorScreen(look: MonitorLook, stats: SystemStats, appLabel: (String) -> String?, modifier: Modifier = Modifier) {
    val accent = Color(look.accent.argb)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var snap by remember { mutableStateOf<SystemSnapshot?>(null) }
    var usage by remember { mutableStateOf<UsageInfo?>(null) }
    val currentHistory = remember { mutableStateListOf<Float>() }
    val memoryHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(lifecycle, stats) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var tick = 0
            while (true) {
                val next = withContext(Dispatchers.IO) { stats.snapshot() }
                snap = next
                next.battery.currentMa?.let { currentHistory.push(it.toFloat()) }
                memoryHistory.push(1f - next.memory.availableBytes.toFloat() / next.memory.totalBytes.coerceAtLeast(1))
                if (MonitorTile.USAGE in look.tiles && tick % USAGE_EVERY == 0) usage = withContext(Dispatchers.IO) { stats.usage(appLabel) }
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
    val heat = s.thermal.headroom?.coerceIn(0f, 1f) ?: ((s.thermal.status ?: 0) / 6f)

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Верхній рядок: час роботи, мережа, тепловий стан.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.mon_title), color = Oled.TextSecondary, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                listOf(duration(s.uptimeMs), stringResource(s.network.type.label()), stringResource(thermalLabel(s.thermal.status))).joinToString(" · "),
                color = Oled.TextSecondary,
                fontSize = 11.sp,
                fontFamily = Mono,
            )
        }

        if (MonitorTile.BATTERY in look.tiles) {
            Box(Modifier.size(208.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                BatteryDial(s.battery.percent / 100f, memUsed, heat, accent, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.mon_battery), color = Oled.TextSecondary, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text("${s.battery.percent}%", color = Oled.TextPrimary, fontSize = 48.sp, fontWeight = FontWeight.ExtraLight, fontFamily = Mono)
                    s.battery.currentMa?.let { Text(signed(it) + " мА", color = accent, fontSize = 13.sp, fontFamily = Mono) }
                    Text(
                        listOfNotNull(
                            stringResource(if (s.battery.charging) R.string.mon_charging else R.string.mon_discharging),
                            s.battery.temperatureC?.let { "%.1f °C".format(it) },
                        ).joinToString(" · "),
                        color = Oled.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                    )
                }
            }
        }

        // Панелі 2×2.
        val panels = buildList {
            if (MonitorTile.MEMORY in look.tiles) {
                add(PanelData(R.string.mon_memory, size(s.memory.totalBytes - s.memory.availableBytes), stringResource(R.string.mon_of, size(s.memory.totalBytes)), memUsed, accent))
            }
            if (MonitorTile.STORAGE in look.tiles) {
                add(PanelData(R.string.mon_storage, size(s.storage.totalBytes - s.storage.freeBytes), stringResource(R.string.mon_free, size(s.storage.freeBytes)), storageUsed, accent))
            }
            if (MonitorTile.THERMAL in look.tiles) {
                add(
                    PanelData(
                        R.string.mon_thermal,
                        stringResource(thermalLabel(s.thermal.status)),
                        s.thermal.headroom?.let { stringResource(R.string.mon_headroom, ((1f - it.coerceIn(0f, 1f)) * 100).roundToInt()) }
                            ?: s.battery.temperatureC?.let { "%.1f °C".format(it) }.orEmpty(),
                        heat,
                        Warm,
                    ),
                )
            }
            if (MonitorTile.NETWORK in look.tiles) {
                add(
                    PanelData(
                        R.string.mon_network,
                        stringResource(s.network.type.label()),
                        listOfNotNull(s.network.signalDbm?.let { "$it дБм" }, s.network.downKbps?.let { "~${it / 1000} Мбіт/с" }).joinToString(" · "),
                        signalLevel(s.network.signalDbm, s.network.type),
                        accent,
                    ),
                )
            }
        }
        panels.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { PanelTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }

        // Живі графіки: струм батареї й зайнята пам'ять за останні хвилини.
        val graphs = listOfNotNull(
            Triple(R.string.mon_current, currentHistory, s.battery.currentMa?.let { signed(it) + " мА" }.orEmpty()).takeIf { MonitorTile.CURRENT_GRAPH in look.tiles && currentHistory.size > 1 },
            Triple(R.string.mon_memory_graph, memoryHistory, "${(memUsed * 100).roundToInt()}%").takeIf { MonitorTile.MEMORY_GRAPH in look.tiles && memoryHistory.size > 1 },
        )
        graphs.forEach { (label, history, value) ->
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(4.dp)).border(1.dp, Line, RoundedCornerShape(4.dp)).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(label), color = Oled.TextSecondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Text(value, color = accent, fontSize = 11.sp, fontFamily = Mono)
                }
                Sparkline(history, accent, Modifier.fillMaxWidth().height(36.dp))
            }
        }

        if (MonitorTile.DETAILS in look.tiles) {
            val facts = listOfNotNull(
                s.battery.voltageMv?.let { stringResource(R.string.mon_voltage) to "%.2f В".format(it / 1000f) },
                s.battery.cycles?.let { stringResource(R.string.mon_cycles) to "$it" },
                stringResource(R.string.mon_health) to stringResource(healthLabel(s.battery.health)),
                s.battery.toFullMs?.let { stringResource(R.string.mon_to_full) to duration(it) },
                stringResource(R.string.mon_pressure) to stringResource(if (s.memory.low) R.string.mon_yes else R.string.mon_no),
                stringResource(R.string.mon_uptime) to duration(s.uptimeMs),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                facts.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { (k, v) ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k, color = Oled.TextSecondary, fontSize = 11.sp, fontFamily = Mono)
                                Text(v, color = Oled.TextPrimary, fontSize = 11.sp, fontFamily = Mono)
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }

        if (MonitorTile.USAGE in look.tiles) {
            val u = usage
            if (u == null) {
                Text(stringResource(R.string.mon_usage_off), color = Oled.TextSecondary, fontSize = 11.sp)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.mon_screen_today, duration(u.screenMs)), color = Oled.TextPrimary, fontSize = 11.sp, fontFamily = Mono)
                    u.mobileBytes?.let { Text(stringResource(R.string.mon_data_today, size(it)), color = Oled.TextPrimary, fontSize = 11.sp, fontFamily = Mono) }
                }
                u.top.take(TOP_SHOWN).forEachIndexed { i, (name, ms) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}", color = Oled.TextSecondary, fontSize = 11.sp, fontFamily = Mono)
                        Text(name, color = Oled.TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(duration(ms), color = Oled.TextSecondary, fontSize = 11.sp, fontFamily = Mono)
                    }
                }
            }
        }
    }
}

private class PanelData(@StringRes val label: Int, val value: String, val detail: String, val level: Float, val color: Color)

@Composable
private fun PanelTile(p: PanelData, modifier: Modifier) {
    Column(
        modifier.background(Panel, RoundedCornerShape(4.dp)).border(1.dp, Line, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(p.label), color = Oled.TextSecondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(p.value, color = Oled.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Light, fontFamily = Mono, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(3.dp).background(Track, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(p.level.coerceIn(0f, 1f)).height(3.dp).background(p.color, RoundedCornerShape(2.dp)))
        }
        Text(p.detail, color = Oled.TextSecondary, fontSize = 10.sp, fontFamily = Mono, maxLines = 1)
    }
}

/** Шкала з поділками (батарея) і два тонкі кільця всередині (пам'ять, тепло). */
@Composable
private fun BatteryDial(battery: Float, memory: Float, heat: Float, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val lit = (battery * TICKS).roundToInt()
        repeat(TICKS) { i ->
            rotate(i * 360f / TICKS, c) {
                val long = i % 5 == 0
                drawLine(
                    color = if (i < lit) accent else Track,
                    start = Offset(c.x, c.y - r + 2.dp.toPx()),
                    end = Offset(c.x, c.y - r + (if (long) 16.dp else 12.dp).toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        fun ring(radius: Float, value: Float, color: Color) {
            val topLeft = Offset(c.x - radius, c.y - radius)
            val arc = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            drawArc(Track, -90f, 360f, false, topLeft, arc, style = Stroke(2.dp.toPx()))
            drawArc(color, -90f, 360f * value.coerceIn(0f, 1f), false, topLeft, arc, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        ring(r - 26.dp.toPx(), heat, Warm)
        ring(r - 34.dp.toPx(), memory, accent)
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (HISTORY - 1).toFloat()
            val y = size.height - (v - min) / span * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx()))
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

/** Сигнал у дБм → 0..1 (Wi‑Fi від −90 до −40, мобільна від −120 до −70); без даних — повна смуга, якщо мережа є. */
private fun signalLevel(dbm: Int?, type: NetworkType): Float = when {
    type == NetworkType.NONE -> 0f
    dbm == null -> 1f
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
private fun thermalLabel(status: Int?): Int = when (status) {
    null, 0 -> R.string.mon_thermal_0
    1 -> R.string.mon_thermal_1
    2 -> R.string.mon_thermal_2
    3 -> R.string.mon_thermal_3
    else -> R.string.mon_thermal_4
}

@StringRes
private fun healthLabel(health: Int): Int = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> R.string.mon_health_good
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.mon_health_hot
    BatteryManager.BATTERY_HEALTH_DEAD -> R.string.mon_health_dead
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.mon_health_voltage
    BatteryManager.BATTERY_HEALTH_COLD -> R.string.mon_health_cold
    else -> R.string.mon_health_unknown
}

private const val REFRESH_MS = 2_000L
private const val USAGE_EVERY = 15
private const val HISTORY = 150
private const val TICKS = 60
private const val TOP_SHOWN = 3

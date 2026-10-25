package com.blacklaunch.launcher.ui

import android.os.BatteryManager
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Приглушене тепле для тепла — у тон акцентів, не яскраве. */
private val Warm = Color(0xFFC9956A)
private val Track = Color(0x1FFFFFFF)
private val GlassShape = RoundedCornerShape(18.dp)

/** Наш шрифт, але цифри однакової ширини — щоб значення не «стрибали» щосекунди. */
private fun num(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size, color = color, fontWeight = weight, fontFeatureSettings = "tnum")

/**
 * Монітор системи: зверху шкала батареї з кільцями пам'яті й тепла, нижче «термінал» — сегментні смуги,
 * таблиця, графіки, топ програм і характеристики телефона. Дані — раз на 2 с і лише поки екран на виду.
 */
@Composable
internal fun MonitorScreen(look: MonitorLook, stats: SystemStats, appLabel: (String) -> String?, modifier: Modifier = Modifier) {
    val accent = Color(look.accent.argb)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var snap by remember { mutableStateOf<SystemSnapshot?>(null) }
    var usage by remember { mutableStateOf<UsageInfo?>(null) }
    var device by remember { mutableStateOf<DeviceInfo?>(null) }
    var deviceOpen by remember { mutableStateOf(false) }
    val currentHistory = remember { mutableStateListOf<Float>() }
    val memoryHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(lifecycle, stats) {
        if (device == null) device = withContext(Dispatchers.IO) { runCatching { stats.device() }.getOrNull() }
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
    val tiles = look.tiles

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mon_title), color = Oled.TextSecondary, fontSize = 11.sp, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(duration(s.uptimeMs), stringResource(s.network.type.label()), s.battery.temperatureC?.let { "%.1f °C".format(it) }).joinToString(" · "),
                    style = num(11.sp, Oled.TextSecondary),
                )
                // Характеристики — за кнопкою, щоб не займати місце на екрані.
                if (MonitorTile.DEVICE in tiles && device != null) {
                    Box(
                        Modifier.padding(start = 6.dp).size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { deviceOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("ⓘ", color = accent, fontSize = 18.sp)
                    }
                }
            }
        }

        if (MonitorTile.BATTERY in tiles) {
            Box(Modifier.size(176.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                BatteryDial(s.battery.percent / 100f, memUsed.takeIf { MonitorTile.MEMORY in tiles }, heat.takeIf { MonitorTile.THERMAL in tiles }, accent, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.battery.percent}%", style = num(42.sp, Oled.TextPrimary, FontWeight.ExtraLight))
                    s.battery.currentMa?.let { Text(signed(it) + " мА", style = num(13.sp, accent)) }
                    Text(
                        listOfNotNull(
                            stringResource(if (s.battery.charging) R.string.mon_charging else R.string.mon_discharging),
                            s.battery.temperatureC?.let { "%.1f °C".format(it) },
                        ).joinToString(" · "),
                        style = num(11.sp, Oled.TextSecondary),
                    )
                }
            }
        }

        // «Термінал»: сегментні смуги, як у htop.
        val bars = buildList {
            if (MonitorTile.BATTERY in tiles) add(BarRow("BAT", s.battery.percent / 100f, accent, "${s.battery.percent}%"))
            if (MonitorTile.MEMORY in tiles) add(BarRow("MEM", memUsed, accent, "${size(s.memory.totalBytes - s.memory.availableBytes)} / ${size(s.memory.totalBytes)}"))
            if (MonitorTile.STORAGE in tiles) add(BarRow("DSK", storageUsed, accent, "${size(s.storage.totalBytes - s.storage.freeBytes)} / ${size(s.storage.totalBytes)}"))
            if (MonitorTile.THERMAL in tiles) {
                // Тепло цифрами: температура батареї й запас до сповільнення (те, чого прошивка не дає, — не показуємо).
                val value = listOfNotNull(
                    s.battery.temperatureC?.let { "%.1f °C".format(it) },
                    s.thermal.headroom?.let { stringResource(R.string.mon_headroom, ((1f - it.coerceIn(0f, 1f)) * 100).roundToInt()) },
                ).joinToString(" · ")
                val level = s.thermal.headroom?.coerceIn(0f, 1f) ?: s.battery.temperatureC?.let { ((it - 20f) / 25f).coerceIn(0f, 1f) }
                if (value.isNotEmpty() && level != null) add(BarRow("THR", level, Warm, value))
            }
            if (MonitorTile.NETWORK in tiles) {
                add(
                    BarRow(
                        "NET",
                        s.network.signalDbm?.let { signalLevel(it, s.network.type) },
                        accent,
                        listOfNotNull(stringResource(s.network.type.label()), s.network.downKbps?.let { "~${it / 1000} Мбіт/с" }).joinToString(" · "),
                    ),
                )
            }
        }
        val facts = if (MonitorTile.DETAILS in tiles) {
            listOfNotNull(
                s.battery.voltageMv?.let { stringResource(R.string.mon_voltage) to "%.2f В".format(it / 1000f) },
                s.battery.cycles?.let { stringResource(R.string.mon_cycles) to "$it" },
                healthLabel(s.battery.health)?.let { stringResource(R.string.mon_health) to stringResource(it) },
                s.battery.toFullMs?.let { stringResource(R.string.mon_to_full) to duration(it) },
                stringResource(R.string.mon_pressure) to stringResource(if (s.memory.low) R.string.mon_yes else R.string.mon_no),
                s.network.signalDbm?.let { stringResource(R.string.mon_signal) to "$it дБм" },
            )
        } else {
            emptyList()
        }
        if (bars.isNotEmpty() || facts.isNotEmpty()) {
            Glass {
                bars.forEach { bar -> SegmentRow(bar) }
                if (bars.isNotEmpty() && facts.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(Track))
                facts.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { (k, v) ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k, color = Oled.TextSecondary, fontSize = 11.sp)
                                Text(v, style = num(11.sp, Oled.TextPrimary))
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }

        // Живі графіки в одній картці: струм батареї й зайнята пам'ять.
        val showCurrent = MonitorTile.CURRENT_GRAPH in tiles && currentHistory.size > 1
        val showMemory = MonitorTile.MEMORY_GRAPH in tiles && memoryHistory.size > 1
        if (showCurrent || showMemory) {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showCurrent) GraphCell(R.string.mon_current, s.battery.currentMa?.let { signed(it) + " мА" }.orEmpty(), currentHistory, accent, Modifier.weight(1f))
                    if (showMemory) GraphCell(R.string.mon_memory_graph, "${(memUsed * 100).roundToInt()}%", memoryHistory, accent.copy(alpha = 0.7f), Modifier.weight(1f))
                }
            }
        }

        if (MonitorTile.USAGE in tiles) {
            val u = usage
            if (u == null) {
                Text(stringResource(R.string.mon_usage_off), color = Oled.TextSecondary, fontSize = 11.sp)
            } else {
                Glass {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.mon_screen_today, duration(u.screenMs)), style = num(11.sp, Oled.TextPrimary))
                        u.mobileBytes?.let { Text(stringResource(R.string.mon_data_today, size(it)), style = num(11.sp, Oled.TextPrimary)) }
                    }
                    u.top.take(TOP_SHOWN).forEachIndexed { i, (name, ms) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}", style = num(11.sp, Oled.TextSecondary))
                            Text(name, color = Oled.TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(duration(ms), style = num(11.sp, Oled.TextSecondary))
                        }
                    }
                }
            }
        }

        val d = device
        if (deviceOpen && d != null) {
            AlertDialog(
                onDismissRequest = { deviceOpen = false },
                containerColor = Color(0xFF141414),
                title = { Text(d.model, color = Oled.TextPrimary, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val rows = listOfNotNull(
                            d.soc?.let { stringResource(R.string.mon_chip) to it },
                            stringResource(R.string.mon_cpu) to stringResource(R.string.mon_cores, d.cores),
                            stringResource(R.string.mon_screen) to listOfNotNull(d.screenPx, d.screenInches?.let { "%.1f″".format(it) }, d.refreshHz?.let { "$it Гц" }).joinToString(" · "),
                            stringResource(R.string.mon_memory_total) to "${size(d.ramBytes)} · ${size(d.storageBytes)}",
                            d.cameras.takeIf { it.isNotEmpty() }?.let { cams ->
                                stringResource(R.string.mon_cameras) to cams.sortedBy { it.first }.joinToString(", ") { (front, mp) ->
                                    "%.0f".format(mp) + " Мп" + if (front) " ↺" else ""
                                }
                            },
                            stringResource(R.string.mon_android) to d.android,
                            d.securityPatch?.let { stringResource(R.string.mon_patch) to it },
                            d.kernel?.let { stringResource(R.string.mon_kernel) to it },
                        )
                        rows.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(k, color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(v, style = num(13.sp, Oled.TextPrimary), modifier = Modifier.weight(1.4f))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { deviceOpen = false }) { Text(stringResource(R.string.close), color = Oled.TextPrimary) } },
            )
        }
    }
}

/** [level] null — смуги немає (прошивка не дає значення), лише текст. */
private class BarRow(val label: String, val level: Float?, val color: Color, val value: String)

/** Скляна картка, як у налаштуваннях Black Glass. */
@Composable
private fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .background(Oled.Surface)
            .border(1.dp, Oled.Glint, GlassShape)
            .then(modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun SegmentRow(bar: BarRow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(bar.label, color = Oled.TextSecondary, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.width(30.dp))
        val level = bar.level
        if (level == null) {
            Box(Modifier.weight(1f))
        } else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val lit = (level.coerceIn(0f, 1f) * SEGMENTS).roundToInt()
                // Скляний сегмент: світліший відблиск зверху, колір знизу.
                val glass = Brush.verticalGradient(listOf(lerp(bar.color, Color.White, 0.45f), bar.color, bar.color.copy(alpha = 0.6f)))
                repeat(SEGMENTS) { i ->
                    Box(
                        Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(2.dp))
                            .then(if (i < lit) Modifier.background(glass) else Modifier.background(Track)),
                    )
                }
            }
        }
        Text(bar.value, style = num(11.sp, Oled.TextPrimary), maxLines = 1, modifier = Modifier.width(112.dp))
    }
}

@Composable
private fun GraphCell(@StringRes label: Int, value: String, history: List<Float>, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), color = Oled.TextSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(value, style = num(11.sp, color))
        }
        Sparkline(history, color, Modifier.fillMaxWidth().height(30.dp))
    }
}

/** Шкала з поділками (батарея) і тонкі кільця всередині (пам'ять, тепло — якщо ввімкнені). */
@Composable
private fun BatteryDial(battery: Float, memory: Float?, heat: Float?, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val lit = (battery * TICKS).roundToInt()
        repeat(TICKS) { i ->
            // Неон: тонка світла лінія над м'яким світінням; колір плавно світлішає вздовж кола.
            val tint = lerp(accent, Color.White, 0.35f * i / TICKS)
            rotate(i * 360f / TICKS, c) {
                val start = Offset(c.x, c.y - r + 3.dp.toPx())
                val end = Offset(c.x, c.y - r + (if (i % 5 == 0) 13.dp else 9.dp).toPx())
                if (i < lit) drawLine(tint.copy(alpha = 0.22f), start, end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(if (i < lit) tint else Track, start, end, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        fun ring(radius: Float, value: Float, color: Color) {
            val topLeft = Offset(c.x - radius, c.y - radius)
            val arc = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            val sweep = 360f * value.coerceIn(0f, 1f)
            drawArc(Track, -90f, 360f, false, topLeft, arc, style = Stroke(1.dp.toPx()))
            drawArc(color.copy(alpha = 0.2f), -90f, sweep, false, topLeft, arc, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.5f), color, lerp(color, Color.White, 0.4f)), center = c),
                -90f, sweep, false, topLeft, arc, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        heat?.let { ring(r - 22.dp.toPx(), it, Warm) }
        memory?.let { ring(r - 29.dp.toPx(), it, accent.copy(alpha = 0.7f)) }
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
            // На всю ширину одразу, а не лише коли назбирається 5 хвилин.
            val x = size.width * i / (values.size - 1).toFloat()
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
private const val TOP_SHOWN = 3

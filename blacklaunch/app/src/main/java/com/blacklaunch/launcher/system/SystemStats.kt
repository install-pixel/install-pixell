package com.blacklaunch.launcher.system

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import java.util.Calendar
import kotlin.math.abs

/** Батарея: усе, що Android дає офіційно. Невідоме — null. */
data class BatteryStats(
    val percent: Int,
    val charging: Boolean,
    /** Струм зараз, мА: мінус — розряд, плюс — заряд. */
    val currentMa: Int?,
    val voltageMv: Int?,
    val temperatureC: Float?,
    val health: Int,
    /** Цикли заряду (Android 14+). */
    val cycles: Int?,
    /** Скільки до повного заряду, мс (лише під час заряджання, Android 9+). */
    val toFullMs: Long?,
)

data class MemoryStats(val totalBytes: Long, val availableBytes: Long, val low: Boolean)

data class StorageStats(val totalBytes: Long, val freeBytes: Long)

/** Тепло: стан Android (0 — норма … 6 — вимкнення) і запас до сповільнення (1.0 — сповільнення почнеться). */
data class ThermalStats(val status: Int?, val headroom: Float?)

data class NetworkStatsInfo(val type: NetworkType, val signalDbm: Int?, val downKbps: Int?)

enum class NetworkType { NONE, WIFI, MOBILE, ETHERNET, OTHER }

/** З «Доступом до використання»: час екрана й мобільні дані сьогодні, топ програм за часом. */
data class UsageInfo(val screenMs: Long, val mobileBytes: Long?, val top: List<Pair<String, Long>>)

/** Характеристики телефона — офіційно доступне (типу пам'яті й заводської ємності батареї Android не дає). */
data class DeviceInfo(
    val model: String,
    /** Чип: «Qualcomm SM8750» (Android 12+), інакше null. */
    val soc: String?,
    val cores: Int,
    /** Кластери ядер: кількість і найвища частота, МГц (з офіційного sysfs; не прочиталось — порожньо). */
    val clusters: List<Pair<Int, Int>>,
    val android: String,
    val securityPatch: String?,
    val kernel: String?,
    val screenPx: String,
    val screenInches: Float?,
    val refreshHz: Int?,
    /** Камери, які телефон відкриває програмам: напрямок (true — фронтальна) і мегапікселі на повній роздільності. */
    val cameras: List<Pair<Boolean, Float>>,
    val ramBytes: Long,
    val storageBytes: Long,
)

data class SystemSnapshot(
    val battery: BatteryStats,
    val memory: MemoryStats,
    val storage: StorageStats,
    val thermal: ThermalStats,
    val network: NetworkStatsInfo,
    val uptimeMs: Long,
)

/**
 * Читає стан телефона офіційними API — лише коли монітор на екрані (раз на 1–2 с; використання — рідше).
 * Без root і без системних файлів: точної температури процесора чи завантаження ядер тут немає — Android їх закрив.
 */
class SystemStats(context: Context) {
    private val app = context.applicationContext
    private val batteryManager = app.getSystemService(BatteryManager::class.java)
    private val activityManager = app.getSystemService(ActivityManager::class.java)
    private val powerManager = app.getSystemService(PowerManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    fun snapshot(): SystemSnapshot = SystemSnapshot(battery(), memory(), storage(), thermal(), network(), SystemClock.elapsedRealtime())

    private fun battery(): BatteryStats {
        // Липке системне повідомлення — останній стан батареї, без підписки й без фонової роботи.
        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else {
            null
        }
        val toFull = if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager?.computeChargeTimeRemaining()?.takeIf { it > 0 }
        } else {
            null
        }
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        // Знак струму прошивки віддають по-різному (OnePlus — навпаки): вирівнюємо за станом заряду.
        val current = currentMa()?.let { ma -> if (charging) abs(ma) else -abs(ma) }
        return BatteryStats(percent, charging, current, voltage, temperature, health, cycles, toFull)
    }

    /** Android віддає мікроампери, але деякі прошивки — міліампери; великі значення — це мкА. */
    private fun currentMa(): Int? {
        val raw = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: return null
        if (raw == Int.MIN_VALUE || raw == 0) return null
        return if (abs(raw) > MICROAMP_THRESHOLD) raw / 1000 else raw
    }

    private fun memory(): MemoryStats {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)
        return MemoryStats(info.totalMem, info.availMem, info.lowMemory)
    }

    private fun storage(): StorageStats {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageStats(stat.totalBytes, stat.availableBytes)
    }

    private fun thermal(): ThermalStats {
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager?.currentThermalStatus else null
        // Запас Android дозволяє питати не частіше, ніж раз на секунду, — інакше NaN.
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            powerManager?.getThermalHeadroom(HEADROOM_FORECAST_S)?.takeIf { !it.isNaN() }
        } else {
            null
        }
        return ThermalStats(status, headroom)
    }

    private fun network(): NetworkStatsInfo {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return NetworkStatsInfo(NetworkType.NONE, null, null)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
        val generic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        } else {
            null
        }
        // Деякі прошивки (OxygenOS) загальний сигнал не дають — беремо з Wi‑Fi чи мобільної мережі напряму.
        val signal = generic ?: when (type) {
            NetworkType.WIFI -> wifiRssi()
            NetworkType.MOBILE -> mobileDbm()
            else -> null
        }
        return NetworkStatsInfo(type, signal, caps.linkDownstreamBandwidthKbps.takeIf { it > 0 })
    }

    /** Характеристики — читаються раз, при відкритті монітора. */
    fun device(): DeviceInfo {
        val model = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ")
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").ifBlank { null }
        } else {
            null
        }
        val metrics = app.resources.displayMetrics
        val display = app.getSystemService(android.hardware.display.DisplayManager::class.java)?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        // Справжня роздільність панелі — з найбільшого режиму дисплея (вікно програми може бути меншим).
        val mode = display?.supportedModes?.maxByOrNull { it.physicalWidth * it.physicalHeight }
        val w = mode?.physicalWidth ?: metrics.widthPixels
        val h = mode?.physicalHeight ?: metrics.heightPixels
        val inches = if (metrics.xdpi > 0 && metrics.ydpi > 0) {
            kotlin.math.sqrt((w / metrics.xdpi).let { it * it } + (h / metrics.ydpi).let { it * it })
        } else {
            null
        }
        val hz = display?.supportedModes?.maxOfOrNull { it.refreshRate }?.let { kotlin.math.round(it).toInt() }
        val cameras = runCatching {
            val manager = app.getSystemService(android.hardware.camera2.CameraManager::class.java)
            // Логічна камера може складатися з кількох фізичних (основна, ширококутна, теле) — беремо й їх.
            val ids = manager.cameraIdList.flatMap { id ->
                val physical = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.getCameraCharacteristics(id).physicalCameraIds else emptySet()
                if (physical.isEmpty()) listOf(id) else physical.toList()
            }.distinct()
            ids.mapNotNull { id ->
                val c = manager.getCameraCharacteristics(id)
                // Сенсори 50 Мп звично звітують «об'єднану» роздільність (12,5 Мп); повна — окремим ключем (Android 12+).
                val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    c.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION)
                } else {
                    null
                }
                val size = full ?: c.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return@mapNotNull null
                val front = c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                front to size.width * size.height / 1_000_000f
            }
        }.getOrDefault(emptyList())
        val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH.ifBlank { null } else null
        return DeviceInfo(
            model, soc?.let { socName(it) }, Runtime.getRuntime().availableProcessors(), clusters(), Build.VERSION.RELEASE, patch,
            System.getProperty("os.version")?.substringBefore('-'),
            "${maxOf(w, h)}×${minOf(w, h)}", inches, hz, cameras, memory().totalBytes, storage().totalBytes,
        )
    }

    /** «QTI SM8750» → «Snapdragon 8 Elite (SM8750)» для відомих чипів, інакше як дає Android. */
    private fun socName(soc: String): String {
        val code = soc.substringAfterLast(' ')
        return SOC_NAMES[code]?.let { "$it ($code)" } ?: soc
    }

    /** Ядра, згруповані за найвищою частотою: [(2, 4320), (6, 3530)]. */
    private fun clusters(): List<Pair<Int, Int>> = runCatching {
        val cores = Runtime.getRuntime().availableProcessors()
        val max = (0 until cores).mapNotNull { cpu ->
            java.io.File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }?.readText()?.trim()?.toIntOrNull()?.div(1000)
        }
        // Неповні дані не показуємо.
        if (max.size != cores) return@runCatching emptyList()
        max.groupingBy { it }.eachCount().map { (mhz, count) -> count to mhz }.sortedByDescending { it.second }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun wifiRssi(): Int? = runCatching {
        val fromNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivity?.getNetworkCapabilities(connectivity.activeNetwork)?.transportInfo as? android.net.wifi.WifiInfo
        } else {
            null
        }
        val info = fromNetwork ?: app.getSystemService(android.net.wifi.WifiManager::class.java)?.connectionInfo
        info?.rssi?.takeIf { it in -127..-1 }
    }.getOrNull()

    @android.annotation.SuppressLint("MissingPermission")
    private fun mobileDbm(): Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            app.getSystemService(android.telephony.TelephonyManager::class.java)?.signalStrength
                ?.cellSignalStrengths?.firstOrNull()?.dbm?.takeIf { it in -140..-1 }
        }.getOrNull()
    } else {
        null
    }

    /** Чи дано «Доступ до використання» (дає користувач у налаштуваннях Android). */
    fun hasUsageAccess(): Boolean {
        val ops = app.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Сьогодні з півночі. [label] — назва програми за пакетом. */
    fun usage(label: (String) -> String?): UsageInfo? {
        if (!hasUsageAccess()) return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val usage = app.getSystemService(UsageStatsManager::class.java) ?: return null
        val perApp = runCatching { usage.queryAndAggregateUsageStats(start, now) }.getOrNull().orEmpty()
            .filterKeys { it != app.packageName }
            .mapValues { it.value.totalTimeInForeground }
            .filterValues { it > 0 }
        val top = perApp.entries.sortedByDescending { it.value }.take(TOP_APPS).map { (pkg, ms) -> (label(pkg) ?: pkg) to ms }
        val mobile = runCatching {
            val stats = app.getSystemService(NetworkStatsManager::class.java)
            @Suppress("DEPRECATION")
            val bucket: NetworkStats.Bucket? = stats?.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, now)
            bucket?.let { it.rxBytes + it.txBytes }
        }.getOrNull()
        return UsageInfo(perApp.values.sum(), mobile, top)
    }

    private companion object {
        const val MICROAMP_THRESHOLD = 20_000
        const val HEADROOM_FORECAST_S = 10
        const val TOP_APPS = 5
        val SOC_NAMES = mapOf(
            "SM8850" to "Snapdragon 8 Elite Gen 5", "SM8750" to "Snapdragon 8 Elite", "SM8650" to "Snapdragon 8 Gen 3",
            "SM8550" to "Snapdragon 8 Gen 2", "SM8475" to "Snapdragon 8+ Gen 1", "SM8450" to "Snapdragon 8 Gen 1",
            "SM8350" to "Snapdragon 888", "SM8250" to "Snapdragon 865", "SM7675" to "Snapdragon 7+ Gen 3",
            "SM7550" to "Snapdragon 7 Gen 3", "SM6450" to "Snapdragon 6 Gen 1",
        )
    }
}

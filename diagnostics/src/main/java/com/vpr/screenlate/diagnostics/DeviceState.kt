package com.vpr.screenlate.diagnostics

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.view.Display
import android.webkit.WebView
import androidx.core.content.ContextCompat

/** What the device and the system are doing: thermal state, power saving, battery, network, storage. */
internal object DeviceState {
    /** Once per process start: the device, the system and the app. */
    fun identity(context: Context): Map<String, Any?> {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return linkedMapOf(
            "app" to info.versionName,
            "code" to info.longVersionCode,
            "package" to context.packageName,
            "maker" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT,
            "patch" to Build.VERSION.SECURITY_PATCH,
            "display_build" to Build.DISPLAY,
            "abis" to Build.SUPPORTED_ABIS.toList(),
            "cores" to Runtime.getRuntime().availableProcessors(),
            "ram_mb" to (memory.totalMem shr 20),
            "memory_class_mb" to manager.memoryClass,
            "large_memory_class_mb" to manager.largeMemoryClass,
            "low_ram" to manager.isLowRamDevice,
            "screen" to "${metrics.widthPixels}x${metrics.heightPixels}",
            "density" to metrics.density,
            "font_scale" to context.resources.configuration.fontScale,
            "refresh_hz" to display?.refreshRate,
            "refresh_modes" to display?.supportedModes?.map { it.refreshRate }?.distinct(),
            "webview" to runCatching { WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } }.getOrNull(),
            "locale" to context.resources.configuration.locales[0].toLanguageTag(),
        )
    }

    /** Cheap: for every scan. */
    fun brief(context: Context): Map<String, Any?> {
        val power = context.getSystemService(PowerManager::class.java)
        return linkedMapOf(
            "thermal" to power.currentThermalStatus,
            "power_save" to power.isPowerSaveMode,
            "net" to network(context),
        )
    }

    /** For the periodic sample. */
    fun full(context: Context): Map<String, Any?> {
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val activities = context.getSystemService(ActivityManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val data = StatFs(Environment.getDataDirectory().path)
        return linkedMapOf(
            "thermal" to power.currentThermalStatus,
            "headroom" to power.getThermalHeadroom(HEADROOM_SECONDS),
            "power_save" to power.isPowerSaveMode,
            "interactive" to power.isInteractive,
            "doze" to power.isDeviceIdleMode,
            "battery_exempt" to power.isIgnoringBatteryOptimizations(context.packageName),
            "battery_pct" to battery?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) level * 100 / scale else null
            },
            "charging" to battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 },
            "battery_temp_c" to battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 },
            "net" to network(context),
            "data_saver" to connectivity.restrictBackgroundStatus,
            "standby_bucket" to runCatching { usage.appStandbyBucket }.getOrNull(),
            "background_restricted" to activities.isBackgroundRestricted,
            "service_enabled" to serviceEnabled(context),
            "free_mb" to (data.availableBytes shr 20),
        )
    }

    /** Whether the user's accessibility settings list the app's service (on, though maybe not running). */
    fun serviceEnabled(context: Context): Boolean? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.startsWith("${context.packageName}/") }
    }.getOrNull()

    /** `wifi`, `cell`, `vpn+wifi`, …, with `metered` and `unvalidated` where they apply; `none` offline. */
    fun network(context: Context): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return "none"
        return describe(capabilities)
    }

    private fun describe(capabilities: NetworkCapabilities): String = buildList {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> add("wifi")
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> add("cell")
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> add("eth")
        }
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) add("metered")
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add("unvalidated")
        add("${capabilities.linkDownstreamBandwidthKbps}kbps")
    }.joinToString("+").ifEmpty { "other" }

    /** Records changes of the thermal state, power saving, the screen and the default network as events. */
    fun watch(context: Context, handler: Handler) {
        val power = context.getSystemService(PowerManager::class.java)
        power.addThermalStatusListener(handler::post) { status -> Diag.event("thermal", "status" to status) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> Diag.event("power_save", "on" to power.isPowerSaveMode)
                    Intent.ACTION_SCREEN_ON -> Diag.event("screen", "on" to true)
                    Intent.ACTION_SCREEN_OFF -> Diag.event("screen", "on" to false)
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> Diag.event("doze", "on" to power.isDeviceIdleMode)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                private var last: String? = null

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    // The bandwidth estimate changes all the time; only the kind of network counts as a change.
                    val kind = describe(capabilities).substringBeforeLast('+')
                    if (kind == last) return
                    last = kind
                    Diag.event("network", "net" to describe(capabilities))
                }

                override fun onLost(network: Network) {
                    last = null
                    Diag.event("network", "net" to "none")
                }
            },
            handler,
        )
    }

    private const val HEADROOM_SECONDS = 10
}

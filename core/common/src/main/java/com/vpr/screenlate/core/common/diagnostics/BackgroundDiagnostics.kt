package com.vpr.screenlate.core.common.diagnostics

import android.app.Activity
import android.app.Application
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Bundle
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.vpr.screenlate.core.common.log.Log
import java.io.File

/** Dev-only, bounded journal diagnostics. Uses no alarms, wake locks, network or recognized content. */
object BackgroundDiagnostics {
    private const val TAG = "BackgroundDiagnostics"
    private const val PERIOD_MS = 60_000L
    private val lock = Any()
    private var state = BackgroundDiagnosticState()
    @Volatile private var monitor: Monitor? = null

    val isEnabled: Boolean get() = monitor != null

    fun install(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        synchronized(lock) {
            if (monitor != null) return
            monitor = Monitor(context.applicationContext)
        }
        monitor?.start()
    }

    fun update(event: String, change: (BackgroundDiagnosticState) -> BackgroundDiagnosticState) {
        val active = monitor ?: return
        val snapshot = synchronized(lock) {
            val next = change(state)
            if (next == state) return
            state = next
            next
        }
        active.event(event, snapshot)
    }

    /** Names are fixed call-site labels, never exception messages or application content. */
    fun event(event: String) {
        val active = monitor ?: return
        active.event(event, synchronized(lock) { state })
    }

    /** Called by log export on its IO dispatcher; places the sample before the journal's export barrier. */
    fun sampleForExport() {
        monitor?.sampleForExport()
    }

    private class Monitor(private val context: Context) {
        private val thread = HandlerThread("BackgroundDiagnostics", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        private val handler = Handler(thread.looper)
        private val gate = DiagnosticSampleGate()
        private var previousCpu: DiagnosticCpuPoint? = null
        private val power = context.getSystemService(PowerManager::class.java)
        private val activity = context.getSystemService(ActivityManager::class.java)
        private val periodic = object : Runnable {
            override fun run() {
                sample("periodic")
                handler.postDelayed(this, PERIOD_MS)
            }
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> "screen_off"
                    Intent.ACTION_SCREEN_ON -> "screen_on"
                    Intent.ACTION_USER_PRESENT -> "user_present"
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> "device_idle_changed"
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> "power_save_changed"
                    Intent.ACTION_POWER_CONNECTED -> "power_connected"
                    Intent.ACTION_POWER_DISCONNECTED -> "power_disconnected"
                    else -> return
                }
                event(name, synchronized(lock) { state })
            }
        }

        fun start() {
            (context as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                private var resumed = 0
                override fun onActivityResumed(activity: Activity) {
                    resumed++
                    update("activity_resumed") { it.copy(activityResumed = true) }
                }
                override fun onActivityPaused(activity: Activity) {
                    resumed = (resumed - 1).coerceAtLeast(0)
                    update("activity_paused") { it.copy(activityResumed = resumed > 0) }
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else context.registerReceiver(receiver, filter)
            }.onFailure { Log.w(TAG, "receiver_registration_failed type=${it.javaClass.simpleName}") }
            event("monitor_started", synchronized(lock) { state })
            handler.postDelayed(periodic, PERIOD_MS)
        }

        fun event(name: String, snapshot: BackgroundDiagnosticState) {
            val elapsed = SystemClock.elapsedRealtime()
            val uptime = SystemClock.uptimeMillis()
            handler.post {
                Log.i(TAG, "event=$name elapsedMs=$elapsed uptimeMs=$uptime ${snapshot.describe()} ${systemState()}")
                sample("event_$name")
            }
        }

        fun sampleForExport() {
            val completed = java.util.concurrent.CountDownLatch(1)
            handler.post {
                try { sample("export", force = true) } finally { completed.countDown() }
            }
            // A diagnostic worker must never prevent exporting a log if the platform memory query stalls.
            completed.await(2, java.util.concurrent.TimeUnit.SECONDS)
        }

        private fun sample(reason: String, force: Boolean = false) {
            val elapsed = SystemClock.elapsedRealtime()
            if (!force && !gate.admit(elapsed)) return
            val started = SystemClock.uptimeMillis()
            runCatching {
                val snapshot = ProcessDiagnostics.snapshot()
                val cpu = DiagnosticCpuPoint(elapsed, started, snapshot.cpuTimeMs)
                val interval = cpuInterval(previousCpu, cpu)
                previousCpu = cpu
                val memory = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }
                val process = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
                val proc = runCatching { File("/proc/self/status").readLines() }.getOrDefault(emptyList())
                fun statusValue(key: String): String = proc.firstOrNull { it.startsWith("$key:") }
                    ?.substringAfter(':')?.trim()?.substringBefore(' ') ?: "unavailable"
                val fds = runCatching { File("/proc/self/fd").list()?.size }.getOrNull()
                val counters = "rssKb=${statusValue("VmRSS")} threads=${statusValue("Threads")} fds=${fds ?: "unavailable"}"
                val current = synchronized(lock) { state }
                Log.i(TAG, "sample=$reason pid=${Process.myPid()} elapsedMs=$elapsed uptimeMs=$started " +
                    "cpuTotalMs=${snapshot.cpuTimeMs} $interval importance=${process.importance} lifecycle=${snapshot.state} " +
                    "pssMb=${snapshot.totalPssMb} dalvikPssMb=${snapshot.dalvikPssMb} nativePssMb=${snapshot.nativePssMb} " +
                    "otherPssMb=${snapshot.otherPssMb} nativeAllocatedMb=${snapshot.nativeAllocatedMb} " +
                    "heapMb=${snapshot.heapUsedMb}/${snapshot.heapMaxMb} $counters " +
                    "systemAvailableMb=${memory.availMem / 1048576} systemLowMemory=${memory.lowMemory} " +
                    "systemThresholdMb=${memory.threshold / 1048576} trim=${snapshot.lastTrim?.level ?: "none"} " +
                    "${current.describe()} ${systemState()} sampleCostMs=${SystemClock.uptimeMillis() - started}")
                if (Build.VERSION.SDK_INT >= 30) {
                    // Android retains at most 128 bytes alongside this process's historical exit record.
                    val summary = "v1 e=$elapsed s=${current.serviceConnected} f=${current.foregroundService} " +
                        "d=${current.docked} p=${current.popupVisible} c=${current.catcherActive} scan=${current.scanSequence}"
                    activity.setProcessStateSummary(summary.take(128).toByteArray(Charsets.US_ASCII))
                }
            }.onFailure { Log.w(TAG, "sample_failed type=${it.javaClass.simpleName}") }
        }

        private fun systemState(): String = runCatching {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').any { it.substringBefore('/') == context.packageName }
            val bucket = if (Build.VERSION.SDK_INT >= 28) {
                context.getSystemService(UsageStatsManager::class.java).appStandbyBucket.toString()
            } else "unsupported"
            val restricted = if (Build.VERSION.SDK_INT >= 28) activity.isBackgroundRestricted.toString() else "unsupported"
            "interactive=${power.isInteractive} deviceIdle=${power.isDeviceIdleMode} powerSave=${power.isPowerSaveMode} " +
                "batteryExempt=${power.isIgnoringBatteryOptimizations(context.packageName)} backgroundRestricted=$restricted " +
                "standbyBucket=$bucket accessibilityEnabled=$enabled " +
                "charging=${context.getSystemService(BatteryManager::class.java).isCharging} " +
                "notifications=${context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()}"
        }.getOrElse { "systemState=unavailable type=${it.javaClass.simpleName}" }
    }
}

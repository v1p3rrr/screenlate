package com.vpr.screenlate.diagnostics

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.Trace
import java.io.File
import okhttp3.EventListener

/**
 * TEMPORARY DIAGNOSTICS BUILD. The one entry point of the diagnostics that the owner runs on the phone for a while and
 * hands over for analysis; everything here and every call to it is removed afterwards (`ai/notes/diagnostics.md`).
 *
 * Records events as JSON lines in rotating files in the app's no-backup storage, copies the app's logcat next to them,
 * and adds `android.os.Trace` sections for system tracing. Exported with the logs (About → Logs). Calls before
 * [start] (unit tests) do nothing.
 *
 * Privacy as for logs: fields are numbers, identifiers and states; never recognized text, looked-up words, note
 * contents or URLs with terms.
 */
@SuppressLint("StaticFieldLeak")
object Diag {
    private const val TAG_PREFIX = "SL:"
    private const val EVENT_FILE_BYTES = 2L shl 20
    private const val EVENT_FILES = 8
    private const val SAMPLE_PERIOD_MS = 15 * 60_000L
    private const val FULL_MEMORY_MIN_GAP_MS = 60_000L
    private const val SCAN_TIMEOUT_MS = 30_000L
    private const val STALL_MS = 48L
    private const val STALL_EVENT_MS = 150L
    private const val SUMMARY_DELAY_MS = 1_000L

    @Volatile
    private var recorder: Recorder? = null
    // The application, which lives as long as the process.
    @SuppressLint("StaticFieldLeak")
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var handler: Handler
    private var tee: LogcatTee? = null

    private val lock = Any()
    private var scan: ScanTrace? = null
    private var scanCount = 0

    @Volatile
    private var currentScanId = 0
    private var lastFullMemory = 0L
    private val summary = LinkedHashMap<String, String>()
    private val counters = Counters()
    private var summaryPending = false

    /** First thing in `Application.onCreate`, before Hilt builds anything. */
    fun start(application: Application) {
        // One process records; another one would stop its logcat copy.
        if (recorder != null || Application.getProcessName() != application.packageName) return
        context = application
        directory = File(application.noBackupFilesDir, "diagnostics").apply { mkdirs() }
        val startedFile = File(directory, "started-at.txt")
        if (!startedFile.exists()) startedFile.writeText(System.currentTimeMillis().toString())
        recorder = Recorder(RotatingLog(File(directory, "events"), "events", EVENT_FILE_BYTES, EVENT_FILES))
        handler = Handler(HandlerThread("diagnostics", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }.looper)
        event("process", *ProcessInfo.start().toPairs())
        tee = LogcatTee(File(directory, "logcat")).also { tee -> handler.post { runCatching { tee.start() } } }
        catchCrashes()
        Looper.getMainLooper().setMessageLogging(MainThreadWatch(STALL_MS) { ms, what -> mainStall(ms, what) })
        application.registerActivityLifecycleCallbacks(FrameWatch(handler))
        application.registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    event("trim_memory", "level" to level, *Memory.cheap().toPairs())
                    memory("trim", full = true)
                }

                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = event("low_memory")
            },
        )
        ProcessInfo.watchStart(application)
        handler.post {
            runCatching { event("device", *DeviceState.identity(application).toPairs()) }
            runCatching { ProcessInfo.exits(application, directory, File(directory, "traces")) }
            runCatching { DeviceState.watch(application, handler) }
            sample()
        }
    }

    /** Receives every event in unit tests, where [start] is not called. */
    @Volatile
    internal var sink: ((name: String, fields: Array<out Pair<String, Any?>>) -> Unit)? = null

    /** One record; see the class description for what may go into [fields]. */
    fun event(name: String, vararg fields: Pair<String, Any?>) {
        sink?.invoke(name, fields)
        val recorder = recorder ?: return
        recorder.offer(DiagJson.line(System.currentTimeMillis(), SystemClock.elapsedRealtime(), name, fields))
    }

    /** A section of a system trace (Developer options → System tracing) on the current thread. */
    inline fun <T> trace(name: String, block: () -> T): T {
        Trace.beginSection("SL:$name")
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    /** A trace section that may end on another thread, e.g. across a suspension; [cookie] tells overlapping ones apart. */
    fun beginAsync(name: String, cookie: Int) = Trace.beginAsyncSection(TAG_PREFIX + name, cookie)

    fun endAsync(name: String, cookie: Int) = Trace.endAsyncSection(TAG_PREFIX + name, cookie)

    /** [block]'s time in ms with its result. */
    inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val started = SystemClock.elapsedRealtime()
        val result = block()
        return result to SystemClock.elapsedRealtime() - started
    }

    // region Scans

    /** Opens the summary of a new scan (closing an open one) and returns its number, which other events carry. */
    fun scanBegin(trigger: String, vararg fields: Pair<String, Any?>): Int {
        if (recorder == null) return 0
        val now = SystemClock.elapsedRealtime()
        val trace = synchronized(lock) {
            scan?.let { close(it, "next_scan", now) }
            scanCount++
            ScanTrace(scanCount, now, linkedMapOf("trigger" to trigger, *fields)).also {
                scan = it
                currentScanId = it.id
            }
        }
        trace.cpuBefore = Process.getElapsedCpuTime()
        beginAsync("scan", trace.id)
        state("scan", "${trace.id}:open")
        // The device state takes a call to the system and the memory a read of /proc: not on the scan's thread.
        handler.post {
            runCatching { DeviceState.brief(context) }.getOrNull()?.forEach { (key, value) -> trace.note(key, value) }
            Memory.cheap().forEach { (key, value) -> trace.note("before_$key", value) }
        }
        handler.postDelayed({ scanEnd("timeout", trace.id) }, SCAN_TIMEOUT_MS)
        return trace.id
    }

    /** A stage of the open scan reached now (`capture_start`, `popup_shown`, …). */
    fun scanMark(stage: String) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) { scan?.mark(stage, now) }
    }

    /** A value for the open scan's summary. */
    fun scanNote(key: String, value: Any?) {
        synchronized(lock) { scan?.note(key, value) }
    }

    /** A recognition result the overlay received. */
    fun scanUpdate(kind: String, engine: String, paragraphs: Int) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) { scan?.update(kind, engine, paragraphs, now) }
    }

    /** Ends the open scan's summary; with [id], only that scan. */
    fun scanEnd(reason: String, id: Int? = null) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val open = scan ?: return
            if (id != null && open.id != id) return
            close(open, reason, now)
        }
    }

    /** A repeated step of the open scan, summed up in its `stats`. */
    fun scanStat(name: String, ms: Long) {
        synchronized(lock) { scan?.stat(name, ms) }
    }

    /** The number of the open scan, 0 when none is open. */
    fun currentScan(): Int = currentScanId

    /** Under [lock]. The summary is written on the diagnostics thread, which reads the memory first. */
    private fun close(trace: ScanTrace, reason: String, now: Long) {
        scan = null
        currentScanId = 0
        endAsync("scan", trace.id)
        trace.note("cpu_ms", Process.getElapsedCpuTime() - trace.cpuBefore)
        handler.post {
            Memory.cheap().forEach { (key, value) -> trace.note("after_$key", value) }
            event("scan", *trace.summary(reason, now))
        }
        state("scan", "${trace.id}:$reason")
        memory("scan_end", full = true)
    }

    internal fun scanNet(id: Int, host: String, ms: Long, reused: Boolean, waitMs: Long?, error: String?) {
        synchronized(lock) { scan?.takeIf { it.id == id }?.net(host, ms, reused, waitMs, error) }
    }

    // endregion

    // region Engines and lookups

    /**
     * One pass of a recognition engine over an image: [engine] (`mlkit`, `tesseract`, `paddle`, `lens`), [kind]
     * (`band`, `whole`, `warm_up`, `region`), its size and time, with the engine's own details in [extra].
     */
    fun ocrPass(engine: String, kind: String, model: String?, width: Int, height: Int, ms: Long, vararg extra: Pair<String, Any?>) {
        if (recorder == null) return
        val scanId = currentScanId
        val native = android.os.Debug.getNativeHeapAllocatedSize() shr 10
        event(
            "ocr_pass",
            "engine" to engine,
            "kind" to kind,
            "model" to model,
            "w" to width,
            "h" to height,
            "ms" to ms,
            *extra,
            "native_kb" to native,
            "scan" to scanId.takeIf { it > 0 },
        )
        val details = extra.filter { it.second != null }.joinToString("") { " ${it.first}=${it.second}" }
        synchronized(lock) {
            scan?.takeIf { it.id == scanId }?.pass("$engine/$kind ${model.orEmpty()} ${width}x$height ${ms}ms$details")
        }
    }

    /** A dictionary lookup with its phases; never the text, only its length. */
    fun lookup(kind: String, ms: Long, vararg fields: Pair<String, Any?>) {
        if (recorder == null) return
        val scanId = currentScanId
        event("lookup", "kind" to kind, "ms" to ms, *fields, "scan" to scanId.takeIf { it > 0 })
        synchronized(lock) { scan?.takeIf { it.id == scanId }?.lookup(ms) }
    }

    // endregion

    // region Process and memory

    /**
     * A memory record, posted to the diagnostics thread. [full] reads the whole memory map (PSS by kind), at most once a
     * minute unless [force]d.
     */
    fun memory(reason: String, full: Boolean = false, force: Boolean = false) {
        if (recorder == null) return
        handler.post {
            val now = SystemClock.elapsedRealtime()
            val detailed = full && (force || now - lastFullMemory >= FULL_MEMORY_MIN_GAP_MS)
            if (detailed) lastFullMemory = now
            val fields = runCatching { if (detailed) Memory.full(context) else Memory.cheap() }.getOrDefault(emptyMap())
            event("memory", "reason" to reason, *fields.toPairs())
            (fields["pss_kb"] as? Long)?.let {
                Trace.setCounter("${TAG_PREFIX}pss_mb", it shr 10)
                state("pss", "${it shr 10}M")
            }
            (fields["native_kb"] as? Long)?.let { Trace.setCounter("${TAG_PREFIX}native_mb", it shr 10) }
        }
    }

    /** Counts an event too frequent for its own lines (window changes, popup updates); the `sample` carries the counts. */
    fun count(key: String) {
        if (recorder != null) counters.add(key)
    }

    /** How long this process has run. */
    fun processUpMs(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    /** The cheap memory counters, as event fields. */
    fun memoryNow(): Array<Pair<String, Any?>> = if (recorder == null) emptyArray() else Memory.cheap().toPairs()

    /** The default network as `wifi`, `cell+metered`, …; null before [start]. */
    fun network(): String? = if (recorder == null) null else runCatching { DeviceState.network(context) }.getOrNull()

    /**
     * A short state kept by the system for this process, which the next process reads in the exit record if this one is
     * killed: what it was doing then.
     */
    fun state(key: String, value: String) {
        if (recorder == null) return
        handler.post {
            summary[key] = value
            if (summaryPending) return@post
            summaryPending = true
            handler.postDelayed(
                {
                    summaryPending = false
                    ProcessInfo.setSummary(context, summary)
                },
                SUMMARY_DELAY_MS,
            )
        }
    }

    /** The periodic sample: memory, device state and CPU time, every [SAMPLE_PERIOD_MS] while the process lives. */
    private fun sample() {
        runCatching {
            val memory = Memory.full(context)
            lastFullMemory = SystemClock.elapsedRealtime()
            event(
                "sample",
                "cpu_ms" to Process.getElapsedCpuTime(),
                "process_up_ms" to processUpMs(),
                *memory.toPairs(),
                *DeviceState.full(context).toPairs(),
                "diag_kb" to (sizeBytes() shr 10),
                "counts" to counters.drain().takeIf { it.isNotEmpty() },
            )
        }
        handler.postDelayed(::sample, SAMPLE_PERIOD_MS)
    }

    private fun mainStall(ms: Long, what: String) {
        val scanId = currentScanId
        synchronized(lock) { scan?.stall(ms) }
        if (ms >= STALL_EVENT_MS) event("stall", "ms" to ms, "what" to what, "scan" to scanId.takeIf { it > 0 })
    }

    /** Uncaught exceptions: their types and frames (messages may hold text and are left out), then the usual crash. */
    private fun catchCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                event("crash", "thread" to thread.name, *Crashes.describe(error).toPairs())
                recorder?.sync(timeoutMs = 500)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    // endregion

    // region Network

    /** For the shared OkHttp client: one `net` event per call. */
    val networkEvents: EventListener.Factory by lazy { NetworkEvents() }

    // endregion

    // region Export

    /** The files to export, newest data on disk first. */
    fun files(): List<File> {
        recorder?.sync()
        return directory.takeIf { recorder != null }?.walkTopDown()?.filter { it.isFile }?.toList().orEmpty()
    }

    fun directory(): File? = if (recorder != null) directory else null

    fun sizeBytes(): Long = directory().let { dir -> dir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L }

    /** When collecting began (the first start, or the last [clear]); epoch ms. */
    fun since(): Long? = directory()?.let { runCatching { File(it, "started-at.txt").readText().trim().toLong() }.getOrNull() }

    /** Deletes what was collected; collecting goes on from now. */
    fun clear() {
        val recorder = recorder ?: return
        recorder.clear()
        File(directory, "traces").deleteRecursively()
        runCatching { tee?.clear() }
        File(directory, "started-at.txt").writeText(System.currentTimeMillis().toString())
        event("cleared")
    }

    // endregion

    private fun Map<String, Any?>.toPairs(): Array<Pair<String, Any?>> = map { it.key to it.value }.toTypedArray()
}

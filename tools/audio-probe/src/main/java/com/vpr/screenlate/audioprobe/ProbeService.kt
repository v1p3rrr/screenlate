package com.vpr.screenlate.audioprobe

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.util.Properties
import kotlin.concurrent.thread

@TargetApi(29)
class ProbeService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var display: VirtualDisplay? = null
    private var images: ImageReader? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running || intent == null) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("probe", getString(R.string.probe_name), NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "probe")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(getString(R.string.probe_name))
            .setOngoing(true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        @Suppress("DEPRECATION")
        val consent = intent.getParcelableExtra<Intent>("consent") ?: run { stopSelf(); return START_NOT_STICKY }
        val rate = intent.getIntExtra("rate", 48000)
        val metrics = Properties()
        try {
            val session = checkNotNull(getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(Activity.RESULT_OK, consent))
            projection = session
            session.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { running = false }
            }, Handler(Looper.getMainLooper()))
            if (intent.getBooleanExtra("screenshot", false)) {
                val dimensions = DisplayMetrics()
                @Suppress("DEPRECATION")
                getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(dimensions)
                val reader = ImageReader.newInstance(dimensions.widthPixels, dimensions.heightPixels, PixelFormat.RGBA_8888, 2)
                images = reader
                var frames = 0L
                reader.setOnImageAvailableListener({ source ->
                    source.acquireLatestImage()?.use { image ->
                        metrics["screenshotFrames"] = (++frames).toString()
                        metrics["screenshotWidth"] = image.width.toString()
                        metrics["screenshotHeight"] = image.height.toString()
                    }
                }, Handler(Looper.getMainLooper()))
                display = session.createVirtualDisplay("audio-probe", dimensions.widthPixels, dimensions.heightPixels,
                    dimensions.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
            }
            val capture = AudioPlaybackCaptureConfiguration.Builder(session)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .excludeUid(Process.myUid()).build()
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "unsupported-rate" }
            val input = AudioRecord.Builder().setAudioPlaybackCaptureConfig(capture)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setSampleRate(rate).build())
                .setBufferSizeInBytes(maxOf(minimum * 2, rate / 5 * 2)).build()
            check(input.state == AudioRecord.STATE_INITIALIZED) { "uninitialized" }
            recorder = input
            running = true
            File(filesDir, "result.properties").delete()
            worker = thread(name = "probe-capture") {
                val selfTone = ProbeTone(997.0)
                try {
                    input.startRecording()
                    selfTone.start()
                    val start = SystemClock.elapsedRealtimeNanos()
                    val buffer = ByteArray(9600)
                    var bytes = 0L
                    var maximumWriteNanos = 0L
                    val pcm = File(filesDir, "capture.pcm")
                    pcm.outputStream().use { output ->
                        while (running && bytes < input.sampleRate * 2L * 10) {
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), input.sampleRate * 20L - bytes).toInt())
                            check(count > 0 && count % 2 == 0) { "read-$count" }
                            val beforeWrite = SystemClock.elapsedRealtimeNanos()
                            output.write(buffer, 0, count)
                            maximumWriteNanos = maxOf(maximumWriteNanos, SystemClock.elapsedRealtimeNanos() - beforeWrite)
                            bytes += count
                        }
                    }
                    val timestamp = AudioTimestamp()
                    metrics["timestampStatus"] = input.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME).toString()
                    metrics["timestampFrame"] = timestamp.framePosition.toString()
                    metrics["timestampNanos"] = timestamp.nanoTime.toString()
                    metrics["elapsedMillis"] = ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000).toString()
                    metrics["maxWriteMicros"] = (maximumWriteNanos / 1000).toString()
                    metrics["rate"] = input.sampleRate.toString()
                    metrics["bytes"] = bytes.toString()
                    if (running) {
                        ProbeCodec.analyze(pcm, input.sampleRate, metrics, "capture")
                        ProbeCodec.encode(pcm, File(filesDir, "capture.m4a"), input.sampleRate, metrics)
                        ProbeCodec.decode(File(filesDir, "capture.m4a"), File(filesDir, "decoded.pcm"), metrics)
                    }
                    metrics["status"] = if (running) "complete" else "stopped"
                } catch (error: Exception) {
                    metrics["status"] = "failed"
                    metrics["errorType"] = error.javaClass.simpleName
                    metrics["errorCode"] = error.message?.takeIf { it.matches(Regex("[a-z0-9-]+")) } ?: "operation"
                } finally {
                    selfTone.close()
                    runCatching { input.stop() }
                    input.release()
                    recorder = null
                    running = false
                    File(filesDir, "result.properties").outputStream().use { metrics.store(it, "Synthetic media probe") }
                    Handler(Looper.getMainLooper()).post { stopSelf() }
                }
            }
        } catch (error: Exception) {
            metrics["status"] = "start-failed"
            metrics["errorType"] = error.javaClass.simpleName
            File(filesDir, "result.properties").outputStream().use { metrics.store(it, "Synthetic media probe") }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        runCatching { recorder?.stop() }
        display?.release()
        display = null
        images?.setOnImageAvailableListener(null, null)
        images?.close()
        images = null
        projection?.stop()
        projection = null
        super.onDestroy()
    }
}

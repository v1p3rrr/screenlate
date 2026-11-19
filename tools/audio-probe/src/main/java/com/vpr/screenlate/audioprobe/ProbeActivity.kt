package com.vpr.screenlate.audioprobe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.Properties
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/** A deliberately separate package: running this probe never replaces or instruments Screenlate. */
class ProbeActivity : Activity() {
    private var tone: ProbeTone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = packageName.endsWith(".source")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }
        layout.addView(TextView(this).apply {
            setText(if (source) R.string.probe_source else R.string.probe_description)
            textSize = 20f
        })
        layout.addView(Button(this).apply {
            setText(if (source) R.string.probe_start_source else R.string.probe_capture)
            setOnClickListener {
                if (source) {
                    tone?.close()
                    tone = ProbeTone(440.0).also { it.start() }
                } else if (Build.VERSION.SDK_INT < 29) {
                    setText(R.string.probe_unavailable)
                } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 2)
                }
            }
        })
        layout.addView(Button(this).apply {
            setText(R.string.probe_stop)
            setOnClickListener {
                tone?.close()
                tone = null
                stopService(Intent(this@ProbeActivity, ProbeService::class.java))
            }
        })
        setContentView(layout)
        if (intent.getBooleanExtra("codecOnly", false)) {
            thread(name = "probe-fixture") {
                val metrics = Properties()
                try {
                    val pcm = File(filesDir, "fixture.pcm")
                    val frames = 480_123
                    pcm.outputStream().buffered().use { output ->
                        for (position in 0 until frames) {
                            val frequency = if (position >= frames - 480) 2203 else 440
                            val sample = (sin(2 * PI * frequency * position / 48000) * 6000).toInt()
                            output.write(sample and 255)
                            output.write((sample shr 8) and 255)
                        }
                    }
                    val opus = intent.getStringExtra("codec") == "opus"
                    val encoded = File(filesDir, if (opus) "fixture.ogg" else "fixture.m4a")
                    metrics["sourceFrames"] = frames.toString()
                    ProbeCodec.encode(pcm, encoded, 48000, metrics, opus)
                    ProbeCodec.decode(encoded, File(filesDir, "fixture-decoded.pcm"), metrics)
                    metrics["status"] = "complete"
                } catch (error: Exception) {
                    metrics["status"] = "failed"
                    metrics["errorType"] = error.javaClass.simpleName
                } finally {
                    File(filesDir, "fixture.properties").outputStream().use { metrics.store(it, "Synthetic codec probe") }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && resultCode == RESULT_OK && data != null && Build.VERSION.SDK_INT >= 29) {
            startForegroundService(Intent(this, ProbeService::class.java).putExtra("consent", data)
                .putExtra("rate", intent.getIntExtra("rate", 48000)))
        }
    }

    override fun onDestroy() {
        tone?.close()
        super.onDestroy()
    }
}

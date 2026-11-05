package com.vpr.screenlate.camera

import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.MainActivity
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.photos.LookupSource
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoText
import com.vpr.screenlate.core.photos.PhotoWork
import com.vpr.screenlate.dictionary.api.registry.DictionaryEntity
import com.vpr.screenlate.overlay.OverlayServiceStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real OCR and pull-out acceptance for CAM59; uses only the generated photo and the verification package. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 37)
class PhotoLoadingUiTest {
    @Test
    fun heldOutBubbleFollowsPhotoRecognition(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val mode = InstrumentationRegistry.getArguments().getString("photoLoading")
        assumeTrue("Explicit emulator-only fixture required", context.packageName == "com.vpr.screenlate.verification" &&
            Build.HARDWARE in listOf("ranchu", "goldfish") && mode in listOf("success", "failure", "stored"))
        val access = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        val documents = access.documents()
        val photo = requireNotNull(documents.current.first())
        check(photo.rawWidth == 2800 && photo.rawHeight == 2400) { "Owned generated page required" }
        val stored = photo.text as? PhotoText.Recognized
        check((mode == "stored") == (stored != null)) { "Stored text does not match mode $mode" }
        if (Language.JAPANESE !in access.profiles().current().turnedOn) {
            access.profiles().turnOn(Language.JAPANESE, activate = true)
        }
        access.profiles().setActive(Language.JAPANESE)
        access.preferences().select(Language.JAPANESE)
        access.commonPreferences().edit {
            it[LanguageProfiles.FIRST_RUN] = false
            it[booleanPreferencesKey("background_tip_seen")] = true
        }
        val dictionary = importTerms(context, access)

        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val resolver = context.contentResolver
        val servicesKey = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val enabledKey = Settings.Secure.ACCESSIBILITY_ENABLED
        val proxyKey = Settings.Global.HTTP_PROXY
        val originalServices = Settings.Secure.getString(resolver, servicesKey)
        val originalEnabled = Settings.Secure.getString(resolver, enabledKey)
        val originalProxy = Settings.Global.getString(resolver, proxyKey)
        val component = "${context.packageName}/com.vpr.screenlate.overlay.ScreenlateAccessibilityService"
        assumeTrue(originalServices.orEmpty().split(':').none { it.startsWith(context.packageName + "/") })
        var scenario: ActivityScenario<MainActivity>? = null
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try {
            Settings.Secure.putString(resolver, servicesKey, component)
            Settings.Secure.putInt(resolver, enabledKey, 1)
            await { OverlayServiceStatus.running.value }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            clickText(automation, context.getString(R.string.camera_title))
            await {
                val source = access.sources().source.value
                if (mode == "stored") source is LookupSource.Photo && source.documentId == photo.id
                else source is LookupSource.NoText && !source.recognizing
            }
            SystemClock.sleep(1500)
            screenshot(context, automation, "cam59-$mode-before.png")

            if (mode != "success") Settings.Global.putString(resolver, proxyKey, "10.0.2.2:8899")
            val engines = if (mode == "success") OcrEngines.DEVICE else OcrEngines.CLOUD
            access.jobs().recognize(photo, Language.JAPANESE,
                PhotoOcrSettings(engines, if (engines == OcrEngines.DEVICE) "mlkit" else null, tiled = true))
            await {
                val work = access.jobs().work.value
                work is PhotoWork.Running && work.documentId == photo.id && work.total == 4
            }
            await {
                val source = access.sources().source.value
                if (mode == "stored") source is LookupSource.Photo && source.revision == photo.revision
                else source is LookupSource.NoText && source.recognizing
            }
            pullOut(automation)
            SystemClock.sleep(150)
            screenshot(context, automation, "cam59-$mode-loading.png")
            if (mode == "stored") {
                assertThat(access.sources().source.value).isInstanceOf(LookupSource.Photo::class.java)
            } else {
                assertThat((access.sources().source.value as LookupSource.NoText).recognizing).isTrue()
            }

            var progress = false
            val deadline = SystemClock.elapsedRealtime() + 90_000
            while (SystemClock.elapsedRealtime() < deadline) {
                val work = access.jobs().work.value
                if (!progress && work is PhotoWork.Running && work.completed in 1 until work.total) {
                    screenshot(context, automation, "cam59-$mode-progress.png")
                    progress = true
                }
                if (work == null) break
                SystemClock.sleep(50)
            }
            check(access.jobs().work.value == null) { "Photo recognition did not finish" }
            check(progress || mode == "failure") { "No intermediate photo progress observed" }
            when (mode) {
                "success" -> {
                    await { access.sources().source.value is LookupSource.Photo }
                    assertThat(documents.current.first()?.text).isInstanceOf(PhotoText.Recognized::class.java)
                }
                "failure" -> {
                    await { (access.sources().source.value as? LookupSource.NoText)?.recognizing == false }
                    assertThat(documents.current.first()?.text).isNotInstanceOf(PhotoText.Recognized::class.java)
                }
                "stored" -> {
                    val current = documents.current.first()
                    assertThat((current?.text as? PhotoText.Recognized)?.recognition).isEqualTo(stored?.recognition)
                    assertThat((access.sources().source.value as? LookupSource.Photo)?.revision).isEqualTo(photo.revision)
                }
            }
            SystemClock.sleep(250)
            screenshot(context, automation, "cam59-$mode-after.png")
        } finally {
            scenario?.moveToState(Lifecycle.State.CREATED)
            scenario?.close()
            Settings.Secure.putString(resolver, servicesKey, originalServices)
            Settings.Secure.putString(resolver, enabledKey, originalEnabled)
            Settings.Global.putString(resolver, proxyKey, originalProxy)
            automation.dropShellPermissionIdentity()
            access.dictionaries().delete(dictionary.id)
            assertThat(Settings.Secure.getString(resolver, servicesKey)).isEqualTo(originalServices)
            assertThat(Settings.Secure.getString(resolver, enabledKey)).isEqualTo(originalEnabled)
            assertThat(Settings.Global.getString(resolver, proxyKey)).isEqualTo(originalProxy)
        }
    }

    private suspend fun importTerms(context: android.content.Context, access: PhotoTestAccess): DictionaryEntity {
        access.dictionaries().getAll().filter { it.title == TERMS_TITLE }.forEach { access.dictionaries().delete(it.id) }
        val archive = File(context.cacheDir, "cam59-terms.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            entry("index.json", """{"title":"$TERMS_TITLE","revision":"1","format":3,"sourceLanguage":"ja","targetLanguage":"en"}""")
            entry("term_bank_1.json", listOf("これは", "これ", "日本語", "日本", "語", "写真", "です").joinToString(",", "[", "]") {
                """["$it","","","",0,["fixture"],1,""]"""
            })
        }
        return access.dictionaries().import(archive).also { archive.delete() }
    }

    private fun pullOut(automation: UiAutomation) {
        val start = SystemClock.uptimeMillis()
        inject(automation, start, start, MotionEvent.ACTION_DOWN, 1230f, 1280f)
        SystemClock.sleep(80)
        val steps = 14
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            val at = SystemClock.uptimeMillis()
            inject(automation, start, at, MotionEvent.ACTION_MOVE,
                1230f + (320f - 1230f) * fraction, 1280f + (600f - 1280f) * fraction)
            SystemClock.sleep(30)
        }
        inject(automation, start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 320f, 600f)
    }

    private fun inject(automation: UiAutomation, down: Long, at: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, at, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            check(automation.injectInputEvent(event, true)) { "Touch injection failed" }
        } finally {
            event.recycle()
        }
    }

    private fun clickText(automation: UiAutomation, text: String) {
        await {
            val node = find(automation.rootInActiveWindow) { it.text?.toString() == text } ?: return@await false
            generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
    }

    private fun screenshot(context: android.content.Context, automation: UiAutomation, name: String) {
        val output = File(context.getExternalFilesDir(null), "ui-acceptance").apply { mkdirs() }
        val bitmap = checkNotNull(automation.takeScreenshot())
        File(output, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (true) {
            if (condition()) return
            check(SystemClock.elapsedRealtime() < deadline) { "UI did not reach its expected state" }
            SystemClock.sleep(50)
        }
    }

    private companion object {
        const val TERMS_TITLE = "CAM59 fixture"
    }
}

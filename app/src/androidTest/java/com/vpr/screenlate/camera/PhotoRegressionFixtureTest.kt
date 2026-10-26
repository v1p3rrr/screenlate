package com.vpr.screenlate.camera

import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.settings.LanguageProfiles
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit emulator-only states for manual acceptance. Restore reinstates the complete original preference map. */
@RunWith(AndroidJUnit4::class)
class PhotoRegressionFixtureTest {
    @Test
    fun configureAcceptanceFixture(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val stage = args.getString("photoRegression")
        assumeTrue("Explicit emulator-only fixture required", stage in listOf("prepare", "configure", "firstRun", "unrecognized", "unreadable", "restore") &&
            (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val access = EntryPointAccessors.fromApplication(context.applicationContext, PhotoTestAccess::class.java)
        val store = access.commonPreferences()
        val backup = File(context.noBackupFilesDir, "photo-regression-preferences.json")
        if (stage == "prepare") {
            if (!backup.exists()) backup.writeText(encode(store.data.first()).toString())
            val profiles = access.profiles().current()
            check(Language.JAPANESE in profiles.turnedOn)
            access.profiles().restore(profiles.copy(active = Language.JAPANESE,
                starred = listOfNotNull(profiles.active.takeIf { it != Language.JAPANESE })))
            store.edit {
                it[stringPreferencesKey("overlay_text_source")] = "SCREEN"
                it[stringPreferencesKey("overlay_ocr_engines")] = "BOTH"
                it[stringPreferencesKey("overlay_aim_mode")] = "ABOVE_FINGER"
                it[booleanPreferencesKey("popup_hide_off_word")] = true
            }
        } else if (stage == "restore") {
            check(backup.exists()) { "No acceptance preference snapshot" }
            val saved = Json.parseToJsonElement(backup.readText()).jsonObject
            store.edit { preferences ->
                preferences.clear()
                saved.forEach { (key, entry) ->
                    val data = entry.jsonObject
                    val value = data.getValue("value")
                    when (data.getValue("type").jsonPrimitive.content) {
                        "string" -> preferences[stringPreferencesKey(key)] = value.jsonPrimitive.content
                        "boolean" -> preferences[booleanPreferencesKey(key)] = value.jsonPrimitive.boolean
                        "int" -> preferences[intPreferencesKey(key)] = value.jsonPrimitive.int
                        "long" -> preferences[longPreferencesKey(key)] = value.jsonPrimitive.long
                        "float" -> preferences[floatPreferencesKey(key)] = value.jsonPrimitive.float
                        "double" -> preferences[doublePreferencesKey(key)] = value.jsonPrimitive.double
                        "set" -> preferences[stringSetPreferencesKey(key)] = value.jsonArray.map { it.jsonPrimitive.content }.toSet()
                        else -> error("Unknown preference type")
                    }
                }
            }
            check(encode(store.data.first()) == saved) { "Preference restoration mismatch" }
            backup.delete()
        } else {
            check(backup.exists()) { "Prepare acceptance preferences first" }
            when (stage) {
                "firstRun" -> store.edit {
                    it[LanguageProfiles.FIRST_RUN] = true
                    it[booleanPreferencesKey("background_tip_seen")] = false
                }
                "configure" -> {
                    args.getString("active")?.let { access.profiles().setActive(requireNotNull(Language.of(it))) }
                    store.edit { preferences ->
                        for ((arg, key) in listOf("engines" to "overlay_ocr_engines", "textSource" to "overlay_text_source",
                            "aim" to "overlay_aim_mode", "model" to "ocr_model")) {
                            args.getString(arg)?.let { preferences[stringPreferencesKey(key)] = it }
                        }
                        args.getString("hideOffWord")?.let { preferences[booleanPreferencesKey("popup_hide_off_word")] = it.toBooleanStrict() }
                        args.getString("translationButton")?.let { preferences[booleanPreferencesKey("translation_button")] = it.toBooleanStrict() }
                        args.getString("tipSeen")?.let { preferences[booleanPreferencesKey("background_tip_seen")] = it.toBooleanStrict() }
                    }
                }
                "unrecognized" -> {
                    val documents = access.documents()
                    val photo = requireNotNull(documents.current.first())
                    check(photo.rawWidth == 2800 && photo.rawHeight == 2400) { "Owned generated page required" }
                    documents.pinImage(photo.id)!!.use { image ->
                        val file = documents.newCaptureFile()
                        file.outputStream().use { image.copyTo(it) }
                        documents.replaceCurrent(file)
                    }
                }
                "unreadable" -> {
                    val photo = requireNotNull(access.documents().current.first())
                    check(photo.rawWidth == 2800 && photo.rawHeight == 2400 && photo.revision > 0) { "Owned recognized page required" }
                    File(photo.image.parentFile, "recognition-${photo.revision}").writeBytes(byteArrayOf(1, 2, 3))
                }
            }
        }
    }

    private fun encode(preferences: Preferences): JsonObject = buildJsonObject {
        preferences.asMap().forEach { (key, value) ->
            val type = when (value) {
                is String -> "string"; is Boolean -> "boolean"; is Int -> "int"; is Long -> "long"
                is Float -> "float"; is Double -> "double"; is Set<*> -> "set"; else -> error("Unsupported preference type")
            }
            put(key.name, buildJsonObject {
                put("type", type)
                put("value", when (value) {
                    is String -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Set<*> -> JsonArray(value.map { JsonPrimitive(it as String) }.sortedBy { it.content })
                    else -> error("Unsupported preference type")
                })
            })
        }
    }
}

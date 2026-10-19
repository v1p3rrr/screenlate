package com.vpr.screenlate.core.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** ML Kit leaves a hyphen at a line end out of the line's last element; the engine puts it back. */
@RunWith(AndroidJUnit4::class)
class MlKitLineEndHyphenTest {

    @Test
    fun wordsHyphenatedAtALineEndKeepTheirHyphen() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 420, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 54f
                isAntiAlias = true
            }
            drawText("Das ist eine sehr impor-", 40f, 110f, paint)
            drawText("tante Sache für uns alle,", 40f, 190f, paint)
            drawText("die wir gemein-", 40f, 270f, paint)
            drawText("sam lösen.", 40f, 350f, paint)
        }
        val engine = MlKitOcrEngine()
        try {
            val page = engine.recognize(bitmap, Language.GERMAN)
            val lastWords = page.paragraphs.flatMap { it.lines }.map { it.words.last() }
            assertThat(lastWords.map { it.text }).containsAtLeast("impor-", "gemein-")
            for (word in lastWords.filter { it.text.endsWith("-") }) {
                word.characterBoxes?.let { assertThat(it).hasSize(word.text.length) }
            }
        } finally {
            engine.release()
        }
    }
}

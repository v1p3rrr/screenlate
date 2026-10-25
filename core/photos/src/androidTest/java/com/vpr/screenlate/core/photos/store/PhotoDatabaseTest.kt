package com.vpr.screenlate.core.photos.store

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrEngineType
import com.vpr.screenlate.core.ocr.OcrEngines
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrPage
import com.vpr.screenlate.core.ocr.OcrParagraph
import com.vpr.screenlate.core.ocr.OcrWord
import com.vpr.screenlate.core.photos.PhotoOcrSettings
import com.vpr.screenlate.core.photos.PhotoOrientation
import com.vpr.screenlate.core.photos.PhotoRecognition
import com.vpr.screenlate.core.photos.PhotoText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Room metadata and the platform image probe, which the unit tests replace. */
@RunWith(AndroidJUnit4::class)
class PhotoDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folder = File(context.cacheDir, "photo-database-test")
    private val databaseName = "photo-database-test.db"
    private lateinit var database: PhotoDatabase

    @Before
    fun setUp() {
        folder.deleteRecursively()
        context.deleteDatabase(databaseName)
        database = open()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        folder.deleteRecursively()
    }

    private fun open() = Room.databaseBuilder(context, PhotoDatabase::class.java, databaseName).build()

    private fun store(database: PhotoDatabase) =
        PhotoStore(File(folder, "photos"), RoomPhotoIndex(database.photoDao()), BitmapImageProbe, { 7L }, Dispatchers.IO)

    @Test
    fun theCurrentRowFollowsEachInsert() = runTest {
        val dao = database.photoDao()
        val first = dao.insertCurrent(row("a"))
        val second = dao.insertCurrent(row("b"))
        assertThat(dao.current()!!.id).isEqualTo(second)
        assertThat(dao.observeCurrent().first()!!.directory).isEqualTo("b")
        assertThat(dao.all().map { it.id }).containsExactly(first, second).inOrder()
    }

    @Test
    fun deletingTheCurrentPhotoLeavesNoCurrentOne() = runTest {
        val dao = database.photoDao()
        val id = dao.insertCurrent(row("a"))
        dao.delete(id)
        assertThat(dao.current()).isNull()
    }

    @Test
    fun aRecognitionIsNamedOnlyFromTheRevisionItStartedFrom() = runTest {
        val dao = database.photoDao()
        val id = dao.insertCurrent(row("a"))
        assertThat(dao.setRecognition(id, expected = 0, file = "recognition-1", revision = 1)).isEqualTo(1)
        assertThat(dao.setRecognition(id, expected = 0, file = "recognition-1", revision = 1)).isEqualTo(0)
        assertThat(dao.get(id)!!.recognitionRevision).isEqualTo(1)
    }

    @Test
    fun aPhotoWithItsTextSurvivesReopeningTheDatabase() = runTest {
        val store = store(database)
        val capture = store.newCaptureFile()
        writeJpeg(capture, width = 64, height = 48, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val photo = store.replaceCurrent(capture)
        assertThat(photo.orientation).isEqualTo(PhotoOrientation.ROTATE_90)
        assertThat(photo.width to photo.height).isEqualTo(48 to 64)
        val recognition = recognition()
        store.commitRecognition(photo.id, 0, recognition)
        database.close()

        database = open()
        val reopened = store(database).current.first()!!
        assertThat(reopened.id).isEqualTo(photo.id)
        assertThat(reopened.text).isEqualTo(PhotoText.Recognized(recognition))
        assertThat(reopened.image.length()).isGreaterThan(0L)
    }

    private fun row(directory: String) =
        PhotoEntity(directory = directory, rawWidth = 1, rawHeight = 1, orientation = 1, imageBytes = 1, createdAt = 1)

    private fun writeJpeg(file: File, width: Int, height: Int, orientation: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
    }

    private fun recognition() = PhotoRecognition(
        language = Language.JAPANESE,
        settings = PhotoOcrSettings(OcrEngines.DEVICE, "mlkit"),
        page = OcrPage(
            width = 48,
            height = 64,
            paragraphs = listOf(
                OcrParagraph(
                    listOf(OcrLine(listOf(OcrWord("写真", "", Box(10f, 10f, 20f, 40f))), Box(10f, 10f, 20f, 40f), vertical = true)),
                ),
            ),
            engine = OcrEngineType.ML_KIT,
        ),
        recognizedAt = 5L,
    )
}

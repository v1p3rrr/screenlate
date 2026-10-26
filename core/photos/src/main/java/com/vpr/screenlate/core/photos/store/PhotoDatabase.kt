package com.vpr.screenlate.core.photos.store

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Database(entities = [PhotoEntity::class, CurrentPhotoEntity::class], version = 3, exportSchema = true)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE photos ADD COLUMN attemptFile TEXT") }
        }

        /** The history's order and language; photos saved before it keep the order they were taken in. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN language TEXT")
                db.execSQL("ALTER TABLE photos ADD COLUMN savedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE photos SET savedAt = createdAt WHERE saved = 1")
            }
        }
    }
}

/**
 * A stored photo. Its files are in [directory] under the photo storage root (see `PhotoStore`).
 *
 * @property orientation the image's EXIF orientation value.
 * @property recognitionFile the committed recognition's file in [directory]; null before the first.
 * @property recognitionRevision counts the recognitions committed; 0 before the first.
 * @property saved kept in the photo history, so replacing the current photo does not delete it.
 * @property language the code of the committed recognition's language; null before the first.
 * @property savedAt when the photo joined the history, which lists the latest first; 0 when it is not in it.
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val directory: String,
    val rawWidth: Int,
    val rawHeight: Int,
    val orientation: Int,
    val imageBytes: Long,
    val createdAt: Long,
    val recognitionFile: String? = null,
    val recognitionRevision: Int = 0,
    val saved: Boolean = false,
    val attemptFile: String? = null,
    val language: String? = null,
    @ColumnInfo(defaultValue = "0") val savedAt: Long = 0,
)

/** The one row that names the current photo. */
@Entity(
    tableName = "current_photo",
    foreignKeys = [ForeignKey(PhotoEntity::class, ["id"], ["photoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("photoId")],
)
data class CurrentPhotoEntity(@PrimaryKey val slot: Int = 0, val photoId: Long)

@Dao
abstract class PhotoDao {
    @Query(CURRENT)
    abstract fun observeCurrent(): Flow<PhotoEntity?>

    @Query(CURRENT)
    abstract suspend fun current(): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id = :id")
    abstract suspend fun get(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos ORDER BY id")
    abstract suspend fun all(): List<PhotoEntity>

    @Query(HISTORY)
    abstract fun observeHistory(): Flow<List<PhotoEntity>>

    @Query(HISTORY)
    abstract suspend fun history(): List<PhotoEntity>

    @Insert
    abstract suspend fun insert(photo: PhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setCurrent(current: CurrentPhotoEntity)

    /** Adds the photo to the history, keeping the language a recognition named; the count of rows changed. */
    @Query("UPDATE photos SET saved = 1, savedAt = :at, language = COALESCE(language, :language) WHERE id = :id AND saved = 0")
    abstract suspend fun save(id: Long, at: Long, language: String?): Int

    /** Adds [photo] and makes it the current one in one transaction. */
    @Transaction
    open suspend fun insertCurrent(photo: PhotoEntity): Long {
        val id = insert(photo)
        setCurrent(CurrentPhotoEntity(photoId = id))
        return id
    }

    /** Names the photo's new recognition, unless another was committed since [expected]; the count of rows changed. */
    @Query(
        "UPDATE photos SET recognitionFile = :file, recognitionRevision = :revision, attemptFile = NULL, " +
            "language = :language WHERE id = :id AND recognitionRevision = :expected",
    )
    abstract suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int, language: String): Int

    @Query("UPDATE photos SET attemptFile = :file WHERE id = :id AND recognitionRevision = :expected " +
        "AND id IN (SELECT photoId FROM current_photo)")
    abstract suspend fun setAttempt(id: Long, expected: Int, file: String): Int

    @Query("DELETE FROM current_photo")
    abstract suspend fun clearCurrent()

    @Query("DELETE FROM photos WHERE id = :id")
    abstract suspend fun delete(id: Long)

    private companion object {
        const val CURRENT = "SELECT photos.* FROM photos JOIN current_photo ON photos.id = current_photo.photoId"
        const val HISTORY = "SELECT * FROM photos WHERE saved = 1 ORDER BY savedAt DESC, id DESC"
    }
}

/** The photo metadata [PhotoStore] needs; [PhotoDao] in the app, a map in unit tests. */
internal interface PhotoIndex {
    fun observeCurrent(): Flow<PhotoEntity?>
    suspend fun current(): PhotoEntity?
    suspend fun get(id: Long): PhotoEntity?
    suspend fun all(): List<PhotoEntity>

    /** The photos in the history, the latest saved first. */
    fun observeHistory(): Flow<List<PhotoEntity>>
    suspend fun history(): List<PhotoEntity>
    suspend fun insertCurrent(photo: PhotoEntity): Long
    suspend fun setCurrent(id: Long)

    /** False when the photo is gone or has another recognition than [expected] by now. */
    suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int, language: String): Boolean
    suspend fun setAttempt(id: Long, expected: Int, file: String): Boolean

    /** False when the photo is gone or already in the history. */
    suspend fun save(id: Long, at: Long, language: String?): Boolean
    suspend fun clearCurrent()
    suspend fun delete(id: Long)
}

internal class RoomPhotoIndex(private val dao: PhotoDao) : PhotoIndex {
    override fun observeCurrent() = dao.observeCurrent()
    override suspend fun current() = dao.current()
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun all() = dao.all()
    override fun observeHistory() = dao.observeHistory()
    override suspend fun history() = dao.history()
    override suspend fun insertCurrent(photo: PhotoEntity) = dao.insertCurrent(photo)
    override suspend fun setCurrent(id: Long) = dao.setCurrent(CurrentPhotoEntity(photoId = id))
    override suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int, language: String) =
        dao.setRecognition(id, expected, file, revision, language) == 1
    override suspend fun setAttempt(id: Long, expected: Int, file: String) = dao.setAttempt(id, expected, file) == 1
    override suspend fun save(id: Long, at: Long, language: String?) = dao.save(id, at, language) == 1
    override suspend fun clearCurrent() = dao.clearCurrent()
    override suspend fun delete(id: Long) = dao.delete(id)
}

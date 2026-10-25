package com.vpr.screenlate.core.photos.store

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
import kotlinx.coroutines.flow.Flow

@Database(entities = [PhotoEntity::class, CurrentPhotoEntity::class], version = 1, exportSchema = true)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
}

/**
 * A stored photo. Its files are in [directory] under the photo storage root (see `PhotoStore`).
 *
 * @property orientation the image's EXIF orientation value.
 * @property recognitionFile the committed recognition's file in [directory]; null before the first.
 * @property recognitionRevision counts the recognitions committed; 0 before the first.
 * @property saved kept in the photo history, so replacing the current photo does not delete it.
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

    @Insert
    abstract suspend fun insert(photo: PhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setCurrent(current: CurrentPhotoEntity)

    /** Adds [photo] and makes it the current one in one transaction. */
    @Transaction
    open suspend fun insertCurrent(photo: PhotoEntity): Long {
        val id = insert(photo)
        setCurrent(CurrentPhotoEntity(photoId = id))
        return id
    }

    /** Names the photo's new recognition, unless another was committed since [expected]; the count of rows changed. */
    @Query(
        "UPDATE photos SET recognitionFile = :file, recognitionRevision = :revision " +
            "WHERE id = :id AND recognitionRevision = :expected",
    )
    abstract suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int): Int

    @Query("DELETE FROM current_photo")
    abstract suspend fun clearCurrent()

    @Query("DELETE FROM photos WHERE id = :id")
    abstract suspend fun delete(id: Long)

    private companion object {
        const val CURRENT = "SELECT photos.* FROM photos JOIN current_photo ON photos.id = current_photo.photoId"
    }
}

/** The photo metadata [PhotoStore] needs; [PhotoDao] in the app, a map in unit tests. */
internal interface PhotoIndex {
    fun observeCurrent(): Flow<PhotoEntity?>
    suspend fun current(): PhotoEntity?
    suspend fun get(id: Long): PhotoEntity?
    suspend fun all(): List<PhotoEntity>
    suspend fun insertCurrent(photo: PhotoEntity): Long

    /** False when the photo is gone or has another recognition than [expected] by now. */
    suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int): Boolean
    suspend fun clearCurrent()
    suspend fun delete(id: Long)
}

internal class RoomPhotoIndex(private val dao: PhotoDao) : PhotoIndex {
    override fun observeCurrent() = dao.observeCurrent()
    override suspend fun current() = dao.current()
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun all() = dao.all()
    override suspend fun insertCurrent(photo: PhotoEntity) = dao.insertCurrent(photo)
    override suspend fun setRecognition(id: Long, expected: Int, file: String, revision: Int) =
        dao.setRecognition(id, expected, file, revision) == 1
    override suspend fun clearCurrent() = dao.clearCurrent()
    override suspend fun delete(id: Long) = dao.delete(id)
}

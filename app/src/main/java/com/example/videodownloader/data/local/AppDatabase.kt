package com.example.videodownloader.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE folderPath = :path LIMIT 1")
    suspend fun getByFolderPath(path: String): DownloadEntity?

    @Query("SELECT filePath FROM downloads WHERE filePath IS NOT NULL")
    suspend fun getAllFilePaths(): List<String>

    @Query("SELECT folderPath FROM downloads WHERE folderPath IS NOT NULL")
    suspend fun getAllFolderPaths(): List<String>

    /** Сколько всего скачано (успешно завершённых). */
    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    /** Сколько скачано с :sinceMs. */
    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED' AND createdAt >= :sinceMs")
    suspend fun getCompletedCountSince(sinceMs: Long): Int

    /** Пути завершённых загрузок — чтобы посчитать общий размер. */
    @Query("SELECT filePath FROM downloads WHERE status = 'COMPLETED' AND filePath IS NOT NULL")
    suspend fun getCompletedFilePaths(): List<String>

    @Insert
    suspend fun insert(item: DownloadEntity): Long

    @Insert
    suspend fun insertAll(items: List<DownloadEntity>): List<Long>

    @Update
    suspend fun update(item: DownloadEntity)

    @Delete
    suspend fun delete(item: DownloadEntity)
}

@Database(entities = [DownloadEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "downloads.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

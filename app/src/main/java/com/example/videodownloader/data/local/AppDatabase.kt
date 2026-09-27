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

    @Insert
    suspend fun insert(item: DownloadEntity): Long

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

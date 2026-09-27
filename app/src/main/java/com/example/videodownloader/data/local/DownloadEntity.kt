package com.example.videodownloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val filePath: String? = null,
    val folderPath: String? = null,     // Для фото-карусели — путь к папке; для аудио — DCIM/.../Audio
    val type: String = "VIDEO",          // VIDEO / AUDIO / PHOTOS
    val itemCount: Int = 1,
    val status: String = "QUEUED",
    val progress: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val error: String? = null
)

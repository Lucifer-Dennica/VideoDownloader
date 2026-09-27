package com.example.videodownloader.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.videodownloader.data.local.*
import kotlinx.coroutines.flow.Flow

class DownloadRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).downloads()

    val items: Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun add(url: String, title: String) =
        dao.insert(DownloadEntity(url = url, title = title))

    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun update(item: DownloadEntity) = dao.update(item)
    suspend fun delete(item: DownloadEntity) = dao.delete(item)

    /** Сканирует папку DCIM/VideoDownloader и добавляет все файлы в БД. */
    suspend fun scanFolder() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        // Сканируем ВИДЕО
        scanMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        // Сканируем ФОТО (для карусели)
        scanMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
    }

    private suspend fun scanMedia(collection: android.net.Uri, isVideo: Boolean) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%DCIM/VideoDownloader%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            appContext.contentResolver.query(
                collection, projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "file"
                    val dateSec = cursor.getLong(dateCol)
                    val relativePath = cursor.getString(pathCol) ?: ""

                    val uri = ContentUris.withAppendedId(collection, mediaId)
                    val uriStr = uri.toString()

                    if (dao.getByPath(uriStr) != null) continue

                    // Определяем сервис из пути
                    val service = detectServiceFromPath(relativePath)

                    // Определяем тип: фото или видео
                    val type = if (isVideo) "VIDEO" else "PHOTOS"

                    dao.insert(
                        DownloadEntity(
                            url = "local://$service",         // псевдо-url для определения
                            title = if (isVideo) "Видео • $service" else "Фото • $service",
                            filePath = uriStr,
                            type = type,
                            itemCount = 1,
                            status = "COMPLETED",
                            progress = 100,
                            createdAt = dateSec * 1000
                        )
                    )
                    Log.d("DownloadRepository", "scanFolder: $name → $service ($type)")
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "scanMedia error: ${e.message}")
        }
    }

    /** Из пути DCIM/VideoDownloader/TikTok/Photos/album_xxx/photo_1.jpg
     *  вытаскиваем сервис (TikTok) */
    private fun detectServiceFromPath(relativePath: String): String {
        val lower = relativePath.lowercase()
        return when {
            lower.contains("/tiktok") -> "TikTok"
            lower.contains("/youtube") -> "YouTube"
            lower.contains("/instagram") -> "Instagram"
            lower.contains("/facebook") -> "Facebook"
            lower.contains("/vk") -> "VK"
            lower.contains("/twitter") -> "Twitter"
            lower.contains("/reddit") -> "Reddit"
            lower.contains("/pinterest") -> "Pinterest"
            lower.contains("/snapchat") -> "Snapchat"
            else -> "Другое"
        }
    }
}

package com.example.videodownloader.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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

    /** Сканирует папку DCIM/VideoDownloader. Группирует фото по папкам-альбомам. */
    suspend fun scanFolder() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        scanVideos()
        scanPhotos()
    }

    // ---------- ВИДЕО ----------
    private suspend fun scanVideos() {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%DCIM/VideoDownloader%")

        try {
            appContext.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "video.mp4"
                    val dateSec = cursor.getLong(dateCol)
                    val relativePath = cursor.getString(pathCol) ?: ""

                    // Пропускаем фото из папок Photos/ — они обрабатываются отдельно
                    if (relativePath.contains("/Photos/")) continue

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId
                    )
                    val uriStr = uri.toString()

                    if (dao.getByPath(uriStr) != null) continue

                    val service = detectServiceFromPath(relativePath)

                    dao.insert(
                        DownloadEntity(
                            url = "local://$service",
                            title = "Видео • $service",
                            filePath = uriStr,
                            type = "VIDEO",
                            itemCount = 1,
                            status = "COMPLETED",
                            progress = 100,
                            createdAt = dateSec * 1000
                        )
                    )
                    Log.d(TAG, "scanVideos: $name → $service")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanVideos error: ${e.message}")
        }
    }

    // ---------- ФОТО (группировка по альбомам) ----------
    private suspend fun scanPhotos() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%DCIM/VideoDownloader%")

        // Группируем по папке: folderPath -> список (uri, dateAdded)
        val albums = mutableMapOf<String, MutableList<Pair<String, Long>>>()

        try {
            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val dateSec = cursor.getLong(dateCol)
                    val relativePath = cursor.getString(pathCol) ?: ""

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId
                    )

                    albums.getOrPut(relativePath) { mutableListOf() }
                        .add(uri.toString() to dateSec)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanPhotos query error: ${e.message}")
            return
        }

        // Создаём ОДНУ запись на альбом (папку)
        for ((folder, photos) in albums) {
            if (photos.isEmpty()) continue

            // Если запись для этой папки уже есть — пропускаем
            if (dao.getByFolderPath(folder) != null) continue

            val firstUri = photos.first().first
            val newestDate = photos.maxOf { it.second }
            val service = detectServiceFromPath(folder)

            dao.insert(
                DownloadEntity(
                    url = "local://$service",
                    title = "Фото • $service",
                    filePath = firstUri,
                    folderPath = folder,
                    type = "PHOTOS",
                    itemCount = photos.size,
                    status = "COMPLETED",
                    progress = 100,
                    createdAt = newestDate * 1000
                )
            )
            Log.d(TAG, "scanPhotos: $folder → $service (${photos.size} фото)")
        }
    }

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

    companion object {
        private const val TAG = "DownloadRepository"
    }
}

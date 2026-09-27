package com.example.videodownloader.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.videodownloader.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DownloadRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).downloads()

    val items: Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun add(url: String, title: String) =
        dao.insert(DownloadEntity(url = url, title = title))

    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun update(item: DownloadEntity) = dao.update(item)
    suspend fun delete(item: DownloadEntity) = dao.delete(item)

    suspend fun deleteWithFile(item: DownloadEntity) = withContext(Dispatchers.IO) {
        try {
            if (item.type == "PHOTOS" && !item.folderPath.isNullOrBlank()) {
                deletePhotosInFolder(item.folderPath)
            } else {
                item.filePath?.let { deleteMediaByPath(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteWithFile file error: ${e.message}")
        }
        dao.delete(item)
    }

    private fun deleteMediaByPath(path: String) {
        if (path.startsWith("content://")) {
            try {
                appContext.contentResolver.delete(Uri.parse(path), null, null)
            } catch (e: Exception) {
                Log.w(TAG, "contentResolver.delete failed: ${e.message}")
            }
        } else {
            File(path).takeIf { it.exists() }?.delete()
        }
    }

    private fun deletePhotosInFolder(folderRelativePath: String) {
        val normalized = folderRelativePath.trimEnd('/') + "/"
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        try {
            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, arrayOf(normalized), null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    try { appContext.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deletePhotosInFolder failed: ${e.message}")
        }
    }

    suspend fun scanFolder() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        scanVideos()
        scanAudio()
        scanPhotos()
    }

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

                    if (relativePath.contains("/Photos/")) continue

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId
                    )
                    val uriStr = uri.toString()

                    if (dao.getByPath(uriStr) != null) continue

                    val service = detectServiceFromPath(relativePath)
                    val thumbPath = generateThumbnail(mediaId, name)

                    dao.insert(
                        DownloadEntity(
                            url = "local://$service",
                            title = "Видео • $service",
                            filePath = uriStr,
                            thumbnailUrl = thumbPath,
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

    /**
     * Аудио теперь лежит в Music/VideoDownloader/{service}/Audio/.
     * Фильтр — просто %VideoDownloader%, чтобы ловилось независимо от корневой папки.
     */
    private suspend fun scanAudio() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%VideoDownloader%")

        try {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val dateSec = cursor.getLong(dateCol)
                    val relativePath = cursor.getString(pathCol) ?: ""

                    if (!relativePath.contains("/Audio/")) continue

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
                    )
                    val uriStr = uri.toString()

                    if (dao.getByPath(uriStr) != null) continue

                    val service = detectServiceFromPath(relativePath)
                    val folderPath = relativePath.trimEnd('/')

                    dao.insert(
                        DownloadEntity(
                            url = "local://$service",
                            title = "Аудио • $service",
                            filePath = uriStr,
                            thumbnailUrl = null,
                            folderPath = folderPath,
                            type = "AUDIO",
                            itemCount = 1,
                            status = "COMPLETED",
                            progress = 100,
                            createdAt = dateSec * 1000
                        )
                    )
                    Log.d(TAG, "scanAudio: $relativePath → $service")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanAudio error: ${e.message}")
        }
    }

    private suspend fun generateThumbnail(mediaId: Long, name: String): String? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId
                )
                retriever.setDataSource(appContext, uri)
                val bitmap = retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return@withContext null

                val thumbsDir = File(appContext.cacheDir, "thumbs").apply { mkdirs() }
                val thumbFile = File(thumbsDir, "thumb_${mediaId}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                bitmap.recycle()
                thumbFile.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "generateThumbnail failed for $name: ${e.message}")
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

    private suspend fun scanPhotos() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%DCIM/VideoDownloader%")

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

        for ((folder, photos) in albums) {
            if (photos.isEmpty()) continue
            if (dao.getByFolderPath(folder) != null) continue

            val firstUri = photos.first().first
            val newestDate = photos.maxOf { it.second }
            val service = detectServiceFromPath(folder)

            dao.insert(
                DownloadEntity(
                    url = "local://$service",
                    title = "Фото • $service",
                    filePath = firstUri,
                    thumbnailUrl = firstUri,
                    folderPath = folder,
                    type = "PHOTOS",
                    itemCount = photos.size,
                    status = "COMPLETED",
                    progress = 100,
                    createdAt = newestDate * 1000
                )
            )
            Log.d(TAG, "scanPhotos: $folder → $service (${photos.size})")
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

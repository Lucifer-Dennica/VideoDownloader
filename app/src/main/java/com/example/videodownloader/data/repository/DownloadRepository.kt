package com.example.videodownloader.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
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

    data class Statistics(
        val total: Int,
        val thisMonth: Int,
        val totalSizeBytes: Long
    )

    suspend fun add(url: String, title: String) =
        dao.insert(DownloadEntity(url = url, title = title))

    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun update(item: DownloadEntity) = dao.update(item)
    suspend fun delete(item: DownloadEntity) = dao.delete(item)

    /** Считает статистику: всего/за месяц/общий размер. */
    suspend fun getStatistics(): Statistics = withContext(Dispatchers.IO) {
        val total = dao.getCompletedCount()
        val monthAgoMs = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val thisMonth = dao.getCompletedCountSince(monthAgoMs)

        var totalSize = 0L
        try {
            val paths = dao.getCompletedFilePaths()
            for (path in paths) {
                try {
                    totalSize += if (path.startsWith("content://")) {
                        appContext.contentResolver.openAssetFileDescriptor(Uri.parse(path), "r")
                            ?.use { it.length } ?: 0L
                    } else {
                        File(path).takeIf { it.exists() }?.length() ?: 0L
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getStatistics size error: ${e.message}")
        }

        Statistics(total = total, thisMonth = thisMonth, totalSizeBytes = totalSize)
    }

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

    suspend fun scanFolder(sinceMs: Long = 0L) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        val start = System.currentTimeMillis()
        Log.d(TAG, "scanFolder: since=$sinceMs")

        val existingFiles = dao.getAllFilePaths().toHashSet()
        val existingFolders = dao.getAllFolderPaths().toHashSet()

        scanVideos(sinceMs, existingFiles)
        scanAudio(sinceMs, existingFiles)
        scanPhotos(sinceMs, existingFolders)

        Log.d(TAG, "scanFolder done in ${System.currentTimeMillis() - start}ms")
    }

    private suspend fun scanVideos(sinceMs: Long, existing: Set<String>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RELATIVE_PATH
        )
        val baseSel = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val (selection, args) = if (sinceMs > 0) {
            "$baseSel AND ${MediaStore.Video.Media.DATE_ADDED} > ?" to
                arrayOf("%DCIM/VideoDownloader%", (sinceMs / 1000).toString())
        } else {
            baseSel to arrayOf("%DCIM/VideoDownloader%")
        }

        val batch = mutableListOf<DownloadEntity>()

        try {
            appContext.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
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
                    if (existing.contains(uriStr)) continue

                    val service = detectServiceFromPath(relativePath)
                    val thumbPath = generateThumbnail(mediaId, name)

                    batch.add(
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
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanVideos error: ${e.message}")
            return
        }

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            Log.d(TAG, "scanVideos: inserted ${batch.size}")
        }
    }

    private suspend fun scanAudio(sinceMs: Long, existing: Set<String>) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        val baseSel = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val (selection, args) = if (sinceMs > 0) {
            "$baseSel AND ${MediaStore.Audio.Media.DATE_ADDED} > ?" to
                arrayOf("%VideoDownloader%", (sinceMs / 1000).toString())
        } else {
            baseSel to arrayOf("%VideoDownloader%")
        }

        val batch = mutableListOf<DownloadEntity>()

        try {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
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
                    if (existing.contains(uriStr)) continue

                    val service = detectServiceFromPath(relativePath)
                    batch.add(
                        DownloadEntity(
                            url = "local://$service",
                            title = "Аудио • $service",
                            filePath = uriStr,
                            thumbnailUrl = null,
                            folderPath = relativePath.trimEnd('/'),
                            type = "AUDIO",
                            itemCount = 1,
                            status = "COMPLETED",
                            progress = 100,
                            createdAt = dateSec * 1000
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanAudio error: ${e.message}")
            return
        }

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            Log.d(TAG, "scanAudio: inserted ${batch.size}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun generateThumbnail(mediaId: Long, name: String): String? =
        withContext(Dispatchers.IO) {
            val thumbsDir = File(appContext.filesDir, "thumbs").apply { mkdirs() }
            val thumbFile = File(thumbsDir, "thumb_$mediaId.jpg")
            if (thumbFile.exists()) return@withContext thumbFile.absolutePath

            val videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId
            )

            try {
                val size = android.util.Size(320, 180)
                val bmp = appContext.contentResolver.loadThumbnail(videoUri, size, null)
                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                bmp.recycle()
                return@withContext thumbFile.absolutePath
            } catch (e: Exception) {
                Log.d(TAG, "loadThumbnail failed for $name: ${e.message}, fallback")
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, videoUri)
                val bitmap = retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return@withContext null
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

    private suspend fun scanPhotos(sinceMs: Long, existingFolders: Set<String>) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val baseSel = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val (selection, args) = if (sinceMs > 0) {
            "$baseSel AND ${MediaStore.Images.Media.DATE_ADDED} > ?" to
                arrayOf("%DCIM/VideoDownloader%", (sinceMs / 1000).toString())
        } else {
            baseSel to arrayOf("%DCIM/VideoDownloader%")
        }

        val albums = mutableMapOf<String, MutableList<Pair<String, Long>>>()

        try {
            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
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

        val batch = mutableListOf<DownloadEntity>()
        for ((folder, photos) in albums) {
            if (photos.isEmpty()) continue
            if (existingFolders.contains(folder)) continue

            val firstUri = photos.first().first
            val newestDate = photos.maxOf { it.second }
            val service = detectServiceFromPath(folder)

            batch.add(
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
        }

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            Log.d(TAG, "scanPhotos: inserted ${batch.size}")
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

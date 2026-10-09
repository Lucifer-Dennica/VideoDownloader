package com.example.videodownloader.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.videodownloader.MainActivity
import com.example.videodownloader.data.repository.DownloadRepository
import com.example.videodownloader.data.settings.VideoQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val id = inputData.getLong(KEY_ID, 0L)
        val quality = VideoQuality.fromName(inputData.getString(KEY_QUALITY))
        val audioOnly = inputData.getBoolean(KEY_AUDIO, false)
        val service = getServiceFolder(url)

        Log.d(TAG, "=== Начало: url=$url id=$id service=$service audioOnly=$audioOnly ===")

        val repository = DownloadRepository(applicationContext)
        val current = repository.getById(id)
        if (current != null) {
            repository.update(current.copy(status = "DOWNLOADING", progress = 0))
        }

        showNotification(
            id,
            if (audioOnly) "Скачивание аудио…" else "Скачивание…",
            0, ongoing = true
        )

        return try {
            val resolved = resolveMedia(url, quality, audioOnly)
                ?: throw Exception("Не удалось получить ссылку на медиа")

            repository.getById(id)?.let {
                repository.update(it.copy(thumbnailUrl = resolved.thumbnail, progress = 5))
            }

            if (resolved.imageUrls.isNotEmpty()) {
                // Фото-карусель TikTok
                val result = downloadPhotoCarousel(resolved.imageUrls, id, service, repository)
                repository.getById(id)?.let {
                    repository.update(it.copy(
                        filePath = result.firstUri,
                        folderPath = result.folderPath,
                        type = "PHOTOS",
                        itemCount = resolved.imageUrls.size,
                        status = "COMPLETED",
                        progress = 100,
                        error = null
                    ))
                }
                showNotification(
                    id, "✅ ${resolved.imageUrls.size} фото скачано", 100, ongoing = false,
                    clickUri = result.firstUri?.let { Uri.parse(it) },
                    clickMime = "image/*"
                )
                cancelNotificationDelayed(id)
                Result.success(workDataOf(KEY_FILE to (result.firstUri ?: "")))
            } else {
                val mediaUrl = resolved.videoUrl
                    ?: throw Exception("Пустой ответ от сервера")

                val tempFile = File(applicationContext.cacheDir, "media_$id.tmp")
                val result = downloadFile(mediaUrl, tempFile, id, repository, audioOnly)

                if (audioOnly && result.size < 10_000L) {
                    tempFile.delete()
                    throw Exception("Сервер вернул пустой аудиофайл (${result.size} Б)")
                }

                val extension = resolved.extension
                    ?: detectExtension(result.contentType, audioOnly)
                val fileName = "${if (audioOnly) "audio" else "video"}_${id}_${System.currentTimeMillis()}.$extension"
                val savedPath = if (audioOnly) {
                    saveAudioToPublicMusic(tempFile, fileName, service)
                } else {
                    saveToPublicDcim(tempFile, fileName, service)
                }
                tempFile.delete()

                repository.getById(id)?.let {
                    repository.update(it.copy(
                        filePath = savedPath,
                        folderPath = if (audioOnly) "Music/VideoDownloader/$service/Audio" else it.folderPath,
                        type = if (audioOnly) "AUDIO" else "VIDEO",
                        itemCount = 1,
                        status = "COMPLETED",
                        progress = 100,
                        error = null
                    ))
                }
                showNotification(
                    id,
                    if (audioOnly) "✅ Аудио скачано" else "✅ Видео скачано",
                    100, ongoing = false,
                    clickUri = buildClickUri(savedPath),
                    clickMime = mimeFromExt(extension, audioOnly)
                )
                cancelNotificationDelayed(id)
                Result.success(workDataOf(KEY_FILE to savedPath))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== Ошибка ===", e)
            repository.getById(id)?.let {
                repository.update(it.copy(status = "ERROR", error = e.message))
            }
            showNotification(id, "❌ Ошибка: ${e.message}", 0, ongoing = false)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Ошибка")))
        }
    }

    private fun buildClickUri(savedPath: String): Uri? = try {
        if (savedPath.startsWith("content://")) Uri.parse(savedPath)
        else {
            val file = File(savedPath)
            if (!file.exists()) null
            else FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                file
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "buildClickUri failed: ${e.message}")
        null
    }

    private fun mimeFromExt(ext: String, audioOnly: Boolean): String = when {
        audioOnly -> when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            else -> "audio/mpeg"
        }
        else -> when (ext) {
            "webm" -> "video/webm"
            else -> "video/mp4"
        }
    }

    private fun detectExtension(contentType: String?, audioOnly: Boolean): String {
        if (audioOnly) {
            return when {
                contentType == null -> "m4a"
                contentType.contains("mpeg", ignoreCase = true) -> "mp3"
                contentType.contains("mp4", ignoreCase = true) -> "m4a"
                contentType.contains("aac", ignoreCase = true) -> "aac"
                else -> "m4a"
            }
        } else {
            return when {
                contentType == null -> "mp4"
                contentType.contains("webm", ignoreCase = true) -> "webm"
                else -> "mp4"
            }
        }
    }

    data class Resolved(
        val videoUrl: String? = null,
        val imageUrls: List<String> = emptyList(),
        val thumbnail: String? = null,
        val extension: String? = null
    )

    data class CarouselResult(val firstUri: String?, val folderPath: String)
    data class DownloadResult(val size: Long, val contentType: String?)

    private suspend fun downloadPhotoCarousel(
        imageUrls: List<String>,
        id: Long,
        service: String,
        repository: DownloadRepository
    ): CarouselResult {
        val albumName = "album_${id}_${System.currentTimeMillis()}"
        val albumPath = "DCIM/VideoDownloader/$service/Photos/$albumName"
        var firstUri: String? = null

        for ((index, imgUrl) in imageUrls.withIndex()) {
            try {
                val tempImg = File(applicationContext.cacheDir, "img_${id}_$index.jpg")
                downloadFileSimple(imgUrl, tempImg)
                val savedUri = saveImageToDcim(tempImg, "photo_${index + 1}.jpg", albumPath)
                if (firstUri == null) firstUri = savedUri
                tempImg.delete()

                val percent = 5 + ((index + 1) * 90 / imageUrls.size)
                repository.getById(id)?.let { repository.update(it.copy(progress = percent)) }
                showNotification(id, "Скачивание фото ${index + 1}/${imageUrls.size}", percent, true)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось скачать фото $index: ${e.message}")
            }
        }
        return CarouselResult(firstUri, albumPath)
    }

    private fun downloadFileSimple(url: String, outFile: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
        } finally { conn.disconnect() }
    }

    private fun saveImageToDcim(tempFile: File, fileName: String, relativePath: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            try {
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) return null
                    tempFile.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri.toString()
            } catch (e: Exception) {
                Log.e(TAG, "saveImageToDcim error", e)
                return null
            }
        }
        return null
    }

    private fun getServiceFolder(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("rutube.ru") -> "Rutube"
            lower.contains("facebook.com") || lower.contains("fb.watch") -> "Facebook"
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("pinterest.com") || lower.contains("pin.it") -> "Pinterest"
            else -> "Other"
        }
    }

    /**
     * Роутинг: TikTok напрямую через tikwm (с телефона),
     * Rutube / Facebook / Instagram / Pinterest — через Render.
     */
    private suspend fun resolveMedia(
        url: String,
        quality: VideoQuality,
        audioOnly: Boolean
    ): Resolved? {
        val lower = url.lowercase()

        // TikTok → tikwm напрямую (работает бесплатно)
        if (lower.contains("tiktok.com")) {
            return if (audioOnly) resolveTikTokAudio(url) else resolveTikTok(url, quality)
        }

        // Direct .mp4 — скачиваем как есть
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") ||
            lower.endsWith(".mov") || lower.endsWith(".m4v")) {
            return Resolved(videoUrl = url)
        }

        // Всё остальное → сервер Render
        return resolveViaServer(url, audioOnly)
    }

    private suspend fun resolveTikTokAudio(url: String): Resolved? = withTikwmLock {
        val api = "https://tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8")
        val json = httpGetString(api, 20_000) ?: throw Exception("tikwm не ответил")
        val obj = JSONObject(json)
        if (obj.optInt("code", -1) != 0) throw Exception("tikwm: ${obj.optString("msg")}")
        val data = obj.optJSONObject("data") ?: throw Exception("tikwm: нет data")
        val music = data.optString("music").ifBlank { null } ?: throw Exception("tikwm: нет аудио")
        val ext = if (music.contains(".mp3", true)) "mp3" else "m4a"
        Resolved(videoUrl = music, thumbnail = data.optString("cover").ifBlank { null }, extension = ext)
    }

    private suspend fun resolveTikTok(url: String, quality: VideoQuality): Resolved? = withTikwmLock {
        val hd = quality.tikwmHd
        val api = "https://tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8") + "&hd=$hd"
        val json = httpGetString(api, 20_000) ?: throw Exception("tikwm не ответил")
        val obj = JSONObject(json)
        if (obj.optInt("code", -1) != 0) throw Exception("tikwm: ${obj.optString("msg")}")
        val data = obj.optJSONObject("data") ?: throw Exception("tikwm: нет data")
        val cover = data.optString("cover").ifBlank { null }

        // Фото-карусель?
        val imagesArr = data.optJSONArray("images")
        if (imagesArr != null && imagesArr.length() > 0) {
            val images = mutableListOf<String>()
            for (i in 0 until imagesArr.length()) {
                val img = imagesArr.optString(i, "")
                if (img.isNotBlank()) images.add(img)
            }
            if (images.isNotEmpty()) return@withTikwmLock Resolved(imageUrls = images, thumbnail = cover)
        }

        val hdPlay = data.optString("hdplay").ifBlank { null }
        val sdPlay = data.optString("play").ifBlank { null }
        val video = if (hd == 1) hdPlay ?: sdPlay else sdPlay ?: hdPlay
            ?: throw Exception("tikwm: нет видео")
        Resolved(videoUrl = video, thumbnail = cover, extension = "mp4")
    }

    private suspend fun <T> withTikwmLock(block: suspend () -> T): T {
        tikwmMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastTikwmCallMs
            if (elapsed < TIKWM_MIN_INTERVAL_MS) delay(TIKWM_MIN_INTERVAL_MS - elapsed)
            try {
                return block()
            } finally {
                lastTikwmCallMs = System.currentTimeMillis()
            }
        }
    }

    /** Запрос к Render-серверу (FastSaver). */
    private fun resolveViaServer(url: String, audioOnly: Boolean): Resolved? {
        val body = """{"url":"$url","audio_only":$audioOnly,"quality":"max"}"""
        Log.d(TAG, "Render request: $body")

        val response = httpPostJson(SERVER_URL, body, 90_000)
            ?: throw Exception("Сервер не ответил")

        val obj = JSONObject(response)

        if (obj.has("detail")) {
            val detail = obj.optString("detail")
            throw Exception(detail.take(200))
        }

        val media = obj.optString("video_url").ifBlank { null }
            ?: throw Exception("Сервер вернул пустой ответ")

        val thumb = obj.optString("thumbnail").ifBlank { null }
        val ext = obj.optString("ext").ifBlank { "mp4" }

        return Resolved(videoUrl = media, thumbnail = thumb, extension = ext)
    }

    private fun httpGetString(apiUrl: String, timeoutMs: Int): String? {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }

    private fun httpPostJson(apiUrl: String, body: String, timeoutMs: Int): String? {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            text.ifBlank { throw Exception("HTTP $code") }
        } finally { conn.disconnect() }
    }

    private suspend fun downloadFile(
        url: String,
        outFile: File,
        id: Long,
        repository: DownloadRepository,
        audioOnly: Boolean
    ): DownloadResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val action = if (audioOnly) "Скачивание аудио…" else "Скачивание…"
        try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            val contentType = conn.contentType?.substringBefore(";")?.trim()
            val total = conn.contentLengthLong
            var done = 0L
            var lastNotified = 0
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = 5 + ((done * 90) / total).toInt()
                            repository.getById(id)?.let { repository.update(it.copy(progress = percent)) }
                            if (percent - lastNotified >= 10) {
                                lastNotified = percent
                                showNotification(id, "$action $percent%", percent, ongoing = true)
                            }
                        }
                    }
                }
            }
            return DownloadResult(size = done, contentType = contentType)
        } finally { conn.disconnect() }
    }

    private fun saveToPublicDcim(tempFile: File, fileName: String, service: String): String {
        val relativePath = Environment.DIRECTORY_DCIM + "/VideoDownloader/" + service + "/"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("MediaStore insert failed")
            resolver.openOutputStream(uri).use { out ->
                if (out == null) throw Exception("openOutputStream failed")
                tempFile.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "VideoDownloader/$service"
            ).apply { mkdirs() }
            val target = File(dir, fileName)
            tempFile.copyTo(target, overwrite = true)
            return target.absolutePath
        }
    }

    private fun saveAudioToPublicMusic(tempFile: File, fileName: String, service: String): String {
        val relativePath = Environment.DIRECTORY_MUSIC + "/VideoDownloader/" + service + "/Audio/"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mime = when {
                fileName.endsWith(".mp3") -> "audio/mpeg"
                fileName.endsWith(".m4a") -> "audio/mp4"
                else -> "audio/mpeg"
            }
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("AudioStore insert failed")
            resolver.openOutputStream(uri).use { out ->
                if (out == null) throw Exception("openOutputStream failed")
                tempFile.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "VideoDownloader/$service/Audio"
            ).apply { mkdirs() }
            val target = File(dir, fileName)
            tempFile.copyTo(target, overwrite = true)
            return target.absolutePath
        }
    }

    private fun showNotification(
        id: Long, text: String, progress: Int, ongoing: Boolean,
        clickUri: Uri? = null, clickMime: String? = null
    ) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Загрузки", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val intent = if (clickUri != null && clickMime != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(clickUri, clickMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(applicationContext, id.toInt(), intent, flags)
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VideoDownloader")
            .setContentText(text)
            .setProgress(100, progress, progress == 0)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(pi)
            .build()
        manager.notify(id.toInt(), notif)
    }

    private fun cancelNotificationDelayed(id: Long) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            manager.cancel(id.toInt())
        }, 3000)
    }

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_URL = "url"
        const val KEY_ID = "id"
        const val KEY_QUALITY = "quality"
        const val KEY_AUDIO = "audio"
        const val KEY_FILE = "file"
        const val KEY_ERROR = "error"

        // ⚠️ URL сервера на Render
        private const val SERVER_URL = "https://videodownloader-backend-te3k.onrender.com/api/resolve"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val TIKWM_MIN_INTERVAL_MS = 1100L
        private val tikwmMutex = Mutex()
        @Volatile private var lastTikwmCallMs = 0L
    }
}

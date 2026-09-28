package com.example.videodownloader.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.videodownloader.data.repository.DownloadRepository
import com.example.videodownloader.data.settings.VideoQuality
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

        Log.d(TAG, "=== Начало: url=$url id=$id service=$service quality=$quality audioOnly=$audioOnly ===")

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
            val resolved = resolveDirectUrl(url, quality, audioOnly)
                ?: throw Exception("Не удалось получить ссылку на медиа")

            repository.getById(id)?.let {
                repository.update(it.copy(thumbnailUrl = resolved.thumbnail, progress = 5))
            }

            if (resolved.imageUrls.isNotEmpty()) {
                Log.d(TAG, "Это фото-карусель: ${resolved.imageUrls.size} фото")
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
                showNotification(id, "✅ ${resolved.imageUrls.size} фото скачано", 100, ongoing = false)
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
                    100, ongoing = false
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

    private fun detectExtension(contentType: String?, audioOnly: Boolean): String {
        if (audioOnly) {
            return when {
                contentType == null -> "m4a"
                contentType.contains("mpeg", ignoreCase = true) -> "mp3"
                contentType.contains("mp4", ignoreCase = true) -> "m4a"
                contentType.contains("aac", ignoreCase = true) -> "aac"
                contentType.contains("ogg", ignoreCase = true) -> "ogg"
                contentType.contains("webm", ignoreCase = true) -> "weba"
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
                repository.getById(id)?.let {
                    repository.update(it.copy(progress = percent))
                }
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
                outFile.outputStream().use { output ->
                    input.copyTo(output, 64 * 1024)
                }
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
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                relativePath.removePrefix("DCIM/")
            ).apply { mkdirs() }
            val target = File(dir, fileName)
            tempFile.copyTo(target, overwrite = true)
            return target.absolutePath
        }
    }

    private fun getServiceFolder(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("facebook.com") || lower.contains("fb.watch") -> "Facebook"
            lower.contains("vk.com") -> "VK"
            lower.contains("twitter.com") || lower.contains("x.com") -> "Twitter"
            lower.contains("reddit.com") -> "Reddit"
            lower.contains("pinterest.com") || lower.contains("pin.it") -> "Pinterest"
            lower.contains("snapchat.com") -> "Snapchat"
            else -> "Другое"
        }
    }

    private fun resolveDirectUrl(
        url: String,
        quality: VideoQuality,
        audioOnly: Boolean
    ): Resolved? {
        val lower = url.lowercase()

        // === АУДИО: всегда через yt-dlp ===
        if (audioOnly) {
            if (lower.endsWith(".mp4") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".m4v")) {
                throw Exception("Аудио из прямых ссылок не поддерживается")
            }
            return resolveViaYtdlp(url, quality, audioOnly = true)
        }

        // === ВИДЕО ===
        return when {
            lower.contains("tiktok.com") -> resolveTikTok(url, quality)
            lower.contains("youtube.com") || lower.contains("youtu.be") ->
                resolveViaYtdlp(url, quality, audioOnly = false)
            lower.contains("instagram.com") ->
                resolveViaYtdlp(url, quality, audioOnly = false)
            lower.contains("facebook.com") || lower.contains("fb.watch") ||
            lower.contains("vk.com") || lower.contains("twitter.com") ||
            lower.contains("x.com") || lower.contains("reddit.com") ||
            lower.contains("pinterest.com") || lower.contains("pin.it") ||
            lower.contains("snapchat.com") || lower.contains("vimeo.com") ||
            lower.contains("dailymotion.com") || lower.contains("twitch.tv") ||
            lower.contains("rumble.com") || lower.contains("odysee.com") ||
            lower.contains("soundcloud.com") || lower.contains("rutube.ru") ||
            lower.contains("linkedin.com") || lower.contains("threads.net") ||
            lower.contains("tumblr.com") -> resolveCobalt(url, quality)

            lower.endsWith(".mp4") || lower.endsWith(".webm") ||
            lower.endsWith(".mov") || lower.endsWith(".m4v") -> Resolved(videoUrl = url)

            else -> resolveCobalt(url, quality)
        }
    }

    private fun resolveTikTok(url: String, quality: VideoQuality): Resolved? {
        val hd = quality.tikwmHd
        val api = "https://tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8") + "&hd=$hd"
        Log.d(TAG, "GET $api")

        val json = httpGetString(api, timeoutMs = 20_000)
            ?: throw Exception("tikwm не ответил")

        val obj = JSONObject(json)
        val code = obj.optInt("code", -1)
        if (code != 0) {
            throw Exception("tikwm: ${obj.optString("msg", "unknown error")}")
        }

        val data = obj.optJSONObject("data")
            ?: throw Exception("tikwm: нет поля data")

        val cover = data.optString("cover").ifBlank { null }

        val imagesArr = data.optJSONArray("images")
        if (imagesArr != null && imagesArr.length() > 0) {
            val images = mutableListOf<String>()
            for (i in 0 until imagesArr.length()) {
                val img = imagesArr.optString(i, "")
                if (img.isNotBlank()) images.add(img)
            }
            if (images.isNotEmpty()) {
                Log.d(TAG, "TikTok фото-карусель: ${images.size} фото")
                return Resolved(imageUrls = images, thumbnail = cover)
            }
        }

        val video = data.optString("play").ifBlank { null }
            ?: data.optString("hdplay").ifBlank { null }
            ?: throw Exception("tikwm: нет ни видео, ни картинок")

        Log.d(TAG, "TikTok OK (видео, hd=$hd)")
        return Resolved(videoUrl = video, thumbnail = cover)
    }

    private fun resolveViaYtdlp(
        url: String,
        quality: VideoQuality,
        audioOnly: Boolean
    ): Resolved? {
        val qualityStr = when (quality) {
            VideoQuality.MAX -> "max"
            VideoQuality.P1080 -> "1080"
            VideoQuality.P720 -> "720"
            VideoQuality.P480 -> "480"
            VideoQuality.P360 -> "360"
        }
        val body = """{"url":"$url","audio_only":$audioOnly,"quality":"$qualityStr"}"""
        Log.d(TAG, "yt-dlp request: $body")

        val response = httpPostJson(YTDLP_URL, body, timeoutMs = 60_000)
            ?: throw Exception("yt-dlp сервер не ответил")
        val obj = JSONObject(response)
        if (obj.has("detail")) {
            val detail = obj.optString("detail")
            if (detail.contains("Sign in to confirm", ignoreCase = true) ||
                detail.contains("not a bot", ignoreCase = true)) {
                throw Exception("YouTube требует авторизацию. См. Настройки → YouTube")
            }
            if (detail.contains("login", ignoreCase = true)) {
                throw Exception("Instagram требует авторизацию. См. Настройки → Instagram")
            }
            throw Exception("yt-dlp: " + detail.take(150))
        }
        val media = obj.optString("video_url").ifBlank { null }
            ?: throw Exception("yt-dlp: нет ссылки")
        val thumb = obj.optString("thumbnail").ifBlank { null }
        val ext = obj.optString("ext").ifBlank { null }
        Log.d(TAG, "yt-dlp OK: ext=$ext")
        return Resolved(videoUrl = media, thumbnail = thumb, extension = ext)
    }

    /**
     * Cobalt — только публичные бесплатные инстансы.
     * Railway-Cobalt убран — приложение не зависит от личного сервера.
     */
    private fun resolveCobalt(
        url: String,
        quality: VideoQuality
    ): Resolved? {
        val instances = listOf(
            "https://cobalt-api.kwiatekmiki.com/",
            "https://co.eepy.today/",
            "https://cobalt-api.ayo.tf/",
            "https://cobalt.255x.ru/",
            "https://api.cobalt.best/"
        )

        val bodies = listOf(
            """{"url":"$url","videoQuality":"${quality.cobaltValue}"}""",
            """{"url":"$url","vQuality":"${quality.cobaltValue}"}"""
        )

        var lastError = "Нет инстансов"

        for (base in instances) {
            for ((idx, body) in bodies.withIndex()) {
                try {
                    val response = httpPostJson(base, body, timeoutMs = 15_000) ?: continue
                    val obj = JSONObject(response)
                    val status = obj.optString("status")
                    if (status == "error") {
                        val code = obj.optJSONObject("error")?.optString("code") ?: "unknown"
                        lastError = "Cobalt: $code"
                        Log.w(TAG, "Cobalt $base [вар. ${idx + 1}/${bodies.size}] → $code")
                        continue
                    }
                    if (status != "tunnel" && status != "redirect" && status != "stream") {
                        lastError = "Cobalt: неожиданный статус $status"
                        continue
                    }
                    val media = obj.optString("url").ifBlank {
                        val picker = obj.optJSONArray("picker")
                        if (picker != null && picker.length() > 0)
                            picker.getJSONObject(0).optString("url", "")
                        else ""
                    }.ifBlank { null } ?: continue
                    val thumb = obj.optString("thumbnail").ifBlank { null }
                    Log.d(TAG, "Cobalt OK через $base")
                    return Resolved(videoUrl = media, thumbnail = thumb)
                } catch (e: Exception) {
                    lastError = e.message ?: "unknown"
                    Log.w(TAG, "Cobalt $base ошибка: ${e.message}")
                }
            }
        }
        throw Exception(lastError)
    }

    private fun httpGetString(apiUrl: String, timeoutMs: Int = 20_000): String? {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }

    private fun httpPostJson(apiUrl: String, body: String, timeoutMs: Int = 30_000): String? {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
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
            Log.d(TAG, "downloadFile: $url → content-type=$contentType, size=${conn.contentLengthLong}")

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
                            repository.getById(id)?.let {
                                repository.update(it.copy(progress = percent))
                            }
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
                ?: throw Exception("Не удалось создать файл в MediaStore")
            resolver.openOutputStream(uri).use { out ->
                if (out == null) throw Exception("Не удалось открыть поток")
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
                fileName.endsWith(".aac") -> "audio/aac"
                fileName.endsWith(".ogg") -> "audio/ogg"
                fileName.endsWith(".weba") -> "audio/webm"
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
                ?: throw Exception("Не удалось создать аудио в MediaStore")
            resolver.openOutputStream(uri).use { out ->
                if (out == null) throw Exception("Не удалось открыть поток")
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

    private fun showNotification(id: Long, text: String, progress: Int, ongoing: Boolean) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Загрузки", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VideoDownloader")
            .setContentText(text)
            .setProgress(100, progress, progress == 0)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
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
        const val KEY_PROGRESS = "progress"
        const val KEY_FILE = "file"
        const val KEY_ERROR = "error"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val YTDLP_URL = "https://ytdlp-server-production-16c0.up.railway.app/api/resolve"
    }
}

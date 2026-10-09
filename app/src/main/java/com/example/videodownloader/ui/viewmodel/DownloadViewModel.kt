package com.example.videodownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.videodownloader.data.local.DownloadEntity
import com.example.videodownloader.data.repository.DownloadRepository
import com.example.videodownloader.data.settings.SettingsRepository
import com.example.videodownloader.data.settings.VideoQuality
import com.example.videodownloader.download.DownloadWorker
import com.example.videodownloader.util.UrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)
    private val settings = SettingsRepository(app)

    @Volatile private var isScanning = false

    private val _statistics = MutableStateFlow<DownloadRepository.Statistics?>(null)
    val statistics = _statistics.asStateFlow()

    // Превью для карточки перед скачиванием
    private val _preview = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val preview = _preview.asStateFlow()

    val items = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeItems = repo.items
        .map { list -> list.filter {
            it.status == "QUEUED" || it.status == "DOWNLOADING" || it.status == "ERROR"
        } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completedItems = repo.items
        .map { list -> list.filter { it.status == "COMPLETED" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun autoScan() = viewModelScope.launch(Dispatchers.IO) {
        if (isScanning) return@launch
        isScanning = true
        try {
            val last = settings.getLastScanTime()
            repo.scanFolder(sinceMs = last)
            settings.setLastScanTime(System.currentTimeMillis())
        } catch (_: Exception) { } finally { isScanning = false }
    }

    fun rescanFolder() = viewModelScope.launch(Dispatchers.IO) {
        if (isScanning) return@launch
        isScanning = true
        try {
            repo.scanFolder(sinceMs = 0L)
            settings.setLastScanTime(System.currentTimeMillis())
        } finally { isScanning = false }
    }

    fun loadStatistics() = viewModelScope.launch(Dispatchers.IO) {
        try { _statistics.value = repo.getStatistics() } catch (_: Exception) { }
    }

    /** Загрузить превью для ссылки (через /api/info). */
    fun fetchPreview(raw: String) = viewModelScope.launch(Dispatchers.IO) {
        val parsed = UrlParser.parse(raw) ?: run {
            _preview.value = PreviewState.Idle
            return@launch
        }

        // TikTok — превью делаем через tikwm
        if (parsed.service == "TikTok") {
            _preview.value = PreviewState.Loading
            try {
                val encoded = java.net.URLEncoder.encode(parsed.value, "UTF-8")
                val api = "https://tikwm.com/api/?url=$encoded"
                val json = httpGet(api)
                val obj = JSONObject(json)
                if (obj.optInt("code", -1) == 0) {
                    val data = obj.optJSONObject("data") ?: throw Exception("no data")
                    _preview.value = PreviewState.Success(
                        title = data.optString("title", "TikTok"),
                        thumbnail = data.optString("cover").ifBlank { null },
                        duration = data.optInt("duration", 0).takeIf { it > 0 },
                        service = "TikTok"
                    )
                } else {
                    _preview.value = PreviewState.Error("tikwm: ${obj.optString("msg")}")
                }
            } catch (e: Exception) {
                _preview.value = PreviewState.Error(e.message ?: "Ошибка превью")
            }
            return@launch
        }

        // Остальные — через Render /api/info
        if (!parsed.supported) {
            _preview.value = PreviewState.ComingSoon(parsed.service)
            return@launch
        }

        _preview.value = PreviewState.Loading
        try {
            val body = """{"url":"${parsed.value}"}"""
            val json = httpPost("${SERVER_BASE}/api/info", body)
            val obj = JSONObject(json)
            if (obj.has("detail")) {
                _preview.value = PreviewState.Error(obj.optString("detail").take(150))
                return@launch
            }
            _preview.value = PreviewState.Success(
                title = obj.optString("title", "Media"),
                thumbnail = obj.optString("thumbnail").ifBlank { null },
                duration = obj.optInt("duration", 0).takeIf { it > 0 },
                service = obj.optString("platform", parsed.service)
            )
        } catch (e: Exception) {
            _preview.value = PreviewState.Error(e.message ?: "Ошибка превью")
        }
    }

    fun clearPreview() { _preview.value = PreviewState.Idle }

    fun enqueue(raw: String, audio: Boolean = false) {
        val parsed = UrlParser.parse(raw) ?: return
        viewModelScope.launch {
            val title = if (audio) "Аудио • ${parsed.service}" else "Видео • ${parsed.service}"
            val id = repo.add(parsed.value, title)
            enqueueWorker(id, parsed.value, VideoQuality.MAX, audio)
        }
    }

    fun enqueueMany(text: String, audio: Boolean = false) {
        val parsedList = UrlParser.parseAll(text)
        if (parsedList.isEmpty()) return
        viewModelScope.launch {
            for (parsed in parsedList) {
                val title = if (audio) "Аудио • ${parsed.service}" else "Видео • ${parsed.service}"
                val id = repo.add(parsed.value, title)
                enqueueWorker(id, parsed.value, VideoQuality.MAX, audio)
            }
        }
    }

    fun retry(item: DownloadEntity) = viewModelScope.launch {
        val audio = item.type == "AUDIO"
        repo.update(item.copy(status = "QUEUED", error = null, progress = 0))
        enqueueWorker(item.id, item.url, VideoQuality.MAX, audio)
    }

    private fun enqueueWorker(id: Long, url: String, quality: VideoQuality, audio: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(
                DownloadWorker.KEY_URL to url,
                DownloadWorker.KEY_ID to id,
                DownloadWorker.KEY_QUALITY to quality.name,
                DownloadWorker.KEY_AUDIO to audio
            ))
            .addTag(id.toString())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(getApplication()).enqueue(request)
    }

    fun delete(item: DownloadEntity) = viewModelScope.launch {
        try { WorkManager.getInstance(getApplication()).cancelAllWorkByTag(item.id.toString()) } catch (_: Exception) { }
        repo.deleteWithFile(item)
    }

    fun deleteMany(items: List<DownloadEntity>) = viewModelScope.launch {
        items.forEach { item ->
            try { WorkManager.getInstance(getApplication()).cancelAllWorkByTag(item.id.toString()) } catch (_: Exception) { }
            repo.deleteWithFile(item)
        }
    }

    private fun httpGet(apiUrl: String): String {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        }
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }

    private fun httpPost(apiUrl: String, body: String): String {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText().orEmpty().ifBlank { "HTTP $code" }
            }
        } finally { conn.disconnect() }
    }

    companion object {
        const val SERVER_BASE = "https://videodownloader-backend-te3k.onrender.com"
    }
}

/** Состояние превью. */
sealed class PreviewState {
    object Idle : PreviewState()
    object Loading : PreviewState()
    data class Success(
        val title: String,
        val thumbnail: String?,
        val duration: Int?,
        val service: String
    ) : PreviewState()
    data class Error(val message: String) : PreviewState()
    data class ComingSoon(val service: String) : PreviewState()
}

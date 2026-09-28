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

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)
    private val settings = SettingsRepository(app)

    @Volatile private var isScanning = false

    private val _statistics = MutableStateFlow<DownloadRepository.Statistics?>(null)
    val statistics = _statistics.asStateFlow()

    val items = repo.items.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

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
        } catch (_: Exception) {
        } finally {
            isScanning = false
        }
    }

    fun rescanFolder() = viewModelScope.launch(Dispatchers.IO) {
        if (isScanning) return@launch
        isScanning = true
        try {
            repo.scanFolder(sinceMs = 0L)
            settings.setLastScanTime(System.currentTimeMillis())
        } finally {
            isScanning = false
        }
    }

    fun loadStatistics() = viewModelScope.launch(Dispatchers.IO) {
        try {
            _statistics.value = repo.getStatistics()
        } catch (_: Exception) { }
    }

    /** Одна ссылка — отправить. */
    fun enqueue(raw: String, audio: Boolean = false) {
        val parsed = UrlParser.parse(raw) ?: return
        viewModelScope.launch {
            val title = if (audio) "Аудио • ${parsed.service}" else "Видео • ${parsed.service}"
            val id = repo.add(parsed.value, title)
            enqueueWorker(id, parsed.value, VideoQuality.MAX, audio)
        }
    }

    /** Несколько ссылок из текста — ставим все в очередь. */
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

    private fun enqueueWorker(
        id: Long,
        url: String,
        quality: VideoQuality,
        audio: Boolean
    ) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_URL to url,
                    DownloadWorker.KEY_ID to id,
                    DownloadWorker.KEY_QUALITY to quality.name,
                    DownloadWorker.KEY_AUDIO to audio
                )
            )
            .addTag(id.toString())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(getApplication()).enqueue(request)
    }

    fun delete(item: DownloadEntity) = viewModelScope.launch {
        try {
            WorkManager.getInstance(getApplication())
                .cancelAllWorkByTag(item.id.toString())
        } catch (_: Exception) { }
        repo.deleteWithFile(item)
    }

    fun deleteMany(items: List<DownloadEntity>) = viewModelScope.launch {
        items.forEach { item ->
            try {
                WorkManager.getInstance(getApplication())
                    .cancelAllWorkByTag(item.id.toString())
            } catch (_: Exception) { }
            repo.deleteWithFile(item)
        }
    }
}

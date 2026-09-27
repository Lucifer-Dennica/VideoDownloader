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
import com.example.videodownloader.download.DownloadWorker
import com.example.videodownloader.util.UrlParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)
    private val settings = SettingsRepository(app)

    init {
        viewModelScope.launch {
            repo.scanFolder()
        }
    }

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

    /**
     * Ставит загрузку в очередь.
     * @param audio true — скачать только аудио (mp3), false — видео с качеством из настроек.
     */
    fun enqueue(raw: String, audio: Boolean = false) {
        val parsed = UrlParser.parse(raw) ?: return
        viewModelScope.launch {
            val quality = settings.getVideoQuality()
            val title = when {
                audio -> "Аудио • ${parsed.service}"
                else -> "Видео • ${parsed.service}"
            }
            val id = repo.add(parsed.value, title)
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        DownloadWorker.KEY_URL to parsed.value,
                        DownloadWorker.KEY_ID to id,
                        DownloadWorker.KEY_QUALITY to quality.name,
                        DownloadWorker.KEY_AUDIO to audio
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(getApplication()).enqueue(request)
        }
    }

    /** Удаляет запись вместе с файлом. */
    fun delete(item: DownloadEntity) = viewModelScope.launch {
        try {
            WorkManager.getInstance(getApplication())
                .cancelAllWorkByTag(item.id.toString())
        } catch (_: Exception) { }
        repo.deleteWithFile(item)
    }

    /** Массовое удаление (мультивыбор). */
    fun deleteMany(items: List<DownloadEntity>) = viewModelScope.launch {
        items.forEach { item ->
            try {
                WorkManager.getInstance(getApplication())
                    .cancelAllWorkByTag(item.id.toString())
            } catch (_: Exception) { }
            repo.deleteWithFile(item)
        }
    }

    fun rescanFolder() = viewModelScope.launch {
        repo.scanFolder()
    }
}

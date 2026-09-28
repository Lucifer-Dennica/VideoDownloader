package com.example.videodownloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Качество видео. Сейчас в UI скрыто, вернём когда заведётся YouTube.
 */
enum class VideoQuality(
    val label: String,
    val cobaltValue: String,
    val tikwmHd: Int
) {
    MAX("Максимум", "max", 1),
    P1080("1080p", "1080", 1),
    P720("720p", "720", 1),
    P480("480p", "480", 0),
    P360("360p", "360", 0);

    companion object {
        fun fromName(name: String?): VideoQuality =
            entries.firstOrNull { it.name == name } ?: MAX
    }
}

/** Что показывать в бейдже на вкладке «Загрузки». */
enum class BadgeMode(val label: String) {
    OFF("Выключен"),
    QUEUE("В очереди"),
    TOTAL("Всего скачано");

    companion object {
        fun fromName(name: String?): BadgeMode =
            entries.firstOrNull { it.name == name } ?: OFF
    }
}

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val AUDIO_ONLY = booleanPreferencesKey("audio_only")
        val AUTO_PASTE = booleanPreferencesKey("auto_paste")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
        val LAST_SCAN = longPreferencesKey("last_scan")
        val BADGE_MODE = stringPreferencesKey("badge_mode")
    }

    val videoQuality: Flow<VideoQuality> = appContext.settingsDataStore.data
        .map { VideoQuality.fromName(it[Keys.VIDEO_QUALITY]) }

    val audioOnly: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.AUDIO_ONLY] ?: false }

    val autoPaste: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.AUTO_PASTE] ?: false }

    val autoDownload: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.AUTO_DOWNLOAD] ?: false }

    val badgeMode: Flow<BadgeMode> = appContext.settingsDataStore.data
        .map { BadgeMode.fromName(it[Keys.BADGE_MODE]) }

    suspend fun setVideoQuality(value: VideoQuality) {
        appContext.settingsDataStore.edit { it[Keys.VIDEO_QUALITY] = value.name }
    }

    suspend fun setAudioOnly(value: Boolean) {
        appContext.settingsDataStore.edit { it[Keys.AUDIO_ONLY] = value }
    }

    suspend fun setAutoPaste(value: Boolean) {
        appContext.settingsDataStore.edit { it[Keys.AUTO_PASTE] = value }
    }

    suspend fun setAutoDownload(value: Boolean) {
        appContext.settingsDataStore.edit { it[Keys.AUTO_DOWNLOAD] = value }
    }

    suspend fun setBadgeMode(value: BadgeMode) {
        appContext.settingsDataStore.edit { it[Keys.BADGE_MODE] = value.name }
    }

    suspend fun getVideoQuality(): VideoQuality = videoQuality.first()
    suspend fun getAudioOnly(): Boolean = audioOnly.first()

    suspend fun getLastScanTime(): Long =
        appContext.settingsDataStore.data.map { it[Keys.LAST_SCAN] ?: 0L }.first()

    suspend fun setLastScanTime(value: Long) {
        appContext.settingsDataStore.edit { it[Keys.LAST_SCAN] = value }
    }
}

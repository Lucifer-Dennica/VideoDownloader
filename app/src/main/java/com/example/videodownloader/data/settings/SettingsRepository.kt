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

/** Режим оформления. */
enum class ThemeMode(val label: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    NEON("Неоновая"),
    AMOLED("AMOLED"),
    OCEAN("Океан");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BADGE_QUEUE = booleanPreferencesKey("badge_queue")
        val BADGE_TOTAL = booleanPreferencesKey("badge_total")
    }

    val videoQuality: Flow<VideoQuality> = appContext.settingsDataStore.data
        .map { VideoQuality.fromName(it[Keys.VIDEO_QUALITY]) }

    val audioOnly: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.AUDIO_ONLY] ?: false }

    val autoPaste: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.AUTO_PASTE] ?: false }

    val autoDownload: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.AUTO_DOWNLOAD] ?: false }

    val themeMode: Flow<ThemeMode> = appContext.settingsDataStore.data
        .map { ThemeMode.fromName(it[Keys.THEME_MODE]) }

    val badgeQueue: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.BADGE_QUEUE] ?: false }

    val badgeTotal: Flow<Boolean> = appContext.settingsDataStore.data
        .map { it[Keys.BADGE_TOTAL] ?: false }

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

    suspend fun setThemeMode(value: ThemeMode) {
        appContext.settingsDataStore.edit { it[Keys.THEME_MODE] = value.name }
    }

    suspend fun setBadgeQueue(value: Boolean) {
        appContext.settingsDataStore.edit { it[Keys.BADGE_QUEUE] = value }
    }

    suspend fun setBadgeTotal(value: Boolean) {
        appContext.settingsDataStore.edit { it[Keys.BADGE_TOTAL] = value }
    }

    suspend fun getVideoQuality(): VideoQuality = videoQuality.first()
    suspend fun getAudioOnly(): Boolean = audioOnly.first()
    suspend fun getThemeMode(): ThemeMode = themeMode.first()

    suspend fun getLastScanTime(): Long =
        appContext.settingsDataStore.data.map { it[Keys.LAST_SCAN] ?: 0L }.first()

    suspend fun setLastScanTime(value: Long) {
        appContext.settingsDataStore.edit { it[Keys.LAST_SCAN] = value }
    }
}

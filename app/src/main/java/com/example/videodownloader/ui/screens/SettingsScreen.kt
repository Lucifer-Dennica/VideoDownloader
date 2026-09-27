package com.example.videodownloader.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.example.videodownloader.BuildConfig
import com.example.videodownloader.data.settings.SettingsRepository
import com.example.videodownloader.data.settings.VideoQuality
import com.example.videodownloader.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(onChooseFolder: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var showInstagramHelp by remember { mutableStateOf(false) }
    var showYoutubeHelp by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    val videoQuality by settings.videoQuality.collectAsState(initial = VideoQuality.MAX)
    val audioOnly by settings.audioOnly.collectAsState(initial = false)
    val autoPaste by settings.autoPaste.collectAsState(initial = false)
    val autoDownload by settings.autoDownload.collectAsState(initial = false)

    val cookiesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val cookiesFile = File(context.filesDir, "cookies.txt")
                inputStream?.use { input ->
                    cookiesFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, "Cookies загружены", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showInstagramHelp) {
        InfoScreen(
            title = "Как скачать из Instagram",
            content = INSTAGRAM_HELP,
            onBack = { showInstagramHelp = false }
        )
        return
    }
    if (showYoutubeHelp) {
        InfoScreen(
            title = "YouTube и ограничения",
            content = YOUTUBE_HELP,
            onBack = { showYoutubeHelp = false }
        )
        return
    }

    // Диалог выбора качества
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Качество видео") },
            text = {
                Column {
                    VideoQuality.entries.forEach { q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { settings.setVideoQuality(q) }
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = videoQuality == q,
                                onClick = {
                                    scope.launch { settings.setVideoQuality(q) }
                                    showQualityDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(q.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = { Text("Правила использования") },
            text = {
                Text(
                    "Приложение предназначено для личного использования. " +
                    "Скачивайте только тот контент, на который у вас есть права. " +
                    "Мы не храним видео на серверах и не передаём данные третьим лицам."
                )
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) { Text("Понятно") }
            }
        )
    }

    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = { Text("Безопасность") },
            text = {
                Text(
                    "Все данные хранятся только на вашем устройстве. " +
                    "Приложение не отправляет ссылки на сторонние серверы, " +
                    "кроме тех, что нужны для скачивания видео."
                )
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDialog = false }) { Text("Понятно") }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ============ РАЗДЕЛ: ЗАГРУЗКА ============
        SectionHeader("Загрузка")

        SettingItem(
            icon = Icons.Default.Tune,
            title = "Качество видео",
            subtitle = videoQuality.label,
            onClick = { showQualityDialog = true }
        )

        SettingSwitch(
            icon = Icons.Default.ContentPaste,
            title = "Автопаста из буфера",
            subtitle = "Подставлять ссылку автоматически",
            checked = autoPaste,
            onCheckedChange = { scope.launch { settings.setAutoPaste(it) } }
        )

        SettingSwitch(
            icon = Icons.Default.Download,
            title = "Автоскачивание",
            subtitle = if (audioOnly) "Недоступно в режиме «Только аудио»"
                       else "Начинать скачивание сразу после вставки",
            checked = autoDownload && !audioOnly,
            enabled = !audioOnly,
            onCheckedChange = { scope.launch { settings.setAutoDownload(it) } }
        )

        SettingSwitch(
            icon = Icons.Default.MusicNote,
            title = "Только аудио",
            subtitle = "Скачивать только звук (M4A)",
            checked = audioOnly,
            onCheckedChange = { enabled ->
                scope.launch {
                    settings.setAudioOnly(enabled)
                    if (enabled && autoDownload) {
                        settings.setAutoDownload(false)
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // ============ РАЗДЕЛ: ФАЙЛЫ ============
        SectionHeader("Файлы")

        SettingItem(
            icon = Icons.Default.Folder,
            title = "Папка сохранения",
            subtitle = "DCIM/VideoDownloader",
            onClick = onChooseFolder
        )

        SettingItem(
            icon = Icons.Default.Upload,
            title = "Загрузить cookies.txt",
            subtitle = "Для Instagram и приватного контента",
            onClick = { cookiesPicker.launch("text/plain") }
        )

        SettingItem(
            icon = Icons.Default.Delete,
            title = "Очистить очередь",
            subtitle = "Отменить все активные загрузки",
            onClick = {
                WorkManager.getInstance(context).cancelAllWork()
                Toast.makeText(context, "Очередь очищена", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(Modifier.height(16.dp))

        // ============ РАЗДЕЛ: СПРАВКА ============
        SectionHeader("Справка")

        SettingItem(
            icon = Icons.Default.HelpOutline,
            title = "Instagram — как скачивать",
            subtitle = "Требуется авторизация",
            onClick = { showInstagramHelp = true }
        )

        SettingItem(
            icon = Icons.Default.HelpOutline,
            title = "YouTube — ограничения",
            subtitle = "Почему иногда требует вход",
            onClick = { showYoutubeHelp = true }
        )

        Spacer(Modifier.height(16.dp))

        // ============ РАЗДЕЛ: О ПРИЛОЖЕНИИ ============
        SectionHeader("О приложении")

        SettingItem(
            icon = Icons.Default.Info,
            title = "Версия",
            subtitle = BuildConfig.VERSION_NAME,
            onClick = { }
        )

        SettingItem(
            icon = Icons.Default.Refresh,
            title = "Проверить обновления",
            subtitle = if (isCheckingUpdate) "Проверяю…" else "Последняя версия с GitHub",
            onClick = {
                if (isCheckingUpdate) return@SettingItem
                isCheckingUpdate = true
                CoroutineScope(Dispatchers.IO).launch {
                    val update = UpdateChecker.checkForUpdate()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        isCheckingUpdate = false
                        if (update != null) {
                            UpdateChecker.showUpdateDialog(context, update)
                        } else {
                            Toast.makeText(context, "У вас последняя версия", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // ============ РАЗДЕЛ: ПРАВОВАЯ ИНФОРМАЦИЯ ============
        SectionHeader("Правовая информация")

        SettingItem(
            icon = Icons.Default.Shield,
            title = "Правила использования",
            subtitle = "Скачивайте только свой контент",
            onClick = { showRulesDialog = true }
        )

        SettingItem(
            icon = Icons.Default.CheckCircle,
            title = "Безопасность",
            subtitle = "Cookies хранятся только на устройстве",
            onClick = { showSecurityDialog = true }
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "VideoDownloader ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun InfoScreen(title: String, content: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(content, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}

private val INSTAGRAM_HELP = """
Instagram — самая защищённая платформа. Он блокирует автоматические запросы и требует авторизации.

Что делать:
1. Откройте Instagram в браузере.
2. Войдите в свой аккаунт.
3. Установите расширение «Get cookies.txt LOCALLY».
4. Откройте расширение и нажмите Export.
5. Сохраните cookies.txt и загрузите в настройках приложения.

Cookies хранятся только на вашем устройстве.
""".trimIndent()

private val YOUTUBE_HELP = """
YouTube защищается от автоматических загрузок. Иногда он требует подтвердить, что вы не бот.

Если YouTube не качается:
1. Подождите 2–3 часа и попробуйте снова.
2. Если не помогает — нужны cookies от аккаунта.
3. Создайте отдельный тестовый Google-аккаунт (не личный!).
4. Экспортируйте cookies и передайте в yt-dlp сервер.

Основные соцсети (TikTok, Facebook, VK и др.) работают без ограничений.
""".trimIndent()

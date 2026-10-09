package com.example.videodownloader.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import com.example.videodownloader.BuildConfig
import com.example.videodownloader.data.settings.SettingsRepository
import com.example.videodownloader.data.settings.ThemeMode
import com.example.videodownloader.ui.viewmodel.DownloadViewModel
import com.example.videodownloader.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    onChooseFolder: () -> Unit,
    vm: DownloadViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var showSupported by remember { mutableStateOf(false) }
    var showWhyBetter by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val audioOnly by settings.audioOnly.collectAsState(initial = false)
    val autoPaste by settings.autoPaste.collectAsState(initial = false)
    val autoDownload by settings.autoDownload.collectAsState(initial = false)
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val badgeQueue by settings.badgeQueue.collectAsState(initial = false)
    val badgeTotal by settings.badgeTotal.collectAsState(initial = false)

    val stats by vm.statistics.collectAsState()

    LaunchedEffect(Unit) { vm.loadStatistics() }

    if (showSupported) {
        InfoScreen("Поддерживаемые сервисы", SUPPORTED, { showSupported = false }); return
    }
    if (showWhyBetter) {
        InfoScreen("Почему наше лучше", WHY_BETTER, { showWhyBetter = false }); return
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Тема оформления") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { settings.setThemeMode(mode) }
                                showThemeDialog = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = {
                                scope.launch { settings.setThemeMode(mode) }
                                showThemeDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Закрыть") } }
        )
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = { Text("Правила использования") },
            text = { Text("Приложение предназначено для личного использования. Скачивайте только тот контент, на который у вас есть права.") },
            confirmButton = { TextButton(onClick = { showRulesDialog = false }) { Text("Понятно") } }
        )
    }
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = { Text("Безопасность") },
            text = { Text("Все данные хранятся только на вашем устройстве.") },
            confirmButton = { TextButton(onClick = { showSecurityDialog = false }) { Text("Понятно") } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp))

        SectionHeader("Загрузка")

        SettingSwitch(Icons.Default.ContentPaste, "Автопаста из буфера",
            "Подставлять ссылку автоматически", autoPaste) {
            scope.launch { settings.setAutoPaste(it) }
        }
        SettingSwitch(Icons.Default.Download, "Автоскачивание",
            if (audioOnly) "Недоступно в режиме «Только аудио»" else "Скачивать сразу после вставки",
            autoDownload && !audioOnly, enabled = !audioOnly) {
            scope.launch { settings.setAutoDownload(it) }
        }
        SettingSwitch(Icons.Default.MusicNote, "Только аудио",
            "Скачивать только звук", audioOnly) { enabled ->
            scope.launch {
                settings.setAudioOnly(enabled)
                if (enabled && autoDownload) settings.setAutoDownload(false)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Интерфейс")

        SettingItem(Icons.Default.Palette, "Тема", themeMode.label) { showThemeDialog = true }

        SettingSwitch(Icons.Default.Badge, "Счётчик очереди", "Показывать на «Главной»", badgeQueue) {
            scope.launch { settings.setBadgeQueue(it) }
        }
        SettingSwitch(Icons.Default.Badge, "Счётчик всего", "Показывать на «Загрузках»", badgeTotal) {
            scope.launch { settings.setBadgeTotal(it) }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Статистика")
        val s = stats
        StatItem("Всего скачано", s?.total?.toString() ?: "…")
        StatItem("За 30 дней", s?.thisMonth?.toString() ?: "…")
        StatItem("Общий размер", s?.let { formatSize(it.totalSizeBytes) } ?: "…")

        Spacer(Modifier.height(16.dp))
        SectionHeader("Файлы")

        SettingItem(Icons.Default.Folder, "Папка сохранения", "DCIM/VideoDownloader", onChooseFolder)

        SettingItem(Icons.Default.Delete, "Очистить очередь", "Отменить все активные загрузки") {
            WorkManager.getInstance(context).cancelAllWork()
            Toast.makeText(context, "Очередь очищена", Toast.LENGTH_SHORT).show()
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Справка")

        SettingItem(Icons.Default.Star, "Почему наше лучше", "Преимущества приложения") {
            showWhyBetter = true
        }
        SettingItem(Icons.AutoMirrored.Filled.HelpOutline, "Поддерживаемые сервисы",
            "Что работает сейчас, что в разработке") { showSupported = true }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Обратная связь")

        SettingItem(Icons.Default.Favorite, "Поддержать разработчика",
            "Донат на DonationAlerts") {
            openUrl(context, "https://www.donationalerts.com/r/lucifer_dennica")
        }
        SettingItem(Icons.Default.Share, "Поделиться приложением", "Отправить другу") {
            shareApp(context)
        }
        SettingItem(Icons.Default.Star, "Оценить в RuStore", "Оставьте отзыв") {
            openUrl(context, RUSTORE_URL)
        }
        SettingItem(Icons.Default.Email, "Написать разработчику", SUPPORT_EMAIL) {
            sendEmail(context)
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("О приложении")

        SettingItem(Icons.Default.Info, "Версия", BuildConfig.VERSION_NAME) { }
        SettingItem(Icons.Default.Refresh, "Проверить обновления",
            if (isCheckingUpdate) "Проверяю…" else "Последняя версия") {
            if (isCheckingUpdate) return@SettingItem
            isCheckingUpdate = true
            CoroutineScope(Dispatchers.IO).launch {
                val update = UpdateChecker.checkForUpdate()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isCheckingUpdate = false
                    if (update != null) UpdateChecker.showUpdateDialog(context, update)
                    else Toast.makeText(context, "У вас последняя версия", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Правовая информация")

        SettingItem(Icons.Default.Shield, "Правила использования", "Личное использование") { showRulesDialog = true }
        SettingItem(Icons.Default.CheckCircle, "Безопасность", "Данные не передаются наружу") { showSecurityDialog = true }

        Spacer(Modifier.height(32.dp))
        Text("VideoDownloader ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private const val RUSTORE_URL = "#"   // ⚠️ заменить после публикации
private const val SUPPORT_EMAIL = "denis22142qwe@gmail.com"

private fun openUrl(context: android.content.Context, url: String) {
    if (url == "#") {
        Toast.makeText(context, "Ссылка появится позже", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) { }
}

private fun shareApp(context: android.content.Context) {
    val text = "VideoDownloader — скачивай видео из Rutube, TikTok, Facebook, Instagram, Pinterest без водяного знака!"
    try {
        context.startActivity(android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }, "Поделиться"))
    } catch (_: Exception) { }
}

private fun sendEmail(context: android.content.Context) {
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO,
            android.net.Uri.parse("mailto:$SUPPORT_EMAIL")).apply {
            putExtra(android.content.Intent.EXTRA_SUBJECT, "VideoDownloader — обратная связь")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Напишите на $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
private fun SettingItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun StatItem(title: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f ГБ", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f МБ", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f КБ", kb)
        else -> "$bytes Б"
    }
}

@Composable
private fun InfoScreen(title: String, content: String, onBack: () -> Unit) {
    BackHandler(enabled = true) { onBack() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(content, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }
}

private val SUPPORTED = """
✅ Работает сейчас:

• TikTok — видео, фото-карусели, аудио
• Rutube — видео
• Facebook — видео
• Instagram — публичные Reels
• Pinterest — видео-пины

🕐 Скоро добавим:

• YouTube
• VK
• Twitter / X
• Reddit
• SoundCloud
• Snapchat

И другие источники.
""".trimIndent()

private val WHY_BETTER = """
VideoDownloader — не просто «скачать видео».

✅ БЕЗ ВОДЯНОГО ЗНАКА
Встроенная кнопка в TikTok ставит логотип и ник автора. Мы скачиваем чистое видео.

✅ 5 СЕРВИСОВ В ОДНОМ
TikTok, Rutube, Facebook, Instagram, Pinterest — всё в одном приложении.

✅ ФОТО-КАРУСЕЛИ
Сохраняем все фото из поста TikTok в отдельную папку.

✅ ТОЛЬКО АУДИО
Режим «Только аудио» — mp3/m4a без видео.

✅ ПАКЕТНОЕ СКАЧИВАНИЕ
Вставьте 10 ссылок — все скачаются.

✅ ФОНОВАЯ ЗАГРУЗКА
Сверните приложение — загрузка продолжится.

✅ СОРТИРОВКА И ФИЛЬТРЫ
Найдите нужное видео за секунду.

✅ БЕСПЛАТНО И БЕЗ РЕКЛАМЫ
(реклама будет, но не навязчивая)
""".trimIndent()

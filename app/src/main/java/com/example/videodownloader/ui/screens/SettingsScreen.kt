package com.example.videodownloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.work.WorkManager
import androidx.lifecycle.viewmodel.compose.viewModel
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

    var showSupportedHelp by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        vm.loadStatistics()
    }

    if (showSupportedHelp) {
        InfoScreen(
            title = "Поддерживаемые сервисы",
            content = SUPPORTED_SERVICES,
            onBack = { showSupportedHelp = false }
        )
        return
    }
    if (showWhyBetter) {
        InfoScreen(
            title = "Почему наше лучше",
            content = WHY_BETTER,
            onBack = { showWhyBetter = false }
        )
        return
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Тема оформления") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { settings.setThemeMode(mode) }
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    scope.launch { settings.setThemeMode(mode) }
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Закрыть") }
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

        SectionHeader("Загрузка")

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
            subtitle = "Скачивать только звук (только TikTok)",
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

        SectionHeader("Интерфейс")

        SettingItem(
            icon = Icons.Default.Palette,
            title = "Тема",
            subtitle = themeMode.label,
            onClick = { showThemeDialog = true }
        )

        SettingSwitch(
            icon = Icons.Default.Badge,
            title = "Счётчик очереди",
            subtitle = "Показывать на «Главной»",
            checked = badgeQueue,
            onCheckedChange = { scope.launch { settings.setBadgeQueue(it) } }
        )

        SettingSwitch(
            icon = Icons.Default.Badge,
            title = "Счётчик всего",
            subtitle = "Показывать на «Загрузках»",
            checked = badgeTotal,
            onCheckedChange = { scope.launch { settings.setBadgeTotal(it) } }
        )

        Spacer(Modifier.height(16.dp))

        SectionHeader("Статистика")

        val s = stats
        StatItem(title = "Всего скачано", value = s?.total?.toString() ?: "…")
        StatItem(title = "За последние 30 дней", value = s?.thisMonth?.toString() ?: "…")
        StatItem(title = "Общий размер", value = s?.let { formatSize(it.totalSizeBytes) } ?: "…")

        Spacer(Modifier.height(16.dp))

        SectionHeader("Файлы")

        SettingItem(
            icon = Icons.Default.Folder,
            title = "Папка сохранения",
            subtitle = "DCIM/VideoDownloader",
            onClick = onChooseFolder
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

        SectionHeader("Справка")

        SettingItem(
            icon = Icons.Default.Star,
            title = "Почему наше лучше",
            subtitle = "Преимущества приложения",
            onClick = { showWhyBetter = true }
        )

        SettingItem(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            title = "Поддерживаемые сервисы",
            subtitle = "Что работает сейчас, что в разработке",
            onClick = { showSupportedHelp = true }
        )

        Spacer(Modifier.height(16.dp))

        // ================= ОБРАТНАЯ СВЯЗЬ И ПОДДЕРЖКА =================
        SectionHeader("Обратная связь")

        SettingItem(
            icon = Icons.Default.Favorite,
            title = "Поддержать разработчика",
            subtitle = "Добровольный донат на DonationAlerts",
            onClick = { openUrl(context, SUPPORT_URL) }
        )

        SettingItem(
            icon = Icons.Default.Share,
            title = "Поделиться приложением",
            subtitle = "Отправить ссылку другу",
            onClick = { shareApp(context) }
        )

        SettingItem(
            icon = Icons.Default.Star,
            title = "Оценить в RuStore",
            subtitle = "Поставьте оценку — это помогает проекту",
            onClick = { openUrl(context, RUSTORE_URL) }
        )

        SettingItem(
            icon = Icons.AutoMirrored.Filled.Send,
            title = "Telegram-канал",
            subtitle = TELEGRAM_DISPLAY,
            onClick = { openUrl(context, TELEGRAM_URL) }
        )

        SettingItem(
            icon = Icons.Default.Email,
            title = "Написать разработчику",
            subtitle = SUPPORT_EMAIL,
            onClick = { sendEmail(context) }
        )

        Spacer(Modifier.height(16.dp))

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
            subtitle = "Никакие данные не передаются наружу",
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

// ================= КОНСТАНТЫ ССЫЛОК =================

// Ссылка на страницу донатов DonationAlerts
private const val SUPPORT_URL = "https://www.donationalerts.com/r/lucifer_dennica"

// ⚠️ ЗАМЕНИТЬ после публикации в RuStore:
// Вставь реальную ссылку вида: "https://www.rustore.ru/catalog/app/com.example.videodownloader"
private const val RUSTORE_URL = "#"

// Telegram
private const val TELEGRAM_URL = "https://t.me/Lucifer_Denicca_22142"
private const val TELEGRAM_DISPLAY = "@Lucifer_Denicca_22142"

// Почта разработчика
private const val SUPPORT_EMAIL = "denis22142qwe@gmail.com"

// ================= ХЕЛПЕРЫ =================

private fun openUrl(context: android.content.Context, url: String) {
    if (url == "#" || url.isBlank()) {
        Toast.makeText(context, "Ссылка появится после публикации в RuStore", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

private fun shareApp(context: android.content.Context) {
    // Если ссылка на RuStore ещё не вставлена — делимся текстом без неё
    val hasRuStore = RUSTORE_URL != "#" && RUSTORE_URL.isNotBlank()
    val text = if (hasRuStore) {
        "Скачивай видео из TikTok и Facebook без водяного знака: $RUSTORE_URL"
    } else {
        "VideoDownloader — скачивай видео из TikTok и Facebook без водяного знака"
    }

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "VideoDownloader")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться"))
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось поделиться", Toast.LENGTH_SHORT).show()
    }
}

private fun sendEmail(context: android.content.Context) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "VideoDownloader — обратная связь")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Нет почтового клиента. Напишите на $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
    }
}

// ================= UI-КОМПОНЕНТЫ =================

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
private fun StatItem(title: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
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
    BackHandler(enabled = true) {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(content, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}

private val SUPPORTED_SERVICES = """
✅ Работает стабильно:

• TikTok — видео, фото-карусели, аудио
• Facebook — видео

⚠️ В разработке:

• YouTube
• Instagram
• VK
• Twitter / X
• Reddit
• Pinterest
• Snapchat
• SoundCloud

Мы работаем над добавлением всех этих сервисов. Следите за обновлениями!
""".trimIndent()

private val WHY_BETTER = """
VideoDownloader — не просто «скачать видео». Вот что делает его особенным:

✅ БЕЗ ВОДЯНОГО ЗНАКА
TikTok встроенная кнопка «Скачать» ставит логотип и ник автора поверх видео. Мы скачиваем чистую версию — без меток, без логотипов.

✅ ОБХОД ЗАПРЕТА НА СКАЧИВАНИЕ
Если автор запретил скачивание — кнопка в TikTok исчезает. У нас ссылка работает всё равно: мы обращаемся к серверу напрямую, а не через интерфейс.

✅ ФОТО-КАРУСЕЛИ
TikTok не даёт скачать картинки из постов-каруселей. Мы сохраняем все фото (8, 10, сколько есть) в отдельную папку — по одной карточке на альбом.

✅ ТОЛЬКО АУДИО
Хотите сохранить только музыку из видео TikTok? Включите режим «Только аудио» — получите mp3/m4a без видео.

✅ ПАКЕТНОЕ СКАЧИВАНИЕ
Вставьте сразу 10 ссылок — по одной на строку. Все уйдут в очередь и скачаются параллельно.

✅ ФОНОВАЯ ЗАГРУЗКА
Можно свернуть приложение — загрузка продолжится. TikTok требует держать экран открытым.

✅ ОРГАНИЗАЦИЯ
Файлы сохраняются по папкам: DCIM/VideoDownloader/TikTok/, /Facebook/. Никакой свалки в общем DCIM.

✅ БЕСПЛАТНО И БЕЗ РЕКЛАМЫ
Никаких подписок, никаких всплывающих окон, никаких «премиум» функций.
""".trimIndent()

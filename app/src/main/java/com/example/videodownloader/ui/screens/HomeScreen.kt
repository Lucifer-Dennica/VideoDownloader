package com.example.videodownloader.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.videodownloader.data.local.DownloadEntity
import com.example.videodownloader.data.settings.SettingsRepository
import com.example.videodownloader.ui.components.LinkInputField
import com.example.videodownloader.ui.viewmodel.PreviewState
import com.example.videodownloader.util.UrlParser
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    initialLink: String,
    activeItems: List<DownloadEntity>,
    preview: PreviewState,
    onPreviewRequest: (String) -> Unit,
    onPreviewClear: () -> Unit,
    onDownloadVideo: (String) -> Unit,
    onDownloadAudio: (String) -> Unit,
    onDownloadMany: (String) -> Unit,
    onDelete: (DownloadEntity) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var link by remember(initialLink) { mutableStateOf(initialLink) }
    val parsedList = remember(link) { UrlParser.parseAll(link) }
    val hasText = link.isNotBlank()

    val autoPaste by settings.autoPaste.collectAsState(initial = false)
    val autoDownload by settings.autoDownload.collectAsState(initial = false)
    val audioOnly by settings.audioOnly.collectAsState(initial = false)

    // Автопаста
    DisposableEffect(lifecycleOwner, autoPaste) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && autoPaste && link.isBlank()) {
                val clip = try {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                } catch (_: Exception) { null }

                if (!clip.isNullOrBlank() && UrlParser.parseAll(clip).isNotEmpty()) {
                    link = clip
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Фоновое получение превью (с debounce)
    LaunchedEffect(link) {
        if (link.isBlank()) {
            onPreviewClear()
            return@LaunchedEffect
        }
        if (parsedList.size == 1) {
            delay(800)
            onPreviewRequest(parsedList.first().value)
        } else {
            onPreviewClear()
        }
    }

    // Автоскачивание
    LaunchedEffect(link, autoDownload, audioOnly) {
        if (!autoDownload || audioOnly) return@LaunchedEffect
        if (parsedList.size != 1) return@LaunchedEffect
        delay(1500)
        onDownloadVideo(parsedList.first().value)
        link = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Заголовок + индикатор загрузки превью ---
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "VideoDownloader",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            if (preview is PreviewState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            "Вставьте ссылку — одну или несколько, по одной на строку.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LinkInputField(link) { link = it }

        // --- Ошибка валидации ---
        if (hasText && parsedList.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "⚠️ Ссылка не поддерживается",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // --- Превью-карточка (если есть данные) ---
        val successPreview = preview as? PreviewState.Success
        if (successPreview != null) {
            PreviewCard(
                title = successPreview.title,
                thumbnail = successPreview.thumbnail,
                duration = successPreview.duration,
                service = successPreview.service
            )
        }

        // --- Предупреждение "скоро" ---
        val comingSoon = preview as? PreviewState.ComingSoon
        if (comingSoon != null) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "🕐 ${comingSoon.service}: скоро добавим",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // --- Кнопки скачивания — активны сразу ---
        if (parsedList.isNotEmpty()) {
            val isMulti = parsedList.size >= 2

            if (audioOnly) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isMulti) onDownloadMany(link) else onDownloadVideo(link)
                            link = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isMulti) "🎬 Видео (${parsedList.size})" else "🎬 Скачать")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isMulti) onDownloadMany(link) else onDownloadAudio(link)
                            link = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isMulti) "🎵 Аудио (${parsedList.size})" else "🎵 Аудио")
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (isMulti) onDownloadMany(link) else onDownloadVideo(link)
                        link = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isMulti) "🎬 Скачать все (${parsedList.size})" else "🎬 Скачать")
                }
            }
        }

        Text(
            "Скачивайте только те материалы, на которые у вас есть права.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (activeItems.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Активные загрузки", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activeItems, key = { it.id }) { item ->
                    ActiveDownloadCard(item) { onDelete(item) }
                }
            }
        }
    }
}

/** Карточка превью (только отображение, кнопки — выше). */
@Composable
private fun PreviewCard(
    title: String,
    thumbnail: String?,
    duration: Int?,
    service: String
) {
    val context = LocalContext.current
    val durationText = duration?.let {
        val m = it / 60
        val s = it % 60
        "%d:%02d".format(m, s)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnail)
                            .crossfade(true)
                            .size(300, 170)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("🎬", style = MaterialTheme.typography.displaySmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(service, durationText).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(item: DownloadEntity, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Отмена") }
            }
            Spacer(Modifier.height(4.dp))
            when (item.status) {
                "QUEUED" -> {
                    Text("⏳ В очереди", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                "DOWNLOADING" -> {
                    // Если идёт обработка на сервере — показываем неопределённый прогресс
                    val isProcessing = item.error?.contains("Обработка") == true
                    if (isProcessing) {
                        Text("⚙️ Обработка на сервере…", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("⬇️ Скачивание ${item.progress}%", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                "ERROR" -> Text(
                    "❌ ${item.error ?: "Ошибка"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

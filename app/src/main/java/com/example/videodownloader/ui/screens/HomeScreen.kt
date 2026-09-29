package com.example.videodownloader.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.videodownloader.data.local.DownloadEntity
import com.example.videodownloader.data.settings.SettingsRepository
import com.example.videodownloader.ui.components.LinkInputField
import com.example.videodownloader.util.UrlParser
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    initialLink: String,
    activeItems: List<DownloadEntity>,
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

    LaunchedEffect(link, autoDownload, audioOnly) {
        if (!autoDownload || audioOnly) return@LaunchedEffect
        if (parsedList.size != 1) return@LaunchedEffect
        delay(800)
        onDownloadVideo(parsedList.first().value)
        link = ""
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("VideoDownloader", style = MaterialTheme.typography.headlineLarge)
        Text("Вставьте ссылку из TikTok или Facebook — одну или несколько, по одной на строку.")

        LinkInputField(link) { link = it }

        when {
            !hasText -> Unit
            parsedList.isEmpty() -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "⚠️ Ссылка не поддерживается. Сейчас работаем с TikTok и Facebook.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            parsedList.size == 1 -> {
                AssistChip(
                    onClick = {},
                    label = { Text("Источник: ${parsedList.first().service}") }
                )
            }
            else -> {
                val servicesText = parsedList
                    .map { it.service }
                    .distinct()
                    .joinToString(", ")
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "Найдено ${parsedList.size} ссылок: $servicesText",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        val isMulti = parsedList.size >= 2

        if (audioOnly) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isMulti) onDownloadMany(link)
                        else onDownloadVideo(link)
                        link = ""
                    },
                    enabled = parsedList.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isMulti) "🎬 Видео (${parsedList.size})" else "🎬 Скачать")
                }
                OutlinedButton(
                    onClick = {
                        if (isMulti) onDownloadMany(link)
                        else onDownloadAudio(link)
                        link = ""
                    },
                    enabled = parsedList.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isMulti) "🎵 Аудио (${parsedList.size})" else "🎵 Аудио")
                }
            }
        } else {
            Button(
                onClick = {
                    if (isMulti) onDownloadMany(link)
                    else onDownloadVideo(link)
                    link = ""
                },
                enabled = parsedList.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isMulti) "🎬 Скачать все (${parsedList.size})"
                    else "🎬 Скачать"
                )
            }
        }

        Text(
            "Скачивайте только те материалы, на которые у вас есть права.",
            style = MaterialTheme.typography.bodySmall
        )

        if (activeItems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Активные загрузки", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activeItems, key = { it.id }) { item ->
                    ActiveDownloadCard(item) { onDelete(item) }
                }
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
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDelete) { Text("Отмена") }
            }
            Spacer(Modifier.height(4.dp))

            when (item.status) {
                "QUEUED" -> {
                    Text("⏳ В очереди", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                "DOWNLOADING" -> {
                    Text(
                        "⬇️ Скачивание ${item.progress}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
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

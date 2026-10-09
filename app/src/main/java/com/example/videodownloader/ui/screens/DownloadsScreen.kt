package com.example.videodownloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.videodownloader.data.local.DownloadEntity
import com.example.videodownloader.ui.components.DownloadItemCard
import com.example.videodownloader.util.ShareUtil
import java.io.File

enum class SortMode(val label: String) {
    DATE_DESC("Новые"),
    DATE_ASC("Старые"),
    NAME_ASC("По имени"),
    SIZE_DESC("По размеру")
}

@Composable
fun DownloadsScreen(
    items: List<DownloadEntity>,
    onDelete: (DownloadEntity) -> Unit,
    onDeleteMany: (List<DownloadEntity>) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onRescan: () -> Unit
) {
    val context = LocalContext.current

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.DATE_DESC) }
    var serviceFilter by remember { mutableStateOf("Все") }

    // Определяем доступные сервисы из items
    val allServices = remember(items) {
        val s = mutableSetOf<String>()
        for (item in items) s.add(detectService(item))
        listOf("Все") + s.sorted()
    }

    val filtered = remember(items, serviceFilter, sortMode) {
        val base = if (serviceFilter == "Все") items
                   else items.filter { detectService(it) == serviceFilter }
        when (sortMode) {
            SortMode.DATE_DESC -> base.sortedByDescending { it.createdAt }
            SortMode.DATE_ASC -> base.sortedBy { it.createdAt }
            SortMode.NAME_ASC -> base.sortedBy { it.title.lowercase() }
            SortMode.SIZE_DESC -> base.sortedByDescending { sizeOf(it) }
        }
    }

    val selectedItems = items.filter { it.id in selectedIds }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {

        // Верхняя панель
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { exitSelection() }) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "${selectedIds.size} выбрано",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row {
                    IconButton(
                        onClick = { if (selectedItems.isNotEmpty()) ShareUtil.shareMultiple(context, selectedItems) },
                        enabled = selectedItems.isNotEmpty()
                    ) { Text("📤", style = MaterialTheme.typography.titleMedium) }
                    IconButton(
                        onClick = { if (selectedItems.isNotEmpty()) showDeleteDialog = true },
                        enabled = selectedItems.isNotEmpty()
                    ) { Text("🗑", style = MaterialTheme.typography.titleMedium) }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Мои видео", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onRescan) { Text("🔄 Обновить") }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Фильтр по сервисам
        if (allServices.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allServices.forEach { service ->
                    FilterChip(
                        selected = serviceFilter == service,
                        onClick = { serviceFilter = service },
                        label = { Text(service, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Сортировка
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SortMode.entries.forEach { mode ->
                FilterChip(
                    selected = sortMode == mode,
                    onClick = { sortMode = mode },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (items.isEmpty()) "Здесь появятся скачанные видео"
                        else "Нет видео в этой категории",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        selectionMode = selectionMode,
                        selected = item.id in selectedIds,
                        onDelete = { onDelete(item) },
                        onOpen = { openItem(context, it) },
                        onOpenFolder = { openFolder(context, it) },
                        onShare = { ShareUtil.shareSingle(context, it) },
                        onRetry = { onRetry(it) },
                        onLongClick = {
                            selectionMode = true
                            selectedIds = setOf(item.id)
                        },
                        onToggleSelect = {
                            selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить ${selectedIds.size} элем.?") },
            text = { Text("Файлы будут удалены из памяти устройства безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMany(selectedItems)
                    showDeleteDialog = false
                    exitSelection()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}

private fun sizeOf(item: DownloadEntity): Long {
    val path = item.filePath ?: return 0L
    return try {
        if (path.startsWith("content://")) 0L
        else File(path).takeIf { it.exists() }?.length() ?: 0L
    } catch (_: Exception) { 0L }
}

private fun openItem(context: android.content.Context, item: DownloadEntity) {
    val path = item.filePath ?: run {
        Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
        return
    }
    val mimeType = when (item.type) {
        "PHOTOS" -> "image/*"
        "AUDIO" -> when {
            path.endsWith(".m4a", true) -> "audio/mp4"
            path.endsWith(".aac", true) -> "audio/aac"
            path.endsWith(".ogg", true) -> "audio/ogg"
            else -> "audio/mpeg"
        }
        else -> "video/mp4"
    }
    val uri: Uri = if (path.startsWith("content://")) Uri.parse(path) else {
        val file = File(path)
        if (!file.exists()) { Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show(); return }
        Uri.fromFile(file)
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(intent) }
    catch (e: Exception) { Toast.makeText(context, "Не открывается: ${e.message}", Toast.LENGTH_LONG).show() }
}

private fun openFolder(context: android.content.Context, item: DownloadEntity) {
    val service = detectService(item)
    val path = when {
        !item.folderPath.isNullOrBlank() -> item.folderPath.trimEnd('/')
        item.type == "AUDIO" -> "Music/VideoDownloader/$service/Audio"
        else -> "DCIM/VideoDownloader/$service"
    }
    try {
        val sm = context.getSystemService(android.content.Context.STORAGE_SERVICE) as StorageManager
        val intent = sm.primaryStorageVolume.createOpenDocumentTreeIntent()
        val encodedPath = "primary%3A" + Uri.encode(path)
        intent.putExtra("android.provider.extra.INITIAL_URI",
            Uri.parse("content://com.android.externalstorage.documents/root/$encodedPath"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
    } catch (_: Exception) { }
    Toast.makeText(context, "Путь: $path", Toast.LENGTH_LONG).show()
}

private fun detectService(item: DownloadEntity): String {
    if (item.url.startsWith("local://")) return item.url.removePrefix("local://")
    val url = item.url.lowercase()
    val title = item.title.lowercase()
    val path = (item.folderPath ?: item.filePath ?: "").lowercase()
    return when {
        url.contains("tiktok") || title.contains("tiktok") || path.contains("tiktok") -> "TikTok"
        url.contains("rutube") || title.contains("rutube") || path.contains("rutube") -> "Rutube"
        url.contains("facebook") || url.contains("fb.watch") || path.contains("facebook") -> "Facebook"
        url.contains("instagram") || path.contains("instagram") -> "Instagram"
        url.contains("pinterest") || url.contains("pin.it") || path.contains("pinterest") -> "Pinterest"
        url.contains("youtube") || url.contains("youtu.be") || path.contains("youtube") -> "YouTube"
        url.contains("vk.com") || path.contains("vk") -> "VK"
        else -> "Другое"
    }
}

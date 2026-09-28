package com.example.videodownloader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.videodownloader.data.local.DownloadEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onDelete: () -> Unit,
    onOpen: (DownloadEntity) -> Unit,
    onOpenFolder: (DownloadEntity) -> Unit,
    onShare: (DownloadEntity) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val context = LocalContext.current

    val dateText = remember(item.createdAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAt))
    }

    val isCarousel = item.type == "PHOTOS"
    val isAudio = item.type == "AUDIO"

    val thumbModel: Any? = remember(item.thumbnailUrl) {
        val t = item.thumbnailUrl ?: return@remember null
        when {
            t.startsWith("content://") -> t
            t.startsWith("http") -> t
            t.startsWith("/") -> File(t)
            else -> t
        }
    }

    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    when {
                        selectionMode -> onToggleSelect()
                        item.status == "COMPLETED" -> onOpen(item)
                    }
                },
                onLongClick = {
                    if (!selectionMode) onLongClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Spacer(Modifier.width(4.dp))
            }

            Box(
                modifier = Modifier
                    .width(124.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (thumbModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbModel)
                            .size(300, 170)
                            .allowHardware(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                isCarousel -> "🖼"
                                isAudio -> "🎵"
                                else -> "🎬"
                            },
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, topEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        when {
                            isCarousel -> "${item.itemCount} 🖼"
                            isAudio -> "🎵"
                            else -> "🎬"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))

                val statusText = when (item.status) {
                    "QUEUED" -> "⏳ В очереди"
                    "DOWNLOADING" -> "⬇️ ${item.progress}%"
                    "COMPLETED" -> when {
                        isCarousel -> "✅ Готово (${item.itemCount} фото)"
                        isAudio -> "✅ Аудио готово"
                        else -> "✅ Готово"
                    }
                    "ERROR" -> "❌ Ошибка"
                    else -> item.status
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        "COMPLETED" -> MaterialTheme.colorScheme.primary
                        "ERROR" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )

                if (item.status == "DOWNLOADING") {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item.error?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!selectionMode) {
                Spacer(Modifier.width(4.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (item.status) {
                        "COMPLETED" -> {
                            CardActionButton("📁") { onOpenFolder(item) }
                            CardActionButton("📤") { onShare(item) }
                        }
                        "ERROR" -> {
                            CardActionButton("🔄") { onRetry(item) }
                        }
                    }
                    CardActionButton("🗑", onDelete)
                }
            }
        }
    }
}

@Composable
private fun CardActionButton(emoji: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.titleSmall)
    }
}

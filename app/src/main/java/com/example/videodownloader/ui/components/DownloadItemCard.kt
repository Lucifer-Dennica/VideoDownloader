package com.example.videodownloader.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.videodownloader.data.local.DownloadEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    onDelete: () -> Unit,
    onOpen: (DownloadEntity) -> Unit,
    onOpenFolder: (DownloadEntity) -> Unit
) {
    val context = LocalContext.current

    val fileSize = remember(item.filePath) {
        val path = item.filePath ?: return@remember null
        try {
            if (path.startsWith("content://")) null
            else {
                val f = File(path)
                if (f.exists()) Formatter.formatShortFileSize(context, f.length()) else null
            }
        } catch (e: Exception) { null }
    }

    val dateText = remember(item.createdAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAt))
    }

    val isCarousel = item.type == "PHOTOS"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { if (item.status == "COMPLETED") onOpen(item) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Обложка с бейджем типа
            Box {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isCarousel) "🖼" else "🎬",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                // Бейдж с типом контента
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 0.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        if (isCarousel) "${item.itemCount} 🖼" else "🎬",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    fileSize?.let {
                        Text(
                            "• $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))

                val statusText = when (item.status) {
                    "QUEUED" -> "⏳ В очереди"
                    "DOWNLOADING" -> "⬇️ ${item.progress}%"
                    "COMPLETED" -> if (isCarousel) "✅ Готово (${item.itemCount} фото)" else "✅ Готово"
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

            Spacer(Modifier.width(4.dp))

            if (item.status == "COMPLETED") {
                IconButton(
                    onClick = { onOpenFolder(item) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("📁", style = MaterialTheme.typography.titleMedium)
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Text("🗑", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

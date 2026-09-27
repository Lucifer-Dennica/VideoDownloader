package com.example.videodownloader.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.videodownloader.data.local.DownloadEntity
import java.io.File

object ShareUtil {

    /** Шарит один элемент. Для фото-карусели — все фото из папки. */
    fun shareSingle(context: Context, item: DownloadEntity) {
        val uris = urisOf(context, item)
        if (uris.isEmpty()) return

        if (uris.size == 1) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(item)
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        } else {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeOf(item)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        }
    }

    /** Шарит группу элементов одним интентом. Карусели раскрываются в полный набор. */
    fun shareMultiple(context: Context, items: List<DownloadEntity>) {
        val uris = items.flatMap { urisOf(context, it) }
        if (uris.isEmpty()) return

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMimeOf(items)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться"))
    }

    /** Возвращает все URI для элемента: 1 для видео/аудио, N для фото-карусели. */
    private fun urisOf(context: Context, item: DownloadEntity): List<Uri> {
        return if (item.type == "PHOTOS" && !item.folderPath.isNullOrBlank()) {
            carouselUris(context, item.folderPath)
        } else {
            val uri = shareUri(context, item)
            if (uri != null) listOf(uri) else emptyList()
        }
    }

    /** Собирает URI всех картинок в папке карусели. */
    private fun carouselUris(context: Context, folderPath: String): List<Uri> {
        val normalized = folderPath.trimEnd('/') + "/"
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val uris = mutableListOf<Uri>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, arrayOf(normalized), null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    uris.add(
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return uris
    }

    private fun shareUri(context: Context, item: DownloadEntity): Uri? {
        val path = item.filePath ?: return null
        return try {
            if (path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                val file = File(path)
                if (!file.exists()) return null
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mimeOf(item: DownloadEntity): String = when (item.type) {
        "PHOTOS" -> "image/*"
        "AUDIO" -> "audio/*"
        else -> "video/*"
    }

    /** Общий MIME: если все типы совпадают — берём его, иначе любое. */
    private fun commonMimeOf(items: List<DownloadEntity>): String {
        val types = items.map { it.type }.toSet()
        return when {
            types.size == 1 -> when (types.first()) {
                "PHOTOS" -> "image/*"
                "AUDIO" -> "audio/*"
                else -> "video/*"
            }
            else -> "*/*"
        }
    }
}

package com.example.videodownloader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.videodownloader.data.local.DownloadEntity
import java.io.File

object ShareUtil {

    /** Шарит один элемент. */
    fun shareSingle(context: Context, item: DownloadEntity) {
        val uri = shareUri(context, item) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(item)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться"))
    }

    /** Шарит группу элементов одним интентом. */
    fun shareMultiple(context: Context, items: List<DownloadEntity>) {
        val uris = items.mapNotNull { shareUri(context, it) }
        if (uris.isEmpty()) return

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMimeOf(items)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться"))
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

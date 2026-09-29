package com.example.videodownloader.util

import android.net.Uri

data class ParsedUrl(
    val value: String,
    val host: String,
    val service: String,
    val isDirectMedia: Boolean
)

object UrlParser {

    /**
     * Только те сервисы, которые реально работают на 1.7.4.
     * Остальные (VK, Twitter, Reddit, Pinterest, Snapchat, SoundCloud,
     * YouTube, Instagram) временно убраны — работаем над ними.
     */
    private val knownHosts = listOf(
        "tiktok.com", "vt.tiktok.com", "vm.tiktok.com",
        "facebook.com", "fb.watch", "m.facebook.com"
    )

    fun parse(raw: String): ParsedUrl? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme !in listOf("http", "https")) return null

        val isDirectMedia = isDirectMediaPath(uri.path)
        val knownHost = knownHosts.any { host == it || host.endsWith(".$it") }

        if (!isDirectMedia && !knownHost) return null

        val service = detectService(host)
        return ParsedUrl(value, host, service, isDirectMedia)
    }

    fun parseAll(text: String): List<ParsedUrl> {
        if (text.isBlank()) return emptyList()
        val regex = Regex("""https?://[^\s]+""")
        return regex.findAll(text)
            .map { it.value.trim().trimEnd(',', '.', ')', ']', '}', '>', '"', '\'') }
            .mapNotNull { parse(it) }
            .distinctBy { it.value }
            .toList()
    }

    private fun isDirectMediaPath(path: String?): Boolean {
        val p = path?.lowercase() ?: return false
        return p.endsWith(".mp4") || p.endsWith(".webm") ||
               p.endsWith(".mov") || p.endsWith(".m4v")
    }

    private fun detectService(host: String): String = when {
        host.contains("tiktok") -> "TikTok"
        host.contains("facebook") || host == "fb.watch" -> "Facebook"
        else -> host
    }
}

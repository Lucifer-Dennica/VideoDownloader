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
     * Только те сервисы, которые реально работают на текущий момент.
     * Остальные (Vimeo, Twitch, Rutube, Tumblr, Odysee, Rumble, Dailymotion,
     * LinkedIn, Threads) убраны — они не скачиваются через публичные Cobalt-инстансы.
     */
    private val knownHosts = listOf(
        // 100% работает
        "tiktok.com", "vt.tiktok.com", "vm.tiktok.com",
        // Работает с cookies
        "youtube.com", "youtu.be", "m.youtube.com",
        "instagram.com",
        // Работает нестабильно, но шансы есть
        "facebook.com", "fb.watch", "m.facebook.com",
        "vk.com", "m.vk.com",
        "twitter.com", "x.com",
        "reddit.com", "redd.it",
        "pinterest.com", "pin.it",
        "snapchat.com",
        // Аудио-сервисы
        "soundcloud.com"
    )

    /** Одна ссылка. Возвращает null, если невалидна или сервис неизвестен. */
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

    /** Все ссылки из текста (одна на строку, из заметок, смешанные). */
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
        host.contains("youtube") || host == "youtu.be" -> "YouTube"
        host.contains("instagram") -> "Instagram"
        host.contains("facebook") || host == "fb.watch" -> "Facebook"
        host.contains("vk.com") -> "VK"
        host.contains("twitter") || host == "x.com" -> "Twitter"
        host.contains("reddit") || host == "redd.it" -> "Reddit"
        host.contains("pinterest") || host == "pin.it" -> "Pinterest"
        host.contains("snapchat") -> "Snapchat"
        host.contains("soundcloud") -> "SoundCloud"
        else -> host
    }
}

package com.example.videodownloader.util

import android.net.Uri

data class ParsedUrl(
    val value: String,
    val host: String,
    val service: String,
    val isDirectMedia: Boolean
)

object UrlParser {

    /** Известные сервисы. */
    private val knownHosts = listOf(
        "tiktok.com", "vt.tiktok.com", "vm.tiktok.com",
        "youtube.com", "youtu.be", "m.youtube.com",
        "instagram.com",
        "facebook.com", "fb.watch", "m.facebook.com",
        "vk.com", "m.vk.com",
        "twitter.com", "x.com",
        "reddit.com", "redd.it",
        "pinterest.com", "pin.it",
        "snapchat.com",
        "vimeo.com",
        "dailymotion.com", "dai.ly",
        "twitch.tv",
        "rumble.com",
        "odysee.com",
        "soundcloud.com",
        "rutube.ru",
        "linkedin.com",
        "threads.net",
        "tumblr.com"
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
        host.contains("vimeo") -> "Vimeo"
        host.contains("dailymotion") || host == "dai.ly" -> "Dailymotion"
        host.contains("twitch") -> "Twitch"
        host.contains("rumble") -> "Rumble"
        host.contains("odysee") -> "Odysee"
        host.contains("soundcloud") -> "SoundCloud"
        host.contains("rutube") -> "Rutube"
        host.contains("linkedin") -> "LinkedIn"
        host.contains("threads") -> "Threads"
        host.contains("tumblr") -> "Tumblr"
        else -> host
    }
}

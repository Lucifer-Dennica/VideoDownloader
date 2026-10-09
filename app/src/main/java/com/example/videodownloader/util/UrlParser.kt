package com.example.videodownloader.util

import android.net.Uri

data class ParsedUrl(
    val value: String,
    val host: String,
    val service: String,
    val isDirectMedia: Boolean,
    val supported: Boolean
)

object UrlParser {

    /** Рабочие сервисы через FastSaver. */
    private val fastSaverHosts = listOf(
        "rutube.ru",
        "facebook.com", "fb.watch", "m.facebook.com",
        "instagram.com",
        "pinterest.com", "pin.it"
    )

    /** TikTok идёт напрямую через tikwm. */
    private val tiktokHosts = listOf(
        "tiktok.com", "vt.tiktok.com", "vm.tiktok.com"
    )

    /** Скоро добавим. */
    private val comingSoonHosts = listOf(
        "youtube.com", "youtu.be", "m.youtube.com",
        "vk.com", "m.vk.com", "vkvideo.ru",
        "twitter.com", "x.com",
        "reddit.com", "redd.it",
        "soundcloud.com", "snapchat.com"
    )

    fun parse(raw: String): ParsedUrl? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme !in listOf("http", "https")) return null

        val isDirect = isDirectMediaPath(uri.path)

        // Проверка по всем спискам
        val matchFastSaver = fastSaverHosts.any { host == it || host.endsWith(".$it") }
        val matchTikTok = tiktokHosts.any { host == it || host.endsWith(".$it") }
        val matchSoon = comingSoonHosts.any { host == it || host.endsWith(".$it") }

        if (!isDirect && !matchFastSaver && !matchTikTok && !matchSoon) return null

        val service = detectService(host, matchFastSaver, matchTikTok, matchSoon)
        val supported = isDirect || matchFastSaver || matchTikTok

        return ParsedUrl(value, host, service, isDirect, supported)
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

    private fun detectService(
        host: String,
        fastSaver: Boolean,
        tiktok: Boolean,
        soon: Boolean
    ): String = when {
        tiktok -> "TikTok"
        host.contains("rutube") -> "Rutube"
        host.contains("facebook") || host == "fb.watch" -> "Facebook"
        host.contains("instagram") -> "Instagram"
        host.contains("pinterest") || host == "pin.it" -> "Pinterest"
        host.contains("youtube") || host == "youtu.be" -> "YouTube"
        host.contains("vk.com") || host.contains("vkvideo") -> "VK"
        host.contains("twitter") || host == "x.com" -> "Twitter"
        host.contains("reddit") || host == "redd.it" -> "Reddit"
        host.contains("soundcloud") -> "SoundCloud"
        else -> host
    }
}

package com.infinity.drive.core.telegram

import java.net.URI
import java.net.URISyntaxException

object TelegramMessageLink {
    private val username = Regex("[A-Za-z0-9_]{1,32}")
    private val hosts = setOf("t.me", "www.t.me", "telegram.me", "telegram.dog")

    /** Accepts copied Telegram post links and normalizes public preview links. */
    fun normalize(value: String): String? {
        val uri = try {
            URI(value.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        val host = uri.host?.lowercase()
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || host !in hosts ||
            uri.userInfo != null || (uri.port != -1 && uri.port != 443)
        ) return null
        val path = uri.rawPath.orEmpty().trim('/').split('/')
        val parts = if (path.firstOrNull() == "s" && path.size >= 3) path.drop(1) else path
        val chat = parts.firstOrNull() ?: return null
        if (chat == "c") {
            if (parts.size !in 3..4) return null
        } else if (!username.matches(chat) || parts.size !in 2..3) return null
        if (parts.drop(1).any { id -> id.isEmpty() || id.any { it !in '0'..'9' } || (id.toLongOrNull() ?: 0) <= 0 }) {
            return null
        }
        return buildString {
            append("https://t.me/")
            append(parts.joinToString("/"))
            uri.rawQuery?.let { append('?').append(it) }
        }
    }
}

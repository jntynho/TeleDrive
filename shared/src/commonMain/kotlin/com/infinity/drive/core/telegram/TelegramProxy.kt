package com.infinity.drive.core.telegram

/**
 * A route to Telegram for networks that block it directly. Held by the app
 * rather than by TDLib: the session database is wiped on an auth reset, and
 * losing the only usable route would leave the account unreachable.
 */
data class TelegramProxy(
    val type: TelegramProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val secret: String? = null
)

/** Local checks shared by the editor and the repository. TDLib validates the remote route. */
fun TelegramProxy.hasValidFields(): Boolean {
    if (host.isBlank() || host.length > 253 || host.any { it.isWhitespace() || it.isISOControl() || (it in "/@?#" || it == '\\') }) return false
    if (port !in ProxyLink.MIN_PORT..ProxyLink.MAX_PORT) return false
    return when (type) {
        TelegramProxyType.MTPROTO -> !secret.isNullOrBlank() && secret.length <= 1024 &&
            secret.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "_-+/=" }
        TelegramProxyType.SOCKS5, TelegramProxyType.HTTP -> listOfNotNull(username, password).all {
            it.encodeToByteArray().size <= 255 && it.none(Char::isISOControl)
        }
    }
}

package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hands a tg: login link to a Telegram app installed on this same device, so
 * confirming does not require scanning across devices.
 */
interface TelegramLinkOpener {

    val canOpenTelegram: Boolean

    fun open(link: String): Boolean

    /** Hands an original to an installed Telegram client, where the user chooses its destination. */
    fun shareFile(path:String,mimeType:String,title:String):Boolean = false
}

val LocalTelegramLinkOpener = staticCompositionLocalOf<TelegramLinkOpener> {
    error("TelegramLinkOpener is not provided")
}

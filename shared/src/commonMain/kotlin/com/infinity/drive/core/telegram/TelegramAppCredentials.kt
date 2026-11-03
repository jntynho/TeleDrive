package com.infinity.drive.core.telegram

/** Build-time application identity. It never replaces an installed user's stored identity. */
fun interface TelegramAppCredentials {
    fun get(): TelegramCredentials?
}

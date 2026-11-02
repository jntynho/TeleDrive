package com.infinity.drive.core.common

/** Prepares an installed TG Drive session before the new Telegram client is opened. */
interface AppUpgrade {
    /** A user-visible notice, or null when no intervention is needed. */
    suspend fun prepare(): String?
}

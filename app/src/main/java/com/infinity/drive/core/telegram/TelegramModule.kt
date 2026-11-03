// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.telegram

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val telegramModule = module {
    single<TelegramAppCredentials> {
        TelegramAppCredentials {
            val id = app.tgdrive.BuildConfig.TELEGRAM_API_ID.trim().toIntOrNull()
            val hash = app.tgdrive.BuildConfig.TELEGRAM_API_HASH.trim()
            if (id != null && id > 0 && hash.matches(Regex("[0-9a-fA-F]{32}"))) TelegramCredentials(id, hash) else null
        }
    }
    single<TelegramClient> { TdLibTelegramClient(androidContext(),get(),get(),get()) }
    single { TelegramPacer() }
}

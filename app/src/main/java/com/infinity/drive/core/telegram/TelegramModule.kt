// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.telegram

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val telegramModule = module {
    single<TelegramClient> { TdLibTelegramClient(androidContext(),get(),get(),get()) }
    single { TelegramPacer() }
}

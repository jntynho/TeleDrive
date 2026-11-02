// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.common

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext

val commonModule = module {
    singleOf(::AppNotifications)
    single<AppUpgrade> { app.tgdrive.migration.LegacySessionUpgrade(androidContext(),get(),get(),get()) }
}

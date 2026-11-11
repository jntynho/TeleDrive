package com.infinity.drive.presentation

import com.infinity.drive.presentation.hidden.HiddenLockViewModel

import com.infinity.drive.presentation.di.sharedUiModule
import com.infinity.drive.presentation.platform.AndroidPlatformCapabilities
import com.infinity.drive.presentation.platform.AndroidStandardFolderPaths
import com.infinity.drive.presentation.platform.PlatformCapabilities
import com.infinity.drive.presentation.platform.StandardFolderPaths
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext

val presentationModule = module {
    viewModelOf(::HiddenLockViewModel)
    singleOf(::AndroidStandardFolderPaths) bind StandardFolderPaths::class
    singleOf(::AndroidPlatformCapabilities) bind PlatformCapabilities::class
    single { com.infinity.drive.presentation.audio.AndroidAudioPlayback(androidContext(), get(), get(), get(),hiddenAccess=get()) }
    includes(sharedUiModule)
}

package com.infinity.drive.core.security

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.android.ext.koin.androidContext

val securityModule = module {
    singleOf(::AppLockManager)
    singleOf(::SensitiveScreens)
    single<HiddenLockStore>{AndroidHiddenLockStore(androidContext(),get())}
    single {HiddenLockManager(get())}
    single<CollectionLockStores>{CollectionLockStores { name->AndroidHiddenLockStore(androidContext(),get(),"collection-$name-lock.bin") }}
    singleOf(::CollectionLocks)
    singleOf(::HiddenContentAccess).onClose{it?.close()}
}

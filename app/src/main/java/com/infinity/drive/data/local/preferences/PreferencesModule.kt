package com.infinity.drive.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.infinity.drive.core.accounts.DeviceAccounts
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val preferencesModule = module {
    single<DataStore<Preferences>> {
        val context = androidContext()
        if (DeviceAccounts.primary) context.dataStore
        else PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(DeviceAccounts.scoped("settings")) }
    }
}

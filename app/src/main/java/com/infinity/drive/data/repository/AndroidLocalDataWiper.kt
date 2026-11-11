// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.data.repository

import android.content.Context
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.domain.repository.CacheRepository
import java.io.File

/**
 * Drops everything this device knows about the signed-in drive. Logging out
 * has to leave no index behind: rows point at message ids in a channel the
 * next account cannot read, and a stale index would be merged with whatever
 * the new account has. Files in Telegram are untouched, and signing back in
 * rebuilds the index from the channel.
 */
class AndroidLocalDataWiper(
    private val storagePaths: com.infinity.drive.core.files.AppStoragePaths,
    private val database: DriveInfinityDatabase,
    private val cacheRepository: CacheRepository,
    private val files:com.infinity.drive.domain.repository.FileRepository,
    private val hiddenLock:com.infinity.drive.core.security.HiddenLockManager
) : LocalDataWiper {

    override suspend fun wipe() {
        hiddenLock.clear()
        files.clearStartupCache()
        runCatching { cacheRepository.clearAll() }
            .onFailure { SafeLog.w(TAG, "Cache clear failed during wipe", it) }

        runCatching { database.clearAllTables() }
            .onFailure { SafeLog.w(TAG, "Database clear failed during wipe", it) }

        for (name in APP_DIRECTORIES) {
            runCatching { File(storagePaths.filesDir, name).deleteRecursively() }
        }
        SafeLog.d(TAG, "Local drive data cleared")
    }

    private companion object {
        const val TAG = "LocalDataWiper"

        /** Import staging and generated previews; the key store is left alone. */
        val APP_DIRECTORIES = listOf("imports", "previews")
    }
}

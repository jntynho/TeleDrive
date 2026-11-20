package com.infinity.drive.domain.repository

import com.infinity.drive.core.common.AppResult

interface KeyBackupRepository {

    /** Wraps the content key with the passphrase and stores it in the storage channel. */
    suspend fun createBackup(passphrase: CharArray, hint: String?): AppResult<Unit>
}

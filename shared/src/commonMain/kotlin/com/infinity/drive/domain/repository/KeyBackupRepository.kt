package com.infinity.drive.domain.repository

import com.infinity.drive.core.common.AppResult

interface KeyBackupRepository {

    /** Wraps the content key with the passphrase and stores it in the storage channel. */
    suspend fun createBackup(passphrase: CharArray, hint: String?): AppResult<Unit>

    /** Returns true when the passphrase unwrapped the key and it was installed. */
    suspend fun restore(passphrase: CharArray): AppResult<Boolean>

    /** Tests the existing Telegram backup without installing anything. */
    suspend fun verifyBackup(password: CharArray): AppResult<KeyVerification>

    /** Encrypts a device key into a portable file bound to the signed-in account. */
    suspend fun exportPortable(password: CharArray): AppResult<ByteArray>

    /** Checks the password and account and installs the key if it is safe to do so. */
    suspend fun importPortable(blob: ByteArray, password: CharArray): AppResult<KeyVerification>

    enum class KeyVerification { VERIFIED, INVALID_FILE_OR_PASSWORD, OTHER_ACCOUNT, DIFFERENT_LOCAL_KEY }

    /** Plaintext passphrase hint from the stored backup, readable without it. */
    suspend fun backupHint(): AppResult<String?>
}

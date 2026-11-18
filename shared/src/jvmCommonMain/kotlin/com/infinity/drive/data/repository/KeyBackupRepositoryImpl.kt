package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.KeyBackupCodec
import com.infinity.drive.core.crypto.KeyUnavailableException
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramDownloadEvent
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.core.telegram.TelegramUploadEvent
import com.infinity.drive.domain.repository.KeyBackupRepository
import com.infinity.drive.domain.repository.KeyBackupRepository.KeyVerification
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class KeyBackupRepositoryImpl(
    private val storagePaths: AppStoragePaths,
    private val telegramClient: TelegramClient,
    private val keyBackupCodec: KeyBackupCodec,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val settingsRepository: SettingsRepository
) : KeyBackupRepository {

    private val mutex = Mutex()

    override suspend fun createBackup(
        passphrase: CharArray,
        hint: String?
    ): AppResult<Unit> = runTelegram {
        val chatId = storageChatId()
        val previous = existingBackup(chatId)
        if (previous != null && !wrappedKeyRepository.exists(CryptoKeys.CONTENT)) {
            return@runTelegram AppResult.Failure(
                AppError.UnsupportedOperation(
                    "This channel already holds a key backup, and this device has no " +
                            "key yet. Restore the existing backup with its passphrase first, " +
                            "or files encrypted with the old key can never be decrypted."
                )
            )
        }
        val contentKey = wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT)
        val blob = keyBackupCodec.encode(contentKey, passphrase, hint)

        val staging = File.createTempFile("key-backup-", ".tmp", storagePaths.cacheDir)
        var completed = false
        try {
            staging.writeBytes(blob)
            telegramClient.uploadDocument(
                chatId = chatId,
                localPath = staging.absolutePath,
                fileName = KeyBackupCodec.BACKUP_FILE_NAME,
                mimeType = "application/octet-stream",
                caption = BACKUP_MARKER
            ).collect { event ->
                if (event is TelegramUploadEvent.Completed) completed = true
            }
            if (!completed) return@runTelegram AppResult.Failure(AppError.Unknown())
            // Keep the previous backup until Telegram confirms the replacement.
            if (previous != null) {
                try {
                    telegramClient.deleteMessages(chatId, listOf(previous.messageId))
                } catch (_: TelegramException) {
                    // A failed cleanup must not invalidate the new backup.
                }
            }
            AppResult.Success(Unit)
        } finally {
            staging.delete()
        }
    }

    override suspend fun restore(passphrase: CharArray): AppResult<Boolean> = runTelegram {
        val blob = downloadBackupBlob()
            ?: return@runTelegram AppResult.Failure(AppError.NotFound)
        val key = keyBackupCodec.decode(blob, passphrase)
            ?: return@runTelegram AppResult.Success(false)
        try {
            if (key.size != 32) return@runTelegram AppResult.Success(false)
            if (!wrappedKeyRepository.storeIfMissingOrMatching(CryptoKeys.CONTENT, key)) {
                return@runTelegram AppResult.Failure(AppError.KeyUnreadable)
            }
            AppResult.Success(true)
        } finally {
            key.fill(0)
        }
    }

    override suspend fun verifyBackup(password: CharArray): AppResult<KeyVerification> = runTelegram {
        val selected = settingsRepository.preferences.first().storageChatId
            ?: return@runTelegram AppResult.Failure(AppError.NotFound)
        val blob = downloadBackupBlob(selected)
            ?: return@runTelegram AppResult.Failure(AppError.NotFound)
        val recovered = keyBackupCodec.decode(blob, password)
            ?: return@runTelegram AppResult.Success(KeyVerification.INVALID_FILE_OR_PASSWORD)
        try {
            val local = wrappedKeyRepository.get(CryptoKeys.CONTENT)
            AppResult.Success(when {
                recovered.size != 32 -> KeyVerification.INVALID_FILE_OR_PASSWORD
                local != null && !MessageDigest.isEqual(local, recovered) -> KeyVerification.DIFFERENT_LOCAL_KEY
                else -> KeyVerification.VERIFIED
            })
        } finally {
            recovered.fill(0)
        }
    }

    override suspend fun exportPortable(password: CharArray): AppResult<ByteArray> = runTelegram {
        if (telegramClient.authState.value != TelegramAuthState.Ready) {
            return@runTelegram AppResult.Failure(AppError.AuthenticationRequired)
        }
        val accountId = telegramClient.getCurrentUser().id
        val key = wrappedKeyRepository.get(CryptoKeys.CONTENT)
            ?: return@runTelegram AppResult.Failure(AppError.KeyUnreadable)
        AppResult.Success(keyBackupCodec.encodePortable(key, accountId, password))
    }

    override suspend fun importPortable(blob: ByteArray, password: CharArray): AppResult<KeyVerification> = runTelegram {
        if (telegramClient.authState.value != TelegramAuthState.Ready) {
            return@runTelegram AppResult.Failure(AppError.AuthenticationRequired)
        }
        val accountId = telegramClient.getCurrentUser().id
        when (val key = keyBackupCodec.decodePortable(blob, accountId, password)) {
            KeyBackupCodec.PortableKey.Invalid -> AppResult.Success(KeyVerification.INVALID_FILE_OR_PASSWORD)
            KeyBackupCodec.PortableKey.OtherAccount -> AppResult.Success(KeyVerification.OTHER_ACCOUNT)
            is KeyBackupCodec.PortableKey.Unwrapped -> try {
                if (telegramClient.authState.value != TelegramAuthState.Ready ||
                    telegramClient.getCurrentUser().id != accountId
                ) return@runTelegram AppResult.Failure(AppError.AuthenticationRequired)
                AppResult.Success(
                    if (wrappedKeyRepository.storeIfMissingOrMatching(CryptoKeys.CONTENT, key.bytes)) {
                        KeyVerification.VERIFIED
                    } else {
                        KeyVerification.DIFFERENT_LOCAL_KEY
                    }
                )
            } finally {
                key.bytes.fill(0)
            }
        }
    }

    override suspend fun backupHint(): AppResult<String?> = runTelegram {
        val blob = downloadBackupBlob()
            ?: return@runTelegram AppResult.Failure(AppError.NotFound)
        AppResult.Success(keyBackupCodec.readInfo(blob)?.hint)
    }

    private suspend fun downloadBackupBlob(selectedChatId: Long? = null): ByteArray? {
        val chatId = selectedChatId ?: storageChatId()
        val document = existingBackup(chatId) ?: return null
        if (document.sizeBytes > KeyBackupCodec.MAX_BACKUP_BYTES) return null
        var localPath: String? = null
        telegramClient.downloadDocument(document.remoteFileId).collect { event ->
            if (event is TelegramDownloadEvent.Completed) localPath = event.localPath
        }
        return localPath?.let(::File)
            ?.takeIf { it.isFile && it.length() in 1..KeyBackupCodec.MAX_BACKUP_BYTES.toLong() }
            ?.readBytes()
    }

    private suspend fun existingBackup(chatId: Long): RemoteDocument? {
        var fromMessageId = 0L
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = telegramClient.fetchDocuments(chatId, fromMessageId, PAGE_SIZE)
            page.documents.firstOrNull {
                it.fileName == KeyBackupCodec.BACKUP_FILE_NAME ||
                        it.caption.startsWith(BACKUP_MARKER)
            }?.let { return it }
            if (page.nextFromMessageId == 0L) return null
            if (!isAdvancingHistory(fromMessageId, page.nextFromMessageId)) {
                throw IOException("Key backup search did not advance")
            }
            fromMessageId = page.nextFromMessageId
        }
        throw IOException("Key backup search reached the page limit")
    }

    private suspend fun storageChatId(): Long {
        val prefs = settingsRepository.preferences.first()
        val chatId = telegramClient.ensureStorageChat(prefs.storageChatId)
        if (chatId != prefs.storageChatId) {
            settingsRepository.update { it.copy(storageChatId = chatId) }
        }
        return chatId
    }

    private suspend fun <T> runTelegram(block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    block()
                } catch (e: TelegramException) {
                    AppResult.Failure(
                        if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
                        else AppError.TelegramError(e.code, e.message)
                    )
                } catch (_: KeyUnavailableException) {
                    AppResult.Failure(AppError.KeyUnreadable)
                } catch (_: IOException) {
                    AppResult.Failure(AppError.Unknown())
                }
            }
        }

    companion object {
        private const val BACKUP_MARKER = "#driveinfinity-keybackup"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 200
    }
}

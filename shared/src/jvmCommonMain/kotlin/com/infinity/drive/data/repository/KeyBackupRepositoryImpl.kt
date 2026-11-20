package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.KeyBackupCodec
import com.infinity.drive.core.crypto.KeyUnavailableException
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.core.telegram.TelegramUploadEvent
import com.infinity.drive.domain.repository.KeyBackupRepository
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
                            "matching key yet. Update this backup from a device that already has the key."
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

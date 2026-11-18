package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.KeyUnavailableException
import com.infinity.drive.core.files.LocalCleanup
import com.infinity.drive.core.files.LocalCopyDeleter
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.entity.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.security.GeneralSecurityException

class VerifiedLocalCleanup(
    private val active: ActiveChannel,
    private val files: FileDao,
    private val hidden: HiddenContentAccess,
    private val telegram: TelegramClient,
    private val reader: DriveContentReader,
    private val deleter: LocalCopyDeleter
) {
    private val execution = Mutex()

    suspend fun run(): AppResult<LocalCleanup> = execution.withLock {
        withContext(Dispatchers.IO) {
            val chat = active.id() ?: return@withContext AppResult.Failure(AppError.NotFound)
            val candidates = files.cleanupCandidates(chat).filter { hidden.canRead(it.id) }
            val verified = mutableListOf<VerifiedCopy>()
            suspend fun checkDrive() {
                ensureActive()
                if (active.id() != chat || telegram.authState.value != TelegramAuthState.Ready) {
                    throw CleanupInterrupted()
                }
            }
            try {
                checkDrive()
                for (candidate in candidates) {
                    checkDrive()
                    val path = candidate.localPath ?: continue
                    val stamp = fileStamp(path) ?: continue
                    val local = File(path)
                    val matched = try {
                        val localHash = reader.copyLocal(local, stamp.size, discard)
                        val remote = reader.copyRemote(candidate, discard) {
                            checkDrive()
                            if (!hidden.canRead(candidate.id)) throw CleanupInterrupted()
                        }
                        localHash == remote && fileStamp(path) == stamp
                    } catch (e: TelegramException) {
                        if (e.isRateLimit) return@withContext AppResult.Failure(AppError.RateLimited(e.retryAfterSeconds ?: 0))
                        false
                    } catch (_: KeyUnavailableException) {
                        false
                    } catch (_: IOException) {
                        false
                    } catch (_: GeneralSecurityException) {
                        false
                    } catch (_: IllegalArgumentException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    }
                    if (matched) verified.add(VerifiedCopy(candidate, stamp))
                }

                checkDrive()
                val eligible = files.cleanupCandidates(chat).associateBy { it.id }
                val safe = verified.filter { (record, before) ->
                    val path = record.localPath ?: return@filter false
                    hidden.canRead(record.id) && eligible[record.id] == record && fileStamp(path) == before
                }
                checkDrive()
                val cleanup = deleter.delete(safe.mapNotNull { it.record.localPath })
                if (cleanup.consentRequest != null) {
                    return@withContext AppResult.Success(cleanup.copy(
                        keptCount = candidates.size - safe.size,
                        consentCopies = safe.map { it.record }.associate { it.id to requireNotNull(it.localPath) }
                    ))
                }
                val deleted = reconcile(safe.map { it.record }.associate { it.id to requireNotNull(it.localPath) })
                AppResult.Success(LocalCleanup(deleted, keptCount = candidates.size - deleted))
            } catch (_: CleanupInterrupted) {
                AppResult.Failure(AppError.AuthenticationRequired)
            }
        }
    }

    suspend fun reconcile(verified: Map<String, String>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        for ((id, path) in verified) {
            if (deleter.isGone(path)) {
                removed += files.clearLocalPathIfMatches(id, path, true)
            }
        }
        removed
    }

    private data class VerifiedCopy(val record: FileEntity, val stamp: CleanupFileStamp)
    private class CleanupInterrupted : Exception()

    private val discard = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}

internal data class CleanupFileStamp(val size: Long, val modified: Long, val key: Any?)

internal fun fileStamp(path: String): CleanupFileStamp? {
    if (path.startsWith("content://")) return null
    return try {
        val attributes = Files.readAttributes(File(path).toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        if (attributes.isRegularFile) {
            CleanupFileStamp(attributes.size(), attributes.lastModifiedTime().toMillis(), attributes.fileKey())
        } else null
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

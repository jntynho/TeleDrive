package com.infinity.drive.core.media

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.Semaphore

internal class ExternalMediaStreams(
    private val files: FileRepository,
    private val parts: FilePartDao,
    private val settings: SettingsRepository,
    private val hidden: HiddenContentAccess,
    private val appLock: AppLockManager,
    private val telegram: TelegramClient,
    private val crypto: StreamCrypto,
    private val keys: WrappedKeyRepository
) {
    internal class Grant(
        val token: String,
        val file: DriveFile,
        val parts: List<MediaPart>,
        val expiresAt: Long
    )

    private val grants = LinkedHashMap<String, Grant>()
    val openSlots = Semaphore(8)

    suspend fun authorize(context: Context, id: String): Uri {
        val file = files.getFile(id) ?: error("File unavailable")
        require(file.category in setOf(FileCategory.VIDEO, FileCategory.AUDIO))
        val grant = prepare(file)
        synchronized(grants) {
            grants.entries.removeAll { it.value.expiresAt <= SystemClock.elapsedRealtime() }
            while (grants.size >= 12) grants.remove(grants.keys.first())
            grants[grant.token] = grant
        }
        return Uri.Builder().scheme("content").authority(context.packageName + ".stream").appendPath(grant.token).build()
    }

    suspend fun prepare(file: DriveFile): Grant {
        require(file.sizeBytes >= 0)
        val chatId = requireNotNull(file.chatId)
        val remote = requireNotNull(file.remoteFileId)
        require(file.hasRemoteCopy && canRead(file))
        check(telegram.ownedPrivateChannel(chatId) != null)

        val fileParts = parts.partsOf(file.id).map { part ->
            require(part.chatId == chatId && part.messageId != null)
            MediaPart(requireNotNull(part.remoteFileId), part.plainOffset, part.plainSize)
        }.ifEmpty {
            if (file.isEncrypted) listOf(MediaPart(remote, 0, file.sizeBytes)) else emptyList()
        }
        if (fileParts.isNotEmpty()) {
            var position = 0L
            for (part in fileParts) {
                require(part.plainOffset == position && part.plainSize in 1..(file.sizeBytes - position))
                position += part.plainSize
            }
            require(position == file.sizeBytes)
        }
        if (file.isEncrypted) check(keys.exists(CryptoKeys.CONTENT))

        return Grant(UUID.randomUUID().toString(), file, fileParts, SystemClock.elapsedRealtime() + GRANT_DURATION_MS)
    }

    fun grant(uri: Uri): Grant? = synchronized(grants) {
        val token = uri.pathSegments.singleOrNull() ?: return@synchronized null
        grants[token]?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }
    }

    fun revoke(uri: Uri) {
        synchronized(grants) { grants.remove(uri.lastPathSegment) }
    }

    suspend fun assertReadable(grant: Grant) {
        val alive = synchronized(grants) {
            grants[grant.token] === grant && grant.expiresAt > SystemClock.elapsedRealtime()
        }
        check(alive && canRead(grant.file))
    }

    suspend fun canRead(snapshot: DriveFile): Boolean {
        if (appLock.locked.value || telegram.authState.value != TelegramAuthState.Ready) return false
        val prefs = settings.preferences.first()
        if (!prefs.onboardingComplete || prefs.storageChatId != snapshot.chatId || prefs.blockScreenCapture) return false
        val file = files.getFile(snapshot.id) ?: return false
        return !file.isTrashed && file.backupState == BackupState.BACKED_UP && file.chatId == snapshot.chatId && file.messageId == snapshot.messageId &&
            file.sizeBytes == snapshot.sizeBytes && file.modifiedAt == snapshot.modifiedAt &&
            file.remoteFileId == snapshot.remoteFileId && file.isEncrypted == snapshot.isEncrypted &&
            hidden.canRead(file.id) && !hidden.protectedFile(file)
    }

    fun source(grant: Grant): MediaByteSource {
        if (grant.parts.isEmpty()) {
            return TelegramMediaByteSource(telegram, requireNotNull(grant.file.remoteFileId))
        }
        val key = if (grant.file.isEncrypted) requireNotNull(keys.get(CryptoKeys.CONTENT)).copyOf() else null
        val source = PartedMediaByteSource(telegram, crypto, grant.parts, grant.file.isEncrypted, key)
        return object : MediaByteSource by source {
            override fun close() {
                try {
                    source.close()
                } finally {
                    key?.fill(0)
                }
            }
        }
    }

    private companion object {
        const val GRANT_DURATION_MS = 2L * 60 * 60 * 1_000
    }
}

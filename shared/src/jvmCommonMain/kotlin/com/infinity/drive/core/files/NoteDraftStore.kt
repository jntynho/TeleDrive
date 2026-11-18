package com.infinity.drive.core.files

import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

@Serializable
data class NoteDraft(val title: String, val body: String)

/** Device-local encrypted working copies, independent of the saved Telegram content. */
class NoteDraftStore(
    storage: AppStoragePaths,
    private val keys: WrappedKeyRepository,
    private val crypto: StreamCrypto
) {
    private val root = File(storage.filesDir, "note-drafts")
    private val mutex = Mutex()

    suspend fun read(slot: String): NoteDraft? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = draftFile(slot)
            val key = keys.get(CryptoKeys.CACHE) ?: return@withContext null
            if (!file.isFile) return@withContext null
            runCatching {
                val bytes = crypto.decryptBytes(key, file.readBytes())
                try { Json.decodeFromString<NoteDraft>(bytes.decodeToString()) }
                finally { bytes.fill(0) }
            }.getOrNull()
        }
    }

    suspend fun write(slot: String, draft: NoteDraft): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            Files.createDirectories(root.toPath())
            val target = draftFile(slot)
            val temporary = Files.createTempFile(root.toPath(), "draft-", ".tmp")
            val bytes = Json.encodeToString(draft).encodeToByteArray()
            try {
                temporary.toFile().writeBytes(crypto.encryptBytes(keys.getOrCreate(CryptoKeys.CACHE), bytes))
                ensureActive()
                Files.move(temporary, target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                bytes.fill(0)
                Files.deleteIfExists(temporary)
            }
        }
    }

    suspend fun clear(slot: String) = mutex.withLock {
        withContext(Dispatchers.IO) { Files.deleteIfExists(draftFile(slot).toPath()) }
    }

    private fun draftFile(slot: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(slot.encodeToByteArray()).toHexString()
        return File(root, hash)
    }
}

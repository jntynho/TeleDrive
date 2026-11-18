package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramDownloadEvent
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.FilePartEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class DriveIntegrityChecker(
    private val activeChannel: ActiveChannel,
    private val files: FileDao,
    private val parts: FilePartDao,
    private val telegram: TelegramClient,
    private val hidden: HiddenContentAccess,
    private val keys: WrappedKeyRepository,
    private val crypto: StreamCrypto
) {
    data class Report(
        val checked: Int = 0,
        val available: Int = 0,
        val missing: Int = 0,
        val inconsistent: Int = 0,
        val unverified: Int = 0,
        val locked: Int = 0,
        val samplesVerified: Int = 0,
        val problemNames: List<String> = emptyList()
    )

    suspend fun check(
        sample: Boolean,
        onProgress: (Report) -> Unit
    ): AppResult<Report> = withContext(Dispatchers.IO) {
        val chatId = activeChannel.id() ?: return@withContext AppResult.Failure(AppError.NotFound)
        val key = keys.get(CryptoKeys.CONTENT)?.copyOf()
        try {
            inspect(chatId, sample, key, onProgress)
        } finally {
            key?.fill(0)
        }
    }

    private suspend fun inspect(
        chatId: Long,
        sample: Boolean,
        key: ByteArray?,
        onProgress: (Report) -> Unit
    ): AppResult<Report> {
        var afterId = ""
        var report = Report()
        var sampleCount = 0
        var remainingBytes = MAX_SAMPLE_BYTES
        while (true) {
            if (telegram.authState.value != TelegramAuthState.Ready || activeChannel.id() != chatId) {
                return AppResult.Failure(AppError.AuthenticationRequired)
            }
            val page = files.integrityPage(chatId, afterId, PAGE_SIZE)
            if (page.isEmpty()) return AppResult.Success(report)
            afterId = page.last().id
            val visible = page.filter { hidden.canRead(it.id) }
            val allParts = parts.partsOfAll(visible.map { it.id }).groupBy { it.fileId }
            val layouts = visible.associateWith { file ->
                layoutFor(chatId, file, allParts[file.id].orEmpty().sortedBy { it.partIndex })
            }
            val documentIds = layouts.values.filterNotNull().flatten().map { it.messageId }.distinct()
            val documents = mutableMapOf<Long, RemoteDocument>()
            val unavailable = mutableSetOf<Long>()
            for (ids in documentIds.chunked(MAX_MESSAGE_LOOKUP)) {
                try {
                    telegram.getDocuments(chatId, ids).associateByTo(documents) { it.messageId }
                } catch (e: TelegramException) {
                    // If Telegram rejects a request, absence is unknown for all its messages.
                    if (e.isRateLimit) {
                        return AppResult.Failure(AppError.RateLimited(e.retryAfterSeconds ?: 0))
                    }
                    unavailable.addAll(ids)
                }
            }
            for ((file, layout) in layouts) {
                if (!hidden.canRead(file.id)) continue
                var status = referenceStatus(layout, documents, unavailable)
                var verified = false
                if (sample && status == Status.AVAILABLE && layout != null &&
                    sampleCount < MAX_SAMPLES && (file.contentHash != null || file.isEncrypted) &&
                    (!file.isEncrypted || key != null)
                ) {
                    val sources = layout.map { documents.getValue(it.messageId) }
                    val size = sources.fold(0L) { sum, doc ->
                        (sum + doc.sizeBytes.coerceIn(0, MAX_SAMPLE_BYTES + 1))
                            .coerceAtMost(MAX_SAMPLE_BYTES + 1)
                    }
                    if (sources.all { it.sizeBytes > 0 } && size <= remainingBytes) {
                        sampleCount++
                        remainingBytes -= size
                        status = sampleContents(file, layout, sources, key)
                        verified = status == Status.AVAILABLE
                    }
                }
                val names = if ((status == Status.MISSING || status == Status.INCONSISTENT) &&
                    report.problemNames.size < MAX_PROBLEMS_SHOWN && !file.isHidden &&
                    !file.name.endsWith(".md", ignoreCase = true) && !hidden.protectedFolder(file.folderId)
                ) report.problemNames + file.name else report.problemNames
                report = Report(
                    checked = report.checked + 1,
                    available = report.available + if (status == Status.AVAILABLE) 1 else 0,
                    missing = report.missing + if (status == Status.MISSING) 1 else 0,
                    inconsistent = report.inconsistent + if (status == Status.INCONSISTENT) 1 else 0,
                    unverified = report.unverified + if (status == Status.UNVERIFIED) 1 else 0,
                    locked = report.locked + if (file.isEncrypted && key == null) 1 else 0,
                    samplesVerified = report.samplesVerified + if (verified) 1 else 0,
                    problemNames = names
                )
            }
            onProgress(report)
        }
    }

    private suspend fun sampleContents(
        file: FileEntity,
        layout: List<Reference>,
        documents: List<RemoteDocument>,
        key: ByteArray?
    ): Status {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((index, document) in documents.withIndex()) {
            val source = try {
                var downloaded: File? = null
                telegram.downloadDocument(document.remoteFileId).collect { event ->
                    if (event is TelegramDownloadEvent.Completed) downloaded = File(event.localPath)
                }
                downloaded?.takeIf { it.isFile } ?: return Status.UNVERIFIED
            } catch (e: TelegramException) {
                return Status.UNVERIFIED
            } catch (e: IOException) {
                return Status.UNVERIFIED
            }
            if (source.length() != document.sizeBytes) return Status.INCONSISTENT
            val context = coroutineContext
            val sink = object : OutputStream() {
                var size = 0L
                override fun write(value: Int) {
                    context.ensureActive()
                    digest.update(value.toByte())
                    size++
                }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    context.ensureActive()
                    digest.update(bytes, offset, length)
                    size += length
                }
            }
            try {
                source.inputStream().buffered().use { input ->
                    if (file.isEncrypted) crypto.decryptStream(requireNotNull(key), input, sink)
                    else input.copyTo(sink)
                }
            } catch (e: GeneralSecurityException) {
                return Status.INCONSISTENT
            } catch (e: IllegalArgumentException) {
                return Status.INCONSISTENT
            } catch (e: java.io.EOFException) {
                return Status.INCONSISTENT
            } catch (e: IOException) {
                return Status.UNVERIFIED
            }
            if (sink.size != layout[index].plaintextBytes) return Status.INCONSISTENT
        }
        val expected = file.contentHash ?: return Status.AVAILABLE
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (actual.equals(expected, ignoreCase = true)) Status.AVAILABLE else Status.INCONSISTENT
    }

    private companion object {
        const val PAGE_SIZE = 80
        const val MAX_MESSAGE_LOOKUP = 100
        const val MAX_SAMPLES = 8
        const val MAX_SAMPLE_BYTES = 64L * 1024 * 1024
        const val MAX_PROBLEMS_SHOWN = 12
    }
}

internal data class Reference(
    val messageId: Long,
    val uniqueId: String?,
    val remoteBytes: Long?,
    val plaintextBytes: Long
)

internal enum class Status { AVAILABLE, MISSING, INCONSISTENT, UNVERIFIED }

internal fun layoutFor(chatId: Long, file: FileEntity, parts: List<FilePartEntity>): List<Reference>? {
    if (file.partCount == 0) {
        return listOf(Reference(
            file.messageId ?: return null,
            file.remoteUniqueId,
            file.sizeBytes.takeUnless { file.isEncrypted },
            file.sizeBytes
        ))
    }
    if (parts.size != file.partCount) return null
    var offset = 0L
    val layout = parts.mapIndexed { index, part ->
        if (part.partIndex != index || part.chatId != chatId || part.plainOffset != offset ||
            part.plainSize <= 0 || part.plainSize > file.sizeBytes - offset
        ) return null
        offset += part.plainSize
        Reference(
            part.messageId ?: return null,
            part.remoteUniqueId,
            part.storedSize.takeIf { it > 0 },
            part.plainSize
        )
    }
    return layout.takeIf { offset == file.sizeBytes }
}

internal fun referenceStatus(
    layout: List<Reference>?,
    documents: Map<Long, RemoteDocument>,
    unavailable: Set<Long>
): Status {
    if (layout == null) return Status.INCONSISTENT
    var status = Status.AVAILABLE
    for (reference in layout) {
        if (reference.messageId in unavailable) {
            if (status == Status.AVAILABLE) status = Status.UNVERIFIED
            continue
        }
        val document = documents[reference.messageId]
        if (document == null) {
            status = Status.MISSING
            continue
        }
        if ((reference.uniqueId != null && reference.uniqueId != document.uniqueFileId) ||
            (reference.remoteBytes != null && document.sizeBytes > 0 && reference.remoteBytes != document.sizeBytes)
        ) return Status.INCONSISTENT
    }
    return status
}

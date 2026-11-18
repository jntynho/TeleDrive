package com.infinity.drive.core.files

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import app.tgdrive.R
import com.infinity.drive.core.media.ExternalMediaStreams
import com.infinity.drive.core.media.openTelegramDescriptor
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.FileQueryBuilder
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FolderDao
import com.infinity.drive.data.local.entity.FolderEntity
import com.infinity.drive.data.mapper.toDomain
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileQuerySpec
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import java.io.FileNotFoundException
import java.util.Base64

/** Read-only Android file picker access to the signed-in drive's visible, backed-up files. */
class TeleDriveDocumentsProvider : DocumentsProvider() {
    private val koin get() = GlobalContext.get()
    private val settings get() = koin.get<SettingsRepository>()
    private val telegram get() = koin.get<TelegramClient>()
    private val lock get() = koin.get<AppLockManager>()
    private val access get() = koin.get<HiddenContentAccess>()
    private val files get() = koin.get<FileDao>()
    private val folders get() = koin.get<FolderDao>()
    private val streams get() = koin.get<ExternalMediaStreams>()

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = work {
        val cursor = MatrixCursor(projection ?: rootColumns)
        val prefs = settings.preferences.first()
        val drive = prefs.storageChatId?.takeIf { prefs.onboardingComplete } ?: return@work cursor
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, drive.toString())
            .add(Root.COLUMN_DOCUMENT_ID, DocumentId(drive, Kind.ROOT).encode())
            .add(Root.COLUMN_TITLE, "TeleDrive")
            .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD)
        cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = work {
        val id = parseDocumentId(documentId)
        requireDrive(id.drive)
        val out = MatrixCursor(projection ?: documentColumns)
        when (id.kind) {
            Kind.ROOT -> addDirectory(out, id, "TeleDrive", null)
            Kind.FOLDER -> folder(id)?.let { addDirectory(out, id, it.name, it.modifiedAt) } ?: unavailable()
            Kind.FILE -> file(id)?.let { addFile(out, it) } ?: unavailable()
        }
        requireDrive(id.drive)
        out
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor = work {
        val parent = parseDocumentId(parentDocumentId)
        requireDrive(parent.drive)
        if (parent.kind == Kind.FILE || parent.kind == Kind.FOLDER && folder(parent) == null) unavailable()
        val out = MatrixCursor(projection ?: documentColumns)
        folders.childrenOf(parent.key, parent.drive)
            .filterNot { it.isHidden || it.isArchived }
            .sortedBy { it.name.lowercase() }
            .forEach { addDirectory(out, DocumentId(parent.drive, Kind.FOLDER, it.id), it.name, it.modifiedAt) }
        for (file in matchingFiles(parent.drive, parent.key, filterByFolder = true)) addFile(out, file)
        requireDrive(parent.drive)
        out
    }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor = work {
        val drive = rootId.toLongOrNull() ?: unavailable()
        requireDrive(drive)
        val out = MatrixCursor(projection ?: documentColumns)
        val term = query.trim()
        if (term.isNotEmpty()) {
            for (file in matchingFiles(drive, nameQuery = term.take(180))) addFile(out, file)
        }
        requireDrive(drive)
        out
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = try {
        work {
            val parent = parseDocumentId(parentDocumentId)
            val child = parseDocumentId(documentId)
            if (parent.drive != child.drive || parent.kind == Kind.FILE || parent == child) return@work false
            requireDrive(parent.drive)
            if (parent.kind == Kind.FOLDER && folder(parent) == null) return@work false
            val direct = when (child.kind) {
                Kind.ROOT -> return@work false
                Kind.FILE -> (file(child) ?: return@work false).folderId
                Kind.FOLDER -> (folder(child) ?: return@work false).parentId
            }
            withAncestors(child.drive, direct) { it == parent.key }
        }
    } catch (_: FileNotFoundException) {
        false
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("TeleDrive documents are read only")
        signal?.throwIfCanceled()
        val id = parseDocumentId(documentId)
        if (id.kind != Kind.FILE) unavailable()
        val grant = work {
            requireDrive(id.drive)
            streams.prepare(file(id) ?: unavailable())
        }
        return openTelegramDescriptor(
            requireNotNull(context), streams, grant.file.sizeBytes, { streams.source(grant) }, signal
        ) {
            requireDrive(id.drive)
            if (!streams.canRead(grant.file) || file(id) == null) unavailable()
        }
    }

    private suspend fun requireDrive(drive: Long) {
        val prefs = settings.preferences.first()
        if (prefs.storageChatId != drive || !prefs.onboardingComplete || prefs.blockScreenCapture ||
            lock.locked.value || telegram.authState.value != TelegramAuthState.Ready
        ) {
            throw FileNotFoundException("Open TeleDrive and unlock this drive first")
        }
    }

    private suspend fun folder(id: DocumentId): FolderEntity? {
        val folder = folders.byId(id.key ?: return null) ?: return null
        return folder.takeIf { withAncestors(id.drive, it.id) { parent -> parent == null } }
    }

    private suspend fun file(id: DocumentId): DriveFile? {
        val file = files.byId(id.key ?: return null)?.toDomain() ?: return null
        if (file.chatId != id.drive || !visible(file, excludeNotes())) return null
        if (!withAncestors(id.drive, file.folderId) { it == null }) return null
        return file
    }

    private suspend fun matchingFiles(
        drive: Long,
        parent: String? = null,
        filterByFolder: Boolean = false,
        nameQuery: String? = null
    ): List<DriveFile> {
        val excludeNotes = excludeNotes()
        val spec = FileQuerySpec(
            chatId = drive, scopeToChat = true, folderId = parent, filterByFolder = filterByFolder,
            nameQuery = nameQuery, excludeHiddenAncestors = true,
            excludeNotes = excludeNotes, backedUpOnly = true
        )
        val query = if (nameQuery == null) FileQueryBuilder.build(spec) else FileQueryBuilder.buildLimited(spec, 200)
        return files.observeList(query).first()
            .map { it.toDomain() }
            .filter { visible(it, excludeNotes) && withAncestors(drive, it.folderId) { parent -> parent == null } }
    }

    private fun visible(file: DriveFile, excludeNotes: Boolean): Boolean =
        file.hasRemoteCopy && file.remoteFileId != null && file.backupState == BackupState.BACKED_UP &&
            !file.isHidden && !file.isArchived && !file.isTrashed &&
            (!excludeNotes || !file.name.endsWith(".md", ignoreCase = true))

    private suspend fun excludeNotes(): Boolean {
        val notes = access.notesLock ?: return false
        notes.awaitReady()
        return notes.state.value.let { it.configured || it.error }
    }

    private suspend fun withAncestors(drive: Long, start: String?, matches: (String?) -> Boolean): Boolean {
        var current = start
        val seen = mutableSetOf<String>()
        while (current != null) {
            if (seen.size >= 256 || !seen.add(current)) return false
            val folder = folders.byId(current) ?: return false
            if (folder.chatId != drive || folder.isHidden || folder.isArchived || folder.trashedAt != null) return false
            if (matches(current)) return true
            current = folder.parentId
        }
        return matches(null)
    }

    private fun addDirectory(out: MatrixCursor, id: DocumentId, title: String, modifiedAt: Long?) {
        out.newRow().add(Document.COLUMN_DOCUMENT_ID, id.encode()).add(Document.COLUMN_DISPLAY_NAME, title)
            .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR).add(Document.COLUMN_LAST_MODIFIED, modifiedAt)
            .add(Document.COLUMN_FLAGS, 0)
    }

    private fun addFile(out: MatrixCursor, file: DriveFile) {
        out.newRow().add(Document.COLUMN_DOCUMENT_ID, DocumentId(requireNotNull(file.chatId), Kind.FILE, file.id).encode())
            .add(Document.COLUMN_DISPLAY_NAME, file.name).add(Document.COLUMN_MIME_TYPE, file.mimeType)
            .add(Document.COLUMN_SIZE, file.sizeBytes).add(Document.COLUMN_LAST_MODIFIED, file.modifiedAt)
            .add(Document.COLUMN_FLAGS, 0)
    }

    private fun <T> work(block: suspend () -> T): T = try {
        runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    } catch (error: FileNotFoundException) {
        throw error
    } catch (error: Exception) {
        throw FileNotFoundException("TeleDrive document unavailable").apply { initCause(error) }
    }

    private fun unavailable(): Nothing = throw FileNotFoundException("TeleDrive document unavailable")

    private companion object {
        val rootColumns = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_ICON, Root.COLUMN_FLAGS)
        val documentColumns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
    }
}

internal enum class Kind { ROOT, FOLDER, FILE }

internal data class DocumentId(val drive: Long, val kind: Kind, val key: String? = null) {
    fun encode(): String = listOf(drive.toString(), kind.name,
        key?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) }.orEmpty()
    ).joinToString("/")
}

internal fun parseDocumentId(value: String): DocumentId {
    val parts = value.split('/', limit = 3)
    val id = runCatching {
        val drive = parts[0].toLong()
        val kind = Kind.valueOf(parts[1])
        require(parts[2].length <= 512)
        val key = parts[2].takeIf { it.isNotEmpty() }?.let { Base64.getUrlDecoder().decode(it).decodeToString() }
        require((key == null) == (kind == Kind.ROOT))
        DocumentId(drive, kind, key).also { require(it.encode() == value) }
    }.getOrElse { throw FileNotFoundException("TeleDrive document unavailable") }
    return id
}

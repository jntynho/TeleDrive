package com.infinity.drive.presentation.sync

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FolderDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.repository.DriveContentReader
import com.infinity.drive.data.repository.TelegramFileVersions
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.TransferRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

internal data class SyncLocal(val path: String, val source: DocumentFile, val digest: String)
internal data class SyncEntry(
    val path: String,
    val local: SyncLocal?,
    val remote: FileEntity?,
    val remoteDigest: String?,
    val action: SyncAction
)

internal data class SyncPlan(
    val drive: Long,
    val location: Uri,
    val folder: String,
    val previousRootId: String?,
    val entries: List<SyncEntry>
)

internal data class SyncResult(val uploaded: Int, val downloaded: Int, val failed: Int)

internal class AndroidFolderSync(
    private val context: Context,
    private val storage: AppStoragePaths,
    private val settings: SettingsRepository,
    private val lock: AppLockManager,
    private val folders: FolderDao,
    private val files: FileDao,
    private val access: HiddenContentAccess,
    private val repository: FileRepository,
    private val reader: DriveContentReader,
    private val importer: FileImporter,
    private val transfers: TransferRepository,
    private val versions: TelegramFileVersions
) {
    private val gate = Mutex()

    suspend fun preview(location: Uri, folder: String, direction: SyncDirection): SyncPlan = withContext(Dispatchers.IO) {
        require(FileNameUtils.isValidName(folder))
        val drive = activeDrive()
        val root = requireNotNull(DocumentFile.fromTreeUri(context, location))
        check(root.isDirectory && root.canRead())
        val remoteRoot = findRoot(drive, folder)
        if (remoteRoot != null) check(!remoteRoot.isHidden && access.canReadFolder(remoteRoot.id))
        val localRows = linkedMapOf<String, SyncLocal>()
        val remoteRows = linkedMapOf<String, FileEntity>()
        val remotePaths = mutableMapOf<String, String>()
        val blocked = mutableSetOf<String>()
        suspend fun scanLocal(parent: DocumentFile, relative: String, depth: Int) {
            check(depth < MAX_DEPTH)
            for (child in parent.listFiles()) {
                currentCoroutineContext().ensureActive()
                val name = child.name ?: continue
                if (name.startsWith(PREVIOUS_PREFIX) || !FileNameUtils.isValidName(name)) continue
                val childPath = join(relative, name)
                if (child.isDirectory) scanLocal(child, childPath, depth + 1)
                else if (child.isFile) {
                    check(localRows.size < MAX_ITEMS) { "Choose a smaller folder." }
                    val key = childPath.lowercase()
                    if (localRows.containsKey(key)) blocked.add(key)
                    localRows[key] = SyncLocal(childPath, child, localDigest(child))
                }
            }
        }
        suspend fun scanRemote(id: String, relative: String, depth: Int) {
            check(depth < MAX_DEPTH)
            for (child in folders.childrenOf(id, drive)) {
                val path = join(relative, child.name)
                if (child.isHidden || !access.canReadFolder(child.id)) blocked.add(path.lowercase())
                else scanRemote(child.id, path, depth + 1)
            }
            for (file in files.filesWithNamesInFolder(id, drive)) {
                check(remoteRows.size < MAX_ITEMS) { "Choose a smaller folder." }
                val path = join(relative, file.name)
                val key = path.lowercase()
                if (file.isHidden || !access.canRead(file.id)) {
                    blocked.add(key)
                    continue
                }
                remotePaths[key] = path
                if (remoteRows.containsKey(key)) blocked.add(key)
                remoteRows[key] = file
            }
        }
        scanLocal(root, "", 0)
        if (remoteRoot != null) scanRemote(remoteRoot.id, "", 0)
        val journal = journal(drive, location, folder)
        val base = readJournal(journal)
        val result = (localRows.keys + remoteRows.keys).sorted().map { key ->
            check(activeDrive() == drive)
            val local = localRows[key]
            val remote = remoteRows[key]
            val pending = remote != null && (remote.backupState != BackupState.BACKED_UP || remote.messageId == null)
            val serverPath = remotePaths[key]
            val deny = blocked.any { key == it || key.startsWith(it + '/') } ||
                (serverPath != null && local != null && serverPath != local.path)
            val digest = remote?.takeUnless { pending || deny }?.let { file ->
                file.contentHash ?: if (local != null) reader.copyRemote(file, DISCARD) { verifyRemote(drive, file) } else UNKNOWN
            }
            val path = serverPath ?: local?.path ?: key
            SyncEntry(path, local, remote, digest, syncAction(direction, local?.digest, digest,
                base.optString(key).takeIf(String::isNotBlank), pending, deny))
        }
        SyncPlan(drive, location, folder, remoteRoot?.id, result)
    }

    suspend fun apply(plan: SyncPlan): SyncResult = withContext(Dispatchers.IO) { gate.withLock {
        check(activeDrive() == plan.drive)
        val local = requireNotNull(DocumentFile.fromTreeUri(context, plan.location))
        val currentRoot = findRoot(plan.drive, plan.folder)
        check(currentRoot?.id == plan.previousRootId)
        val journal = journal(plan.drive, plan.location, plan.folder)
        val record = readJournal(journal)
        var up = 0
        var down = 0
        var failed = 0
        var cloudRoot = currentRoot?.id
        for (entry in plan.entries) {
            currentCoroutineContext().ensureActive()
            check(activeDrive() == plan.drive)
            try {
                when (entry.action) {
                    SyncAction.MATCH -> record.put(entry.path.lowercase(), entry.local?.digest)
                    SyncAction.UPLOAD -> {
                        val source = requireNotNull(entry.local)
                        check(localDigest(source.source) == source.digest)
                        if (entry.remote == null) {
                            if (cloudRoot == null) cloudRoot = requireNotNull(repository.resolveImportFolder(plan.folder, null))
                            uploadNew(plan.drive, cloudRoot, entry, source)
                        } else {
                            verifyRemote(plan.drive, entry.remote)
                            versions.replace(entry.remote, source.source.uri.toString(), source.digest)
                        }
                        up++
                        record.put(entry.path.lowercase(), source.digest)
                    }
                    SyncAction.DOWNLOAD -> {
                        val hash = download(plan.drive, local, entry)
                        down++
                        record.put(entry.path.lowercase(), hash)
                    }
                    else -> continue
                }
                writeJournal(journal, record)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed++
            }
        }
        SyncResult(up, down, failed)
    } }

    private suspend fun uploadNew(drive: Long, cloud: String?, entry: SyncEntry, local: SyncLocal) {
        val parent = entry.path.substringBeforeLast('/', "")
        val folder = repository.resolveImportFolder(parent, cloud)
        val name = entry.path.substringAfterLast('/')
        check(activeDrive() == drive && repository.findNameClash(name, folder) == null &&
            access.canReadFolder(folder) && !access.protectedFolder(folder))
        val source = versions.prepareVerified(local.source.uri.toString(), local.digest)
        var kept = false
        try {
            val result = repository.registerImport(source, folder, name, expectedDrive = drive)
            val created = (result as? AppResult.Success)?.value ?: error("Could not register this file.")
            kept = true
            if (!created.hasRemoteCopy) check(transfers.enqueueUpload(created.id) is AppResult.Success)
        } finally {
            if (!kept) withContext(NonCancellable) {
                if (source.path in files.allLocalPaths()) importer.registered(source.path)
                else importer.discard(source)
            }
        }
    }

    private suspend fun download(drive: Long, tree: DocumentFile, entry: SyncEntry): String {
        val remote = requireNotNull(entry.remote)
        check(tree.canWrite())
        val ancestors = entry.path.split('/').dropLast(1)
        var parent = tree
        for (name in ancestors) {
            check(FileNameUtils.isValidName(name))
            parent = parent.findFile(name)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(name) ?: error("Could not create the target folder.")
        }
        val name = entry.path.substringAfterLast('/')
        check(FileNameUtils.isValidName(name))
        val session = UUID.randomUUID().toString()
        val temporary = parent.createFile("application/octet-stream", PREVIOUS_PREFIX + "download-" + session)
            ?: error("The selected folder refused the write.")
        var replaced: DocumentFile? = null
        try {
            val stream = requireNotNull(context.contentResolver.openOutputStream(temporary.uri, "w"))
            val hash = stream.buffered().use { output -> reader.copyRemote(remote, output) { verifyRemote(drive, remote) } }
            check(entry.remoteDigest == UNKNOWN || entry.remoteDigest == hash)
            check(localDigest(temporary) == hash)
            val existing = parent.findFile(name)
            if (entry.local != null) {
                check(existing?.uri == entry.local.source.uri && localDigest(existing) == entry.local.digest)
                check(existing.renameTo(PREVIOUS_PREFIX + "previous-" + session + "-" + name.take(60)))
                replaced = existing
            } else check(existing == null)
            check(temporary.renameTo(name) && temporary.name == name)
            return hash
        } catch (error: Throwable) {
            temporary.delete()
            replaced?.renameTo(name)
            throw error
        }
    }

    private suspend fun verifyRemote(drive: Long, expected: FileEntity) {
        val current = files.byId(expected.id)
        check(activeDrive() == drive && current != null && current.chatId == drive && current.messageId == expected.messageId &&
            current.modifiedAt == expected.modifiedAt && current.trashedAt == null &&
            current.backupState == BackupState.BACKED_UP && !current.isHidden &&
            !access.protectedFolder(current.folderId) && access.canRead(expected.id))
    }

    private suspend fun activeDrive(): Long {
        val prefs = settings.preferences.first()
        check(prefs.onboardingComplete && !lock.locked.value)
        return requireNotNull(prefs.storageChatId)
    }

    private suspend fun localDigest(file: DocumentFile): String {
        val digester = MessageDigest.getInstance("SHA-256")
        requireNotNull(context.contentResolver.openInputStream(file.uri)).buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digester.update(buffer, 0, count)
            }
        }
        return digester.digest().toHexString()
    }

    private suspend fun findRoot(drive: Long, name: String): com.infinity.drive.data.local.entity.FolderEntity? {
        val found = folders.childrenOf(null, drive).filter { it.name.equals(name, true) }
        check(found.size <= 1)
        return found.firstOrNull()
    }

    private fun journal(drive: Long, tree: Uri, name: String): AtomicFile {
        val key = MessageDigest.getInstance("SHA-256").digest("$drive\u0000$tree\u0000${name.lowercase()}".encodeToByteArray()).toHexString()
        return AtomicFile(File(File(storage.filesDir, "folder-sync").apply { mkdirs() }, "$key.json"))
    }

    private fun readJournal(file: AtomicFile): JSONObject =
        if (file.baseFile.isFile) JSONObject(file.readFully().decodeToString()) else JSONObject()

    private fun writeJournal(file: AtomicFile, data: JSONObject) {
        val stream = file.startWrite()
        try { stream.write(data.toString().encodeToByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    private fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

    private companion object {
        const val MAX_DEPTH = 48
        const val MAX_ITEMS = 2000
        const val PREVIOUS_PREFIX = ".teledrive-sync-"
        const val UNKNOWN = "unknown"
        val DISCARD = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
        }
    }
}

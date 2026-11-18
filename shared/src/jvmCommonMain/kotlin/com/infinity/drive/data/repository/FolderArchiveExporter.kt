package com.infinity.drive.data.repository

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.KeyUnavailableException
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FolderDao
import com.infinity.drive.domain.model.BackupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FolderArchiveExporter(
    private val active: ActiveChannel,
    private val folders: FolderDao,
    private val files: FileDao,
    private val hidden: HiddenContentAccess,
    private val reader: DriveContentReader
) {
    suspend fun export(
        folderId: String?,
        output: OutputStream,
        progress: (Int) -> Unit
    ): AppResult<Int> = withContext(Dispatchers.IO) {
        val drive = active.id() ?: return@withContext AppResult.Failure(AppError.NotFound)
        hidden.lock.awaitReady()
        hidden.notesLock?.awaitReady()
        val initiallyRestricted = hidden.lock.state.value.restricted
        val notesRestricted = hidden.notesLock?.state?.value?.restricted == true
        suspend fun checkAccess() {
            ensureActive()
            if (active.id() != drive || hidden.lock.state.value.restricted != initiallyRestricted ||
                (hidden.notesLock?.state?.value?.restricted == true) != notesRestricted
            ) throw SecurityException("Drive or lock changed")
        }
        if (!hidden.canReadFolder(folderId)) return@withContext AppResult.Failure(AppError.PermissionDenied())
        val root = folderId?.let {
            folders.byId(it)?.takeIf { row -> row.chatId == drive && row.trashedAt == null }
                ?: return@withContext AppResult.Failure(AppError.NotFound)
        }
        val pending = ArrayDeque<ArchiveFolder>()
        pending.add(ArchiveFolder(root?.id, root?.name?.let { archiveComponent(it) + "/" }.orEmpty()))
        val visited = mutableSetOf<String>()
        var written = 0
        try {
            ZipOutputStream(output).use { zip ->
                while (pending.isNotEmpty()) {
                    checkAccess()
                    val folder = pending.removeLast()
                    if (folder.id != null && !visited.add(folder.id)) throw IOException("Invalid folder tree")
                    if (!hidden.canReadFolder(folder.id)) continue
                    if (folder.path.isNotEmpty()) {
                        zip.putNextEntry(ZipEntry(folder.path))
                        zip.closeEntry()
                    }
                    val names = mutableSetOf<String>()
                    for (child in folders.childrenOf(folder.id, drive).sortedBy { it.id }) {
                        if (!hidden.canReadFolder(child.id)) continue
                        val name = reserveArchiveComponent(child.name, names)
                        pending.add(ArchiveFolder(child.id, folder.path + name + "/"))
                    }
                    for (file in files.filesWithNamesInFolder(folder.id, drive).sortedBy { it.id }) {
                        checkAccess()
                        if (!hidden.canRead(file.id)) continue
                        val name = reserveArchiveComponent(file.name, names)
                        zip.putNextEntry(ZipEntry(folder.path + name))
                        val local = file.localPath?.let(::File)?.takeIf { it.isFile }
                        val digest = if (local != null) reader.copyLocal(local, file.sizeBytes, zip) else {
                            if (file.backupState != BackupState.BACKED_UP) throw IOException("A file has no available copy")
                            reader.copyRemote(file, zip) {
                                checkAccess()
                                if (!hidden.canRead(file.id)) throw SecurityException("File was locked")
                            }
                        }
                        if (file.contentHash != null && !file.contentHash.equals(digest, ignoreCase = true)) {
                            throw IOException("File contents changed")
                        }
                        checkAccess()
                        if (!hidden.canRead(file.id)) throw SecurityException("File was locked")
                        zip.closeEntry()
                        progress(++written)
                    }
                }
                checkAccess()
            }
            AppResult.Success(written)
        } catch (_: SecurityException) {
            AppResult.Failure(AppError.PermissionDenied())
        } catch (_: KeyUnavailableException) {
            AppResult.Failure(AppError.KeyUnreadable)
        } catch (e: TelegramException) {
            AppResult.Failure(if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
            else AppError.TelegramError(e.code, e.message))
        } catch (_: IOException) {
            AppResult.Failure(AppError.TransferFailed("The ZIP could not be completed. Check file access and destination space."))
        } catch (_: IllegalArgumentException) {
            AppResult.Failure(AppError.TransferFailed())
        } catch (_: GeneralSecurityException) {
            AppResult.Failure(AppError.EncryptionFailure())
        }
    }

    private data class ArchiveFolder(val id: String?, val path: String)
}

internal fun archiveComponent(raw: String): String = FileNameUtils.sanitize(raw)
    .trimEnd(' ', '.').ifBlank { "unnamed" }

internal fun reserveArchiveComponent(raw: String, used: MutableSet<String>): String {
    val name = FileNameUtils.uniqueName(archiveComponent(raw)) { it.lowercase() in used }
    used.add(name.lowercase())
    return name
}

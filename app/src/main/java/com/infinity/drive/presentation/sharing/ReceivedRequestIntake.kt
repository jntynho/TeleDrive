package com.infinity.drive.presentation.sharing

import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.core.files.ImportedFile
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.TransferRepository
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class ReceivedRequestIntake(
    private val paths: AppStoragePaths,
    private val importer: FileImporter,
    private val files: FileRepository,
    private val transfers: TransferRepository
) {
    private val serial = Mutex()

    suspend fun save(
        request: UploadRequest,
        drive: Long,
        validate: suspend () -> Unit,
        copy: suspend (OutputStream) -> Unit
    ) = serial.withLock {
        val incoming = requireNotNull(request.incoming)
        require(incoming.size in 1..AndroidPublicLinks.MAX_SHARE_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        val folder = File(paths.filesDir, "imports/request-${request.id}").apply { mkdirs() }
        val destination = File(folder, FileNameUtils.sanitize(incoming.name))
        val partial = File(folder, "receiving.partial")
            .takeUnless { it == destination } ?: File(folder, "receiving-other.partial")
        importer.reserve(partial.path)
        importer.reserve(destination.path)
        try {
            validate()
            if (!destination.isFile) {
                check(folder.usableSpace >= incoming.size)
                partial.outputStream().buffered().use { stream ->
                    copy(java.security.DigestOutputStream(stream, digest))
                }
                check(partial.length() == incoming.size && digest.digest().toHexString() == incoming.sha256)
                validate()
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } else {
                destination.inputStream().buffered().use { stream ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                check(destination.length() == incoming.size && digest.digest().toHexString() == incoming.sha256)
            }
            validate()
            val entry = files.registerImport(
                ImportedFile(destination.path, destination.name, destination.length()), null, destination.name,
                expectedDrive = drive
            )
            val file = (entry as? AppResult.Success)?.value ?: error("Could not import the received file")
            if (!file.hasRemoteCopy) check(transfers.enqueueUploads(listOf(file.id)) is AppResult.Success)
        } finally {
            partial.delete()
            importer.registered(partial.path)
            importer.registered(destination.path)
            folder.delete()
        }
    }
}

package com.infinity.drive.presentation.upload

import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.resources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Application-scoped import path shared by Home's picker and file-manager imports. */
class UploadCoordinator(private val fileRepository:FileRepository,
    private val transferRepository:TransferRepository,private val fileImporter:FileImporter) {
    private val _messages=MutableSharedFlow<UiText>(extraBufferCapacity=16)
    val messages=_messages.asSharedFlow()
    private val importScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            SafeLog.e(TAG, "Selected file import failed", error)
            _messages.tryEmit(UiText.Resource(Res.string.files_import_failed))
        }
    )

    fun importAndUpload(uris: List<String>, target: String? = null) {
        if (uris.isEmpty()) return
        importScope.launch {
            _messages.tryEmit(UiText.Resource(Res.string.files_import_preparing))
            var imported = 0
            var restored = 0
            var duplicates = 0
            var copied = 0
            var failed = 0
            val sources = uris.flatMap { uri -> fileImporter.expand(uri) }
            for (source in sources) {
                val destination = fileRepository.resolveImportFolder(
                    source.relativeFolder,
                    target
                )
                val file = fileImporter.import(source.reference)
                if (file == null) {
                    failed++
                    continue
                }
                val existing = fileRepository.findDuplicate(file.path)
                if (existing != null) {
                    if (existing.folderId == destination) {
                        duplicates++
                    } else if (fileRepository.copyFiles(listOf(existing.id), destination)
                                is AppResult.Success
                    ) {
                        copied++
                    } else {
                        failed++
                    }
                    fileImporter.discard(file)
                    continue
                }
                when (
                    val result = fileRepository.importLocalFile(file.path, destination, file.name)
                ) {
                    is AppResult.Success -> {
                        val drive = result.value
                        if (drive.hasRemoteCopy) {
                            restored++
                        } else {
                            if(transferRepository.enqueueUpload(drive.id) is AppResult.Failure)failed++ else imported++
                        }
                    }

                    is AppResult.Failure -> {
                        fileImporter.discard(file)
                        failed++
                    }
                }
            }
            _messages.tryEmit(importSummary(imported, restored, duplicates, copied, failed))
        }
    }

    private fun importSummary(
        imported: Int,
        restored: Int,
        duplicates: Int,
        copied: Int,
        failed: Int
    ): UiText = when {
        imported == 0 && restored == 0 && duplicates == 0 && copied > 0 ->
            UiText.Resource(Res.string.files_import_copied, copied)

        imported > 0 && copied > 0 ->
            UiText.Resource(Res.string.files_import_uploading_copied, imported, copied)

        imported == 0 && restored == 0 && duplicates > 0 ->
            UiText.Resource(Res.string.files_import_duplicates, duplicates)

        imported > 0 && duplicates > 0 ->
            UiText.Resource(Res.string.files_import_uploading_duplicates, imported, duplicates)

        imported == 0 && restored > 0 && failed == 0 ->
            UiText.Resource(Res.string.files_import_restored, restored)

        imported == 0 && restored == 0 ->
            UiText.Resource(Res.string.files_import_failed)

        restored > 0 && failed == 0 ->
            UiText.Resource(Res.string.files_import_uploading_restored, imported, restored)

        failed == 0 -> UiText.Resource(Res.string.files_import_uploading, imported)
        else -> UiText.Resource(Res.string.files_import_uploading_failed, imported, failed)
    }


    private companion object { const val TAG="UploadCoordinator" }
}

package app.tgdrive.rebuild

import android.app.Application
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.FileImporter
import com.infinity.drive.core.files.ImportSource
import com.infinity.drive.core.files.ImportedFile
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.domain.repository.TrashRepository
import com.infinity.drive.presentation.upload.UploadCoordinator
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class UploadCoordinatorTest {
    @Test fun failedRegistrationLeavesExistingFileInPlace() = runBlocking {
        assertEquals(listOf("register"), replace(register = false))
    }

    @Test fun failedQueueingLeavesExistingFileInPlace() = runBlocking {
        assertEquals(listOf("register", "queue"), replace(queue = false))
    }

    @Test fun successfulReplacementQueuesNewFileBeforeTrashingOldFile() = runBlocking {
        assertEquals(listOf("register", "queue", "trash"), replace())
    }

    private suspend fun replace(register: Boolean = true, queue: Boolean = true): List<String> = coroutineScope {
        val calls = CopyOnWriteArrayList<String>()
        val old = DriveFile(
            id = "old", folderId = null, name = "Report.pdf", sizeBytes = 42, mimeType = "application/pdf",
            category = FileCategory.DOCUMENT, localPath = null, contentHash = null, chatId = -100L,
            messageId = 1L, remoteFileId = "remote", remoteUniqueId = null, backupState = BackupState.BACKED_UP,
            isHidden = false, isArchived = false, isFavorite = false, isEncrypted = false,
            width = null, height = null, durationMs = null, trashedAt = null, createdAt = 0, modifiedAt = 0, addedAt = 0
        )
        val pending = old.copy(id = "new", messageId = null, remoteFileId = null, backupState = BackupState.NONE)
        val preferences = MutableStateFlow(UserPreferences(storageChatId = -100L, onboardingComplete = true))
        val coordinator = UploadCoordinator(
            fileRepository = fake<FileRepository> { method, _ ->
                when (method) {
                    "findNameClash" -> old
                    "observeFiles" -> flowOf(listOf(old))
                    "resolveImportFolder" -> null
                    "registerImport" -> {
                        calls.add("register")
                        if (register) AppResult.Success(pending) else AppResult.Failure(AppError.NotFound)
                    }
                    else -> error("Unexpected FileRepository call $method")
                }
            },
            transferRepository = fake<TransferRepository> { method, args ->
                when (method) {
                    "observeActiveCount" -> flowOf(0)
                    "enqueueUploads" -> {
                        assertEquals(listOf("new"), args[0])
                        calls.add("queue")
                        if (queue) AppResult.Success(1) else AppResult.Failure(AppError.NotFound)
                    }
                    else -> error("Unexpected TransferRepository call $method")
                }
            },
            fileImporter = fake<FileImporter> { method, _ ->
                when (method) {
                    "expand" -> listOf(ImportSource("document", ""))
                    "describe" -> ImportedFile("document", "Report.pdf", 42)
                    "discard" -> Unit
                    else -> error("Unexpected FileImporter call $method")
                }
            },
            trash = fake<TrashRepository> { method, args ->
                check(method == "moveFilesToTrash")
                assertEquals(listOf("old"), args[0])
                calls.add("trash")
                AppResult.Success(Unit)
            },
            settings = fake<SettingsRepository> { method, _ ->
                check(method == "getPreferences")
                preferences
            }
        )
        withTimeout(10_000) {
            val finished = async(start = CoroutineStart.UNDISPATCHED) { coordinator.messages.take(2).toList() }
            coordinator.importAndUpload(listOf("document"))
            coordinator.conflict.filterNotNull().first()
            coordinator.answer(UploadCoordinator.Answer(UploadCoordinator.Choice.REPLACE))
            finished.await()
        }
        calls.toList()
    }

    private inline fun <reified T> fake(crossinline response: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            response(method.name, args ?: emptyArray())
        } as T
}

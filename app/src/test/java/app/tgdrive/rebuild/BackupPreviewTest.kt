package app.tgdrive.rebuild

import android.app.Application
import androidx.room.Room
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.dispatchers.DispatcherProvider
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.StorageInspector
import com.infinity.drive.core.media.MediaInfo
import com.infinity.drive.core.telegram.TelegramLimits
import com.infinity.drive.core.transfer.CountingBackupSessionTracker
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.TransferEntity
import com.infinity.drive.data.repository.ActiveChannel
import com.infinity.drive.data.repository.BackupRepositoryImpl
import com.infinity.drive.data.repository.FolderPathResolver
import com.infinity.drive.data.repository.TransferRepositoryImpl
import com.infinity.drive.domain.model.BackupDecision
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.BackupTrigger
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.TransferState
import com.infinity.drive.domain.model.TransferType
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.ExclusionRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.usecase.DecideBackupActionUseCase
import com.infinity.drive.domain.usecase.EvaluateExclusionsUseCase
import com.infinity.drive.domain.usecase.ValidateUploadUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class BackupPreviewTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun overlappingFoldersAreScannedOnceAndNothingIsQueuedOrMigrated() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DriveInfinityDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val files = db.fileDao()
            val transfers = db.transferDao()
            val dir = temporary.newFolder()
            val nested = File(dir, "nested").apply { mkdir() }
            File(dir, "too-small.bin").writeText("tiny")
            File(nested, "eligible.bin").writeBytes(ByteArray(1024 * 1024))
            val storage = object : AppStoragePaths {
                override val filesDir = dir
                override val cacheDir = dir
                override val externalCacheDir: File? = null
                override val externalStorageRoot = dir
            }
            val settings = fake<SettingsRepository> { method, _ ->
                check(method == "getPreferences")
                flowOf(UserPreferences(storageChatId = -1L, backupMinFileSizeMb = 1))
            }
            val transferRepo = TransferRepositoryImpl(
                transfers, files, fake(), fake(), settings, ValidateUploadUseCase(), StorageInspector(storage), fake(), db
            )
            val repo = BackupRepositoryImpl(
                backupDao = db.backupDao(),
                fileDao = files,
                transferDao = transfers,
                transferRepository = transferRepo,
                backupSessionTracker = fake(),
                exclusionRepository = fake<ExclusionRepository> { method, _ ->
                    check(method == "getEnabled")
                    emptyList<Any>()
                },
                settingsRepository = settings,
                decideBackupAction = DecideBackupActionUseCase(EvaluateExclusionsUseCase()),
                mediaMetadataExtractor = fake(),
                folderPathResolver = FolderPathResolver(db.folderDao(), ActiveChannel(settings)),
                fileRepository = fake(),
                channelRepository = fake { method, args ->
                    check(method == "backupFolders" && args[0] == -1L && args[1] == false)
                    setOf(dir.path, nested.path, File(dir, "unreadable").path)
                },
                storagePaths = storage,
                dispatchers = object : DispatcherProvider {
                    override val main = Dispatchers.Unconfined
                    override val io = Dispatchers.Unconfined
                    override val default = Dispatchers.Unconfined
                }
            )
            val report = (repo.previewBackup() as AppResult.Success).value
            assertEquals(1, report.uploads)
            assertEquals(1_048_576L, report.uploadBytes)
            assertEquals(mapOf(BackupDecision.SKIP_TOO_SMALL to 1), report.skips)
            assertEquals(1, report.unreadableFolders)
            assertNull(db.backupDao().activeSession())
            assertTrue(transfers.nextQueued(1).isEmpty())
            assertEquals(0, files.fileCount(-1))
        } finally {
            db.close()
        }
    }

    @Test
    fun backupQueuesItsOwnFileWhenAnotherDriveUsesTheSamePath() = runTest {
        withBackupFolder { db, repo, source ->
            val previous = remoteFile(source, chatId = -2, localPath = source.path)
            db.fileDao().upsert(previous)

            val session = (repo.startBackup(BackupTrigger.MANUAL) as AppResult.Success).value!!
            val task = db.transferDao().bySession(session).single()
            val queued = db.fileDao().byId(requireNotNull(task.fileId))!!
            assertEquals(-1L, queued.chatId)
            assertEquals(source.path, queued.localPath)
            assertEquals(previous, db.fileDao().byId(previous.id))
        }
    }

    @Test
    fun backupDoesNotTreatAnUnverifiedNameAndSizeMatchAsIdentical() = runTest {
        withBackupFolder { db, repo, source ->
            val previous = remoteFile(source, chatId = -1, localPath = null)
            db.fileDao().upsert(previous)

            val report = (repo.previewBackup() as AppResult.Success).value
            assertEquals(1, report.uploads)
            val session = (repo.startBackup(BackupTrigger.MANUAL) as AppResult.Success).value!!
            val task = db.transferDao().bySession(session).single()
            assertEquals(source.path, db.fileDao().byId(requireNotNull(task.fileId))?.localPath)
            assertNull(db.fileDao().byId(previous.id)?.localPath)
        }
    }

    @Test
    fun backupDoesNotTreatAChangedFileAtTheSamePathAsIdentical() = runTest {
        withBackupFolder { db, repo, source ->
            db.fileDao().upsert(remoteFile(source, chatId = -1, localPath = source.path))

            val report = (repo.previewBackup() as AppResult.Success).value

            assertEquals(1, report.uploads)
        }
    }

    @Test
    fun backupDoesNotQueueChangedBytesWithThePreviousRemoteHash() = runTest {
        withBackupFolder { db, repo, source ->
            val oldHash = "0".repeat(64)
            db.fileDao().upsert(remoteFile(source, chatId = -1, localPath = source.path).copy(
                contentHash = oldHash, modifiedAt = source.lastModified()
            ))

            val session = (repo.startBackup(BackupTrigger.MANUAL) as AppResult.Success).value
            val task = db.transferDao().bySession(requireNotNull(session)).single()
            assertNull(db.fileDao().byId(requireNotNull(task.fileId))?.contentHash)
        }
    }

    @Test
    fun backupDoesNotLeaveAnActiveSessionWhenEveryFileAlreadyHasATransfer() = runTest {
        withBackupFolder { db, repo, source ->
            val file = remoteFile(source, chatId = -1, localPath = source.path).copy(
                messageId = null, remoteFileId = null, remoteUniqueId = null, backupState = BackupState.NONE
            )
            db.fileDao().upsert(file)
            db.transferDao().upsert(TransferEntity(
                id = "existing-transfer", type = TransferType.UPLOAD, fileId = file.id,
                displayName = file.name, localPath = null, chatId = null, messageId = null,
                remoteFileId = null, sizeBytes = file.sizeBytes, state = TransferState.QUEUED,
                createdAt = 1, updatedAt = 1
            ))

            val result = repo.startBackup(BackupTrigger.MANUAL)

            assertEquals(AppResult.Success(null), result)
            assertNull(db.backupDao().activeSession())
        }
    }

    private fun remoteFile(source: File, chatId: Long, localPath: String?) = FileEntity(
        id = "previous", folderId = null, name = source.name, sizeBytes = source.length(),
        mimeType = "application/octet-stream", category = FileCategory.OTHER, localPath = localPath,
        contentHash = null, chatId = chatId, messageId = 7, remoteFileId = "remote",
        remoteUniqueId = "unique", backupState = BackupState.BACKED_UP,
        createdAt = 1, modifiedAt = 1, addedAt = 1
    )

    @Test
    fun backupKeepsItsFoldersInTheDriveCapturedBeforeTheUserSwitches() = runTest {
        withBackupFolder(nested = true, switchAfterReads = 3) { db, repo, _ ->
            val session = (repo.startBackup(BackupTrigger.MANUAL) as AppResult.Success).value
            val task = db.transferDao().bySession(requireNotNull(session)).single()
            val file = requireNotNull(db.fileDao().byId(requireNotNull(task.fileId)))
            assertEquals(-1L, file.chatId)
            assertEquals(-1L, db.folderDao().byId(requireNotNull(file.folderId))?.chatId)
            assertTrue(db.folderDao().childrenOf(null, -2L).isEmpty())
        }
    }

    private suspend fun withBackupFolder(
        nested: Boolean = false,
        switchAfterReads: Int? = null,
        test: suspend (DriveInfinityDatabase, BackupRepositoryImpl, File) -> Unit
    ) {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DriveInfinityDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dir = temporary.newFolder()
            val parent = if (nested) File(dir, "camera").apply { mkdir() } else dir
            val source = File(parent, "selected.bin").apply { writeText("local contents") }
            val storage = object : AppStoragePaths {
                override val filesDir = dir
                override val cacheDir = dir
                override val externalCacheDir: File? = null
                override val externalStorageRoot = dir
            }
            var preferenceReads = 0
            val settings = fake<SettingsRepository> { method, _ ->
                check(method == "getPreferences")
                preferenceReads++
                val drive = if (switchAfterReads != null && preferenceReads > switchAfterReads) -2L else -1L
                flowOf(UserPreferences(storageChatId = drive, onboardingComplete = true))
            }
            val files = db.fileDao()
            val transfers = db.transferDao()
            val tracker = CountingBackupSessionTracker(transfers, db.backupDao())
            val transferRepository = TransferRepositoryImpl(
                transfers, files,
                fake { method, _ -> check(method == "getLimits"); TelegramLimits.REGULAR },
                fake { method, _ -> check(method == "kick"); null },
                settings, ValidateUploadUseCase(), StorageInspector(storage), tracker, db
            )
            val repository = BackupRepositoryImpl(
                backupDao = db.backupDao(),
                fileDao = files,
                transferDao = transfers,
                transferRepository = transferRepository,
                backupSessionTracker = tracker,
                exclusionRepository = fake<ExclusionRepository> { method, _ ->
                    check(method == "getEnabled")
                    emptyList<Any>()
                },
                settingsRepository = settings,
                decideBackupAction = DecideBackupActionUseCase(EvaluateExclusionsUseCase()),
                mediaMetadataExtractor = fake { method, _ ->
                    check(method == "extract")
                    MediaInfo(null, null, null)
                },
                folderPathResolver = FolderPathResolver(db.folderDao(), ActiveChannel(settings)),
                fileRepository = fake { method, _ -> check(method == "reviveTrashedCopy"); null },
                channelRepository = fake { method, args ->
                    check(method == "backupFolders" && args[0] == -1L)
                    setOf(dir.path)
                },
                storagePaths = storage,
                dispatchers = object : DispatcherProvider {
                    override val main = Dispatchers.Unconfined
                    override val io = Dispatchers.Unconfined
                    override val default = Dispatchers.Unconfined
                }
            )
            test(db, repository, source)
        } finally {
            db.close()
        }
    }

    private inline fun <reified T> fake(
        crossinline response: (String, Array<out Any?>) -> Any? = { name, _ -> error("Unexpected call: $name") }
    ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        response(method.name, args ?: emptyArray())
    } as T
}

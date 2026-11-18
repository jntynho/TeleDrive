package app.tgdrive.rebuild

import android.app.Application
import androidx.room.Room
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.dispatchers.DispatcherProvider
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.StorageInspector
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.repository.ActiveChannel
import com.infinity.drive.data.repository.BackupRepositoryImpl
import com.infinity.drive.data.repository.FolderPathResolver
import com.infinity.drive.data.repository.TransferRepositoryImpl
import com.infinity.drive.domain.model.BackupDecision
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

    private inline fun <reified T> fake(
        crossinline response: (String, Array<out Any?>) -> Any? = { name, _ -> error("Unexpected call: $name") }
    ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        response(method.name, args ?: emptyArray())
    } as T
}

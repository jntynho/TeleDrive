package app.tgdrive.rebuild

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.files.AndroidFileImporter
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.ImportedFile
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.core.security.HiddenLockManager
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.FolderEntity
import com.infinity.drive.data.mapper.toDomain
import com.infinity.drive.data.remote.telegram.ManifestCodec
import com.infinity.drive.data.repository.ActiveChannel
import com.infinity.drive.data.repository.DriveContentReader
import com.infinity.drive.data.repository.TelegramFileVersions
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.presentation.sync.AndroidFolderSync
import com.infinity.drive.presentation.sync.SyncAction
import com.infinity.drive.presentation.sync.SyncEntry
import com.infinity.drive.presentation.sync.SyncLocal
import com.infinity.drive.presentation.sync.SyncPlan
import com.infinity.drive.presentation.sync.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class FolderSyncApplyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun uploadReplacementRejectsBytesThatChangedDuringPreparation() = runBlocking {
        withUpload(existing = true) {
            val remote = requireNotNull(plan.entries.single().remote)
            assertEquals(SyncResult(uploaded = 0, downloaded = 0, failed = 1), sync.apply(plan))
            assertEquals(remote, db.fileDao().byId(remote.id))
            assertTrue(File(root, "imports").walkTopDown().none { it.isFile })
            assertTrue(File(root, "folder-sync").walkTopDown().none { it.isFile })
        }
    }

    @Test
    fun newUploadKeepsReviewedBytesWhenOriginalIsEditedAfterQueueing() = runBlocking {
        withUpload(existing = false) {
            assertEquals(SyncResult(uploaded = 1, downloaded = 0, failed = 0), sync.apply(plan))
            original.writeText("modified")
            assertEquals("reviewed", queued().readText())
        }
    }

    private data class UploadFixture(
        val sync: AndroidFolderSync,
        val plan: SyncPlan,
        val db: DriveInfinityDatabase,
        val root: File,
        val original: File,
        val queued: () -> File
    )

    private suspend fun withUpload(
        existing: Boolean,
        test: suspend UploadFixture.() -> Unit
    ) {
        val context = RuntimeEnvironment.getApplication()
        val before = temporary.newFile().apply { writeText("reviewed") }
        val after = temporary.newFile().apply { writeText("modified") }
        val authority = if (existing) "sync.fixture" else MediaStore.AUTHORITY
        val provider = ChangingDocument(before, after).apply {
            attachInfo(context, ProviderInfo().apply { this.authority = authority })
        }
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "root")
        val source = requireNotNull(DocumentFile.fromTreeUri(context, treeUri))
        val root = temporary.newFolder()
        val storage = object : AppStoragePaths {
            override val filesDir = root
            override val cacheDir = root
            override val externalCacheDir: File? = null
            override val externalStorageRoot: File? = null
        }
        val settings = fake<SettingsRepository> { method, _ ->
            check(method == "getPreferences")
            flowOf(UserPreferences(storageChatId = -1, onboardingComplete = true))
        }
        val db = Room.inMemoryDatabaseBuilder(context, DriveInfinityDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val files = db.fileDao()
        val folders = db.folderDao()
        val access = HiddenContentAccess(HiddenLockManager(fake { method, _ ->
            check(method == "read")
            null
        }, ownerScope = scope), files, folders)
        try {
            folders.upsert(FolderEntity("cloud-root", -1, null, "Cloud", createdAt = 1, modifiedAt = 1))
            val remote = FileEntity(
                id = "remote", folderId = "cloud-root", name = "source.txt", sizeBytes = 4,
                mimeType = "text/plain", category = FileCategory.DOCUMENT, localPath = null,
                contentHash = null, chatId = -1, messageId = 1, remoteFileId = "remote-id",
                remoteUniqueId = "unique-id", backupState = BackupState.BACKED_UP,
                createdAt = 1, modifiedAt = 1, addedAt = 1
            )
            if (existing) files.upsert(remote)
            val reviewed = SyncLocal("source.txt", source, "e4f934f321eb76c9bf8b5103e0a0d9afe72d6e62ace3d3ea849790619bf7487a")
            val plan = SyncPlan(-1, treeUri, "Cloud", "cloud-root", listOf(
                SyncEntry("source.txt", reviewed, remote.takeIf { existing }, null, SyncAction.UPLOAD)
            ))
            val parts = db.filePartDao()
            val importer = AndroidFileImporter(context, storage)
            val crypto = StreamCrypto()
            val reader = DriveContentReader(fake(), parts, fake(), crypto)
            lateinit var registered: File
            lateinit var queued: File
            val repository = fake<FileRepository> { method, args ->
                when (method) {
                    "resolveImportFolder" -> "cloud-root"
                    "findNameClash" -> null
                    "registerImport" -> {
                        registered = File((args[0] as ImportedFile).path)
                        AppResult.Success(remote.copy(id = "new", localPath = registered.path, messageId = null).toDomain())
                    }
                    else -> error("Unexpected repository call $method")
                }
            }
            val transfers = fake<TransferRepository> { method, _ ->
                check(method == "enqueueUpload")
                if (!existing) queued = registered
                AppResult.Success("queued")
            }
            val versions = TelegramFileVersions(fake(), ManifestCodec(crypto, fake()), files, parts,
                ActiveChannel(settings), access, importer, transfers, db.transferDao(), reader, storage)
            val sync = AndroidFolderSync(context, storage, settings, AppLockManager(settings), folders, files,
                access, repository, reader, importer, transfers, versions)
            UploadFixture(sync, plan, db, root, before) { queued }.test()
        } finally {
            access.close()
            scope.cancel()
            db.close()
        }
    }

    private class ChangingDocument(val before: File, val after: File) : ContentProvider() {
        private var opens = 0

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor = if (projection?.singleOrNull() == MediaStore.MediaColumns.DATA) {
            MatrixCursor(arrayOf(MediaStore.MediaColumns.DATA)).apply { addRow(arrayOf(before.path)) }
        } else MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any>("source.txt", before.length()))
            }

        override fun getType(uri: Uri) = "text/plain"

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(if (opens++ == 0) before else after, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private inline fun <reified T> fake(crossinline response: (String, Array<out Any?>) -> Any? = { name, _ -> error("Unexpected call: $name") }): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            response(method.name, args ?: emptyArray())
        } as T
}

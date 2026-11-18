package app.tgdrive.rebuild

import android.app.Application
import androidx.room.Room
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.TransferEntity
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.TransferState
import com.infinity.drive.domain.model.TransferType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class TransferBatchRetryTest {
    @Test
    fun onlyCurrentTemporaryFailuresInTheSelectedBatchAreRequeued() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DriveInfinityDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            for (id in listOf("eligible", "permanent", "duplicate", "other", "trashed", "superseded")) {
                db.fileDao().upsert(file(id, trashed = id == "trashed"))
            }
            val dao = db.transferDao()
            dao.upsert(task("good", "eligible", state = TransferState.RUNNING).copy(chatId = 123, messageId = 456))
            dao.failRunning("good", "timeout", 10, retryable = true)
            dao.upsert(task("hard-failure", "permanent").copy(retryableFailure = false))
            dao.upsert(task("old-duplicate", "duplicate"))
            dao.upsert(task("running-duplicate", "duplicate", state = TransferState.RUNNING))
            dao.upsert(task("other-batch", "other").copy(backupSessionId = null, uploadBatchId = "batch-2"))
            dao.upsert(task("already-trashed", "trashed"))
            dao.upsert(task("old", "superseded"))
            dao.upsert(task("finished", "superseded", state = TransferState.COMPLETED).copy(createdAt = 2))

            val candidates = dao.failedBatchCandidates("batch-1").map { it.id }
            assertEquals(listOf("good"), candidates)
            assertEquals(1, dao.resetFailedBatch(candidates, 20))
            assertEquals(0, dao.resetFailedBatch(candidates, 20))
            val queued = dao.byId("good")!!
            assertEquals(TransferState.QUEUED, queued.state)
            assertEquals(123L, queued.chatId)
            assertEquals(456L, queued.messageId)
            assertNull(queued.errorMessage)
            assertFalse(queued.retryableFailure)
            assertEquals(listOf("other-batch"), dao.failedBatchCandidates("batch-2").map { it.id })
            assertEquals(listOf("other-batch"), dao.failedBatchCandidates(null).map { it.id })
        } finally {
            db.close()
        }
    }

    private fun task(id: String, fileId: String, state: TransferState = TransferState.FAILED) = TransferEntity(
        id = id, type = TransferType.BACKUP, fileId = fileId, displayName = fileId, localPath = null,
        chatId = null, messageId = null, remoteFileId = null, sizeBytes = 1, state = state,
        backupSessionId = "batch-1", createdAt = 1, updatedAt = 1, retryableFailure = state == TransferState.FAILED
    )

    private fun file(id: String, trashed: Boolean) = FileEntity(
        id = id, folderId = null, name = id, sizeBytes = 1, mimeType = "image/jpeg",
        category = FileCategory.IMAGE, localPath = null, contentHash = null, chatId = null,
        messageId = null, remoteFileId = null, remoteUniqueId = null, backupState = BackupState.NONE,
        createdAt = 1, modifiedAt = 1, addedAt = 1, trashedAt = if (trashed) 1L else null
    )
}

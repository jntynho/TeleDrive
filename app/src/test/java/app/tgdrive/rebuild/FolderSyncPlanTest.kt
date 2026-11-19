package app.tgdrive.rebuild

import com.infinity.drive.presentation.sync.SyncAction
import com.infinity.drive.presentation.sync.SyncDirection
import com.infinity.drive.presentation.sync.syncAction
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSyncPlanTest {
    @Test fun `two way sync copies the changed side without overwriting a conflict`() {
        assertEquals(SyncAction.UPLOAD, syncAction(SyncDirection.BOTH, "new", "old", "old"))
        assertEquals(SyncAction.DOWNLOAD, syncAction(SyncDirection.BOTH, "old", "new", "old"))
        assertEquals(SyncAction.CONFLICT, syncAction(SyncDirection.BOTH, "local", "other", "old"))
        assertEquals(SyncAction.CONFLICT, syncAction(SyncDirection.BOTH, "local", "other", null))
    }

    @Test fun `direction and unfinished backups prevent unintended operations`() {
        assertEquals(SyncAction.SKIP, syncAction(SyncDirection.UPLOAD, null, "remote", null))
        assertEquals(SyncAction.SKIP, syncAction(SyncDirection.DOWNLOAD, "local", null, null))
        assertEquals(SyncAction.MATCH, syncAction(SyncDirection.BOTH, "same", "same", null))
        assertEquals(SyncAction.WAITING, syncAction(SyncDirection.BOTH, "same", null, "old", remotePending = true))
        assertEquals(SyncAction.CONFLICT, syncAction(SyncDirection.BOTH, "local", null, null, blocked = true))
    }
}

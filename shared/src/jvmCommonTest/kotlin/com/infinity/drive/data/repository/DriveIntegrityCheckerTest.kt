package com.infinity.drive.data.repository

import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.FilePartEntity
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveIntegrityCheckerTest {
    private val file = FileEntity(
        id = "f", folderId = null, name = "asset.bin", sizeBytes = 12,
        mimeType = "application/octet-stream", category = FileCategory.OTHER,
        localPath = null, contentHash = null, chatId = -1, messageId = 1,
        remoteFileId = "remote", remoteUniqueId = "unique", backupState = BackupState.BACKED_UP,
        partCount = 2, createdAt = 0, modifiedAt = 0, addedAt = 0
    )
    private val parts = listOf(
        FilePartEntity("f", 0, -1, 1, "r1", "u1", 0, 7, 7, 0),
        FilePartEntity("f", 1, -1, 2, "r2", "u2", 7, 5, 5, 0)
    )
    private val documents = parts.associate {
        it.messageId!! to RemoteDocument(
            -1, it.messageId!!, it.remoteFileId!!, it.remoteUniqueId!!, "part",
            "application/octet-stream", it.storedSize, "", 0
        )
    }

    @Test
    fun multipartLayoutRejectsGapsAndReferencesFromAnotherDrive() {
        val layout = layoutFor(-1, file, parts)!!
        assertEquals(listOf(7L, 5L), layout.map { it.plaintextBytes })
        assertNull(layoutFor(-1, file, parts.take(1)))
        assertNull(layoutFor(-1, file, listOf(parts[0], parts[1].copy(plainOffset = 9))))
        assertNull(layoutFor(-1, file, listOf(parts[0], parts[1].copy(chatId = -2))))
    }

    @Test
    fun failedLookupIsUnverifiedAndSuccessfulMissingLookupIsMissing() {
        val layout = layoutFor(-1, file, parts)!!
        assertEquals(Status.AVAILABLE, referenceStatus(layout, documents, emptySet()))
        assertEquals(Status.UNVERIFIED, referenceStatus(layout, documents - 2L, setOf(2L)))
        assertEquals(Status.MISSING, referenceStatus(layout, documents - 2L, emptySet()))
    }

    @Test
    fun comparesStableTelegramIdentityAndKnownRemoteLength() {
        val layout = layoutFor(-1, file, parts)!!
        val wrongIdentity = documents + (2L to documents.getValue(2L).copy(uniqueFileId = "wrong"))
        val wrongSize = documents + (2L to documents.getValue(2L).copy(sizeBytes = 3))
        assertEquals(Status.INCONSISTENT, referenceStatus(layout, wrongIdentity, emptySet()))
        assertEquals(Status.INCONSISTENT, referenceStatus(layout, wrongSize, emptySet()))
        assertEquals(Status.INCONSISTENT, referenceStatus(null, documents, emptySet()))
    }
}

package com.infinity.drive.data.repository

import com.infinity.drive.domain.repository.SyncRepository.RestoreProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryRestoreCheckpointTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun persistedPageSurvivesReopeningAndCanBeReplaced() {
        val checkpoint = temporary.newFile()
        val first = LibraryRestoreCheckpoint(100, RestoreProgress(9, 5, 2))
        val second = LibraryRestoreCheckpoint(50, RestoreProgress(14, 8, 3))
        first.write(checkpoint)
        assertEquals(first, LibraryRestoreCheckpoint.read(checkpoint))
        second.write(checkpoint)
        assertEquals(second, LibraryRestoreCheckpoint.read(checkpoint))
        assertEquals(listOf(checkpoint), temporary.root.listFiles()?.toList())
    }

    @Test
    fun invalidOrMissingCheckpointRestartsSafely() {
        val checkpoint = temporary.newFile()
        checkpoint.writeText("1\n4\n5\n0")
        assertEquals(LibraryRestoreCheckpoint(), LibraryRestoreCheckpoint.read(checkpoint))
        checkpoint.delete()
        assertEquals(LibraryRestoreCheckpoint(), LibraryRestoreCheckpoint.read(checkpoint))
    }

    @Test
    fun historyMustAdvanceTowardOlderMessagesOrFinish() {
        assertTrue(isAdvancingHistory(0, 200))
        assertTrue(isAdvancingHistory(200, 100))
        assertTrue(isAdvancingHistory(100, 0))
        assertFalse(isAdvancingHistory(100, 100))
        assertFalse(isAdvancingHistory(100, 200))
        assertFalse(isAdvancingHistory(100, -1))
    }
}

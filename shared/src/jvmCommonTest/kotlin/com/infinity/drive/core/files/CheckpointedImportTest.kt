package com.infinity.drive.core.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CheckpointedImportTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun resumesAfterInterruptedCopy() = runBlocking {
        val checkpoint = temp.newFile()
        val bytes = ByteArray(700_000) { (it % 127).toByte() }
        var iterations = 0
        try {
            CheckpointedImport.copy(checkpoint, bytes.size.toLong(), { bytes.inputStream() }) {
                if (++iterations == 2) throw CancellationException("Paused")
            }
            fail("The partial copy should have paused")
        } catch (_: CancellationException) {
            assertTrue(checkpoint.length() in 1 until bytes.size.toLong())
        }
        CheckpointedImport.copy(checkpoint, bytes.size.toLong(), { bytes.inputStream() }) {}
        assertArrayEquals(bytes, checkpoint.readBytes())
    }

    @Test fun aChangedProviderPrefixIsNeverMixedWithNewBytes() = runBlocking {
        val checkpoint = temp.newFile()
        checkpoint.writeText("old")
        val source = "new data".toByteArray()
        CheckpointedImport.copy(checkpoint, source.size.toLong(), { source.inputStream() }) {}
        assertArrayEquals(source, checkpoint.readBytes())
    }
}

package com.infinity.drive.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CleanupFileStampTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun detectsModifiedFilesAndSkipsPathsWithoutVerifiableContents() {
        val file = temp.newFile("local")
        file.writeText("original")
        val original = fileStamp(file.path)
        assertEquals(8L, original?.size)
        file.writeText("changed")
        assertNotEquals(original, fileStamp(file.path))
        assertNull(fileStamp(temp.newFolder().path))
        assertNull(fileStamp("content://media/item/1"))
    }
}

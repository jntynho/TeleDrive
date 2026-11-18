package com.infinity.drive.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectoryTreeLineTest {
    @Test
    fun visibleNameStaysOnOneLineAndCannotForgeTreeEntries() {
        assertEquals("    [Folder] Photos", directoryTreeLine("Photos", 2, folder = true))
        assertEquals(
            "  [File] a\\u000a[Folder] forged\\u202e\\\\b",
            directoryTreeLine("a\n[Folder] forged\u202e\\b", 1, folder = false)
        )
        assertEquals("[File] المستند", directoryTreeLine("المستند", 0, folder = false))
    }
}

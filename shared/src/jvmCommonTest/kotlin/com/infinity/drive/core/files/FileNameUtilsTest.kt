// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameUtilsTest {

    @Test
    fun `sanitize strips invalid characters`() {
        assertEquals("a_b_c_.jpg", FileNameUtils.sanitize("a/b\\c?.jpg"))
    }

    @Test
    fun `sanitize of blank yields placeholder`() {
        assertEquals("unnamed", FileNameUtils.sanitize("   "))
    }

    @Test fun `dot names are never filesystem destinations`() {
        assertEquals("unnamed",FileNameUtils.sanitize(".."))
        assertEquals("unnamed",FileNameUtils.sanitize("."))
    }
    @Test fun `restored folder paths cannot traverse above downloads`() {
        org.junit.Assert.assertTrue(runCatching { FileNameUtils.relativeFolderPath("photos/../../private") }.isFailure)
        org.junit.Assert.assertTrue(runCatching { FileNameUtils.relativeFolderPath("photos\\..\\private") }.isFailure)
        assertEquals("photos/2026",FileNameUtils.relativeFolderPath("photos/2026"))
    }

    @Test
    fun `unique name appends counter before extension`() {
        val existing = setOf("photo.jpg", "photo (1).jpg")
        val result = FileNameUtils.uniqueName("photo.jpg") { it in existing }
        assertEquals("photo (2).jpg", result)
    }

    @Test
    fun `unique name without extension`() {
        val existing = setOf("notes")
        assertEquals("notes (1)", FileNameUtils.uniqueName("notes") { it in existing })
    }

    @Test
    fun `unique name returns original when free`() {
        assertEquals("free.txt", FileNameUtils.uniqueName("free.txt") { false })
    }

    @Test
    fun `extension and base name helpers`() {
        assertEquals("jpg", FileNameUtils.extensionOf("a.b.jpg"))
        assertEquals("a.b", FileNameUtils.baseNameOf("a.b.jpg"))
        assertEquals("", FileNameUtils.extensionOf("noext"))
    }
}

package com.infinity.drive.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArchiveNamesTest {
    @Test fun unsafeAndCollidingComponentsRemainWithinTheirFolder() {
        val used = mutableSetOf<String>()
        assertEquals("unnamed", reserveArchiveComponent("..", used))
        assertEquals("Photo.jpg", reserveArchiveComponent("Photo.jpg", used))
        assertEquals("photo (1).JPG", reserveArchiveComponent("photo.JPG", used))
        assertFalse(archiveComponent("../test\\sample").any { it == '/' || it == '\\' })
    }
}

package com.infinity.drive.data.repository

import com.infinity.drive.domain.model.BackupFolderRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupFolderRuleTest {
    @Test fun theMostSpecificSelectedFolderControlsItsFiles() {
        val parent = BackupFolderRule(-1, "/media", mediaOnly = true)
        val child = BackupFolderRule(-1, "/media/DCIM", maximumSizeMb = 0)
        val rules = mapOf(parent.path to parent, child.path to child)
        val roots = listOf("/media/DCIM", "/media/Unrestricted", "/media")
        assertEquals(child, selectBackupFolderRule(File("/media/DCIM/a.jpg"), roots, rules))
        assertEquals(parent, selectBackupFolderRule(File("/media/DCIM-old/a.jpg"), roots, rules))
        assertNull(selectBackupFolderRule(File("/media/Unrestricted/a.jpg"), roots, rules))
        assertNull(selectBackupFolderRule(File("/media2/a.jpg"), roots, rules))
    }
}

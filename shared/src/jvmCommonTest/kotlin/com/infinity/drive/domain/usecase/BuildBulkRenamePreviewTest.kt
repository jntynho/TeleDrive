package com.infinity.drive.domain.usecase

import com.infinity.drive.domain.model.BulkRenameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildBulkRenamePreviewTest {
    private val sources = listOf(
        BuildBulkRenamePreview.Source("a", "photos", "Scan.JPG"),
        BuildBulkRenamePreview.Source("b", "photos", "Take.2.png")
    )

    @Test
    fun appliesSelectionOrderWhileKeepingExtensionsAndDottedNames() {
        val result = BuildBulkRenamePreview.create("{number} - {name}", sources, emptyMap())
        assertTrue(result.canApply)
        assertEquals(listOf("1 - Scan.JPG", "2 - Take.2.png"), result.rows.map { it.newName })
        assertFalse(BuildBulkRenamePreview.create("{name}", sources, emptyMap()).canApply)
    }

    @Test
    fun preventsInFolderCollisionsAndUnsafeNames() {
        val existing = mapOf<String?, List<BuildBulkRenamePreview.Source>>("photos" to listOf(BuildBulkRenamePreview.Source("other", "photos", "COPY.JPG")))
        val conflict = BuildBulkRenamePreview.create("copy", sources, existing)
        assertEquals(BulkRenameStatus.CONFLICT, conflict.rows[0].status)
        assertFalse(conflict.canApply)
        val invalid = BuildBulkRenamePreview.create("../{name}", sources, emptyMap())
        assertTrue(invalid.rows.all { it.status == BulkRenameStatus.INVALID })
        val duplicates = BuildBulkRenamePreview.create(
            "photo", listOf(sources[0], sources[1].copy(name = "other.JPG")), emptyMap()
        )
        assertTrue(duplicates.rows.all { it.status == BulkRenameStatus.CONFLICT })
    }
}

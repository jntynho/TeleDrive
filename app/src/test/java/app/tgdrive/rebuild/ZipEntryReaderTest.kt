package app.tgdrive.rebuild

import com.infinity.drive.presentation.preview.PreviewContent
import com.infinity.drive.presentation.preview.ZipEntryReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipEntryReaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun savesOnlyTheSelectedEntryWithoutUsingItsArchivePath() = runTest {
        val zip = temporary.newFile("archive.zip")
        val target = "wanted".toByteArray()
        val name = "../nested/selected.txt"
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("first.txt"))
            it.write("skip me".toByteArray())
            it.closeEntry()
            it.putNextEntry(ZipEntry(name))
            it.write(target)
            it.closeEntry()
        }
        val entry = PreviewContent.ArchiveEntry(
            name = name, sizeBytes = target.size.toLong(), compressedBytes = 0,
            isDirectory = false, index = 1, isEncrypted = false,
            crc = CRC32().apply { update(target) }.value
        )
        val archive = PreviewContent.Archive(listOf(entry), "ZIP", zip.path, "file")
        val output = ByteArrayOutputStream()
        ZipEntryReader.copy(archive, entry, null, output) { true }
        assertArrayEquals(target, output.toByteArray())
        assertEquals("selected.txt", ZipEntryReader.suggestedName(name))
        assertEquals("archive-entry", ZipEntryReader.suggestedName("..\\.."))
        assertTrue(runCatching {
            ZipEntryReader.copy(archive, entry.copy(index = 0), null, ByteArrayOutputStream()) { true }
        }.isFailure)
        assertTrue(runCatching {
            ZipEntryReader.copy(archive, entry, null, ByteArrayOutputStream()) { false }
        }.isFailure)
    }
}

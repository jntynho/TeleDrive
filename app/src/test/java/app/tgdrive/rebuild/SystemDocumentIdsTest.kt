package app.tgdrive.rebuild

import com.infinity.drive.core.files.DocumentId
import com.infinity.drive.core.files.Kind
import com.infinity.drive.core.files.parseDocumentId
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemDocumentIdsTest {
    @Test fun `folders and files retain the drive and literal key`() {
        for (id in listOf(
            DocumentId(-10077, Kind.ROOT),
            DocumentId(-10077, Kind.FOLDER, "folder/A"),
            DocumentId(-10055, Kind.FILE, "ملاحظات-1")
        )) {
            assertEquals(id, parseDocumentId(id.encode()))
        }
    }

    @Test fun `malformed and ambiguous identifiers are rejected`() {
        for (malformed in listOf("-100/FILE/", "-100/ROOT/Zg", "-100/FILE/Zg==", "drive/FOLDER/Zg", "-100/FILE")) {
            assertThrows(FileNotFoundException::class.java) { parseDocumentId(malformed) }
        }
    }
}

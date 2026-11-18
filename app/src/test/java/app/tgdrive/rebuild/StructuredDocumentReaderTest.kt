package app.tgdrive.rebuild

import com.infinity.drive.presentation.preview.StructuredDocumentReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StructuredDocumentReaderTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun csvRetainsQuotedSeparatorsAndLineBreaks() {
        val file = directory.newFile("data.csv").apply { writeText("Name;Notes\r\nX;\"a;b\nline\"\r\n") }
        val rows = StructuredDocumentReader.read(file, file.name, "text/csv").sections.single().rows
        assertEquals(listOf(listOf("Name", "Notes"), listOf("X", "a;b\nline")), rows)
    }

    @Test fun wordReadsParagraphsAndRejectsDocumentTypeDeclarations() {
        val file = directory.newFile("note.docx")
        fun write(xml: String) = ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("word/document.xml"))
            it.write(xml.encodeToByteArray())
            it.closeEntry()
        }
        write("<w:document xmlns:w='urn:words'><w:p><w:r><w:t>Hello</w:t></w:r></w:p></w:document>")
        assertEquals(listOf(listOf("Hello")), StructuredDocumentReader.read(file, file.name, "").sections.single().rows)
        write("<!DOCTYPE document [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><document>&secret;</document>")
        assertTrue(runCatching { StructuredDocumentReader.read(file, file.name, "") }.isFailure)
    }

    @Test fun workbookUsesSheetNamesAndKeepsEmptyColumnsAligned() {
        val file = directory.newFile("cells.xlsx")
        val entries = mapOf(
            "xl/workbook.xml" to "<workbook xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheet name='Budget' r:id='r1'/></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships><Relationship Id='r1' Target='worksheets/sheet1.xml'/></Relationships>",
            "xl/sharedStrings.xml" to "<sst><si><t>Cost</t></si></sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet><row><c r='A1' t='s'><v>0</v></c><c r='C1'><v>24</v></c></row></worksheet>"
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((path, text) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
        val preview = StructuredDocumentReader.read(file, file.name, "").sections.single()
        assertEquals("Budget", preview.name)
        assertEquals(listOf(listOf("Cost", "", "24")), preview.rows)
    }
}

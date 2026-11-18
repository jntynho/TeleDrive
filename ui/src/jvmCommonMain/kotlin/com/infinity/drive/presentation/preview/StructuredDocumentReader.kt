package com.infinity.drive.presentation.preview

import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

object StructuredDocumentReader {
    data class Section(val name: String, val rows: List<List<String>>)
    data class Document(val sections: List<Section>, val truncated: Boolean)

    private enum class Kind { CSV, WORD, WORKBOOK, SLIDES }

    fun supports(name: String, mimeType: String): Boolean = kind(name, mimeType) != null

    fun read(file: File, name: String, mimeType: String): Document {
        val kind = requireNotNull(kind(name, mimeType))
        val excerpt = Excerpt()
        val sections = if (kind == Kind.CSV) {
            val text = file.inputStream().use { input -> excerpt.read(input, MAX_TEXT_BYTES) }
            listOf(Section("", excerpt.csv(text, if (name.endsWith(".tsv", true)) '\t' else null)))
        } else ZipFile(file).use { zip ->
            when (kind) {
                Kind.WORD -> listOf(Section("", excerpt.paragraphs(zip, "word/document.xml")))
                Kind.SLIDES -> {
                    val slides = zip.entries().asSequence().filter { SLIDE.matches(it.name) }
                        .sortedBy { it.name.substringAfterLast("slide").substringBefore('.').toIntOrNull() }
                        .take(MAX_SECTIONS + 1).toList()
                    if (slides.size > MAX_SECTIONS) excerpt.truncated = true
                    slides.take(MAX_SECTIONS).mapIndexed { i, slide ->
                        Section((i + 1).toString(), excerpt.paragraphs(zip, slide.name))
                    }
                }
                Kind.WORKBOOK -> excerpt.workbook(zip)
                Kind.CSV -> error("CSV is not a ZIP")
            }
        }
        return Document(sections, excerpt.truncated)
    }

    private fun kind(name: String, mime: String): Kind? = when {
        name.endsWith(".csv", true) || name.endsWith(".tsv", true) ||
            mime in setOf("text/csv", "text/tab-separated-values", "application/csv") -> Kind.CSV
        name.endsWith(".docx", true) || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Kind.WORD
        name.endsWith(".xlsx", true) || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> Kind.WORKBOOK
        name.endsWith(".pptx", true) || mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> Kind.SLIDES
        else -> null
    }

    private class Excerpt {
        var truncated = false
        private var xmlBudget = 6 * 1024 * 1024

        fun read(input: InputStream, maximum: Int, failOnLimit: Boolean = false): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var remaining = maximum + 1
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(remaining, buffer.size))
                if (count < 0) break
                if (count == 0) throw IOException("No input")
                output.write(buffer, 0, count)
                remaining -= count
            }
            val bytes = output.toByteArray()
            if (bytes.size > maximum) {
                truncated = true
                if (failOnLimit) throw IOException("The document preview XML is too large")
            }
            val text = bytes.decodeToString(endIndex = minOf(maximum, bytes.size), throwOnInvalidSequence = failOnLimit)
            return text.removePrefix("\uFEFF")
        }

        fun csv(text: String, suggested: Char?): List<List<String>> {
            val separator = suggested ?: inferSeparator(text)
            val rows = mutableListOf<List<String>>()
            val row = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var index = 0
            fun endCell() {
                if (row.size < MAX_COLUMNS) row.add(cell.toString()) else truncated = true
                cell.setLength(0)
            }
            fun endRow() {
                endCell()
                rows.add(row.toList())
                row.clear()
            }
            while (index < text.length && rows.size < MAX_ROWS) {
                val ch = text[index++]
                when {
                    ch == '"' && quoted && text.getOrNull(index) == '"' -> {
                        cell.append('"')
                        index++
                    }
                    ch == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                    ch == separator && !quoted -> endCell()
                    (ch == '\r' || ch == '\n') && !quoted -> {
                        if (ch == '\r' && text.getOrNull(index) == '\n') index++
                        endRow()
                    }
                    cell.length < MAX_CELL_CHARS -> cell.append(ch)
                    else -> truncated = true
                }
            }
            if (index < text.length) truncated = true
            if (rows.size < MAX_ROWS && (row.isNotEmpty() || cell.isNotEmpty())) endRow()
            return rows
        }

        fun paragraphs(zip: ZipFile, path: String): List<List<String>> {
            val result = mutableListOf<List<String>>()
            val paragraph = StringBuilder()
            var withinText = false
            parse(zip, path) { parser ->
                parser.contentHandler = object : DefaultHandler() {
                    override fun startElement(uri: String?, local: String, qName: String?, attrs: Attributes?) {
                        if (local == "t") withinText = true
                        if (local == "tab" || local == "br") paragraph.append(if (local == "tab") '\t' else '\n')
                    }
                    override fun characters(chars: CharArray, start: Int, length: Int) {
                        if (withinText && result.size < MAX_ROWS) appendLimited(paragraph, chars, start, length)
                    }
                    override fun endElement(uri: String?, local: String, qName: String?) {
                        if (local == "t") withinText = false
                        if (local == "p") {
                            if (result.size == MAX_ROWS) truncated = true
                            else if (paragraph.isNotBlank()) result.add(listOf(paragraph.toString()))
                            paragraph.setLength(0)
                        }
                    }
                }
            }
            return result
        }

        fun workbook(zip: ZipFile): List<Section> {
            val sharedText = mutableListOf<String>()
            if (zip.getEntry("xl/sharedStrings.xml") != null) {
                val item = StringBuilder()
                var reading = false
                parse(zip, "xl/sharedStrings.xml") { parser ->
                    parser.contentHandler = object : DefaultHandler() {
                        override fun startElement(uri: String?, local: String, qName: String?, attrs: Attributes?) {
                            if (local == "t") reading = true
                        }
                        override fun characters(chars: CharArray, start: Int, length: Int) {
                            if (reading) appendLimited(item, chars, start, length)
                        }
                        override fun endElement(uri: String?, local: String, qName: String?) {
                            if (local == "t") reading = false
                            if (local == "si") {
                                sharedText.add(item.toString())
                                item.setLength(0)
                            }
                        }
                    }
                }
            }
            val names = mutableListOf<Pair<String, String>>()
            parse(zip, "xl/workbook.xml") { parser ->
                parser.contentHandler = object : DefaultHandler() {
                    override fun startElement(uri: String?, local: String, qName: String?, attrs: Attributes) {
                        if (local == "sheet") {
                            if (names.size >= MAX_SECTIONS) truncated = true
                            else names.add(attrs.getValue("name").orEmpty() to
                                (attrs.getValue("r:id") ?: attrs.getValue(RELATION_NS, "id")).orEmpty())
                        }
                    }
                }
            }
            val relationships = mutableMapOf<String, String>()
            parse(zip, "xl/_rels/workbook.xml.rels") { parser ->
                parser.contentHandler = object : DefaultHandler() {
                    override fun startElement(uri: String?, local: String, qName: String?, attrs: Attributes) {
                        if (local != "Relationship" || attrs.getValue("TargetMode") == "External") return
                        val target = attrs.getValue("Target") ?: return
                        val path = if (target.startsWith('/')) target.drop(1) else "xl/$target"
                        if (SHEET.matches(path)) relationships[attrs.getValue("Id").orEmpty()] = path
                    }
                }
            }
            return names.mapNotNull { (name, id) ->
                relationships[id]?.let { Section(name, cells(zip, it, sharedText)) }
            }
        }

        private fun cells(zip: ZipFile, path: String, values: List<String>): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            val row = mutableListOf<String>()
            val value = StringBuilder()
            var type: String? = null
            var reading = false
            var column = 0
            parse(zip, path) { parser ->
                parser.contentHandler = object : DefaultHandler() {
                    override fun startElement(uri: String?, local: String, qName: String?, attrs: Attributes) {
                        if (local == "c") {
                            column = columnIndex(attrs.getValue("r"), row.size)
                            type = attrs.getValue("t")
                            value.setLength(0)
                        }
                        if (local == "v" || local == "t" && type == "inlineStr") reading = true
                    }
                    override fun characters(chars: CharArray, start: Int, length: Int) {
                        if (reading && rows.size < MAX_ROWS && column < MAX_COLUMNS) appendLimited(value, chars, start, length)
                    }
                    override fun endElement(uri: String?, local: String, qName: String?) {
                        if (local == "v" || local == "t") reading = false
                        if (local == "c" && rows.size < MAX_ROWS) {
                            if (column in 0 until MAX_COLUMNS) {
                                while (row.size <= column) row.add("")
                                val raw = value.toString()
                                row[column] = if (type == "s") raw.toIntOrNull()?.let(values::getOrNull).orEmpty()
                                else raw
                            } else truncated = true
                        }
                        if (local == "row") {
                            if (rows.size >= MAX_ROWS) truncated = true
                            else rows.add(row.toList())
                            row.clear()
                        }
                    }
                }
            }
            return rows
        }

        private fun appendLimited(target: StringBuilder, chars: CharArray, start: Int, length: Int) {
            val toCopy = minOf(length, (MAX_CELL_CHARS - target.length).coerceAtLeast(0))
            target.append(chars, start, toCopy)
            if (toCopy < length) truncated = true
        }

        private fun parse(zip: ZipFile, path: String, configure: (XMLReader) -> Unit) {
            val entry = zip.getEntry(path) ?: throw IOException("Missing document section")
            val allowed = minOf(MAX_XML_BYTES, xmlBudget)
            val content = zip.getInputStream(entry).use { read(it, allowed, failOnLimit = true) }
            xmlBudget -= content.encodeToByteArray().size
            require(!content.contains("<!DOCTYPE", ignoreCase = true) &&
                !content.contains("<!ENTITY", ignoreCase = true) && '\u0000' !in content)
            val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
            parser.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
            configure(parser)
            parser.parse(InputSource(StringReader(content)))
        }
    }

    private fun columnIndex(reference: String?, fallback: Int): Int {
        val letters = reference?.takeWhile { it in 'A'..'Z' }.orEmpty()
        if (letters.isEmpty()) return fallback
        if (letters.length > 3) return MAX_COLUMNS
        return letters.fold(0) { value, ch -> value * 26 + (ch - 'A') + 1 } - 1
    }

    private fun inferSeparator(text: String): Char {
        val candidates = listOf(',', ';', '\t')
        val counts = candidates.associateWith { 0 }.toMutableMap()
        var inQuotes = false
        for (ch in text) {
            if (ch == '"') inQuotes = !inQuotes
            if (!inQuotes) {
                if (ch == '\n' || ch == '\r') break
                if (ch in counts) counts[ch] = counts.getValue(ch) + 1
            }
        }
        return candidates.maxBy { counts.getValue(it) }
    }

    private val SHEET = Regex("xl/worksheets/sheet[0-9]+\\.xml")
    private val SLIDE = Regex("ppt/slides/slide[0-9]+\\.xml")
    private const val RELATION_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val MAX_XML_BYTES = 2 * 1024 * 1024
    private const val MAX_TEXT_BYTES = 512 * 1024
    private const val MAX_CELL_CHARS = 4096
    private const val MAX_ROWS = 300
    private const val MAX_COLUMNS = 30
    private const val MAX_SECTIONS = 20
}

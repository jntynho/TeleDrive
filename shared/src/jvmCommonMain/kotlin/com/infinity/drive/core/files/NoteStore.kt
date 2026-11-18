package com.infinity.drive.core.files

import java.io.File

/** Local copies of notes, kept beside the app's other staged files. */
class NoteStore(
    private val storagePaths: AppStoragePaths
) {

    fun write(name: String, body: String, existingPath: String?): File {
        val directory = File(storagePaths.filesDir, NOTE_DIR).apply { mkdirs() }
        val target = existingPath?.let(::File)
            ?.takeIf { it.parentFile?.parentFile == directory }
            ?: File(File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }, name)
        val temporary = File.createTempFile("note-", ".tmp", target.parentFile)
        try {
            temporary.writeText(body)
            java.nio.file.Files.move(
                temporary.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            return target
        } finally {
            temporary.delete()
        }
    }

    fun read(path: String): String? = File(path).takeIf { it.isFile }?.readText()

    fun fileName(title: String): String {
        val safe = FileNameUtils.sanitize(title.trim().ifEmpty { UNTITLED })
        return if (safe.endsWith(EXTENSION, ignoreCase = true)) safe else "$safe$EXTENSION"
    }

    private companion object {
        const val NOTE_DIR = "notes"
        const val EXTENSION = ".md"
        const val UNTITLED = "Note"
    }
}

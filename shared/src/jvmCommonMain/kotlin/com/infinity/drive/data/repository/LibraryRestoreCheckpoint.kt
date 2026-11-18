package com.infinity.drive.data.repository

import com.infinity.drive.domain.repository.SyncRepository.RestoreProgress
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal data class LibraryRestoreCheckpoint(
    val cursor: Long = 0,
    val progress: RestoreProgress = RestoreProgress()
) {
    fun write(file: File) {
        val temporary = Files.createTempFile(file.toPath().toAbsolutePath().parent, "restore-", ".tmp")
        try {
            temporary.toFile().writeText(
                listOf(cursor, progress.scanned, progress.restored, progress.locked).joinToString("\n")
            )
            Files.move(temporary, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        fun read(file: File): LibraryRestoreCheckpoint {
            if (!file.exists()) return LibraryRestoreCheckpoint()
            val lines = file.readLines()
            if (lines.size != 4) return LibraryRestoreCheckpoint()
            val cursor = lines[0].toLongOrNull()?.takeIf { it > 0 } ?: return LibraryRestoreCheckpoint()
            val scanned = lines[1].toIntOrNull()?.takeIf { it >= 0 } ?: return LibraryRestoreCheckpoint()
            val restored = lines[2].toIntOrNull()?.takeIf { it in 0..scanned } ?: return LibraryRestoreCheckpoint()
            val locked = lines[3].toIntOrNull()?.takeIf { it in 0..scanned } ?: return LibraryRestoreCheckpoint()
            return LibraryRestoreCheckpoint(cursor, RestoreProgress(scanned, restored, locked))
        }
    }
}

internal fun isAdvancingHistory(cursor: Long, next: Long): Boolean =
    next == 0L || next > 0 && (cursor == 0L || next < cursor)

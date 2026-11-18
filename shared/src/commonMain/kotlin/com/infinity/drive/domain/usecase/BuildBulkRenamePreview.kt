package com.infinity.drive.domain.usecase

import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.domain.model.BulkRenameEntry
import com.infinity.drive.domain.model.BulkRenamePreview
import com.infinity.drive.domain.model.BulkRenameStatus

internal object BuildBulkRenamePreview {
    data class Source(val id: String, val folderId: String?, val name: String)

    private val placeholder = Regex("\\{name}|\\{number}")

    fun create(template: String, sources: List<Source>, occupied: Map<String?, List<Source>>): BulkRenamePreview {
        val destinations = sources.mapIndexed { index, source ->
            val finalDot = source.name.lastIndexOf('.')
            val extension = if (finalDot > 0) source.name.substring(finalDot) else ""
            val base = source.name.removeSuffix(extension)
            val newBase = placeholder.replace(template.trim()) { match ->
                if (match.value == "{name}") base else (index + 1).toString()
            }.trim()
            if (newBase.isBlank() || newBase == "." || newBase == "..") "" else newBase + extension
        }
        val duplicates = sources.zip(destinations)
            .groupingBy { (source, name) -> source.folderId to name.lowercase() }
            .eachCount()
        val rows = sources.zip(destinations).map { (source, name) ->
            val status = when {
                !FileNameUtils.isValidName(name) -> BulkRenameStatus.INVALID
                name == source.name -> BulkRenameStatus.UNCHANGED
                (duplicates[source.folderId to name.lowercase()] ?: 0) > 1 ||
                    occupied[source.folderId].orEmpty().any { existing ->
                        existing.id != source.id && existing.name.equals(name, ignoreCase = true)
                    } -> BulkRenameStatus.CONFLICT
                else -> BulkRenameStatus.READY
            }
            BulkRenameEntry(source.id, source.name, name, status)
        }
        return BulkRenamePreview(template, rows)
    }
}

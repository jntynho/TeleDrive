// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinity.drive.core.files.MimeTypes
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinity.drive.domain.model.FileCategory

@Composable
fun iconFor(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGE -> DriveIcons.Image
    FileCategory.VIDEO -> DriveIcons.VideoFile
    FileCategory.AUDIO -> DriveIcons.AudioFile
    FileCategory.DOCUMENT -> DriveIcons.Description
    FileCategory.ARCHIVE -> DriveIcons.Archive
    FileCategory.OTHER -> DriveIcons.InsertDriveFile
}


/** Uses the filename first because imported files can have a generic MIME type. */
@Composable
fun fileTypeIcon(name: String, mime: String, category: FileCategory = FileCategory.fromMimeType(mime)): ImageVector =
    when (fileExtension(name)) {
        "pdf" -> DriveIcons.PictureAsPdf
        "apk", "apks", "xapk", "aab" -> DriveIcons.PhoneAndroid
        "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz" -> DriveIcons.Archive
        "csv", "xls", "xlsx", "ods", "tsv" -> DriveIcons.GridView
        "ppt", "pptx", "odp" -> DriveIcons.PhotoLibrary
        "txt", "doc", "docx", "odt", "rtf", "md", "epub" -> DriveIcons.Description
        "json", "xml", "html", "css", "js", "kt", "py", "sh", "sql", "yaml", "yml" -> DriveIcons.Code
        else -> when {
            MimeTypes.isApk(mime, name) -> DriveIcons.PhoneAndroid
            mime == "application/pdf" -> DriveIcons.PictureAsPdf
            else -> iconFor(FileCategory.fromMimeType(MimeTypes.fromFileName(name)).takeUnless { it == FileCategory.OTHER } ?: category)
        }
    }

@Composable
fun FileTypeFallback(name: String, mime: String, category: FileCategory, iconSize: Dp, description: String? = null) {
    val format = fileExtension(name).takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.semantics { if (description != null) contentDescription = description }
    ) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(fileTypeIcon(name, mime, category), null, Modifier.size(iconSize), tint = tint)
        if (category !in setOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO) && format != null) {
            Text(format.uppercase(), color = tint, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1)
        }
    }
}

private fun fileExtension(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(dot + 1).lowercase() else ""
}

package com.infinity.drive.presentation.gallery

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.repository.DriveContentReader
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.FileQuerySpec
import com.infinity.drive.domain.model.FileSortField
import com.infinity.drive.domain.model.SortDirection
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal data class PhotoPlace(val latitude: Double, val longitude: Double)
internal data class DatedPhoto(val file: DriveFile, val capturedAt: Long?, val place: PhotoPlace?) {
    val date: Long get() = capturedAt ?: file.createdAt
}
internal data class PhotoHistory(val photos: List<DatedPhoto>, val hasMore: Boolean)

internal class AndroidPhotoHistory(
    private val context: Context,
    private val repository: FileRepository,
    private val files: FileDao,
    private val reader: DriveContentReader,
    private val access: HiddenContentAccess,
    private val settings: SettingsRepository
) {
    fun observeGallery(onAlbum: Boolean, folder: String?, query: String) = repository.observeFiles(FileQuerySpec(
        folderId = folder.takeIf { onAlbum },
        filterByFolder = onAlbum,
        nameQuery = query.trim().takeIf { it.isNotEmpty() },
        categories = listOf(FileCategory.IMAGE),
        excludeHiddenAncestors = true,
        sortField = FileSortField.DATE_MODIFIED,
        sortDirection = SortDirection.DESCENDING
    ))

    suspend fun scan(input: List<DriveFile>, limit: Int, inspectBackups: Boolean): PhotoHistory =
        withContext(Dispatchers.IO) {
            val drive = settings.preferences.first().storageChatId
                ?: return@withContext PhotoHistory(emptyList(), false)
            var budget = if (inspectBackups) MAX_REMOTE_BYTES else 0
            val result = mutableListOf<DatedPhoto>()
            val photos = input.filter { it.chatId == drive && !it.isTrashed && !it.isHidden }
            suspend fun allowed(file: DriveFile): Boolean = settings.preferences.first().storageChatId == drive &&
                access.canRead(file.id) && !access.protectedFile(file)
            for (file in photos.take(limit)) {
                ensureActive()
                if (!allowed(file)) continue
                val remote = inspectBackups && file.backupState == BackupState.BACKED_UP &&
                    file.sizeBytes in 1..MAX_PER_FILE && file.sizeBytes <= budget
                val found = try {
                    val local = file.localPath?.let(::openLocal)
                    if (local != null) {
                        local.use { metadata(file, ExifInterface(it)) }
                    } else if (remote) {
                        budget -= file.sizeBytes
                        withTimeoutOrNull(12_000) { remoteExif(file) { check(allowed(file)) } }
                    } else null
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    null
                }
                if (allowed(file)) result += found ?: DatedPhoto(file, null, null)
            }
            PhotoHistory(result.sortedByDescending(DatedPhoto::date), photos.size > limit)
        }

    suspend fun outlines(): List<FloatArray> = withContext(Dispatchers.IO) {
        val json = context.assets.open("teledrive/natural-earth-land.json").bufferedReader().use { it.readText() }
        val polygons = JSONArray(json)
        List(polygons.length()) { index ->
            val points = polygons.getJSONArray(index)
            FloatArray(points.length()) { point -> points.getDouble(point).toFloat() }
        }
    }

    private suspend fun remoteExif(file: DriveFile, verify: suspend () -> Unit): DatedPhoto? {
        val row = files.byId(file.id) ?: return null
        if (row.chatId != file.chatId || row.modifiedAt != file.modifiedAt || row.trashedAt != null) return null
        val spool = File.createTempFile("gallery-exif-", ".tmp", context.cacheDir)
        try {
            return ParcelFileDescriptor.open(spool, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
                check(spool.delete())
                ParcelFileDescriptor.AutoCloseOutputStream(descriptor.dup()).buffered().use { output ->
                    reader.copyRemote(row, output, verify)
                }
                currentCoroutineContext().ensureActive()
                Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
                metadata(file, ExifInterface(descriptor.fileDescriptor))
            }
        } finally {
            spool.delete()
        }
    }

    private fun openLocal(path: String): InputStream? = try {
        if (path.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(path))
        else File(path).takeIf(File::isFile)?.inputStream()
    } catch (_: java.io.IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun metadata(file: DriveFile, exif: ExifInterface): DatedPhoto = DatedPhoto(
        file,
        exifCaptureTime(
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
        ),
        exif.latLong?.takeIf { it.size >= 2 && it[0] in -90.0..90.0 && it[1] in -180.0..180.0 }
            ?.let { PhotoPlace(it[0], it[1]) }
    )

    private companion object {
        const val MAX_PER_FILE = 8L * 1024 * 1024
        const val MAX_REMOTE_BYTES = 48L * 1024 * 1024
    }
}

private val originalExifTime = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", java.util.Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

internal fun exifCaptureTime(value: String?, offset: String?, localZone: ZoneId = ZoneId.systemDefault()): Long? =
    value?.let { raw ->
        runCatching {
            val time = LocalDateTime.parse(raw.trim(), originalExifTime)
            val zone = offset?.trim()?.takeIf(String::isNotEmpty)?.let(ZoneOffset::of) ?: localZone
            time.atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

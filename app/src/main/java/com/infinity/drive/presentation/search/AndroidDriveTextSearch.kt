package com.infinity.drive.presentation.search

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.repository.DriveContentReader
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer

/** Searches on demand without maintaining a plaintext full-text index. */
internal class AndroidDriveTextSearch(
    private val context: Context,
    private val files: FileDao,
    private val reader: DriveContentReader,
    private val settings: SettingsRepository,
    private val access: HiddenContentAccess,
    private val appLock: AppLockManager
) : DriveTextSearch {
    override val supportsPdf: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    override suspend fun search(query: String, candidates: List<DriveFile>): ContentMatches = withContext(Dispatchers.IO) {
        val drive = settings.preferences.first().takeIf { it.onboardingComplete }?.storageChatId
            ?: return@withContext ContentMatches(emptyList(), 0)
        val text = normalizedSearchText(query)
        if (text.isBlank()) return@withContext ContentMatches(emptyList(), 0)

        val eligible = candidates.filter { it.chatId == drive && (it.name.endsWith(".md", true) || isPdf(it)) }
        val hits = mutableListOf<DriveFile>()
        var skipped = (eligible.size - MAX_CANDIDATES).coerceAtLeast(0)
        var remainingRemote = MAX_REMOTE_BYTES
        suspend fun stillOpen(): Boolean {
            val current = settings.preferences.first()
            return current.onboardingComplete && current.storageChatId == drive && !appLock.locked.value
        }

        for (file in eligible.take(MAX_CANDIDATES)) {
            ensureActive()
            if (!stillOpen()) return@withContext ContentMatches(emptyList(), 0)
            if (!access.canRead(file.id)) continue
            val pdf = isPdf(file)
            val maxSize = if (pdf) MAX_PDF_BYTES else MAX_NOTE_BYTES
            if (file.sizeBytes !in 0..maxSize || pdf && !supportsPdf) {
                skipped++
                continue
            }
            try {
                val entity = files.byId(file.id)
                if (entity?.chatId != drive || entity.trashedAt != null || entity.modifiedAt != file.modifiedAt) {
                    skipped++
                    continue
                }
                suspend fun copyTo(output: OutputStream) {
                    val local = file.localPath?.let(::openLocal)
                    if (local != null) {
                        val coroutine = currentCoroutineContext()
                        local.use { stream ->
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                coroutine.ensureActive()
                                val read = stream.read(buffer)
                                if (read <= 0) break
                                total += read
                                check(total <= maxSize)
                                output.write(buffer, 0, read)
                            }
                        }
                        return
                    }
                    check(remainingRemote >= file.sizeBytes)
                    remainingRemote -= file.sizeBytes
                    reader.copyRemote(entity, output) {
                        check(stillOpen() && access.canRead(file.id))
                    }
                }
                val matched = if (pdf && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    val staging = File.createTempFile("content-search-", ".pdf", context.cacheDir)
                    try {
                        ParcelFileDescriptor.open(staging, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
                            // Only the descriptors can reach the file while it contains decrypted bytes.
                            check(staging.delete())
                            ParcelFileDescriptor.AutoCloseOutputStream(descriptor.dup()).buffered().use { copyTo(it) }
                            matchesPdf(descriptor, query)
                        }
                    } finally {
                        staging.delete()
                    }
                } else {
                    val output = ByteArrayOutputStream()
                    copyTo(output)
                    normalizedSearchText(output.toByteArray().decodeToString()).contains(text)
                }
                if (matched && stillOpen() && access.canRead(file.id)) hits += file
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                skipped++
            }
        }
        if (!stillOpen()) return@withContext ContentMatches(emptyList(), 0)
        ContentMatches(hits.filter { access.canRead(it.id) }, skipped)
    }

    private fun openLocal(path: String): InputStream? = try {
        if (path.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(path))
        else File(path).takeIf(File::isFile)?.inputStream()
    } catch (_: java.io.IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun matchesPdf(descriptor: ParcelFileDescriptor, query: String): Boolean =
        PdfRenderer(descriptor).use renderer@ { pdf ->
            for (index in 0 until minOf(pdf.pageCount, MAX_PDF_PAGES)) {
                currentCoroutineContext().ensureActive()
                pdf.openPage(index).use { page ->
                    if (page.searchText(query).isNotEmpty()) return@renderer true
                }
            }
            check(pdf.pageCount <= MAX_PDF_PAGES)
            false
        }

    private fun isPdf(file: DriveFile): Boolean =
        file.mimeType == "application/pdf" || file.name.endsWith(".pdf", ignoreCase = true)

    private companion object {
        const val MAX_CANDIDATES = 200
        const val MAX_NOTE_BYTES = 512L * 1024
        const val MAX_PDF_BYTES = 16L * 1024 * 1024
        const val MAX_REMOTE_BYTES = 64L * 1024 * 1024
        const val MAX_PDF_PAGES = 200
    }
}

private val diacritics = Regex("\\p{M}+")
private val whitespace = Regex("[\\s\\p{Z}]+")

internal fun normalizedSearchText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(diacritics, "").replace(whitespace, " ").trim().lowercase()

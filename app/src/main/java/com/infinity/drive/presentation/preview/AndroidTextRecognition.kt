package com.infinity.drive.presentation.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.infinity.drive.core.files.MimeTypes
import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.repository.DriveContentReader
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

internal data class RecognizedText(val text: String, val truncated: Boolean)

internal class AndroidTextRecognition(
    private val context: Context,
    private val files: FileDao,
    private val reader: DriveContentReader,
    private val access: HiddenContentAccess,
    private val lock: AppLockManager,
    private val settings: SettingsRepository
) {
    suspend fun extract(file: DriveFile): RecognizedText = withContext(Dispatchers.IO) {
        require(file.sizeBytes in 1..MAX_SOURCE_BYTES)
        val drive = requireNotNull(file.chatId)
        suspend fun verify() {
            ensureActive()
            val prefs = settings.preferences.first()
            check(prefs.onboardingComplete && prefs.storageChatId == drive && !lock.locked.value)
            check(access.canRead(file.id) && !access.protectedFile(file))
        }
        verify()
        val row = requireNotNull(files.byId(file.id))
        check(row.chatId == drive && row.trashedAt == null && row.modifiedAt == file.modifiedAt && !row.isHidden &&
            !access.protectedFolder(row.folderId))
        val staging = File.createTempFile("text-recognition-", ".tmp", context.cacheDir)
        try {
            ParcelFileDescriptor.open(staging, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
                check(staging.delete())
                ParcelFileDescriptor.AutoCloseOutputStream(descriptor.dup()).buffered().use { output ->
                    val local = row.localPath?.let(::openLocal)
                    if (local != null) {
                        val buffer = ByteArray(128 * 1024)
                        var size = 0L
                        local.use { input ->
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                check(size <= MAX_SOURCE_BYTES)
                                output.write(buffer, 0, count)
                            }
                        }
                        check(size == file.sizeBytes)
                    } else {
                        check(row.backupState == BackupState.BACKED_UP)
                        reader.copyRemote(row, output, ::verify)
                    }
                }
                verify()
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { recognizer ->
                    val text = if (MimeTypes.isPdf(file.mimeType)) {
                        recognizePdf(descriptor, recognizer, ::verify)
                    } else {
                        require(MimeTypes.isImage(file.mimeType))
                        val image = recognizeImage(descriptor, recognizer)
                        RecognizedText(image.take(MAX_TEXT), image.length > MAX_TEXT)
                    }
                    verify()
                    text
                }
            }
        } finally {
            staging.delete()
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

    private suspend fun recognizeImage(descriptor: ParcelFileDescriptor, recognizer: TextRecognizer): String {
        val fd = descriptor.fileDescriptor
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        val rotation = runCatching { ExifInterface(fd).rotationDegrees }.getOrDefault(0)
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(fd, null, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION) sample *= 2
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        val bitmap = requireNotNull(BitmapFactory.decodeFileDescriptor(fd, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }))
        return recognize(bitmap, rotation, recognizer)
    }

    private suspend fun recognizePdf(
        descriptor: ParcelFileDescriptor,
        recognizer: TextRecognizer,
        verify: suspend () -> Unit
    ): RecognizedText = PdfRenderer(descriptor).use { document ->
        val text = StringBuilder()
        var truncated = document.pageCount > MAX_PAGES
        for (index in 0 until minOf(document.pageCount, MAX_PAGES)) {
            verify()
            val bitmap = document.openPage(index).use { page ->
                val scale = MAX_DIMENSION.toFloat() / maxOf(page.width, page.height)
                val image = Bitmap.createBitmap(
                    (page.width * scale).roundToInt().coerceAtLeast(1),
                    (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                )
                try {
                    image.eraseColor(Color.WHITE)
                    page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    image
                } catch (failure: Throwable) {
                    image.recycle()
                    throw failure
                }
            }
            val result = recognize(bitmap, 0, recognizer).trim()
            if (result.isEmpty()) continue
            if (text.isNotEmpty()) text.append("\n\n")
            val available = (MAX_TEXT - text.length).coerceAtLeast(0)
            text.append(result.take(available))
            if (result.length > available || text.length >= MAX_TEXT) {
                truncated = true
                break
            }
        }
        RecognizedText(text.toString().take(MAX_TEXT), truncated)
    }

    private suspend fun recognize(bitmap: Bitmap, rotation: Int, recognizer: TextRecognizer): String = try {
        val image = InputImage.fromBitmap(bitmap, rotation)
        // The native recognizer continues using the bitmap until its task completes.
        withContext(NonCancellable) { recognizer.process(image).awaitResult().text }
            .also { currentCoroutineContext().ensureActive() }
    } finally {
        bitmap.recycle()
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { result ->
            when {
                result.isSuccessful -> continuation.resume(result.result)
                result.isCanceled -> continuation.cancel()
                else -> continuation.resumeWithException(result.exception ?: IllegalStateException("Text extraction failed"))
            }
        }
    }

    private companion object {
        const val MAX_DIMENSION = 2400
        const val MAX_PAGES = 24
        const val MAX_TEXT = 32_000
        const val MAX_SOURCE_BYTES = 48L * 1024 * 1024
    }
}

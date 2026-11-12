// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.files.MimeTypes
import com.infinity.drive.core.files.Urls
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramDownloadEvent
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.ThumbnailDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.ThumbnailEntity
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Generates and caches thumbnails. Cache files are AES-GCM sealed when
 * thumbnail encryption is enabled, so private media never sits readable in
 * the cache directory. Falls back to the Telegram mini-thumbnail when no
 * local copy exists.
 */
class AndroidThumbnailStore(
    private val context: Context,
    private val storagePaths: com.infinity.drive.core.files.AppStoragePaths,
    private val fileDao: FileDao,
    private val thumbnailDao: ThumbnailDao,
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val settingsRepository: SettingsRepository,
    private val hiddenAccess:com.infinity.drive.core.security.HiddenContentAccess
) : ThumbnailStore {

    private val generationSemaphore = Semaphore(3)
    private val remoteSemaphore = Semaphore(2)
    private val fileLocks=Array(64){Mutex()}
    private fun thumbnailName(file:FileEntity):String {
        val revision="${file.id}:${file.modifiedAt}:${file.localPath}:${file.remoteFileId}:${file.iconFileId}:${file.contentHash}"
        val key=java.security.MessageDigest.getInstance("SHA-256").digest(revision.toByteArray()).joinToString(""){"%02x".format(it)}
        return "$key.thumb"
    }

    override suspend fun cachedThumbnailBytes(fileId:String):ByteArray? {
        if(!hiddenAccess.canRead(fileId))return null
        try{
            val entity=fileDao.byId(fileId)?:return null
            if(!settingsRepository.preferences.first().linkPreviews&&MimeTypes.isText(entity.mimeType))return null
            val cached=thumbnailDao.byFileId(fileId)?:return null
            val target=File(cached.path)
            if(target.name!=thumbnailName(entity)||!target.isFile)return null
            val bytes=target.readBytes()
            return if(cached.encrypted)streamCrypto.decryptBytes(thumbnailKey(),bytes)else bytes
        }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(_:Exception){return null}
    }

    override suspend fun thumbnailBytes(fileId: String): ByteArray? {
        if(!hiddenAccess.canRead(fileId))return null
        return thumbnailForUpload(fileId)
    }

    /** Trusted background upload preparation does not grant UI thumbnail access. */
    private suspend fun thumbnailForUpload(fileId:String):ByteArray? {
        return fileLocks[(fileId.hashCode() and Int.MAX_VALUE)%fileLocks.size].withLock {
        val entity=fileDao.byId(fileId)?:return null
        if (!settingsRepository.preferences.first().linkPreviews && isNote(fileId)) {
            thumbnailDao.delete(fileId)
            return null
        }
        thumbnailDao.byFileId(fileId)?.let { cached ->
            val file = File(cached.path)
            if (file.exists() && file.name==thumbnailName(entity)) {
                thumbnailDao.touch(fileId, System.currentTimeMillis())
                val raw = file.readBytes()
                return if (cached.encrypted) {
                    runCatching {
                        streamCrypto.decryptBytes(thumbnailKey(), raw)
                    }.getOrNull()
                } else raw
            }
            file.delete()
            thumbnailDao.delete(fileId)
        }
        return generate(fileId)
        }
    }

    override suspend fun uploadThumbnailFile(fileId: String): File? {
        val bytes = thumbnailForUpload(fileId) ?: return null
        val dir = File(storagePaths.cacheDir, "upload-thumbs").apply { mkdirs() }
        val target = File(dir, "$fileId.jpg")
        return runCatching {
            val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: return null
            val ratio=minOf(1f,320f/maxOf(decoded.width,decoded.height))
            val scaled=if(ratio<1f)Bitmap.createScaledBitmap(decoded,(decoded.width*ratio).toInt().coerceAtLeast(1),(decoded.height*ratio).toInt().coerceAtLeast(1),true)else decoded
            try {
                target.outputStream().use { output -> check(scaled.compress(Bitmap.CompressFormat.JPEG,82,output)) }
                check(target.length()<=200_000) { "Telegram thumbnail is too large" }
                target
            } finally { if(scaled!==decoded)scaled.recycle();decoded.recycle() }
        }.getOrNull()
    }

    private suspend fun isNote(fileId: String): Boolean =
        fileDao.byId(fileId)?.let { MimeTypes.isText(it.mimeType) } == true

    private fun audioArtwork(file:File):Bitmap? = runCatching {
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val data=retriever.embeddedPicture?:return@runCatching null
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(data,0,data.size,bounds)
            val sample=generateSequence(1){it*2}.first{bounds.outWidth/it<=MAX_DIMENSION*2&&bounds.outHeight/it<=MAX_DIMENSION*2}
            BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply{inSampleSize=sample})
        } finally { retriever.release() }
    }.getOrNull()

    private suspend fun generate(fileId: String): ByteArray? {
        val entity = fileDao.byId(fileId) ?: return null
        val bitmap = generationSemaphore.withPermit { entity.localPath?.let { path ->
            val file = File(path)
            when {
                !file.exists() -> null
                entity.mimeType.startsWith("audio/")->audioArtwork(file)
                MimeTypes.isText(entity.mimeType) -> linkThumbnail(file)
                else -> decodeThumbnail(file, entity.mimeType)
            }
        }

        }
        val jpegBytes = if (bitmap != null) {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                bitmap.recycle()
                output.toByteArray()
            }
        } else {
            remoteSemaphore.withPermit {
                kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    val iconFileId = entity.iconFileId
                    if (iconFileId != null) fetchIconBytes(entity, iconFileId) ?: fetchRemoteThumbnail(entity)
                    else fetchRemoteThumbnail(entity)
                }
            }
        } ?: return null

        val encrypt = settingsRepository.preferences.first().encryptThumbnails
        val stored = if (encrypt) {
            streamCrypto.encryptBytes(thumbnailKey(), jpegBytes)
        } else jpegBytes

        val dir = File(storagePaths.cacheDir, "thumbnails").apply { mkdirs() }
        val target = File(dir, thumbnailName(entity))
        runCatching { target.writeBytes(stored) }.onFailure { return jpegBytes }

        val now = System.currentTimeMillis()
        thumbnailDao.upsert(
            ThumbnailEntity(
                fileId = fileId,
                path = target.absolutePath,
                encrypted = encrypt,
                width = MAX_DIMENSION,
                height = MAX_DIMENSION,
                sizeBytes = stored.size.toLong(),
                generatedAt = now,
                lastAccessAt = now
            )
        )
        return jpegBytes
    }

    /** A note holding just a link previews as that link's image. */
    private suspend fun linkThumbnail(file: File): Bitmap? {
        if (file.length() > LINK_NOTE_LIMIT) return null
        if (!settingsRepository.preferences.first().linkPreviews) return null
        val url = Urls.sole(file.readText()) ?: return null
        val imagePath = telegramClient
            .linkPreview(Urls.normalize(url), withImage = true)
            ?.imagePath
            ?: return null
        return File(imagePath).takeIf { it.exists() }?.let(::decodeImageThumbnail)
    }

    private fun decodeThumbnail(file: File, mimeType: String): Bitmap? = runCatching {
        when {
            MimeTypes.isImage(mimeType) -> decodeImageThumbnail(file)
            MimeTypes.isVideo(mimeType) -> decodeVideoThumbnail(file)
            MimeTypes.isApk(mimeType, file.name) -> decodeApkIcon(file)
            else -> null
        }
    }.getOrNull()

    private fun decodeApkIcon(file: File): Bitmap? {
        val bitmapFromPm = runCatching {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val appInfo = info?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                val drawable = appInfo.loadIcon(pm)
                if (drawable != null) {
                    return@runCatching drawableToBitmap(drawable)
                }
            }
            null
        }.getOrNull()

        if (bitmapFromPm != null) return bitmapFromPm

        val bytes = ApkIconExtractor.extractIconBytes(file) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else MAX_DIMENSION
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else MAX_DIMENSION
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun decodeImageThumbnail(file: File): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThumbnailUtils.createImageThumbnail(file, Size(MAX_DIMENSION, MAX_DIMENSION), null)
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val sample = maxOf(
                1,
                maxOf(options.outWidth, options.outHeight) / MAX_DIMENSION
            )
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }

    private fun decodeVideoThumbnail(file: File): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThumbnailUtils.createVideoThumbnail(file, Size(MAX_DIMENSION, MAX_DIMENSION), null)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.frameAtTime
            } finally {
                retriever.release()
            }
        }

    private suspend fun fetchIconBytes(entity: FileEntity, iconFileId: String): ByteArray? {
        var readyPath: String? = null
        val rawRemoteFileId = iconFileId.substringBefore(":")
        runCatching {
            telegramClient.downloadDocument(rawRemoteFileId).collect { event ->
                if (event is TelegramDownloadEvent.Completed) {
                    readyPath = event.localPath
                }
            }
        }
        val path = readyPath ?: return null
        val rawBytes = runCatching { File(path).readBytes() }.getOrNull() ?: return null
        return if (entity.isEncrypted) {
            val key = wrappedKeyRepository.get(CryptoKeys.CONTENT) ?: return null
            runCatching { streamCrypto.decryptBytes(key, rawBytes) }.getOrNull()
        } else {
            rawBytes
        }
    }

    private suspend fun fetchRemoteThumbnail(entity: FileEntity): ByteArray? {
        val chatId = entity.chatId ?: return null
        val messageId = entity.messageId ?: return null
        if (MimeTypes.isText(entity.mimeType) &&
            !settingsRepository.preferences.first().linkPreviews
        ) return null
        return try { telegramClient.fetchThumbnail(chatId, messageId) }
        catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(_:Exception){null}
    }

    private fun thumbnailKey(): ByteArray =
        wrappedKeyRepository.getOrCreate(CryptoKeys.THUMBNAIL)

    companion object {
        private const val LINK_NOTE_LIMIT = 8L * 1024
        private const val MAX_DIMENSION = 512
        private const val JPEG_QUALITY = 82
    }
}

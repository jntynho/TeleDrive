package com.infinity.drive.core.media

import java.io.File

/** Creates and caches preview thumbnails for stored files. */
interface ThumbnailStore {

    suspend fun thumbnailBytes(fileId: String): ByteArray?

    /** Reads an existing, valid preview only. Never generates, downloads or fetches a link preview. */
    suspend fun cachedThumbnailBytes(fileId:String):ByteArray? = null

    /** Upload preparation can seal these bytes without a plaintext staging image. */
    suspend fun thumbnailForBackup(fileId: String): ByteArray? = thumbnailBytes(fileId)

    suspend fun uploadThumbnailFile(fileId: String): File?
}

package com.infinity.drive.core.media

/** Coil request model resolved by [ThumbnailFetcher]. */
data class ThumbnailModel(val fileId: String, val revision:String="",val cacheOnly:Boolean=false)

fun thumbnailCacheKey(fileId: String): String = "thumb:$fileId"

/** Shared revision across library rows, warmup and the fullscreen audio surface. */
fun thumbnailModel(file:com.infinity.drive.domain.model.DriveFile)=
    ThumbnailModel(file.id,"${file.modifiedAt}:${file.localPath}:${file.remoteFileId}")

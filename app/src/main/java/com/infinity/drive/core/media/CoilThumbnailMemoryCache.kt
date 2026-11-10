package com.infinity.drive.core.media

import coil3.ImageLoader

class CoilThumbnailMemoryCache(
    private val imageLoader: ImageLoader
) : ThumbnailMemoryCache {

    override fun remove(fileId: String) {
        val cache=imageLoader.memoryCache?:return
        val base=thumbnailCacheKey(fileId)
        cache.keys.filter{it.key==base||it.key.startsWith("$base:")}.forEach(cache::remove)
    }

    override fun clear() {
        imageLoader.memoryCache?.clear()
    }
}

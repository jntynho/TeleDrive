package com.infinity.drive.domain.model

/** Small durable index. Artwork and lyrics remain in bounded, optional caches. */
data class AudioTags(val title:String?=null,val artist:String?=null,val durationMs:Long?=null,val inspected:Boolean=true)

fun DriveFile.audioRevision():String = "$id:$modifiedAt:$sizeBytes:${contentHash.orEmpty()}"

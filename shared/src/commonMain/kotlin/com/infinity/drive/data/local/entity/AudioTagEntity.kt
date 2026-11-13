package com.infinity.drive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName="audio_tags")
data class AudioTagEntity(
    @PrimaryKey val fileId:String,val modifiedAt:Long,val localPath:String?,val title:String?,val artist:String?,
    @ColumnInfo(defaultValue="-1") val sizeBytes:Long=-1,
    val contentHash:String?=null,val durationMs:Long?=null,
    @ColumnInfo(defaultValue="1") val inspected:Boolean=true
)

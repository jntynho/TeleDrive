package com.infinity.drive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Locally extracted tags, versioned independently of Telegram's file manifest. */
@Entity(tableName="audio_tags")
data class AudioTagEntity(@PrimaryKey val fileId:String,val modifiedAt:Long,val localPath:String?,val title:String?,val artist:String?)

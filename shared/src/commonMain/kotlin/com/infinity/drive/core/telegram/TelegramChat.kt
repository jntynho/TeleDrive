package com.infinity.drive.core.telegram

/** Chat identity and the small avatar only; list loading never waits for a photo download. */
data class TelegramChat(val id:Long,val title:String,val photoFileId:Int?=null,val photoPath:String?=null,val canSendDocuments:Boolean=true)

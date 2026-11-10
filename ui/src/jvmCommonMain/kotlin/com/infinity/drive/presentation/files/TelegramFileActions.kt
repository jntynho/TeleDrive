package com.infinity.drive.presentation.files

import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.domain.model.DriveFile
import kotlinx.coroutines.CancellationException

/** Resolves the file's actual Telegram messages, including files moved between storage channels. */
class TelegramFileActions(private val telegram:TelegramClient,private val parts:FilePartDao) {
    data class Reference(val chatId:Long,val messageId:Long)
    suspend fun references(file:DriveFile):List<Reference> = buildList {
        parts.partsOf(file.id).sortedBy{it.partIndex}.forEach { part ->
            val chat=part.chatId?:file.chatId
            part.messageId?.let{message->if(chat!=null)add(Reference(chat,message))}
        }
        val chat=file.chatId;val message=file.messageId
        if(chat!=null&&message!=null)add(Reference(chat,message))
    }.distinct()
    suspend fun needsOriginal(file:DriveFile):Boolean = file.isEncrypted||parts.partsOf(file.id).size>1||
        (references(file).isEmpty()&&file.remoteFileId==null)
    suspend fun messageLink(file:DriveFile):String {
        val refs=references(file)
        check(refs.isNotEmpty()){ "This file has no Telegram message yet. Upload it to your drive first." }
        var failure:Exception?=null
        for(ref in refs)try{return telegram.messageLink(ref.chatId,ref.messageId)}
        catch(cancel:CancellationException){throw cancel}
        catch(error:Exception){failure=error;if(error !is TelegramException||!error.isMissingMessage)throw error}
        throw failure?:IllegalStateException("The source message is no longer available in Telegram.")
    }
    suspend fun send(file:DriveFile,toChatId:Long){
        check(!needsOriginal(file)){"Send the prepared original using Telegram."}
        val ref=references(file).firstOrNull()
        if(ref!=null){
            try {telegram.sendToChat(toChatId,ref.chatId,listOf(ref.messageId));return}
            catch(cancel:CancellationException){throw cancel}
            catch(error:TelegramException){
                // A rejected/missing reference is safe to replace. Never repeat an unconfirmed send.
                if(!error.isMissingMessage||file.remoteFileId==null)throw error
            }
        }
        val remote=file.remoteFileId?:error("The original Telegram file reference is unavailable.")
        telegram.copyDocument(toChatId,remote,file.name,file.mimeType,"")
    }
}

internal val TelegramException.isMissingMessage:Boolean get() = code==404 || code==400&&
    (message.contains("MESSAGE_NOT_FOUND",true)||message.contains("message not found",true)||
        message.contains("message identifier",true)||message.contains("MESSAGE_ID_INVALID",true)||message.contains("FILE_REFERENCE_EXPIRED",true))

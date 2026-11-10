package app.tgdrive.rebuild

import com.infinity.drive.core.telegram.*
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.data.local.entity.FilePartEntity
import com.infinity.drive.domain.model.*
import com.infinity.drive.presentation.files.TelegramFileActions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class TelegramFileActionsTest {
    private inline fun <reified T> fake(crossinline answer:(String,Array<out Any?>)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,args->
            val values=args?:emptyArray()
            try{answer(method.name,values)}catch(error:Exception){
                @Suppress("UNCHECKED_CAST") val continuation=values.lastOrNull() as? kotlin.coroutines.Continuation<Any?>
                if(continuation==null)throw error
                continuation.resumeWith(Result.failure(error));kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }
        } as T
    private fun file()=DriveFile("file",null,"photo.jpg",10,"image/jpeg",FileCategory.IMAGE,null,null,1,1048576,"remote",null,BackupState.BACKED_UP,
        false,false,false,false,null,null,null,null,createdAt=1,modifiedAt=1,addedAt=1)
    private fun part(chat:Long,message:Long,index:Int=0)=FilePartEntity("file",index,chat,message,"remote",null,index*10L,10,10,1)
    private fun parts(rows:List<FilePartEntity>)=fake<FilePartDao>{name,_->if(name=="partsOf")rows else error(name)}
    @Test fun partReferencesUseTheirOwnChannelAndMissingHeadFallsBackToAnotherConfirmedReference()=runTest {
        val visited=mutableListOf<Pair<Long,Long>>()
        val telegram=fake<TelegramClient>{name,args->when(name){
            "messageLink"->{val ref=(args[0] as Long) to(args[1] as Long);visited+=ref;if(ref.first==2L)throw TelegramException(404,"Message not found");"tg://confirmed"}
            else->error(name)
        }}
        val action=TelegramFileActions(telegram,parts(listOf(part(2,2097152))))
        assertEquals("tg://confirmed",action.messageLink(file()))
        assertEquals(listOf(2L to 2097152L,1L to 1048576L),visited)
    }
    @Test fun missingMessageCanReuseRemoteFileButAnUnconfirmedSendIsNeverRepeated()=runTest {
        var code=404;var copies=0
        val telegram=fake<TelegramClient>{name,_->when(name){"sendToChat"->throw TelegramException(code,"Message not found");"copyDocument"->{copies++;RemoteDocument(1,2,"remote","unique","photo.jpg","image/jpeg",10,"",1,null)};else->error(name)}}
        val actions=TelegramFileActions(telegram,parts(emptyList()))
        actions.send(file(),9);assertEquals(1,copies)
        code=408
        assertTrue(runCatching{actions.send(file(),9)}.exceptionOrNull() is TelegramException)
        assertEquals(1,copies)
    }
    @Test fun encryptedAndSplitFilesUseTheOriginalPathInsteadOfForwardingCiphertextOrOnePart()=runTest {
        val telegram=fake<TelegramClient>{name,_->error("Unexpected send: $name")}
        assertTrue(TelegramFileActions(telegram,parts(emptyList())).needsOriginal(file().copy(isEncrypted=true)))
        assertTrue(TelegramFileActions(telegram,parts(listOf(part(2,2),part(3,3,1)))).needsOriginal(file()))
        assertTrue(TelegramFileActions(telegram,parts(emptyList())).needsOriginal(file().copy(chatId=null,messageId=null,remoteFileId=null,localPath="/original")))
    }
}

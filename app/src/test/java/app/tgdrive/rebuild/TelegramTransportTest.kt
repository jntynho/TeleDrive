package app.tgdrive.rebuild

import android.app.Application
import app.tgdrive.data.TdConnection
import app.tgdrive.data.TdConnectionFactory
import com.infinity.drive.core.files.AndroidStoragePaths
import com.infinity.drive.core.telegram.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class TelegramTransportTest {
    private class Wire:TdConnectionFactory {
        val callbacks=mutableListOf<(TdApi.Object)->Unit>()
        val sent=mutableListOf<TdApi.Function<*>>()
        val pending=mutableListOf<Pair<TdApi.Function<*>,(TdApi.Object)->Unit>>()
        override fun create(onUpdate:(TdApi.Object)->Unit,onError:()->Unit):TdConnection {
            callbacks+=onUpdate
            return object:TdConnection {
                override fun send(function:TdApi.Function<*>,result:(TdApi.Object)->Unit) {
                    sent+=function;pending+=function to result
                }
            }
        }
        fun reply(type:Class<*>,value:TdApi.Object) {
            val request=pending.first { type.isInstance(it.first) };pending.remove(request);request.second(value)
        }
        fun emit(value:TdApi.Object)=callbacks.last()(value)
    }
    private fun TestScope.client(wire:Wire,pacer:TelegramPacer=TelegramPacer()):TdLibTelegramClient {
        val context=RuntimeEnvironment.getApplication()
        return TdLibTelegramClient(context,object:TdlibDatabaseKeyProvider { override fun databaseKey()=ByteArray(32) },pacer,AndroidStoragePaths(context),wire,backgroundScope)
    }
    private fun message(id:Long,pending:Boolean=false):TdApi.Message = TdApi.Message().apply {
        this.id=id;chatId=-1001;date=123
        sendingState=if(pending)TdApi.MessageSendingStatePending(1)else null
        content=TdApi.MessageDocument(TdApi.Document().apply {
            fileName="photo.jpg";mimeType="image/jpeg"
            document=TdApi.File().apply { this.id=42;size=3;remote=TdApi.RemoteFile("remote","unique",false,true,3) }
        },TdApi.FormattedText("manifest",emptyArray()))
    }
    @Test fun `one direct send carries both original file and manifest without waiting for another update`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        val events=mutableListOf<TelegramUploadEvent>()
        val job=backgroundScope.launch { client.uploadDocument(-1001,"/photo.jpg","photo.jpg","image/jpeg","manifest").toList(events) }
        runCurrent()
        val sent=wire.sent.single() as TdApi.SendMessage
        val content=sent.inputMessageContent as TdApi.InputMessageDocument
        assertEquals(-1001L,sent.chatId)
        assertEquals("manifest",content.caption.text)
        assertTrue(content.document.document is TdApi.InputFileLocal)
        assertTrue(content.document.disableContentTypeDetection)
        wire.reply(TdApi.SendMessage::class.java,message(9));runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(9L,(events.last() as TelegramUploadEvent.Completed).document.messageId)
        assertFalse(wire.sent.any { it is TdApi.PreliminaryUploadFile || it is TdApi.DeleteMessages })
    }
    @Test fun `success arriving before send reply is not lost`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        val events=mutableListOf<TelegramUploadEvent>()
        val job=backgroundScope.launch { client.uploadDocument(-1001,"/photo.jpg","photo.jpg","image/jpeg","manifest").toList(events) }
        runCurrent();wire.emit(TdApi.UpdateMessageSendSucceeded(message(11),-5));runCurrent()
        wire.reply(TdApi.SendMessage::class.java,message(-5,true));runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(11L,(events.last() as TelegramUploadEvent.Completed).document.messageId)
        assertFalse(wire.sent.any { it is TdApi.DeleteMessages })
    }
    @Test fun `cancelling collection deletes only its pending message`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        val job=backgroundScope.launch { client.uploadDocument(-1001,"/photo.jpg","photo.jpg","image/jpeg","manifest").collect {} }
        runCurrent();wire.reply(TdApi.SendMessage::class.java,message(-5,true));runCurrent()
        job.cancel();runCurrent()
        val cancel=wire.sent.filterIsInstance<TdApi.DeleteMessages>().single()
        assertEquals(-1001L,cancel.chatId);assertArrayEquals(longArrayOf(-5),cancel.messageIds)
    }
    @Test fun `server-side copy completes immediately when response is final`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        var result:RemoteDocument?=null
        val job=backgroundScope.launch { result=client.copyDocument(-1001,"remote","photo.jpg","image/jpeg","manifest") }
        runCurrent();wire.reply(TdApi.SendMessage::class.java,message(19));runCurrent()
        assertTrue(job.isCompleted);assertEquals(19L,result!!.messageId)
    }
    @Test fun `stale connection updates cannot close the replacement client`()=runTest {
        val wire=Wire();val client=client(wire);val credentials=TelegramCredentials(12345,"a".repeat(32));client.start(credentials)
        val old=wire.callbacks.single()
        wire.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        client.start(credentials)
        wire.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
        old(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        assertEquals(TelegramAuthState.WaitingForPhoneNumber,client.authState.value)
        assertEquals(2,wire.callbacks.size)
    }
    @Test fun `missing native library produces a recoverable state`()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val client=TdLibTelegramClient(context,object:TdlibDatabaseKeyProvider { override fun databaseKey()=ByteArray(32) },TelegramPacer(),AndroidStoragePaths(context),TdConnectionFactory { _,_->throw UnsatisfiedLinkError("fixture") },backgroundScope)
        client.start(TelegramCredentials(12345,"a".repeat(32)))
        assertTrue(client.authState.value is TelegramAuthState.Failed)
    }
    @Test fun `an asynchronous send failure records the global deadline and removes only its failed placeholder`()=runTest {
        val wire=Wire();val pacer=TelegramPacer{testScheduler.currentTime};val client=client(wire,pacer)
        client.start(TelegramCredentials(12345,"a".repeat(32)))
        var failure:Throwable?=null
        val job=backgroundScope.launch{try{client.uploadDocument(-1001,"/photo.jpg","photo.jpg","image/jpeg","manifest").collect{}}catch(error:Throwable){failure=error}}
        runCurrent();wire.reply(TdApi.SendMessage::class.java,message(-5,true));runCurrent()
        wire.emit(TdApi.UpdateMessageSendFailed().apply{message=message(-8);oldMessageId=-5;error=TdApi.Error(429,"FLOOD_WAIT_160")});runCurrent()
        assertTrue(job.isCompleted);assertTrue(failure is TelegramException);assertEquals(161,pacer.floodWaitSeconds)
        val deleted=wire.sent.filterIsInstance<TdApi.DeleteMessages>().single();assertArrayEquals(longArrayOf(-8),deleted.messageIds)
    }
    @Test fun `an interrupted pending upload resumes delivery without another SendMessage`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        var events:kotlinx.coroutines.flow.Flow<TelegramUploadEvent>?=null
        val lookup=backgroundScope.launch{events=client.recoverUpload(-1001,-5)}
        runCurrent();wire.reply(TdApi.GetMessage::class.java,message(-5,true));runCurrent();assertTrue(lookup.isCompleted)
        val received=mutableListOf<TelegramUploadEvent>()
        val resume=backgroundScope.launch{events!!.toList(received)}
        runCurrent();wire.emit(TdApi.UpdateMessageSendSucceeded(message(23),-5));runCurrent()
        wire.reply(TdApi.GetMessage::class.java,message(-5,true));runCurrent()
        assertTrue(resume.isCompleted);assertEquals(23L,(received.last() as TelegramUploadEvent.Completed).document.messageId)
        assertFalse(wire.sent.any{it is TdApi.SendMessage||it is TdApi.PreliminaryUploadFile})
    }
    @Test fun `copying from a chat replaces its caption in the same request without a local upload`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        val events=mutableListOf<TelegramUploadEvent>()
        val job=backgroundScope.launch{client.copyMessageEvents(-1001,-2002,31,"new manifest").toList(events)}
        runCurrent()
        val send=wire.sent.single() as TdApi.SendMessage
        val forwarded=send.inputMessageContent as TdApi.InputMessageForwarded
        assertEquals(-2002L,forwarded.fromChatId);assertEquals(31L,forwarded.messageId)
        assertTrue(forwarded.copyOptions.sendCopy);assertTrue(forwarded.copyOptions.replaceCaption)
        assertEquals("new manifest",forwarded.copyOptions.newCaption.text)
        wire.reply(TdApi.SendMessage::class.java,message(41));runCurrent()
        assertTrue(job.isCompleted);assertEquals(41L,(events.last() as TelegramUploadEvent.Completed).document.messageId)
    }

    @Test fun `canceling before send acceptance cleans up a late final reply instead of leaking a message`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        val events=mutableListOf<TelegramUploadEvent>()
        val job=backgroundScope.launch{client.uploadDocument(-1001,"/photo.jpg","photo.jpg","image/jpeg","manifest").toList(events)}
        runCurrent();job.cancel();runCurrent()
        wire.reply(TdApi.SendMessage::class.java,message(99));runCurrent()
        assertTrue(job.isCompleted)
        assertArrayEquals(longArrayOf(99),wire.sent.filterIsInstance<TdApi.DeleteMessages>().single().messageIds)
        assertFalse(events.any{it is TelegramUploadEvent.Completed})
    }

    @Test fun `private channel link remains available when export is rejected`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        val result=async { client.messageLink(-1001,123L shl 20) }
        runCurrent();wire.reply(TdApi.GetMessage::class.java,message(123L shl 20));runCurrent()
        wire.reply(TdApi.GetChat::class.java,TdApi.Chat().apply{id=-1001;type=TdApi.ChatTypeSupergroup(77,true)});runCurrent()
        wire.reply(TdApi.GetMessageProperties::class.java,TdApi.MessageProperties().apply{canGetLink=true});runCurrent()
        wire.reply(TdApi.GetMessageLink::class.java,TdApi.Error(403,"Chat link export is unavailable"));runCurrent()
        assertEquals("https://t.me/c/77/123",result.await())
    }
    @Test fun `legacy server message ids are normalized before creating a direct link`()=runTest {
        val wire=Wire();val client=client(wire);client.start(TelegramCredentials(12345,"a".repeat(32)))
        val result=async { client.messageLink(-1001,123) }
        runCurrent();wire.reply(TdApi.GetMessage::class.java,TdApi.Error(404,"Message not found"));runCurrent()
        assertEquals(123L shl 20,wire.sent.filterIsInstance<TdApi.GetMessage>().last().messageId)
        wire.reply(TdApi.GetMessage::class.java,message(123L shl 20));runCurrent()
        wire.reply(TdApi.GetChat::class.java,TdApi.Chat().apply{id=-1001;type=TdApi.ChatTypePrivate(42)});runCurrent()
        wire.reply(TdApi.GetMessageProperties::class.java,TdApi.MessageProperties());runCurrent()
        assertEquals("tg://openmessage?user_id=42&message_id=123",result.await())
    }

}

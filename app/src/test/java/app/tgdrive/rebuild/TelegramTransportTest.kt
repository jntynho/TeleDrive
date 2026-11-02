package app.tgdrive.rebuild

import android.app.Application
import app.tgdrive.data.TdConnection
import app.tgdrive.data.TdConnectionFactory
import com.infinity.drive.core.files.AndroidStoragePaths
import com.infinity.drive.core.telegram.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private fun TestScope.client(wire:Wire):TdLibTelegramClient {
        val context=RuntimeEnvironment.getApplication()
        return TdLibTelegramClient(context,object:TdlibDatabaseKeyProvider { override fun databaseKey()=ByteArray(32) },TelegramPacer(),AndroidStoragePaths(context),wire,backgroundScope)
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
}

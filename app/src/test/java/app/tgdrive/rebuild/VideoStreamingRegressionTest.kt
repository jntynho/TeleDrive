package app.tgdrive.rebuild

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.media.*
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramFileInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.EOFException
import java.lang.reflect.Proxy

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class VideoStreamingRegressionTest {
    private class Wire {
        var reads=0
        var resolutions=0
        var infos=0
        var delayed=false
        var empty=false
        val cancelled=mutableListOf<Int>()
        private fun info(id:Int,ready:Boolean)=TelegramFileInfo(id,3,null,ready,0,if(ready)3 else 0)
        val client:TelegramClient=Proxy.newProxyInstance(TelegramClient::class.java.classLoader,arrayOf(TelegramClient::class.java)){_,method,args->
            val a=args?:emptyArray()
            when(method.name){
                "resolveFile"->{resolutions++;info(if(a[0]=="two")2 else 1,!delayed)}
                "getFileInfo"->{infos++;info(a[0] as Int,!delayed||infos>=2)}
                "requestFileRange"->Unit
                "readFilePart"->{reads++;if(empty||(delayed&&reads==1))byteArrayOf()else ByteArray((a[2] as Long).toInt()){7}}
                "fileUpdates"->error("No update will arrive: it was emitted before subscription")
                "cancelFileDownload"->{cancelled.add(a[0] as Int);Unit}
                else->error(method.name)
            }
        } as TelegramClient
    }
    private fun spec(position:Long=0,length:Long=C.LENGTH_UNSET.toLong())=DataSpec.Builder()
        .setUri(Uri.parse("telegram://stream")).setPosition(position).setLength(length).build()

    @Test fun requestedRangeIsClampedToActualFileAndSourceCanBeReopened() {
        val wire=Wire();val source=TelegramDataSource(wire.client,"one")
        assertEquals(2,source.open(spec(position=1,length=100)))
        assertEquals(2,source.read(ByteArray(100),0,100))
        assertEquals(C.RESULT_END_OF_INPUT,source.read(ByteArray(1),0,1))
        source.close()
        assertEquals(3,source.open(spec()))
        assertEquals(2,wire.resolutions)
        source.close()
    }
    @Test fun missingBytesBeforeTheDeclaredEndAreAnErrorNotSuccessfulEof() {
        val wire=Wire().apply{empty=true};val source=TelegramDataSource(wire.client,"one")
        source.open(spec())
        try { source.read(ByteArray(3),0,3);fail("Expected premature EOF") }
        catch(_:EOFException){} finally{source.close()}
    }
    @Test fun completionBeforeUpdateSubscriptionDoesNotHang()=runBlocking {
        val wire=Wire().apply{delayed=true}
        val source=TelegramMediaByteSource(wire.client,"one")
        assertArrayEquals(byteArrayOf(7,7,7),source.read(0,3))
        assertEquals(2,wire.infos)
        source.close()
    }
    @Test fun multipartRangesAreBoundedAndAllPrefetchedDownloadsAreClosed() {
        val wire=Wire()
        val source=PartedTelegramDataSource(wire.client,StreamCrypto(),listOf(MediaPart("one",0,3),MediaPart("two",3,3)),false,null)
        assertEquals(6,source.open(spec(length=100)))
        assertEquals(3,source.read(ByteArray(10),0,10))
        source.close()
        assertEquals(setOf(1,2),wire.cancelled.toSet())
    }
    @Test fun encryptedFramesAndHeadersAcceptShortNativeReads()=runBlocking {
        val crypto=StreamCrypto()
        val key=ByteArray(32){it.toByte()}
        val plaintext=ByteArray(103){it.toByte()}
        val output=java.io.ByteArrayOutputStream()
        crypto.encryptStream(key,plaintext.inputStream(),output)
        val sealed=output.toByteArray()
        val info=TelegramFileInfo(1,sealed.size.toLong(),null,true,0,sealed.size.toLong())
        val client=Proxy.newProxyInstance(TelegramClient::class.java.classLoader,arrayOf(TelegramClient::class.java)){_,method,args->
            when(method.name){
                "resolveFile","getFileInfo"->info
                "readFilePart"->{val start=(args!![1] as Long).toInt();val requested=(args[2] as Long).toInt();sealed.copyOfRange(start,minOf(start+requested,start+7,sealed.size))}
                "cancelFileDownload"->Unit
                else->error(method.name)
            }
        } as TelegramClient
        val source=PartedMediaByteSource(client,crypto,listOf(MediaPart("one",0,plaintext.size.toLong())),true,key)
        try { assertArrayEquals(plaintext,source.read(0,plaintext.size)) } finally { source.close() }
    }
    @Test fun seekingPastTheFileReportsAnOutOfRangeError() {
        val source=TelegramDataSource(Wire().client,"one")
        try {source.open(spec(position=4));fail("Expected out of range")}
        catch(e:androidx.media3.datasource.DataSourceException){assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,e.reason)}
        finally{source.close()}
    }
}

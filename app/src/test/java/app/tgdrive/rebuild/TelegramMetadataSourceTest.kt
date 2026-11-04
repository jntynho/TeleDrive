package app.tgdrive.rebuild

import android.app.Application
import com.infinity.drive.core.media.TelegramMetadataSource
import com.infinity.drive.core.telegram.*
import java.io.IOException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class TelegramMetadataSourceTest {
    private class Wire {
        val ranges=mutableListOf<Pair<Long,Long>>()
        var available=false
        var cancelled=0
        val client=Proxy.newProxyInstance(TelegramClient::class.java.classLoader,arrayOf(TelegramClient::class.java)){_,method,args->
            when(method.name){
                "resolveFile","getFileInfo"->TelegramFileInfo(4,100_000_000,null,false,0,if(available)4096 else 0)
                "readFilePart"->if(available)ByteArray((args!![2] as Long).toInt()){7}else throw IOException("Not downloaded")
                "requestFileRange"->{ranges+=(args!![1] as Long) to (args[2] as Long);available=true;Unit}
                "cancelFileDownload"->{cancelled++;Unit}
                else->error(method.name)
            }
        } as TelegramClient
    }
    @Test fun tagsReadOnlyABoundedRangeAndClosingDoesNotCancelPlaybackDownloads(){
        val wire=Wire();val source=TelegramMetadataSource(wire.client,"file",{false},byteBudget=4096)
        assertEquals(100_000_000,source.size)
        assertEquals(1024,source.readAt(0,ByteArray(1024),0,1024))
        assertEquals(listOf(0L to 1024L),wire.ranges)
        source.close();assertEquals(0,wire.cancelled)
    }
    @Test fun inspectionStopsWhenPlaybackTakesOwnershipOrItsReadBudgetIsExhausted(){
        val wire=Wire();var playing=false
        val source=TelegramMetadataSource(wire.client,"file",{playing},byteBudget=1024)
        source.readAt(0,ByteArray(1024),0,1024)
        assertThrows(IOException::class.java){source.readAt(1024,ByteArray(1),0,1)}
        val second=TelegramMetadataSource(wire.client,"file",{playing})
        playing=true
        assertThrows(IOException::class.java){second.readAt(0,ByteArray(1),0,1)}
        assertEquals(0,wire.cancelled)
    }
    @Test fun exhaustedDeadlineNeverStartsANetworkRequest(){
        val wire=Wire();val source=TelegramMetadataSource(wire.client,"file",{false},timeoutMs=0)
        assertThrows(IOException::class.java){source.readAt(0,ByteArray(1),0,1)}
        assertTrue(wire.ranges.isEmpty())
    }
}

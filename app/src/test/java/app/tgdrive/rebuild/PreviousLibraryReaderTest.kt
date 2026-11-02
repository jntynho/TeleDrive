package app.tgdrive.rebuild

import android.app.Application
import app.tgdrive.library.*
import app.tgdrive.migration.PreviousLibraryReader
import com.infinity.drive.core.telegram.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class PreviousLibraryReaderTest {
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun reader(parts:List<File>):PreviousLibraryReader {
        val client=Proxy.newProxyInstance(TelegramClient::class.java.classLoader,arrayOf(TelegramClient::class.java)) { _,method,args ->
            when(method.name) {
                "getCurrentUser" -> TelegramUser(123,"Fixture","",null,"",false)
                "previousSavedMessagesChatId" -> 123L
                "getDocument" -> {
                    val index=((args!![1] as Long)-10).toInt()
                    RemoteDocument(123,index+10L,index.toString(),"part-$index","part","application/octet-stream",parts[index].length(),"",0)
                }
                "downloadDocument" -> {
                    val file=parts[(args!![0] as String).toInt()]
                    flowOf(TelegramDownloadEvent.Completed(file.path,file.length()))
                }
                else -> error("Unexpected Telegram write or request: ${method.name}")
            }
        } as TelegramClient
        return PreviousLibraryReader(RuntimeEnvironment.getApplication(),client)
    }
    private fun entry(bytes:List<ByteArray>,vault:String="")=PreviousLibraryReader.Entry(
        Item(id="item",name="original.bin",size=bytes.sumOf{it.size}.toLong(),versionId="version"),
        Version(id="version",itemId="item",size=bytes.sumOf{it.size}.toLong(),sha256=sha(bytes.fold(ByteArray(0)){all,part->all+part}),parts=bytes.mapIndexed { i,part -> Part(i+10L,i,part.size.toLong(),part.size.toLong(),sha(part),false) }),vault)
    @Test fun `previous multipart file is assembled verified and never republished`()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val bytes=listOf("first part".toByteArray(),"second part".toByteArray())
        val files=bytes.mapIndexed { i,b -> File(context.cacheDir,"part-$i").apply { writeBytes(b) } }
        val reader=reader(files);reader.list()
        val result=reader.verifiedCopy(entry(bytes)) {}
        assertArrayEquals(bytes[0]+bytes[1],result.readBytes())
        assertTrue(files.all{it.exists()});result.delete();reader.close()
    }
    @Test fun `corrupted part is rejected without leaving an assembled export`()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val bytes=listOf("expected bytes".toByteArray())
        val file=File(context.cacheDir,"broken").apply { writeBytes("modified bytes".toByteArray()) }
        val reader=reader(listOf(file));reader.list()
        assertNotNull(runCatching { reader.verifiedCopy(entry(bytes)) {} }.exceptionOrNull())
        assertTrue(File(context.cacheDir,"previous-library-exports").listFiles().orEmpty().isEmpty())
        assertEquals("modified bytes",file.readText());reader.close()
    }
    @Test fun `previous encrypted vault remains decryptable with its original password`()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val original="previous encrypted bytes".toByteArray()
        val creator=VaultManager()
        val descriptor=creator.create("Previous vault","long-fixture-password".toCharArray()).first
        val encrypted=java.io.ByteArrayOutputStream().also { creator.encrypt(descriptor.id,original.inputStream(),it,"item/version/0") }.toByteArray()
        val file=File(context.cacheDir,"encrypted-part").apply { writeBytes(encrypted) }
        val reader=reader(listOf(file));reader.list();reader.unlock(descriptor,"long-fixture-password".toCharArray(),false)
        val base=entry(listOf(original),descriptor.id)
        val encryptedEntry=base.copy(version=base.version.copy(parts=listOf(Part(10,0,original.size.toLong(),encrypted.size.toLong(),sha(encrypted),true))))
        val result=reader.verifiedCopy(encryptedEntry) {}
        assertArrayEquals(original,result.readBytes())
        result.delete();reader.close();creator.lock()
    }
}

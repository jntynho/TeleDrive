package app.tgdrive.rebuild

import app.tgdrive.migration.copySessionDirectory
import com.infinity.drive.core.crypto.*
import com.infinity.drive.core.files.AppStoragePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28],application=android.app.Application::class,manifest=org.robolectric.annotation.Config.NONE)
class UpgradeSafetyTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun paths(root:File)=object:AppStoragePaths {
        override val filesDir=root
        override val cacheDir=root
        override val externalCacheDir:File?=null
        override val externalStorageRoot:File?=null
    }
    @Test fun `session upgrade is a byte-for-byte copy and refuses replacement`()=runTest {
        val source=temporary.newFolder("old")
        val bytes=ByteArray(300_123) { (it%241).toByte() }
        val original=File(source,"nested/td.binlog").apply { parentFile.mkdirs();writeBytes(bytes) }
        val target=File(temporary.root,"new/db")
        copySessionDirectory(source,target)
        assertArrayEquals(bytes,File(target,"nested/td.binlog").readBytes())
        assertArrayEquals(bytes,original.readBytes())
        val rejected=runCatching { copySessionDirectory(source,target) }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)
        assertArrayEquals(bytes,original.readBytes())
        assertFalse(File(target.parentFile,"db.upgrade-copy").exists())
    }
    @Test fun `unreadable wrapped Telegram key is preserved rather than recreated`() {
        val root=temporary.newFolder("key-store")
        val wrapped=File(root,"keys/${CryptoKeys.TDLIB_DATABASE}.bin").apply { parentFile.mkdirs();writeText("original ciphertext") }
        val cipher=object:CredentialCipher {
            override fun encrypt(plaintext:ByteArray)=error("must not replace")
            override fun decrypt(ciphertext:ByteArray):ByteArray=error("wrong device")
        }
        val keys=FileWrappedKeyRepository(paths(root),cipher)
        assertTrue(runCatching { keys.getOrCreate(CryptoKeys.TDLIB_DATABASE) }.exceptionOrNull() is KeyUnavailableException)
        assertEquals("original ciphertext",wrapped.readText())
    }
    @Test fun `existing session with missing key fails without minting a replacement`() {
        val root=temporary.newFolder("session")
        File(root,"tdlib/db/td.binlog").apply { parentFile.mkdirs();writeText("preserve") }
        val cipher=object:CredentialCipher {
            override fun encrypt(plaintext:ByteArray)=error("must not create")
            override fun decrypt(ciphertext:ByteArray)=ciphertext
        }
        val keys=FileWrappedKeyRepository(paths(root),cipher)
        assertTrue(runCatching { TdlibDatabaseKeyProviderImpl(paths(root),keys).databaseKey() }.exceptionOrNull() is KeyUnavailableException)
        assertEquals("preserve",File(root,"tdlib/db/td.binlog").readText())
        assertFalse(File(root,"keys").exists())
    }
}

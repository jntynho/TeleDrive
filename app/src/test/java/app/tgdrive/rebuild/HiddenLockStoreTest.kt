package app.tgdrive.rebuild

import android.app.Application
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.core.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class HiddenLockStoreTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val file get()=File(context.noBackupFilesDir,"hidden-lock.bin")
    private val cipher=object:CredentialCipher {
        private val key=SecretKeySpec(ByteArray(32){it.toByte()},"AES")
        override fun encrypt(plaintext:ByteArray):ByteArray {
            val aes=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key)}
            return aes.iv+aes.doFinal(plaintext)
        }
        override fun decrypt(ciphertext:ByteArray):ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run{
            init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,ciphertext,0,12));doFinal(ciphertext,12,ciphertext.size-12)
        }
    }
    @Before @After fun remove(){listOf("",".bak",".new").forEach{File(file.path+it).delete()}}
    @Test fun authenticatedRecordIsOutsideBackupAndTamperingDoesNotDisableTheLock()=runTest {
        val store=AndroidHiddenLockStore(context,cipher)
        val manager=HiddenLockManager(store,1000,{100_000},backgroundScope)
        manager.awaitReady();manager.configure("246810".toCharArray(),"0123456789ABCDEF0123456789ABCDEF")
        assertTrue(file.isFile)
        val bytes=file.readBytes()
        assertFalse(bytes.decodeToString().contains("pinHash"));assertFalse(bytes.decodeToString().contains("246810"))
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
        val reopened=HiddenLockManager(store,1000,{100_000},backgroundScope);reopened.awaitReady()
        assertTrue(reopened.state.value.error);assertTrue(reopened.state.value.restricted)
    }
    @Test fun interruptedAtomicWriteWithOnlyBackupRemainingDoesNotLookLikeAnUnconfiguredLock()=runTest {
        val store=AndroidHiddenLockStore(context,cipher)
        val manager=HiddenLockManager(store,1000,{100_000},backgroundScope)
        manager.awaitReady();manager.configure("246810".toCharArray(),"0123456789ABCDEF0123456789ABCDEF")
        assertTrue(file.renameTo(File(file.path+".bak")))
        val reopened=HiddenLockManager(store,1000,{100_000},backgroundScope);reopened.awaitReady()
        assertTrue(reopened.state.value.configured);assertTrue(reopened.state.value.restricted)
        assertEquals(HiddenVerification.SUCCESS,reopened.verifyPin("246810".toCharArray()))
    }
}

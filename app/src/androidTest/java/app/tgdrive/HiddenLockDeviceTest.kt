package app.tgdrive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infinity.drive.core.security.*
import com.infinity.drive.presentation.hidden.*
import com.infinity.drive.presentation.platform.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** The native adapter's success/cancel callbacks drive the real Hidden gate and setting. */
@RunWith(AndroidJUnit4::class)
class HiddenLockDeviceTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @After fun close(){scope.cancel()}
    @Test fun nativeConfirmationIsRequiredAndCancellationDoesNotUnlock(){
        val store=object:HiddenLockStore{
            var record:HiddenLockRecord?=null
            override suspend fun read()=record
            override suspend fun write(record:HiddenLockRecord){this.record=record}
            override suspend fun clear(){record=null}
        }
        val lock=HiddenLockManager(store,1000,ownerScope=scope)
        runBlocking{lock.awaitReady();lock.setDeviceLock(true,lock.state.value.epoch)}
        var requests=0;var confirm:(()->Unit)?=null;var deny:((String?)->Unit)?=null
        val gate=DeviceOwnerGate{_,_,no,yes->requests++;confirm=yes;deny=no}
        var backedOut=false
        val open=androidx.compose.runtime.mutableStateOf(true)
        compose.setContent{CompositionLocalProvider(LocalHiddenDeviceOwnerGate provides gate){MaterialTheme{if(open.value)HiddenLockScreen({backedOut=true},lock)}}}
        compose.waitUntil(10_000){requests==1}
        assertTrue(lock.state.value.restricted)
        compose.runOnIdle{deny!!(null)}
        compose.waitUntil(10_000){backedOut}
        compose.onNodeWithText("Hidden is locked").assertDoesNotExist()
        compose.runOnIdle{open.value=false};compose.waitForIdle()
        compose.runOnIdle{open.value=true}
        compose.waitUntil(10_000){requests==2}
        compose.runOnIdle{confirm!!()}
        compose.waitUntil(10_000){lock.state.value.unlocked}
        assertTrue(lock.state.value.deviceLock)
    }
}

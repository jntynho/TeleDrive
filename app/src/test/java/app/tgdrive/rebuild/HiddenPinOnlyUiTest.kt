package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.security.*
import com.infinity.drive.presentation.hidden.*
import com.infinity.drive.presentation.platform.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class HiddenPinOnlyUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val models=ViewModelStore()
    @Before fun start(){
        startKoin{modules(module{single{SensitiveScreens()}})}
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun close(){activity.pause().stop().destroy();models.clear();scope.cancel();stopKoin()}
    @Test fun aLegacyBiometricEnrollmentStillUnlocksUsingOnlyTheAppPinAndLightKeyHaptics(){
        val store=object:HiddenLockStore{
            var record:HiddenLockRecord?=null
            override suspend fun read()=record
            override suspend fun write(record:HiddenLockRecord){this.record=record}
            override suspend fun clear(){record=null}
        }
        val manager=HiddenLockManager(store,1000,ownerScope=scope)
        runBlocking{
            manager.awaitReady();manager.configure("246810".toCharArray(),"0123456789ABCDEF0123456789ABCDEF")
            manager.enrollBiometric(ByteArray(12),ByteArray(48),ByteArray(32){7});manager.lock()
        }
        var taps=0
        val haptic=object:HapticFeedback{override fun performHapticFeedback(hapticFeedbackType:HapticFeedbackType){taps++}}
        val biometric=object:HiddenBiometrics{
            override val available=true
            override suspend fun enroll():HiddenBiometricEnrollment?=error("Hidden must not request phone authentication")
            override suspend fun authenticate(iv:ByteArray,ciphertext:ByteArray):ByteArray?=error("Hidden must not request phone authentication")
        }
        val vm=HiddenLockViewModel(manager);models.put("hidden",vm)
        activity.get().setContent{
            CompositionLocalProvider(LocalHapticFeedback provides haptic,LocalHiddenBiometrics provides biometric,
                LocalDeviceOwnerGate provides DeviceOwnerGate{_,_,_,_->error("Phone lock must not be requested")}){
                MaterialTheme{HiddenLockScreen({},vm)}
            }
        }
        compose.onNodeWithText("Your private space").assertIsDisplayed()
        compose.onNodeWithContentDescription("Unlock with fingerprint").assertDoesNotExist()
        "246810".forEachIndexed{index,digit->
            compose.onNodeWithContentDescription(digit.toString()).performScrollTo().performClick()
            if(index<5)compose.onNodeWithContentDescription("PIN, ${index+1} of 6 digits entered").assertExists()
        }
        compose.waitUntil(5000){
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            manager.state.value.unlocked||vm.ui.value.message!=null
        }
        assertTrue("PIN verification failed: ${vm.ui.value}; lock=${manager.state.value}",manager.state.value.unlocked)
        compose.runOnIdle{assertEquals(6,taps)}
        assertNotEquals("246810",store.record!!.pinHash)
    }
}

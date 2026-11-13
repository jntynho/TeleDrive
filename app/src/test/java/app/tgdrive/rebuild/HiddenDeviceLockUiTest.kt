package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
class HiddenDeviceLockUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var record:HiddenLockRecord?=null
    private val store=object:HiddenLockStore{
        override suspend fun read()=record
        override suspend fun write(record:HiddenLockRecord){this@HiddenDeviceLockUiTest.record=record}
        override suspend fun clear(){record=null}
    }
    private lateinit var manager:HiddenLockManager
    private var requests=0
    private var confirmed:(()->Unit)?=null
    private var denied:((String?)->Unit)?=null
    private val gate=DeviceOwnerGate{_,_,no,yes->requests++;confirmed=yes;denied=no}
    @Before fun start(){
        manager=HiddenLockManager(store,1000,ownerScope=scope)
        runBlocking{manager.awaitReady()}
        startKoin{modules(module{single{SensitiveScreens()};single{manager}})}
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun close(){activity.pause().stop().destroy();scope.cancel();stopKoin()}
    private fun legacy(){runBlocking{manager.configure("246810".toCharArray(),"0123456789ABCDEF0123456789ABCDEF");manager.lock()}}
    private fun show(content:@Composable ()->Unit){activity.get().setContent{
        CompositionLocalProvider(LocalHiddenDeviceOwnerGate provides gate){MaterialTheme{content()}}
    }}
    private fun await(condition:()->Boolean){compose.waitUntil(5000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();condition()}}
    @Test fun existingPinUsesNativeAuthenticationAndMigratesOnlyAfterSuccess(){
        legacy();show{HiddenLockScreen({},manager)}
        compose.waitForIdle();assertEquals(1,requests)
        compose.onNodeWithContentDescription("1").assertDoesNotExist()
        assertTrue(manager.state.value.restricted);assertEquals(1,record!!.version)
        compose.runOnIdle{confirmed!!()}
        await{manager.state.value.unlocked}
        assertEquals(2,record!!.version);assertTrue(record!!.pinHash.isEmpty())
    }
    @Test fun cancellationDoesNotUnlockAndLateSuccessFromTheCancelledPromptIsIgnored(){
        var backedOut=false
        legacy();show{HiddenLockScreen({backedOut=true},manager)};compose.waitForIdle()
        val stale=confirmed!!
        compose.runOnIdle{denied!!(null);stale()}
        compose.waitForIdle();assertTrue(manager.state.value.restricted);assertTrue(backedOut)
        compose.onNodeWithText("Hidden is locked").assertDoesNotExist()
        compose.onNodeWithText("Unlock",substring=false).assertDoesNotExist()
    }

    @Test fun entryStaysOnTheSourceUntilNativeAuthenticationSucceeds(){
        legacy();var opened by mutableStateOf(false)
        show{
            val enter=rememberCollectionEntry(manager,"Hidden"){opened=true}
            if(opened)Text("Private collection")else androidx.compose.material3.Button(enter){Text("Open Hidden")}
        }
        compose.onNodeWithText("Open Hidden").performClick()
        compose.waitForIdle();assertEquals(1,requests)
        compose.onNodeWithText("Private collection").assertDoesNotExist()
        compose.runOnIdle{denied!!(null)}
        compose.onNodeWithText("Open Hidden").assertIsDisplayed()
        compose.onNodeWithText("Open Hidden").performClick()
        compose.waitForIdle();assertEquals(2,requests)
        compose.runOnIdle{confirmed!!()}
        await{opened}
        compose.onNodeWithText("Private collection").assertIsDisplayed()
    }
    @Test fun securityAndHiddenSettingsShareTheSameAuthenticatedToggle(){
        show{Column{HiddenLockSettingRow(manager);HiddenLockSettingRow(manager)}}
        compose.onAllNodes(isToggleable())[0].assertIsOff().performClick()
        compose.runOnIdle{assertFalse(manager.state.value.configured);confirmed!!()}
        await{manager.state.value.configured}
        compose.onAllNodes(isToggleable()).fetchSemanticsNodes().indices.forEach{compose.onAllNodes(isToggleable())[it].assertIsOn()}
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.runOnIdle{denied!!(null)}
        compose.waitForIdle();assertTrue(manager.state.value.configured)
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.runOnIdle{confirmed!!()};await{!manager.state.value.configured}
        compose.onAllNodes(isToggleable())[0].assertIsOff();assertNull(record)
    }
    @Test fun aDeviceCredentialRoundTripCanResumeTheSameDestination(){
        legacy();show{HiddenLockScreen({},manager)};compose.waitForIdle()
        activity.pause().stop();manager.lock()
        activity.start().resume().visible()
        compose.runOnIdle{confirmed!!()}
        await{manager.state.value.unlocked}
        assertEquals(1,requests)
    }
    @Test fun navigationInvalidatesThePromptBeforeTheScreenIsDisposed(){
        legacy();var navigating by mutableStateOf(false)
        show{CompositionLocalProvider(app.tgdrive.ui.LocalNavigationInProgress provides navigating){HiddenLockScreen({},manager)}}
        compose.waitForIdle();val stale=confirmed!!
        compose.runOnIdle{navigating=true};compose.waitForIdle()
        compose.runOnIdle{stale()};compose.waitForIdle()
        assertTrue(manager.state.value.restricted)
    }
    @Test fun leavingTheScreenInvalidatesAPendingNativeResult(){
        legacy();var visible by mutableStateOf(true)
        show{if(visible)HiddenLockScreen({},manager)else Text("Public page")}
        compose.waitForIdle();val stale=confirmed!!
        compose.runOnIdle{visible=false};compose.waitForIdle()
        compose.runOnIdle{stale()};compose.waitForIdle()
        assertTrue(manager.state.value.restricted)
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun relockOptionsPersistAndRequireAuthenticationWhenTheCollectionIsLocked(){
        runBlocking{manager.setDeviceLock(true,manager.state.value.epoch);manager.unlockWithDevice(manager.state.value.epoch)}
        show{HiddenSettingsScreen({})}
        compose.onNodeWithText("After leaving the app",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Automatically relock",substring=false).performClick()
        await{compose.onAllNodesWithText("Never",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Never",substring=false).performClick()
        await{manager.state.value.autoLockMinutes==-1}
        compose.runOnIdle{manager.lock()}
        compose.onNodeWithText("Automatically relock",substring=false).performClick()
        compose.waitForIdle();assertEquals(1,requests)
        compose.onNodeWithText("After 3 minutes",substring=false).assertDoesNotExist()
        compose.runOnIdle{confirmed!!()}
        await{manager.state.value.unlocked}
        await{compose.onAllNodesWithText("After 3 minutes",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("After 3 minutes",substring=false).performClick()
        await{manager.state.value.autoLockMinutes==3}
    }

}

package app.tgdrive

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infinity.drive.core.security.*
import com.infinity.drive.presentation.hidden.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Drives the real in-app keypad and recovery acknowledgement, without a Telegram account or an IME. */
@RunWith(AndroidJUnit4::class)
class HiddenLockDeviceTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val models=ViewModelStore()
    @After fun close(){models.clear();scope.cancel()}
    @Test fun setupRequiresConfirmationAndASavedRecoveryKeyThenUnlocksWithTheSameKeypad(){
        val store=object:HiddenLockStore {
            var saved:HiddenLockRecord?=null
            override suspend fun read()=saved
            override suspend fun write(record:HiddenLockRecord){saved=record}
            override suspend fun clear(){saved=null}
        }
        val manager=HiddenLockManager(store,1000,ownerScope=scope)
        val vm=HiddenLockViewModel(manager);models.put("hidden",vm)
        compose.setContent{MaterialTheme{HiddenLockScreen({},vm)}}
        compose.waitUntil(10_000){compose.onAllNodesWithText("Protect Hidden").fetchSemanticsNodes().isNotEmpty()}
        fun enter(){"246810".forEach{compose.onNodeWithContentDescription(it.toString()).performClick()}}
        enter();compose.onNodeWithText("Confirm your PIN").assertIsDisplayed()
        enter();compose.onNodeWithText("Save your recovery key").assertIsDisplayed()
        compose.onNodeWithText("Finish setup").assertIsNotEnabled()
        compose.onNodeWithText("I saved this key outside TeleDrive").performScrollTo().performClick()
        compose.onNodeWithText("Finish setup").performScrollTo().performClick()
        compose.waitUntil(10_000){manager.state.value.unlocked}
        compose.runOnIdle{manager.lock()}
        compose.waitUntil(10_000){compose.onAllNodesWithText("Your private space").fetchSemanticsNodes().isNotEmpty()}
        enter();compose.waitUntil(10_000){manager.state.value.unlocked}
        assertNotNull(store.saved)
    }
}

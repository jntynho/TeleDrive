package app.tgdrive.rebuild

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasSetTextAction
import com.infinity.drive.core.accounts.DeviceAccounts
import com.infinity.drive.core.accounts.AccountSwitchActivity
import org.junit.Assert.*
import org.robolectric.Shadows.shadowOf
import com.infinity.drive.presentation.platform.AccountProfilesSetting
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AccountProfilesSettingTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun switchIconOpensCurrentAndSavedProfiles() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val context = activity.get()
            val workId = "00000000-0000-0000-0000-000000000001"
            context.getSharedPreferences("teledrive-device-accounts", Context.MODE_PRIVATE).edit()
                .putString("profiles", """[{"id":"$workId","label":"Work profile"}]""").commit()
            DeviceAccounts(context).rememberIdentity(workId, "Jordan Lee", "jordan")
            activity.get().setContent {
                MaterialTheme {
                    AccountProfilesSetting(compact = false, profileName = "Avery Quinn", username = "aquinn")
                }
            }

            compose.onNodeWithContentDescription("Switch account").performClick()
            compose.onNodeWithText("Avery Quinn").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithText("@aquinn · Active").assertIsDisplayed()
            compose.onNodeWithText("Jordan Lee").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("@jordan").assertIsDisplayed()
            compose.onNodeWithText("Add account").assertHasClickAction()
            compose.onNode(hasSetTextAction()).assertDoesNotExist()
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test fun addAccountSwitchesDirectlyAndStoresTelegramIdentityWhenItArrives() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val context = activity.get()
            if (runCatching { WorkManager.getInstance(context) }.isFailure) {
                WorkManager.initialize(context, Configuration.Builder().build())
            }
            val accounts = DeviceAccounts(context)
            activity.get().setContent { MaterialTheme { AccountProfilesSetting(compact = false) } }
            compose.onNodeWithContentDescription("Switch account").performClick()
            compose.onNodeWithText("Add account").performClick()
            compose.waitUntil(10_000) { accounts.selected() != DeviceAccounts.PRIMARY }
            compose.waitUntil(5_000) { shadowOf(context).peekNextStartedActivity() != null }
            val selected = accounts.selected()
            assertEquals(AccountSwitchActivity::class.java.name, shadowOf(context).nextStartedActivity.component?.className)
            assertEquals("", accounts.profiles().single { it.id == selected }.label)
            accounts.rememberIdentity(selected, "Avery Quinn", "aquinn")
            assertEquals("Avery Quinn", DeviceAccounts(context).profiles().single { it.id == selected }.label)
            assertEquals("aquinn", DeviceAccounts(context).profiles().single { it.id == selected }.username)
        } finally {
            activity.pause().stop().destroy()
        }
    }

}

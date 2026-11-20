package app.tgdrive.rebuild

import android.app.Application
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
import com.infinity.drive.core.accounts.DeviceAccounts
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
            DeviceAccounts(activity.get()).create("Work profile")
            activity.get().setContent {
                MaterialTheme {
                    AccountProfilesSetting(compact = false, profileName = "Avery Quinn", username = "aquinn")
                }
            }

            compose.onNodeWithContentDescription("Switch account").performClick()
            compose.onNodeWithText("Avery Quinn").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithText("@aquinn · Active").assertIsDisplayed()
            compose.onNodeWithText("Work profile").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("Add account").performClick()
            compose.onNodeWithText("Account label").assertIsDisplayed()
        } finally {
            activity.pause().stop().destroy()
        }
    }
}

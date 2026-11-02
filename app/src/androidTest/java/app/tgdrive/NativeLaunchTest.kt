package app.tgdrive

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeLaunchTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualActivityStartsWithPrivateChannelOnboarding() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("TeleDrive").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Your files live in your own private Telegram channel. Your account, your storage, your control.").assertIsDisplayed()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("API access").assertIsDisplayed()
        compose.onNodeWithText("API ID").assertIsDisplayed()
        compose.onNodeWithText("API hash").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }
}

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
        compose.onNodeWithContentDescription("TeleDrive app icon").assertIsDisplayed()
        compose.onNodeWithText("Your files, together. Powered by Telegram.").assertIsDisplayed()
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("API access").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("API ID").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue",substring=false).assertIsNotEnabled()
        val apiInput=compose.onNode(hasSetTextAction() and hasText("API ID"))
        apiInput.performScrollTo().performClick().performTextInput("1")
        apiInput.assertTextContains("1")
        compose.onNodeWithContentDescription("paste",ignoreCase=true).assertDoesNotExist()
        compose.waitUntil(15_000) {
            var visible=false
            compose.runOnIdle {
                val insets=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                visible=insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true
            }
            visible
        }

        // The automatic keyboard resizes the scroll viewport after the form opens.
        // Exercise scrolling to the second input once the IME is visible.
        val apiHashInput = compose.onNode(hasSetTextAction() and hasText("API hash"))
        compose.waitUntil(5_000) {
            apiHashInput.performScrollTo().isDisplayed()
        }
    }
}

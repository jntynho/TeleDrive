package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.infinity.drive.core.update.AppRelease
import com.infinity.drive.presentation.components.UpdateSheet
import com.infinity.drive.presentation.platform.AppUpdateInstallResult
import com.infinity.drive.presentation.platform.AppUpdateInstaller
import com.infinity.drive.presentation.platform.LocalAppUpdateInstaller
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
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
class UpdateSheetRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val release = AppRelease("1.5.6", "Release notes", "https://github.com/jntynho/TeleDrive/releases/tag/v1.5.6")

    @Test fun laterKeepsTheUpdateNoticeMountedUntilItsExitMotionFinishes() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        var visible by mutableStateOf(true)
        try {
            activity.get().setContent {
                DriveInfinityTheme {
                    if (visible) UpdateSheet(
                        release = release,
                        currentVersion = "1.5.5",
                        onOpenUrl = {},
                        onDismiss = { visible = false }
                    )
                }
            }
            compose.onNodeWithText("Later").assertExists()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Later").performClick()
            assertTrue("Closing should retain the sheet until the exit animation finishes", visible)
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.autoAdvance = true
            compose.waitUntil(3000) { !visible }
        } finally {
            compose.mainClock.autoAdvance = true
            activity.pause().stop().destroy()
        }
    }

    @Test fun installUsesTheInAppActionAndReportsProgressAndRequiredPermission() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val permissionResult = CompletableDeferred<AppUpdateInstallResult>()
        val installer = object : AppUpdateInstaller {
            override fun supports(release: AppRelease) = true
            override suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): AppUpdateInstallResult {
                onProgress(.42f)
                return permissionResult.await()
            }
        }
        try {
            activity.get().setContent {
                DriveInfinityTheme {
                    CompositionLocalProvider(LocalAppUpdateInstaller provides installer) {
                        UpdateSheet(release, "1.5.5", { error("Installing must use the Android action") }, {})
                    }
                }
            }
            compose.onNodeWithText("Install update").performClick()
            compose.onNodeWithText("Downloading update…").assertIsDisplayed()
            compose.onNodeWithText("Install update").assertIsNotEnabled()
            permissionResult.complete(AppUpdateInstallResult.PermissionRequired)
            compose.onNodeWithText("Allow TeleDrive to install", substring = true).assertIsDisplayed()
        } finally {
            permissionResult.cancel()
            activity.pause().stop().destroy()
        }
    }
}

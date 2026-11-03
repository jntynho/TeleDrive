package app.tgdrive.rebuild

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.components.ChoiceDialog
import com.infinity.drive.presentation.components.ErrorState
import com.infinity.drive.presentation.preview.*
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class UiComponentRegressionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var controller:ActivityController<ComponentActivity>
    @Before fun start(){controller=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()}
    @After fun stop(){controller.pause().stop().destroy()}

    @Test fun errorActionRemainsReachableInAShortWindowWithLargeText(){
        var retried=false
        controller.get().setContent {
            DriveInfinityTheme {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){
                    Box(Modifier.size(320.dp,180.dp)){
                        ErrorState("The library could not be loaded. Check your connection and try again.",onRetry={retried=true})
                    }
                }
            }
        }
        compose.onNodeWithText("Retry",substring=false).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test fun longChoiceDialogCanReachItsLastOption(){
        var chosen=-1
        controller.get().setContent{DriveInfinityTheme{
            ChoiceDialog("Choose",(0..30).map{it to "Option $it"},0,{chosen=it},{})
        }}
        compose.onNodeWithText("Option 30",substring=false).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(30,chosen)
    }

    @Test fun emptyStateInsideALazyListUsesTheOuterScrollContainer(){
        controller.get().setContent{DriveInfinityTheme{
            androidx.compose.foundation.lazy.LazyColumn {
                item {com.infinity.drive.presentation.components.EmptyState(app.tgdrive.ui.DriveIcons.Info,"No saved proxies","Add a proxy when you need one.")}
            }
        }}
        compose.onNodeWithText("No saved proxies",substring=false).assertIsDisplayed()
    }

    @Test fun closingSearchBeforeFocusDelayDoesNotTargetADetachedField(){
        var expanded by mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        controller.get().setContent{DriveInfinityTheme{
            app.tgdrive.ui.ExpandableLibraryHeader("Gallery","",{},expanded,{expanded=it},"Search gallery")
        }}
        compose.mainClock.advanceTimeBy(64)
        compose.runOnUiThread{expanded=false}
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithContentDescription("Search gallery").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun viewerOwnsOrientationAcrossPageChangesAndRestoresItOnExit(){
        val original=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        controller.get().requestedOrientation=original
        var visible by mutableStateOf(true)
        var page by mutableStateOf(0)
        lateinit var owner:VideoOrientationState
        controller.get().setContent{
            if(visible)WithVideoOrientation{
                owner=LocalVideoOrientation.current!!
                key(page){Text("Page $page")}
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread{owner.select(PlayerRotation.LANDSCAPE)}
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,controller.get().requestedOrientation)
        compose.runOnUiThread{page++}
        compose.onNodeWithText("Page 1",substring=false).assertIsDisplayed()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,controller.get().requestedOrientation)
        compose.runOnUiThread{visible=false}
        compose.waitForIdle()
        assertEquals(original,controller.get().requestedOrientation)
    }
}

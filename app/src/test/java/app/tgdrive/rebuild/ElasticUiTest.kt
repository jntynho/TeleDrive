package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ElasticUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    @Before fun start(){activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()}
    @After fun stop(){activity.pause().stop().destroy()}

    @Test fun nearEdgeStretchSettlesWithoutMovingTheLogicalScrollPosition(){
        lateinit var state:ScrollState
        activity.get().setContent{DriveTheme{
            state=rememberScrollState()
            Box(Modifier.size(300.dp,300.dp)) {
                Column(Modifier.fillMaxSize().testTag("scroll").elasticVerticalScroll(state)) {
                    repeat(15){Text("Row $it",Modifier.fillMaxWidth().height(60.dp).testTag("row-$it"))}
                }
            }
        }}
        compose.waitForIdle()
        compose.runOnUiThread{runBlocking{state.scrollTo(state.maxValue-64)}}
        compose.onNodeWithTag("scroll").performTouchInput{
            down(center);moveBy(Offset(0f,-38f),200)
        }
        compose.waitForIdle()
        assertTrue(state.value<state.maxValue)
        val position=state.value
        val stretched=compose.onNodeWithTag("row-14").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("scroll").performTouchInput{cancel()}
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        assertEquals(position,state.value)
        val settled=compose.onNodeWithTag("row-14").getUnclippedBoundsInRoot()
        assertTrue("Approach should change draw geometry before the end",kotlin.math.abs(stretched.top.value-settled.top.value)>.05f)
        compose.onNodeWithTag("scroll").performTouchInput{swipeUp()}
        compose.waitForIdle()
        assertEquals(state.maxValue,state.value)
    }

    @Test fun gridKeepsFirstAndLastItemsReachableAfterRepeatedBoundaryPulls(){
        activity.get().setContent{DriveTheme{
            ElasticLazyVerticalGrid(GridCells.Fixed(3),Modifier.size(300.dp,300.dp).testTag("grid")) {
                items(120,key={it}){Text("Cell $it",Modifier.height(80.dp))}
            }
        }}
        compose.onNodeWithTag("grid").performScrollToIndex(119)
        compose.onNodeWithText("Cell 119",substring=false).assertIsDisplayed()
        repeat(3){compose.onNodeWithTag("grid").performTouchInput{swipeUp()}}
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithText("Cell 119",substring=false).assertIsDisplayed()
        compose.onNodeWithTag("grid").performScrollToIndex(0)
        repeat(3){compose.onNodeWithTag("grid").performTouchInput{swipeDown()}}
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithText("Cell 0",substring=false).assertIsDisplayed()
    }
}

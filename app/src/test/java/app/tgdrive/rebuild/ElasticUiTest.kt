package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
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

    @Test fun aHeldBoundaryPullRemainsSteadyUntilTheFingerIsReleased(){
        lateinit var state:ScrollState
        activity.get().setContent{DriveTheme{
            state=rememberScrollState()
            Column(Modifier.size(300.dp,300.dp).testTag("held-scroll").elasticVerticalScroll(state)){
                repeat(15){Text("Held row $it",Modifier.fillMaxWidth().height(60.dp).testTag("held-$it"))}
            }
        }}
        compose.waitForIdle()
        compose.runOnUiThread{runBlocking{state.scrollTo(state.maxValue)}}
        compose.onNodeWithTag("held-scroll").performTouchInput{down(center);moveBy(Offset(0f,-90f),200)}
        compose.waitForIdle()
        val before=compose.onNodeWithTag("held-14").getUnclippedBoundsInRoot()
        compose.mainClock.advanceTimeBy(1200)
        val held=compose.onNodeWithTag("held-14").getUnclippedBoundsInRoot()
        assertEquals(before.top.value,held.top.value,.5f)
        assertEquals(before.bottom.value,held.bottom.value,.5f)
        compose.onNodeWithTag("held-scroll").performTouchInput{up()}
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        assertEquals(state.maxValue,state.value)
    }

    @Test fun tappingDuringReturnResumesSettlingWhenTheFingerLifts(){
        lateinit var state:ScrollState
        activity.get().setContent{DriveTheme{
            state=rememberScrollState()
            Column(Modifier.size(300.dp,300.dp).testTag("tap-scroll").elasticVerticalScroll(state)){
                repeat(15){Text("Tap row $it",Modifier.fillMaxWidth().height(60.dp).testTag("tap-$it"))}
            }
        }}
        compose.waitForIdle()
        compose.runOnUiThread{runBlocking{state.scrollTo(state.maxValue)}}
        val baseline=compose.onNodeWithTag("tap-14").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("tap-scroll").performTouchInput{down(center);moveBy(Offset(0f,-100f),200);up()}
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("tap-scroll").performTouchInput{down(center)}
        val held=compose.onNodeWithTag("tap-14").getUnclippedBoundsInRoot()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(held.top.value,compose.onNodeWithTag("tap-14").getUnclippedBoundsInRoot().top.value,.5f)
        compose.onNodeWithTag("tap-scroll").performTouchInput{up()}
        compose.mainClock.advanceTimeBy(1600)
        val settled=compose.onNodeWithTag("tap-14").getUnclippedBoundsInRoot()
        assertEquals(baseline.top.value,settled.top.value,.5f)
        assertEquals(state.maxValue,state.value)
    }

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
        assertTrue("Approach should change draw geometry before the end: stretched=$stretched, settled=$settled, scroll=$position",kotlin.math.abs(stretched.top.value-settled.top.value)>.05f)
        compose.onNodeWithTag("scroll").performTouchInput{swipeUp()}
        compose.waitForIdle()
        assertEquals(state.maxValue,state.value)
    }

    @Test fun fileMoreAndSwitchClicksAreDeliveredOnceDuringReturn(){
        lateinit var state:ScrollState
        var files=0;var more=0;var enabled=false
        activity.get().setContent{DriveTheme{
            state=rememberScrollState()
            var checked by remember{mutableStateOf(false)}
            Column(Modifier.size(300.dp,300.dp).testTag("click-scroll").elasticVerticalScroll(state)){
                repeat(15){index->Row(Modifier.fillMaxWidth().height(60.dp)){
                    Text("File $index",Modifier.weight(1f).fillMaxHeight().testTag("file-$index").clickable{files++})
                    IconButton({more++},Modifier.testTag("more-$index")){Text("More")}
                    DriveSwitch(checked,{checked=it;enabled=it},Modifier.testTag("switch-$index"))
                }}
            }
        }}
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        fun pull(){
            compose.runOnUiThread{runBlocking{state.scrollTo(state.maxValue)}}
            compose.onNodeWithTag("click-scroll").performTouchInput{
                down(center);moveBy(Offset(0f,-90f),200);advanceEventTime(300);up()
            }
            compose.mainClock.advanceTimeBy(80)
        }
        pull();compose.onNodeWithTag("file-14").performTouchInput{click()}
        compose.runOnIdle{assertEquals(1,files)}
        pull();compose.onNodeWithTag("more-14").performTouchInput{click()}
        compose.runOnIdle{assertEquals(1,more)}
        pull();compose.onNodeWithTag("switch-14").performTouchInput{click()}
        compose.runOnIdle{assertTrue(enabled);assertEquals(1,files);assertEquals(1,more)}
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithTag("more-14").performTouchInput{click()}
        compose.runOnIdle{assertEquals(2,more)}
    }

    @Test fun nearEdgeAnticipationDoesNotStealATapOrALongPress(){
        lateinit var state:ScrollState
        var clicks=0;var holds=0
        activity.get().setContent{DriveTheme{
            state=rememberScrollState()
            Column(Modifier.size(300.dp,300.dp).testTag("near-scroll").elasticVerticalScroll(state)){
                repeat(15){Text("Near $it",Modifier.fillMaxWidth().height(60.dp).testTag("near-$it")
                    .combinedClickable(onClick={clicks++},onLongClick={holds++}))}
            }
        }}
        compose.waitForIdle();compose.mainClock.autoAdvance=false
        compose.runOnUiThread{runBlocking{state.scrollTo(state.maxValue-64)}}
        compose.onNodeWithTag("near-scroll").performTouchInput{down(center);moveBy(Offset(0f,-40f),120);moveBy(Offset.Zero,120);up()}
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("near-13").performTouchInput{click()}
        compose.runOnIdle{assertEquals(1,clicks);assertEquals("Before hold",0,holds)}
        compose.onNodeWithTag("near-13").performTouchInput{down(center)}
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("near-13").performTouchInput{advanceEventTime(700);up()}
        compose.runOnIdle{assertEquals("Long presses",1,holds);assertEquals("Clicks after long press",1,clicks)}
    }

    @Test fun pullToRefreshKeepsOwnershipOfTheTopGesture(){
        var refreshed=0
        val capabilities=object:com.infinity.drive.presentation.platform.PlatformCapabilities{
            override val supportsAutoBackup=false
            override val requiresPermissions=false
            override val supportsPullToRefresh=true
            override val supportsAppLock=false
            override val supportsScreenCaptureBlocking=false
            override val supportsDynamicColor=false
        }
        activity.get().setContent{DriveTheme{
            CompositionLocalProvider(com.infinity.drive.presentation.platform.LocalPlatformCapabilities provides capabilities){
                com.infinity.drive.presentation.components.RefreshableContent(false,{refreshed++},Modifier.size(300.dp,300.dp)){
                    ElasticLazyColumn(Modifier.fillMaxSize().testTag("refresh-scroll")){
                        items(15){Text("Refresh $it",Modifier.fillMaxWidth().height(60.dp))}
                    }
                }
            }
        }}
        compose.onNodeWithTag("refresh-scroll").performTouchInput{
            down(Offset(center.x,70f));moveBy(Offset(0f,400f),600);up()
        }
        compose.waitForIdle()
        compose.runOnIdle{assertEquals(1,refreshed)}
        compose.onNodeWithText("Refresh 0").assertIsDisplayed()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun aSheetActionStillClosesTheSheetAfterABoundaryPull(){
        var chosen=0
        activity.get().setContent{DriveTheme{
            var shown by remember{mutableStateOf(true)}
            if(shown)com.infinity.drive.presentation.components.DriveBottomSheet(onDismissRequest={shown=false}){
                val closeThen=com.infinity.drive.presentation.components.LocalSheetCloseThen.current
                Column(Modifier.height(260.dp).testTag("sheet-scroll").elasticVerticalScroll(rememberScrollState())){
                    repeat(15){index->
                        com.infinity.drive.presentation.components.SheetActionRow("Action $index",DriveIcons.Info){closeThen{chosen++}}
                    }
                }
            }
        }}
        compose.onNodeWithTag("sheet-scroll").performScrollToNode(hasText("Action 14"))
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("sheet-scroll").performTouchInput{
            down(center);moveBy(Offset(0f,-90f),200);advanceEventTime(300);up()
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("Action 14").performTouchInput{click()}
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        compose.runOnIdle{assertEquals(1,chosen)}
        compose.onNodeWithTag("sheet-scroll").assertDoesNotExist()
    }

    @Test fun replacingTheListStateDropsOldDeformationAndKeepsClicksWorking(){
        var version by mutableIntStateOf(0)
        var clicks=0
        lateinit var state:androidx.compose.foundation.lazy.LazyListState
        activity.get().setContent{DriveTheme{
            state=remember(version){androidx.compose.foundation.lazy.LazyListState()}
            ElasticLazyColumn(Modifier.size(300.dp,300.dp).testTag("replaced-list"),state=state){
                items(15){index->Text("Version $version row $index",Modifier.fillMaxWidth().height(60.dp).testTag("replaced-$index").clickable{clicks++})}
            }
        }}
        compose.onNodeWithTag("replaced-list").performScrollToIndex(14)
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("replaced-list").performTouchInput{down(center);moveBy(Offset(0f,-90f),200);up()}
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle{version++}
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Version 1 row 0").assertIsDisplayed()
        assertEquals(0f,compose.onNodeWithTag("replaced-0").getUnclippedBoundsInRoot().top.value,.5f)
        compose.onNodeWithTag("replaced-0").performTouchInput{click()}
        compose.runOnIdle{assertEquals(1,clicks)}
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

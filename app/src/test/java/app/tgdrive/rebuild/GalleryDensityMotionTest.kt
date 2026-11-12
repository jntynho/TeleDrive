package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryDensityMotionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private var columns by mutableIntStateOf(3)
    private lateinit var motion:GridDensityMotion
    private var opened:String?=null
    private var viewportHeight by mutableStateOf(500.dp)
    @Before fun setup(){
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            val grid=rememberLazyGridState()
            motion=rememberGridDensityMotion(grid,"sample")
            val items=remember{List(80){DensityItem("photo-$it")}}
            LazyVerticalGrid(GridCells.Fixed(columns),state=grid,userScrollEnabled=!motion.busy,
                modifier=Modifier.size(360.dp,viewportHeight).testTag("grid").pinchZoom(
                    {columns=(columns-1).coerceAtLeast(2)},{columns=(columns+1).coerceAtMost(6)},
                    onGestureStarted={motion.begin(grid,columns,items,it,0f,.8f)},
                    onGestureProgress=motion::preview,onGestureFinished={motion.finish(grid,it){columns}},
                    columns=columns,canZoomIn=columns>2,canZoomOut=columns<6,commitAllowed={motion.dragging})){
                items(items,key={it.key}){item->
                    Box(Modifier.fillMaxWidth().aspectRatio(.8f).then(motion.cell(item.key)).testTag(item.key)
                        .clickable{opened=item.key}.background(Color(0xff365b69))){Text(item.key,color=Color.White)}
                }
            }
        }
        compose.waitForIdle()
    }
    @After fun close(){activity.pause().stop().destroy()}
    private fun pinch(expand:Boolean){compose.onNodeWithTag("grid").performTouchInput{
        down(0,center.copy(x=center.x-50));down(1,center.copy(x=center.x+50))
        val span=if(expand)100f else 20f
        moveTo(0,center.copy(x=center.x-span));moveTo(1,center.copy(x=center.x+span));up(0);up(1)
    };compose.waitForIdle()}
    @Test fun repeatedReflowsSettleAndLeaveCorrectCellsClickable(){
        repeat(3){
            pinch(true);assertEquals(2,columns);assertFalse(motion.busy)
            pinch(false);assertEquals(3,columns);assertFalse(motion.busy)
        }
        compose.onNodeWithTag("photo-0").performScrollTo().performClick()
        assertEquals("photo-0",opened)
    }
    @Test fun liftingOneFingerCommitsWithoutWaitingForTheOther(){
        compose.onNodeWithTag("grid").performTouchInput{
            down(0,center.copy(x=center.x-50));down(1,center.copy(x=center.x+50))
            moveTo(0,center.copy(x=center.x-100));moveTo(1,center.copy(x=center.x+100));up(0)
        }
        compose.runOnIdle{assertEquals(2,columns)}
        compose.onNodeWithTag("grid").performTouchInput{up(1)}
        compose.waitForIdle();assertFalse(motion.busy)
    }
    @Test fun originalFocalPointSurvivesReflow(){
        val old=compose.onNodeWithTag("photo-4").getUnclippedBoundsInRoot()
        val fraction=((250f-old.top.value)/(old.bottom.value-old.top.value)).coerceIn(0f,1f)
        pinch(true);assertEquals(2,columns)
        val next=compose.onNodeWithTag("photo-4").getUnclippedBoundsInRoot()
        assertEquals(250f,next.top.value+(next.bottom.value-next.top.value)*fraction,1.5f)
    }
    @Test fun resizingDuringAPinchCancelsItsPendingCommitAndReleasesInput(){
        compose.onNodeWithTag("grid").performTouchInput{
            down(0,center.copy(x=center.x-50));down(1,center.copy(x=center.x+50))
            moveTo(0,center.copy(x=center.x-100));moveTo(1,center.copy(x=center.x+100))
        }
        compose.runOnIdle{viewportHeight=420.dp}
        compose.waitForIdle()
        compose.onNodeWithTag("grid").performTouchInput{up(0);up(1)}
        compose.waitForIdle();assertEquals(3,columns);assertFalse(motion.busy)
        compose.onNodeWithTag("grid").performScrollToIndex(0)
        compose.onNodeWithTag("photo-0").performClick();assertEquals("photo-0",opened)
    }

}

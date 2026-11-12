package app.tgdrive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infinity.drive.presentation.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Runs the real lazy-grid measurement, transformed hit targets and pointer pipeline on Android. */
@RunWith(AndroidJUnit4::class)
class GalleryDensityDeviceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun pinchCommitsOnFirstReleaseAndKeepsTheVisibleFileClickable(){
        var columns by mutableIntStateOf(3)
        var opened:String?=null
        lateinit var motion:GridDensityMotion
        compose.setContent {
            val grid=rememberLazyGridState()
            motion=rememberGridDensityMotion(grid,"device")
            val rows=remember{List(90){DensityItem("photo-$it")}}
            LazyVerticalGrid(GridCells.Fixed(columns),state=grid,userScrollEnabled=!motion.busy,
                modifier=Modifier.size(360.dp,500.dp).then(motion.fade()).testTag("density-grid").pinchZoom(
                    {columns=(columns-1).coerceAtLeast(2)},{columns=(columns+1).coerceAtMost(6)},
                    onGestureStarted={motion.begin(columns,rows,it)},
                    onDensityCommit={step->motion.release(step,{columns}){columns=(columns-step).coerceIn(2,6)}},
                    onGestureFinished={if(!it)motion.cancel()},columns=columns,commitAllowed={motion.dragging},enabled=!motion.transitioning)){
                items(rows,key={it.key}){row->
                    Box(Modifier.fillMaxWidth().aspectRatio(.8f).testTag(row.key)
                        .clickable(enabled=motion.acceptsInput){opened=row.key}.background(Color.DarkGray)){Text(row.key,color=Color.White)}
                }
            }
        }
        compose.onNodeWithTag("density-grid").performTouchInput{
            down(0,center.copy(x=center.x-50));down(1,center.copy(x=center.x+50))
            moveTo(0,center.copy(x=center.x-110));moveTo(1,center.copy(x=center.x+110));up(0)
        }
        compose.runOnIdle{assertEquals(2,columns)}
        compose.onNodeWithTag("density-grid").performTouchInput{up(1)}
        compose.waitForIdle()
        compose.runOnIdle{assertFalse(motion.busy)}
        compose.onNodeWithTag("density-grid").performScrollToIndex(0)
        compose.onNodeWithTag("photo-0").performClick()
        compose.runOnIdle{assertEquals("photo-0",opened)}
    }
}

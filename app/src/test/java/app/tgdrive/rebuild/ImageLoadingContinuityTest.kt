package app.tgdrive.rebuild

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.preview.ZoomableImage
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ImageLoadingContinuityTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    @After fun close(){if(::activity.isInitialized)activity.pause().stop().destroy()}
    @Test fun anIntermediateLoadingStateRetainsTheOriginalUntilItsReplacementIsReady(){
        fun image(color:Int)=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(color)}
        val red=image(Color.RED);val blue=image(Color.BLUE);val green=image(Color.GREEN)
        var model by mutableStateOf<Any?>(red)
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent{MaterialTheme{ZoomableImage(model,"Preview",Modifier.size(200.dp).testTag("preview-image"),contentKey="same-photo",previewModel=blue)}}
        fun center():Int {val bitmap=compose.onNodeWithTag("preview-image").captureToImage().asAndroidBitmap();return bitmap.getPixel(bitmap.width/2,bitmap.height/2)}
        compose.waitUntil(10000){center()==Color.RED}
        compose.runOnIdle{model=null}
        compose.mainClock.advanceTimeBy(400)
        assertEquals(Color.RED,center())
        compose.runOnIdle{model=green}
        compose.waitUntil(10000){center()==Color.GREEN}
    }
}

package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import app.tgdrive.ui.DriveTheme
import com.infinity.drive.presentation.navigation.NavigationTransitions
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*

/** Check real navigation frames rather than only the transition's configuration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationMotionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    @After fun stop(){if(::activity.isInitialized)activity.pause().stop().destroy()}
    @Test fun horizontalNavigationKeepsHeightStableAndMovesMonotonically(){
        lateinit var nav:NavHostController
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent{DriveTheme{
            nav=rememberNavController()
            NavHost(nav,"gallery",Modifier.fillMaxSize(),
                enterTransition={NavigationTransitions.enter()},exitTransition={NavigationTransitions.exit()},
                popEnterTransition={NavigationTransitions.enter(false)},popExitTransition={NavigationTransitions.exit(false)},
                sizeTransform={SizeTransform(clip=true){_,_->snap()}}){
                composable("gallery"){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Gallery content")}}
                composable("audio"){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Audio content")}}
            }
        }}
        compose.waitForIdle()
        val anchor=compose.onNodeWithText("Gallery content").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance=false
        fun sample(label:String,forward:Boolean){
            var previous=if(forward)Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
            repeat(6){
                compose.mainClock.advanceTimeBy(80)
                val center=compose.onNodeWithText(label).getUnclippedBoundsInRoot()
                assertEquals((anchor.top.value+anchor.bottom.value)/2,(center.top.value+center.bottom.value)/2,.5f)
                assertTrue(if(forward)(center.left.value+center.right.value)/2<=previous+.5f else (center.left.value+center.right.value)/2>=previous-.5f)
                previous=(center.left.value+center.right.value)/2
            }
            assertEquals((anchor.left.value+anchor.right.value)/2,previous,.5f)
        }
        compose.runOnUiThread{nav.navigate("audio")}
        compose.mainClock.advanceTimeBy(32)
        sample("Audio content",true)
        compose.runOnUiThread{nav.popBackStack()}
        compose.mainClock.advanceTimeBy(32)
        sample("Gallery content",false)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("Audio content").assertDoesNotExist()
    }
}

package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import app.tgdrive.ui.DriveTheme
import com.infinity.drive.presentation.navigation.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationQueueRegressionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private lateinit var nav:NavHostController
    @Before fun start(){
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent{DriveTheme{
            nav=rememberNavController()
            NavigationCoordinator(nav)
            NavHost(nav,Route.Home,Modifier.fillMaxSize(),
                enterTransition={NavigationTransitions.enter()},exitTransition={NavigationTransitions.exit()},
                sizeTransform={SizeTransform(clip=true){_,_->snap()}}){
                composable<Route.Home>{Text("Home")}
                composable<Route.Gallery>{Text("Gallery")}
                composable<Route.Audio>{Text("Audio")}
                composable<Route.Settings>{Text("Settings")}
            }
        }}
        compose.waitForIdle()
    }
    @After fun stop(){activity.pause().stop().destroy()}
    private fun top(route:Route)=nav.navigateTopLevelOnce(route){popUpTo(Route.Home){saveState=true};restoreState=true;launchSingleTop=true}
    @Test fun latestTabTapDuringAnUnfinishedTransitionIsDelivered(){
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{top(Route.Gallery())}
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread{top(Route.Audio(true));top(Route.Settings)}
        compose.mainClock.autoAdvance=true
        compose.waitForIdle()
        compose.waitUntil(5000){nav.currentDestination?.hasRoute<Route.Settings>()==true}
        compose.waitForIdle()
        assertTrue(nav.currentDestination!!.hasRoute<Route.Settings>())
    }
    @Test fun aSecondOpenOfTheSameRouteDoesNotStackAnotherViewer(){
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{nav.navigateOnce(Route.Gallery());nav.navigateOnce(Route.Gallery())}
        compose.mainClock.autoAdvance=true
        compose.waitForIdle()
        compose.runOnUiThread{nav.popBackStackOnce()}
        compose.waitForIdle()
        assertTrue(nav.currentDestination!!.hasRoute<Route.Home>())
    }
}

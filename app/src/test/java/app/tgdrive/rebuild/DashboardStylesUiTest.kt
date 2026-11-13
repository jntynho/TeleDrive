package app.tgdrive.rebuild

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.*
import com.infinity.drive.domain.model.DashboardStyle
import com.infinity.drive.presentation.settings.SettingsSwitchRow
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DashboardStylesUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    @After fun close(){if(::activity.isInitialized)activity.pause().stop().destroy()}
    private fun show(content:@Composable ()->Unit){
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent{DriveInfinityTheme{Surface(Modifier.fillMaxSize()){content()}}}
    }
    private fun capture(name:String){
        val file=File("build/design-previews/$name.png").apply{parentFile?.mkdirs()}
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun everyDashboardShowsTheSameRealTotalsAndDoesNotImplyACapacityQuota(){
        var style by mutableStateOf(DashboardStyle.CLASSIC)
        val usage=LibraryOverview(1284,26_628_797_235,listOf(9_450_000_000,11_350_000_000,3_200_000_000,2_628_797_235))
        show{Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)){
            Text(style.label,style=MaterialTheme.typography.headlineMedium)
            LibraryDashboard(usage,{},showAddAction=false,updatedLabel="Updated just now",style=style.name)
        }}
        DashboardStyle.entries.forEach{choice->
            compose.runOnIdle{style=choice}
            compose.onNodeWithText("24.8 GB",substring=false).assertIsDisplayed()
            compose.onNodeWithText("1284 files",substring=false).assertIsDisplayed()
            compose.onNodeWithText("%",substring=true).assertDoesNotExist()
            capture("dashboard-${choice.name.lowercase()}")
        }
    }
    @Test fun repeatedSwitchChangesKeepTheTouchTargetAndRowStable(){
        var checked by mutableStateOf(false)
        show{Column(Modifier.padding(16.dp)){SettingsSwitchRow("Debug logging",checked,{checked=it})}}
        val node=compose.onNode(hasText("Debug logging") and isToggleable())
        val bounds=node.fetchSemanticsNode().boundsInRoot
        repeat(8){
            node.performClick()
            compose.mainClock.advanceTimeBy(80)
            assertEquals(bounds,node.fetchSemanticsNode().boundsInRoot)
            if(it%2==0)node.assertIsOn()else node.assertIsOff()
        }
        compose.mainClock.advanceTimeBy(1000)
        node.assertIsOff();assertEquals(bounds,node.fetchSemanticsNode().boundsInRoot)
    }
}

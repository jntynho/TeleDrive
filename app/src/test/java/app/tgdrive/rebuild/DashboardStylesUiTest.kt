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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.*
import com.infinity.drive.domain.model.DashboardStyle
import com.infinity.drive.presentation.settings.SettingsSwitchRow
import com.infinity.drive.presentation.settings.DashboardStyleOption
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
    @Test fun allSixFramesStayEqualAcrossLightDarkLoadingAndEmptyData(){
        var style by mutableStateOf(DashboardStyle.CLASSIC)
        var dark by mutableStateOf(false)
        var loading by mutableStateOf(false)
        var empty by mutableStateOf(false)
        show{Column(Modifier.padding(24.dp)){
            LibraryDashboard(if(empty)LibraryOverview(0,0,emptyList())else LibraryOverview(12,2048,listOf(1024,1024,0,0)),{},
                showAddAction=false,style=style.name,dark=dark,loading=loading)
        }}
        val bounds=compose.onNodeWithTag("library-card").fetchSemanticsNode().boundsInRoot
        DashboardStyle.entries.forEach{choice->
            listOf(false,true).forEach{tone->
                compose.runOnIdle{style=choice;dark=tone;loading=false;empty=false}
                assertEquals(bounds,compose.onNodeWithTag("library-card").fetchSemanticsNode().boundsInRoot)
                capture("dashboard-${choice.name.lowercase()}-${if(tone)"dark"else"light"}")
                compose.runOnIdle{loading=true}
                assertEquals(bounds,compose.onNodeWithTag("library-card").fetchSemanticsNode().boundsInRoot)
                compose.runOnIdle{loading=false;empty=true}
                assertEquals(bounds,compose.onNodeWithTag("library-card").fetchSemanticsNode().boundsInRoot)
            }
        }
    }
    @Test fun tonePreviewDoesNotApplyUntilTheExplicitButtonIsPressed(){
        var savedDark by mutableStateOf(false)
        var active by mutableStateOf(false)
        var applies=0
        var style by mutableStateOf(DashboardStyle.CLASSIC)
        show{Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())){
            DashboardStyleOption(style,LibraryOverview(12,2048,listOf(1024,1024,0,0)),active,savedDark){savedDark=it;active=true;applies++}
        }}
        val bounds=compose.onNodeWithTag("dashboard-option-CLASSIC").fetchSemanticsNode().size
        DashboardStyle.entries.forEach{choice->
            compose.runOnIdle{style=choice}
            assertEquals(bounds,compose.onNodeWithTag("dashboard-option-${choice.name}").fetchSemanticsNode().size)
        }
        compose.runOnIdle{style=DashboardStyle.CLASSIC}
        compose.onNodeWithTag("dashboard-tone-CLASSIC-Dark").performScrollTo().performClick()
        assertEquals(0,applies);assertFalse(savedDark)
        compose.onNodeWithTag("apply-dashboard-CLASSIC").performScrollTo().performClick()
        assertEquals(1,applies);assertTrue(savedDark)
        compose.onNodeWithTag("apply-dashboard-CLASSIC").assertIsNotEnabled().assertIsSelected()
        compose.onNodeWithTag("dashboard-tone-CLASSIC-Light").performScrollTo().performClick()
        assertEquals(1,applies)
        compose.onNodeWithTag("apply-dashboard-CLASSIC").assertIsEnabled()
        capture("dashboard-style-option")
    }
    @Test @Config(qualifiers="w320dp-h1200dp-xhdpi")
    fun largeTextPreservesEqualFramesAndReadableTotalsForAllSixStyles(){
        var style by mutableStateOf(DashboardStyle.CLASSIC)
        show{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){
                Column(Modifier.padding(16.dp)){
                    LibraryDashboard(LibraryOverview(1284,26_628_797_235,listOf(9_450_000_000,11_350_000_000,3_200_000_000,2_628_797_235)),{},showAddAction=false,style=style.name)
                }
            }
        }
        val bounds=compose.onNodeWithTag("library-card").fetchSemanticsNode().boundsInRoot
        DashboardStyle.entries.forEach{choice->
            compose.runOnIdle{style=choice}
            assertEquals(bounds,compose.onNodeWithTag("library-card").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithText("24.8 GB",substring=false).assertIsDisplayed()
            compose.onNodeWithText("1284 files",substring=false).assertIsDisplayed()
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("24.8 GB",substring=false).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(layouts)}
            capture("dashboard-${choice.name.lowercase()}-large-text")
            // Check the visible line, rather than the paragraph's wider allocation rectangle.
            val clipped=layouts.any{it.didOverflowHeight||it.isLineEllipsized(0)||it.getLineEnd(0,visibleEnd=true)<it.layoutInput.text.length}
            assertFalse("Total must remain fully readable in ${choice.name}",clipped)
        }
    }
}

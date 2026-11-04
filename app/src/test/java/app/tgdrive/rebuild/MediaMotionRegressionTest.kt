package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.*
import app.tgdrive.ui.SlimSlider
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.theme.DriveInfinityTheme
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
class MediaMotionRegressionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    @Before fun start(){activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()}
    @After fun stop(){activity.pause().stop().destroy()}

    @Test fun sharedSliderDrawsACircularThumbOnTheTrackAxisAndKeepsNativeRtlSeeking(){
        var value by mutableFloatStateOf(.25f)
        var rtl by mutableStateOf(false)
        var density=1f
        activity.get().setContent{DriveInfinityTheme{
            density=androidx.compose.ui.platform.LocalDensity.current.density
            CompositionLocalProvider(LocalLayoutDirection provides if(rtl)LayoutDirection.Rtl else LayoutDirection.Ltr){
                Box(Modifier.width(320.dp).height(64.dp).background(Color.Black)){
                    SlimSlider(value,{value=it},Modifier.fillMaxWidth().testTag("slider"),trackHeight=2.dp,thumbSize=8.dp,
                        activeColor=Color.Red,inactiveColor=Color.DarkGray)
                }
            }
        }}
        for(reversed in listOf(false,true))for(fraction in listOf(.25f,.75f)){
            compose.runOnUiThread{rtl=reversed;value=fraction}
            val shot=compose.onNodeWithTag("slider").captureToImage()
            java.io.File("build/design-previews").mkdirs()
            java.io.File("build/design-previews/slider-$reversed-$fraction.png").outputStream().use{shot.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            val bitmap=shot.toPixelMap()
            // Slider semantics include a larger hit region; measure its drawn track, not that region.
            val spans=(0 until bitmap.width).map{x->(0 until bitmap.height).filter{val p=bitmap[x,it];p.red>.9f&&p.green<.1f}}
            val maxHeight=spans.maxOf{it.size}
            val centerX=spans.indices.filter{spans[it].size==maxHeight}.average().toFloat()
            val redYs=spans[centerX.toInt()]
            assertTrue("Thumb must remain a circle: drawn height=$maxHeight density=$density",maxHeight in (6*density).toInt()..(10*density).toInt())
            assertEquals("Thumb center must sit on the track center",bitmap.height/2f,(redYs.first()+redYs.last())/2f,1.5f)
            val track=(0 until bitmap.width).filter{val p=bitmap[it,bitmap.height/2];(p.red>.9f&&p.green<.1f)||(kotlin.math.abs(p.red-Color.DarkGray.red)<.02f&&kotlin.math.abs(p.green-Color.DarkGray.green)<.02f)}
            val expected=track.first()+(track.last()-track.first())*(if(reversed)1-fraction else fraction)
            assertEquals("Drawn travel must follow native RTL semantics",expected,centerX,2*density)
        }
        compose.onNodeWithTag("slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(.6f)}
        assertEquals(.6f,value,.001f)
        compose.onNodeWithTag("slider").performTouchInput{click(androidx.compose.ui.geometry.Offset(width*.25f,height*.5f))}
        assertEquals("Touch travel must follow RTL",.75f,value,.035f)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun nestedPagesKeepOneModalAndExplicitCloseWaitsForTheExitAnimation(){
        var visible by mutableStateOf(true)
        var page by mutableStateOf("actions")
        var dismissed=0
        activity.get().setContent{DriveInfinityTheme{
            if(visible)DriveBottomSheet(onDismissRequest={visible=false;dismissed++}){dismiss->
                DriveSheetPage(page){shown->
                    if(shown=="actions")TextButton({page="details"}){Text("Open details")}
                    else DriveBottomSheet(onDismissRequest={page="actions"}){back->
                        Column{Text("Details page");TextButton(back){Text("Go back")}}
                    }
                }
                TextButton(dismiss){Text("Close modal")}
            }
        }}
        compose.onNodeWithText("Open details").performClick()
        compose.onNodeWithText("Details page").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("Go back").performClick()
        compose.onNodeWithText("Open details").assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("Close modal").performClick()
        assertTrue("Do not dispose the window before its exit animation",visible)
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance=true
        compose.waitUntil(3000){!visible}
        assertEquals(1,dismissed)
    }
    @Test fun consecutiveDuplicateNamesAlwaysOpenANewUsableSheet(){
        fun conflict(name:String):com.infinity.drive.presentation.upload.UploadCoordinator.Conflict {
            val file=com.infinity.drive.domain.model.DriveFile(name,null,name,10,"application/pdf",com.infinity.drive.domain.model.FileCategory.DOCUMENT,null,null,null,null,null,null,
                com.infinity.drive.domain.model.BackupState.NONE,false,false,false,false,null,null,null,null,createdAt=1,modifiedAt=1,addedAt=1)
            return com.infinity.drive.presentation.upload.UploadCoordinator.Conflict(name,10,file,"My Drive","copy-$name",2,kotlinx.coroutines.CompletableDeferred())
        }
        var request by mutableStateOf<com.infinity.drive.presentation.upload.UploadCoordinator.Conflict?>(conflict("first.pdf"))
        var answers=0
        activity.get().setContent{DriveInfinityTheme{
            request?.let{com.infinity.drive.presentation.upload.UploadConflictSheet(it){answer->
                assertEquals(com.infinity.drive.presentation.upload.UploadCoordinator.Choice.SKIP,answer.choice)
                answers++
                // Deliberately omit a null frame: the next request can arrive immediately.
                request=if(answers==1)conflict("second.pdf")else null
            }}
        }}
        compose.onNodeWithText("Skip",substring=false).performScrollTo().performClick()
        compose.waitUntil(3000){answers==1}
        compose.onNodeWithText("“second.pdf” is already in My Drive").assertIsDisplayed()
        compose.onNodeWithText("Skip",substring=false).performScrollTo().performClick()
        compose.waitUntil(3000){answers==2}
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

}

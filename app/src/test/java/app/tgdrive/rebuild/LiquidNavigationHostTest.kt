package app.tgdrive.rebuild

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import com.infinity.drive.presentation.navigation.liquid.rememberLiquidNavigation
import com.infinity.drive.presentation.platform.LiquidNavigationItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real shader on API 33+ including its solid rendering option. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LiquidNavigationHostTest {
    @get:Rule val compose=createComposeRule()
    private var destination by mutableIntStateOf(0)
    private var labels by mutableStateOf(true)
    private var rtl by mutableStateOf(false)
    private var glass by mutableStateOf(true)

    @androidx.annotation.RequiresApi(33)
    private fun scene() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if(rtl)LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    val navigation=rememberLiquidNavigation()
                    Box(Modifier.fillMaxSize()) {
                        Column(navigation.capture(Modifier.fillMaxSize(),navigation.supportsGlass&&glass)) {
                            repeat(16){i->Box(Modifier.fillMaxWidth().weight(1f).background(if(i%2==0)Color(0xFF246CAC)else Color(0xFFAC5F9E)))}
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            val items=remember{(0..3).map{i->LiquidNavigationItem(ImageVector.Builder("Tab$i",24.dp,24.dp,24f,24f).build(),"Tab $i","tab-$i")}}
                            navigation.Bar(items,destination,labels,glass){destination=it}
                        }
                    }
                }
            }
        }
    }

    // Robolectric enforces API 35 through @Config; instrumentation filters do not run here.
    @android.annotation.SuppressLint("UseSdkSuppress")
    @androidx.annotation.RequiresApi(33)
    @Test fun externalSelectionThenDraggingBackCommitsTheActualPage() {
        scene()
        compose.onNodeWithTag("tab-3").performClick()
        compose.onNodeWithTag("tab-3").assertIsSelected()
        val start=compose.onNodeWithTag("tab-3").fetchSemanticsNode().boundsInRoot.center
        val end=compose.onNodeWithTag("tab-0").fetchSemanticsNode().boundsInRoot.center
        val bar=compose.onNodeWithTag("liquid-navigation")
        val origin=bar.fetchSemanticsNode().boundsInRoot.topLeft
        bar.performTouchInput { swipe(start-origin,end-origin,600) }
        compose.onNodeWithTag("tab-0").assertIsSelected()
        compose.runOnIdle { assertEquals(0,destination) }
        // Checks the native software renderer; device tests exercise the actual Android GPU path.
        assertTrue(bar.captureToImage().width>0)
    }

    // Robolectric enforces API 35 through @Config; instrumentation filters do not run here.
    @android.annotation.SuppressLint("UseSdkSuppress")
    @androidx.annotation.RequiresApi(33)
    @Test fun rapidTapsLabelsAndRtlKeepExactlyOneSelectedDestination() {
        scene()
        for(i in listOf(1,3,0,2,1))compose.onNodeWithTag("tab-$i").performClick()
        compose.onNodeWithTag("tab-1").assertIsSelected()
        compose.runOnIdle{labels=false;rtl=true;destination=3}
        compose.onNodeWithTag("tab-3").assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onAllNodesWithTag("tab-0").assertCountEquals(1)
        compose.onNodeWithTag("tab-0").performClick()
        compose.onNodeWithTag("tab-0").assertIsSelected()
        // The solid option uses the same selection and interaction without shaders.
        compose.runOnIdle{glass=false}
        compose.onNodeWithTag("tab-2").performClick()
        compose.onNodeWithTag("tab-2").assertIsSelected()
    }
}

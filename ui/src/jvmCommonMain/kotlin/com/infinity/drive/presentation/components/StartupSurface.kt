package com.infinity.drive.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.platform.LocalAppIcon
import kotlin.math.sin

/** The real local-readiness gate, with a restrained visual indicator and no artificial delay. */
@Composable
fun StartupSurface(modifier:Modifier=Modifier){
    val colors=MaterialTheme.colorScheme
    val phase=rememberInfiniteTransition(label="startupIndicator").animateFloat(0f,6.283185f,
        infiniteRepeatable(tween(1600,easing=LinearEasing)),label="startupPhase")
    Surface(modifier.fillMaxSize().pointerInput(Unit){
        awaitPointerEventScope{while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach{it.consume()}}
    }.clearAndSetSemantics{
        contentDescription="Preparing TeleDrive";progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate;liveRegion=LiveRegionMode.Polite
    },color=colors.background){
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()){
            val compact=maxHeight<360.dp
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(colors.primary.copy(alpha=.065f),Color.Transparent))))
            Column(Modifier.align(Alignment.Center).widthIn(max=360.dp).padding(horizontal=32.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                LocalAppIcon.current(Modifier.size(if(compact)64.dp else 88.dp))
                Spacer(Modifier.height(if(compact)16.dp else 26.dp))
                Text("TeleDrive",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold,color=colors.onBackground)
                Spacer(Modifier.height(8.dp))
                Text("Preparing your library",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                Spacer(Modifier.height(if(compact)18.dp else 28.dp))
                Canvas(Modifier.size(44.dp,12.dp)){
                    repeat(3){index->
                        val wave=(sin(phase.value-index*.8f)+1f)/2
                        drawCircle(colors.primary.copy(alpha=.25f+.6f*wave),2.dp.toPx(),Offset(size.width/2+(index-1)*12.dp.toPx(),size.height/2))
                    }
                }
            }
            if(!compact)Text("Your files. Your space.",Modifier.align(Alignment.BottomCenter).padding(bottom=32.dp),
                style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant.copy(alpha=.65f))
        }
    }
}

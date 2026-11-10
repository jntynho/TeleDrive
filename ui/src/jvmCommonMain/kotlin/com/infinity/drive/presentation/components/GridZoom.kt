package com.infinity.drive.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import com.infinity.drive.domain.model.ViewMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*

const val MIN_GRID_COLUMNS = 2
const val MAX_GRID_COLUMNS = 6

data class GridZoomLevel(val viewMode: ViewMode, val gridSize: Int)
fun GridZoomLevel.zoomedIn():GridZoomLevel = if(viewMode==ViewMode.LIST)GridZoomLevel(ViewMode.GRID,MAX_GRID_COLUMNS)
    else GridZoomLevel(ViewMode.GRID,(gridSize-1).coerceAtLeast(MIN_GRID_COLUMNS))
fun GridZoomLevel.zoomedOut():GridZoomLevel = when {
    viewMode==ViewMode.LIST->this
    gridSize>=MAX_GRID_COLUMNS->GridZoomLevel(ViewMode.LIST,gridSize)
    else->GridZoomLevel(ViewMode.GRID,gridSize+1)
}

/** One density decision per gesture; jitter never triggers repeated layout or preference writes. */
class GridPinchSession {
    var zoom=1f;private set
    fun add(factor:Float){if(factor.isFinite()&&factor>0)zoom=(zoom*factor).coerceIn(.25f,4f)}
    val steps:Int get()=(ln(zoom.toDouble())/ln(1.28)).toInt().coerceIn(-4,4)
}

fun Modifier.pinchZoom(onZoomIn:()->Unit,onZoomOut:()->Unit,
    onGestureStarted:(Offset)->Unit={},onGestureFinished:(Boolean)->Unit={}):Modifier = composed {
    val inward by rememberUpdatedState(onZoomIn)
    val outward by rememberUpdatedState(onZoomOut)
    val started by rememberUpdatedState(onGestureStarted)
    val finished by rememberUpdatedState(onGestureFinished)
    var visualScale by remember{mutableFloatStateOf(1f)}
    var pivot by remember{mutableStateOf(TransformOrigin.Center)}
    var settling by remember{mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    this.wheelZoom({inward()},{outward()}).pointerInput(Unit){
        awaitEachGesture {
            val session=GridPinchSession()
            var multitouch=false
            var completed=false
            try {
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    if(event.changes.count{it.pressed}>=2){
                        if(!multitouch){
                            multitouch=true;settling?.cancel();visualScale=1f
                            val center=event.calculateCentroid()
                            if(center.x.isFinite()&&center.y.isFinite()){
                                pivot=TransformOrigin((center.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f),(center.y/size.height.coerceAtLeast(1)).coerceIn(0f,1f))
                                started(center)
                            }
                        }
                        session.add(event.calculateZoom())
                        visualScale=session.zoom.coerceIn(.9f,1.1f)
                    }
                    if(multitouch)event.changes.forEach{it.consume()}
                }while(event.changes.any{it.pressed})
                completed=true
                if(multitouch)repeat(abs(session.steps)){if(session.steps>0)inward()else outward()}
            }finally{
                if(multitouch){
                    finished(completed&&session.steps!=0)
                    if(completed)settling=scope.launch{Animatable(visualScale).animateTo(1f,tween(220,easing=FastOutSlowInEasing)){visualScale=value}}
                    else visualScale=1f
                }
            }
        }
    }.graphicsLayer{scaleX=visualScale;scaleY=visualScale;transformOrigin=pivot;clip=true}
}

private fun Modifier.wheelZoom(onZoomIn:()->Unit,onZoomOut:()->Unit):Modifier=pointerInput(onZoomIn,onZoomOut){
    awaitPointerEventScope {
        while(true){
            val event=awaitPointerEvent(PointerEventPass.Initial)
            if(event.type!=PointerEventType.Scroll||!event.keyboardModifiers.isCtrlPressed)continue
            val delta=event.changes.firstOrNull()?.scrollDelta?.y?:0f
            when{delta<0->onZoomIn();delta>0->onZoomOut()}
            event.changes.forEach{it.consume()}
        }
    }
}

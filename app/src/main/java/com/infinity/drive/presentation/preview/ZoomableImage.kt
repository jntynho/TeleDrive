// Modified for TeleDrive; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.preview

import app.tgdrive.ui.DriveIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.crossfade
import coil3.compose.AsyncImagePainter
import com.infinity.drive.presentation.components.EmptyState
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.preview_image_undecodable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.MotionDurationScale
import androidx.compose.animation.rememberSplineBasedDecay
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** Fit-aware zoom. Drag transforms are synchronous; only double-tap uses an animation. */
@kotlin.OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun ZoomableImage(model:Any?,contentDescription:String?,modifier:Modifier=Modifier,
    onTap:()->Unit={},onZoomChanged:(Boolean)->Unit={},contentKey:Any?=model,previewModel:Any?=null
) {
    var failed by remember(model){mutableStateOf(false)}
    var scale by remember(contentKey){mutableFloatStateOf(1f)}
    var offset by remember(contentKey){mutableStateOf(Offset.Zero)}
    var viewport by remember(contentKey){mutableStateOf(IntSize.Zero)}
    var imageSize by remember(contentKey){mutableStateOf(IntSize.Zero)}
    var animation by remember(contentKey){mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    val decay=rememberSplineBasedDecay<Float>()
    var region by remember(model){mutableStateOf<ZoomRegion?>(null)}
    LaunchedEffect(model){
        region=null
        snapshotFlow{Triple(scale,offset,viewport)}.debounce(100).collectLatest{(zoom,pan,view)->
            if(model is String){
                val tile=withContext(Dispatchers.IO){decodeZoomRegion(model,view,zoom,pan)}
                ensureActive();region=tile
            } else region=null
        }
    }
    val tap by rememberUpdatedState(onTap)
    val zoomChanged by rememberUpdatedState(onZoomChanged)
    fun clamp(position:Offset,zoom:Float):Offset=clampImagePan(position,zoom,viewport,imageSize)
    LaunchedEffect(contentKey){snapshotFlow{scale>1.01f}.distinctUntilChanged().collect{zoomChanged(it)}}
    DisposableEffect(contentKey){onDispose{animation?.cancel();zoomChanged(false)}}
    if(failed){
        LaunchedEffect(model){animation?.cancel();scale=1f;offset=Offset.Zero;zoomChanged(false)}
        EmptyState(DriveIcons.BrokenImage,stringResource(Res.string.preview_image_undecodable),modifier=modifier,
            actionLabel="Retry",onAction={failed=false});return}
    Box(modifier.fillMaxSize().semantics {
        stateDescription="Zoom ${(scale*100).toInt()} percent"
        customActions=listOf(CustomAccessibilityAction("Zoom in"){animation?.cancel();scale=(scale*1.5f).coerceAtMost(6f);offset=clamp(offset,scale);true},
            CustomAccessibilityAction("Fit image"){animation?.cancel();scale=1f;offset=Offset.Zero;true})
    }.clipToBounds().onSizeChanged{viewport=it;offset=clamp(offset,scale)}
        .pointerInput(contentKey){detectTapGestures(onTap={tap()},onDoubleTap={point->
            animation?.cancel()
            val startScale=scale
            val startOffset=offset
            val targetScale=if(scale>1.01f)1f else 2.5f
            val anchor=point-Offset(viewport.width/2f,viewport.height/2f)
            val targetOffset=clamp((startOffset-anchor)*(targetScale/startScale)+anchor,targetScale)
            animation=scope.launch{
                Animatable(0f).animateTo(1f,tween(220)){
                    scale=startScale+(targetScale-startScale)*value
                    offset=clamp(startOffset+(targetOffset-startOffset)*value,scale)
                }
            }
        })}
        .pointerInput(contentKey){awaitEachGesture{
            awaitFirstDown(requireUnconsumed=false)
            animation?.cancel()
            val velocity=VelocityTracker()
            var moved=false
            var multitouch=false
            do {
                val event=awaitPointerEvent()
                val pinching=event.changes.count{it.pressed}>1
                if(pinching){multitouch=true;velocity.resetTracking()}
                else event.changes.firstOrNull{it.pressed}?.let{velocity.addPosition(it.uptimeMillis,it.position)}
                if(!pinching&&scale<=1.01f)continue
                val zoom=event.calculateZoom()
                val pan=event.calculatePan()
                if(pan.getDistance()>.5f)moved=true
                if(abs(zoom-1f)<.0001f&&pan==Offset.Zero)continue
                animation?.cancel()
                val next=(scale*zoom).coerceIn(1f,6f)
                val centroid=event.calculateCentroid(useCurrent=false)
                val anchor=if(centroid.x.isFinite()&&centroid.y.isFinite())centroid-Offset(viewport.width/2f,viewport.height/2f)else Offset.Zero
                offset=if(next<=1.01f)Offset.Zero else clamp((offset-anchor)*(next/scale)+anchor+pan,next)
                scale=next
                event.changes.forEach{it.consume()}
            }while(event.changes.any{it.pressed})
            if(moved&&!multitouch&&scale>1.01f&&scope.coroutineContext[MotionDurationScale]?.scaleFactor!=0f){
                val speed=velocity.calculateVelocity()
                animation=scope.launch{coroutineScope {
                    launch{androidx.compose.animation.core.AnimationState(offset.x,speed.x).animateDecay(decay){val old=value;val next=clamp(Offset(value,offset.y),scale);offset=next;if(next.x!=old)cancelAnimation()}}
                    launch{androidx.compose.animation.core.AnimationState(offset.y,speed.y).animateDecay(decay){val old=value;val next=clamp(Offset(offset.x,value),scale);offset=next;if(next.y!=old)cancelAnimation()}}
                }}
            }
        }}
    ) {
        val context=coil3.compose.LocalPlatformContext.current
        val imageRequest=remember(model,previewModel,context){coil3.request.ImageRequest.Builder(context).data(model?:previewModel).crossfade(160).build()}
        AsyncImage(imageRequest,contentDescription,contentScale=ContentScale.Fit,onState={state->
            when(state){
                is AsyncImagePainter.State.Error->if(model!=null)failed=true
                is AsyncImagePainter.State.Success->{imageSize=IntSize(state.result.image.width,state.result.image.height);offset=clamp(offset,scale)}
                else->Unit
            }
        },modifier=Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y})
        region?.let{tile->
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()){
                val factor=minOf(size.width/tile.original.width,size.height/tile.original.height)*scale
                val left=size.width/2-tile.original.width*factor/2+offset.x+tile.source.left*factor
                val top=size.height/2-tile.original.height*factor/2+offset.y+tile.source.top*factor
                drawImage(tile.bitmap.asImageBitmap(),dstOffset=IntOffset(left.roundToInt(),top.roundToInt()),
                    dstSize=IntSize((tile.source.width()*factor).roundToInt().coerceAtLeast(1),(tile.source.height()*factor).roundToInt().coerceAtLeast(1)))
            }
        }
    }
}

internal fun clampImagePan(position:Offset,scale:Float,viewport:IntSize,image:IntSize):Offset {
    if(viewport.width<=0||viewport.height<=0||scale<=1f)return Offset.Zero
    val ratio=if(image.width>0&&image.height>0)minOf(viewport.width.toFloat()/image.width,viewport.height.toFloat()/image.height)else 1f
    val width=if(image.width>0)image.width*ratio else viewport.width.toFloat()
    val height=if(image.height>0)image.height*ratio else viewport.height.toFloat()
    val x=((width*scale-viewport.width)/2f).coerceAtLeast(0f)
    val y=((height*scale-viewport.height)/2f).coerceAtLeast(0f)
    return Offset(if(x>0f)position.x.coerceIn(-x,x)else 0f,if(y>0f)position.y.coerceIn(-y,y)else 0f)
}

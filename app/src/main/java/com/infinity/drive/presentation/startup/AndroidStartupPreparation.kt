package com.infinity.drive.presentation.startup

import android.app.ActivityManager
import android.content.Context
import android.graphics.*
import android.media.ImageReader
import android.os.Build
import android.os.Trace
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.infinity.drive.core.media.ThumbnailModel
import com.infinity.drive.data.local.dao.ThumbnailDao
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.navigation.NavigationTransitions
import com.infinity.drive.presentation.platform.StartupPreparation
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.sqrt

/** Optional, bounded work. No original media decoding, network, players or live page view models. */
class AndroidStartupPreparation(private val context:Context,private val loader:ImageLoader,private val thumbnails:ThumbnailDao):StartupPreparation {
    data class Report(val images:Int,val scenes:Int,val gpuFrames:Int,val timedOut:Boolean,val millis:Long)
    @Volatile var report=Report(0,0,0,false,0);private set
    private val warmedImages=java.util.concurrent.atomic.AtomicInteger()

    override val enabled:Boolean get() = if(app.tgdrive.BuildConfig.STARTUP_BENCHMARK)context.getSharedPreferences("benchmark-control",Context.MODE_PRIVATE).getBoolean("visual",true)else true
    private val lowRam=context.getSystemService(ActivityManager::class.java)?.isLowRamDevice==true
    override suspend fun prepareImages(files:List<DriveFile>,viewportWidth:Int,viewportHeight:Int):Unit=withContext<Unit>(Dispatchers.IO){
        val cookie=traceId.incrementAndGet()
        if(Build.VERSION.SDK_INT>=29)Trace.beginAsyncSection("TeleDrive.startup.thumbnails",cookie)
        try{
            val side=(viewportWidth/3).coerceIn(48,if(lowRam)192 else 384)
            val budget=if(lowRam)2_000_000L else 6_000_000L
            val count=minOf(if(lowRam)6 else 16,(budget/(side.toLong()*side*5)).toInt().coerceAtLeast(1))
            val slots=Semaphore(2)
            val decodedBytes=java.util.concurrent.atomic.AtomicLong()
            coroutineScope {files.distinctBy{it.id}.take(count).map{file->async{
                slots.withPermit{
                    if(thumbnails.byFileId(file.id)==null)return@withPermit
                    val model=com.infinity.drive.core.media.thumbnailModel(file).copy(cacheOnly=true)
                    val audio=file.category==com.infinity.drive.domain.model.FileCategory.AUDIO
                    val width=if(audio)minOf(side,(context.resources.displayMetrics.density*64).toInt())else side
                    val height=if(audio)width else width*5/4
                    val request=ImageRequest.Builder(context).data(model).size(width,height).build()
                    val result=loader.execute(request)
                    if(result is coil3.request.SuccessResult){
                        if(result.dataSource==coil3.decode.DataSource.MEMORY_CACHE||decodedBytes.addAndGet(result.image.size)<=budget)warmedImages.incrementAndGet()
                        else {decodedBytes.addAndGet(-result.image.size);result.memoryCacheKey?.let{loader.memoryCache?.remove(it)}}
                    }
                }
            }}.awaitAll()}
        }finally{if(Build.VERSION.SDK_INT>=29)Trace.endAsyncSection("TeleDrive.startup.thumbnails",cookie)}
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable override fun RenderProbe(key:String,glass:Boolean,scene:@Composable (Int)->Unit,onReady:()->Unit){
        val parent=rememberCompositionContext()
        val ready by rememberUpdatedState(onReady)
        val drawScene by rememberUpdatedState(scene)
        var view by remember(key){mutableStateOf<ComposeView?>(null)}
        androidx.compose.runtime.key(key){AndroidView(factory={ctx->ComposeView(ctx).apply{
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setParentCompositionContext(parent)
            setContent {
                val seek=remember(key){SeekableTransitionState(0)}
                val transition=rememberTransition(seek,label="startup-real-transition")
                transition.AnimatedContent(modifier=Modifier.fillMaxSize().clearAndSetSemantics{},transitionSpec={
                    NavigationTransitions.enter(true) togetherWith NavigationTransitions.exit(true)
                }){drawScene(it)}
                LaunchedEffect(key){
                    val start=android.os.SystemClock.elapsedRealtime()
                    var scenes=0;var gpuFrames=0;var timedOut=false
                    try{
                        val result=withTimeoutOrNull<Boolean>(if(lowRam)180 else 360){
                            val host=view?:return@withTimeoutOrNull false
                            awaitDraw(host)
                            val stages=if(lowRam)listOf(1,2)else listOf(1,2,3)
                            for(stage in stages){
                                seek.seekTo(.5f,stage)
                                awaitDraw(host)
                                scenes++
                                if(Build.VERSION.SDK_INT>=29&&!lowRam&&renderHardwareFrame(host,glass))gpuFrames++
                                seek.snapTo(stage)
                            }
                            true
                        }
                        timedOut=result!=true
                    }catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){ /* Preparation is optional; the actual interface remains usable. */ }
                    finally{
                        report=Report(warmedImages.get(),scenes,gpuFrames,timedOut,android.os.SystemClock.elapsedRealtime()-start)
                        if(currentCoroutineContext().isActive)ready()
                    }
                }
            }
            view=this
        }},modifier=Modifier.fillMaxSize().clearAndSetSemantics{})}
    }

    private suspend fun awaitDraw(view:View){
        // An opaque splash may suppress on-screen drawing. Wait for composition/layout,
        // then record the view explicitly into a hardware node instead of waiting for OnDraw.
        withFrameNanos{}
        if(!view.isLaidOut||view.width==0||view.height==0)withFrameNanos{}
        check(view.isAttachedToWindow&&view.width>0&&view.height>0){"Startup probe is not attached"}
    }

    @RequiresApi(29)
    private suspend fun renderHardwareFrame(view:View,glass:Boolean):Boolean {
        if(!view.isHardwareAccelerated||view.width<=0||view.height<=0)return false
        val ratio=minOf(1.0,sqrt(524288.0/(view.width.toDouble()*view.height)))
        val width=(view.width*ratio).toInt().coerceAtLeast(1);val height=(view.height*ratio).toInt().coerceAtLeast(1)
        val node=RenderNode("TeleDrive startup probe").apply{setPosition(0,0,width,height)}
        try{
            Trace.beginSection("TeleDrive.startup.record")
            val canvas=node.beginRecording(width,height)
            try{
                canvas.scale(ratio.toFloat(),ratio.toFloat());view.draw(canvas)
                if(glass&&Build.VERSION.SDK_INT>=33)drawHighlight(canvas)
            }finally{node.endRecording()}
        }catch(error:Exception){node.discardDisplayList();throw error}
        finally{Trace.endSection()}
        // A GPU/driver stall cannot hold the startup UI beyond its optional deadline.
        val gpu=submitHardwareFrame(node,width,height)
        return try{gpu.await()}finally{if(!gpu.isCompleted)gpu.cancel()}
    }

    private companion object {
        val traceId=java.util.concurrent.atomic.AtomicInteger()
        val gpuScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val gpuSlots=Semaphore(1)
        @OptIn(ExperimentalCoroutinesApi::class,DelicateCoroutinesApi::class)
        @RequiresApi(29)
        fun submitHardwareFrame(node:RenderNode,width:Int,height:Int):Deferred<Boolean> = gpuScope.async(start=CoroutineStart.ATOMIC){
            try{gpuSlots.withPermit{
                drawRecordedNode(node,width,height)
            }}finally{node.discardDisplayList()}
        }
        @RequiresApi(29)
        private fun drawRecordedNode(node:RenderNode,width:Int,height:Int):Boolean {
            var reader:ImageReader?=null
            var renderer:HardwareRenderer?=null
            try {
                Trace.beginSection("TeleDrive.startup.gpu")
                reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
                renderer=HardwareRenderer().apply{setSurface(reader.surface);setContentRoot(node)}
                val result=renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                val image=reader.acquireLatestImage()
                val rendered=image!=null&&result==HardwareRenderer.SYNC_OK
                image?.close()
                return rendered
            } finally {
                try { renderer?.destroy() }
                finally { try { reader?.close() } finally { Trace.endSection() } }
            }
        }
    }
    @RequiresApi(33)
    private fun drawHighlight(canvas:Canvas){
        val shader=com.infinity.drive.presentation.navigation.liquid.miuix.animation.createHighlightShader()
        shader.setFloatUniform("size",64f,64f);shader.setColorUniform("color",Color.WHITE)
        shader.setFloatUniform("radius",60f);shader.setFloatUniform("position",32f,32f)
        canvas.drawRect(0f,0f,64f,64f,Paint().apply{this.shader=shader;blendMode=BlendMode.PLUS})
    }
}

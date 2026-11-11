package com.infinity.drive.presentation.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class StartupPreparationUi(val visibility:MutableTransitionState<Boolean>,val render:Boolean,val key:String,
    val mode:String,val glass:Boolean,val onLibraryFrame:()->Unit,val onProbeReady:()->Unit)

@Composable
fun rememberStartupPreparation(loading:Boolean,mode:String,glass:Boolean,state:StartupPreparationState,files:FileRepository):StartupPreparationUi {
    val configured=LocalStartupPreparation.current
    val platform=configured?.takeIf{it.enabled}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val window=LocalWindowInfo.current.containerSize
    val density=androidx.compose.ui.platform.LocalDensity.current
    val background=androidx.compose.material3.MaterialTheme.colorScheme.background
    val resourceKey="$mode:${window.width}:${window.height}:${density.density}:${density.fontScale}:${background.value}:$glass"
    val finished=remember(mode){CompletableDeferred<Unit>()}
    val libraryFrame=remember(mode){CompletableDeferred<Unit>()}
    var render by remember(mode){mutableStateOf(false)}
    val pending=loading||state.needs(mode)
    val visibility=remember(mode){MutableTransitionState(pending)}
    visibility.targetState=pending
    LaunchedEffect(loading,mode){
        if(loading||!state.needs(mode))return@LaunchedEffect
        lifecycle.currentStateFlow.first{it.isAtLeast(Lifecycle.State.STARTED)}
        try {
            withTimeoutOrNull(550){
                coroutineScope {
                    if(mode=="library")launch { libraryFrame.await() }
                    if(mode=="library"&&platform!=null)launch {
                        try{withTimeoutOrNull(220){platform.prepareImages(files.preparedStartupFiles(),window.width,window.height)}}
                        catch(cancel:CancellationException){throw cancel}
                        catch(_:Exception){ /* Local thumbnails are optional. */ }
                    }
                    if(platform!=null&&mode!="locked"){
                        render=true
                        finished.await()
                    }
                    withFrameNanos{};withFrameNanos{}
                }
            }
            state.complete(mode)
        } finally { render=false }
    }
    return StartupPreparationUi(visibility,render,resourceKey,mode,glass,{libraryFrame.complete(Unit)}){finished.complete(Unit)}
}

@Composable
fun StartupRenderProbe(state:StartupPreparationUi){
    if(state.render)LocalStartupPreparation.current?.RenderProbe(state.key,state.glass,
        scene={page->StartupVisualScene(page,state.mode=="library",state.glass)},onReady=state.onProbeReady)
}

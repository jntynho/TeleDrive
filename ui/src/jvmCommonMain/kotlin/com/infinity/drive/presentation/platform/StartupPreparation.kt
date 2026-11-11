package com.infinity.drive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.infinity.drive.domain.model.DriveFile

/** Rendering probes share the real theme/viewport but never own navigation, playback or sync. */
interface StartupPreparation {
    val enabled:Boolean get()=true
    suspend fun prepareImages(files:List<DriveFile>,viewportWidth:Int,viewportHeight:Int)
    @Composable fun RenderProbe(key:String,glass:Boolean,scene:@Composable (Int)->Unit,onReady:()->Unit)
}
val LocalStartupPreparation=staticCompositionLocalOf<StartupPreparation?>{null}
val LocalStartupProbe=staticCompositionLocalOf{false}
val LocalFullyDrawn=staticCompositionLocalOf<()->Unit>{{}}

/** Initial library data reached its first composed frame; never waits for network sync. */
val LocalFirstLibraryFrame=staticCompositionLocalOf<()->Unit>{{}}

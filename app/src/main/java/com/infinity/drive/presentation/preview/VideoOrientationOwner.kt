package com.infinity.drive.presentation.preview

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

internal class VideoOrientationState(val rotation:State<PlayerRotation>,val select:(PlayerRotation)->Unit)
internal val LocalVideoOrientation=staticCompositionLocalOf<VideoOrientationState?>{null}

/** One orientation lease for the viewer, rather than one lease for every paged video. */
@Composable
internal fun WithVideoOrientation(content:@Composable ()->Unit){
    val activity=LocalActivity.current
    val original=rememberSaveable{activity?.requestedOrientation?:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
    val rotation=rememberSaveable{mutableStateOf(PlayerRotation.AUTO)}
    val owner=remember(activity){VideoOrientationState(rotation){next->rotation.value=next;activity?.requestedOrientation=next.orientation}}
    LaunchedEffect(activity){if(rotation.value!=PlayerRotation.AUTO)activity?.requestedOrientation=rotation.value.orientation}
    DisposableEffect(activity){onDispose{
        if(activity!=null&&!activity.isChangingConfigurations)activity.requestedOrientation=original
    }}
    CompositionLocalProvider(LocalVideoOrientation provides owner,content=content)
}

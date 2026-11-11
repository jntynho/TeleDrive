package com.infinity.drive.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.tgdrive.ui.LocalNavigationInProgress

/** Every result-key type is supported; only draw opacity changes, never item geometry. */
@Composable
fun Modifier.resultReveal(revision:Any?):Modifier {
    val opacity=remember{Animatable(1f)}
    val navigating=LocalNavigationInProgress.current
    LaunchedEffect(revision,navigating){
        opacity.snapTo(1f)
        if(!navigating&&revision!=null&&(revision !is String||revision.isNotBlank())){
            opacity.snapTo(.96f)
            opacity.animateTo(1f,tween(180))
        }
    }
    return graphicsLayer{alpha=opacity.value}
}

package com.infinity.drive.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** A restrained draw-only reveal: result updates never resize the toolbar or scroll viewport. */
@Composable
fun Modifier.resultReveal(revision:Any?):Modifier {
    val opacity=remember{Animatable(1f)}
    LaunchedEffect(revision){opacity.snapTo(.82f);opacity.animateTo(1f,tween(260))}
    return graphicsLayer{alpha=opacity.value}
}

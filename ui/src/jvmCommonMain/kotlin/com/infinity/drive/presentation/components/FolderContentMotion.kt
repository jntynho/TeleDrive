package com.infinity.drive.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** One existing grid animates; headers, hit testing and paging ownership stay unchanged. */
fun Modifier.folderContentReveal(key:Any?):Modifier=composed {
    val progress=remember(key){Animatable(0f)}
    LaunchedEffect(key){progress.animateTo(1f,tween(260,easing=FastOutSlowInEasing))}
    graphicsLayer{alpha=.7f+.3f*progress.value;translationX=10.dp.toPx()*(1f-progress.value)}
}

fun Modifier.fileRevealHighlight(active:Boolean):Modifier=composed {
    val color by animateColorAsState(if(active)MaterialTheme.colorScheme.primary.copy(alpha=.18f)else androidx.compose.ui.graphics.Color.Transparent,tween(if(active)200 else 550),label="revealedFile")
    background(color,RoundedCornerShape(14.dp)).semantics{if(active)stateDescription="Located file"}
}

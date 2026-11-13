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

/** A folder push and its reverse animate only the body, on the navigation transition clock. */
object FolderContentTransitions {
    private val easing=androidx.compose.animation.core.CubicBezierEasing(.2f,0f,0f,1f)
    const val DurationMillis=360
    fun enter(forward:Boolean)=androidx.compose.animation.slideInHorizontally(tween(DurationMillis,easing=easing)){
        if(forward)it/7 else -it/12
    }+androidx.compose.animation.fadeIn(tween(DurationMillis,easing=easing),initialAlpha=if(forward).35f else .7f)
    fun exit(forward:Boolean)=androidx.compose.animation.slideOutHorizontally(tween(DurationMillis,easing=easing)){
        if(forward)-it/12 else it/7
    }+androidx.compose.animation.fadeOut(tween(DurationMillis,easing=easing),targetAlpha=0f)
}

/** Remember committed entries, so returning to an ancestor reverses the same motion. */
class FolderNavigationHistory(saved:List<String> = emptyList()) {
    data class Motion(val enabled:Boolean=false,val forward:Boolean=true)
    private val entries=saved.map{it.substringAfter(':') to it.startsWith("1:")}.toMutableList()
    fun snapshot():List<String> = entries.map{(id,folder)->"${if(folder)1 else 0}:$id"}
    fun visit(id:String?,folder:Boolean):Motion {
        if(id==null)return Motion()
        if(entries.lastOrNull()?.first==id)return Motion()
        val previous=entries.lastOrNull()?.second==true
        val index=entries.indexOfFirst{it.first==id}
        val forward=index<0
        if(forward)entries.add(id to folder)else entries.subList(index+1,entries.size).clear()
        return Motion(previous&&folder,forward)
    }
}

fun Modifier.fileRevealHighlight(active:Boolean):Modifier=composed {
    val color by animateColorAsState(if(active)MaterialTheme.colorScheme.primary.copy(alpha=.18f)else androidx.compose.ui.graphics.Color.Transparent,tween(if(active)200 else 550),label="revealedFile")
    background(color,RoundedCornerShape(14.dp)).semantics{if(active)stateDescription="Located file"}
}

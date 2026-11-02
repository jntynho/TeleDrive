package com.infinity.drive.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.platform.LocalAppIcon

/** Drawn while actual startup work and the first content frames are prepared. No fake progress or timed gate. */
@Composable
fun StartupSurface(modifier:Modifier=Modifier){
    val background by animateColorAsState(MaterialTheme.colorScheme.background,tween(320),label="startupBackground")
    Surface(modifier.fillMaxSize().clickable(indication=null,interactionSource=remember{MutableInteractionSource()}){},color=background){
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            LocalAppIcon.current(Modifier.size(80.dp))
            Spacer(Modifier.height(20.dp))
            Text("TeleDrive",style=MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Getting things ready",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            app.tgdrive.ui.SkeletonScene{app.tgdrive.ui.SkeletonBlock(Modifier.width(104.dp).height(3.dp))}
        }
    }
}

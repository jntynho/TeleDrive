package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun TelegramProfileAvatar(path:String?,modifier:Modifier=Modifier){
    var failed by remember(path){mutableStateOf(false)}
    Box(modifier.clip(CircleShape),contentAlignment=Alignment.Center){
        if(!path.isNullOrBlank()&&!failed)AsyncImage(path,"Telegram profile photo",Modifier.fillMaxSize(),
            contentScale=ContentScale.Crop,onError={failed=true})
        else Icon(DriveIcons.AccountCircle,"Telegram account",Modifier.fillMaxSize(),tint=MaterialTheme.colorScheme.primary)
    }
}

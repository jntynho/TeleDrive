package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.domain.model.*
import com.infinity.drive.presentation.audio.AudioLibraryRow
import com.infinity.drive.presentation.platform.*

/** Uses production widgets without starting their view models, requests, media or navigation. */
@Composable
fun StartupVisualScene(page:Int,library:Boolean,glass:Boolean=false){
    val sample=remember { DriveFile("startup-probe",null,"Your files.jpg",1024,"image/jpeg",FileCategory.IMAGE,
        null,null,null,null,null,null,BackupState.NONE,false,false,false,false,null,null,null,null,createdAt=1,modifiedAt=1,addedAt=1) }
    CompositionLocalProvider(LocalStartupProbe provides true){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(if(!library)"Welcome to TeleDrive"else when(page){0->"Home";1->"Gallery";2->"Audio";else->"Settings"},style=MaterialTheme.typography.headlineMedium)
                if(!library){
                    OutlinedTextField("",{},label={Text("Phone number")},readOnly=true,modifier=Modifier.fillMaxWidth())
                    Button({}){Text("Continue")}
                }else when(page){
                    0->repeat(2){FileListItem(sample,false,false,{},{})}
                    1->Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                        repeat(3){FileGridItem(sample,it==1,{},{},Modifier.weight(1f),compact=true,galleryTile=true)}
                    }
                    2->repeat(2){AudioLibraryRow(sample.copy(name="Audio.mp3",mimeType="audio/mpeg",category=FileCategory.AUDIO))}
                    else->{
                        app.tgdrive.ui.ExpandableLibraryHeader("Settings","",{},true,{},"Search settings",requestSearchFocus=false)
                        Surface(shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){
                            Column{SheetActionRow("Download",DriveIcons.Download){};SheetActionRow("Send in Telegram",DriveIcons.Share){};SheetActionRow("Details",DriveIcons.Info){}}
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                val liquid=LocalLiquidNavigation.current
                if(library&&liquid!=null)liquid.Bar(listOf(
                    LiquidNavigationItem(DriveIcons.Home,"Home","startup-home"),LiquidNavigationItem(DriveIcons.PhotoLibrary,"Gallery","startup-gallery"),
                    LiquidNavigationItem(DriveIcons.MusicNotes,"Audio","startup-audio"),LiquidNavigationItem(DriveIcons.Settings,"Settings","startup-settings")
                ),page.coerceIn(0,3),true,glass){}
            }
        }
    }
}

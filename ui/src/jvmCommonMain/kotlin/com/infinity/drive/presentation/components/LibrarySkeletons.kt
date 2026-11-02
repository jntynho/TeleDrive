package com.infinity.drive.presentation.components

import app.tgdrive.ui.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.audio.AudioLibraryRow
import com.infinity.drive.presentation.audio.AudioListHeading
import kotlin.math.ceil

@Composable
fun HomeSkeleton(modifier:Modifier=Modifier,bottomPadding:androidx.compose.ui.unit.Dp=0.dp){
    SkeletonScene(modifier){LazyColumn(contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=96.dp+bottomPadding),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{LibraryDashboard(LibraryOverview(0,0,emptyList()),{},showAddAction=false,loading=true,
            shortcuts=listOf(LibraryShortcut("Favorites",DriveIcons.Star){},LibraryShortcut("Archived",DriveIcons.Archive){},LibraryShortcut("Trash",DriveIcons.Delete){}))}
        item{HomeQuickLinks(listOf(LibraryShortcut("All files",DriveIcons.Folder){},LibraryShortcut("Activity",DriveIcons.SwapVert){},LibraryShortcut("Backups",DriveIcons.Backup){}),loading=true)}
        item{RecentSectionHeader(loading=true)}
        items(6){LibraryRecentRow(null)}
    }}
}
@Composable
fun AudioSkeleton(modifier:Modifier=Modifier,bottomPadding:androidx.compose.ui.unit.Dp=0.dp){
    SkeletonScene(modifier){LazyColumn(contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=bottomPadding+20.dp)){
        items(10){AudioLibraryRow(null)}
    }}
}
@Composable
fun GallerySkeleton(columns:Int,modifier:Modifier=Modifier,bottomPadding:androidx.compose.ui.unit.Dp=0.dp,listMode:Boolean=false){
    if(listMode){GalleryListSkeleton(modifier,bottomPadding);return}
    SkeletonScene(modifier){BoxWithConstraints(Modifier.fillMaxSize()){
        val count=columns.coerceAtLeast(1)
        val tileWidth=(maxWidth-LibraryLayout.GalleryGutter*(count-1))/count
        val tileHeight=tileWidth/LibraryLayout.GalleryAspect
        val rows=ceil((maxHeight/tileHeight).toDouble()).toInt().coerceIn(2,12)
        LazyVerticalGrid(columns=GridCells.Fixed(count),contentPadding=PaddingValues(top=LibraryLayout.GalleryTop,bottom=bottomPadding+12.dp),horizontalArrangement=Arrangement.spacedBy(LibraryLayout.GalleryGutter),verticalArrangement=Arrangement.spacedBy(LibraryLayout.GalleryGutter)){
            item(span={GridItemSpan(maxLineSpan)}){Text("Today",Modifier.wrapContentWidth(androidx.compose.ui.Alignment.Start).padding(start=16.dp,top=12.dp,bottom=8.dp).skeleton(),style=MaterialTheme.typography.titleSmall)}
            items(count*rows){SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(LibraryLayout.GalleryAspect),0.dp)}
        }
    }}
}

@Composable
private fun GalleryListSkeleton(modifier:Modifier,bottomPadding:androidx.compose.ui.unit.Dp){
    val placeholder=androidx.compose.runtime.remember {
        com.infinity.drive.domain.model.DriveFile(
            id="loading",folderId=null,name="Photo from your library.jpg",sizeBytes=245760,
            mimeType="image/jpeg",category=com.infinity.drive.domain.model.FileCategory.IMAGE,
            localPath=null,contentHash=null,chatId=null,messageId=null,remoteFileId=null,remoteUniqueId=null,
            backupState=com.infinity.drive.domain.model.BackupState.NONE,isHidden=false,isArchived=false,
            isFavorite=false,isEncrypted=false,width=null,height=null,durationMs=null,trashedAt=null,
            createdAt=0,modifiedAt=0,addedAt=0
        )
    }
    SkeletonScene(modifier){
        LazyColumn(contentPadding=PaddingValues(start=8.dp,end=8.dp,top=8.dp,bottom=bottomPadding+8.dp)){
            item{Text("Today",Modifier.wrapContentWidth(androidx.compose.ui.Alignment.Start).padding(start=16.dp,top=12.dp,bottom=8.dp).skeleton(),style=MaterialTheme.typography.titleSmall)}
            items(12){FileListItem(placeholder,false,false,{},{},loading=true)}
        }
    }
}

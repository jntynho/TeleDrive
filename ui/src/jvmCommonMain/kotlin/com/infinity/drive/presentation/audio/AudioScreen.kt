package com.infinity.drive.presentation.audio

import app.tgdrive.ui.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.infinity.drive.domain.model.*
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.files.SortMenu
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.platform.*
import com.infinity.drive.presentation.preview.PreviewSequence
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AudioScreen(onOpenFile:(String,PreviewSequence)->Unit,viewModel:AudioViewModel=koinViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val player=LocalAudioPlayback.current
    val playback by player.state.collectAsStateWithLifecycle()
    val audio=viewModel.audio.collectAsLazyPagingItems()
    var searching by rememberSaveable{mutableStateOf(false)}
    com.infinity.drive.presentation.common.AppBackHandler(searching){searching=false;viewModel.search("")}
    var sorting by rememberSaveable{mutableStateOf(false)}
    val listState=rememberLazyListState()
    LaunchedEffect(query,filter,prefs.audioSortField,prefs.audioSortDirection){listState.scrollToItem(0)}
    Scaffold(topBar={Column{
        ExpandableLibraryHeader("Audio",query,viewModel::search,searching,{searching=it},"Search audio",actions={Box {
            TextButton({sorting=true}){Icon(DriveIcons.Sort,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Sort")}
            SortMenu(sorting,prefs.audioSortField,prefs.audioSortDirection,{sorting=false},{field,direction->sorting=false;viewModel.sort(field,direction)},fields=listOf(FileSortField.NAME,FileSortField.DATE_ADDED,FileSortField.DATE_MODIFIED,FileSortField.SIZE))
        }})
        LibraryFilters(listOf("All","Favorites","On device"),filter.ordinal,{viewModel.filter(AudioFilter.entries[it])})
    }}){padding->
        val bottom=padding.calculateBottomPadding()+LocalBottomBarInset.current
        when {
            audio.loadState.refresh is LoadState.Loading&&audio.itemCount==0->AudioSkeleton(Modifier.padding(top=padding.calculateTopPadding()),bottom)
            audio.loadState.refresh is LoadState.Error->EmptyState(DriveIcons.Info,"Couldn't load audio","Try loading your library again.",Modifier.padding(top=padding.calculateTopPadding(),bottom=bottom),"Retry",{audio.retry()})
            audio.itemCount==0->EmptyState(DriveIcons.MusicNote,if(query.isNotBlank())"No matching audio"else when(filter){AudioFilter.FAVORITES->"No favorites yet";AudioFilter.ON_DEVICE->"No audio on this device";else->"Your sound, in one place"},if(query.isNotBlank())"Try another filename."else"Audio files in your drive will appear here.",Modifier.padding(top=padding.calculateTopPadding(),bottom=bottom))
            else->LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).resultReveal(query to audio.itemSnapshotList.items.take(8).map{it.id}),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=bottom+20.dp)){
                item("audio-actions") {AudioListHeading()}
                items(audio.itemCount,key=audio.itemKey{it.id}){index->
                    val file=audio[index]?:return@items
                    AudioLibraryRow(file,playback.file?.id==file.id&&playback.playing,playback.metadata.takeIf{playback.file?.id==file.id},modifier=Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(240)),onPlay={viewModel.play(file,player)},onFavorite={viewModel.favorite(file)}){
                        onOpenFile(file.id,PreviewSequence(categories=listOf(FileCategory.AUDIO),nameQuery=query.trim().takeIf{it.isNotBlank()},favoritesOnly=filter==AudioFilter.FAVORITES,sortField=prefs.audioSortField,sortDirection=prefs.audioSortDirection))
                    }
                }
                if(audio.loadState.append is LoadState.Error)item{TextButton({audio.retry()}){Text("Couldn't load more · Retry")}}
            }
        }
    }
}

@Composable
fun AudioListHeading(loading:Boolean=false){
    Text("Your audio",Modifier.fillMaxWidth().padding(vertical=12.dp).wrapContentWidth(Alignment.Start).skeleton(loading),style=MaterialTheme.typography.titleMedium)
}

@Composable
fun AudioLibraryRow(file:DriveFile?,playing:Boolean=false,currentMetadata:AudioMetadata?=null,modifier:Modifier=Modifier,
    onPlay:()->Unit={},onFavorite:()->Unit={},onDetails:()->Unit={}
) {
    val loading=file==null
    val player=LocalAudioPlayback.current
    val storedMetadata by produceState(AudioMetadata(),file?.id,file?.modifiedAt,file?.localPath){if(file!=null)value=player.metadata(file)}
    val meta=if(currentMetadata!=null)AudioMetadata(currentMetadata.title?:storedMetadata.title,currentMetadata.artist?:storedMetadata.artist,currentMetadata.durationMs?:storedMetadata.durationMs)else storedMetadata
    var menu by remember{mutableStateOf(false)}
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled=!loading,onClick=onPlay).padding(vertical=LibraryLayout.AudioRowPadding),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(LibraryLayout.AudioRowGap)){
        if(file!=null)FileThumbnail(file,Modifier.size(LibraryLayout.AudioCover).clip(RoundedCornerShape(LibraryLayout.CoverCorner)))
        else SkeletonBlock(Modifier.size(LibraryLayout.AudioCover),LibraryLayout.CoverCorner)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(meta.title?.takeIf{it.isNotBlank()}?:file?.name?.substringBeforeLast('.')?:"Midnight City",Modifier.skeleton(loading),style=MaterialTheme.typography.titleSmall,color=if(playing)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(meta.artist?.takeIf{it.isNotBlank()}?:file?.extension?.uppercase()?.ifBlank{"Audio"}?:"Artist name",Modifier.skeleton(loading),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        val duration=meta.durationMs?:file?.durationMs
        if(duration!=null||loading)Text(if(loading)"3:42"else Formatters.duration(duration!!),Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(playing)Icon(DriveIcons.Audiotrack,"Playing",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
        Box {
            IconButton({menu=true},enabled=!loading){Icon(DriveIcons.MoreVert,"Audio options for ${file?.name.orEmpty()}",Modifier.skeleton(loading))}
            DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text(if(file?.isFavorite==true)"Remove from favorites"else"Add to favorites")},onClick={menu=false;onFavorite()})
                DropdownMenuItem(text={Text("Open player & file options")},onClick={menu=false;onDetails()})
            }
        }
    }
}

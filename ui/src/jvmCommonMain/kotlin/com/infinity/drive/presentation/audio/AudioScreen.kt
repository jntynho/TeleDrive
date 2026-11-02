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
            audio.loadState.refresh is LoadState.Loading&&audio.itemCount==0->LoadingState(Modifier.padding(top=padding.calculateTopPadding(),bottom=bottom))
            audio.loadState.refresh is LoadState.Error->EmptyState(DriveIcons.Info,"Couldn't load audio","Try loading your library again.",Modifier.padding(top=padding.calculateTopPadding(),bottom=bottom),"Retry",{audio.retry()})
            audio.itemCount==0->EmptyState(DriveIcons.MusicNote,if(query.isNotBlank())"No matching audio"else when(filter){AudioFilter.FAVORITES->"No favorites yet";AudioFilter.ON_DEVICE->"No audio on this device";else->"Your sound, in one place"},if(query.isNotBlank())"Try another filename."else"Audio files in your drive will appear here.",Modifier.padding(top=padding.calculateTopPadding(),bottom=bottom))
            else->LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=bottom+20.dp)){
                item("audio-actions") {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your audio",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton({viewModel.playAll(player)}){Icon(DriveIcons.PlayArrow,null,Modifier.size(18.dp));Text("Play all")}}}
                items(audio.itemCount,key=audio.itemKey{it.id}){index->
                    val file=audio[index]?:return@items
                    AudioRow(file,playback.file?.id==file.id&&playback.playing,playback.metadata.takeIf{playback.file?.id==file.id},{viewModel.play(file,player)},{viewModel.favorite(file)}){
                        player.pause()
                        onOpenFile(file.id,PreviewSequence(categories=listOf(FileCategory.AUDIO),nameQuery=query.trim().takeIf{it.isNotBlank()},favoritesOnly=filter==AudioFilter.FAVORITES,sortField=prefs.audioSortField,sortDirection=prefs.audioSortDirection))
                    }
                }
                if(audio.loadState.append is LoadState.Error)item{TextButton({audio.retry()}){Text("Couldn't load more · Retry")}}
            }
        }
    }
}

@Composable
private fun AudioRow(file:DriveFile,playing:Boolean,currentMetadata:AudioMetadata?,onPlay:()->Unit,onFavorite:()->Unit,onDetails:()->Unit) {
    val player=LocalAudioPlayback.current
    val storedMetadata by produceState(AudioMetadata(),file.id,file.modifiedAt,file.localPath){value=player.metadata(file)}
    val meta=if(currentMetadata!=null)AudioMetadata(currentMetadata.title?:storedMetadata.title,currentMetadata.artist?:storedMetadata.artist,currentMetadata.durationMs?:storedMetadata.durationMs)else storedMetadata
    var menu by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onPlay).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        FileThumbnail(file,Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(meta.title?.takeIf{it.isNotBlank()}?:file.name.substringBeforeLast('.'),style=MaterialTheme.typography.titleSmall,color=if(playing)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(meta.artist?.takeIf{it.isNotBlank()}?:file.extension.uppercase().ifBlank{"Audio"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        val duration=meta.durationMs?:file.durationMs
        if(duration!=null)Text(Formatters.duration(duration),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(playing)Icon(DriveIcons.Audiotrack,"Playing",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
        Box {
            IconButton({menu=true}){Icon(DriveIcons.MoreVert,"Audio options for ${file.name}")}
            DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text(if(file.isFavorite)"Remove from favorites"else"Add to favorites")},onClick={menu=false;onFavorite()})
                DropdownMenuItem(text={Text("Open player & file options")},onClick={menu=false;onDetails()})
            }
        }
    }
}

@Composable
fun LibraryAudioMiniPlayer() {
    val player=LocalAudioPlayback.current
    val state by player.state.collectAsStateWithLifecycle()
    val file=state.file?:return
    val canPause=state.playing||(state.loading&&state.playRequested)
    var expanded by rememberSaveable(file.id){mutableStateOf(false)}
    Surface(color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
        Column {
            Row(Modifier.fillMaxWidth().clickable{expanded=!expanded}.padding(start=16.dp,end=4.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                FileThumbnail(file,Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f)){
                    Text(state.metadata.title?:file.name.substringBeforeLast('.'),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                    Text(state.error?:if(state.loading)"Loading audio…"else state.metadata.artist?:"Audio",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=if(state.error!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(player::toggle){Icon(if(canPause)DriveIcons.Pause else DriveIcons.PlayArrow,if(state.error!=null)"Retry audio"else if(canPause)"Pause audio"else"Play audio")}
                IconButton(player::next){Icon(DriveIcons.KeyboardArrowRight,"Next audio")}
                IconButton(player::stop){Icon(DriveIcons.Close,"Close audio player")}
            }
            if(expanded&&state.durationMs>0){
                app.tgdrive.ui.SlimSlider(value=state.positionMs.coerceIn(0,state.durationMs).toFloat(),onValueChange={player.seek(it.toLong())},valueRange=0f..state.durationMs.toFloat(),modifier=Modifier.padding(horizontal=16.dp))
                Text("${Formatters.duration(state.positionMs)} / ${Formatters.duration(state.durationMs)}",Modifier.padding(start=16.dp,bottom=8.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package com.infinity.drive.presentation.audio

import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import app.tgdrive.ui.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AudioScreen(onOpenFile:(String,PreviewSequence)->Unit,viewModel:AudioViewModel=koinViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val player=LocalAudioPlayback.current
    val playback by remember(player){player.state.map{it.copy(positionMs=0)}.distinctUntilChanged()}.collectAsStateWithLifecycle(player.state.value)
    val audio=viewModel.audio.collectAsLazyPagingItems()
    var searching by rememberSaveable{mutableStateOf(false)}
    com.infinity.drive.presentation.common.AppBackHandler(searching){searching=false;viewModel.search("")}
    var sorting by rememberSaveable{mutableStateOf(false)}
    val listState=rememberLazyListState()
    val scopeKey=listOf(query,filter.name,prefs.audioSortField.name,prefs.audioSortDirection.name).joinToString("\u0000")
    var lastScope by rememberSaveable{mutableStateOf(scopeKey)}
    LaunchedEffect(scopeKey){if(lastScope!=scopeKey){listState.scrollToItem(0);lastScope=scopeKey}}

    val filters=com.infinity.drive.presentation.components.rememberCollapsingFilters(query to filter){listState.canScrollBackward}
    Scaffold(modifier=filters.scrollModifier,topBar={Column{
        ExpandableLibraryHeader("Audio",query,viewModel::search,searching,{searching=it},"Search audio",actions={Box {
            IconButton({sorting=true}){Icon(DriveIcons.Sort,"Sort audio",Modifier.size(24.dp))}
            SortMenu(sorting,prefs.audioSortField,prefs.audioSortDirection,{sorting=false},{field,direction->sorting=false;viewModel.sort(field,direction)},fields=listOf(FileSortField.NAME,FileSortField.DATE_ADDED,FileSortField.DATE_MODIFIED,FileSortField.SIZE))
        }})
        androidx.compose.animation.AnimatedVisibility(filters.visible,enter=androidx.compose.animation.fadeIn()+androidx.compose.animation.expandVertically(),exit=androidx.compose.animation.fadeOut()+androidx.compose.animation.shrinkVertically()){
            LibraryFilters(listOf("All","Favorites","On device"),filter.ordinal,{viewModel.filter(AudioFilter.entries[it])})
        }
    }}){padding->
        val bottom=padding.calculateBottomPadding()+LocalBottomBarInset.current
        when {
            audio.loadState.refresh is LoadState.Loading&&audio.itemCount==0->AudioSkeleton(Modifier.padding(top=padding.calculateTopPadding()),bottom)
            audio.loadState.refresh is LoadState.Error&&audio.itemCount==0->EmptyState(DriveIcons.Info,"Couldn't load audio","Try loading your library again.",Modifier.padding(top=padding.calculateTopPadding(),bottom=bottom),"Retry",{audio.retry()})
            audio.itemCount==0->EmptyState(DriveIcons.MusicNote,if(query.isNotBlank())"No matching audio"else when(filter){AudioFilter.FAVORITES->"No favorites yet";AudioFilter.ON_DEVICE->"No audio on this device";else->"Your sound, in one place"},if(query.isNotBlank())"Try another filename."else"Audio files in your drive will appear here.",Modifier.padding(top=padding.calculateTopPadding(),bottom=bottom))
            else->LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).resultReveal(query to audio.itemSnapshotList.items.take(8).map{it.id}),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=bottom+20.dp)){

                items(audio.itemCount,key=audio.itemKey{it.id}){index->
                    val file=audio[index]?:return@items
                    AudioLibraryRow(file,playback.file?.id==file.id&&playback.playing,playback.metadata.takeIf{playback.file?.id==file.id},modifier=Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(240)),onPlay={viewModel.play(file,player);onOpenFile(file.id,PreviewSequence(categories=listOf(FileCategory.AUDIO),localCopiesOnly=filter==AudioFilter.ON_DEVICE,favoritesOnly=filter==AudioFilter.FAVORITES,nameQuery=query.trim().takeIf{it.isNotBlank()},sortField=prefs.audioSortField,sortDirection=prefs.audioSortDirection))},onFavorite={viewModel.favorite(file)}){
                        onOpenFile(file.id,PreviewSequence(categories=listOf(FileCategory.AUDIO),nameQuery=query.trim().takeIf{it.isNotBlank()},favoritesOnly=filter==AudioFilter.FAVORITES,localCopiesOnly=filter==AudioFilter.ON_DEVICE,sortField=prefs.audioSortField,sortDirection=prefs.audioSortDirection))
                    }
                }
                if(audio.loadState.refresh is LoadState.Error)item("audio-refresh-error"){TextButton({audio.retry()}){Text("Couldn't refresh · Retry")}}
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
    val storedMetadata by produceState(AudioMetadata(),file?.id,file?.modifiedAt,file?.localPath){if(file!=null)value=player.basicMetadata(file)}
    val meta=if(currentMetadata!=null)AudioMetadata(currentMetadata.title?.takeIf(String::isNotBlank)?:storedMetadata.title,currentMetadata.artist?.takeIf(String::isNotBlank)?:storedMetadata.artist,currentMetadata.durationMs?:storedMetadata.durationMs)else storedMetadata
    var menu by remember(file?.id){mutableStateOf(false)}
    if(menu&&file!=null)com.infinity.drive.presentation.components.FileActionsSheet(file,onDismiss={menu=false},onOpen=onPlay)
    Column {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled=!loading,onClick=onPlay).padding(vertical=LibraryLayout.AudioRowPadding),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(LibraryLayout.AudioRowGap)){
        if(file!=null)FileThumbnail(file,Modifier.size(LibraryLayout.AudioCover).clip(RoundedCornerShape(LibraryLayout.CoverCorner)))
        else SkeletonBlock(Modifier.size(LibraryLayout.AudioCover),LibraryLayout.CoverCorner)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(meta.title?.takeIf{it.isNotBlank()}?:file?.name?.substringBeforeLast('.')?:"Midnight City",Modifier.skeleton(loading),style=MaterialTheme.typography.titleSmall,color=if(playing)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(meta.artist?.takeIf{it.isNotBlank()}?:if(loading)"Artist name"else file?.extension?.uppercase()?.ifBlank{"Audio"}?:"Audio",Modifier.skeleton(loading),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Box(Modifier.width(56.dp),contentAlignment=Alignment.CenterEnd){
            androidx.compose.animation.Crossfade(playing,animationSpec=androidx.compose.animation.core.tween(220),label="audioDateOrPlayback"){active->
                if(active)PlayingEqualizer()
                else Text(remember(loading,file?.addedAt,java.util.Locale.getDefault()){if(loading)"Jan 20"else file?.addedAt?.takeIf{it>0}?.let{java.text.SimpleDateFormat("MMM d",java.util.Locale.getDefault()).format(java.util.Date(it))}.orEmpty()},
                    Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
        }
        Box {
            IconButton({menu=true},enabled=!loading){Icon(DriveIcons.MoreVert,"Audio options for ${file?.name.orEmpty()}",Modifier.size(20.dp).skeleton(loading))}

        }
    }
    HorizontalDivider(Modifier.padding(start=LibraryLayout.AudioCover+LibraryLayout.AudioRowGap),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
    }
}

@Composable
private fun PlayingEqualizer(){
    val color=MaterialTheme.colorScheme.primary
    val transition=androidx.compose.animation.core.rememberInfiniteTransition(label="playingBars")
    val phase by transition.animateFloat(initialValue=0f,targetValue=6.283185f,
        animationSpec=androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100,easing=androidx.compose.animation.core.LinearEasing)),label="barPhase")
    androidx.compose.foundation.Canvas(Modifier.size(20.dp).semantics{contentDescription="Playing"}){
        repeat(3){i->val h=size.height*(.3f+.65f*((kotlin.math.sin(phase+i*1.8f)+1f)/2f))
            drawLine(color,androidx.compose.ui.geometry.Offset(size.width*(i+1)/4f,(size.height-h)/2),androidx.compose.ui.geometry.Offset(size.width*(i+1)/4f,(size.height+h)/2),2.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)}
    }
}

package com.infinity.drive.presentation.components

import app.tgdrive.ui.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.common.Formatters

/** Used unchanged by Home and its skeleton, including typography and touch/thumbnail geometry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRecentRow(file:DriveFile?,modifier:Modifier=Modifier,onClick:()->Unit={}) {
    val loading=file==null
    var options by rememberSaveable(file?.id){mutableStateOf(false)}
    var info by remember{mutableStateOf(false)}
    var failure by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    if(options&&file!=null){
        val repository=org.koin.compose.koinInject<com.infinity.drive.domain.repository.FileRepository>()
        ModalBottomSheet(onDismissRequest={options=false}){
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=24.dp)){
                Text(file.name,style=MaterialTheme.typography.titleMedium,maxLines=2,modifier=Modifier.padding(16.dp))
                ListItem(headlineContent={Text("Open")},leadingContent={Icon(DriveIcons.FileOpen,null)},modifier=Modifier.clickable{options=false;onClick()})
                ListItem(headlineContent={Text(if(file.isFavorite)"Remove from favorites"else"Add to favorites")},leadingContent={Icon(DriveIcons.Star,null)},modifier=Modifier.clickable{scope.launch{runCatching{repository.setFilesFavorite(listOf(file.id),!file.isFavorite)}.onFailure{failure="Couldn't update this file. Try again."};options=false}})
                ListItem(headlineContent={Text(if(file.isArchived)"Unarchive"else"Archive")},leadingContent={Icon(DriveIcons.Archive,null)},modifier=Modifier.clickable{scope.launch{runCatching{repository.setFilesArchived(listOf(file.id),!file.isArchived)}.onFailure{failure="Couldn't update this file. Try again."};options=false}})
                ListItem(headlineContent={Text("File details")},leadingContent={Icon(DriveIcons.Info,null)},modifier=Modifier.clickable{options=false;info=true})
            }
        }
    }
    if(info&&file!=null)FileInfoSheet(file,onDismiss={info=false})
    failure?.let{message->AlertDialog(onDismissRequest={failure=null},text={Text(message)},confirmButton={TextButton({failure=null}){Text("Close")}})}
    Column {
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled=!loading,onClick=onClick)
        .padding(vertical=LibraryLayout.HomeRowPadding),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(LibraryLayout.HomeRowGap)) {
        if(file!=null)FileThumbnail(file,Modifier.size(LibraryLayout.HomeCover).clip(RoundedCornerShape(LibraryLayout.CoverCorner)))
        else SkeletonBlock(Modifier.size(LibraryLayout.HomeCover),LibraryLayout.CoverCorner)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(file?.name?:"Weekend photograph.jpg",Modifier.skeleton(loading),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(file?.let{"${it.category.name.lowercase().replaceFirstChar(Char::uppercase)} · ${Formatters.bytes(it.sizeBytes)}"}?:"Photos · 3.8 MB",Modifier.skeleton(loading),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        }
        if(file!=null)Text(java.text.SimpleDateFormat("MMM d, HH:mm",java.util.Locale.getDefault()).format(java.util.Date(file.addedAt)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        IconButton({options=true},enabled=!loading){Icon(DriveIcons.MoreVert,"Options for ${file?.name.orEmpty()}",Modifier.size(20.dp).skeleton(loading))}
    }
    HorizontalDivider(Modifier.padding(start=LibraryLayout.HomeCover+LibraryLayout.HomeRowGap),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
    }
}
@Composable
fun HomeQuickLinks(links:List<LibraryShortcut>,loading:Boolean=false){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        links.forEach{link->
            Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(enabled=!loading,onClick=link.onClick).padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                Icon(link.icon,null,Modifier.size(25.dp).skeleton(loading))
                Text(link.label,Modifier.skeleton(loading),style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}
@Composable
fun RecentSectionHeader(onSeeAll:()->Unit={},loading:Boolean=false){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("Recent files",Modifier.weight(1f).wrapContentWidth(Alignment.Start).skeleton(loading),style=MaterialTheme.typography.titleLarge)
        TextButton(onSeeAll,enabled=!loading){Text("See all",Modifier.skeleton(loading))}
    }
}

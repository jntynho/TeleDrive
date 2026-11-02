package com.infinity.drive.presentation.components

import app.tgdrive.ui.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.common.Formatters

/** Used unchanged by Home and its skeleton, including typography and touch/thumbnail geometry. */
@Composable
fun LibraryRecentRow(file:DriveFile?,modifier:Modifier=Modifier,onClick:()->Unit={}) {
    val loading=file==null
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled=!loading,onClick=onClick)
        .padding(vertical=LibraryLayout.HomeRowPadding),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(LibraryLayout.HomeRowGap)) {
        if(file!=null)FileThumbnail(file,Modifier.size(LibraryLayout.HomeCover).clip(RoundedCornerShape(LibraryLayout.CoverCorner)))
        else SkeletonBlock(Modifier.size(LibraryLayout.HomeCover),LibraryLayout.CoverCorner)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(file?.name?:"Weekend photograph.jpg",Modifier.skeleton(loading),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(file?.let{"${it.category.name.lowercase().replaceFirstChar(Char::uppercase)} · ${Formatters.bytes(it.sizeBytes)}"}?:"Photos · 3.8 MB",Modifier.skeleton(loading),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        }
        Icon(DriveIcons.KeyboardArrowRight,null,Modifier.size(18.dp).skeleton(loading),tint=MaterialTheme.colorScheme.onSurfaceVariant)
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

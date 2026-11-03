package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.common.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shared by Recent files and the browser; details replace the menu instead of stacking modals. */
@Composable
fun FileActionsSheet(file: DriveFile, onDismiss: () -> Unit, onOpen: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val repository=koinInject<FileRepository>()
    val scope=rememberCoroutineScope()
    fun update(action: suspend () -> Unit) {
        if(working)return
        working=true;error=null
        scope.launch {
            try {
                action()
                onDismiss()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                error = "Couldn't update this file. Try again."
            } finally {
                working = false
            }
        }
    }
    if(details){FileInfoSheet(file,onDismiss);return}
    DriveBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,bottom=20.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                FileThumbnail(file,Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(file.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text("${file.category.name.lowercase().replaceFirstChar(Char::uppercase)} · ${Formatters.bytes(file.sizeBytes)}",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(working)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
            }
            error?.let { Text(it,Modifier.padding(horizontal=24.dp,vertical=8.dp),color=MaterialTheme.colorScheme.error) }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
            SheetActionRow("Open",DriveIcons.FileOpen,enabled=!working){onDismiss();onOpen()}
            SheetActionRow(if(file.isFavorite)"Remove from favorites"else"Add to favorites",DriveIcons.Star,enabled=!working){
                update { repository.setFilesFavorite(listOf(file.id),!file.isFavorite) }
            }
            SheetActionRow(if(file.isArchived)"Unarchive"else"Archive",DriveIcons.Archive,enabled=!working){
                update { repository.setFilesArchived(listOf(file.id),!file.isArchived) }
            }
            SheetActionRow("File details",DriveIcons.Info,enabled=!working){details=true}
        }
    }
}

package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.dao.FilePartDao
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.platform.*
import com.infinity.drive.presentation.upload.TelegramChatPicker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.koinInject

val LocalFileFolderNavigation=staticCompositionLocalOf<(String?)->Unit>{{}}

/** A single action surface used in lists, search, Recent, audio and the full-screen viewer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(file:DriveFile,onDismiss:()->Unit,onOpen:()->Unit) {
    val repository=koinInject<FileRepository>()
    val transfers=koinInject<TransferRepository>()
    val telegram=koinInject<TelegramClient>()
    val trash=koinInject<TrashRepository>()
    val parts=koinInject<FilePartDao>()
    val sharer=LocalFileSharer.current
    val linkOpener=LocalTelegramLinkOpener.current
    val navigateFolder=LocalFileFolderNavigation.current
    val scope=rememberCoroutineScope()
    val live by repository.observeFile(file.id).collectAsStateWithLifecycle(file)
    val current=live?:file
    var page by remember{mutableStateOf("actions")}
    var closeSheet by remember{mutableStateOf(onDismiss)}
    var working by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var failure by remember{mutableStateOf(false)}
    fun requireSuccess(result:AppResult<*>){if(result is AppResult.Failure)error("The operation couldn't be completed. Check your connection and try again.")}
    fun run(close:Boolean=true,action:suspend()->Unit){
        if(working)return
        working=true;message=null;failure=false
        scope.launch {
            try{action();if(close)closeSheet()}
            catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){failure=true;message=error.message?:"Couldn't complete this action. Try again.";page="actions"}
            finally{working=false}
        }
    }
    suspend fun obtain():String {
        val latest=repository.getFile(current.id)?:error("This file is no longer available")
        latest.localPath?.let{return it}
        requireSuccess(transfers.enqueueDownload(latest.id))
        message="Downloading a copy to share… You can close this sheet to leave the download in Activity."
        return withTimeoutOrNull(30*60_000L){
            repository.observeFile(latest.id).filterNotNull().first{it.localPath!=null||it.isTrashed}.localPath
        }?:error("The download is still waiting or unavailable. Check Activity, then try Share again.")
    }
    var folder by remember{mutableStateOf<String?>(null)}
    var folders by remember{mutableStateOf<List<DriveFolder>?>(null)}
    var folderError by remember{mutableStateOf<String?>(null)}
    var folderRetry by remember{mutableIntStateOf(0)}
    LaunchedEffect(page,folder,folderRetry){
        if(page!="move")return@LaunchedEffect
        folders=null;folderError=null
        try{repository.observeFolders(folder,true,true).collect{folders=it}}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){folderError="Couldn't load folders."}
    }
    DriveBottomSheet(onDismissRequest=onDismiss) { dismiss ->
        val closeThen=LocalSheetCloseThen.current
        SideEffect{closeSheet=dismiss}
        DriveSheetPage(page){shown ->
        if(shown=="details")Column{
            IconButton({page="actions"}){Icon(DriveIcons.ArrowBack,"Back to file actions")}
            FileInfoSheet(current){page="actions"}
        }
        else if(shown=="send"){
            TelegramChatPicker("Send in Telegram",onDismiss={if(!working)page="actions"}){chat->
                page="actions"
                run {
                    check(!current.isEncrypted){"Download and use Share for encrypted files."}
                    val chatId=current.chatId?:error("Upload this file to Telegram first")
                    val ids=parts.partsOf(current.id).sortedBy{it.partIndex}.mapNotNull{it.messageId}
                        .ifEmpty{listOf(current.messageId?:error("Upload this file first"))}
                    telegram.sendToChat(chat.id,chatId,ids)
                }
            }
        }else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start=if(shown=="actions")24.dp else 8.dp,end=24.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                if(shown!="actions")IconButton({if(!working)page="actions"}){Icon(DriveIcons.ArrowBack,"Back to file actions")}
                FileThumbnail(current,Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(current.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text("${current.category.name.lowercase().replaceFirstChar(Char::uppercase)} · ${Formatters.bytes(current.sizeBytes)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(working)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
            }
            message?.let{Text(it,Modifier.padding(horizontal=24.dp,vertical=8.dp),color=if(failure)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
            when(shown){
                "rename"->{
                    var name by remember(current.id){mutableStateOf(current.name)}
                    OutlinedTextField(name,{name=it},singleLine=true,enabled=!working,label={Text("File name")},isError=!FileNameUtils.isValidName(name),modifier=Modifier.fillMaxWidth().padding(24.dp))
                    Button({run{check(repository.findNameClash(name.trim(),current.folderId)?.id.let{it==null||it==current.id}){"A file already has that name in this folder."};requireSuccess(repository.renameFile(current.id,name.trim()))}},
                        enabled=!working&&name.trim()!=current.name&&FileNameUtils.isValidName(name),modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp)){Text("Rename")}
                }
                "move"->{
                    var folderName by remember{mutableStateOf("My Drive")}
                    LaunchedEffect(folder){folderName=folder?.let{repository.getFolder(it)?.name}?:"My Drive"}
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
                        IconButton({scope.launch{folder=folder?.let{repository.getFolder(it)?.parentId}}},enabled=folder!=null&&!working){Icon(DriveIcons.ArrowBack,"Parent folder")}
                        Text(folderName,style=MaterialTheme.typography.titleMedium)
                    }
                    if(folderError!=null){Text(folderError!!,Modifier.padding(24.dp));TextButton({folderRetry++}){Text("Retry")}}
                    else if(folders==null)LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
                    else LazyColumn(Modifier.heightIn(max=320.dp)){items(folders!!,key={it.id}){child->ListItem(headlineContent={Text(child.name)},leadingContent={Icon(DriveIcons.Folder,null)},trailingContent={Icon(DriveIcons.ChevronRight,null)},modifier=Modifier.clickable(enabled=!working){folder=child.id})}}
                    Button({run{val clash=repository.findNameClash(current.name,folder);check(clash==null||clash.id==current.id){"A file with this name already exists here. Rename it first."};requireSuccess(repository.moveFiles(listOf(current.id),folder))}},
                        enabled=!working&&folder!=current.folderId,modifier=Modifier.fillMaxWidth().padding(24.dp)){Text("Move here")}
                }
                "trash"->{
                    Text("Move this file to Trash? You can restore it until the configured automatic cleanup.",Modifier.padding(24.dp))
                    Button({run{requireSuccess(trash.moveFilesToTrash(listOf(current.id)))}},enabled=!working,modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp)){Text("Move to Trash")}
                }
                else->{
                    SheetActionRow("Open",DriveIcons.FileOpen,enabled=!working){closeThen(onOpen)}
                    SheetActionRow("Download to this phone",DriveIcons.Download,enabled=!working&&(current.hasRemoteCopy||current.hasLocalCopy)){
                        run(false){requireSuccess(transfers.enqueueSaveToPhone(current.id));message="Saving to Downloads. You can follow its progress in Activity."}
                    }
                    SheetActionRow("Send in Telegram",DriveIcons.Share,enabled=!working&&current.hasRemoteCopy&&!current.isEncrypted){page="send"}
                    SheetActionRow(if(current.offlinePinned)"Stop keeping offline"else"Make available offline",DriveIcons.OfflinePin,enabled=!working){
                        run(false){repository.setOfflinePinned(current.id,!current.offlinePinned);if(!current.offlinePinned&&!current.hasLocalCopy&&current.hasRemoteCopy)requireSuccess(transfers.enqueueDownload(current.id));message=if(current.offlinePinned)"This copy is no longer protected from cleanup."else"Offline protection enabled. Follow any pending transfer in Activity."}
                    }
                    SheetActionRow("Share",DriveIcons.Share,enabled=!working){run{val path=obtain();sharer.share(listOf(path),current.mimeType,"Share ${current.name}")}}
                    SheetActionRow(if(current.isFavorite)"Remove from Starred"else"Add to Starred",DriveIcons.Star,enabled=!working){run{repository.setFilesFavorite(listOf(current.id),!current.isFavorite)}}
                    SheetActionRow("Rename",DriveIcons.Edit,enabled=!working){page="rename"}
                    SheetActionRow("Move",DriveIcons.DriveFileMove,enabled=!working){page="move"}
                    SheetActionRow("Show in folder",DriveIcons.FolderOpen,enabled=!working){closeThen{navigateFolder(current.folderId)}}
                    SheetActionRow("Details",DriveIcons.Info,enabled=!working){page="details"}
                    SheetActionRow("Show in Telegram",DriveIcons.OpenInNew,enabled=!working&&current.hasRemoteCopy){
                        run{check(linkOpener.open(telegram.messageLink(current.chatId!!,current.messageId!!))){"No app could open this Telegram link."}}
                    }
                    SheetActionRow(if(current.isArchived)"Unarchive"else"Archive",DriveIcons.Archive,enabled=!working){run{repository.setFilesArchived(listOf(current.id),!current.isArchived)}}
                    SheetActionRow("Move to Trash",DriveIcons.DeleteOutline,enabled=!working){page="trash"}
                }
            }
        }
    }
    }
}

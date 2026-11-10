package com.infinity.drive.presentation.upload

import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.telegram.*
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.common.Formatters
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/** Search is debounced and canceled with the sheet. Loading and failures never look like an empty chat list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChatPicker(title:String,onDismiss:()->Unit,forSending:Boolean=false,onSelected:(TelegramChat)->Unit) {
    val client=koinInject<TelegramClient>()
    val photoSlots=remember(client){Semaphore(2)}
    var query by remember{mutableStateOf("")}
    var rows by remember{mutableStateOf<List<TelegramChat>?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var retry by remember{mutableIntStateOf(0)}
    LaunchedEffect(query,retry){
        rows=null;error=null
        if(query.isNotEmpty())delay(250)
        try{rows=client.listChats(query)}catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){error="Couldn't load your chats. Check your connection and retry."}
    }
    DriveBottomSheet(onDismissRequest=onDismiss) { dismiss ->
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=4.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(dismiss){Icon(DriveIcons.Close,"Close chat picker")}
        }
        TextField(query,{query=it},singleLine=true,placeholder={Text("Search your chats")},
            leadingIcon={Icon(DriveIcons.Search,null)},shape=RoundedCornerShape(14.dp),
            colors=TextFieldDefaults.colors(focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent),
            modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp))
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp)) {
            if(error!=null)item{Column(Modifier.padding(24.dp)){Text(error!!);TextButton({retry++}){Text("Retry")}}}
            else if(rows==null)item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))}
            else if(rows!!.isEmpty())item{Text("No matching chats",Modifier.padding(24.dp))}
            else items(rows!!,key={it.id}){chat->
                ListItem(headlineContent={Text(chat.title,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},
                    leadingContent={TelegramChatAvatar(chat){photoSlots.withPermit{withTimeoutOrNull(8_000){client.fetchChannelPhoto(chat.id)}}}},
                    supportingContent={if(forSending&&!chat.canSendDocuments)Text("Documents are not allowed in this chat")},
                    trailingContent={Icon(DriveIcons.ChevronRight,null)},modifier=Modifier.clickable(enabled=!forSending||chat.canSendDocuments){onSelected(chat)})
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Photos never block chat selection and never replace another chat's identity on row reuse. */
@Composable
fun TelegramChatAvatar(chat:TelegramChat,load:suspend ()->String?) {
    val loader by rememberUpdatedState(load)
    var photo by remember(chat.id,chat.photoFileId,chat.photoPath){mutableStateOf(chat.photoPath)}
    LaunchedEffect(chat.id,chat.photoFileId,chat.photoPath){
        if(photo==null&&chat.photoFileId!=null)try{photo=loader()}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){photo=null}
    }
    val initials=remember(chat.title){chat.title.trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.take(2)
        .joinToString(""){it.substring(0,it.offsetByCodePoints(0,1))}.uppercase().ifBlank{"?"}}
    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center){
        Text(initials,color=MaterialTheme.colorScheme.onSecondaryContainer,style=MaterialTheme.typography.titleMedium)
        if(photo!=null)coil3.compose.AsyncImage(photo,null,Modifier.fillMaxSize().testTag("chat-avatar-photo"),contentScale=ContentScale.Crop)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramImportSheet(target:String?=null,onDismiss:()->Unit) {
    val client=koinInject<TelegramClient>()
    val uploads=koinInject<UploadCoordinator>()
    val settings=koinInject<SettingsRepository>()
    var chat by remember{mutableStateOf<TelegramChat?>(null)}
    var documents by remember{mutableStateOf<List<RemoteDocument>>(emptyList())}
    var selected by remember{mutableStateOf<Set<Long>>(emptySet())}
    var cursor by remember{mutableLongStateOf(0)}
    var page by remember{mutableIntStateOf(0)}
    var end by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var encrypted by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){encrypted=settings.preferences.first().encryptFiles}

    LaunchedEffect(chat?.id,page){
        if(chat==null)return@LaunchedEffect
        loading=true;error=null
        try{
            val result=client.fetchDocuments(chat!!.id,cursor,100)
            documents=(documents+result.documents).distinctBy{it.messageId}
            end=result.nextFromMessageId==0L||result.nextFromMessageId==cursor
            cursor=result.nextFromMessageId
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){error="Couldn't load this chat. It may be protected or unavailable."}
        finally{loading=false}
    }
    DriveBottomSheet(onDismissRequest=onDismiss) { dismiss ->
        val closeThen=LocalSheetCloseThen.current
        DriveSheetPage(chat){shown ->
        if(shown==null)TelegramChatPicker("Save from a Telegram chat",dismiss){chat=it}
        else Column {
        Row(Modifier.fillMaxWidth().padding(end=24.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            IconButton({chat=null;documents=emptyList();selected=emptySet();cursor=0;end=false;page=0}){Icon(DriveIcons.ArrowBack,"Choose another chat")}
            Text(shown.title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton({selected=if(selected.size==documents.size)emptySet()else documents.map{it.messageId}.toSet()},enabled=documents.isNotEmpty()){Text(if(selected.size==documents.size)"Clear"else"Select loaded")}
        }
        Text("Original messages stay in this chat. Copies go into your private drive.",Modifier.padding(horizontal=24.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall)
        if(encrypted)Text("Direct copies remain unencrypted. Turn off file encryption to use this option, or download and upload the originals.",Modifier.padding(horizontal=24.dp),color=MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp)) {
            items(documents,key={it.messageId}){document->
                ListItem(headlineContent={Text(document.fileName)},supportingContent={Text(Formatters.bytes(document.sizeBytes))},leadingContent={Icon(DriveIcons.InsertDriveFile,null)},
                    trailingContent={Checkbox(document.messageId in selected,{checked->selected=if(checked)selected+document.messageId else selected-document.messageId})},
                    modifier=Modifier.clickable{selected=if(document.messageId in selected)selected-document.messageId else selected+document.messageId})
            }
            if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))}
            else if(error!=null)item{Column(Modifier.padding(24.dp)){Text(error!!);TextButton({page++}){Text("Retry")}}}
            else if(!end)item{TextButton({page++},Modifier.fillMaxWidth()){Text("Load more messages")}}
            else if(documents.isEmpty())item{Text("No supported files in this chat",Modifier.padding(24.dp))}
        }
        Button({val chosen=documents.filter{it.messageId in selected};closeThen{uploads.importFromTelegram(chosen,target)}},enabled=selected.isNotEmpty()&&!encrypted,modifier=Modifier.fillMaxWidth().padding(24.dp)){
            Text("Save ${selected.size} ${if(selected.size==1)"file"else"files"}")
        }
    }
    }
    }
}

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.telegram.*
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.LinkedTelegramSource
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.resources.*
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.common.Formatters
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Search is debounced and canceled with the sheet. Loading and failures never look like an empty chat list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChatPicker(
    title: String,
    onDismiss: () -> Unit,
    forSending: Boolean = false,
    linked: List<LinkedTelegramSource> = emptyList(),
    onUnlink: (LinkedTelegramSource) -> Unit = {},
    onOpenLinked: (LinkedTelegramSource) -> Unit = {},
    onSelected: (TelegramChat) -> Unit
) {
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
    val linkedShown = linked.filter { it.title.contains(query.trim(), ignoreCase = true) }
    val unlinkedChats = rows?.filter { chat -> linked.none { it.sourceChatId == chat.id } }
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
            items(linkedShown, key = { "linked-${it.sourceChatId}" }) { source ->
                ListItem(
                    headlineContent = { Text(source.title, maxLines = 1) },
                    supportingContent = { Text(stringResource(Res.string.chat_import_linked)) },
                    leadingContent = { Icon(DriveIcons.Link, null) },
                    trailingContent = {
                        IconButton({ onUnlink(source) }) {
                            Icon(DriveIcons.Close, stringResource(Res.string.chat_import_unlink))
                        }
                    },
                    modifier = Modifier.clickable { onOpenLinked(source) }
                )
            }
            if(error!=null)item{Column(Modifier.padding(24.dp)){Text(error!!);TextButton({retry++}){Text("Retry")}}}
            else if(rows==null)item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))}
            else if(unlinkedChats.isNullOrEmpty() && linkedShown.isEmpty())item{Text("No matching chats",Modifier.padding(24.dp))}
            else items(unlinkedChats.orEmpty(),key={it.id}){chat->
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
fun TelegramImportSheet(target: String? = null, onDismiss: () -> Unit) {
    val client = koinInject<TelegramClient>()
    val uploads = koinInject<UploadCoordinator>()
    val settings = koinInject<SettingsRepository>()
    val preferences by settings.preferences.collectAsState(initial = null)
    val drive = preferences?.takeIf { it.onboardingComplete }?.storageChatId
    val sourceList = preferences?.linkedTelegramSources.orEmpty().filter { it.driveChatId == drive }
    val scope = rememberCoroutineScope()
    var chat by remember(drive) { mutableStateOf<TelegramChat?>(null) }

    fun unlink(source: LinkedTelegramSource) {
        scope.launch {
            settings.update { prefs ->
                prefs.copy(linkedTelegramSources = prefs.linkedTelegramSources.filterNot {
                    it.driveChatId == source.driveChatId && it.sourceChatId == source.sourceChatId
                })
            }
        }
    }

    DriveBottomSheet(onDismissRequest = onDismiss) { dismiss ->
        val closeThen = LocalSheetCloseThen.current
        DriveSheetPage(chat) { shown ->
            if (shown == null) {
                TelegramChatPicker(
                    title = stringResource(Res.string.chat_import_title),
                    onDismiss = dismiss,
                    linked = sourceList,
                    onUnlink = ::unlink,
                    onOpenLinked = { chat = TelegramChat(it.sourceChatId, it.title, canSendDocuments = false) }
                ) { chat = it }
            } else {
                key(drive, shown.id) {
                    val linked = sourceList.firstOrNull { it.sourceChatId == shown.id }
                    var linkable by remember { mutableStateOf<TelegramChat?>(null) }
                    var linking by remember { mutableStateOf(false) }
                    var linkFailed by remember { mutableStateOf(false) }
                    LaunchedEffect(drive, shown.id) {
                        if (drive == null || drive == shown.id || linked != null) return@LaunchedEffect
                        linkable = try {
                            client.ownedPrivateChannel(shown.id)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            null
                        }
                    }
                    TelegramChatFiles(
                        chat = shown,
                        client = client,
                        isLinked = linked != null,
                        encrypted = preferences?.encryptFiles == true,
                        canImport = drive != null,
                        onBack = { chat = null },
                        onImport = { files ->
                            if (drive != null) closeThen { uploads.importFromTelegram(files, target, drive) }
                        },
                        sourceAction = {
                            val source = linkable
                            when {
                                linked != null -> Text(
                                    stringResource(Res.string.chat_import_linked),
                                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                source != null && drive != null -> TextButton(
                                    onClick = {
                                        linking = true
                                        linkFailed = false
                                        scope.launch {
                                            try {
                                                val current = client.ownedPrivateChannel(source.id)
                                                    ?: error("Channel is no longer linkable")
                                                val shortcut = LinkedTelegramSource(drive, current.id, current.title)
                                                settings.update { prefs ->
                                                    if (prefs.storageChatId != drive || !prefs.onboardingComplete) prefs
                                                    else prefs.copy(linkedTelegramSources = (
                                                        prefs.linkedTelegramSources + shortcut
                                                    ).distinctBy { it.driveChatId to it.sourceChatId })
                                                }
                                            } catch (cancel: CancellationException) {
                                                throw cancel
                                            } catch (_: Exception) {
                                                linkFailed = true
                                            } finally {
                                                linking = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    enabled = !linking
                                ) {
                                    Icon(DriveIcons.Link, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(Res.string.chat_import_link))
                                }
                            }
                            if (linkFailed) {
                                Text(
                                    stringResource(Res.string.chat_import_link_failed),
                                    Modifier.padding(horizontal = 24.dp),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramChatFiles(
    chat: TelegramChat,
    client: TelegramClient,
    isLinked: Boolean,
    encrypted: Boolean,
    canImport: Boolean,
    onBack: () -> Unit,
    onImport: (List<RemoteDocument>) -> Unit,
    sourceAction: @Composable () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<FileCategory?>(null) }
    val selected = remember { mutableStateMapOf<Long, RemoteDocument>() }

    val term = query.trim()
    var documents by remember(term, isLinked) { mutableStateOf(emptyList<RemoteDocument>()) }
    var cursor by remember(term, isLinked) { mutableLongStateOf(0) }
    var page by remember(term, isLinked) { mutableIntStateOf(0) }
    var end by remember(term, isLinked) { mutableStateOf(false) }
    var loading by remember(term, isLinked) { mutableStateOf(true) }
    var failed by remember(term, isLinked) { mutableStateOf(false) }

    LaunchedEffect(term, page, isLinked) {
        loading = true
        failed = false
        try {
            if (term.isNotEmpty() && cursor == 0L) delay(300)
            if (isLinked) {
                check(client.ownedPrivateChannel(chat.id) != null) { "Linked channel is no longer available" }
            }
            val result = client.browseChatFiles(chat.id, term, cursor, 100)
            documents = (documents + result.documents).distinctBy(RemoteDocument::messageId)
            end = result.nextFromMessageId == 0L || result.nextFromMessageId == cursor
            cursor = result.nextFromMessageId
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    val visible = remember(documents, category) {
        documents.filter { category == null || FileCategory.fromMimeType(it.mimeType) == category }
    }
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.messageId in selected }

    Column {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(DriveIcons.ArrowBack, stringResource(Res.string.chat_import_back)) }
            Text(chat.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            TextButton(
                onClick = {
                    if (allVisibleSelected) visible.forEach { selected.remove(it.messageId) }
                    else visible.forEach { selected[it.messageId] = it }
                },
                enabled = visible.isNotEmpty()
            ) {
                Text(stringResource(if (allVisibleSelected) Res.string.chat_import_clear_visible else Res.string.chat_import_select_visible))
            }
        }
        sourceAction()
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.chat_import_search)) },
            leadingIcon = { Icon(DriveIcons.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(category == null, { category = null }, { Text(stringResource(Res.string.chat_import_all_types)) })
            FileCategory.entries.forEach { type ->
                FilterChip(category == type, { category = type }, { Text(type.label()) })
            }
        }
        if (encrypted) {
            Text(
                stringResource(Res.string.chat_import_encryption_enabled),
                Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).weight(1f, fill = false)) {
            items(visible, key = { it.messageId }) { document ->
                val checked = document.messageId in selected
                ListItem(
                    headlineContent = { Text(document.fileName) },
                    supportingContent = { Text(Formatters.bytes(document.sizeBytes)) },
                    leadingContent = { Icon(DriveIcons.InsertDriveFile, null) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    modifier = Modifier.clickable {
                        if (checked) selected.remove(document.messageId) else selected[document.messageId] = document
                    }
                )
            }
            when {
                loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp)) }
                failed -> item {
                    Column(Modifier.padding(24.dp)) {
                        Text(stringResource(Res.string.chat_import_unavailable))
                        TextButton({ page++ }) { Text(stringResource(Res.string.chat_import_retry)) }
                    }
                }
                !end -> item {
                    TextButton({ page++ }, Modifier.fillMaxWidth()) { Text(stringResource(Res.string.chat_import_more)) }
                }
                visible.isEmpty() -> item {
                    Text(stringResource(Res.string.chat_import_no_files), Modifier.padding(24.dp))
                }
            }
        }
        Button(
            onClick = { onImport(selected.values.sortedByDescending(RemoteDocument::messageId)) },
            enabled = selected.isNotEmpty() && !encrypted && canImport && !failed,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Text(stringResource(Res.string.chat_import_save_selected, selected.size))
        }
    }
}

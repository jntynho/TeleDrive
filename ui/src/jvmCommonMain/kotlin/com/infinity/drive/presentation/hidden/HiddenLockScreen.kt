package com.infinity.drive.presentation.hidden

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.LocalNavigationInProgress
import com.infinity.drive.core.security.HiddenLockManager
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.PageHeader
import com.infinity.drive.presentation.platform.LocalHiddenDeviceOwnerGate
import com.infinity.drive.presentation.settings.SettingsGroup
import com.infinity.drive.presentation.settings.SettingsSwitchRow
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.settings_hidden_lock
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

@Composable
fun SensitiveScreen(){
    val screens=koinInject<com.infinity.drive.core.security.SensitiveScreens>()
    DisposableEffect(screens){val token=screens.enter();onDispose{token.close()}}
}

private data class HiddenAction(val working:Boolean,val message:String?,val request:(Boolean?)->Unit)

/** A native result belongs to this visible screen, never an outgoing or disposed destination. */
@Composable
private fun rememberHiddenAction(manager:HiddenLockManager,title:String="Hidden",onUnlocked:()->Unit={},onCancelled:()->Unit={}):HiddenAction {
    val unlockedCallback by rememberUpdatedState(onUnlocked)
    val cancelledCallback by rememberUpdatedState(onCancelled)
    val gate=LocalHiddenDeviceOwnerGate.current
    val owner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val navigating by rememberUpdatedState(LocalNavigationInProgress.current)
    var generation by remember{mutableLongStateOf(0)}
    var working by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    fun invalidate(){generation++;working=false}
    DisposableEffect(owner,manager){onDispose{invalidate()}}
    LaunchedEffect(navigating){if(navigating)invalidate()}
    val request:(Boolean?)->Unit={enabled->
        if(!working&&!navigating){
            working=true;message=null
            val request=++generation
            fun current()=request==generation&&!navigating
            try {gate.require(
                title=when(enabled){true->"Enable $title lock";false->"Turn off $title lock";null->"Unlock $title"},
                subtitle="Confirm with your fingerprint or screen lock",
                onDenied={error->if(current()){generation++;working=false;message=error?:"Authentication cancelled. No changes were made.";if(enabled==null)cancelledCallback()}}
            ){
                scope.launch {
                    try {
                        // Device-credential activities can stop the host. Accept their result only
                        // after this same destination resumes; ordinary navigation invalidates it.
                        val resumed=withTimeoutOrNull(5000){owner.lifecycle.currentStateFlow.first{it.isAtLeast(Lifecycle.State.RESUMED)}}
                        if(!current())return@launch
                        if(resumed==null){message="Please authenticate again.";if(enabled==null)cancelledCallback();return@launch}
                        val ticket=manager.state.value.epoch
                        val success=if(enabled==null)manager.unlockWithDevice(ticket)else manager.setDeviceLock(enabled,ticket)
                        if(current()){if(!success){message="Authentication expired. Please try again.";if(enabled==null)cancelledCallback()} else if(enabled==null)unlockedCallback()}
                    }catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){if(current()){message="Couldn't update lock. Please try again.";if(enabled==null)cancelledCallback()}}
                    finally{if(current()){working=false;generation++}}
                }
            }}catch(_:Exception){if(current()){working=false;message="Couldn't open device authentication. Please try again.";if(enabled==null)cancelledCallback()}}
        }
    }
    return HiddenAction(working,message,request)
}

/** Shared setting: exactly the same state and authentication policy in Security and Hidden. */
@Composable
fun HiddenLockSettingRow(manager:HiddenLockManager=koinInject(),title:String="Hidden") {
    val lock by manager.state.collectAsStateWithLifecycle()
    val action=rememberHiddenAction(manager,title)
    Column {
        SettingsSwitchRow(title=if(title=="Hidden")stringResource(Res.string.settings_hidden_lock)else"Lock $title",checked=lock.configured||lock.error,
            onChange={action.request(it)},subtitle="Require fingerprint or screen lock",
            enabled=lock.ready&&!lock.error&&!action.working)
        val message=if(lock.error)"Hidden lock couldn't be read. Your files remain protected."else action.message
        if(action.working)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=16.dp))
        message?.let{Text(it,Modifier.padding(horizontal=16.dp,vertical=10.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable
private fun CollectionRelockSetting(manager:HiddenLockManager,title:String) {
    val lock by manager.state.collectAsStateWithLifecycle()
    var choosing by remember{mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val action=rememberHiddenAction(manager,title,onUnlocked={choosing=true})
    if(lock.configured&&!lock.error) {
        com.infinity.drive.presentation.settings.SettingsClickRow(
            title="Automatically relock",
            subtitle=com.infinity.drive.domain.model.CollectionRelock.fromMinutes(lock.autoLockMinutes).label,
            enabled=!action.working&&!saving,
            onClick={if(lock.unlocked)choosing=true else action.request(null)})
    }
    if(choosing&&!lock.restricted)com.infinity.drive.presentation.components.ChoiceDialog(
        title="Automatically relock",
        options=com.infinity.drive.domain.model.CollectionRelock.entries.map{it.minutes to it.label},
        selected=lock.autoLockMinutes,
        onSelect={minutes->
            choosing=false;saving=true;error=null
            val ticket=lock.epoch
            scope.launch {
                try{if(!manager.setAutoLockMinutes(minutes,ticket))error="Unlock the collection and try again."}
                catch(cancel:CancellationException){throw cancel}
                catch(_:Exception){error="Couldn't save the relock time. Please try again."}
                finally{saving=false}
            }
        },onDismiss={choosing=false})
    LaunchedEffect(lock.restricted){if(lock.restricted)choosing=false}
    (error?:action.message)?.let{Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
}

@Composable
fun HiddenSettingsScreen(onBack:()->Unit) {
    com.infinity.drive.presentation.common.AppBackHandler{onBack()}
    Scaffold(topBar={PageHeader(title={Text("Hidden settings")},navigationIcon={IconButton(onBack){Icon(DriveIcons.ArrowBack,"Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).verticalScroll(rememberScrollState())){
            Text("Protect your private collection",Modifier.padding(horizontal=24.dp,vertical=20.dp),style=MaterialTheme.typography.titleMedium)
            val manager=koinInject<HiddenLockManager>()
            val lock by manager.state.collectAsStateWithLifecycle()
            SettingsGroup{add{HiddenLockSettingRow(manager)};add(visible=lock.configured&&!lock.error){CollectionRelockSetting(manager,"Hidden")}}
            Text("Uses your phone’s screen lock. The selected delay starts when you leave TeleDrive. Never keeps the collection unlocked for this app session; restarting the app always locks it.",
                Modifier.padding(24.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp+padding.calculateBottomPadding()+com.infinity.drive.presentation.navigation.LocalBottomBarHeight.current))
        }
    }
}

/** No intermediary locked page. Denial returns to the source without mounting private content. */
@Composable
fun HiddenLockScreen(onBack:()->Unit,manager:HiddenLockManager=koinInject(),title:String="Hidden") {
    SensitiveScreen()
    val lock by manager.state.collectAsStateWithLifecycle()
    val action=rememberHiddenAction(manager,title,onCancelled=onBack)
    val navigating=LocalNavigationInProgress.current
    LaunchedEffect(lock.ready,lock.configured,lock.unlocked,lock.error,navigating){
        if(lock.ready&&!lock.unlocked&&!navigating){
            if(lock.error)onBack()else if(lock.configured)action.request(null)
        }
    }
    com.infinity.drive.presentation.common.AppBackHandler{onBack()}
    Box(Modifier.fillMaxSize())
}

/** Entry authentication is hosted by Home: cancel never navigates away from Home. */
@Composable
fun rememberCollectionEntry(manager:HiddenLockManager,title:String,onOpen:()->Unit):()->Unit {
    val lock by manager.state.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpen)
    var requested by remember{mutableStateOf(false)}
    val action=rememberHiddenAction(manager,title,onUnlocked={requested=false;open()},onCancelled={requested=false})
    LaunchedEffect(requested,lock.ready){
        if(requested&&lock.ready){
            requested=false
            if(!lock.error){if(!lock.configured||lock.unlocked)open()else action.request(null)}
        }
    }
    return {if(!action.working)requested=true}
}

@Composable
fun CollectionSettingsScreen(type:String,onBack:()->Unit){
    val locks=koinInject<com.infinity.drive.core.security.CollectionLocks>()
    val title=when(type){"FAVORITES"->"Favorites";"ARCHIVED"->"Archived";else->"Trash"}
    val manager=locks.manager(type)
    val lock by manager.state.collectAsStateWithLifecycle()
    Scaffold(topBar={PageHeader(title={Text("$title settings")},navigationIcon={IconButton(onBack){Icon(DriveIcons.ArrowBack,"Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom=com.infinity.drive.presentation.navigation.LocalBottomBarInset.current)){
            Spacer(Modifier.height(20.dp))
            SettingsGroup{add{HiddenLockSettingRow(manager,title)};add(visible=lock.configured&&!lock.error){CollectionRelockSetting(manager,title)}}
            Text("Uses your phone’s screen lock. The delay starts when you leave TeleDrive. Never keeps this collection unlocked for the current app session. Restarting the app always locks it.",Modifier.padding(24.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CollectionAccessGate(type:String,onBack:()->Unit,content:@Composable ()->Unit){
    val locks=koinInject<com.infinity.drive.core.security.CollectionLocks>()
    val manager=locks.manager(type)
    val lock by manager.state.collectAsStateWithLifecycle()
    if(lock.ready&&!lock.error&&(!lock.configured||lock.unlocked))content()
    else HiddenLockScreen(onBack,manager,when(type){"FAVORITES"->"Favorites";"ARCHIVED"->"Archived";else->"Trash"})
}

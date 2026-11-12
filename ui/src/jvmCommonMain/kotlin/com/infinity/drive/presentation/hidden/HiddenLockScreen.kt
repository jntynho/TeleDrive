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
private fun rememberHiddenAction(manager:HiddenLockManager):HiddenAction {
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
                title=when(enabled){true->"Enable Hidden lock";false->"Turn off Hidden lock";null->"Unlock Hidden"},
                subtitle="Confirm with your fingerprint or screen lock",
                onDenied={error->if(current()){generation++;working=false;message=error?:"Authentication cancelled. No changes were made."}}
            ){
                scope.launch {
                    try {
                        // Device-credential activities can stop the host. Accept their result only
                        // after this same destination resumes; ordinary navigation invalidates it.
                        val resumed=withTimeoutOrNull(5000){owner.lifecycle.currentStateFlow.first{it.isAtLeast(Lifecycle.State.RESUMED)}}
                        if(!current())return@launch
                        if(resumed==null){message="Please unlock Hidden again.";return@launch}
                        val ticket=manager.state.value.epoch
                        val success=if(enabled==null)manager.unlockWithDevice(ticket)else manager.setDeviceLock(enabled,ticket)
                        if(current()&&!success)message="Hidden was locked. Please try again."
                    }catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){if(current())message="Couldn't update Hidden lock. Please try again."}
                    finally{if(current()){working=false;generation++}}
                }
            }}catch(_:Exception){if(current()){working=false;message="Couldn't open device authentication. Please try again."}}
        }
    }
    return HiddenAction(working,message,request)
}

/** Shared setting: exactly the same state and authentication policy in Security and Hidden. */
@Composable
fun HiddenLockSettingRow(manager:HiddenLockManager=koinInject()) {
    val lock by manager.state.collectAsStateWithLifecycle()
    val action=rememberHiddenAction(manager)
    Column {
        SettingsSwitchRow(title=stringResource(Res.string.settings_hidden_lock),checked=lock.configured||lock.error,
            onChange={action.request(it)},subtitle="Require fingerprint or screen lock",
            enabled=lock.ready&&!lock.error&&!action.working,icon=DriveIcons.Lock)
        val message=if(lock.error)"Hidden lock couldn't be read. Your files remain protected."else action.message
        if(action.working)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=16.dp))
        message?.let{Text(it,Modifier.padding(horizontal=16.dp,vertical=10.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable
fun HiddenSettingsScreen(onBack:()->Unit) {
    com.infinity.drive.presentation.common.AppBackHandler{onBack()}
    Scaffold(topBar={PageHeader(title={Text("Hidden settings")},navigationIcon={IconButton(onBack){Icon(DriveIcons.ArrowBack,"Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).verticalScroll(rememberScrollState())){
            Text("Protect your private collection",Modifier.padding(horizontal=24.dp,vertical=20.dp),style=MaterialTheme.typography.titleMedium)
            SettingsGroup{add{HiddenLockSettingRow()}}
            Text("Uses the screen lock already set on your phone. Hidden locks again when you leave it or put TeleDrive in the background.",
                Modifier.padding(24.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp+padding.calculateBottomPadding()+com.infinity.drive.presentation.navigation.LocalBottomBarHeight.current))
        }
    }
}

/** The OS owns the credential UI. This surface is only a retry/back state after cancellation. */
@Composable
fun HiddenLockScreen(onBack:()->Unit,manager:HiddenLockManager=koinInject()) {
    SensitiveScreen()
    val lock by manager.state.collectAsStateWithLifecycle()
    val action=rememberHiddenAction(manager)
    val navigating=LocalNavigationInProgress.current
    LaunchedEffect(lock.ready,lock.configured,lock.unlocked,navigating){
        if(lock.ready&&lock.configured&&!lock.unlocked&&!lock.error&&!navigating)action.request(null)
    }
    com.infinity.drive.presentation.common.AppBackHandler{onBack()}
    Scaffold(topBar={PageHeader(title={Text("Hidden")},navigationIcon={IconButton(onBack){Icon(DriveIcons.ArrowBack,"Back")}})}){padding->
        if(!lock.ready){LoadingState(Modifier.padding(padding));return@Scaffold}
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)){
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min=maxHeight).padding(32.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Icon(DriveIcons.Lock,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text("Hidden is locked",style=MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Text(if(lock.error)"Hidden lock couldn't be read. Your files remain protected."else"Use your phone's fingerprint, PIN, pattern or password.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                action.message?.let{Text(it,Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Spacer(Modifier.height(24.dp))
                if(!lock.error)Button({action.request(null)},enabled=!action.working){
                    Icon(DriveIcons.Fingerprint,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(action.working)"Waiting for confirmation…"else"Unlock")
                }
            }
        }
    }
}

package com.infinity.drive.presentation.hidden

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.PageHeader
import com.infinity.drive.presentation.input.*
import com.infinity.drive.presentation.platform.LocalHiddenBiometrics
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SensitiveScreen(){
    val screens=org.koin.compose.koinInject<com.infinity.drive.core.security.SensitiveScreens>()
    DisposableEffect(screens){val token=screens.enter();onDispose{token.close()}}
}

@Composable
fun HiddenLockScreen(onBack:()->Unit,viewModel:HiddenLockViewModel=koinViewModel()){
    SensitiveScreen()
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner,viewModel){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_START)viewModel.refreshDelay()}
        owner.lifecycle.addObserver(observer);viewModel.refreshDelay()
        onDispose{owner.lifecycle.removeObserver(observer);viewModel.stopCountdown()}
    }
    val lock by viewModel.manager.state.collectAsStateWithLifecycle()
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val biometrics=LocalHiddenBiometrics.current
    val clipboard=LocalClipboardManager.current
    val secureClipboard=com.infinity.drive.presentation.platform.LocalSensitiveClipboard.current
    val pinStage=state.stage in setOf(HiddenStage.UNLOCK,HiddenStage.CHOOSE,HiddenStage.CONFIRM)
    val input=remember(pinStage){InlineInputController()}
    val latest by rememberUpdatedState(state)
    LaunchedEffect(pinStage){
        if(pinStage)input.target=InlineTarget("hidden-pin",InlineInputMode.NUMBER,
            {TextFieldValue(latest.entry,TextRange(latest.entry.length))},{viewModel.input(it.text)},
            {!latest.working&&latest.delaySeconds==0L})
    }
    fun back(){if(!viewModel.back())onBack()}
    com.infinity.drive.presentation.common.AppBackHandler {back()}
    Scaffold(topBar={PageHeader(title={Text(if(state.stage==HiddenStage.MANAGE)"Hidden lock"else"Hidden")},navigationIcon={IconButton(::back){Icon(DriveIcons.ArrowBack,"Back")}})}){padding->
        if(!lock.ready){LoadingState(Modifier.padding(padding));return@Scaffold}
        if(lock.error){Column(Modifier.padding(padding).padding(24.dp)){
            Icon(DriveIcons.Lock,null);Text("Hidden is locked",style=MaterialTheme.typography.headlineSmall)
            Text("The lock data couldn't be read on this device. It has not been reset or bypassed.")
        };return@Scaffold}
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)){
            val compact=maxHeight<620.dp
            val information:@Composable (Modifier)->Unit={modifier->
                Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){Icon(
                        if(state.stage==HiddenStage.RECOVER||state.stage==HiddenStage.RECOVERY_OPTIONS)DriveIcons.Key else DriveIcons.Lock,
                        null,Modifier.padding(18.dp).size(36.dp))}
                    val heading=when(state.stage){HiddenStage.UNLOCK->"Your private space";HiddenStage.CHOOSE->if(lock.configured)"Create a new PIN"else"Protect Hidden";HiddenStage.CONFIRM->"Confirm your PIN";HiddenStage.SAVE_KEY->"Save your recovery key";HiddenStage.RECOVER,HiddenStage.RECOVERY_OPTIONS->"Forgot your PIN?";HiddenStage.MANAGE->"Your Hidden lock"}
                    AnimatedContent(heading,transitionSpec={fadeIn(tween(180)) togetherWith fadeOut(tween(140))},label="hidden-heading"){Text(it,style=MaterialTheme.typography.headlineSmall)}
                    Text(when(state.stage){HiddenStage.UNLOCK->"Enter your 6-digit PIN";HiddenStage.CHOOSE->"Choose 6 digits to protect Hidden";HiddenStage.CONFIRM->"Enter the same PIN again";HiddenStage.SAVE_KEY->"Keep this key somewhere safe, outside TeleDrive. You can use it to create a new PIN.";HiddenStage.RECOVER->"Enter the recovery key you saved during setup.";HiddenStage.RECOVERY_OPTIONS->"Verify your identity to create a new PIN.";HiddenStage.MANAGE->"This lock protects Hidden inside TeleDrive on this device."},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(pinStage){Row(horizontalArrangement=Arrangement.spacedBy(15.dp),modifier=Modifier.padding(vertical=14.dp).semantics{password();contentDescription="PIN, ${state.entry.length} of 6 digits entered"}){
                        repeat(6){index->val fill by androidx.compose.animation.animateColorAsState(if(index<state.entry.length)MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent,animationSpec=tween(130),label="pin-dot")
                            Box(Modifier.size(12.dp).background(fill,CircleShape)
                            .border(1.5.dp,MaterialTheme.colorScheme.onSurfaceVariant,CircleShape))}
                    }}
                    if(state.stage==HiddenStage.SAVE_KEY){
                        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){
                            Text(state.recovery.chunked(4).chunked(4).joinToString("\n"){it.joinToString(" ")},Modifier.padding(18.dp),style=MaterialTheme.typography.titleMedium,fontFamily=FontFamily.Monospace)
                        }
                        TextButton({val key=state.recovery.chunked(4).joinToString("-");if(secureClipboard!=null)secureClipboard.copy(key)else clipboard.setText(AnnotatedString(key))}){Icon(DriveIcons.ContentPaste,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Copy recovery key")}
                        Row(Modifier.fillMaxWidth().clickable{viewModel.acknowledge(!state.saved)},verticalAlignment=Alignment.CenterVertically){Checkbox(state.saved,viewModel::acknowledge);Text("I saved this key outside TeleDrive",Modifier.weight(1f))}
                        Button(viewModel::save,enabled=state.saved&&!state.working,modifier=Modifier.fillMaxWidth()){Text("Finish setup")}
                        Text(if(lock.configured)"Your previous recovery key is replaced when you finish."else"This key is shown only during setup.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(state.stage==HiddenStage.RECOVERY_OPTIONS){
                        RecoveryAction("Use recovery key","The key you saved during setup",DriveIcons.Key,viewModel::enterRecovery)
                        if(lock.biometrics&&biometrics?.available==true)RecoveryAction("Use fingerprint","If enabled for recovery",DriveIcons.Fingerprint){viewModel.biometric(biometrics,true)}
                        Text("Your files stay locked until verification succeeds.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton({viewModel.back()}){Text("Back to unlock")}
                    }
                    if(state.stage==HiddenStage.RECOVER){CompositionLocalProvider(LocalInlineInput provides input){
                        InlineInputField(state.entry,viewModel::input,"Recovery key",Modifier.fillMaxWidth(),mode=InlineInputMode.HEX,password=true,enabled=!state.working&&state.delaySeconds==0L)
                    }}
                    if(state.stage==HiddenStage.MANAGE){
                        RecoveryAction("Change PIN","Verify access before changing the lock",DriveIcons.Key,viewModel::changePin)
                        if(biometrics?.available==true)RecoveryAction(if(lock.biometrics)"Disable fingerprint"else"Enable fingerprint","Optional on this device",DriveIcons.Fingerprint){viewModel.toggleBiometric(biometrics)}
                        RecoveryAction("Lock now","Hide this collection immediately",DriveIcons.Lock){viewModel.manager.lock()}
                    }
                    state.message?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
                    if(state.delaySeconds>0)Text("Try again in ${state.delaySeconds}s",color=MaterialTheme.colorScheme.error)
                    if(state.working)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                }
            }
            val keyboard:@Composable (Modifier)->Unit={modifier->
                if(pinStage||state.stage==HiddenStage.RECOVER){Column(modifier.padding(horizontal=24.dp).padding(bottom=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
                    if(state.stage==HiddenStage.RECOVER)Button(viewModel::verifyRecovery,enabled=state.entry.length==32&&!state.working&&state.delaySeconds==0L,modifier=Modifier.fillMaxWidth()){Text("Verify recovery key")}
                    InlineKeyboard(input,compact=compact,numericLeading=if(pinStage&&state.stage==HiddenStage.UNLOCK&&lock.biometrics&&biometrics?.available==true)"fingerprint"else null,
                        onLeadingAction={if(biometrics!=null)viewModel.biometric(biometrics)})
                    if(state.stage==HiddenStage.UNLOCK)TextButton(viewModel::recoveryOptions,enabled=!state.working){Text("Forgot PIN?")}
                    Text("Locks when you leave Hidden",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
            }
            if(maxWidth>600.dp&&maxWidth>maxHeight)Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){information(Modifier.weight(1f).fillMaxHeight());keyboard(Modifier.weight(1f))}
            else Column(Modifier.fillMaxSize()){information(Modifier.weight(1f).fillMaxWidth());keyboard(Modifier.fillMaxWidth())}
        }
    }
}

@Composable
private fun RecoveryAction(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
    Surface(onClick=onClick,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(25.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(DriveIcons.ChevronRight,null)}
    }
}

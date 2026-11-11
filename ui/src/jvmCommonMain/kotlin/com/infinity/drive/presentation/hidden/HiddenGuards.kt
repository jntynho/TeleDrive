package com.infinity.drive.presentation.hidden

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.presentation.collection.CollectionScreen
import com.infinity.drive.presentation.collection.CollectionType
import com.infinity.drive.presentation.preview.PreviewSequence
import com.infinity.drive.presentation.components.LoadingState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HiddenCollectionGate(onBack:()->Unit,onOpenFile:(String,PreviewSequence)->Unit){
    val vm:HiddenLockViewModel=koinViewModel()
    val lock by vm.manager.state.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    SensitiveScreen()
    if(lock.unlocked&&ui.stage==HiddenStage.UNLOCK&&!ui.working)CollectionScreen(onBack,onOpenFile,onManageHidden=vm::manage)
    else HiddenLockScreen(onBack,vm)
}

/** Fail closed immediately on every epoch; the actual viewer isn't composed while access is checked. */
@Composable
fun HiddenContentGuard(fileId:String?=null,folderId:String?=null,onBack:()->Unit,content:@Composable ()->Unit){
    val access=koinInject<HiddenContentAccess>()
    val lock by access.lock.state.collectAsStateWithLifecycle()
    val protected by access.protectedFolders.collectAsStateWithLifecycle()
    var allowed by remember(fileId,folderId,lock.epoch,lock.restricted,protected){mutableStateOf<Boolean?>(null)}
    LaunchedEffect(fileId,folderId,lock.epoch,lock.restricted,protected){
        if(fileId!=null)access.observeCanRead(fileId).collect{allowed=it&&access.canRead(fileId)}
        else allowed=access.canReadFolder(folderId)
    }
    when(allowed){
        null->LoadingState(Modifier)
        false->HiddenLockScreen(onBack)
        true->{if(lock.unlocked)SensitiveScreen();content()}
    }
}

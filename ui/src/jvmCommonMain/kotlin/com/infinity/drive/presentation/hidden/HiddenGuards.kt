package com.infinity.drive.presentation.hidden

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.core.security.HiddenContentAccess
import com.infinity.drive.presentation.collection.CollectionScreen
import com.infinity.drive.presentation.collection.CollectionType
import com.infinity.drive.presentation.preview.PreviewSequence
import com.infinity.drive.presentation.components.LoadingState
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HiddenCollectionGate(onBack:()->Unit,onOpenFile:(String,PreviewSequence)->Unit,onManageHidden:()->Unit){
    val manager=koinInject<com.infinity.drive.core.security.HiddenLockManager>()
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    val lock by manager.state.collectAsStateWithLifecycle()
    SensitiveScreen()
    if(lock.ready&&!lock.error&&(!lock.configured||lock.unlocked))CollectionScreen(onBack,onOpenFile,onManageHidden=onManageHidden,listState=listState)
    else HiddenLockScreen(onBack,manager)
}

/** Keep public content mounted across unrelated lock epochs; private revocation stays synchronous. */
@Composable
fun HiddenContentGuard(fileId:String?=null,folderId:String?=null,onBack:()->Unit,content:@Composable ()->Unit){
    val access=koinInject<HiddenContentAccess>()
    val lock by access.lock.state.collectAsStateWithLifecycle()
    val protected by access.protectedFolders.collectAsStateWithLifecycle()
    val allowed:Boolean?=if(fileId!=null){
        val observed by remember(access,fileId){access.observeAccessFile(fileId).map{Pair<String?,com.infinity.drive.domain.model.DriveFile?>(fileId,it)}}
            .collectAsStateWithLifecycle(initialValue=null to null)
        when {
            !lock.ready||observed.first!=fileId->null
            observed.second==null->false
            else->access.snapshotCanRead(observed.second!!)
        }
    }else when {
        !lock.ready->null
        !lock.restricted||folderId==null->true
        else->access.snapshotCanReadFolder(folderId)
    }
    when(allowed){
        null->LoadingState(Modifier)
        false->HiddenLockScreen(onBack)
        true->{if(lock.unlocked)SensitiveScreen();content()}
    }
}

package com.infinity.drive.core.security

import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FolderDao
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileQuerySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable

/** Read-side policy only: background backup/publication retains raw repository access. */
@OptIn(ExperimentalCoroutinesApi::class)
class HiddenContentAccess(val lock:HiddenLockManager,private val files:FileDao,private val folders:FolderDao):Closeable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var folderIndexReady=false
    // Observe the recursive set once per folder mutation, not once per thumbnail or list row.
    val protectedFolders:StateFlow<Set<String>?> = lock.state.filter{it.ready}.map{it.configured||it.error}.distinctUntilChanged()
        .flatMapLatest{configured->
            folderIndexReady=false
            if(configured)folders.observeProtectedFolderIds().map{it.toSet()}.onEach{folderIndexReady=true}else flowOf(emptySet())
        }
        .stateIn(scope,SharingStarted.Eagerly,null)
    override fun close(){scope.cancel()}
    fun observeCanRead(id:String):Flow<Boolean> = combine(files.observeById(id),lock.state,protectedFolders){file,state,protected->
        state.ready&&(!state.restricted||(protected!=null&&file!=null&&!file.isHidden&&file.folderId !in protected))
    }
    suspend fun canReadSnapshot(file:DriveFile):Boolean {
        lock.awaitReady()
        if(!lock.state.value.restricted)return true
        if(file.isHidden)return false
        val folder=file.folderId?:return true
        return if(folderIndexReady)folder !in protectedFolders.filterNotNull().first()else !protectedFolder(folder)
    }
    fun query(spec:FileQuerySpec):FileQuerySpec = if(lock.state.value.restricted&&spec.respectHiddenLock)
        spec.copy(showHidden=false,excludeHiddenAncestors=true) else spec
    suspend fun protectedFolder(id:String?):Boolean {
        if(id==null)return false
        return folders.isProtectedFolder(id)
    }
    suspend fun protectedFile(file:DriveFile):Boolean = file.isHidden||protectedFolder(file.folderId)
    suspend fun canRead(id:String):Boolean {
        lock.awaitReady()
        if(!lock.state.value.restricted)return true
        val file=files.byId(id)?:return false
        return !file.isHidden&&!protectedFolder(file.folderId)
    }
    suspend fun notificationName(id:String?,fallback:String):String {
        lock.awaitReady()
        if(!lock.state.value.configured&&!lock.state.value.error)return fallback
        val row=id?.let{files.byId(it)}?:return "File transfer"
        return if(row.isHidden||protectedFolder(row.folderId))"Hidden file"else fallback
    }
    suspend fun canReadFolder(id:String?):Boolean {
        lock.awaitReady();return !lock.state.value.restricted||!protectedFolder(id)
    }
}

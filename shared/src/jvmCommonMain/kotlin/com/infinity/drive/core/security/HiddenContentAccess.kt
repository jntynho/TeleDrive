package com.infinity.drive.core.security

import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.FolderDao
import com.infinity.drive.data.mapper.toDomain
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileQuerySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable

/** Read-side policy only: background backup/publication retains raw repository access. */
@OptIn(ExperimentalCoroutinesApi::class)
class HiddenContentAccess(val lock:HiddenLockManager,private val files:FileDao,private val folders:FolderDao,
    val notesLock: HiddenLockManager? = null
):Closeable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var folderIndexReady=false
    // Observe the recursive set once per folder mutation, not once per thumbnail or list row.
    val protectedFolders:StateFlow<Set<String>?> = lock.state.filter{it.ready}.map{it.configured||it.error}.distinctUntilChanged()
        .flatMapLatest{configured->
            folderIndexReady=false
            if(configured)folders.observeProtectedFolderIds().map{it.toSet() as Set<String>?}
                .onStart{emit(null)}.onEach{folderIndexReady=it!=null}else flowOf(emptySet())
        }
        .stateIn(scope,SharingStarted.Eagerly,null)
    val notesRestricted: Flow<Boolean> = notesLock?.state?.map { it.restricted }?.distinctUntilChanged() ?: flowOf(false)
    private fun isNoteRestricted(name: String) = name.endsWith(".md", ignoreCase = true) && notesLock?.state?.value?.restricted == true
    override fun close(){scope.cancel()}
    /** Synchronous fail-closed policy for already observed rows; no per-thumbnail database work. */
    fun snapshotCanRead(file:DriveFile):Boolean? {
        val state=lock.state.value
        if(!state.ready)return null
        if (isNoteRestricted(file.name)) return false
        if(!state.restricted)return true
        if(file.isHidden)return false
        return snapshotCanReadFolder(file.folderId)
    }
    fun snapshotCanReadFolder(id:String?):Boolean? {
        val state=lock.state.value
        if(!state.ready)return null
        if(!state.restricted||id==null)return true
        if(!folderIndexReady)return null
        return protectedFolders.value?.let{id !in it}
    }
    fun observeAccessFile(id:String):Flow<DriveFile?> = files.observeById(id).map{it?.toDomain()}
    fun observeCanRead(id:String):Flow<Boolean> = combine(files.observeById(id),lock.state,protectedFolders,notesRestricted){file,state,protected,_ ->
        state.ready && file?.let { !isNoteRestricted(it.name) } != false &&(!state.restricted||(protected!=null&&file!=null&&!file.isHidden&&file.folderId !in protected))
    }
    suspend fun canReadSnapshot(file:DriveFile):Boolean {
        lock.awaitReady()
        notesLock?.awaitReady()
        if (isNoteRestricted(file.name)) return false
        if(!lock.state.value.restricted)return true
        if(file.isHidden)return false
        val folder=file.folderId?:return true
        return if(folderIndexReady)folder !in protectedFolders.filterNotNull().first()else !protectedFolder(folder)
    }
    fun query(spec: FileQuerySpec): FileQuerySpec {
        val visible = if (lock.state.value.restricted && spec.respectHiddenLock)
            spec.copy(showHidden = false, excludeHiddenAncestors = true) else spec
        return if (notesLock?.state?.value?.restricted == true) visible.copy(excludeNotes = true) else visible
    }
    suspend fun protectedFolder(id:String?):Boolean {
        if(id==null)return false
        return folders.isProtectedFolder(id)
    }
    suspend fun protectedFile(file:DriveFile):Boolean = file.isHidden||protectedFolder(file.folderId)
    suspend fun canRead(id:String):Boolean {
        lock.awaitReady()
        notesLock?.awaitReady()
        if (!lock.state.value.restricted && notesLock?.state?.value?.restricted != true) return true
        val file = files.byId(id) ?: return false
        return !isNoteRestricted(file.name) &&
            (!lock.state.value.restricted || (!file.isHidden && !protectedFolder(file.folderId)))
    }
    suspend fun notificationName(id:String?,fallback:String):String {
        lock.awaitReady()
        val restrictedNotes = notesLock?.state?.value?.restricted == true
        val configuredHidden = lock.state.value.configured || lock.state.value.error
        if (!restrictedNotes && !configuredHidden) return fallback
        val row = id?.let { files.byId(it) } ?: return "File transfer"
        if (isNoteRestricted(row.name)) return "Note transfer"
        if (!configuredHidden) return fallback
        return if(row.isHidden||protectedFolder(row.folderId))"Hidden file"else fallback
    }
    suspend fun canReadFolder(id:String?):Boolean {
        lock.awaitReady();return !lock.state.value.restricted||!protectedFolder(id)
    }
}

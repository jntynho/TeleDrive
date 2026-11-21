package app.tgdrive.rebuild

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.StreamCrypto
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.files.NoteDraftStore
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.ChannelRepository
import com.infinity.drive.presentation.channels.ChannelsViewModel
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.presentation.note.NoteEditorViewModel
import com.infinity.drive.presentation.search.SearchViewModel
import com.infinity.drive.presentation.search.ContentMatches
import com.infinity.drive.presentation.search.DriveTextSearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class, manifest=Config.NONE)
class UiStateRegressionTest {
    private inline fun <reified T> fake(crossinline answer:(String,Array<out Any?>)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,args ->
            answer(method.name,args?:emptyArray())
        } as T

    private fun note(files:FileRepository,transfers:TransferRepository,fileId:String?=null) = NoteEditorViewModel(
        files,transfers,SavedStateHandle(mapOf("fileId" to fileId,"folderId" to null,"title" to "Note","sharedText" to null)))
    private fun clear(vm:ViewModel){ViewModelStore().apply{put("tested",vm);clear()}}

    @Test fun searchClearsStaleResultsImmediatelyAndStopsOnBlankQuery()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=DriveFile("first",null,"first.jpg",10,"image/jpeg",FileCategory.IMAGE,
            null,null,null,null,null,null,BackupState.NONE,false,false,false,false,null,null,null,null,
            createdAt=1,modifiedAt=1,addedAt=1)
        val source=MutableStateFlow(listOf(fixture))
        val queries=mutableListOf<String?>()
        val repo=fake<FileRepository>{method,args->when(method){
            "observeFiles"->{queries+=(args[0] as FileQuerySpec).nameQuery;source}
            "searchFolders"->flowOf(emptyList<DriveFolder>())
            else->error(method)
        }}
        val vm=SearchViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.uiState.collect()}
        try {
            vm.setQuery("first");advanceTimeBy(250);runCurrent()
            assertEquals(listOf("first"),queries)
            assertFalse(vm.uiState.value.searching)
            assertEquals(listOf("first"),vm.uiState.value.results.map{it.id})
            vm.setQuery("second");runCurrent()
            assertTrue(vm.uiState.value.searching)
            assertTrue(vm.uiState.value.results.isEmpty())
            assertEquals(listOf("first"),queries)
            vm.setQuery("");runCurrent()
            assertFalse(vm.uiState.value.searched)
            assertFalse(vm.uiState.value.searching)
            advanceTimeBy(300);runCurrent()
            assertEquals(listOf("first"),queries)
        }finally{clear(vm);Dispatchers.resetMain()}
    }

    @Test fun searchFailureCanRetryAndSortAloneDoesNotStartASearch()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var fail=true
        var calls=0
        val repo=fake<FileRepository>{method,_->when(method){
            "observeFiles"->{calls++;flow<List<DriveFile>>{if(fail)error("database unavailable")else emit(emptyList())}}
            "searchFolders"->flowOf(emptyList<DriveFolder>())
            else->error(method)
        }}
        val vm=SearchViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.uiState.collect()}
        try{
            vm.setSort(FileSortField.SIZE,SortDirection.ASCENDING);advanceTimeBy(300);runCurrent()
            assertEquals(0,calls);assertFalse(vm.uiState.value.searched)
            vm.setQuery("photo");advanceTimeBy(300);runCurrent()
            assertNotNull(vm.uiState.value.error);assertFalse(vm.uiState.value.searching)
            fail=false;vm.retry();advanceTimeBy(300);runCurrent()
            assertEquals(2,calls);assertNull(vm.uiState.value.error);assertFalse(vm.uiState.value.searching)
            vm.setBackedUpOnly(true);vm.setNotBackedUpOnly(true);runCurrent()
            assertFalse(vm.uiState.value.filters.backedUpOnly)
            assertTrue(vm.uiState.value.filters.notBackedUpOnly)
        }finally{clear(vm);Dispatchers.resetMain()}
    }

    @Test fun contentSearchRunsOnlyWhenRequestedAndClearsStaleMatches() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val file = DriveFile("note", null, "meeting.md", 10, "text/markdown", FileCategory.DOCUMENT,
            null, null, null, null, null, null, BackupState.NONE, false, false, false, false,
            null, null, null, null, createdAt = 1, modifiedAt = 1, addedAt = 1)
        val repo = fake<FileRepository> { method, args ->
            when (method) {
                "observeFiles" -> flowOf(if ((args[0] as FileQuerySpec).nameQuery == null) listOf(file) else emptyList())
                "searchFolders" -> flowOf(emptyList<DriveFolder>())
                else -> error(method)
            }
        }
        val terms = mutableListOf<String>()
        val search = object : DriveTextSearch {
            override val supportsPdf = true
            override suspend fun search(query: String, candidates: List<DriveFile>): ContentMatches {
                terms += query
                return ContentMatches(candidates, skipped = 2)
            }
        }
        val vm = SearchViewModel(repo, textSearch = search)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        try {
            vm.setQuery("proposal")
            advanceTimeBy(300); runCurrent()
            assertTrue(terms.isEmpty())
            assertTrue(vm.uiState.value.results.isEmpty())
            vm.toggleContentSearch()
            advanceTimeBy(300); runCurrent()
            assertEquals(listOf("proposal"), terms)
            assertEquals(listOf(file), vm.uiState.value.results)
            assertEquals(2, vm.uiState.value.contentSkipped)
            vm.setQuery("agenda")
            runCurrent()
            assertTrue(vm.uiState.value.results.isEmpty())
            advanceTimeBy(300); runCurrent()
            assertEquals(listOf("proposal"), terms)
            assertFalse(vm.uiState.value.searchingContents)
        } finally {
            clear(vm)
            Dispatchers.resetMain()
        }
    }

    @Test fun smartFoldersSaveSearchRulesAndRejectOpeningAnotherDrive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = MutableStateFlow(UserPreferences(storageChatId = -1L))
        val repo = fake<FileRepository> { method, _ -> error(method) }
        val settings = fake<SettingsRepository> { method, args ->
            when (method) {
                "getPreferences" -> preferences
                "update" -> {
                    @Suppress("UNCHECKED_CAST")
                    preferences.value = (args[0] as (UserPreferences) -> UserPreferences)(preferences.value)
                    Unit
                }
                else -> error(method)
            }
        }
        val vm = SearchViewModel(repo, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.smartFolders.collect() }
        try {
            vm.setQuery("#travel")
            vm.setCategory(FileCategory.IMAGE)
            assertTrue(vm.saveSmartFolder("Travel photos"))
            assertFalse(vm.saveSmartFolder("travel photos"))
            runCurrent()
            val folder = vm.smartFolders.value.single()
            assertEquals("#travel", folder.query)
            assertEquals(FileCategory.IMAGE, folder.category)
            assertEquals(-1L, folder.chatId)
            preferences.value = preferences.value.copy(storageChatId = -2L)
            vm.removeSmartFolder(folder)
            runCurrent()
            assertTrue(vm.smartFolders.value.isEmpty())
            assertEquals(folder, preferences.value.smartFolders.single())
        } finally {
            clear(vm)
            Dispatchers.resetMain()
        }
    }

    @Test fun newNoteUsesTheTitleSuppliedByTextRecognition() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val directory = createTempDirectory("note-title-").toFile()
        val storage = object : AppStoragePaths {
            override val filesDir = directory
            override val cacheDir = directory
            override val externalCacheDir = null
            override val externalStorageRoot = null
        }
        val keys = fake<WrappedKeyRepository> { method, _ ->
            when (method) {
                "get" -> null
                else -> error(method)
            }
        }
        val settings = fake<SettingsRepository> { method, _ ->
            when (method) {
                "getPreferences" -> flowOf(UserPreferences(storageChatId = -1L))
                else -> error(method)
            }
        }
        val vm = NoteEditorViewModel(
            fake<FileRepository> { method, _ -> error(method) },
            fake<TransferRepository> { method, _ -> error(method) },
            SavedStateHandle(mapOf("title" to "Meeting agenda", "sharedText" to "Review the milestone")),
            draftStore = NoteDraftStore(storage, keys, StreamCrypto()),
            settings = settings
        )
        try {
            val state = vm.uiState.first { !it.loading }
            assertEquals("Meeting agenda", state.title)
        } finally {
            clear(vm)
            Dispatchers.resetMain()
            directory.deleteRecursively()
        }
    }

    @Test fun noteKeepsExactBodyAndBuffersSaveCompletionUntilACollectorArrives()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var savedBody:String?=null
        val repo=fake<FileRepository>{method,args->when(method){
            "saveNote"->{savedBody=args[3] as String;AppResult.Success("new-id")}
            else->error(method)
        }}
        val transfers=fake<TransferRepository>{_,_->AppResult.Success("upload")}
        val vm=note(repo,transfers)
        try{
            val body="  code starts here\n\n"
            vm.setBody(body);assertTrue(vm.uiState.value.dirty)
            vm.setBody("");assertFalse(vm.uiState.value.dirty)
            vm.setBody(body);vm.save();runCurrent()
            assertEquals(body,savedBody);assertFalse(vm.uiState.value.dirty)
            assertEquals(Unit,vm.saved.first())
        }finally{clear(vm);Dispatchers.resetMain()}
    }

    @Test fun savingDuringUploadKeepsAnEncryptedRevisionAndQueuesItOnceIdle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val directory = createTempDirectory("note-pending-").toFile()
        val storage = object : AppStoragePaths {
            override val filesDir = directory
            override val cacheDir = directory
            override val externalCacheDir = null
            override val externalStorageRoot = null
        }
        val keys = fake<WrappedKeyRepository> { _, _ -> ByteArray(32) { 4 } }
        val drafts = NoteDraftStore(storage, keys, StreamCrypto())
        val busy = MutableStateFlow(true)
        var readRemote = true
        val written = mutableListOf<String>()
        val files = fake<FileRepository> { method, args ->
            when (method) {
                "readNote" -> if (readRemote) AppResult.Success("original") else AppResult.Failure(AppError.NetworkUnavailable)
                "observeFile" -> flowOf(null)
                "saveNote" -> if (busy.value) AppResult.Failure(AppError.NoteTransferInProgress) else {
                    written += args[3] as String
                    AppResult.Success("existing")
                }
                else -> error(method)
            }
        }
        var queued = 0
        val transfers = fake<TransferRepository> { method, _ -> when (method) {
            "observeUnfinishedForFile" -> busy
            "enqueueUpload" -> { queued++; AppResult.Success("upload") }
            else -> error(method)
        } }
        val settings = fake<SettingsRepository> { _, _ -> flowOf(UserPreferences(storageChatId = -1L)) }
        fun model() = NoteEditorViewModel(
            files, transfers, SavedStateHandle(mapOf("fileId" to "existing", "title" to "Note")),
            draftStore = drafts, settings = settings
        )
        var editor = model()
        try {
            editor.uiState.first { !it.loading }
            editor.setBody("revised")
            editor.save()
            editor.uiState.first { !it.saving }
            assertFalse(editor.uiState.value.dirty)
            assertEquals("revised", drafts.read("-1:note:existing:pending")?.body)
            assertTrue(written.isEmpty())
            clear(editor)

            readRemote = false
            busy.value = false
            editor = model()
            editor.uiState.first { !it.loading && !it.pendingSync && it.uploadState == BackupState.QUEUED }
            assertEquals(listOf("revised"), written)
            assertEquals(1, queued)
            assertNull(drafts.read("-1:note:existing:pending"))
        } finally {
            clear(editor)
            Dispatchers.resetMain()
            directory.deleteRecursively()
        }
    }

    @Test fun failedNoteLoadCannotOverwriteTheOriginalAndCanRetry()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var fail=true
        var saves=0
        val repo=fake<FileRepository>{method,_->when(method){
            "readNote"->if(fail)AppResult.Failure(AppError.NotFound)else AppResult.Success("original")
            "saveNote"->{saves++;AppResult.Success("existing")}
            else->error(method)
        }}
        val vm=note(repo,fake<TransferRepository>{_,_->error("no upload expected")},"existing")
        try{
            runCurrent();assertNotNull(vm.uiState.value.loadError)
            vm.setBody("replacement");vm.save();runCurrent();assertEquals(0,saves)
            fail=false;vm.retryLoad();runCurrent()
            assertNull(vm.uiState.value.loadError);assertEquals("original",vm.uiState.value.body)
            assertFalse(vm.uiState.value.dirty)
            vm.setBody("edit");vm.setBody("original");assertFalse(vm.uiState.value.dirty)
        }finally{clear(vm);Dispatchers.resetMain()}
    }

    @Test fun uploadQueueFailureKeepsTheLocallySavedNoteAndReusesItsId()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val ids=mutableListOf<String?>()
        val repo=fake<FileRepository>{method,args->when(method){
            "saveNote"->{ids+=args[0] as String?;AppResult.Success("saved-locally")}
            else->error(method)
        }}
        val vm=note(repo,fake<TransferRepository>{_,_->AppResult.Failure(AppError.AuthenticationRequired)})
        try{
            vm.setBody("first");vm.save();runCurrent()
            assertFalse(vm.uiState.value.saving);assertFalse(vm.uiState.value.isNew)
            assertEquals("first",vm.uiState.value.body)
            vm.setBody("second");vm.save();runCurrent()
            assertEquals(listOf(null,"saved-locally"),ids)
        }finally{clear(vm);Dispatchers.resetMain()}
    }

    @Test fun unexpectedSaveFailureRetainsTheDraftAndReleasesBusyState()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm=note(fake<FileRepository>{_,_->error("disk failure")},fake<TransferRepository>{_,_->error("not reached")})
        try{
            vm.setBody("unsaved draft");vm.save();runCurrent()
            assertFalse(vm.uiState.value.saving);assertTrue(vm.uiState.value.dirty)
            assertEquals("unsaved draft",vm.uiState.value.body)
        }finally{clear(vm);Dispatchers.resetMain()}
    }
    @Test fun refreshingAnEmptyDriveListDoesNotCreateAChannelAndCanRecoverObservation()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var failRead=true
        var observations=0
        var creations=0
        val repo=fake<ChannelRepository>{method,_->when(method){
            "observeChannels"->{observations++;flow<List<DriveChannel>>{if(failRead)error("read failed")else emit(emptyList())}}
            "pruneDeleted"->AppResult.Success(0)
            "refresh"->AppResult.Success(Unit)
            "create"->{creations++;error("must be explicit")}
            else->error(method)
        }}
        val vm=ChannelsViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.channels.collect()}
        try{
            runCurrent();assertNotNull(vm.error.value);assertEquals(0,creations)
            failRead=false;vm.refresh();runCurrent()
            assertEquals(2,observations);assertNull(vm.error.value)
            assertFalse(vm.loading.value);assertFalse(vm.refreshing.value);assertEquals(0,creations)
        }finally{clear(vm);Dispatchers.resetMain()}
    }

    @Test fun rapidGalleryDensityChangesCommitImmediatelyAndPersistOnlyTheLatestChoice()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val prefs=MutableStateFlow(UserPreferences())
        val saved=mutableListOf<Int>()
        val settings=fake<com.infinity.drive.domain.repository.SettingsRepository>{method,args->when(method){
            "getPreferences"->prefs
            "update"->{@Suppress("UNCHECKED_CAST") val transform=args[0] as (UserPreferences)->UserPreferences
                prefs.value=transform(prefs.value);saved+=prefs.value.galleryGridSize;Unit}
            else->error(method)
        }}
        val vm=com.infinity.drive.presentation.gallery.GalleryViewModel(SavedStateHandle(),fake{_,_->error("files")},
            fake{_,_->error("trash")},fake{_,_->error("transfers")},settings,fake{_,_->error("sync")})
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.uiState.collect()}
        try {
            runCurrent();assertTrue(vm.uiState.value.loaded)
            vm.zoomOut();vm.zoomOut();vm.zoomIn();vm.zoomIn();vm.zoomOut();runCurrent()
            assertEquals(4,vm.uiState.value.gridSize);assertTrue(saved.isEmpty())
            advanceTimeBy(321);runCurrent()
            assertEquals(listOf(4),saved);assertEquals(4,vm.uiState.value.gridSize)
            vm.zoomIn();runCurrent();assertEquals(3,vm.uiState.value.gridSize)
            advanceTimeBy(321);runCurrent();assertEquals(listOf(4,3),saved)
        }finally{clear(vm);Dispatchers.resetMain()}
    }

    @Test fun deletedGalleryAnchorFallsBackToItsSavedNeighborInTheCorrectQueryWindow()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val prefs=MutableStateFlow(UserPreferences())
        val settings=fake<com.infinity.drive.domain.repository.SettingsRepository>{method,_->when(method){"getPreferences"->prefs;else->error(method)}}
        val row=DriveFile("neighbor",null,"neighbor.jpg",10,"image/jpeg",FileCategory.IMAGE,null,null,null,null,null,null,
            BackupState.NONE,false,false,false,false,null,null,null,null,createdAt=1,modifiedAt=1,addedAt=1)
        var requested:FileQuerySpec?=null
        val files=fake<FileRepository>{method,args->when(method){
            "getFile"->if(args[0]=="neighbor")row else null
            "indexOfFile"->44
            "pagedFiles"->{requested=args[0] as FileQuerySpec;flowOf(androidx.paging.PagingData.empty<DriveFile>())}
            else->error(method)
        }}
        val vm=com.infinity.drive.presentation.gallery.GalleryViewModel(SavedStateHandle(),files,fake{_,_->error("trash")},
            fake{_,_->error("transfer")},settings,fake{_,_->error("sync")})
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.uiState.collect()}
        try {
            runCurrent()
            val position=vm.positionFor(ViewMode.GRID)
            position.anchorId="deleted";position.neighborIds=listOf("neighbor")
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.pagedMedia.collect()}
            runCurrent()
            assertEquals("neighbor",position.anchorId)
            assertEquals(44,requested?.initialLoadIndex)
        }finally{clear(vm);Dispatchers.resetMain()}
    }

}

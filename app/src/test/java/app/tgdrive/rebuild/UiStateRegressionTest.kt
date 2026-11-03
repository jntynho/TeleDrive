package app.tgdrive.rebuild

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.ChannelRepository
import com.infinity.drive.presentation.channels.ChannelsViewModel
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.domain.repository.TransferRepository
import com.infinity.drive.presentation.note.NoteEditorViewModel
import com.infinity.drive.presentation.search.SearchViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

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

}

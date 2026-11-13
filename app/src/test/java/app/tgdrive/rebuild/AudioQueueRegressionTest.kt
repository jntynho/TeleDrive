package app.tgdrive.rebuild

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.crypto.*
import com.infinity.drive.core.dispatchers.DispatcherProvider
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.media.TelegramDataSourceFactory
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.dao.*
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.presentation.audio.AndroidAudioPlayback
import com.infinity.drive.presentation.audio.AudioViewModel
import com.infinity.drive.presentation.platform.*
import com.infinity.drive.presentation.preview.PreviewContentResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class AudioQueueRegressionTest {
    private inline fun <reified T> fake(crossinline answer:(String,Array<out Any?>)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,args->answer(method.name,args?:emptyArray())} as T
    private fun file(id:String)=DriveFile(id,null,"$id.mp3",10,"audio/mpeg",FileCategory.AUDIO,null,null,null,null,null,null,BackupState.NONE,
        false,false,false,false,null,null,60_000,null,createdAt=1,modifiedAt=1,addedAt=1)
    private fun settings()=fake<SettingsRepository>{name,_->when(name){"getPreferences"->flowOf(UserPreferences());else->error(name)}}
    private fun openHiddenAccess():com.infinity.drive.core.security.HiddenContentAccess {
        val store=object:com.infinity.drive.core.security.HiddenLockStore {
            override suspend fun read():com.infinity.drive.core.security.HiddenLockRecord?=null
            override suspend fun write(record:com.infinity.drive.core.security.HiddenLockRecord){}
            override suspend fun clear(){}
        }
        return com.infinity.drive.core.security.HiddenContentAccess(com.infinity.drive.core.security.HiddenLockManager(store),
            fake<FileDao>{_,_->null},fake<FolderDao>{_,_->null})
    }
    private fun TestScope.player(files:FileRepository):AndroidAudioPlayback {
        val context=RuntimeEnvironment.getApplication()
        val dispatcher=StandardTestDispatcher(testScheduler)
        val paths=object:AppStoragePaths {override val filesDir=context.filesDir;override val cacheDir=context.cacheDir;override val externalCacheDir:java.io.File?=null;override val externalStorageRoot:java.io.File?=null}
        val keys=fake<WrappedKeyRepository>{_,_->null}
        val telegram=fake<TelegramClient>{name,_->error("Unexpected Telegram call $name")}
        val dispatchers=object:DispatcherProvider {override val main=dispatcher;override val io=dispatcher;override val default=dispatcher}
        val resolver=PreviewContentResolver(paths,telegram,StreamCrypto(),keys,fake<CacheDao>{_,_->Unit},fake<FilePartDao>{_,_->emptyList<Any>()},settings(),dispatchers,openHiddenAccess())
        return AndroidAudioPlayback(context,resolver,TelegramDataSourceFactory(telegram,StreamCrypto(),keys),files,dispatcher,CoroutineScope(SupervisorJob()+dispatcher))
    }
    @Test fun `trash audio requires a viewer lease and stops when that viewer closes`()=runTest {
        val trashed=file("trashed").copy(trashedAt=1)
        val files=fake<FileRepository>{name,_->when(name){
            "observeFile"->flowOf(trashed)
            "getFile"->trashed
            "audioTags"->null
            else->error(name)
        }}
        val player=player(files)
        player.play(trashed);runCurrent()
        assertNull(player.state.value.file)
        player.previewTrash(trashed);runCurrent()
        assertEquals(trashed.id,player.state.value.file?.id)
        assertTrue(player.state.value.file!!.isTrashed)
        player.endTrashPreview(trashed.id);runCurrent()
        assertNull(player.state.value.file)
    }

    @Test fun `rapid Next presses reserve each destination and stale lookups cannot restart an intermediate track`()=runTest {
        val tracks=listOf("a","b","c","d").associateWith(::file)
        val pending=linkedMapOf<String,Continuation<DriveFile?>>()
        val files=fake<FileRepository>{name,args->when(name){
            "observeFile"->flowOf(tracks[args[0]])
            "getFile"->{@Suppress("UNCHECKED_CAST") val continuation=args.last() as Continuation<DriveFile?>;pending[args[0] as String]=continuation;COROUTINE_SUSPENDED}
            else->error(name)
        }}
        val player=player(files)
        try {
            player.play(tracks.getValue("a"));player.setQueue(tracks.keys.toList());runCurrent()
            player.next();runCurrent();player.next();runCurrent();player.next();runCurrent()
            assertEquals(listOf("b","c","d"),pending.keys.toList())
            pending.getValue("d").resume(tracks["d"]);runCurrent()
            assertEquals("d",player.state.value.file?.id)
            pending.getValue("b").resume(tracks["b"]);pending.getValue("c").resume(tracks["c"]);runCurrent()
            assertEquals("d",player.state.value.file?.id)
        }finally{player.release()}
    }
    @Test fun `manual skipping preserves pause and repeat-all previous wraps symmetrically`()=runTest {
        val tracks=listOf("a","b","c").associateWith(::file)
        val files=fake<FileRepository>{name,args->when(name){"observeFile"->flowOf(tracks[args[0]]);"getFile"->tracks[args[0]];else->error(name)}}
        val player=player(files)
        try {
            player.play(tracks.getValue("a"));player.setQueue(tracks.keys.toList());runCurrent();player.pause()
            player.cycleRepeat();player.previous();runCurrent()
            assertEquals("c",player.state.value.file?.id);assertFalse(player.state.value.playRequested)
            player.next();runCurrent();assertEquals("a",player.state.value.file?.id);assertFalse(player.state.value.playRequested)
        }finally{player.release()}
    }
    @Test fun `reopening the current song never toggles its play state`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var toggles=0;var starts=0
        val track=file("a")
        val playback=object:AudioPlayback {
            override val state=MutableStateFlow(AudioPlaybackState(file=track,playing=true,playRequested=true))
            override fun play(file:DriveFile){starts++}
            override fun toggle(){toggles++}
            override fun setQueue(ids:List<String>){}
            override fun pause(){};override fun next(){};override fun stop(){};override fun seek(positionMs:Long){}
            override suspend fun metadata(file:DriveFile)=AudioMetadata()
        }
        val repo=fake<FileRepository>{name,_->when(name){"fileIds"->listOf("a");else->error(name)}}
        val vm=AudioViewModel(SavedStateHandle(),repo,settings())
        try {vm.play(track,playback);advanceTimeBy(200);runCurrent();assertEquals(0,toggles);assertEquals(0,starts);assertTrue(playback.state.value.playing)}
        finally{ViewModelStore().apply{put("tested",vm);clear()};Dispatchers.resetMain()}
    }
    @Test fun `channel switches restore their paused sessions without starting the other channel`()=runTest {
        val track=file("saved-${System.nanoTime()}").copy(chatId=101)
        val files=fake<FileRepository>{name,args->when(name){"observeFile"->flowOf(track.takeIf{it.id==args[0]});"getFile"->track.takeIf{it.id==args[0]};else->error(name)}}
        val player=player(files)
        try {
            player.updateLibraryContext(101,true);runCurrent()
            player.play(track);player.setQueue(listOf(track.id));runCurrent();player.pause();runCurrent()
            player.updateLibraryContext(202,true);runCurrent();assertNull(player.state.value.file)
            player.updateLibraryContext(101,true);runCurrent()
            assertEquals(track.id,player.state.value.file?.id)
            assertFalse(player.state.value.playRequested)
            assertFalse(player.state.value.playing)
            player.stop();runCurrent()
        }finally{player.release()}
    }

}

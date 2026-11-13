package app.tgdrive.rebuild

import android.app.Application
import android.media.MediaMetadataRetriever
import com.infinity.drive.core.crypto.*
import com.infinity.drive.core.dispatchers.DispatcherProvider
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.media.TelegramDataSourceFactory
import com.infinity.drive.core.security.*
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.data.local.dao.*
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.presentation.audio.AndroidAudioPlayback
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
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class AudioMetadataIndexTest {
    private inline fun <reified T> fake(crossinline answer:(String,Array<out Any?>)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,args->answer(method.name,args?:emptyArray())} as T
    private fun file(path:String?=null)=DriveFile("metadata-${System.nanoTime()}",null,"Track.mp3",12,"audio/mpeg",FileCategory.AUDIO,path,null,1,2,"remote",null,BackupState.BACKED_UP,
        false,false,false,false,null,null,null,null,createdAt=1,modifiedAt=1,addedAt=1)
    private fun TestScope.reader(files:FileRepository):AndroidAudioPlayback {
        val context=RuntimeEnvironment.getApplication()
        val dispatcher=StandardTestDispatcher(testScheduler)
        val paths=object:AppStoragePaths{override val filesDir=context.filesDir;override val cacheDir=context.cacheDir;override val externalCacheDir:File?=null;override val externalStorageRoot:File?=null}
        val keys=fake<WrappedKeyRepository>{_,_->null}
        val telegram=fake<TelegramClient>{name,_->error("Unexpected Telegram call $name")}
        val dispatchers=object:DispatcherProvider{override val main=dispatcher;override val io=dispatcher;override val default=dispatcher}
        val store=object:HiddenLockStore{override suspend fun read():HiddenLockRecord?=null;override suspend fun write(record:HiddenLockRecord){};override suspend fun clear(){}}
        val hidden=HiddenContentAccess(HiddenLockManager(store,ownerScope=backgroundScope),fake<FileDao>{_,_->null},fake<FolderDao>{_,_->null})
        val settings=fake<SettingsRepository>{name,_->if(name=="getPreferences")flowOf(UserPreferences())else error(name)}
        val resolver=PreviewContentResolver(paths,telegram,StreamCrypto(),keys,fake<CacheDao>{_,_->Unit},fake<FilePartDao>{_,_->emptyList<Any>()},settings,dispatchers,hidden)
        return AndroidAudioPlayback(context,resolver,TelegramDataSourceFactory(telegram,StreamCrypto(),keys),files,dispatcher,backgroundScope)
    }
    @Test fun durableTagsDisplayForCloudOnlyFilesWithoutOpeningTelegram()=runTest {
        val files=fake<FileRepository>{name,_->when(name){
            "audioTags"->AudioTags("Midnight City","M83",243_000)
            "observeFile"->flowOf(null)
            else->error(name)
        }}
        val result=reader(files).basicMetadata(file())
        assertEquals("Midnight City",result.title);assertEquals("M83",result.artist);assertTrue(result.resolved)
    }
    @Test fun concurrentRowsShareOneInspectionAndMovingTheLocalCopyKeepsTheTextCache()=runTest {
        val local=File.createTempFile("audio-index-",".mp3").apply{writeText("placeholder")}
        var writes=0
        val files=fake<FileRepository>{name,_->when(name){"audioTags"->null;"storeAudioMetadata"->{writes++;Unit};"observeFile"->flowOf(null);else->error(name)}}
        try {
            ShadowMediaMetadataRetriever.addMetadata(local.path,MediaMetadataRetriever.METADATA_KEY_TITLE,"One read")
            val track=file(local.path);val player=reader(files)
            val results=coroutineScope{List(8){async{player.basicMetadata(track)}}.awaitAll()}
            runCurrent()
            assertTrue(results.all{it.title=="One read"});assertEquals(1,writes)
            assertEquals("One read",player.basicMetadata(track.copy(localPath="/a/moved/copy.mp3")).title)
            runCurrent();assertEquals(1,writes)
        } finally {local.delete()}
    }
    @Test fun temporaryFailureHasACooldownAndNeverBecomesAPermanentNoTagsRecord()=runTest {
        val local=File.createTempFile("audio-failed-",".mp3").apply{writeText("placeholder")}
        var writes=0
        val files=fake<FileRepository>{name,_->when(name){"audioTags"->null;"storeAudioMetadata"->{writes++;Unit};"observeFile"->flowOf(null);else->error(name)}}
        try {
            val track=file(local.path);val player=reader(files)
            ShadowMediaMetadataRetriever.addException(DataSource.toDataSource(local.path),IllegalArgumentException("Temporary read error"))
            assertTrue(player.basicMetadata(track).failed)
            ShadowMediaMetadataRetriever.reset()
            ShadowMediaMetadataRetriever.addMetadata(local.path,MediaMetadataRetriever.METADATA_KEY_ARTIST,"Recovered")
            assertNull(player.basicMetadata(track).artist);runCurrent();assertEquals(0,writes)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
            assertEquals("Recovered",player.basicMetadata(track).artist);runCurrent();assertEquals(1,writes)
        } finally {local.delete()}
    }
}

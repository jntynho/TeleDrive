package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.telegram.TelegramChat
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.audio.FullscreenAudioPlayer
import com.infinity.drive.presentation.audio.AudioLibraryRow
import com.infinity.drive.presentation.audio.LibraryAudioMiniPlayer
import com.infinity.drive.presentation.platform.*
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import com.infinity.drive.presentation.upload.TelegramChatAvatar
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MediaSurfaceRegressionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private val favorites=mutableListOf<Pair<String,Boolean>>()
    private fun file(id:String)=DriveFile(id,null,"$id.mp3",10,"audio/mpeg",FileCategory.AUDIO,null,null,null,null,null,null,BackupState.NONE,
        false,false,false,false,null,null,120_000,null,createdAt=1,modifiedAt=1,addedAt=1)
    private val playback=MutableStateFlow(AudioPlaybackState(file=file("first"),durationMs=120_000,canSkipNext=true,canSkipPrevious=true))
    private val player=object:AudioPlayback {
        override val state=playback
        override fun play(file:DriveFile){playback.value=playback.value.copy(file=file)}
        override fun setQueue(ids:List<String>){}
        override fun toggle(){playback.value=playback.value.copy(playRequested=!playback.value.playRequested)}
        override fun pause(){playback.value=playback.value.copy(playRequested=false)}
        override fun next(){}
        override fun stop(){playback.value=AudioPlaybackState()}
        override fun seek(positionMs:Long){playback.value=playback.value.copy(positionMs=positionMs)}
        override suspend fun metadata(file:DriveFile)=AudioMetadata()
    }
    @Before fun start(){
        val repo=Proxy.newProxyInstance(FileRepository::class.java.classLoader,arrayOf(FileRepository::class.java)){_,method,args->
            when(method.name){
                "setFilesFavorite"->{favorites.add((args!![0] as List<*>).first() as String to (args[1] as Boolean));Unit}
                else->error("Unexpected repository call ${method.name}")
            }
        } as FileRepository
        startKoin{modules(module{single<FileRepository>{repo}})}
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }
    @After fun stop(){activity.pause().stop().destroy();stopKoin()}
    private fun install(largeText:Boolean=false){activity.get().setContent{DriveInfinityTheme{
        CompositionLocalProvider(LocalAudioPlayback provides player){
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(largeText)2f else 1f)){
                Box(if(largeText)Modifier.size(320.dp,640.dp)else Modifier.fillMaxSize()){
                    val current by playback.collectAsState()
                    FullscreenAudioPlayer(current.file!!,true,Modifier.fillMaxSize())
                }
            }
        }
    }}}
    @Test fun audioControlsAndArtworkKeepTheirBoundsAcrossLyricsLoadingErrorsAndTrackChanges(){
        install()
        val controls=compose.onNodeWithTag("audio-controls").fetchSemanticsNode().boundsInRoot
        val cover=compose.onNodeWithTag("audio-cover").fetchSemanticsNode().boundsInRoot
        val states=listOf(
            playback.value.copy(metadata=AudioMetadata(lyrics="[00:00.00]First\n[00:30.00]Second\n[01:00.00]Third"),positionMs=30_000),
            playback.value.copy(file=file("second"),loading=true),
            playback.value.copy(error="Couldn't play this audio. Retry."),
            playback.value.copy(metadata=AudioMetadata(lyrics="A long plain lyric with no timestamps that must not resize the player controls."))
        )
        states.forEach{next->
            compose.runOnUiThread{playback.value=next}
            compose.waitForIdle()
            assertEquals(controls,compose.onNodeWithTag("audio-controls").fetchSemanticsNode().boundsInRoot)
            assertEquals(cover,compose.onNodeWithTag("audio-cover").fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithContentDescription("Sleep timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Playback options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show lyrics").assertDoesNotExist()
    }
    @Test fun favoriteButtonPersistsTheCurrentTrackWithoutMovingPlaybackControls(){
        install()
        val before=compose.onNodeWithTag("audio-play").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Add to favorites").performClick()
        compose.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
        assertEquals(listOf("first" to true),favorites)
        assertEquals(before,compose.onNodeWithTag("audio-play").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun largeTextKeepsTheTransportAndLyricsSeparated(){
        install(largeText=true)
        val lyrics=compose.onNodeWithTag("audio-lyrics-slot").fetchSemanticsNode().boundsInRoot
        val controls=compose.onNodeWithTag("audio-controls").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(lyrics.bottom<=controls.top)
        compose.onNodeWithContentDescription("Next audio").assertIsDisplayed()
    }
    @Test fun audioRowShowsArtistOrFormatAlongsideTheActualLibraryDate(){
        val row=file("voice").copy(addedAt=1_700_000_000_000)
        activity.get().setContent{DriveInfinityTheme{CompositionLocalProvider(LocalAudioPlayback provides player){
            AudioLibraryRow(row,currentMetadata=AudioMetadata(artist=" "))
        }}}
        compose.onNodeWithText("MP3").assertIsDisplayed()
        compose.onNodeWithText(java.text.SimpleDateFormat("MMM d",java.util.Locale.getDefault()).format(java.util.Date(row.addedAt))).assertIsDisplayed()
        activity.get().setContent{DriveInfinityTheme{CompositionLocalProvider(LocalAudioPlayback provides player){
            AudioLibraryRow(row,currentMetadata=AudioMetadata(artist="M83"))
        }}}
        compose.onNodeWithText("M83").assertIsDisplayed()
        compose.onNodeWithText("Unknown artist").assertDoesNotExist()
    }
    @Test fun miniPlayerDismissStopsPlaybackAndRemovesTheCard(){
        activity.get().setContent{DriveInfinityTheme{CompositionLocalProvider(LocalAudioPlayback provides player){LibraryAudioMiniPlayer()}}}
        compose.onNodeWithContentDescription("Dismiss audio player").assertIsDisplayed().performClick()
        compose.waitUntil(3000){compose.onAllNodesWithTag("audio-mini-player").fetchSemanticsNodes().isEmpty()}
        assertNull(playback.value.file)
    }
    @Test fun pendingAvatarIsCancelledWhenTheChatChangesAndNeverReusesAnotherIdentity(){
        var chat by mutableStateOf(TelegramChat(1,"Alice North",7))
        var cancelled=false
        activity.get().setContent{DriveInfinityTheme{TelegramChatAvatar(chat){try{awaitCancellation()}finally{cancelled=true}}}}
        compose.onNodeWithText("AN").assertIsDisplayed()
        compose.runOnUiThread{chat=TelegramChat(2,"Mira")}
        compose.onNodeWithText("M").assertIsDisplayed()
        compose.onNodeWithText("AN").assertDoesNotExist()
        assertTrue(cancelled)
    }
    @Test fun cachedAvatarIsClearedWhenTheNextChatHasNoPhoto(){
        var chat by mutableStateOf(TelegramChat(1,"Alice",photoPath="/tmp/nonexistent-avatar.jpg"))
        activity.get().setContent{DriveInfinityTheme{TelegramChatAvatar(chat){null}}}
        compose.onNodeWithTag("chat-avatar-photo").assertExists()
        compose.runOnUiThread{chat=TelegramChat(2,"Mira")}
        compose.onNodeWithTag("chat-avatar-photo").assertDoesNotExist()
        compose.onNodeWithText("M").assertIsDisplayed()
    }

}

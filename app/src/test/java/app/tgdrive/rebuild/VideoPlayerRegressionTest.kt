package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.*
import androidx.media3.common.FlagSet
import com.infinity.drive.presentation.preview.*
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import java.lang.reflect.Proxy
import java.io.File

/** Tests the actual controls against a deterministic Media3 player, without Telegram or codecs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class VideoPlayerRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    @Before fun start() { activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible() }
    @After fun stop() { activity.pause().stop().destroy() }

    private class FakeVideo {
        var requested=true
        var position=12_000L
        var duration=120_000L
        var state=Player.STATE_BUFFERING
        var volume=.35f
        var speed=1f
        var repeat=Player.REPEAT_MODE_OFF
        var seekable=true
        private val listeners=mutableListOf<Player.Listener>()
        val player:Player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)){proxy,method,args->
            val a=args?:emptyArray()
            when(method.name){
                "getPlayWhenReady"->requested
                "isPlaying"->requested && state==Player.STATE_READY
                "getPlaybackState"->state
                "getDuration"->duration
                "getCurrentPosition"->position
                "getPlaybackParameters"->PlaybackParameters(speed)
                "getVolume"->volume
                "getRepeatMode"->repeat
                "getCurrentTracks"->Tracks.EMPTY
                "getAvailableCommands"->Player.Commands.Builder().addAllCommands().build()
                "isCommandAvailable"->if(a[0]==Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)seekable else true
                "getPlaybackSuppressionReason"->Player.PLAYBACK_SUPPRESSION_REASON_NONE
                "getTrackSelectionParameters"->TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
                "addListener"->{listeners.add(a[0] as Player.Listener);null}
                "removeListener"->{listeners.remove(a[0] as Player.Listener);null}
                "pause"->{requested=false;notifyChange();null}
                "play"->{requested=true;notifyChange();null}
                "setPlayWhenReady"->{requested=a[0] as Boolean;notifyChange();null}
                "seekTo"->{position=a.last() as Long;notifyChange();null}
                "setPlaybackSpeed"->{speed=a[0] as Float;notifyChange();null}
                "setVolume"->{volume=a[0] as Float;notifyChange();null}
                "setRepeatMode"->{repeat=a[0] as Int;notifyChange();null}
                "toString"->"FakeVideo"
                "equals"->proxy===a[0]
                "hashCode"->System.identityHashCode(proxy)
                else->error("Unexpected player call ${method.name}")
            }
        } as Player
        fun notifyChange(){listeners.toList().forEach {
            it.onPlaybackStateChanged(state)
            it.onPlayWhenReadyChanged(requested,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            it.onIsPlayingChanged(requested&&state==Player.STATE_READY)
            it.onEvents(player,Player.Events(FlagSet.Builder().add(Player.EVENT_PLAYBACK_STATE_CHANGED).build()))
        }}
    }

    private fun install(video:FakeVideo,compact:Boolean=false) {
        activity.get().setContent { DriveInfinityTheme(darkTheme=true) {
            Box((if(compact)Modifier.size(393.dp,300.dp)else Modifier.fillMaxSize()).background(androidx.compose.ui.graphics.Color.Black)) {
                PlayerControls(video.player,true,false,PlayerResizeMode.FIT,{},{})
            }
        }}
        compose.waitForIdle()
    }

    @Test fun bufferingCanBePausedAndStartedAgain() {
        val video=FakeVideo();install(video)
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed().performClick()
        assertFalse(video.requested)
        compose.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        assertTrue(video.requested)
    }
    @Test fun unknownDurationAndUnseekableStreamsDisableSeekInsteadOfJumpingToZero() {
        val video=FakeVideo().apply{duration=C.TIME_UNSET};install(video)
        compose.onNodeWithContentDescription("Seek video").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Forward 10 seconds").assertIsNotEnabled()
        compose.runOnUiThread{video.duration=120_000;video.seekable=false;video.notifyChange()}
        compose.onNodeWithContentDescription("Seek video").assertIsNotEnabled()
    }
    @Test fun seeksAreClampedAndMuteRestoresThePreviousVolume() {
        val video=FakeVideo().apply{position=115_000;state=Player.STATE_READY};install(video)
        compose.onNodeWithContentDescription("Forward 10 seconds").performClick()
        assertEquals(120_000,video.position)
        compose.onNodeWithContentDescription("Mute").performClick()
        assertEquals(0f,video.volume)
        compose.onNodeWithContentDescription("Unmute").performClick()
        assertEquals(.35f,video.volume)
    }
    @Test fun compactControlsDoNotOverlapTheSeekTrack() {
        val video=FakeVideo();install(video,compact=true)
        val play=compose.onNodeWithContentDescription("Pause").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val seek=compose.onNodeWithContentDescription("Seek video").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Transport must sit above the seek track",play.bottom<=seek.top)
        val screenshot=File("build/design-previews/video-controls-compact.png")
        screenshot.parentFile?.mkdirs()
        screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun lifecycleSuspensionPreservesIntentAndTheUserPausedState() {
        val video=FakeVideo();val checkpoint=VideoPlaybackCheckpoint()
        checkpoint.suspend(video.player)
        assertFalse(video.requested)
        checkpoint.capture(video.player) // progress polling while stopped must not erase intent
        checkpoint.resume(video.player)
        assertTrue(video.requested)
        video.player.pause()
        checkpoint.suspend(video.player)
        checkpoint.resume(video.player)
        assertFalse(video.requested)
    }
    @Test fun decoderReplacementRestoresPositionSpeedVolumeAndRepeat() {
        val video=FakeVideo().apply{speed=1.5f;repeat=Player.REPEAT_MODE_ONE}
        val checkpoint=VideoPlaybackCheckpoint()
        checkpoint.capture(video.player)
        val replacement=FakeVideo().apply{position=0;volume=1f;requested=false}
        checkpoint.restore(replacement.player)
        assertEquals(12_000,replacement.position)
        assertEquals(1.5f,replacement.speed)
        assertEquals(.35f,replacement.volume)
        assertEquals(Player.REPEAT_MODE_ONE,replacement.repeat)
        assertTrue(replacement.requested)
    }
    @Test fun seekPolicyHandlesUnknownDurationAndExtremePositions() {
        assertEquals(22_000,videoSeekTarget(12_000,10_000,C.TIME_UNSET))
        assertEquals(0,videoSeekTarget(3_000,-10_000,120_000))
        assertEquals(120_000,videoSeekTarget(115_000,10_000,120_000))
        assertEquals(Long.MAX_VALUE,videoSeekTarget(Long.MAX_VALUE-10,10_000,C.TIME_UNSET))
    }
}

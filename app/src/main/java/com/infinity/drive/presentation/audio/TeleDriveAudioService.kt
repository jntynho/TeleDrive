package com.infinity.drive.presentation.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.koin.android.ext.android.inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.infinity.drive.presentation.preview.VideoSessionPool

/** Lock-screen, headset and notification controls share the application's actual player. */
@androidx.annotation.OptIn(UnstableApi::class)
class TeleDriveAudioService : MediaSessionService() {
    private val playback: AndroidAudioPlayback by inject()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        fun audioControls():Player = object: ForwardingPlayer(playback.media3Player) {
            override fun getAvailableCommands(): Player.Commands {
                val state=playback.state.value
                val builder=super.getAvailableCommands().buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                if(state.canSkipNext)builder.add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                if(state.canSkipPrevious||state.positionMs>3000)builder.add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                return builder.build()
            }
            override fun seekToNext() = playback.next()
            override fun seekToNextMediaItem() = playback.next()
            override fun seekToPrevious() = playback.previous()
            override fun seekToPreviousMediaItem() = playback.previous()
            override fun play() { if(!playback.state.value.playRequested)playback.toggle() }
            override fun pause() = playback.pause()
            override fun stop() = playback.stop()
        }
        val open=packageManager.getLaunchIntentForPackage(packageName)?:Intent()
        val activity=PendingIntent.getActivity(this,61,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        VideoSessionPool.serviceAvailable=true
        val initial=VideoSessionPool.active.value?:audioControls()
        session=MediaSession.Builder(this,initial).setSessionActivity(activity).build()
        lifecycleScope.launch {VideoSessionPool.active.collect{video->
            val replacement=video?:audioControls()
            if(session?.player!==replacement)session?.setPlayer(replacement)
        }}
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { if(session?.player?.playWhenReady!=true)stopSelf() }
    override fun onDestroy() { VideoSessionPool.serviceAvailable=false;session?.release();session=null;super.onDestroy() }
}

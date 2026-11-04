package com.infinity.drive.presentation.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.koin.android.ext.android.inject

/** Lock-screen, headset and notification controls share the application's actual player. */
@androidx.annotation.OptIn(UnstableApi::class)
class TeleDriveAudioService : MediaSessionService() {
    private val playback: AndroidAudioPlayback by inject()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val controls=object: ForwardingPlayer(playback.media3Player) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
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
        session=MediaSession.Builder(this,controls).setSessionActivity(activity).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { if(!playback.state.value.playRequested)stopSelf() }
    override fun onDestroy() { session?.release();session=null;super.onDestroy() }
}

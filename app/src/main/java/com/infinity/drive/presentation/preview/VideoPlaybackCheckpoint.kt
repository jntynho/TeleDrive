package com.infinity.drive.presentation.preview

import androidx.compose.runtime.saveable.listSaver
import androidx.media3.common.Player

/** User intent survives temporary lifecycle pauses and decoder disposal between pages. */
internal class VideoPlaybackCheckpoint(
    var position: Long = 0,
    var wantsPlay: Boolean = true,
    var speed: Float = 1f,
    var volume: Float = 1f,
    var repeat: Int = Player.REPEAT_MODE_OFF
) {
    private var suspended = false

    fun capture(player: Player) {
        position = if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition.coerceAtLeast(0)
        if (player.playbackState == Player.STATE_ENDED) wantsPlay=false
        else if (!suspended) wantsPlay = player.playWhenReady
        speed = player.playbackParameters.speed
        volume = player.volume
        repeat = player.repeatMode
    }

    fun suspend(player: Player) {
        capture(player)
        suspended = true
        player.pause()
    }

    fun resume(player: Player) {
        if (!suspended) return
        suspended = false
        player.playWhenReady = wantsPlay
    }

    fun restore(player: Player) {
        suspended = false
        player.seekTo(position)
        player.setPlaybackSpeed(speed)
        player.volume = volume
        player.repeatMode = repeat
        player.playWhenReady = wantsPlay
    }

    companion object {
        val Saver = listSaver<VideoPlaybackCheckpoint, Any>(
            save = { listOf(it.position, it.wantsPlay, it.speed, it.volume, it.repeat) },
            restore = { VideoPlaybackCheckpoint(it[0] as Long, it[1] as Boolean, it[2] as Float, it[3] as Float, it[4] as Int) }
        )
    }
}

/** Unknown duration must never turn a forward seek into a jump back to zero. */
internal fun videoSeekTarget(position: Long, delta: Long, duration: Long): Long {
    val current = position.coerceAtLeast(0)
    val target = if (delta > 0 && current > Long.MAX_VALUE - delta) Long.MAX_VALUE else (current + delta).coerceAtLeast(0)
    return if (duration > 0) target.coerceAtMost(duration) else target
}

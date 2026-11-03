package com.infinity.drive.presentation.audio

import android.content.Context
import androidx.lifecycle.ViewModel
import com.infinity.drive.core.media.TelegramDataSourceFactory
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.preview.PreviewContentResolver

/** Activity-retained ownership keeps the same player through configuration changes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AudioPlaybackOwner(
    context: Context,
    resolver: PreviewContentResolver,
    sources: TelegramDataSourceFactory,
    files: FileRepository
) : ViewModel() {
    val playback = AndroidAudioPlayback(context.applicationContext, resolver, sources, files)

    override fun onCleared() {
        playback.release()
    }
}

package app.tgdrive.rebuild

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.infinity.drive.presentation.preview.PreviewContent
import com.infinity.drive.presentation.preview.VideoSessionPool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class HiddenVideoRevocationTest {
    @Test fun revocationClearsTransportMetadataAndCannotReuseTheRevokedDecoder(){
        fun create()=ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        val first=VideoSessionPool.acquire("hidden-video",PreviewContent.Loading,::create)
        first.setMediaItem(MediaItem.fromUri("file:///not-prepared-fixture.mp4"));first.playWhenReady=true
        VideoSessionPool.revoke("hidden-video")
        assertNull(VideoSessionPool.active.value);assertFalse(first.playWhenReady);assertEquals(0,first.mediaItemCount)
        val second=VideoSessionPool.acquire("hidden-video",PreviewContent.Loading,::create)
        assertNotSame(first,second)
        VideoSessionPool.release(second)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS)
        assertNull(VideoSessionPool.active.value)
    }
}

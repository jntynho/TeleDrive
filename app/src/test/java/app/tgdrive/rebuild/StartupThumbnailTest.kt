package app.tgdrive.rebuild

import android.app.Application
import coil3.request.Options
import com.infinity.drive.core.media.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class StartupThumbnailTest {
    @Test fun preparationCannotFallThroughToGenerationOrNetworkWhenTheCacheIsMissing()=runTest {
        var cached=0;var generated=0
        val store=object:ThumbnailStore {
            override suspend fun thumbnailBytes(fileId:String):ByteArray?{generated++;error("Must not generate or download during startup")}
            override suspend fun cachedThumbnailBytes(fileId:String):ByteArray?{cached++;return null}
            override suspend fun uploadThumbnailFile(fileId:String):java.io.File?=error("Must not upload")
        }
        val fetch=ThumbnailFetcher(ThumbnailModel("cloud-only","revision",cacheOnly=true),Options(RuntimeEnvironment.getApplication()),store)
        assertNull(fetch.fetch());assertEquals(1,cached);assertEquals(0,generated)
    }
}

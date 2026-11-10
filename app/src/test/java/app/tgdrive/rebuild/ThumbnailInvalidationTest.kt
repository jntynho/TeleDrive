package app.tgdrive.rebuild

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import com.infinity.drive.core.media.CoilThumbnailMemoryCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class ThumbnailInvalidationTest {
    @Test fun evictionRemovesEveryVersionAndSizeOfOneFileWithoutClearingOthers(){
        val cache=MemoryCache.Builder().maxSizeBytes(1024*1024).weakReferencesEnabled(false).build()
        val loader=ImageLoader.Builder(RuntimeEnvironment.getApplication()).memoryCache(cache).diskCache(null).build()
        try {
            val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).asImage()
            val keys=listOf(MemoryCache.Key("thumb:one"),MemoryCache.Key("thumb:one:old"),MemoryCache.Key("thumb:one:new",mapOf("size" to "64")),MemoryCache.Key("thumb:other:new"))
            keys.forEach{cache[it]=MemoryCache.Value(image)}
            CoilThumbnailMemoryCache(loader).remove("one")
            assertEquals(setOf(keys.last()),cache.keys)
        }finally{loader.shutdown()}
    }
}

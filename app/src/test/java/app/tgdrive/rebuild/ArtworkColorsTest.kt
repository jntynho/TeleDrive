package app.tgdrive.rebuild

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.infinity.drive.presentation.audio.artworkColors
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkColorsTest {
    @Test fun brightTextAndBlackBordersDoNotReplaceTheArtworkPalette() {
        val bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30,110,130))
        for(y in 0..20)for(x in 0 until 200)bitmap.setPixel(x,y,Color.WHITE)
        for(y in 180 until 200)for(x in 0 until 200)bitmap.setPixel(x,y,Color.BLACK)
        val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        bitmap.recycle()
        val colors=artworkColors(bytes)
        assertEquals(2,colors.size)
        assertTrue(colors.all{Color.blue(it.toInt())>Color.red(it.toInt())})
        assertTrue(colors.none{it.toInt()==Color.WHITE||it.toInt()==Color.BLACK})
    }
    @Test fun malformedOrEmptyArtworkUsesThePlayersFallback() {
        assertTrue(artworkColors(byteArrayOf()).isEmpty())
        assertTrue(artworkColors(byteArrayOf(1,2,3)).isEmpty())
    }
}

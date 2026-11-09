package app.tgdrive

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.infinity.drive.presentation.platform.photoToScanPdf
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Uses Android's real PDF writer/renderer; Robolectric SDK 28 does not implement PdfDocument. */
@RunWith(AndroidJUnit4::class)
class ScanPdfDeviceTest {
    @Test fun fallbackCreatesAValidPdfAndRespectsCameraRotation(){
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"scan-pdf-test").apply{mkdirs()}
        try{
            val photo=File(root,"page.jpg")
            val source=Bitmap.createBitmap(96,160,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
            photo.outputStream().use{source.compress(Bitmap.CompressFormat.JPEG,90,it)};source.recycle()
            ExifInterface(photo.absolutePath).apply{setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
            val original=photo.readBytes()
            val pdf=photoToScanPdf(photo,File(root,"scan.pdf"))
            assertArrayEquals(original,photo.readBytes())
            val header=ByteArray(5);java.io.DataInputStream(pdf.inputStream()).use{it.readFully(header)}
            assertEquals("%PDF-",String(header,Charsets.US_ASCII))
            val descriptor=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer=PdfRenderer(descriptor)
            try{
                assertEquals(1,renderer.pageCount)
                val page=renderer.openPage(0)
                try{
                    val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                    try{
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        var minX=bitmap.width;var maxX=0;var minY=bitmap.height;var maxY=0
                        for(y in 0 until bitmap.height step 4)for(x in 0 until bitmap.width step 4){
                            val pixel=bitmap.getPixel(x,y)
                            if(Color.red(pixel)>180&&Color.green(pixel)<80){minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)}
                        }
                        assertTrue("EXIF rotation must turn the portrait capture into landscape content",maxX-minX>maxY-minY)
                    }finally{bitmap.recycle()}
                }finally{page.close()}
            }finally{renderer.close();descriptor.close()}
        }finally{root.deleteRecursively()}
    }
}

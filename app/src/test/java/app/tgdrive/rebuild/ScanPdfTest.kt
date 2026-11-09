package app.tgdrive.rebuild
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.infinity.drive.presentation.platform.photoToScanPdf
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanPdfTest {
    @Test fun invalidCaptureDoesNotLeaveAPdfBehind(){
        val dir=java.nio.file.Files.createTempDirectory("scan-invalid").toFile()
        try{
            val photo=File(dir,"invalid.jpg").apply{writeText("not an image")};val out=File(dir,"scan.pdf")
            assertThrows(IllegalArgumentException::class.java){photoToScanPdf(photo,out)}
            assertFalse(out.exists())
        }finally{dir.deleteRecursively()}
    }
}

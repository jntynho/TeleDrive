package app.tgdrive.rebuild
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.infinity.drive.presentation.preview.clampImagePan
import org.junit.Assert.assertEquals
import org.junit.Test
class ZoomPanBoundsTest {
    @Test fun wideImageCannotPanVerticallyWhileItIsStillLetterboxed(){
        assertEquals(Offset(200f,0f),clampImagePan(Offset(900f,900f),2f,IntSize(400,800),IntSize(1600,800)))
    }
    @Test fun tallImageCannotPanHorizontallyBeforeItFillsTheViewport(){
        assertEquals(Offset(0f,-400f),clampImagePan(Offset(-900f,-900f),2f,IntSize(400,800),IntSize(400,1600)))
    }
    @Test fun fitAndEmptyViewportAlwaysReturnToTheCenter(){
        assertEquals(Offset.Zero,clampImagePan(Offset(10f,20f),1f,IntSize(400,800),IntSize(1600,800)))
        assertEquals(Offset.Zero,clampImagePan(Offset(10f,20f),2f,IntSize.Zero,IntSize(1600,800)))
    }
}

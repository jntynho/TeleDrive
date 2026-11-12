package app.tgdrive.rebuild

import androidx.compose.ui.geometry.Offset
import com.infinity.drive.presentation.components.*
import org.junit.Assert.*
import org.junit.Test

class GalleryDensityPolicyTest {
    @Test fun headersKeepTheirHeightAndEveryPhotoRetainsFourByFiveAtAllDensities() {
        val items=listOf(DensityItem("today",true))+List(19){DensityItem("photo-$it")}+DensityItem("yesterday",true)+DensityItem("last")
        for(columns in 2..6){
            val boxes=densityGeometry(items,columns,393f,2f,.8f,42f)
            assertEquals(42f,boxes.getValue("today").height,.01f)
            assertEquals(42f,boxes.getValue("yesterday").height,.01f)
            boxes.filterKeys{it!="today"&&it!="yesterday"}.values.forEach{rect->
                assertEquals(.8f,rect.width/rect.height,.001f)
                assertTrue(rect.left>=0);assertTrue(rect.right<=393.01f)
            }
            assertTrue(boxes.getValue("yesterday").top>=boxes.getValue("photo-18").bottom)
        }
    }
    @Test fun focalPointSurvivesAChangeOfCellSizeIncludingContentPadding() {
        val anchor=DensityAnchor("photo",.7f,310f)
        for(height in listOf(80f,160f,240f,400f)){
            val scroll=anchoredScrollOffset(anchor,height,12f)
            val screenPoint=12f-scroll+anchor.fraction*height
            assertEquals(anchor.viewportY,screenPoint,.51f)
        }
    }
    @Test fun hysteresisRetainsAnIntentionalStepButAllowsDeliberateReversal() {
        val session=GridPinchSession(3)
        session.add(1.25f);assertEquals(1,session.steps)
        session.add(.97f);assertEquals(1,session.steps)
        session.add(.95f);assertEquals(1,session.steps)
        session.add(.85f);assertEquals(0,session.steps)
        session.add(.7f);assertEquals(-1,session.steps)
    }
    @Test fun aDensityLimitDoesNotCreateAFakeCommitOrWait() {
        val largest=GridPinchSession(2);largest.add(3f);assertEquals(0,largest.steps)
        val smallest=GridPinchSession(6);smallest.add(.3f);assertEquals(0,smallest.steps)
    }
    @Test fun jitterAndInvalidSamplesNeverMoveTheDensity() {
        val session=GridPinchSession(4)
        repeat(20){session.add(1.001f)}
        listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,-1f).forEach(session::add)
        assertEquals(0,session.steps)
    }
    @Test fun albumLabelsDoNotScaleWithTheCover() {
        val items=List(9){DensityItem("album-$it")}
        for(n in 2..6){
            val r=densityGeometry(items,n,369f,10f,1f,0f,64f).getValue("album-0")
            assertEquals(64f,r.height-r.width,.01f)
        }
    }
}

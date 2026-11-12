package app.tgdrive.rebuild

import com.infinity.drive.presentation.components.*
import org.junit.Assert.*
import org.junit.Test

class GalleryDensityPolicyTest {
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

}

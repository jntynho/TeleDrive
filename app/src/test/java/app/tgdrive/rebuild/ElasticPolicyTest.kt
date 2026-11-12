package app.tgdrive.rebuild

import app.tgdrive.ui.edgeApproach
import app.tgdrive.ui.elasticDisplacement
import org.junit.Assert.*
import org.junit.Test

class ElasticPolicyTest {
    @Test fun anticipationOnlyFollowsMovementTowardsTheBoundary(){
        assertTrue(app.tgdrive.ui.elasticEdgeProximity(12f,Float.POSITIVE_INFINITY,48f,1f)>0f)
        assertEquals(0f,app.tgdrive.ui.elasticEdgeProximity(12f,Float.POSITIVE_INFINITY,48f,-1f),0f)
        assertTrue(app.tgdrive.ui.elasticEdgeProximity(Float.POSITIVE_INFINITY,12f,48f,-1f)<0f)
        assertEquals(0f,app.tgdrive.ui.elasticEdgeProximity(0f,0f,48f,0f),0f)
    }
    @Test fun reversalUnwindsVisibleDistanceWithoutAHiddenDebt(){
        val pulled=app.tgdrive.ui.advanceElasticPull(0f,200f,96f)
        assertTrue(pulled in 0f..96f)
        assertEquals(0f,app.tgdrive.ui.advanceElasticPull(pulled,-pulled,96f),.001f)
        assertEquals(pulled,app.tgdrive.ui.advanceElasticPull(pulled,.1f,96f),0f)
        assertEquals(0f,app.tgdrive.ui.advanceElasticPull(Float.NaN,1f,96f),0f)
    }

    @Test fun proximityIsLocalContinuousAndIndependentOfContentLength(){
        assertEquals(0f,edgeApproach(Float.POSITIVE_INFINITY,48f),0f)
        assertEquals(0f,edgeApproach(48f,48f),0f)
        assertEquals(.5f,edgeApproach(24f,48f),.0001f)
        assertEquals(1f,edgeApproach(0f,48f),0f)
        var previous=0f
        for(distance in 48 downTo 0){val current=edgeApproach(distance.toFloat(),48f);assertTrue(current>=previous);previous=current}
    }
    @Test fun displacementIsSymmetricBoundedAndResistsIncreasingPull(){
        var previous=0f
        for(raw in 0..10000 step 10){
            val value=elasticDisplacement(raw.toFloat(),800f,72f)
            assertTrue(value>=previous);assertTrue(value<72f)
            assertEquals(-value,elasticDisplacement(-raw.toFloat(),800f,72f),.001f)
            previous=value
        }
        assertTrue(elasticDisplacement(10000f,100f,72f)<22f)
        assertEquals(0f,elasticDisplacement(0f,800f,72f),0f)
    }
}

package app.tgdrive.rebuild

import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.presentation.components.FilterScrollThreshold
import com.infinity.drive.presentation.preview.PreviewSequence
import org.junit.Assert.*
import org.junit.Test

class UiInteractionPolicyTest {
    @Test fun filterHeaderIgnoresTinyMovesAndRevealsOnReverseScroll(){
        val tracker=FilterScrollThreshold(24f)
        repeat(3){assertFalse(tracker.consume(-5f,false))}
        assertTrue(tracker.consume(-9f,false))
        repeat(3){assertTrue(tracker.consume(5f,false))}
        assertFalse(tracker.consume(9f,false))
        assertTrue(tracker.consume(-24f,false))
        assertTrue(tracker.consume(0f,false))
        assertFalse(tracker.consume(1f,true))
    }
    @Test fun changingDirectionResetsAccumulatedFilterTravel(){
        val tracker=FilterScrollThreshold(24f)
        assertFalse(tracker.consume(-23f,false))
        assertFalse(tracker.consume(1f,false))
        assertFalse(tracker.consume(-1f,false))
        assertTrue(tracker.consume(-23f,false))
    }
    @Test fun renameRejectsUnsafeNamesInsteadOfSilentlyChangingThem(){
        listOf("","  ",".","..","folder/file","a\\b","bad\u0000name","x".repeat(181)).forEach{assertFalse(it,FileNameUtils.isValidName(it))}
        listOf("Photo.jpg","  My notes.md  ","صور.jpg").forEach{assertTrue(it,FileNameUtils.isValidName(it))}
    }
    @Test fun previewRetainsEverySearchScopeConstraint(){
        val route=PreviewSequence(backedUpOnly=true,notBackedUpOnly=false,minSizeBytes=10_485_760L,nameQuery="holiday").routeFor("id")
        assertTrue(route.backedUpOnly);assertFalse(route.notBackedUpOnly)
        assertEquals(10_485_760L,route.minSizeBytes);assertEquals("holiday",route.nameQuery)
    }
}

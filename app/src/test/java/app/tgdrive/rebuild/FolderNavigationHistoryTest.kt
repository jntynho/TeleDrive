package app.tgdrive.rebuild

import com.infinity.drive.presentation.components.FolderNavigationHistory
import org.junit.Test
import org.junit.Assert.*

class FolderNavigationHistoryTest {
    @Test fun folderPushAndAncestorReturnHaveOppositeDirections(){
        val history=FolderNavigationHistory()
        assertFalse(history.visit("home",false).enabled)
        assertFalse(history.visit("root",true).enabled)
        assertTrue(history.visit("folder",true).let{it.enabled&&it.forward})
        assertTrue(history.visit("nested",true).let{it.enabled&&it.forward})
        assertTrue(history.visit("folder",true).let{it.enabled&&!it.forward})
        assertTrue(history.visit("root",true).let{it.enabled&&!it.forward})
    }
    @Test fun rotationPreservesBackDirectionAndOtherScreensDoNotAddASecondAnimation(){
        val old=FolderNavigationHistory().apply{visit("root",true);visit("child",true)}
        val restored=FolderNavigationHistory(old.snapshot())
        assertFalse(restored.visit("child",true).enabled)
        assertTrue(restored.visit("root",true).let{it.enabled&&!it.forward})
        assertFalse(restored.visit("preview",false).enabled)
        assertFalse(restored.visit("root",true).enabled)
    }
}

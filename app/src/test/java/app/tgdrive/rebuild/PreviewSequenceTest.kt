package app.tgdrive.rebuild

import com.infinity.drive.presentation.preview.PreviewSequence
import org.junit.Test
import org.junit.Assert.*

class PreviewSequenceTest {
    @Test fun localAudioScopeSurvivesNavigation() {
        val route=PreviewSequence(localCopiesOnly=true,favoritesOnly=true,nameQuery="song").routeFor("local-track")
        assertTrue(route.localCopiesOnly)
        assertTrue(route.favoritesOnly)
        assertEquals("song",route.nameQuery)
        assertFalse(PreviewSequence().routeFor("any-track").localCopiesOnly)
    }
}

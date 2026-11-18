package app.tgdrive.rebuild

import com.infinity.drive.domain.model.PublicLinkConnection
import com.infinity.drive.presentation.sharing.publicLinkOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PublicLinkOriginTest {
    @Test fun `only final HTTPS origins are used for sending tokens`() {
        assertEquals("https://sharing.example:8443/", publicLinkOrigin(" https://sharing.example:8443 ").toString())
        for (invalid in listOf(
            "http://sharing.example",
            "https://user:pass@sharing.example",
            "https://sharing.example/mount",
            "https://sharing.example/?callback=elsewhere",
            "https://sharing.example/#fragment"
        )) {
            assertThrows(IllegalArgumentException::class.java) { publicLinkOrigin(invalid) }
        }
    }

    @Test fun `connection diagnostics omit the owner token`() {
        assertFalse(PublicLinkConnection("https://sharing.example", "private-owner-token").toString().contains("private-owner-token"))
    }
}

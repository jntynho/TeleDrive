package com.infinity.drive.data.repository

import com.infinity.drive.core.telegram.TelegramMessageLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramMessageLinkTest {
    @Test
    fun acceptsPrivatePostsTopicsAndPublicMessageLinks() {
        assertEquals("https://t.me/c/456/78/90", TelegramMessageLink.normalize("https://t.me/c/456/78/90"))
        assertEquals("https://t.me/channel_1/15?comment=22", TelegramMessageLink.normalize(
            "https://telegram.me/s/channel_1/15?comment=22"
        ))
    }

    @Test
    fun rejectsInvitesNonTelegramHostsAndInvalidTargetIds() {
        listOf(
            "https://t.me.evil.test/channel/1", "https://t.me@elsewhere.test/channel/1",
            "https://elsewhere.test@t.me/channel/1", "https://t.me/+abc",
            "file://t.me/channel/1", "https://t.me/channel/0", "https://t.me/c/x/2",
            "https://t.me/channel%2Fhidden/3"
        ).forEach { assertNull(it, TelegramMessageLink.normalize(it)) }
    }
}

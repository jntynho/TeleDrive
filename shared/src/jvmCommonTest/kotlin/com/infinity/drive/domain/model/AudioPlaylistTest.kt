package com.infinity.drive.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlaylistTest {
    @Test fun moveKeepsTracksUniqueAndHandlesBoundaries() {
        val tracks = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), tracks.movePlaylistTrack("b", -1))
        assertEquals(listOf("a", "c", "b"), tracks.movePlaylistTrack("b", 1))
        assertEquals(tracks, tracks.movePlaylistTrack("a", -1))
        assertEquals(tracks, tracks.movePlaylistTrack("missing", 1))
    }
}

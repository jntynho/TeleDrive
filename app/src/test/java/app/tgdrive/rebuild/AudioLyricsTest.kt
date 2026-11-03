package app.tgdrive.rebuild

import com.infinity.drive.presentation.audio.AudioLyricsParser
import org.junit.Assert.*
import org.junit.Test

class AudioLyricsTest {
    @Test fun plainTextHasNoInventedTiming() {
        val lyrics = AudioLyricsParser.parse("First line\nSecond line")
        assertTrue(lyrics.cues.isEmpty())
        assertEquals(-1, lyrics.activeIndex(5000))
        assertEquals("First line\nSecond line", lyrics.plainText)
    }
    @Test fun seekingInBothDirectionsUsesActualCueBoundaries() {
        val lyrics = AudioLyricsParser.parse("[00:01.2]First\n[00:03.45]Second\n[00:05.678]Third")
        assertEquals(listOf(1200L,3450L,5678L),lyrics.cues.map{it.timeMs})
        assertEquals(-1,lyrics.activeIndex(1199))
        assertEquals(0,lyrics.activeIndex(1200))
        assertEquals(2,lyrics.activeIndex(9000))
        assertEquals(0,lyrics.activeIndex(2000))
    }
    @Test fun metadataAndLateOffsetAreHandledBeforeSorting() {
        val lyrics=AudioLyricsParser.parse("[ti:Track]\n[00:03]Later\n[00:01]Earlier\n[offset:-500]")
        assertEquals(listOf(500L,2500L),lyrics.cues.map{it.timeMs})
        assertEquals("Earlier",lyrics.cues.first().text)
        assertFalse(lyrics.plainText.contains("Track"))
    }
    @Test fun repeatedLinesAndTranslationsShareTheirRealTimes() {
        val lyrics=AudioLyricsParser.parse("[00:01][00:04]Hello\n[00:01]Bonjour")
        assertEquals(2,lyrics.cues.size)
        assertEquals("Hello\nBonjour",lyrics.cues.first().text)
        assertEquals("Hello",lyrics.cues.last().text)
    }
    @Test fun enhancedLrcPreservesPrefixesAndOffsetsRepeatedWordTimes() {
        val lyrics=AudioLyricsParser.parse("[00:01][00:05]Hello <00:01.5>wide <00:02>world\n[offset:100]")
        assertEquals("Hello wide world",lyrics.cues.first().text)
        assertEquals(listOf(1100L,1600L,2100L),lyrics.cues.first().words.map{it.timeMs})
        assertEquals(listOf(5100L,5600L,6100L),lyrics.cues.last().words.map{it.timeMs})
    }
    @Test fun emptyCueClearsThePreviousLineAndHoursAreSupported() {
        val lyrics=AudioLyricsParser.parse("[00:01]Singing\n[00:02]\n[01:02:03.004]Long recording")
        assertEquals("",lyrics.cues[lyrics.activeIndex(2500)].text)
        assertEquals(3_723_004L,lyrics.cues.last().timeMs)
    }
    @Test fun missingMalformedAndOversizedLyricsStayBounded() {
        assertTrue(AudioLyricsParser.parse(null).cues.isEmpty())
        assertTrue(AudioLyricsParser.parse("[00:99]Invalid").cues.isEmpty())
        val lyrics=AudioLyricsParser.parse("[00:01]line\n".repeat(10000))
        assertTrue(lyrics.cues.size<=1024)
        assertTrue(lyrics.plainText.length<=32000)
    }
}

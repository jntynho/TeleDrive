package app.tgdrive.rebuild

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.Assert.*

class EmbeddedLyricsTest {
    private fun read(bytes:ByteArray):String? {
        val file=File.createTempFile("embedded-lyrics-", ".audio")
        return try{
            file.writeBytes(bytes)
            val method=Class.forName("com.infinity.drive.presentation.audio.AndroidAudioPlaybackKt").getDeclaredMethod("readEmbeddedLyrics",File::class.java).apply{isAccessible=true}
            method.invoke(null,file) as String?
        }finally{file.delete()}
    }
    private fun atom(type:String,data:ByteArray)=ByteBuffer.allocate(data.size+8).putInt(data.size+8).put(type.toByteArray(Charsets.ISO_8859_1)).put(data).array()
    @Test fun id3LyricsAreReadWithoutAudioPayload(){
        val body=byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(0)+"First line\nSecond line".toByteArray()
        val frame=ByteBuffer.allocate(body.size+10).put("USLT".toByteArray()).putInt(body.size).putShort(0).put(body).array()
        val count=frame.size
        val header="ID3".toByteArray()+byteArrayOf(3,0,0,((count shr 21)and 127).toByte(),((count shr 14)and 127).toByte(),((count shr 7)and 127).toByte(),(count and 127).toByte())
        assertEquals("First line\nSecond line",read(header+frame))
    }
    @Test fun flacAndM4aLyricsAreReadFromTheirMetadata(){
        val text="LYRICS=Local words".toByteArray()
        val comments=ByteBuffer.allocate(12+text.size).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(1).putInt(text.size).put(text).array()
        assertEquals("Local words",read("fLaC".toByteArray()+byteArrayOf(0x84.toByte(),0,0,comments.size.toByte())+comments))
        val lyric=atom("©lyr",atom("data",ByteArray(8)+"Local words".toByteArray()))
        assertEquals("Local words",read(atom("moov",atom("udta",atom("meta",ByteArray(4)+atom("ilst",lyric))))))
    }
    @Test fun missingOrMalformedMetadataDoesNotPreventPlayback(){
        assertNull(read(byteArrayOf(1,2,3)))
        assertNull(read("fLaC".toByteArray()+byteArrayOf(0x84.toByte(),127,127,127)))
    }
    private fun id3Frame(id:String,body:ByteArray)=ByteBuffer.allocate(body.size+10).put(id.toByteArray()).putInt(body.size).putShort(0).put(body).array()
    private fun id3(vararg frames:ByteArray):ByteArray {
        val data=frames.fold(byteArrayOf()){all,next->all+next};val n=data.size
        return "ID3".toByteArray()+byteArrayOf(3,0,0,((n shr 21)and 127).toByte(),((n shr 14)and 127).toByte(),((n shr 7)and 127).toByte(),(n and 127).toByte())+data
    }
    @Test fun millisecondSynchronizedLyricsTakePriorityOverPlainText(){
        val plain=id3Frame("USLT",byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(0)+"Untimed".toByteArray())
        val syllables="First line".toByteArray()+byteArrayOf(0)+ByteBuffer.allocate(4).putInt(1200).array()+
            "\nSecond line".toByteArray()+byteArrayOf(0)+ByteBuffer.allocate(4).putInt(3400).array()
        val synced=id3Frame("SYLT",byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(2,1,0)+syllables)
        val lyrics=com.infinity.drive.presentation.audio.AudioLyricsParser.parse(read(id3(plain,synced)))
        assertEquals(listOf(1200L,3400L),lyrics.cues.map{it.timeMs})
        assertEquals("Second line",lyrics.cues.last().text)
    }
    @Test fun unresolvedMpegFrameTimingFallsBackToPlainLyrics(){
        val plain=id3Frame("USLT",byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(0)+"Untimed".toByteArray())
        val synced=id3Frame("SYLT",byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(1,1,0)+"Word".toByteArray()+byteArrayOf(0)+ByteBuffer.allocate(4).putInt(400).array())
        assertEquals("Untimed",read(id3(synced,plain)))
    }
    @Test fun utf16DescriptionsAndTextAreReadOnCodeUnitBoundaries(){
        val body=byteArrayOf(2)+"eng".toByteArray()+"description".toByteArray(Charsets.UTF_16BE)+byteArrayOf(0,0)+"Words here".toByteArray(Charsets.UTF_16BE)
        assertEquals("Words here",read(id3(id3Frame("USLT",body))))
    }

    @Test fun wordSynchronizedFramesKeepWordTimesAndExplicitLineBreaks(){
        val words="Hello ".toByteArray()+byteArrayOf(0)+ByteBuffer.allocate(4).putInt(1000).array()+
            "world".toByteArray()+byteArrayOf(0)+ByteBuffer.allocate(4).putInt(1500).array()+
            "\nAgain".toByteArray()+byteArrayOf(0)+ByteBuffer.allocate(4).putInt(3000).array()
        val raw=read(id3(id3Frame("SYLT",byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(2,1,0)+words)))
        val lyrics=com.infinity.drive.presentation.audio.AudioLyricsParser.parse(raw)
        assertEquals(listOf("Hello world","Again"),lyrics.cues.map{it.text})
        assertEquals(listOf(1000L,1500L),lyrics.cues.first().words.map{it.timeMs})
    }
    @Test fun streamedMetadataUsesLyricsAlreadyExposedByTheExtractor(){
        val metadata=androidx.media3.common.Metadata(
            androidx.media3.extractor.metadata.id3.BinaryFrame("USLT",byteArrayOf(3)+"eng".toByteArray()+byteArrayOf(0)+"Plain".toByteArray()),
            androidx.media3.extractor.metadata.vorbis.VorbisComment("LYRICS","[00:01]Timed line"))
        val lyrics=com.infinity.drive.presentation.audio.streamedLyrics(metadata)
        assertEquals("Timed line",com.infinity.drive.presentation.audio.AudioLyricsParser.parse(lyrics).cues.single().text)
    }

}

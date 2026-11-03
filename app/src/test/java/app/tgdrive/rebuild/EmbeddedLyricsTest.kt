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
}

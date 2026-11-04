package com.infinity.drive.core.media

import android.media.MediaDataSource
import com.infinity.drive.core.telegram.TelegramClient
import java.io.IOException
import kotlinx.coroutines.*

/** Optional tag inspection: bounded ranges, no whole-file download and no cancellation of playback. */
internal class TelegramMetadataSource(
    private val client:TelegramClient,
    private val remoteId:String,
    private val playbackActive:()->Boolean,
    private val byteBudget:Int=2*1024*1024,
    private val timeoutMs:Long=8_000
):MediaDataSource(){
    private val deadline=System.nanoTime()+timeoutMs*1_000_000
    private var fileId=0
    private var length=-1L
    private var consumed=0
    private var requests=0
    private var closed=false
    private fun <T> bounded(block:suspend ()->T):T {
        if(closed||playbackActive())throw IOException("Metadata inspection stopped")
        val remaining=(deadline-System.nanoTime())/1_000_000
        if(remaining<=0)throw IOException("Metadata inspection timed out")
        return runBlocking{withTimeout(remaining){block()}}
    }
    override fun getSize():Long {
        if(length<0)bounded{client.resolveFile(remoteId)}.let{fileId=it.fileId;length=it.sizeBytes}
        return length
    }
    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
        require(position>=0&&offset>=0&&size>=0&&offset<=buffer.size-size)
        if(size==0)return 0
        val total=getSize()
        if(position>=total)return -1
        val count=minOf(size,256*1024,(total-position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if(++requests>32||count>byteBudget-consumed)throw IOException("Metadata inspection budget reached")
        consumed+=count
        val bytes=bounded {
            val cached=try{client.readFilePart(fileId,position,count.toLong())}
                catch(cancel:CancellationException){throw cancel}
                catch(_:Exception){ByteArray(0)}
            if(cached.isNotEmpty())cached else {
                client.requestFileRange(fileId,position,count.toLong())
                while(true){
                    currentCoroutineContext().ensureActive()
                    if(playbackActive())throw IOException("Playback owns this stream")
                    val info=client.getFileInfo(fileId)
                    if(info.isDownloadingCompleted||(position>=info.downloadOffset&&position+count<=info.downloadOffset+info.downloadedPrefixSize))break
                    delay(80)
                }
                client.readFilePart(fileId,position,count.toLong())
            }
        }
        if(bytes.isEmpty())throw IOException("Metadata range unavailable")
        val actual=minOf(bytes.size,count)
        bytes.copyInto(buffer,offset,0,actual)
        return actual
    }
    override fun close(){closed=true} // Never cancel a shared TDLib download here.
}

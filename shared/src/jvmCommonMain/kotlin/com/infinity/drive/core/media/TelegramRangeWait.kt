package com.infinity.drive.core.media

import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramFileInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal const val STREAM_RANGE_BYTES=2L*1024*1024
internal fun rangeCovered(info:TelegramFileInfo,position:Long,count:Long)=info.isDownloadingCompleted ||
    (position>=info.downloadOffset && position+count<=info.downloadOffset+info.downloadedPrefixSize)

/** Live updates give prompt reads; local queries recover updates emitted before subscription. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun awaitTelegramRange(client:TelegramClient,fileId:Int,position:Long,count:Long){
    val initial=client.getFileInfo(fileId)
    if(rangeCovered(initial,position,count))return
    val limit=maxOf(count,STREAM_RANGE_BYTES)
    client.requestFileRange(fileId,position,limit)
    withTimeout(30_000){
        var progress=initial.downloadOffset to initial.downloadedPrefixSize
        var changedAt=System.nanoTime()
        val fallback=flow {
            while(true){
                val info=client.getFileInfo(fileId)
                val next=info.downloadOffset to info.downloadedPrefixSize
                if(next!=progress){progress=next;changedAt=System.nanoTime()}
                if(!rangeCovered(info,position,count)&&(System.nanoTime()-changedAt)>2_000_000_000L){
                    // A concurrent transfer may have moved the range or been canceled.
                    client.requestFileRange(fileId,position,limit);changedAt=System.nanoTime()
                }
                emit(info);delay(500)
            }
        }
        val events=flow{emitAll(client.fileUpdates(fileId))}.catch { if(it is CancellationException)throw it }
        merge(fallback,events).first{rangeCovered(it,position,count)}
    }
}

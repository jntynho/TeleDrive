package com.infinity.drive.presentation.preview

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One application-context video decoder, shared across a configuration change only. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object VideoSessionPool {
    private data class Entry(val id:String,val content:PreviewContent,val player:ExoPlayer,var owners:Int=0,var background:Boolean=false,var revoked:Boolean=false)
    private var entry:Entry?=null
    private val handler=Handler(Looper.getMainLooper())
    private val mutable=MutableStateFlow<ExoPlayer?>(null)
    val active=mutable.asStateFlow()
    var serviceAvailable=false
    val currentFileId:String? get()=entry?.id
    /** Revocation removes transport metadata immediately, and prevents reuse of an emptied player. */
    fun revoke(id:String){entry?.takeIf{it.id==id}?.let{
        it.revoked=true;it.background=false;it.player.pause();it.player.clearMediaItems();mutable.value=null;scheduleRelease(it)
    }}
    fun acquire(id:String,content:PreviewContent,create:()->ExoPlayer):ExoPlayer {
        val previous=entry
        if(previous!=null&&previous.id==id&&previous.content==content&&previous.player.playerError==null&&!previous.revoked){previous.owners++;previous.background=false;return previous.player}
        previous?.let{mutable.value=null;entry=null;it.player.release()}
        val player=create()
        val record=Entry(id,content,player,1)
        entry=record;mutable.value=player
        player.addListener(object:Player.Listener {
            override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_ENDED){record.background=false;scheduleRelease(record)}}
            override fun onPlayWhenReadyChanged(ready:Boolean,reason:Int){if(!ready){record.background=false;scheduleRelease(record)}}
        })
        return player
    }
    fun background(player:ExoPlayer,allowed:Boolean){entry?.takeIf{it.player===player}?.background=allowed&&serviceAvailable}
    fun release(player:ExoPlayer){entry?.takeIf{it.player===player}?.let{it.owners=(it.owners-1).coerceAtLeast(0);scheduleRelease(it)}}
    private fun scheduleRelease(record:Entry){handler.postDelayed({
        if(entry===record&&record.owners==0&&!(record.background&&record.player.playWhenReady)){
            mutable.value=null;entry=null;record.player.release()
        }
    },750)}
}

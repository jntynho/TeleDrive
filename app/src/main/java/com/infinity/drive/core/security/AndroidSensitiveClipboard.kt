package com.infinity.drive.core.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.infinity.drive.presentation.platform.SensitiveClipboard
import kotlinx.coroutines.*
import java.io.Closeable

/** Sensitive preview flag; clear only our own clipboard entry, never subsequent user content. */
class AndroidSensitiveClipboard(context:Context):SensitiveClipboard,Closeable {
    private val manager=context.getSystemService(ClipboardManager::class.java)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val label="TeleDrive recovery ${java.util.UUID.randomUUID()}"
    private var job:Job?=null
    override fun copy(text:String){
        val clip=ClipData.newPlainText(label,text)
        clip.description.extras=PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)}
        manager.setPrimaryClip(clip)
        job?.cancel();job=scope.launch{delay(60_000);clearOwned()}
    }
    private fun clearOwned(){runCatching{
        if(manager.primaryClipDescription?.label?.toString()==label){
            if(Build.VERSION.SDK_INT>=28)manager.clearPrimaryClip()else manager.setPrimaryClip(ClipData.newPlainText("",""))
        }
    }}
    override fun close(){scope.cancel();clearOwned()}
}

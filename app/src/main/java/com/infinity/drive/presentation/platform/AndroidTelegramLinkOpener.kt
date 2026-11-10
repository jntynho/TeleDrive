package com.infinity.drive.presentation.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Visibility of tg: handlers on Android 11+ depends on the queries block in
 * the manifest; without it the probe reports no Telegram app even when one
 * is installed.
 */
class AndroidTelegramLinkOpener(
    private val context: Context
) : TelegramLinkOpener {

    override val canOpenTelegram: Boolean
        get() = probe().resolveActivity(context.packageManager) != null

    override fun open(link: String): Boolean {
        val uri=link.toUri()
        val native=if(uri.scheme=="https"&&uri.host in listOf("t.me","telegram.me")) {
            val segments=uri.pathSegments
            when {
                segments.size>=3&&segments[0]=="c" -> "tg://privatepost?channel=${segments[1]}&post=${segments[2]}"
                segments.size>=2 -> "tg://resolve?domain=${segments[0]}&post=${segments[1]}"
                else -> null
            }
        } else null
        fun launch(value:String)=runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW,value.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true
        }.getOrDefault(false)
        if(native!=null&&launch(native))return true
        return launch(link)
    }

    private fun probe() = Intent(Intent.ACTION_VIEW, "tg://login".toUri())
}

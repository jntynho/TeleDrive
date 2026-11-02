package app.tgdrive

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Retired component retained only as a harmless target for an old explicit intent. */
class TransferService:Service() {
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {stopSelf();return START_NOT_STICKY}
    override fun onBind(intent:Intent?):IBinder?=null
}

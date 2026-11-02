package app.tgdrive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Tombstone for persisted jobs created by 0.x: never restart the retired Saved Messages uploader. */
class LibraryWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork()=Result.success()
}

@Deprecated("The Saved Messages scheduler is retired; new transfers use the private-channel engine.")
object BackgroundWork {
    fun enqueue(context:Context,profile:String,delayMillis:Long=1000)=Unit
    fun schedule(context:Context,profile:String,hours:Int)=Unit
}

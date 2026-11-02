package app.tgdrive.migration

import android.content.Context
import androidx.work.WorkManager
import app.tgdrive.AccountProfile
import app.tgdrive.library.model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cancels only the 0.x work names. The new engine's workers keep their own identities. */
suspend fun retirePreviousQueue(context:Context)=withContext(Dispatchers.IO) {
    val marker=context.getSharedPreferences("tgdrive-engine-upgrade",Context.MODE_PRIVATE)
    if(marker.getBoolean("previous-queue-retired",false))return@withContext
    val json=context.getSharedPreferences("account-profiles",Context.MODE_PRIVATE).getString("profiles",null)
    val profiles=runCatching{json?.model<List<AccountProfile>>()}.getOrNull().orEmpty().map{it.id}+"legacy"
    val work=WorkManager.getInstance(context)
    for(id in profiles.distinct()){
        if(id!="legacy"&&!id.matches(Regex("[a-zA-Z0-9-]{1,80}")))continue
        work.cancelUniqueWork("library-$id").result.get()
        work.cancelUniqueWork("backup-$id").result.get()
    }
    check(marker.edit().putBoolean("previous-queue-retired",true).commit())
}

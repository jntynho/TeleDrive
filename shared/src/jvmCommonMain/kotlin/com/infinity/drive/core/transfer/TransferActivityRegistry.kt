package com.infinity.drive.core.transfer

import com.infinity.drive.data.local.dao.TransferDao
import com.infinity.drive.data.local.entity.TransferEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Opening the UI must not recover a transfer that a live background worker still owns. */
object TransferActivityRegistry {
    private val lock=Mutex()
    private val active=mutableSetOf<String>()
    suspend fun claim(dao:TransferDao,lane:Int,now:Long):TransferEntity?=lock.withLock {
        dao.queuedLane(lane).firstOrNull{it.nextAttemptAt<=now}
            ?.takeIf{dao.claim(it.id,now)==1}?.also{active.add(it.id)}
    }
    suspend fun release(id:String)=lock.withLock{active.remove(id)}
    suspend fun recover(dao:TransferDao,now:Long)=lock.withLock{dao.requeueRunningExcept(now,active.toList())}
}

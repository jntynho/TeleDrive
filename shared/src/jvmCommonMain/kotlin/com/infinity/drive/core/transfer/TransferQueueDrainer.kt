package com.infinity.drive.core.transfer

import com.infinity.drive.core.network.NetworkMonitor
import com.infinity.drive.core.network.NetworkStatus
import com.infinity.drive.core.telegram.TelegramPacer
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.dao.TransferDao
import com.infinity.drive.data.local.entity.TransferEntity
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.TransferState
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Separate bounded upload/download lanes; every available slot starts immediately.
 * Server waits are durable queued work and do not spend the network retry budget. */
class TransferQueueDrainer(
    private val transferDao: TransferDao,
    private val fileDao: FileDao,
    private val transferExecutor: TransferRunner,
    private val backupSessionTracker: BackupSessionTracker,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val pacer: TelegramPacer,
    private val nowMillis:()->Long = { System.currentTimeMillis() }
) {
    suspend fun drain(isStopped: () -> Boolean, onTerminalFailure: suspend (TransferEntity) -> Unit): TransferDrainResult {
        var interrupted = false
        try {
            coroutineScope {
                List(6) { slot -> launch {
                    val lane = if(slot<2)0 else if(slot==2)1 else 2
                    val upload = lane != 2
                    val laneSlot = if(lane==0)slot else if(lane==1)0 else slot-3
                    while (isActive) {
                        if (isStopped()) { interrupted = true; return@launch }
                        val prefs = settingsRepository.preferences.first()
                        val network = networkMonitor.currentStatus()
                        if (network == NetworkStatus.UNAVAILABLE || (network == NetworkStatus.METERED && !prefs.allowMeteredTransfers)) {
                            interrupted = true; return@launch
                        }
                        val queued = transferDao.queuedLane(lane)
                        if (queued.isEmpty()) {
                            // Keep each lane available while another lane is active, including newly imported chunks.
                            if(transferDao.observeActiveCount().first()==0)return@launch
                            delay(200);continue
                        }
                        val now = nowMillis()
                        if (laneSlot >= (if(lane==0)prefs.transferConcurrency.coerceIn(1,2) else if(lane==1)1 else if(prefs.transferConcurrency==1)2 else 3) || (upload && pacer.floodWaitSeconds > 0)) {
                            delay(500); continue
                        }
                        val next = TransferActivityRegistry.claim(transferDao,lane,now)
                        if (next == null) { delay(500); continue }
                        try {
                        if(lane==1)delay(400)
                        val current = transferDao.byId(next.id)?.takeIf{it.state==TransferState.RUNNING} ?: continue
                        when (val outcome = controlled(current)) {
                            TransferExecutor.Outcome.Completed -> refreshSession(current.backupSessionId)
                            TransferExecutor.Outcome.Paused -> markState(current.id, TransferState.PAUSED)
                            TransferExecutor.Outcome.Canceled -> { markState(current.id, TransferState.CANCELLED); refreshSession(current.backupSessionId) }
                            is TransferExecutor.Outcome.Failed -> {
                                val serverWait = outcome.retryAfterSeconds
                                val attempt = current.retryCount + if (serverWait != null) 0 else 1
                                if (serverWait != null || (outcome.retryable&&attempt <= prefs.transferRetryCount)) {
                                    if (serverWait != null && upload) pacer.recordFloodWait(serverWait)
                                    val seconds = serverWait?.coerceIn(1, 86_400)?.plus(1)
                                        ?: listOf(5,20,60).getOrElse((attempt-1).coerceAtLeast(0)) { 60 }
                                    current.fileId?.let{fileDao.setBackupStateIfLocalOnly(it,BackupState.QUEUED)}
                                    transferDao.defer(current.id, nowMillis()+seconds*1000L, attempt,
                                        if(serverWait != null) "Waiting for Telegram ($seconds seconds)" else outcome.message, nowMillis())
                                } else {
                                    if(transferDao.failRunning(current.id,outcome.message,nowMillis())==1){
                                        current.fileId?.let { fileDao.setBackupStateIfLocalOnly(it, BackupState.FAILED) }
                                        onTerminalFailure(current); refreshSession(current.backupSessionId)
                                    }
                                }
                            }
                        }
                        } finally {withContext(NonCancellable){TransferActivityRegistry.release(next.id)}}
                    }
                } }.joinAll()
            }
        } finally {
            // Also runs when WorkManager cancels the coroutine, before the next worker claims it.
            withContext(NonCancellable) { TransferActivityRegistry.recover(transferDao,nowMillis()) }
        }
        return if(interrupted) TransferDrainResult.INTERRUPTED else TransferDrainResult.COMPLETED
    }
    private suspend fun controlled(task:TransferEntity):TransferExecutor.Outcome=coroutineScope {
        val operation=async{transferExecutor.execute(task)}
        val control=async{transferDao.observeById(task.id).first{it==null||it.state!=TransferState.RUNNING}}
        try {
            select {
                operation.onAwait{it}
                control.onAwait {
                    operation.cancelAndJoin()
                    when(transferDao.byId(task.id)?.state){
                        TransferState.COMPLETED->TransferExecutor.Outcome.Completed
                        TransferState.CANCELLED,null->TransferExecutor.Outcome.Canceled
                        else->TransferExecutor.Outcome.Paused
                    }
                }
            }
        } finally {control.cancelAndJoin();operation.cancelAndJoin()}
    }
    private suspend fun refreshSession(id: String?) = withContext(NonCancellable) { backupSessionTracker.refresh(id) }
    private suspend fun markState(id: String, state: TransferState) = withContext(NonCancellable) { transferDao.setRunningState(id,state,nowMillis()) }
}

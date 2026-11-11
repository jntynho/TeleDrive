// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.transfer

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.tgdrive.R
import com.infinity.drive.core.common.AppNotifications
import com.infinity.drive.data.local.dao.TransferDao
import com.infinity.drive.domain.model.TransferType
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Runs the shared transfer queue drain as expedited work with a dataSync
 * foreground service, so long transfers survive app death.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class TransferQueueWorker(
    appContext: Context,
    params: WorkerParameters,
    private val transferDao: TransferDao,
    private val drainer: TransferQueueDrainer,
    private val settingsRepository: SettingsRepository,
    private val appNotifications: AppNotifications,
    private val scheduler:TransferScheduler,
    private val auth:com.infinity.drive.domain.repository.TelegramAuthRepository,
    private val hiddenAccess:com.infinity.drive.core.security.HiddenContentAccess
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        TransferActivityRegistry.recover(transferDao,System.currentTimeMillis())
        if (transferDao.nextQueued(1).isEmpty()) return Result.success()
        appNotifications.createChannels()
        runCatching { setForeground(getForegroundInfo()) }

        if(!awaitTransferSession(auth))return Result.retry()
        val startedAt=System.currentTimeMillis()
        val monitor=CoroutineScope(currentCoroutineContext()).launch {
            combine(transferDao.observeActive(100),transferDao.observeActiveCount()){active,count->active to count}.sample(1000).collectLatest { (active,count) ->
                if(active.isNotEmpty())runCatching { setForeground(progressInfo(active,count)) }
                delay(800)
            }
        }
        val result = try { drainer.drain(
            isStopped = { isStopped },
            onTerminalFailure = { transfer ->
                if (settingsRepository.preferences.first().failureNotifications) {
                    appNotifications.notifyFailure(
                        title = applicationContext.getString(
                            when (transfer.type) {
                                TransferType.UPLOAD,
                                TransferType.BACKUP -> R.string.notification_upload_failed

                                TransferType.DOWNLOAD,
                                TransferType.RESTORE -> R.string.notification_download_failed
                            }
                        ),
                        message = hiddenAccess.notificationName(transfer.fileId,transfer.displayName)
                    )
                }
            }
        )
        } catch(cancel:CancellationException){
            withContext(NonCancellable){
                if(transferDao.nextQueued(1).isNotEmpty())scheduler.kick(settingsRepository.preferences.first().allowMeteredTransfers)
            }
            throw cancel
        } finally { withContext(NonCancellable){monitor.cancelAndJoin()} }
        val completed=transferDao.observeCompleted(100).first().filter{(it.completedAt?:0)>=startedAt}
        val completedCount=transferDao.completedSince(startedAt)
        if(completedCount>0&&settingsRepository.preferences.first().backupNotifications){
            appNotifications.notifyBackupResult("$completedCount transfers completed",completed.take(4).map{hiddenAccess.notificationName(it.fileId,it.displayName)}.joinToString(" · "))
        }
        return when (result) {
            TransferDrainResult.COMPLETED -> Result.success()
            TransferDrainResult.INTERRUPTED -> Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = buildNotification()
        val id = AppNotifications.NOTIFICATION_ID_TRANSFER_QUEUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(applicationContext, AppNotifications.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_transfers_active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(
                appNotifications.screenIntent(
                    AppNotifications.DESTINATION_TRANSFERS,
                    QUEUE_REQUEST_CODE
                )
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private suspend fun progressInfo(tasks:List<com.infinity.drive.data.local.entity.TransferEntity>,count:Int):ForegroundInfo {
        val total=tasks.sumOf{it.sizeBytes.coerceAtLeast(0).toDouble()}
        val done=tasks.sumOf{it.transferredBytes.coerceIn(0,it.sizeBytes.coerceAtLeast(0)).toDouble()}
        val percent=if(total>0)(done/total*100).toInt().coerceIn(0,100)else 0
        val names=tasks.take(5).associate{it.id to hiddenAccess.notificationName(it.fileId,it.displayName)}
        val style=NotificationCompat.InboxStyle()
        tasks.take(5).forEach{style.addLine("${names[it.id]} · ${if(it.sizeBytes>0)(it.transferredBytes.toDouble()/it.sizeBytes*100).toInt().coerceIn(0,100).toString()+"%"else "Preparing"}")}
        val notification=NotificationCompat.Builder(applicationContext,AppNotifications.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("$count active transfers")
            .setContentText("$percent% · ${tasks.firstOrNull()?.let{names[it.id]}.orEmpty()}").setStyle(style)
            .setProgress(100,percent,total<=0).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(appNotifications.screenIntent(AppNotifications.DESTINATION_TRANSFERS,QUEUE_REQUEST_CODE)).build()
        return if(Build.VERSION.SDK_INT>=29)ForegroundInfo(AppNotifications.NOTIFICATION_ID_TRANSFER_QUEUE,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(AppNotifications.NOTIFICATION_ID_TRANSFER_QUEUE,notification)
    }

    companion object {
        const val UNIQUE_NAME = "transfer_queue"
        private const val QUEUE_REQUEST_CODE = 3
    }
}

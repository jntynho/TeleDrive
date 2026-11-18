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
import com.infinity.drive.data.local.dao.TransferNotificationProgress
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Drains the transfer queue while a data-sync foreground service reports
 * total progress, retry waits, and terminal failure categories.
 */
@OptIn(FlowPreview::class)
class TransferQueueWorker(
    appContext: Context,
    params: WorkerParameters,
    private val transferDao: TransferDao,
    private val drainer: TransferQueueDrainer,
    private val settingsRepository: SettingsRepository,
    private val appNotifications: AppNotifications,
    private val scheduler: TransferScheduler,
    private val auth: TelegramAuthRepository,
    private val errors: TransferErrorMessages
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        TransferActivityRegistry.recover(transferDao, System.currentTimeMillis())
        if (transferDao.nextQueued(1).isEmpty()) return Result.success()
        appNotifications.createChannels()
        runCatching { setForeground(getForegroundInfo()) }

        if (!awaitTransferSession(auth)) return Result.retry()
        val startedAt = System.currentTimeMillis()
        val monitor = CoroutineScope(currentCoroutineContext()).launch {
            transferDao.observeNotificationProgress().distinctUntilChanged().sample(1000).collect { progress ->
                runCatching { setForeground(foreground(progressNotification(progress))) }
            }
        }
        val failureMutex = Mutex()
        val failures = mutableMapOf<Int, Int>()
        val result = try {
            drainer.drain(isStopped = { isStopped }) { task ->
                failureMutex.withLock {
                    val reason = transferFailureReason(task.errorMessage, task.retryableFailure, errors)
                    failures[reason] = (failures[reason] ?: 0) + 1
                    if (settingsRepository.preferences.first().failureNotifications) {
                        val details = failures.entries.sortedByDescending { it.value }.map { (message, count) ->
                            applicationContext.getString(message, count)
                        }
                        val count = failures.values.sum()
                        appNotifications.notifyTransferFailures(
                            applicationContext.resources.getQuantityString(
                                R.plurals.notification_transfer_failures, count, count
                            ),
                            details
                        )
                    }
                }
            }
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) {
                if (transferDao.nextQueued(1).isNotEmpty()) {
                    scheduler.kick(settingsRepository.preferences.first().allowMeteredTransfers)
                }
            }
            throw cancel
        } finally {
            withContext(NonCancellable) { monitor.cancelAndJoin() }
        }
        val completed = transferDao.completedOutsideBackupSince(startedAt)
        if (completed > 0 && settingsRepository.preferences.first().backupNotifications) {
            appNotifications.notifyTransferCompletion(
                applicationContext.resources.getQuantityString(
                    R.plurals.notification_transfer_completions, completed, completed
                ),
                applicationContext.getString(R.string.notification_transfer_open_activity)
            )
        }
        return when (result) {
            TransferDrainResult.COMPLETED -> Result.success()
            TransferDrainResult.INTERRUPTED -> Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(
        notificationBuilder().setProgress(0, 0, true).build()
    )

    private fun progressNotification(progress: TransferNotificationProgress): Notification {
        val details = buildList {
            if (progress.running > 0) add(applicationContext.getString(R.string.notification_transfers_running_count, progress.running))
            if (progress.queued > 0) add(applicationContext.getString(R.string.notification_transfers_queued_count, progress.queued))
            if (progress.waitingToRetry > 0) add(applicationContext.getString(R.string.notification_transfers_retry_count, progress.waitingToRetry))
            if (progress.preparing > 0) add(applicationContext.getString(R.string.notification_transfers_preparing_count, progress.preparing))
            if (progress.sealing > 0) add(applicationContext.getString(R.string.notification_transfers_sealing_count, progress.sealing))
            if (progress.joining > 0) add(applicationContext.getString(R.string.notification_transfers_joining_count, progress.joining))
        }
        val indeterminate = progress.knownBytes <= 0 || progress.unknownSize > 0
        val percent = if (indeterminate) 0 else
            (100 * progress.transferredBytes / progress.knownBytes).toInt().coerceIn(0, 100)
        val style = NotificationCompat.InboxStyle()
        details.forEach { style.addLine(it) }
        return notificationBuilder()
            .setContentText(details.take(3).joinToString(" · "))
            .setStyle(style)
            .setProgress(100, percent, indeterminate)
            .build()
    }

    private fun notificationBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(applicationContext, AppNotifications.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_transfers_active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(appNotifications.screenIntent(AppNotifications.DESTINATION_TRANSFERS, QUEUE_REQUEST_CODE))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    private fun foreground(notification: Notification): ForegroundInfo {
        val id = AppNotifications.NOTIFICATION_ID_TRANSFER_QUEUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = "transfer_queue"
        private const val QUEUE_REQUEST_CODE = 3
    }
}

/** Translates only known reasons, so system notifications never include raw remote errors or file paths. */
internal fun transferFailureReason(message: String?, retryable: Boolean, errors: TransferErrorMessages): Int =
    when (message) {
        errors.keyMissing -> R.string.notification_transfer_reason_key
        errors.noLocalCopy, errors.localFileGone -> R.string.notification_transfer_reason_local
        errors.noRemoteCopy, errors.downloadedMissing -> R.string.notification_transfer_reason_remote
        errors.saveFailed -> R.string.notification_transfer_reason_save
        errors.decryptionFailed -> R.string.notification_transfer_reason_decrypt
        else -> if (retryable) R.string.notification_transfer_reason_retryable
                else R.string.notification_transfer_reason_activity
    }

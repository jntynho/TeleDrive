// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tgdrive.R
import com.infinity.drive.core.common.AppNotifications
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Looks for a release while the app is closed. Only the notification comes from
 * here: tapping it opens the app, which checks again and shows the dialog, so
 * the release notes are never served from a stale copy.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val settingsRepository: SettingsRepository,
    private val appNotifications: AppNotifications
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = settingsRepository.preferences.first()
        if (!prefs.onboardingComplete || !prefs.updateCheckEnabled) return Result.success()

        val release = try{updateChecker.newerRelease(strict=true)}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;return Result.retry()}
        settingsRepository.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
        if (release == null) return Result.success()
        if (release.version == prefs.notifiedUpdateVersion) return Result.success()
        if (release.version == prefs.skippedUpdateVersion) return Result.success()

        appNotifications.createChannels()
        appNotifications.notifyUpdate(
            title = applicationContext.getString(R.string.notification_update_title),
            message = applicationContext.getString(
                R.string.notification_update_message,
                applicationContext.getString(R.string.app_name),
                release.version
            ),
            version = release.version
        )
        settingsRepository.update { it.copy(notifiedUpdateVersion = release.version) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "update-check"
    }
}

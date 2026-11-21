package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.infinity.drive.core.update.AppRelease

interface AppUpdateInstaller {
    fun supports(release: AppRelease): Boolean
    suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): AppUpdateInstallResult
}

enum class AppUpdateInstallResult {
    PermissionRequired,
    InstallerOpened,
    DownloadFailed,
    VerificationFailed,
    InstallerUnavailable
}

val LocalAppUpdateInstaller = staticCompositionLocalOf<AppUpdateInstaller?> { null }

val LocalPresentAppUpdate = staticCompositionLocalOf<((AppRelease) -> Unit)?> { null }

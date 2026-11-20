package com.infinity.drive.presentation.settings

import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.model.ViewMode
import com.infinity.drive.presentation.platform.PlatformCapabilities
import com.infinity.drive.resources.*
import org.jetbrains.compose.resources.StringResource

data class SettingsSearchEntry(val section:SettingsSectionType,val title:StringResource)
internal fun settingsSearchEntries(
    preferences: UserPreferences,
    capabilities: PlatformCapabilities,
    downloadLocationConfigurable: Boolean
): List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_accent_colour),
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_icon_style),
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_dashboard_style),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_skip_files_smaller_than),
    SettingsSearchEntry(SettingsSectionType.ACCOUNT,Res.string.common_storage_channels),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_automatic_backup),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_back_new_media_instantly),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_backup_interval),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_wi_fi_only),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_only_charging),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_skip_files_larger_than),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_exclusions),
    SettingsSearchEntry(SettingsSectionType.BACKUP,Res.string.settings_add_another_folder),
    SettingsSearchEntry(SettingsSectionType.STORAGE,Res.string.settings_download_location),
    SettingsSearchEntry(SettingsSectionType.STORAGE,Res.string.common_free_space),
    SettingsSearchEntry(SettingsSectionType.STORAGE,Res.string.settings_clear_cache_action),
    SettingsSearchEntry(SettingsSectionType.STORAGE,Res.string.settings_clear_thumbnails_action),
    SettingsSearchEntry(SettingsSectionType.STORAGE,Res.string.settings_cache_limit),
    SettingsSearchEntry(SettingsSectionType.STORAGE,Res.string.settings_auto_clear_trash),
    SettingsSearchEntry(SettingsSectionType.SECURITY,Res.string.settings_app_lock),
    SettingsSearchEntry(SettingsSectionType.SECURITY,Res.string.settings_auto_lock),
    SettingsSearchEntry(SettingsSectionType.SECURITY,Res.string.settings_block_screenshots),
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_floating_navigation_bar),
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_theme),
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_language),
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_gallery_grid_view),
    SettingsSearchEntry(SettingsSectionType.APPEARANCE,Res.string.settings_gallery_grid_columns),
    SettingsSearchEntry(SettingsSectionType.PLAYBACK,Res.string.settings_stream_downloading),
    SettingsSearchEntry(SettingsSectionType.PLAYBACK,Res.string.settings_background_playback),
    SettingsSearchEntry(SettingsSectionType.NOTIFICATIONS,Res.string.settings_backup_results),
    SettingsSearchEntry(SettingsSectionType.NOTIFICATIONS,Res.string.settings_failures),
    SettingsSearchEntry(SettingsSectionType.ADVANCED,Res.string.settings_proxy),
    SettingsSearchEntry(SettingsSectionType.ADVANCED,Res.string.settings_parallel_transfers),
    SettingsSearchEntry(SettingsSectionType.ADVANCED,Res.string.settings_retry_attempts),
    SettingsSearchEntry(SettingsSectionType.ADVANCED,Res.string.settings_transfers_wi_fi_only),
    SettingsSearchEntry(SettingsSectionType.ADVANCED,Res.string.settings_debug_logging)
).filter { entry ->
    when (entry.title) {
        Res.string.settings_gallery_grid_columns -> preferences.galleryViewMode == ViewMode.GRID
        Res.string.settings_back_new_media_instantly -> preferences.autoBackupEnabled
        Res.string.settings_app_lock -> capabilities.supportsAppLock
        Res.string.settings_auto_lock -> capabilities.supportsAppLock && preferences.appLockEnabled
        Res.string.settings_block_screenshots -> capabilities.supportsScreenCaptureBlocking
        Res.string.settings_download_location -> downloadLocationConfigurable
        else -> true
    }
}

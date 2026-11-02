// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.settings

import app.tgdrive.ui.DriveIcons
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.settings_section_about_subtitle
import com.infinity.drive.resources.settings_section_about_title
import com.infinity.drive.resources.settings_section_account_subtitle
import com.infinity.drive.resources.settings_section_account_title
import com.infinity.drive.resources.settings_section_advanced_subtitle
import com.infinity.drive.resources.settings_section_advanced_title
import com.infinity.drive.resources.settings_section_appearance_subtitle
import com.infinity.drive.resources.settings_section_appearance_title
import com.infinity.drive.resources.settings_section_backup_subtitle
import com.infinity.drive.resources.settings_section_backup_title
import com.infinity.drive.resources.settings_section_notifications_subtitle
import com.infinity.drive.resources.settings_section_notifications_title
import com.infinity.drive.resources.settings_section_permissions_subtitle
import com.infinity.drive.resources.settings_section_permissions_title
import com.infinity.drive.resources.settings_section_playback_subtitle
import com.infinity.drive.resources.settings_section_playback_title
import com.infinity.drive.resources.settings_section_security_subtitle
import com.infinity.drive.resources.settings_section_security_title
import com.infinity.drive.resources.settings_section_storage_subtitle
import com.infinity.drive.resources.settings_section_storage_title

enum class SettingsSectionType(
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val icon: ImageVector
) {
    ACCOUNT(
        Res.string.settings_section_account_title,
        Res.string.settings_section_account_subtitle,
        DriveIcons.AccountCircle
    ),
    BACKUP(
        Res.string.settings_section_backup_title,
        Res.string.settings_section_backup_subtitle,
        DriveIcons.CloudUpload
    ),
    STORAGE(
        Res.string.settings_section_storage_title,
        Res.string.settings_section_storage_subtitle,
        DriveIcons.Storage
    ),
    PERMISSIONS(
        Res.string.settings_section_permissions_title,
        Res.string.settings_section_permissions_subtitle,
        DriveIcons.Shield
    ),
    SECURITY(
        Res.string.settings_section_security_title,
        Res.string.settings_section_security_subtitle,
        DriveIcons.Lock
    ),
    APPEARANCE(
        Res.string.settings_section_appearance_title,
        Res.string.settings_section_appearance_subtitle,
        DriveIcons.Palette
    ),
    PLAYBACK(
        Res.string.settings_section_playback_title,
        Res.string.settings_section_playback_subtitle,
        DriveIcons.PlayCircle
    ),
    NOTIFICATIONS(
        Res.string.settings_section_notifications_title,
        Res.string.settings_section_notifications_subtitle,
        DriveIcons.Notifications
    ),
    ADVANCED(
        Res.string.settings_section_advanced_title,
        Res.string.settings_section_advanced_subtitle,
        DriveIcons.Tune
    ),
    ABOUT(
        Res.string.settings_section_about_title,
        Res.string.settings_section_about_subtitle,
        DriveIcons.Info
    )
}

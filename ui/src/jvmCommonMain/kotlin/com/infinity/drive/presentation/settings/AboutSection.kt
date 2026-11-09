// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.settings

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.about_auto_check
import com.infinity.drive.resources.about_auto_check_summary
import com.infinity.drive.resources.about_check_updates
import com.infinity.drive.resources.about_check_updates_summary
import com.infinity.drive.resources.about_checking
import com.infinity.drive.resources.about_support
import com.infinity.drive.resources.about_support_summary
import com.infinity.drive.resources.about_section_project
import com.infinity.drive.resources.about_section_updates
import com.infinity.drive.resources.about_source_code
import com.infinity.drive.resources.about_source_code_summary
import com.infinity.drive.resources.about_tagline
import com.infinity.drive.resources.about_update_ready
import com.infinity.drive.resources.about_version
import com.infinity.drive.resources.app_name
import com.infinity.drive.core.update.AppLinks
import com.infinity.drive.presentation.platform.LocalAppIcon
import com.infinity.drive.presentation.platform.LocalAppVersion
import com.infinity.drive.presentation.platform.LocalUrlOpener
import com.infinity.drive.presentation.components.UpdateDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val urlOpener = LocalUrlOpener.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val checking = updateState is UpdateState.Checking

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.surfaceContainer)))
            .padding(28.dp)
    ) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
            LocalAppIcon.current(Modifier.size(72.dp))
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(stringResource(Res.string.app_name),style=MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.about_version,LocalAppVersion.current),
                    Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=.65f)).padding(horizontal=12.dp,vertical=6.dp),
                    style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    SettingsSectionTitle(stringResource(Res.string.about_section_updates))
    SettingsGroup {
        add {
            SettingsClickRow(
                title = stringResource(Res.string.about_check_updates),
                icon = DriveIcons.Update,
                subtitle = when (val current = updateState) {
                    is UpdateState.Available -> stringResource(
                        Res.string.about_update_ready,
                        current.release.version
                    )

                    UpdateState.Checking -> stringResource(Res.string.about_checking)
                    UpdateState.Idle -> stringResource(Res.string.about_check_updates_summary)
                },
                onClick = { if (!checking) viewModel.checkForUpdates() },
                trailing = if (checking) {
                    { LoadingIndicator(modifier = Modifier.size(24.dp)) }
                } else null
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.about_auto_check),
                icon = DriveIcons.Autorenew,
                subtitle = stringResource(Res.string.about_auto_check_summary),
                checked = state.preferences.updateCheckEnabled,
                onChange = { value ->
                    viewModel.update { it.copy(updateCheckEnabled = value) }
                }
            )
        }
    }

    SettingsSectionTitle(stringResource(Res.string.about_section_project))
    SettingsGroup {
        add {
            SettingsClickRow(
                title = stringResource(Res.string.about_source_code),
                icon = DriveIcons.Code,
                subtitle = stringResource(Res.string.about_source_code_summary),
                onClick = { urlOpener.open(AppLinks.REPOSITORY) }
            )
        }
        add {
            SettingsClickRow(
                title = "Support DriveInfinity upstream",
                icon = DriveIcons.Favorite,
                subtitle = "Optional donation to the original engine maintainers",
                onClick = { urlOpener.open(AppLinks.DONATE) }
            )
        }
    }

    SettingsGroup {
        add {
            SettingsClickRow(
                title = "Credits & licenses",
                icon = DriveIcons.Info,
                subtitle = "DriveInfinity · Apache 2.0; Solar by 480 Design · CC BY 4.0",
                onClick = { urlOpener.open("https://github.com/jntynho/TG-Drive/tree/main/third_party") }
            )
        }
    }

    (updateState as? UpdateState.Available)?.let { available ->
        UpdateDialog(
            currentVersion = LocalAppVersion.current,
            onOpenUrl = urlOpener::open,
            release = available.release,
            onDownload = {
                viewModel.dismissUpdate()
                urlOpener.open(available.release.pageUrl)
            },
            onDismiss = viewModel::dismissUpdate
        )
    }
}

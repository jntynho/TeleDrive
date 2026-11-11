package com.infinity.drive.presentation.settings

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.core.update.AppLinks
import com.infinity.drive.presentation.components.UpdateDialog
import com.infinity.drive.presentation.platform.*
import com.infinity.drive.resources.*
import org.jetbrains.compose.resources.stringResource

/** Quiet identity, useful actions, and attribution; no oversized promotional banner. */
@Composable
fun AboutSection(state:SettingsUiState,viewModel:SettingsViewModel){
    val opener=LocalUrlOpener.current
    val update by viewModel.updateState.collectAsStateWithLifecycle()
    val checking=update is UpdateState.Checking
    Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),
        shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainer){
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
                LocalAppIcon.current(Modifier.size(64.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(stringResource(Res.string.app_name),style=MaterialTheme.typography.headlineSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("Your files. Your space.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text("Version",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape=RoundedCornerShape(9.dp),color=MaterialTheme.colorScheme.surfaceContainerHighest){
                    Text(LocalAppVersion.current,Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium)
                }
            }
            Text("A personal library in your private Telegram channels. Keep originals, find them easily, and enjoy your media.",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SettingsSectionTitle("UPDATES")
    SettingsGroup{
        add{SettingsClickRow(stringResource(Res.string.about_check_updates),onClick={viewModel.checkForUpdates()},
            enabled=!checking,icon=DriveIcons.Update,subtitle=when(val result=update){
                is UpdateState.Available->stringResource(Res.string.about_update_ready,result.release.version)
                UpdateState.Checking->stringResource(Res.string.about_checking)
                UpdateState.Idle->stringResource(Res.string.about_check_updates_summary)
            },trailing=if(checking){{CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)}}else null)}
        add{SettingsSwitchRow(stringResource(Res.string.about_auto_check),state.preferences.updateCheckEnabled,
            {value->viewModel.update{it.copy(updateCheckEnabled=value)}},subtitle=stringResource(Res.string.about_auto_check_summary),icon=DriveIcons.Autorenew)}
    }
    SettingsSectionTitle("PROJECT")
    SettingsGroup{
        add{SettingsClickRow("Source code",{opener.open(AppLinks.REPOSITORY)},subtitle="Explore TeleDrive on GitHub",icon=DriveIcons.Code)}
        add{SettingsClickRow("Release history",{opener.open(AppLinks.REPOSITORY+"/releases")},subtitle="Changes and installable APKs",icon=DriveIcons.Update)}
        add{SettingsClickRow("Credits & licenses",{opener.open(AppLinks.REPOSITORY+"/tree/main/third_party")},subtitle="Open-source components and acknowledgements",icon=DriveIcons.Info)}
        add{SettingsClickRow("Support upstream",{opener.open(AppLinks.DONATE)},subtitle="Optional support for the DriveInfinity engine",icon=DriveIcons.Favorite)}
    }
    Text("Made possible by open source.",Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=24.dp),
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    (update as? UpdateState.Available)?.let{available->UpdateDialog(currentVersion=LocalAppVersion.current,release=available.release,
        onOpenUrl=opener::open,onDownload={viewModel.dismissUpdate();opener.open(available.release.pageUrl)},onDismiss=viewModel::dismissUpdate)}
}

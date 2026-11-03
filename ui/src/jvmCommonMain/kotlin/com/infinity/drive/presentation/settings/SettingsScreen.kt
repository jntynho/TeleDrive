// Modified for TG Drive; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.settings

import com.infinity.drive.presentation.components.resultReveal
import app.tgdrive.ui.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.infinity.drive.presentation.components.GradientCard as Card
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.core.telegram.TelegramConnectionState
import com.infinity.drive.presentation.components.EmptyState
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.platform.LocalPlatformCapabilities
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onBack:(()->Unit)?,onOpenSection:(SettingsSectionType)->Unit,
    onOpenActivity:()->Unit={},onOpenSetting:(SettingsSectionType,String)->Unit={s,_->onOpenSection(s)},
    viewModel:SettingsViewModel=koinViewModel()) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    com.infinity.drive.presentation.common.AppBackHandler(searching){searching=false;query=""}
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val authorization by viewModel.authorization.collectAsStateWithLifecycle()
    val capabilities=LocalPlatformCapabilities.current
    val hidden=buildSet {
        if(!capabilities.supportsAutoBackup)add(SettingsSectionType.BACKUP)
        if(!capabilities.requiresPermissions)add(SettingsSectionType.PERMISSIONS)
    }
    val groups=listOf(
        "Storage & sync" to listOf(SettingsSectionType.BACKUP,SettingsSectionType.STORAGE),
        "Preferences" to listOf(SettingsSectionType.SECURITY,SettingsSectionType.APPEARANCE,SettingsSectionType.PLAYBACK),
        "App" to listOf(SettingsSectionType.NOTIFICATIONS,SettingsSectionType.PERMISSIONS,SettingsSectionType.ADVANCED,SettingsSectionType.ABOUT)
    )
    val sections=SettingsSectionType.entries.filterNot{it in hidden}
    val indexed=settingsSearchEntries().filterNot{it.section in hidden}
    val results=indexed.map { Triple(it,stringResource(it.title),stringResource(it.section.titleRes)) }
        .filter{ (_,title,parent)->"$title $parent".contains(query.trim(),true) }
    Scaffold(topBar={Column {
        ExpandableLibraryHeader("Settings",query,{query=it},searching,{searching=it},"Search settings",navigationIcon={if(onBack!=null)IconButton(onBack){Icon(DriveIcons.ArrowBack,"Back")}})
    }}) { padding ->
        if(query.isNotBlank()&&results.isEmpty()&&sections.none{stringResource(it.titleRes).contains(query.trim(),true)}) {
            EmptyState(DriveIcons.Search,"No settings found","Try a setting name, such as cache, backup or playback.",Modifier.padding(padding),"Clear search",{query=""})
        } else Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).verticalScroll(rememberScrollState()).resultReveal(query)) {
            if(query.isBlank()) {
                Card(onClick={onOpenSection(SettingsSectionType.ACCOUNT)},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=DriveStyle.CardShape,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        com.infinity.drive.presentation.components.TelegramProfileAvatar(state.user?.photoPath,Modifier.size(44.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(state.user?.let{listOf(it.firstName,it.lastName).filter(String::isNotBlank).joinToString(" ")}?.takeIf(String::isNotBlank)?:"Telegram account",style=MaterialTheme.typography.titleMedium)
                            val connection=accountConnectionLabel(authorization,state.connection)
                            Text(connection+if(state.preferences.storageChatId!=null)" · Private drive"else" · Set up your drive",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(DriveIcons.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                groups.forEachIndexed { index,(title,items) ->
                    SettingsSectionTitle(title.uppercase())
                    SettingsGroup {
                        if(index==0)add{SettingsClickRow("Activity",onOpenActivity,icon=DriveIcons.SwapVert,trailing={Icon(DriveIcons.KeyboardArrowRight,null)})}
                        items.filterNot{it in hidden}.forEach { section->add{SectionRow(section){onOpenSection(section)}} }
                    }
                }
            } else {
                val matchingSections=sections.filter{stringResource(it.titleRes).contains(query.trim(),true)}
                if(matchingSections.isNotEmpty()) {
                    SettingsSectionTitle("SECTIONS")
                    SettingsGroup{matchingSections.forEach{section->add{SectionRow(section){onOpenSection(section)}}}}
                }
                if(results.isNotEmpty()) {
                    SettingsSectionTitle("SETTINGS")
                    SettingsGroup{results.forEach{(entry,title,parent)->add{SettingsClickRow(title,{onOpenSetting(entry.section,title)},subtitle="Settings › $parent",icon=entry.section.icon,trailing={Icon(DriveIcons.KeyboardArrowRight,null)})}}}
                }
            }
            Spacer(Modifier.height(24.dp+padding.calculateBottomPadding()+LocalBottomBarInset.current))
        }
    }
}

@Composable
private fun SectionRow(section:SettingsSectionType,onClick:()->Unit) {
    SettingsClickRow(stringResource(section.titleRes),onClick,icon=section.icon,trailing={Icon(DriveIcons.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)})
}

internal fun accountConnectionLabel(auth:com.infinity.drive.core.telegram.TelegramAuthState,connection:TelegramConnectionState):String = when(auth){
    com.infinity.drive.core.telegram.TelegramAuthState.Ready->when(connection){
        TelegramConnectionState.READY->"Connected"
        TelegramConnectionState.CONNECTING->"Connecting"
        TelegramConnectionState.UPDATING->"Syncing"
        TelegramConnectionState.WAITING_FOR_NETWORK->"Waiting for network"
    }
    com.infinity.drive.core.telegram.TelegramAuthState.Initializing->"Connecting"
    com.infinity.drive.core.telegram.TelegramAuthState.Uninitialized->"Set up Telegram"
    com.infinity.drive.core.telegram.TelegramAuthState.LoggingOut->"Signing out"
    else->"Sign-in required"
}

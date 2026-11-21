// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.settings

import com.infinity.drive.presentation.navigation.canBlur
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import app.tgdrive.ui.DriveIcons
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.infinity.drive.domain.model.AppIconStyle
import com.infinity.drive.domain.model.deleteCustomTheme
import com.infinity.drive.domain.model.saveCustomTheme
import com.infinity.drive.domain.model.selectAccentColour
import com.infinity.drive.domain.model.selectCustomTheme
import com.infinity.drive.resources.settings_icon_style
import com.infinity.drive.resources.settings_icon_outline
import com.infinity.drive.resources.settings_icon_broken
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.domain.model.AppLanguage
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.recovery_export_auth_prompt
import com.infinity.drive.resources.recovery_export_subtitle
import com.infinity.drive.resources.recovery_export_title
import com.infinity.drive.resources.language_english
import com.infinity.drive.resources.language_arabic
import com.infinity.drive.resources.language_labels
import com.infinity.drive.resources.language_portuguese_br
import com.infinity.drive.resources.language_russian
import com.infinity.drive.resources.language_system
import com.infinity.drive.resources.settings_language
import com.infinity.drive.resources.backup_preview_description
import com.infinity.drive.resources.backup_preview_title
import com.infinity.drive.resources.backup_folder_remove_message
import com.infinity.drive.resources.backup_folder_subtitle
import com.infinity.drive.resources.backup_rule_title
import com.infinity.drive.resources.backup_rule_subtitle
import com.infinity.drive.resources.backup_rule_pick_folder
import com.infinity.drive.domain.model.BackupFolderRule
import com.infinity.drive.resources.backup_interval_labels
import com.infinity.drive.resources.backup_interval_with_sweep
import com.infinity.drive.resources.backup_size_limit_labels
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_clear
import com.infinity.drive.resources.common_free_space
import com.infinity.drive.resources.common_storage_channels
import com.infinity.drive.resources.lock_timeout_labels
import com.infinity.drive.resources.settings_add_another_folder
import com.infinity.drive.resources.settings_app_lock
import com.infinity.drive.resources.settings_auto_clear_trash
import com.infinity.drive.resources.settings_auto_lock
import com.infinity.drive.resources.settings_automatic_backup
import com.infinity.drive.resources.settings_back_new_media_instantly
import com.infinity.drive.resources.settings_background_playback
import com.infinity.drive.resources.settings_backup_folders_title
import com.infinity.drive.resources.settings_backup_interval
import com.infinity.drive.resources.settings_backup_results
import com.infinity.drive.resources.settings_block_screenshots
import com.infinity.drive.resources.settings_cache_current_size
import com.infinity.drive.resources.settings_cache_limit
import com.infinity.drive.resources.settings_cached_previews_thumbnails_temporary
import com.infinity.drive.resources.settings_change_passphrase
import com.infinity.drive.resources.settings_choose_folder_device
import com.infinity.drive.resources.settings_clear_cache_action
import com.infinity.drive.resources.settings_clear_cache_title
import com.infinity.drive.resources.settings_clear_thumbnails_action
import com.infinity.drive.resources.settings_clear_thumbnails_title
import com.infinity.drive.resources.settings_confirm_removing_lock
import com.infinity.drive.resources.settings_debug_logging
import com.infinity.drive.resources.settings_exclusions
import com.infinity.drive.resources.settings_failures
import com.infinity.drive.resources.settings_files_folders_skipped_backup
import com.infinity.drive.resources.settings_files_stay_telegram_channel
import com.infinity.drive.resources.settings_folder_unreadable
import com.infinity.drive.resources.settings_verified_cleanup_title
import com.infinity.drive.resources.settings_gallery_previews_regenerated_browse
import com.infinity.drive.resources.settings_gallery_grid_columns
import com.infinity.drive.resources.settings_gallery_grid_view
import com.infinity.drive.resources.settings_hides_app_screenshots_screen
import com.infinity.drive.resources.settings_key_backup_stored
import com.infinity.drive.resources.settings_link_previews
import com.infinity.drive.resources.settings_link_previews_subtitle
import com.infinity.drive.resources.settings_verified_cleanup_description
import com.infinity.drive.resources.settings_cleanup_checking
import com.infinity.drive.resources.settings_log
import com.infinity.drive.resources.settings_log_telegram
import com.infinity.drive.resources.settings_no_folders_selected
import com.infinity.drive.resources.settings_no_recovery_passphrase
import com.infinity.drive.resources.settings_only_charging
import com.infinity.drive.resources.settings_parallel_transfers
import com.infinity.drive.resources.settings_play_videos_load_instead
import com.infinity.drive.resources.settings_proxy
import com.infinity.drive.resources.settings_proxy_subtitle
import com.infinity.drive.resources.settings_recent_files
import com.infinity.drive.resources.settings_recent_files_subtitle
import com.infinity.drive.resources.settings_remove
import com.infinity.drive.resources.settings_remove_backup_folder
import com.infinity.drive.resources.settings_removes_session_stored_api
import com.infinity.drive.resources.settings_require_fingerprint_screen_lock
import com.infinity.drive.resources.settings_required_before_encrypt
import com.infinity.drive.resources.settings_retry_attempts
import com.infinity.drive.resources.settings_set
import com.infinity.drive.resources.settings_set_passphrase
import com.infinity.drive.resources.settings_shown_channels_private_owned
import com.infinity.drive.resources.settings_skip_files_larger_than
import com.infinity.drive.resources.settings_stream_downloading
import com.infinity.drive.resources.settings_switch_drives_create_another
import com.infinity.drive.resources.settings_theme
import com.infinity.drive.resources.settings_transfers_wi_fi_only
import com.infinity.drive.resources.settings_transfers_wi_fi_only_summary
import com.infinity.drive.resources.settings_turn_off_app_lock
import com.infinity.drive.resources.settings_uploads_photos_videos_soon
import com.infinity.drive.resources.settings_uploads_stay_unencrypted_set
import com.infinity.drive.resources.settings_wi_fi_only
import com.infinity.drive.resources.settings_wi_fi_only_summary
import com.infinity.drive.resources.theme_dark
import com.infinity.drive.resources.theme_amoled
import com.infinity.drive.resources.theme_labels
import com.infinity.drive.resources.theme_light
import com.infinity.drive.resources.theme_system
import com.infinity.drive.resources.trash_clear_labels
import com.infinity.drive.domain.model.AppTheme
import com.infinity.drive.domain.model.ViewMode
import com.infinity.drive.presentation.platform.LocalDownloadLocationConfigurable
import com.infinity.drive.resources.settings_download_location
import com.infinity.drive.resources.settings_download_location_default
import com.infinity.drive.resources.settings_download_location_reset
import com.infinity.drive.resources.settings_free_up_none
import com.infinity.drive.resources.settings_free_up_subtitle
import com.infinity.drive.presentation.platform.LocalDeleteConsentLauncher
import com.infinity.drive.presentation.platform.LocalStandardFolders
import com.infinity.drive.presentation.platform.LocalDeviceOwnerGate
import com.infinity.drive.presentation.platform.LocalFolderPicker
import com.infinity.drive.presentation.platform.LocalPlatformCapabilities
import com.infinity.drive.presentation.platform.PickResult
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.CollectSnackbarMessages
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.components.ChoiceDialog
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.rememberToolbarLift

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSectionType,
    onOpenChannels: () -> Unit,
    onOpenPreviousLibrary: () -> Unit,
    onBack: () -> Unit,
    onOpenExclusions: () -> Unit,
    onOpenProxy: () -> Unit,
    onLoggedOut: () -> Unit,
    onDashboardStyles:()->Unit={},
    onOpenRecoveryExport: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var customThemePage by rememberSaveable { mutableStateOf<String?>(null) }
    val customTheme = customThemePage
    if (section == SettingsSectionType.APPEARANCE && customTheme != null) {
        CustomThemeEditorScreen(
            preferences = state.preferences,
            themeId = customTheme,
            onBack = { customThemePage = null },
            onSave = { theme ->
                viewModel.update { it.saveCustomTheme(theme) }
                customThemePage = null
            }
        )
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var navigationOptions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    com.infinity.drive.presentation.common.AppBackHandler(navigationOptions){navigationOptions=false}
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }

    val deleteConsentLauncher = LocalDeleteConsentLauncher.current

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)
    LaunchedEffect(Unit) {
        viewModel.deleteConsentRequests.collect { request ->
            deleteConsentLauncher.launch(request, viewModel::onVerifiedCleanupConsent)
        }
    }

    val scrollState = rememberScrollState()
    val lifted by rememberToolbarLift(scrollState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            app.tgdrive.ui.LibraryHeader(
                title = if(navigationOptions)"Floating navigation bar"else stringResource(section.titleRes),
                navigationIcon = {
                    IconButton(onClick = {if(navigationOptions)navigationOptions=false else onBack()}) {
                        Icon(
                            DriveIcons.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(8.dp))
            when (section) {
                SettingsSectionType.ACCOUNT -> AccountSection(
                    onPickChannel = onOpenChannels,
                    state = state,
                    viewModel = viewModel,
                    onLogout = { confirmLogout = true },
                    onOpenRecoveryExport = onOpenRecoveryExport
                )

                SettingsSectionType.BACKUP -> BackupSection(
                    state = state,
                    viewModel = viewModel,
                    onOpenExclusions = onOpenExclusions
                )

                SettingsSectionType.STORAGE -> StorageSection(
                    state = state,
                    viewModel = viewModel,
                    onClearCache = { confirmClearCache = true }
                )

                SettingsSectionType.PERMISSIONS ->
                    PermissionsSection(viewModel.permissionChecker)

                SettingsSectionType.SECURITY -> SecuritySection(state, viewModel)
                SettingsSectionType.APPEARANCE -> androidx.compose.animation.Crossfade(navigationOptions,animationSpec=androidx.compose.animation.core.tween(240),label="navigationPreferencesPage"){open->
                    if (open) NavigationBarSection(state, viewModel)
                    else AppearanceSection(state, viewModel, onDashboardStyles,
                        onNavigation = { navigationOptions = true }, onCustomTheme = { customThemePage = it })
                }
                SettingsSectionType.PLAYBACK -> PlaybackSection(state, viewModel)
                SettingsSectionType.NOTIFICATIONS -> NotificationsSection(state, viewModel)
                SettingsSectionType.ADVANCED -> AdvancedSection(
                    state = state,
                    viewModel = viewModel,
                    onOpenProxy = onOpenProxy
                )
                SettingsSectionType.ABOUT -> AboutSection(state, viewModel)
            }
            Spacer(Modifier.height(24.dp + padding.calculateBottomPadding()+com.infinity.drive.presentation.navigation.LocalBottomBarHeight.current))
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_log_telegram),
            message = stringResource(Res.string.settings_files_stay_telegram_channel),
            confirmLabel = stringResource(Res.string.settings_log),
            destructive = true,
            onConfirm = {
                confirmLogout = false
                viewModel.logout(onLoggedOut)
            },
            onDismiss = { confirmLogout = false }
        )
    }
    if (confirmClearCache) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_clear_cache_title),
            message = stringResource(Res.string.settings_cached_previews_thumbnails_temporary),
            confirmLabel = stringResource(Res.string.settings_clear_cache_action),
            onConfirm = {
                confirmClearCache = false
                viewModel.clearCache()
            },
            onDismiss = { confirmClearCache = false }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onPickChannel: () -> Unit,
    onLogout: () -> Unit,
    onOpenRecoveryExport: () -> Unit
) {
    val recoveryGate = com.infinity.drive.presentation.platform.LocalRecoveryDeviceOwnerGate.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var authenticating by remember { mutableStateOf(false) }
    var openRecovery by remember { mutableStateOf(false) }
    val recoveryTitle = stringResource(Res.string.recovery_export_title)
    val recoveryPrompt = stringResource(Res.string.recovery_export_auth_prompt)
    val openExport by androidx.compose.runtime.rememberUpdatedState(onOpenRecoveryExport)
    LaunchedEffect(openRecovery, owner) {
        if (openRecovery) {
            owner.lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
            authenticating = false
            openRecovery = false
            openExport()
        }
    }
    val platform = com.infinity.drive.presentation.platform.LocalPlatformScreens.current
    val authorization by viewModel.authorization.collectAsStateWithLifecycle()
    val profileName = state.user?.let { user ->
        listOf(user.firstName, user.lastName).filter(String::isNotBlank).joinToString(" ")
    }?.takeIf(String::isNotBlank)

    SettingsGroup {
        add {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                com.infinity.drive.presentation.components.TelegramProfileAvatar(state.user?.photoPath,Modifier.size(56.dp))
                Column(Modifier.weight(1f).padding(start=16.dp)){
                    Text(profileName ?: "Telegram account", style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(state.user?.username?.takeIf(String::isNotBlank)?.let{"@$it"}?:"Your personal cloud",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                platform.AccountProfilesSetting(
                    profileName = profileName,
                    username = state.user?.username,
                    photoPath = state.user?.photoPath
                )
            }
        }
        val detail = listOfNotNull(
            accountConnectionLabel(authorization, state.connection),
            "Premium (4 GB uploads)".takeIf { state.user?.isPremium == true }
        ).joinToString(" · ")
        add(visible = detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.common_storage_channels),
                subtitle = stringResource(Res.string.settings_switch_drives_create_another),
                onClick = onPickChannel
            )
        }
        add {
            SettingsClickRow(
                title = recoveryTitle,
                subtitle = stringResource(Res.string.recovery_export_subtitle),
                enabled = !authenticating && authorization == com.infinity.drive.core.telegram.TelegramAuthState.Ready,
                onClick = {
                    if (!authenticating) {
                        authenticating = true
                        recoveryGate.require(recoveryTitle, recoveryPrompt, onDenied = { message ->
                            authenticating = false
                            message?.let { viewModel.notify(UiText.Plain(it)) }
                        }, onConfirmed = { openRecovery = true })
                    }
                }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_log),
                subtitle = stringResource(Res.string.settings_removes_session_stored_api),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun BackupSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenExclusions: () -> Unit
) {
    val prefs = state.preferences
    var folderToRemove by remember { mutableStateOf<String?>(null) }
    val folderUnreadableMessage = UiText.Resource(Res.string.settings_folder_unreadable)
    val folderPicker = LocalFolderPicker.current
    val onPickBackupFolder = {
        folderPicker.pick { result ->
            when (result) {
                is PickResult.Canceled -> Unit
                is PickResult.Unreadable -> viewModel.notify(folderUnreadableMessage)
                is PickResult.Picked -> viewModel.addBackupFolder(result.path)
            }
        }
    }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showSizeLimitDialog by remember { mutableStateOf(false) }
    var showMinimumDialog by remember { mutableStateOf(false) }

    var showBackupPreview by remember { mutableStateOf(false) }
    val backupPreview by viewModel.backupPreview.collectAsStateWithLifecycle()
    if (showBackupPreview) {
        BackupPreviewDialog(
            state = backupPreview,
            onClose = {
                viewModel.cancelBackupPreview()
                showBackupPreview = false
            },
            onStart = {
                viewModel.startBackupAfterPreview()
                showBackupPreview = false
            }
        )
    }
    if (showIntervalDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.settings_backup_interval),
            options = intervalOptions.map { it to intervalLabel(it) },
            selected = prefs.backupIntervalHours,
            onSelect = { hours ->
                showIntervalDialog = false
                viewModel.update { it.copy(backupIntervalHours = hours) }
            },
            onDismiss = { showIntervalDialog = false }
        )
    }
    if (showSizeLimitDialog) BackupSizeDialog(false,prefs.backupMaxFileSizeMb,prefs.backupMinFileSizeMb,
        onSelect={limit->showSizeLimitDialog=false;viewModel.update{it.copy(backupMaxFileSizeMb=limit)}},onDismiss={showSizeLimitDialog=false})
    if (showMinimumDialog) BackupSizeDialog(true,prefs.backupMinFileSizeMb,prefs.backupMaxFileSizeMb,
        onSelect={limit->showMinimumDialog=false;viewModel.update{it.copy(backupMinFileSizeMb=limit)}},onDismiss={showMinimumDialog=false})

    folderToRemove?.let { folder ->
        ConfirmDialog(
            title = stringResource(Res.string.settings_remove_backup_folder),
            message = stringResource(Res.string.backup_folder_remove_message, folder),
            confirmLabel = stringResource(Res.string.settings_remove),
            destructive = true,
            onConfirm = {
                folderToRemove = null
                viewModel.removeBackupFolder(folder)
            },
            onDismiss = { folderToRemove = null }
        )
    }

    val platform = com.infinity.drive.presentation.platform.LocalPlatformScreens.current
    SettingsGroup {
        add { platform.FolderSyncSetting() }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.backup_preview_title),
                subtitle = stringResource(Res.string.backup_preview_description),
                onClick = {
                    viewModel.previewBackup()
                    showBackupPreview = true
                }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_automatic_backup),
                checked = prefs.autoBackupEnabled,
                onChange = { value ->
                    viewModel.update { it.copy(autoBackupEnabled = value) }
                }
            )
        }
        add(visible = prefs.autoBackupEnabled) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_back_new_media_instantly),
                subtitle = stringResource(Res.string.settings_uploads_photos_videos_soon),
                checked = prefs.instantBackupEnabled,
                onChange = { value ->
                    viewModel.update { it.copy(instantBackupEnabled = value) }
                }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_backup_interval),
                subtitle = if (prefs.instantBackupEnabled && prefs.autoBackupEnabled) {
                    stringResource(
                        Res.string.backup_interval_with_sweep,
                        intervalLabel(prefs.backupIntervalHours)
                    )
                } else {
                    intervalLabel(prefs.backupIntervalHours)
                },
                onClick = { showIntervalDialog = true }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_wi_fi_only),
                subtitle = stringResource(Res.string.settings_wi_fi_only_summary),
                checked = prefs.backupWifiOnly,
                onChange = { value -> viewModel.update { it.copy(backupWifiOnly = value) } }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_only_charging),
                checked = prefs.backupChargingOnly,
                onChange = { value ->
                    viewModel.update { it.copy(backupChargingOnly = value) }
                }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_skip_files_larger_than),
                subtitle = sizeLimitLabel(prefs.backupMaxFileSizeMb),
                onClick = { showSizeLimitDialog = true }
            )
        }
        add {
            SettingsClickRow("Skip files smaller than",{showMinimumDialog=true},subtitle=if(prefs.backupMinFileSizeMb==0)"No limit"else"${prefs.backupMinFileSizeMb} MB")
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_exclusions),
                subtitle = stringResource(Res.string.settings_files_folders_skipped_backup),
                onClick = onOpenExclusions
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SettingsSectionTitle(stringResource(Res.string.settings_backup_folders_title))

    val backupFolders by viewModel.backupFolders.collectAsStateWithLifecycle()
    var chooseRuleFolder by remember { mutableStateOf(false) }
    var editingRule by remember(prefs.storageChatId) { mutableStateOf<BackupFolderRule?>(null) }
    if (chooseRuleFolder && backupFolders.isNotEmpty()) {
        ChoiceDialog(
            title = stringResource(Res.string.backup_rule_pick_folder),
            options = backupFolders.sorted().map { it to it },
            selected = "",
            onSelect = { path ->
                chooseRuleFolder = false
                prefs.storageChatId?.let { chat ->
                    editingRule = prefs.backupFolderRules.firstOrNull { it.chatId == chat && it.path == path }
                        ?: BackupFolderRule(chat, path)
                }
            },
            onDismiss = { chooseRuleFolder = false }
        )
    }
    editingRule?.let { rule ->
        BackupFolderRuleDialog(rule, onSave = {
            viewModel.saveBackupFolderRule(it)
            editingRule = null
        }, onDismiss = { editingRule = null })
    }


    if (prefs.autoBackupEnabled && backupFolders.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings_no_folders_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    val standardFolders = LocalStandardFolders.current
    val standardPaths = standardFolders.map { it.path }.toSet()
    val customFolders = backupFolders.filterNot { it in standardPaths }
    SettingsGroup {
        standardFolders.forEach { folder ->
            add {
                SettingsSwitchRow(
                    title = stringResource(folder.label),
                    subtitle = folder.path,
                    checked = folder.path in backupFolders,
                    onChange = { checked ->
                        if (checked) {
                            viewModel.addBackupFolder(folder.path)
                        } else {
                            viewModel.removeBackupFolder(folder.path)
                        }
                    }
                )
            }
        }
        customFolders.forEach { folder ->
            add {
                SettingsClickRow(
                    title = folder.substringAfterLast('/'),
                    subtitle = stringResource(Res.string.backup_folder_subtitle, folder),
                    onClick = { folderToRemove = folder }
                )
            }
        }
        if (backupFolders.isNotEmpty()) add {
            SettingsClickRow(
                title = stringResource(Res.string.backup_rule_title),
                subtitle = stringResource(Res.string.backup_rule_subtitle),
                onClick = { chooseRuleFolder = true }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_add_another_folder),
                subtitle = stringResource(Res.string.settings_choose_folder_device),
                onClick = { onPickBackupFolder() }
            )
        }
    }
}

@Composable
private fun StorageSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onClearCache: () -> Unit
) {
    val prefs = state.preferences
    val cleaningLocal by viewModel.cleaningLocal.collectAsStateWithLifecycle()
    val reclaimable by viewModel.reclaimableBytes.collectAsStateWithLifecycle()
    var showTrashDialog by remember { mutableStateOf(false) }
    var confirmFreeUpSpace by remember { mutableStateOf(false) }
    var confirmClearThumbnails by remember { mutableStateOf(false) }

    if (confirmClearThumbnails) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_clear_thumbnails_title),
            message = stringResource(Res.string.settings_gallery_previews_regenerated_browse),
            confirmLabel = stringResource(Res.string.common_clear),
            onConfirm = {
                confirmClearThumbnails = false
                viewModel.clearThumbnails()
            },
            onDismiss = { confirmClearThumbnails = false }
        )
    }

    if (confirmFreeUpSpace) {
        ConfirmDialog(
            title = stringResource(
                Res.string.settings_verified_cleanup_title,
                Formatters.bytes(reclaimable)
            ),
            message = stringResource(Res.string.settings_verified_cleanup_description),
            confirmLabel = stringResource(Res.string.common_free_space),
            destructive = true,
            onConfirm = {
                confirmFreeUpSpace = false
                viewModel.freeUpSpace()
            },
            onDismiss = { confirmFreeUpSpace = false }
        )
    }

    if (showTrashDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.settings_auto_clear_trash),
            options = trashDayOptions.map { it to trashDaysLabel(it) },
            selected = prefs.trashAutoClearDays,
            onSelect = { days ->
                showTrashDialog = false
                viewModel.update { it.copy(trashAutoClearDays = days) }
            },
            onDismiss = { showTrashDialog = false }
        )
    }

    val downloadLocationConfigurable = LocalDownloadLocationConfigurable.current
    val downloadFolderPicker = LocalFolderPicker.current
    SettingsGroup {
        add(visible = downloadLocationConfigurable) {
            SettingsClickRow(
                title = stringResource(Res.string.settings_download_location),
                subtitle = prefs.downloadDirectory
                    ?: stringResource(Res.string.settings_download_location_default),
                onClick = {
                    downloadFolderPicker.pick { result ->
                        if (result is PickResult.Picked) {
                            viewModel.update { it.copy(downloadDirectory = result.path) }
                        }
                    }
                }
            )
        }
        add(visible = downloadLocationConfigurable && prefs.downloadDirectory != null) {
            SettingsClickRow(
                title = stringResource(Res.string.settings_download_location_reset),
                subtitle = stringResource(Res.string.settings_download_location_default),
                onClick = { viewModel.update { it.copy(downloadDirectory = null) } }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.common_free_space),
                subtitle = if (cleaningLocal) {
                    stringResource(Res.string.settings_cleanup_checking)
                } else if (reclaimable > 0) {
                    stringResource(
                        Res.string.settings_free_up_subtitle,
                        Formatters.bytes(reclaimable)
                    )
                } else {
                    stringResource(Res.string.settings_free_up_none)
                },
                enabled = reclaimable > 0 && !cleaningLocal,
                onClick = { confirmFreeUpSpace = true }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_clear_cache_action),
                subtitle = stringResource(
                    Res.string.settings_cache_current_size,
                    Formatters.bytes(state.cacheStats.totalBytes)
                ),
                onClick = onClearCache
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_clear_thumbnails_action),
                subtitle = Formatters.bytes(state.cacheStats.thumbnailBytes),
                onClick = { confirmClearThumbnails = true }
            )
        }
        add {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_cache_limit),
                value = prefs.maxCacheSizeMb / 256,
                range = 1..16,
                onChange = { value ->
                    viewModel.update { it.copy(maxCacheSizeMb = value * 256) }
                },
                valueLabel = { Formatters.bytes(it * 256L * 1024 * 1024) }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_auto_clear_trash),
                subtitle = trashDaysLabel(prefs.trashAutoClearDays),
                onClick = { showTrashDialog = true }
            )
        }
    }
}

@Composable
private fun SecuritySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val prefs = state.preferences
    val deviceOwnerGate = LocalDeviceOwnerGate.current
    val keyBackupWorking by viewModel.keyBackupWorking.collectAsStateWithLifecycle()
    val lockPromptTitle = stringResource(Res.string.settings_turn_off_app_lock)
    val lockPromptSubtitle = stringResource(Res.string.settings_confirm_removing_lock)
    var showLockTimeoutDialog by remember { mutableStateOf(false) }
    var showKeyBackupDialog by remember { mutableStateOf(false) }
    if (showKeyBackupDialog) {
        KeyBackupDialog(
            working = keyBackupWorking,
            onConfirm = { passphrase, hint ->
                showKeyBackupDialog = false
                viewModel.backUpEncryptionKey(passphrase, hint)
            },
            onDismiss = { showKeyBackupDialog = false }
        )
    }

    if (prefs.encryptFiles && !prefs.keyBackupCreated) {
        KeyBackupWarning(
            onSetPassphrase = {
                showKeyBackupDialog = true
            }
        )
        Spacer(Modifier.height(12.dp))
    }

    if (showLockTimeoutDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.settings_auto_lock),
            options = lockTimeoutOptions.map { it to lockTimeoutLabel(it) },
            selected = prefs.autoLockTimeoutMinutes,
            onSelect = { minutes ->
                showLockTimeoutDialog = false
                viewModel.update { it.copy(autoLockTimeoutMinutes = minutes) }
            },
            onDismiss = { showLockTimeoutDialog = false }
        )
    }

    val capabilities = LocalPlatformCapabilities.current
    SettingsGroup {
        add(visible = capabilities.supportsAppLock) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_app_lock),
                subtitle = stringResource(Res.string.settings_require_fingerprint_screen_lock),
                checked = prefs.appLockEnabled,
                onChange = { value ->
                    if (value) {
                        viewModel.update { it.copy(appLockEnabled = true) }
                    } else {
                        deviceOwnerGate.require(
                            title = lockPromptTitle,
                            subtitle = lockPromptSubtitle,
                            onDenied = { error ->
                                error?.let { viewModel.notify(UiText.Plain(it)) }
                            }
                        ) {
                            viewModel.update { it.copy(appLockEnabled = false) }
                        }
                    }
                }
            )
        }
        add(visible = capabilities.supportsAppLock && prefs.appLockEnabled) {
            SettingsClickRow(
                title = stringResource(Res.string.settings_auto_lock),
                subtitle = lockTimeoutLabel(prefs.autoLockTimeoutMinutes),
                onClick = { showLockTimeoutDialog = true }
            )
        }
        add(visible = capabilities.supportsScreenCaptureBlocking) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_block_screenshots),
                subtitle = stringResource(Res.string.settings_hides_app_screenshots_screen),
                checked = prefs.blockScreenCapture,
                onChange = { value ->
                    viewModel.update { it.copy(blockScreenCapture = value) }
                }
            )
        }
        add(visible = prefs.encryptFiles) {
            SettingsClickRow(
                title = if (prefs.keyBackupCreated) {
                    stringResource(Res.string.settings_change_passphrase)
                } else {
                    stringResource(Res.string.settings_set_passphrase)
                },
                subtitle = if (prefs.keyBackupCreated) {
                    stringResource(Res.string.settings_key_backup_stored)
                } else {
                    stringResource(Res.string.settings_required_before_encrypt)
                },
                onClick = {
                    showKeyBackupDialog = true
                }
            )
        }
    }
}

@Composable
private fun KeyBackupWarning(onSetPassphrase: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = DriveIcons.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_no_recovery_passphrase),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(Res.string.settings_uploads_stay_unencrypted_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onSetPassphrase) {
                Text(
                    text = stringResource(Res.string.settings_set),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onDashboardStyles: () -> Unit,
    onNavigation: () -> Unit,
    onCustomTheme: (String) -> Unit
) {
    val prefs = state.preferences
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showIconStyleDialog by rememberSaveable { mutableStateOf(false) }

    if (showIconStyleDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.settings_icon_style),
            options = AppIconStyle.entries.map { it to iconStyleLabel(it) },
            selected = prefs.iconStyle,
            onSelect = { style ->
                showIconStyleDialog = false
                viewModel.update { it.copy(iconStyle = style) }
            },
            onDismiss = { showIconStyleDialog = false }
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.settings_theme),
            options = AppTheme.entries.zip(stringArrayResource(Res.array.theme_labels)),
            selected = prefs.theme,
            onSelect = { choice ->
                showThemeDialog = false
                viewModel.update { it.copy(theme = choice) }
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.settings_language),
            options = AppLanguage.entries.zip(stringArrayResource(Res.array.language_labels)),
            selected = prefs.language,
            onSelect = { choice ->
                showLanguageDialog = false
                viewModel.update { it.copy(language = choice) }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    SettingsGroup {
        add{SettingsClickRow("Dashboard Style",onDashboardStyles,subtitle=prefs.dashboardStyle.label+" · "+(if(prefs.dashboardStyle.name in prefs.dashboardDarkStyles)"Dark"else"Light"))}
        add {
            AccentColourPicker(
                preferences = prefs,
                onSelect = { colour -> viewModel.update { it.selectAccentColour(colour) } },
                onSelectCustom = { id -> viewModel.update { it.selectCustomTheme(id) } },
                onEditCustom = onCustomTheme,
                onDeleteCustom = { id -> viewModel.update { it.deleteCustomTheme(id) } }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_icon_style),
                subtitle = iconStyleLabel(prefs.iconStyle),
                onClick = { showIconStyleDialog = true },
                trailing = { Icon(DriveIcons.IconStyle, contentDescription = null, modifier = Modifier.size(24.dp)) }
            )
        }

        add{SettingsClickRow("Floating navigation bar",onNavigation,subtitle="Style and appearance of your bottom navigation")}
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_theme),
                subtitle = when (prefs.theme) {
                    AppTheme.LIGHT -> stringResource(Res.string.theme_light)
                    AppTheme.DARK -> stringResource(Res.string.theme_dark)
                    AppTheme.AMOLED -> stringResource(Res.string.theme_amoled)
                    AppTheme.SYSTEM -> stringResource(Res.string.theme_system)
                },
                onClick = { showThemeDialog = true }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_language),
                subtitle = when (prefs.language) {
                    AppLanguage.SYSTEM -> stringResource(Res.string.language_system)
                    AppLanguage.ENGLISH -> stringResource(Res.string.language_english)
                    AppLanguage.RUSSIAN -> stringResource(Res.string.language_russian)
                    AppLanguage.PORTUGUESE_BR -> stringResource(Res.string.language_portuguese_br)
                    AppLanguage.ARABIC -> stringResource(Res.string.language_arabic)
                },
                onClick = { showLanguageDialog = true }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_gallery_grid_view),
                checked = prefs.galleryViewMode == ViewMode.GRID,
                onChange = { value ->
                    viewModel.update {
                        it.copy(galleryViewMode = if (value) ViewMode.GRID else ViewMode.LIST)
                    }
                }
            )
        }
        add(visible = prefs.galleryViewMode == ViewMode.GRID) {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_gallery_grid_columns),
                value = prefs.galleryGridSize,
                range = 2..6,
                onChange = { value -> viewModel.update { it.copy(galleryGridSize = value) } }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_link_previews),
                subtitle = stringResource(Res.string.settings_link_previews_subtitle),
                checked = prefs.linkPreviews,
                onChange = { value -> viewModel.update { it.copy(linkPreviews = value) } }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_recent_files),
                subtitle = stringResource(Res.string.settings_recent_files_subtitle),
                checked = prefs.showRecentFiles,
                onChange = { value -> viewModel.update { it.copy(showRecentFiles = value) } }
            )
        }
    }
}

@Composable
private fun iconStyleLabel(style: AppIconStyle): String = stringResource(
    when (style) {
        AppIconStyle.OUTLINE -> Res.string.settings_icon_outline
        AppIconStyle.BROKEN -> Res.string.settings_icon_broken
    }
)

@Composable
private fun PlaybackSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val prefs = state.preferences
    SettingsGroup {
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_stream_downloading),
                subtitle = stringResource(Res.string.settings_play_videos_load_instead),
                checked = prefs.streamBeforeDownload,
                onChange = { value ->
                    viewModel.update { it.copy(streamBeforeDownload = value) }
                }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_background_playback),
                checked = prefs.backgroundPlayback,
                onChange = { value ->
                    viewModel.update { it.copy(backgroundPlayback = value) }
                }
            )
        }
    }
}

@Composable
private fun NotificationsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val prefs = state.preferences
    SettingsGroup {
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_backup_results),
                checked = prefs.backupNotifications,
                onChange = { value ->
                    viewModel.update { it.copy(backupNotifications = value) }
                }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_failures),
                checked = prefs.failureNotifications,
                onChange = { value ->
                    viewModel.update { it.copy(failureNotifications = value) }
                }
            )
        }
    }
}

@Composable
private fun AdvancedSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenProxy: () -> Unit
) {
    val prefs = state.preferences
    SettingsGroup {
        add {
            SettingsClickRow(
                title = stringResource(Res.string.settings_proxy),
                subtitle = stringResource(Res.string.settings_proxy_subtitle),
                onClick = onOpenProxy
            )
        }
        add {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_parallel_transfers),
                value = prefs.transferConcurrency.coerceIn(1,2),
                range = 1..2,
                onChange = { value ->
                    viewModel.update { it.copy(transferConcurrency = value) }
                }
            )
        }
        add {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_retry_attempts),
                value = prefs.transferRetryCount,
                range = 0..10,
                onChange = { value ->
                    viewModel.update { it.copy(transferRetryCount = value) }
                }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_transfers_wi_fi_only),
                subtitle = stringResource(Res.string.settings_transfers_wi_fi_only_summary),
                checked = !prefs.allowMeteredTransfers,
                onChange = { value ->
                    viewModel.update { it.copy(allowMeteredTransfers = !value) }
                }
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_debug_logging),
                checked = prefs.debugLogging,
                onChange = { value -> viewModel.update { it.copy(debugLogging = value) } }
            )
        }
    }
}

private val intervalOptions = listOf(1, 2, 4, 6, 12, 24, 48)

@Composable
private fun intervalLabel(hours: Int): String =
    labelFor(Res.array.backup_interval_labels, intervalOptions, hours) { "$it h" }

private val sizeLimitOptions = listOf(0, 100, 500, 1000, 2000, 4000)

@Composable
private fun sizeLimitLabel(mb: Int): String =
    labelFor(Res.array.backup_size_limit_labels, sizeLimitOptions, mb) { "$it MB" }

private val lockTimeoutOptions = listOf(0, 1, 5, 10, 15, 30)

@Composable
private fun lockTimeoutLabel(minutes: Int): String =
    labelFor(Res.array.lock_timeout_labels, lockTimeoutOptions, minutes) { "$it min" }

private val trashDayOptions = listOf(0, 1, 7, 30, 90, 365)

@Composable
private fun trashDaysLabel(days: Int): String =
    labelFor(Res.array.trash_clear_labels, trashDayOptions, days) { "$it d" }

/**
 * Choice labels live in arrays.xml so a translation can reword them freely.
 * A value outside the offered set falls back to a plain formatted number.
 */
@Composable
private fun labelFor(
    arrayId: StringArrayResource,
    values: List<Int>,
    value: Int,
    fallback: (Int) -> String
): String {
    val labels = stringArrayResource(arrayId)
    val index = values.indexOf(value)
    return labels.getOrNull(index) ?: fallback(value)
}

@Composable
private fun ChannelCriteria() {
    Text(
        text = stringResource(Res.string.settings_shown_channels_private_owned),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NavigationBarSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val prefs = state.preferences
    val custom = prefs.floatingNavigationBar
    val style = prefs.navigationBarStyle
    val glass = style == com.infinity.drive.domain.model.NavigationBarStyle.FLOATING
    val glassSupported = com.infinity.drive.presentation.platform.LocalLiquidNavigation.current?.supportsGlass == true
    val backdropSupported = LocalPlatformCapabilities.current.supportsBackdropBlur
    val blurAvailable = custom && style.canBlur(glassSupported, backdropSupported)
    var chooseStyle by remember { mutableStateOf(false) }
    if (chooseStyle && custom) {
        ChoiceDialog(
            title = "Navigation bar style",
            options = com.infinity.drive.domain.model.NavigationBarStyle.entries.map { it to it.label },
            selected = style,
            onSelect = { choice ->
                chooseStyle = false
                viewModel.update { it.copy(navigationBarStyle = choice) }
            },
            onDismiss = { chooseStyle = false }
        )
    }
    SettingsGroup {
        add {
            SettingsSwitchRow(
                "Enable custom navigation", checked = custom, subtitle = "Apply your preferred navigation style",
                onChange = { value -> viewModel.update { it.copy(floatingNavigationBar = value) } }
            )
        }
        add {
            SettingsClickRow(
                "Navigation bar style", onClick = { chooseStyle = true },
                subtitle = if (custom) style.label else "Standard is active", enabled = custom
            )
        }
        add {
            SettingsSwitchRow(
                "Blur background", checked = blurAvailable && prefs.navigationBarBlur, enabled = blurAvailable,
                onChange = { value -> viewModel.update { it.copy(navigationBarBlur = value) } },
                subtitle = when {
                    !custom -> "Enable custom navigation to use blur"
                    !style.supportsBlur -> "Miuix uses a solid background"
                    glass && !glassSupported -> "Glass is unavailable on this device. Floating uses a solid background"
                    !glass && !backdropSupported -> "Backdrop blur requires Android 12 or later"
                    glass -> "Refracts the content behind the floating bar"
                    else -> "Softens content behind the navigation bar"
                }
            )
        }
        add {
            SettingsSwitchRow(
                "Show labels", checked = prefs.navigationBarLabels,
                onChange = { value -> viewModel.update { it.copy(navigationBarLabels = value) } },
                subtitle = "Show destination names alongside their icons"
            )
        }
    }
}

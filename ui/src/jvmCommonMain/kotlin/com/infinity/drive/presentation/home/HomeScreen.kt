// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.home

import app.tgdrive.ui.DriveIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.tgdrive.ui.skeleton
import org.koin.compose.koinInject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.core.telegram.TelegramConnectionState
import com.infinity.drive.domain.model.BackupSessionStatus
import com.infinity.drive.domain.model.FileSortField
import com.infinity.drive.domain.model.SortDirection
import com.infinity.drive.domain.model.StorageSlice
import com.infinity.drive.presentation.collection.CollectionType
import com.infinity.drive.presentation.common.AgeBucket
import com.infinity.drive.presentation.common.CollectSnackbarMessages
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.common.add
import com.infinity.drive.presentation.components.BottomBarSnackbarHost
import com.infinity.drive.presentation.components.ConfirmDialog
import com.infinity.drive.presentation.components.ConnectionDot
import com.infinity.drive.presentation.components.ConnectionIndicator
import com.infinity.drive.presentation.components.DriveAvatarSwitcher
import com.infinity.drive.presentation.components.FileThumbnail
import com.infinity.drive.presentation.components.GroupedList
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.PermissionWarningCard
import com.infinity.drive.presentation.components.chartColor
import com.infinity.drive.presentation.components.label
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.platform.LocalPermissionRequester
import com.infinity.drive.presentation.platform.LocalPlatformCapabilities
import com.infinity.drive.presentation.preview.PreviewSequence
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.app_backed_up
import com.infinity.drive.resources.app_name
import com.infinity.drive.resources.backup_age_days
import com.infinity.drive.resources.backup_age_hours
import com.infinity.drive.resources.backup_age_minutes
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.common_pause
import com.infinity.drive.resources.common_resume
import com.infinity.drive.resources.file_count
import com.infinity.drive.resources.home_active_transfers
import com.infinity.drive.resources.home_back_up_pending
import com.infinity.drive.resources.home_backing_up
import com.infinity.drive.resources.home_backup_all_in_telegram
import com.infinity.drive.resources.home_backup_at
import com.infinity.drive.resources.home_backup_auto_off
import com.infinity.drive.resources.home_backup_just_now
import com.infinity.drive.resources.home_backup_never
import com.infinity.drive.resources.home_backup_nothing_to_back_up
import com.infinity.drive.resources.home_backup_paused
import com.infinity.drive.resources.home_cancel_backup_action
import com.infinity.drive.resources.home_cancel_backup_title
import com.infinity.drive.resources.home_choose_folders
import com.infinity.drive.resources.home_deleted_files_kept_removal
import com.infinity.drive.resources.home_failed_count
import com.infinity.drive.resources.home_lock_app
import com.infinity.drive.resources.home_not_backed_up_count
import com.infinity.drive.resources.home_open_drives
import com.infinity.drive.resources.home_queued_uploads_discarded_files
import com.infinity.drive.resources.home_scan_now
import com.infinity.drive.resources.home_scanning
import com.infinity.drive.resources.home_section_favorites
import com.infinity.drive.resources.home_section_recent
import com.infinity.drive.resources.home_section_storage
import com.infinity.drive.resources.home_session_progress
import com.infinity.drive.resources.home_stat_backed_up
import com.infinity.drive.resources.home_stat_failed
import com.infinity.drive.resources.home_stat_files
import com.infinity.drive.resources.home_stat_pending
import com.infinity.drive.resources.home_status_connected
import com.infinity.drive.resources.home_status_connecting
import com.infinity.drive.resources.home_status_offline
import com.infinity.drive.resources.home_status_rebuilding
import com.infinity.drive.resources.home_status_syncing
import com.infinity.drive.resources.home_storage_percent
import com.infinity.drive.resources.home_storage_percent_min
import com.infinity.drive.resources.home_transfer_history
import com.infinity.drive.resources.home_transfer_history_subtitle
import com.infinity.drive.resources.home_waiting_count
import com.infinity.drive.resources.trash
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenCollection: (CollectionType) -> Unit,
    onOpenBackupSettings: () -> Unit,
    onOpenFiles:()->Unit={},
    onAddFiles:()->Unit={},
    onSearch:()->Unit={},
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uploads= koinInject<com.infinity.drive.presentation.upload.UploadCoordinator>()
    val uploadPicker=com.infinity.drive.presentation.platform.LocalMultiFilePicker.current
    val folderPicker=com.infinity.drive.presentation.platform.LocalFolderPicker.current
    val uploadSheetState=androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded=true)
    var showUploadSheet by rememberSaveable{mutableStateOf(false)}
    val sheetScope=rememberCoroutineScope()

    var searching by rememberSaveable { mutableStateOf(false) }
    val searchModel:com.infinity.drive.presentation.search.SearchViewModel=koinViewModel()
    val searchState by searchModel.uiState.collectAsStateWithLifecycle()
    com.infinity.drive.presentation.common.AppBackHandler(searching){searchModel.setQuery("");searching=false}

    var lastConnectionStatus by remember { mutableStateOf<ConnectionStatus?>(null) }
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionRequester = LocalPermissionRequester.current
    val capabilities = LocalPlatformCapabilities.current

    var confirmCancelBackup by remember { mutableStateOf<String?>(null) }
    confirmCancelBackup?.let { sessionId ->
        ConfirmDialog(
            title = stringResource(Res.string.home_cancel_backup_title),
            message = stringResource(Res.string.home_queued_uploads_discarded_files),
            confirmLabel = stringResource(Res.string.home_cancel_backup_action),
            destructive = true,
            onConfirm = {
                confirmCancelBackup = null
                viewModel.cancelBackup(sessionId)
            },
            onDismiss = { confirmCancelBackup = null }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    CollectSnackbarMessages(viewModel.messages, snackbarHostState)
    CollectSnackbarMessages(uploads.messages, snackbarHostState)
    if(showUploadSheet)androidx.compose.material3.ModalBottomSheet(onDismissRequest={showUploadSheet=false},sheetState=uploadSheetState){
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=24.dp)){
            Text("Upload",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(8.dp))
            androidx.compose.material3.ListItem(colors=androidx.compose.material3.ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent),headlineContent={Text("Upload files")},leadingContent={Icon(DriveIcons.Description,null)},modifier=Modifier.clickable{
                sheetScope.launch{uploadSheetState.hide();showUploadSheet=false;uploadPicker.pick{references->if(references.isNotEmpty())uploads.importAndUpload(references)}}
            })
            androidx.compose.material3.ListItem(colors=androidx.compose.material3.ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent),headlineContent={Text("Upload folder")},leadingContent={Icon(DriveIcons.Folder,null)},modifier=Modifier.clickable{
                sheetScope.launch{uploadSheetState.hide();showUploadSheet=false;folderPicker.pick{result->if(result is com.infinity.drive.presentation.platform.PickResult.Picked)uploads.importAndUpload(listOf(result.path))}}
            })
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { BottomBarSnackbarHost(snackbarHostState) },
        floatingActionButton={
            androidx.compose.animation.AnimatedVisibility(visible=!searching&&!state.loading,enter=fadeIn(androidx.compose.animation.core.tween(240)),exit=fadeOut(androidx.compose.animation.core.tween(160))){
                androidx.compose.material3.FloatingActionButton(onClick={showUploadSheet=true},shape=CircleShape,containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
                    modifier=Modifier.padding(bottom=LocalBottomBarInset.current)){
                    Icon(DriveIcons.CloudUpload,"Upload",Modifier.size(26.dp))
                }
            }
        },
        topBar = {
            app.tgdrive.ui.ExpandableLibraryHeader("Home",searchState.query,searchModel::setQuery,searching,{searching=it},"Search your files")
        }

    ) { padding ->
        if(searching){
            com.infinity.drive.presentation.search.SearchScreen(onBack={searching=false;searchModel.setQuery("")},onOpenFile=onOpenFile,onOpenFolder=onOpenFolder,viewModel=searchModel,showHeader=false,modifier=Modifier.padding(padding))
            return@Scaffold
        }
        if (state.loading) {
            com.infinity.drive.presentation.components.HomeSkeleton(modifier=Modifier.padding(top=padding.calculateTopPadding()),bottomPadding=padding.calculateBottomPadding()+LocalBottomBarInset.current)
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.add(
                horizontal = 16.dp,
                top = 8.dp,
                bottom = 96.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val groups=listOf(
                    state.storage.filter{it.category==com.infinity.drive.domain.model.FileCategory.IMAGE}.sumOf{it.totalBytes},
                    state.storage.filter{it.category==com.infinity.drive.domain.model.FileCategory.VIDEO}.sumOf{it.totalBytes},
                    state.storage.filter{it.category==com.infinity.drive.domain.model.FileCategory.AUDIO}.sumOf{it.totalBytes},
                    state.storage.filter{it.category !in setOf(com.infinity.drive.domain.model.FileCategory.IMAGE,com.infinity.drive.domain.model.FileCategory.VIDEO,com.infinity.drive.domain.model.FileCategory.AUDIO)}.sumOf{it.totalBytes}
                )
                app.tgdrive.ui.LibraryDashboard(app.tgdrive.ui.LibraryOverview(state.storage.sumOf{it.fileCount},groups.sum(),groups,state.localBytes),{showUploadSheet=true},showAddAction=false,shortcuts=listOf(
                    app.tgdrive.ui.LibraryShortcut("Favorites",DriveIcons.Star){onOpenCollection(CollectionType.FAVORITES)},
                    app.tgdrive.ui.LibraryShortcut("Archived",DriveIcons.Archive){onOpenCollection(CollectionType.ARCHIVED)},
                    app.tgdrive.ui.LibraryShortcut("Trash",DriveIcons.Delete,onOpenTrash)
                ))
            }
            item {
                com.infinity.drive.presentation.components.HomeQuickLinks(listOf(
                    app.tgdrive.ui.LibraryShortcut("All files",DriveIcons.Folder,onOpenFiles),
                    app.tgdrive.ui.LibraryShortcut("Activity",DriveIcons.SwapVert,onOpenTransfers),
                    app.tgdrive.ui.LibraryShortcut("Backups",DriveIcons.Backup,onOpenBackupSettings)
                ))
            }
            if (state.autoBackupEnabled && state.missingPermissions.isNotEmpty()) {
                item {
                    PermissionWarningCard(
                        missing = state.missingPermissions,
                        onGrant = {
                            permissionRequester.request(state.missingPermissions) {
                                viewModel.refreshPermissions()
                            }
                        }
                    )
                }
            }
            if (state.favoriteFolders.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(Res.string.home_section_favorites))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.favoriteFolders.take(5).forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onOpenFolder(folder.id) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    DriveIcons.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (state.showRecentSection && state.recentFiles.isNotEmpty()) {
                item("recent-header") {
                    com.infinity.drive.presentation.components.RecentSectionHeader(onOpenFiles)
                }
                items(state.recentFiles,key={"recent-${it.id}"}) { file ->
                    com.infinity.drive.presentation.components.LibraryRecentRow(file,Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(240))){
                        onOpenFile(file.id,PreviewSequence(sortField=FileSortField.DATE_ADDED,sortDirection=SortDirection.DESCENDING))
                    }
                }
            } else if(state.recentFiles.isEmpty()) {
                item("empty-library") {
                    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("A place for everything",style=MaterialTheme.typography.titleLarge)
                        Text("Upload your first files to see them here.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.TextButton({showUploadSheet=true}){Text("Upload files")}
                    }
                }
            }


        }
    }
}

@Composable
private fun CollectionLinks(
    showArchived: Boolean,
    showHidden: Boolean,
    activeTransferCount: Int,
    onOpenCollection: (CollectionType) -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenTrash: () -> Unit
) {
    GroupedList(horizontalPadding = 0.dp) {
        CollectionType.entries.forEach { collection ->
            val visible = when (collection) {
                CollectionType.ARCHIVED -> showArchived
                CollectionType.HIDDEN -> showHidden
                else -> true
            }
            add(visible = visible) {
                CollectionRow(
                    icon = collection.icon,
                    title = stringResource(collection.titleRes),
                    subtitle = stringResource(collection.subtitleRes),
                    onClick = { onOpenCollection(collection) }
                )
            }
        }
        add {
            CollectionRow(
                icon = DriveIcons.SwapVert,
                title = stringResource(Res.string.home_transfer_history),
                subtitle = if (activeTransferCount > 0) {
                    stringResource(Res.string.home_active_transfers, activeTransferCount)
                } else {
                    stringResource(Res.string.home_transfer_history_subtitle)
                },
                onClick = onOpenTransfers
            )
        }
        add {
            CollectionRow(
                icon = DriveIcons.Delete,
                title = stringResource(Res.string.trash),
                subtitle = stringResource(Res.string.home_deleted_files_kept_removal),
                onClick = onOpenTrash
            )
        }
    }
}

@Composable
private fun CollectionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupCard(
    state: HomeUiState,
    scanning: Boolean,
    onChooseFolders: () -> Unit,
    onScanNow: () -> Unit,
    onBackUpPending: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        val statusIcon = when {
            state.rebuilding -> DriveIcons.CloudSync
            state.failedCount > 0 -> DriveIcons.ErrorOutline
            state.activeBackup?.status == BackupSessionStatus.PAUSED -> DriveIcons.CloudSync
            state.pendingCount > 0 -> DriveIcons.CloudUpload
            state.offline -> DriveIcons.CloudOff
            else -> DriveIcons.CloudDone
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                        )
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = when {
                            state.activeBackup?.status == BackupSessionStatus.RUNNING ->
                                stringResource(Res.string.home_backing_up)

                            state.activeBackup?.status == BackupSessionStatus.PAUSED ->
                                stringResource(Res.string.home_backup_paused)

                            state.rebuilding -> stringResource(Res.string.home_status_rebuilding)
                            state.totalFiles == 0 ->
                                stringResource(Res.string.home_backup_nothing_to_back_up)

                            state.failedCount > 0 ->
                                stringResource(Res.string.home_failed_count, state.failedCount)

                            state.pendingCount > 0 ->
                                stringResource(Res.string.home_waiting_count, state.pendingCount)

                            state.backedUpCount < state.totalFiles -> stringResource(
                                Res.string.home_not_backed_up_count,
                                state.totalFiles - state.backedUpCount
                            )

                            else -> stringResource(Res.string.app_backed_up)
                        },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = backupFreshnessLabel(
                            autoBackupEnabled = state.autoBackupEnabled,
                            lastBackupAt = state.lastBackupAt,
                            totalFiles = state.totalFiles,
                            backedUpCount = state.backedUpCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackupStat(
                    value = state.totalFiles.toString(),
                    label = stringResource(Res.string.home_stat_files),
                    modifier = Modifier.weight(1f)
                )
                BackupStat(
                    value = state.backedUpCount.toString(),
                    label = stringResource(Res.string.home_stat_backed_up),
                    modifier = Modifier.weight(1f)
                )
                BackupStat(
                    value = state.pendingCount.toString(),
                    label = stringResource(Res.string.home_stat_pending),
                    modifier = Modifier.weight(1f)
                )
                BackupStat(
                    value = state.failedCount.toString(),
                    label = stringResource(Res.string.home_stat_failed),
                    modifier = Modifier.weight(1f)
                )
            }
            var lastSession by remember { mutableStateOf(state.activeBackup) }
            state.activeBackup?.let { lastSession = it }
            AnimatedVisibility(
                visible = state.activeBackup != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                lastSession?.let { session ->
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sessionButtonColors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            if (session.status == BackupSessionStatus.PAUSED) {
                                Button(
                                    onClick = { onResume(session.id) },
                                    shapes = ButtonDefaults.shapes(),
                                    colors = sessionButtonColors
                                ) { Text(stringResource(Res.string.common_resume)) }
                            } else {
                                Button(
                                    onClick = { onPause(session.id) },
                                    shapes = ButtonDefaults.shapes(),
                                    colors = sessionButtonColors
                                ) { Text(stringResource(Res.string.common_pause)) }
                            }
                            OutlinedButton(
                                onClick = { onCancel(session.id) },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                        .copy(alpha = 0.5f)
                                )
                            ) { Text(stringResource(Res.string.common_cancel)) }
                        }
                        Spacer(Modifier.height(12.dp))
                        HomeProgressBar(
                            progress = overallBackupProgress(state),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                Res.string.home_session_progress,
                                state.backedUpCount,
                                state.totalFiles
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = state.rebuilding && state.activeBackup == null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            AnimatedVisibility(
                visible = state.activeBackup == null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    val foldersSelected = state.backupFoldersSelected
                    val pending = state.localOnlyCount
                    Button(
                        onClick = when {
                            pending > 0 -> onBackUpPending
                            foldersSelected -> onScanNow
                            else -> onChooseFolders
                        },
                        shapes = ButtonDefaults.shapes(),
                        enabled = !scanning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            when {
                                scanning -> stringResource(Res.string.home_scanning)
                                pending > 0 -> pluralStringResource(
                                    Res.plurals.home_back_up_pending,
                                    pending,
                                    pending
                                )

                                foldersSelected -> stringResource(Res.string.home_scan_now)
                                else -> stringResource(Res.string.home_choose_folders)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun overallBackupProgress(state: HomeUiState): Float =
    if (state.totalFiles > 0) {
        (state.backedUpCount.toFloat() / state.totalFiles).coerceIn(0f, 1f)
    } else {
        0f
    }

@Composable
private fun HomeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearWavyProgressIndicator(
        progress = { progress },
        amplitude = { 1f },
        modifier = modifier
    )
}

@Composable
private fun BackupStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Connection state worth showing, or null while everything is normal. */
private data class ConnectionStatus(
    val indicator: ConnectionIndicator,
    val labelRes: StringResource
)

/**
 * Connectivity is only surfaced when it needs attention. A recovery shows
 * briefly so the change is acknowledged, then the row disappears again.
 */
@Composable
private fun rememberConnectionStatus(
    offline: Boolean,
    connection: TelegramConnectionState,
    known: Boolean
): ConnectionStatus? {
    if (!known) return null
    val settled = !offline && connection == TelegramConnectionState.READY
    var wasSettled by rememberSaveable { mutableStateOf(settled) }
    var showRecovered by remember { mutableStateOf(false) }

    LaunchedEffect(settled) {
        if (settled && !wasSettled) {
            showRecovered = true
            delay(RECOVERED_VISIBLE_MS.milliseconds)
            showRecovered = false
        }
        wasSettled = settled
    }

    return when {
        offline -> ConnectionStatus(
            ConnectionIndicator.OFFLINE,
            Res.string.home_status_offline
        )

        connection == TelegramConnectionState.UPDATING -> ConnectionStatus(
            ConnectionIndicator.WORKING,
            Res.string.home_status_syncing
        )

        connection != TelegramConnectionState.READY -> ConnectionStatus(
            ConnectionIndicator.WORKING,
            Res.string.home_status_connecting
        )

        showRecovered -> ConnectionStatus(
            ConnectionIndicator.CONNECTED,
            Res.string.home_status_connected
        )

        else -> null
    }
}


@Composable
private fun StorageCard(slices: List<StorageSlice>, modifier: Modifier = Modifier) {
    val totalBytes = remember(slices) { slices.sumOf { it.totalBytes } }
    val totalFiles = remember(slices) { slices.sumOf { it.fileCount } }
    if (totalBytes <= 0L) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = Formatters.bytes(totalBytes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(Res.plurals.file_count, totalFiles, totalFiles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            StorageBar(slices = slices, totalBytes = totalBytes)
            Spacer(Modifier.height(16.dp))
            StorageLegend(slices = slices, totalBytes = totalBytes)
        }
    }
}

/** Proportional bar. Every slice keeps a sliver so nothing vanishes entirely. */
@Composable
private fun StorageBar(slices: List<StorageSlice>, totalBytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP)
    ) {
        slices.forEach { slice ->
            val fraction = (slice.totalBytes.toFloat() / totalBytes).coerceAtLeast(MIN_SLICE)
            Box(
                modifier = Modifier
                    .weight(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(slice.category.chartColor())
            )
        }
    }
}

private val LegendTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge

@Composable
private fun StorageLegend(slices: List<StorageSlice>, totalBytes: Long) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            slices.forEach { slice ->
                Row(
                    modifier = Modifier.padding(vertical = LEGEND_ROW_GAP),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(LEGEND_DOT)
                            .clip(CircleShape)
                            .background(slice.category.chartColor())
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = slice.category.label(),
                        style = LegendTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            slices.forEach { slice ->
                val percent = slice.totalBytes * 100f / totalBytes
                Text(
                    text = if (percent < 1f) {
                        stringResource(Res.string.home_storage_percent_min)
                    } else {
                        stringResource(Res.string.home_storage_percent, percent.roundToInt())
                    },
                    style = LegendTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = LEGEND_ROW_GAP)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            slices.forEach { slice ->
                Text(
                    text = Formatters.bytes(slice.totalBytes),
                    style = LegendTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = LEGEND_ROW_GAP)
                )
            }
        }
    }
}


/**
 * The backup card answers whether the drive is current, which the storage
 * card cannot say. A stale timestamp means nothing while nothing is
 * scheduled, so a disabled schedule outranks it.
 */
@Composable
private fun backupFreshnessLabel(
    autoBackupEnabled: Boolean,
    lastBackupAt: Long?,
    totalFiles: Int,
    backedUpCount: Int
): String = when {
    !autoBackupEnabled -> stringResource(Res.string.home_backup_auto_off)
    /* Files indexed from an existing channel are already safe even though this
       device never ran a session, so claiming "no backup yet" would be wrong. */
    lastBackupAt == null && totalFiles > 0 && backedUpCount >= totalFiles ->
        stringResource(Res.string.home_backup_all_in_telegram)

    lastBackupAt == null -> stringResource(Res.string.home_backup_never)
    else -> when (val age = Formatters.relativeAge(lastBackupAt)) {
        AgeBucket.JustNow -> stringResource(Res.string.home_backup_just_now)
        is AgeBucket.Minutes -> stringResource(
            Res.string.home_backup_at,
            pluralStringResource(Res.plurals.backup_age_minutes, age.value, age.value)
        )

        is AgeBucket.Hours -> stringResource(
            Res.string.home_backup_at,
            pluralStringResource(Res.plurals.backup_age_hours, age.value, age.value)
        )

        is AgeBucket.Days -> stringResource(
            Res.string.home_backup_at,
            pluralStringResource(Res.plurals.backup_age_days, age.value, age.value)
        )

        is AgeBucket.Longer -> stringResource(
            Res.string.home_backup_at,
            Formatters.date(age.epochMillis)
        )
    }
}


private val AVATAR_SIZE = 32.dp
private const val RECOVERED_VISIBLE_MS = 2_000L
private val BAR_HEIGHT = 14.dp
private val BAR_GAP = 3.dp
private val LEGEND_DOT = 12.dp
private const val MIN_SLICE = 0.02f
private val LEGEND_ROW_GAP = 6.dp

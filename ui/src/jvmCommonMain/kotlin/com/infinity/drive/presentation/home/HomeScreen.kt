// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.home

import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import app.tgdrive.ui.DriveIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.domain.model.FileSortField
import com.infinity.drive.domain.model.SortDirection
import com.infinity.drive.presentation.collection.CollectionType
import com.infinity.drive.presentation.common.CollectSnackbarMessages
import com.infinity.drive.presentation.common.add
import com.infinity.drive.presentation.components.BottomBarSnackbarHost
import com.infinity.drive.presentation.components.PermissionWarningCard
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.platform.LocalPermissionRequester
import com.infinity.drive.presentation.platform.LocalPlatformCapabilities
import com.infinity.drive.presentation.preview.PreviewSequence
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.home_section_favorites
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
    onBrowseCategory:(Int)->Unit={},
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

    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionRequester = LocalPermissionRequester.current
    val capabilities = LocalPlatformCapabilities.current

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
    com.infinity.drive.presentation.upload.AddFilesSheet(showUploadSheet,onDismiss={showUploadSheet=false})

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { BottomBarSnackbarHost(snackbarHostState) },
        floatingActionButton={
            androidx.compose.animation.AnimatedVisibility(visible=!searching&&!state.loading,enter=fadeIn(androidx.compose.animation.core.tween(240)),exit=fadeOut(androidx.compose.animation.core.tween(160))){
                androidx.compose.material3.FloatingActionButton(onClick={showUploadSheet=true},shape=CircleShape,containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
                    modifier=Modifier.padding(bottom=LocalBottomBarInset.current)){
                    Icon(DriveIcons.DriveFolderUpload,"Upload",Modifier.size(26.dp))
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
                ),onBrowse=onOpenFiles)
                Spacer(Modifier.height(20.dp))
                app.tgdrive.ui.LibraryCategories(groups,onBrowseCategory)
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.search

import app.tgdrive.ui.elasticHorizontalScroll as horizontalScroll
import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import com.infinity.drive.presentation.components.resultReveal
import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.app_backed_up
import com.infinity.drive.resources.app_no_results
import com.infinity.drive.resources.app_not_backed_up
import com.infinity.drive.resources.app_nothing_matches_search
import com.infinity.drive.resources.app_search_drive
import com.infinity.drive.resources.app_search_files
import com.infinity.drive.resources.app_search_name_filter_type
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_clear
import com.infinity.drive.resources.search_section_files
import com.infinity.drive.resources.search_section_folders
import com.infinity.drive.domain.model.FileTags
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.infinity.drive.resources.smart_folders_title
import com.infinity.drive.resources.search_files_or_tags
import com.infinity.drive.resources.search_content_label
import com.infinity.drive.resources.search_content_notes_only
import com.infinity.drive.resources.search_content_skipped
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.presentation.components.EmptyState
import com.infinity.drive.presentation.components.FileListItem
import com.infinity.drive.presentation.components.FolderRow
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.label
import com.infinity.drive.presentation.components.liftedTopAppBarColors
import com.infinity.drive.presentation.components.rememberToolbarLift
import com.infinity.drive.presentation.preview.PreviewSequence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenFolder: (String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
    showHeader:Boolean=true,modifier:Modifier=Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val smartFolders by viewModel.smartFolders.collectAsStateWithLifecycle()
    var showSmartFolders by remember { mutableStateOf(false) }
    if (showSmartFolders) {
        SmartFoldersDialog(
            folders = smartFolders,
            canSave = state.searched && !state.searchingContents,
            open = {
                viewModel.openSmartFolder(it)
                showSmartFolders = false
            },
            remove = viewModel::removeSmartFolder,
            save = viewModel::saveSmartFolder,
            close = { showSmartFolders = false }
        )
    }

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    val keyboardVisible=androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
    Scaffold(modifier=modifier.imePadding(),contentWindowInsets=if(showHeader)androidx.compose.material3.ScaffoldDefaults.contentWindowInsets else androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            if(showHeader)app.tgdrive.ui.ExpandableLibraryHeader(
                title="Search",query=state.query,onQueryChange=viewModel::setQuery,
                expanded=true,onExpandedChange={if(!it)onBack()},placeholder=stringResource(Res.string.search_files_or_tags)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { showSmartFolders = true },
                    label = { Text(stringResource(Res.string.smart_folders_title)) }
                )
                if (viewModel.supportsContentSearch) {
                    FilterChip(
                        selected = state.searchingContents,
                        enabled = state.query.isNotBlank() && FileTags.searchTag(state.query) == null,
                        onClick = viewModel::toggleContentSearch,
                        label = { Text(stringResource(
                            if (viewModel.supportsPdfSearch) Res.string.search_content_label else Res.string.search_content_notes_only
                        )) }
                    )
                }
                FilterChip(
                    selected = state.filters.backedUpOnly,
                    onClick = { viewModel.setBackedUpOnly(!state.filters.backedUpOnly) },
                    label = { Text(stringResource(Res.string.app_backed_up)) }
                )
                FilterChip(
                    selected = state.filters.notBackedUpOnly,
                    onClick = { viewModel.setNotBackedUpOnly(!state.filters.notBackedUpOnly) },
                    label = { Text(stringResource(Res.string.app_not_backed_up)) }
                )
                FileCategory.entries.filter { it != FileCategory.OTHER }.forEach { category ->
                    FilterChip(
                        selected = state.filters.category == category,
                        onClick = {
                            viewModel.setCategory(
                                if (state.filters.category == category) null else category
                            )
                        },
                        label = { Text(category.label()) }
                    )
                }
            }

            if (state.searchingContents && state.contentSkipped > 0) {
                Text(
                    stringResource(Res.string.search_content_skipped, state.contentSkipped),
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when {
                state.searching -> LoadingState()
                state.error != null -> com.infinity.drive.presentation.components.ErrorState(state.error!!, onRetry = viewModel::retry)
                !state.searched -> EmptyState(
                    icon = DriveIcons.Search,
                    title = stringResource(Res.string.app_search_drive),
                    description = stringResource(Res.string.app_search_name_filter_type)
                )

                state.results.isEmpty() && state.folders.isEmpty() -> EmptyState(
                    icon = DriveIcons.Search,
                    title = stringResource(Res.string.app_no_results),
                    description = stringResource(Res.string.app_nothing_matches_search)
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().resultReveal(state.results.take(8).map{it.id} to state.folders.take(8).map{it.id}),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 12.dp + padding.calculateBottomPadding()+if(keyboardVisible)0.dp else com.infinity.drive.presentation.navigation.LocalBottomBarInset.current
                    )
                ) {
                    if (state.folders.isNotEmpty()) {
                        item(key = "folders_header") {
                            SearchSectionHeader(
                                title = stringResource(Res.string.search_section_folders),
                                modifier = Modifier.animateItem()
                            )
                        }
                        items(state.folders, key = { "folder_${it.id}" }) { folder ->
                            FolderRow(
                                folder = folder,
                                onClick = { onOpenFolder(folder.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                        if (state.results.isNotEmpty()) {
                            item(key = "files_header") {
                                SearchSectionHeader(
                                    title = stringResource(Res.string.search_section_files),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                    items(state.results, key = { it.id }) { file ->
                        FileListItem(
                            file = file,
                            selected = false,
                            selectionMode = false,
                            onClick = {
                                onOpenFile(
                                    file.id,
                                    PreviewSequence(
                                        nameQuery = state.query.takeIf { !state.searchingContents && it.isNotBlank() && FileTags.searchTag(it) == null },
                                        tag = FileTags.searchTag(state.query),
                                        categories = state.filters.category
                                            ?.let { category -> listOf(category) }
                                            .orEmpty(),
                                        backedUpOnly = state.filters.backedUpOnly,
                                        notBackedUpOnly = state.filters.notBackedUpOnly,
                                        minSizeBytes = state.filters.minSizeMb?.toLong()?.times(1024*1024),
                                        sortField = state.filters.sortField,
                                        sortDirection = state.filters.sortDirection
                                    )
                                )
                            },
                            onLongClick = { },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp)
    )
}

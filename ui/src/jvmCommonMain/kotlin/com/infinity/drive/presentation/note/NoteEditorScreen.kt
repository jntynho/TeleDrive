// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.note

import app.tgdrive.ui.elasticHorizontalScroll as horizontalScroll
import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.infinity.drive.presentation.components.PageHeader
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.note_body_hint
import com.infinity.drive.resources.note_bold
import com.infinity.drive.resources.note_bullet
import com.infinity.drive.resources.note_code
import com.infinity.drive.resources.note_edit
import com.infinity.drive.resources.note_heading
import com.infinity.drive.resources.note_italic
import com.infinity.drive.resources.note_link
import com.infinity.drive.resources.note_link_text
import com.infinity.drive.resources.note_link_url
import com.infinity.drive.resources.note_new
import com.infinity.drive.resources.note_quote
import com.infinity.drive.resources.note_save
import com.infinity.drive.resources.note_strikethrough
import com.infinity.drive.resources.note_title_hint
import com.infinity.drive.presentation.common.CollectSnackbarMessages
import com.infinity.drive.presentation.components.BottomBarSnackbarHost
import com.infinity.drive.presentation.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var body by remember(state.loading) { mutableStateOf(TextFieldValue(state.body)) }

    val linkText = stringResource(Res.string.note_link_text)
    val linkUrl = stringResource(Res.string.note_link_url)

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)
    var confirmDiscard by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    fun requestBack(){if(state.saving)return;if(state.dirty)confirmDiscard=true else onBack()}
    com.infinity.drive.presentation.common.AppBackHandler(state.dirty||state.saving){requestBack()}
    LaunchedEffect(viewModel,owner){owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){viewModel.saved.collect{onBack()}}}
    if(confirmDiscard)com.infinity.drive.presentation.components.ConfirmDialog(
        title="Discard changes?",message="Your changes have not been saved.",confirmLabel="Discard",destructive=true,
        onConfirm={confirmDiscard=false;onBack()},onDismiss={confirmDiscard=false})

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { BottomBarSnackbarHost(snackbarHostState) },
        topBar = {
            PageHeader(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) Res.string.note_new else Res.string.note_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestBack, enabled = !state.saving) {
                        Icon(
                            DriveIcons.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.loading && state.loadError==null) {
                val canSave = body.text.isNotBlank() && !state.saving && (state.dirty || state.isNew)
                FloatingActionButton(
                    onClick = { if (canSave) viewModel.save() },
                    modifier=Modifier.semantics{if(!canSave)disabled()},
                    containerColor = if (canSave) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (canSave) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                    }
                ) {
                    Icon(
                        DriveIcons.Check,
                        contentDescription = stringResource(Res.string.note_save)
                    )
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        if(state.loadError!=null){
            com.infinity.drive.presentation.components.ErrorState(state.loadError!!,Modifier.padding(padding),viewModel::retryLoad)
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.note_title_hint),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                enabled = !state.saving,
                colors = borderlessColors(),
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MarkdownToolbar(
                enabled = !state.saving,
                onAction = { action ->
                    body = body.apply(action, linkText, linkUrl)
                    viewModel.setBody(body.text)
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextField(
                value = body,
                onValueChange = {
                    body = it
                    viewModel.setBody(it.text)
                },
                placeholder = { Text(stringResource(Res.string.note_body_hint)) },
                enabled = !state.saving,
                colors = borderlessColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = SAVE_BUTTON_CLEARANCE)
            )
        }
    }
}

@Composable
private fun MarkdownToolbar(enabled: Boolean, onAction: (MarkdownAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ToolbarButton(DriveIcons.FormatBold, Res.string.note_bold, enabled) {
            onAction(MarkdownAction.Wrap("**"))
        }
        ToolbarButton(DriveIcons.FormatItalic, Res.string.note_italic, enabled) {
            onAction(MarkdownAction.Wrap("_"))
        }
        ToolbarButton(DriveIcons.StrikethroughS, Res.string.note_strikethrough, enabled) {
            onAction(MarkdownAction.Wrap("~~"))
        }
        ToolbarButton(DriveIcons.Code, Res.string.note_code, enabled) {
            onAction(MarkdownAction.Wrap("`"))
        }
        ToolbarButton(DriveIcons.Link, Res.string.note_link, enabled) {
            onAction(MarkdownAction.Link)
        }
        ToolbarButton(DriveIcons.Title, Res.string.note_heading, enabled) {
            onAction(MarkdownAction.LinePrefix("## "))
        }
        ToolbarButton(
            DriveIcons.FormatListBulleted,
            Res.string.note_bullet,
            enabled
        ) {
            onAction(MarkdownAction.LinePrefix("- "))
        }
        ToolbarButton(DriveIcons.FormatQuote, Res.string.note_quote, enabled) {
            onAction(MarkdownAction.LinePrefix("> "))
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    labelRes: StringResource,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun borderlessColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)

private const val DISABLED_ALPHA = 0.38f
private val SAVE_BUTTON_CLEARANCE = 88.dp

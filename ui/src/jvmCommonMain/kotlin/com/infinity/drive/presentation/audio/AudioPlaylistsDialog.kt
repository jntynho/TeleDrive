package com.infinity.drive.presentation.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.domain.model.AudioPlaylist
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.platform.AudioPlayback
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.audio_playlist_add
import com.infinity.drive.resources.audio_playlist_create
import com.infinity.drive.resources.audio_playlist_delete
import com.infinity.drive.resources.audio_playlist_empty
import com.infinity.drive.resources.audio_playlist_move_down
import com.infinity.drive.resources.audio_playlist_move_up
import com.infinity.drive.resources.audio_playlist_name
import com.infinity.drive.resources.audio_playlist_play
import com.infinity.drive.resources.audio_playlist_remove_track
import com.infinity.drive.resources.audio_playlist_save_queue
import com.infinity.drive.resources.audio_playlist_title
import com.infinity.drive.resources.audio_playlist_unavailable
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.common_save
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AudioPlaylistsDialog(
    viewModel: AudioViewModel,
    player: AudioPlayback,
    trackToAdd: DriveFile?,
    close: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    val playlist = playlists.firstOrNull { it.id == selected }
    var adding by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val playback by player.state.collectAsStateWithLifecycle()
    val tracks = if (playlist == null) emptyList() else {
        val result by remember(playlist) { viewModel.playlistTracks(playlist) }
            .collectAsStateWithLifecycle(emptyList())
        result
    }
    AlertDialog(
        onDismissRequest = { if (!creating) close() },
        title = { Text(playlist?.name ?: stringResource(
            if (trackToAdd != null) Res.string.audio_playlist_add else Res.string.audio_playlist_title
        )) },
        text = {
            if (adding) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; failed = false },
                    label = { Text(stringResource(Res.string.audio_playlist_name)) },
                    enabled = !creating,
                    isError = failed,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (failed) Text(stringResource(Res.string.audio_playlist_unavailable))
            } else if (playlist != null) {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(tracks, key = { _, file -> file.id }) { index, file ->
                        ListItem(
                            headlineContent = { Text(file.name) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(
                                        enabled = index > 0,
                                        onClick = { viewModel.movePlaylistTrack(playlist, file.id, -1) }
                                    ) { Icon(DriveIcons.ArrowUpward, stringResource(Res.string.audio_playlist_move_up)) }
                                    IconButton(
                                        enabled = index < tracks.lastIndex,
                                        onClick = { viewModel.movePlaylistTrack(playlist, file.id, 1) }
                                    ) { Icon(DriveIcons.ArrowDownward, stringResource(Res.string.audio_playlist_move_down)) }
                                    IconButton(onClick = { viewModel.removePlaylistTrack(playlist, file.id) }) {
                                        Icon(DriveIcons.Close, stringResource(Res.string.audio_playlist_remove_track))
                                    }
                                }
                            }
                        )
                    }
                    if (tracks.isEmpty()) item { Text(stringResource(Res.string.audio_playlist_empty)) }
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(playlists, key = { _, value -> value.id }) { _, value ->
                        ListItem(
                            headlineContent = { Text(value.name) },
                            trailingContent = if (trackToAdd == null) {
                                {
                                    IconButton(onClick = { viewModel.deletePlaylist(value) }) {
                                        Icon(DriveIcons.Delete, stringResource(Res.string.audio_playlist_delete))
                                    }
                                }
                            } else null,
                            modifier = Modifier.clickable {
                                if (trackToAdd == null) selected = value.id
                                else {
                                    viewModel.addToPlaylist(value, trackToAdd)
                                    close()
                                }
                            }
                        )
                    }
                    if (playlists.isEmpty()) item { Text(stringResource(Res.string.audio_playlist_empty)) }
                }
            }
        },
        confirmButton = {
            when {
                adding -> TextButton(enabled = name.trim().length in 1..60 && !creating, onClick = {
                    scope.launch {
                        creating = true
                        try {
                            val ids = trackToAdd?.let { listOf(it.id) }
                                ?: player.queuedFiles().map { it.id }.ifEmpty { listOfNotNull(playback.file?.id) }
                            if (viewModel.savePlaylist(name, ids)) close() else failed = true
                        } finally { creating = false }
                    }
                }) { Text(stringResource(Res.string.common_save)) }
                playlist != null -> TextButton(enabled = tracks.isNotEmpty(), onClick = {
                    viewModel.playPlaylist(playlist, player)
                    close()
                }) { Text(stringResource(Res.string.audio_playlist_play)) }
                trackToAdd != null || playback.file != null -> TextButton(onClick = { adding = true }) {
                    Text(stringResource(if (trackToAdd != null) Res.string.audio_playlist_create else Res.string.audio_playlist_save_queue))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !creating, onClick = {
                when {
                    adding -> { adding = false; failed = false }
                    selected != null -> selected = null
                    else -> close()
                }
            }) { Text(stringResource(if (selected == null) Res.string.common_cancel else Res.string.common_back)) }
        }
    )
}

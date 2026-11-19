package com.infinity.drive.presentation.workspaces

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.tgdrive.R
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.settings.SettingsClickRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun WorkspacesSetting() {
    var open by remember { mutableStateOf(false) }
    SettingsClickRow(stringResource(R.string.workspaces_title), { open = true },
        subtitle = stringResource(R.string.workspaces_subtitle))
    if (open) WorkspacesDialog(onClose = { open = false })
}

@Composable
internal fun WorkspacesDialog(publish: DriveFile? = null, onClose: () -> Unit) {
    val client = koinInject<AndroidWorkspaces>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<WorkspaceAccess?>(null) }
    var spaces by remember { mutableStateOf(emptyList<Workspace>()) }
    var current by remember { mutableStateOf<Workspace?>(null) }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<WorkspaceRow>()) }
    var members by remember { mutableStateOf(emptyList<WorkspaceMember>()) }
    var received by remember { mutableStateOf<WorkspaceRow?>(null) }
    var revoke by remember { mutableStateOf<WorkspaceMember?>(null) }
    var invite by remember { mutableStateOf<WorkspaceMember?>(null) }
    var memberMode by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf("") }
    var teamId by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var writer by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val messageFailure = stringResource(R.string.workspaces_error)
    val queued = stringResource(R.string.workspaces_queued)
    val published = stringResource(R.string.workspaces_published)

    fun run(operation: suspend () -> Unit) {
        if (loading) return
        loading = true
        notice = null
        scope.launch {
            try { operation() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = messageFailure }
            finally { loading = false }
        }
    }

    suspend fun showFolder(config: WorkspaceAccess, space: Workspace, folder: String) {
        val rows = client.entries(config, space.id, folder)
        entries = rows.sortedWith(compareBy<WorkspaceRow> { it.kind != "folder" }.thenBy { it.path.lowercase() })
        path = folder
    }

    LaunchedEffect(Unit) {
        run {
            client.connection()?.let { config ->
                access = config
                spaces = client.available(config)
            }
        }
    }

    val config = access
    val room = current
    AlertDialog(
        onDismissRequest = { if (!loading) onClose() },
        title = { Text(room?.name ?: stringResource(R.string.workspaces_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                notice?.let { Text(it) }
                when {
                    config == null -> {
                        Text(stringResource(R.string.workspaces_setup))
                        OutlinedTextField(origin, { origin = it }, enabled = !loading, singleLine = true,
                            label = { Text(stringResource(R.string.live_links_origin)) })
                        OutlinedTextField(teamId, { teamId = it }, enabled = !loading, singleLine = true,
                            label = { Text(stringResource(R.string.workspaces_id_optional)) })
                        OutlinedTextField(token, { token = it }, singleLine = true, enabled = !loading,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.workspaces_token)) })
                        TextButton({
                            run {
                                spaces = client.configure(WorkspaceAccess(origin, teamId.trim(), token.trim()))
                                access = client.connection()
                                token = ""
                            }
                        }, enabled = !loading && origin.isNotBlank() && token.length >= 32) {
                            Text(stringResource(R.string.live_links_connect))
                        }
                    }
                    room == null -> {
                        Text(config.origin)
                        LazyColumn(Modifier.heightIn(max = 230.dp)) {
                            items(spaces, key = { it.id }) { space ->
                                TextButton({
                                    run {
                                        val opened = client.open(config, space.id)
                                        showFolder(config, opened, "")
                                        current = opened
                                        memberMode = false
                                    }
                                }, enabled = !loading) { Text(space.name) }
                            }
                        }
                        if (config.workspace.isEmpty()) {
                            OutlinedTextField(name, { name = it.take(180) }, singleLine = true, enabled = !loading,
                                label = { Text(stringResource(R.string.workspaces_name)) })
                            TextButton({ run {
                                client.create(config, name.trim())
                                name = ""
                                spaces = client.available(config)
                            } }, enabled = !loading && name.isNotBlank()) { Text(stringResource(R.string.workspaces_create)) }
                        }
                        TextButton({ run {
                            client.disconnect()
                            access = null
                        } }, enabled = !loading) { Text(stringResource(R.string.live_links_disconnect)) }
                    }
                    memberMode -> {
                        Text(room.id)
                        TextButton({ copy(context, room.id) }) { Text(stringResource(R.string.workspaces_copy_id)) }
                        LazyColumn(Modifier.heightIn(max = 190.dp)) {
                            items(members, key = { it.id }) { user ->
                                Row {
                                    Text(user.name + " · " + stringResource(
                                        if (user.role == "editor") R.string.workspaces_editor else R.string.workspaces_viewer
                                    ), Modifier.weight(1f))
                                    TextButton({ revoke = user }, enabled = !loading) { Text(stringResource(R.string.live_links_revoke)) }
                                }
                            }
                        }
                        OutlinedTextField(name, { name = it.take(180) }, singleLine = true,
                            label = { Text(stringResource(R.string.workspaces_member_name)) })
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(!writer, { writer = false }, { Text(stringResource(R.string.workspaces_viewer)) })
                            FilterChip(writer, { writer = true }, { Text(stringResource(R.string.workspaces_editor)) })
                        }
                        TextButton({ run {
                            invite = client.invite(config, room.id, name.trim(), if (writer) "editor" else "viewer")
                            name = ""
                            members = client.members(config, room.id)
                        } }, enabled = !loading && name.isNotBlank()) { Text(stringResource(R.string.workspaces_invite)) }
                        TextButton({ memberMode = false; name = "" }, enabled = !loading) { Text(stringResource(android.R.string.cancel)) }
                    }
                    else -> {
                        Text("/$path")
                        TextButton({
                            if (path.isEmpty()) { current = null; name = "" }
                            else run { showFolder(config, room, path.substringBeforeLast('/', "")) }
                        }, enabled = !loading) { Text(stringResource(R.string.workspaces_up)) }
                        LazyColumn(Modifier.heightIn(max = 245.dp)) {
                            items(entries, key = { it.path }) { row ->
                                if (row.kind == "folder") TextButton({ run { showFolder(config, room, row.path) } }, enabled = !loading) {
                                    Text(row.name + "/")
                                } else ListItem(
                                    headlineContent = { Text(row.name) },
                                    trailingContent = {
                                        if (publish == null) TextButton({ received = row }, enabled = !loading) {
                                            Text(stringResource(R.string.upload_requests_save))
                                        }
                                    }
                                )
                            }
                        }
                        if (publish != null && room.role != "viewer") {
                            Text(stringResource(R.string.workspaces_publish_description, publish.name, config.origin))
                            TextButton({
                                run {
                                    client.publish(config, room.id, path, publish)
                                    notice = published
                                }
                            }, enabled = !loading) { Text(stringResource(R.string.workspaces_publish)) }
                        }
                        if (room.role != "viewer" && publish == null) {
                            OutlinedTextField(name, { name = it.take(180) }, singleLine = true,
                                label = { Text(stringResource(R.string.workspaces_folder_name)) }, enabled = !loading)
                            TextButton({ run {
                                client.folder(config, room.id, listOf(path, name.trim()).filter(String::isNotEmpty).joinToString("/"))
                                name = ""
                                showFolder(config, room, path)
                            } }, enabled = !loading && name.isNotBlank() && '/' !in name && '\\' !in name) {
                                Text(stringResource(R.string.workspaces_create_folder))
                            }
                        }
                        if (room.role == "owner" && publish == null) TextButton({
                            run {
                                members = client.members(config, room.id)
                                name = ""
                                memberMode = true
                            }
                        }, enabled = !loading) { Text(stringResource(R.string.workspaces_members)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose, enabled = !loading) { Text(stringResource(android.R.string.ok)) } }
    )

    received?.let { selected ->
        if (config != null && room != null) AlertDialog(
            onDismissRequest = { received = null },
            title = { Text(stringResource(R.string.upload_requests_save)) },
            text = { Text(stringResource(R.string.workspaces_receive_description, selected.name)) },
            confirmButton = { TextButton({
                received = null
                run {
                    client.receive(config, room.id, selected)
                    notice = queued
                }
            }) { Text(stringResource(R.string.upload_requests_save)) } },
            dismissButton = { TextButton({ received = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
    revoke?.let { member ->
        if (config != null && room != null) AlertDialog(
            onDismissRequest = { revoke = null }, title = { Text(stringResource(R.string.live_links_revoke)) },
            text = { Text(member.name) },
            confirmButton = { TextButton({
                revoke = null
                run { client.revoke(config, room.id, member.id); members = client.members(config, room.id) }
            }) { Text(stringResource(R.string.live_links_revoke)) } },
            dismissButton = { TextButton({ revoke = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
    invite?.let { member ->
        AlertDialog(
            onDismissRequest = { invite = null }, title = { Text(stringResource(R.string.workspaces_invite)) },
            text = { Text(stringResource(R.string.workspaces_invite_explain)) },
            confirmButton = { TextButton({
                member.token?.let { copy(context, it) }
                invite = null
            }) { Text(stringResource(R.string.workspaces_copy_token)) } }
        )
    }
}

private fun copy(context: Context, value: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("TeleDrive workspace", value))
}

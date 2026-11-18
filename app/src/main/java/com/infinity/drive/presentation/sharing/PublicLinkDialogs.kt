package com.infinity.drive.presentation.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.tgdrive.R
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.PublicLinkConnection
import com.infinity.drive.presentation.settings.SettingsClickRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.UUID

@Composable
internal fun PublicLinksSetting() {
    var show by remember { mutableStateOf(false) }
    SettingsClickRow(stringResource(R.string.live_links_title), onClick = { show = true },
        subtitle = stringResource(R.string.live_links_subtitle))
    if (show) PublicLinksDialog { show = false }
}

@Composable
private fun PublicLinksDialog(dismiss: () -> Unit) {
    val client = koinInject<AndroidPublicLinks>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connection by remember { mutableStateOf<PublicLinkConnection?>(null) }
    var links by remember { mutableStateOf<List<PublicLink>>(emptyList()) }
    var origin by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var revoke by remember { mutableStateOf<PublicLink?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        connection = client.connection()
        val current = connection
        origin = current?.origin.orEmpty()
        links = if (current == null) emptyList() else client.links()
    }

    fun run(operation: suspend () -> Unit) {
        if (pending) return
        pending = true
        failed = false
        scope.launch {
            try {
                operation()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed = true
            } finally {
                pending = false
            }
        }
    }

    LaunchedEffect(Unit) { run { reload() } }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.live_links_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pending) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.live_links_failure))
                val current = connection
                if (current == null) {
                    Text(stringResource(R.string.live_links_configure))
                    OutlinedTextField(origin, { origin = it }, singleLine = true,
                        enabled = !pending, label = { Text(stringResource(R.string.live_links_origin)) })
                    OutlinedTextField(token, { token = it }, singleLine = true, enabled = !pending,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.live_links_owner_token)) })
                    Button(
                        onClick = { run { client.configure(origin, token); token = ""; reload() } },
                        enabled = !pending && origin.isNotBlank() && token.length >= 32
                    ) { Text(stringResource(R.string.live_links_connect)) }
                } else {
                    Text(current.origin)
                    if (links.isEmpty() && !pending && !failed) Text(stringResource(R.string.live_links_empty))
                    links.forEach { link ->
                        Column {
                            Text(link.name)
                            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(link.expiresAt)))
                            Row {
                                TextButton(onClick = {
                                    copyLink(context, client.linkUrl(current, link.id))
                                    copied = link.id
                                }) { Text(stringResource(if (copied == link.id) R.string.live_links_copied else R.string.live_links_copy)) }
                                TextButton(onClick = { revoke = link }, enabled = !pending) {
                                    Text(stringResource(R.string.live_links_revoke))
                                }
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { run { reload() } }, enabled = !pending) {
                            Text(stringResource(R.string.live_links_refresh))
                        }
                        TextButton(onClick = { run { client.disconnect(); reload() } }, enabled = !pending) {
                            Text(stringResource(R.string.live_links_disconnect))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.ok)) } }
    )

    revoke?.let { link ->
        AlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text(stringResource(R.string.live_links_revoke)) },
            text = { Text(stringResource(R.string.live_links_revoke_message, link.name)) },
            confirmButton = {
                TextButton(onClick = {
                    revoke = null
                    run { client.revoke(link.id); reload() }
                }) { Text(stringResource(R.string.live_links_revoke)) }
            },
            dismissButton = { TextButton({ revoke = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
internal fun CreatePublicLinkDialog(
    file: DriveFile,
    connection: PublicLinkConnection,
    dismiss: () -> Unit
) {
    val client = koinInject<AndroidPublicLinks>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hours by remember { mutableStateOf("24") }
    var password by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    val attempt = remember(file.id, hours, password) { UUID.randomUUID().toString() }
    val seconds = hours.toIntOrNull()?.takeIf { it in 1..168 }?.times(3600)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.live_links_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (url != null) {
                    Text(url.orEmpty())
                } else {
                    Text(stringResource(R.string.live_links_copy_notice, connection.origin))
                    OutlinedTextField(
                        value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.live_links_duration)) },
                        singleLine = true, enabled = !pending,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { if (it.toByteArray().size <= 256) password = it },
                        label = { Text(stringResource(R.string.live_links_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, enabled = !pending
                    )
                }
                if (pending) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.live_links_failure))
            }
        },
        confirmButton = {
            val value = url
            if (value != null) {
                TextButton(onClick = { copyLink(context, value); dismiss() }) { Text(stringResource(R.string.live_links_copy)) }
            } else {
                TextButton(enabled = seconds != null && !pending, onClick = {
                    val duration = seconds ?: return@TextButton
                    pending = true
                    failed = false
                    scope.launch {
                        try {
                            url = client.create(file, duration, password, attempt)
                            password = ""
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            pending = false
                        }
                    }
                }) { Text(stringResource(R.string.live_links_create)) }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

private fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("TeleDrive", url))
}

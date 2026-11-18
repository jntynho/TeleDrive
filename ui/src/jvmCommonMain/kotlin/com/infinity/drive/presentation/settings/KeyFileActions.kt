package com.infinity.drive.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.platform.LocalPortableKeyFiles
import com.infinity.drive.presentation.platform.LocalRecoveryDeviceOwnerGate
import com.infinity.drive.presentation.platform.PortableKeyFileResult
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.key_backup_verify
import com.infinity.drive.resources.key_backup_verify_hint
import com.infinity.drive.resources.key_file_device_prompt
import com.infinity.drive.resources.key_file_export
import com.infinity.drive.resources.key_file_export_hint
import com.infinity.drive.resources.key_file_import
import com.infinity.drive.resources.key_file_import_hint
import com.infinity.drive.resources.key_file_io_failed
import com.infinity.drive.resources.key_file_password
import com.infinity.drive.resources.key_file_password_hint
import com.infinity.drive.resources.key_file_password_repeat
import com.infinity.drive.resources.key_file_saved
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

internal data class KeyFileActions(
    val verifyTitle: String,
    val verifySubtitle: String,
    val verify: () -> Unit,
    val exportTitle: String,
    val exportSubtitle: String,
    val export: (() -> Unit)?,
    val importTitle: String,
    val importSubtitle: String,
    val importKey: (() -> Unit)?
)

private enum class KeyFileMode { VERIFY, EXPORT, IMPORT }

@Composable
internal fun rememberKeyFileActions(viewModel: SettingsViewModel, working: Boolean): KeyFileActions {
    val portableFiles = LocalPortableKeyFiles.current
    val gate = LocalRecoveryDeviceOwnerGate.current
    val owner = LocalLifecycleOwner.current
    val verifyTitle = stringResource(Res.string.key_backup_verify)
    val verifySubtitle = stringResource(Res.string.key_backup_verify_hint)
    val exportTitle = stringResource(Res.string.key_file_export)
    val exportSubtitle = stringResource(Res.string.key_file_export_hint)
    val importTitle = stringResource(Res.string.key_file_import)
    val importSubtitle = stringResource(Res.string.key_file_import_hint)
    val devicePrompt = stringResource(Res.string.key_file_device_prompt)
    var mode by remember { mutableStateOf<KeyFileMode?>(null) }
    var confirmed by remember { mutableStateOf<KeyFileMode?>(null) }
    var document by remember { mutableStateOf<ByteArray?>(null) }
    var waiting by remember { mutableStateOf(false) }

    LaunchedEffect(confirmed, owner) {
        val selected = confirmed ?: return@LaunchedEffect
        owner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        confirmed = null
        if (selected == KeyFileMode.IMPORT && portableFiles != null) {
            portableFiles.open { result ->
                waiting = false
                when (result) {
                    is PortableKeyFileResult.Success -> {
                        document = result.value
                        mode = selected
                    }
                    PortableKeyFileResult.Cancelled -> Unit
                    PortableKeyFileResult.Failed -> viewModel.notify(UiText.Resource(Res.string.key_file_io_failed))
                }
            }
        } else {
            waiting = false
            mode = selected
        }
    }

    val selected = mode
    if (selected != null) {
        val (title, subtitle) = when (selected) {
            KeyFileMode.VERIFY -> verifyTitle to verifySubtitle
            KeyFileMode.EXPORT -> exportTitle to exportSubtitle
            KeyFileMode.IMPORT -> importTitle to importSubtitle
        }
        KeyFilePasswordDialog(
            title = title,
            subtitle = subtitle,
            confirmPassword = selected == KeyFileMode.EXPORT,
            onDismiss = {
                document = null
                mode = null
            },
            onSubmit = { password ->
                val blob = document
                mode = null
                document = null
                when (selected) {
                    KeyFileMode.VERIFY -> viewModel.verifyKeyBackup(password)
                    KeyFileMode.IMPORT -> if (blob != null) viewModel.importPortableKey(blob, password)
                    KeyFileMode.EXPORT -> viewModel.exportPortableKey(password) { bytes ->
                        waiting = true
                        portableFiles?.save(bytes) { result ->
                            waiting = false
                            when (result) {
                                is PortableKeyFileResult.Success -> viewModel.notify(UiText.Resource(Res.string.key_file_saved))
                                PortableKeyFileResult.Cancelled -> Unit
                                PortableKeyFileResult.Failed -> viewModel.notify(UiText.Resource(Res.string.key_file_io_failed))
                            }
                        }
                    }
                }
            }
        )
    }

    fun authenticate(title: String, next: KeyFileMode) {
        if (waiting || working) return
        waiting = true
        gate.require(title, devicePrompt, onDenied = { message ->
            waiting = false
            message?.let { viewModel.notify(UiText.Plain(it)) }
        }, onConfirmed = { confirmed = next })
    }

    return KeyFileActions(
        verifyTitle, verifySubtitle, { authenticate(verifyTitle, KeyFileMode.VERIFY) },
        exportTitle, exportSubtitle, portableFiles?.let { { authenticate(exportTitle, KeyFileMode.EXPORT) } },
        importTitle, importSubtitle, portableFiles?.let { { authenticate(importTitle, KeyFileMode.IMPORT) } }
    )
}

@Composable
private fun KeyFilePasswordDialog(
    title: String,
    subtitle: String,
    confirmPassword: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle)
                if (confirmPassword) Text(stringResource(Res.string.key_file_password_hint))
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 512) password = it },
                    label = { Text(stringResource(Res.string.key_file_password)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmPassword) OutlinedTextField(
                    value = repeated,
                    onValueChange = { if (it.length <= 512) repeated = it },
                    label = { Text(stringResource(Res.string.key_file_password_repeat)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = repeated.isNotEmpty() && repeated != password,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = if (confirmPassword) password.length >= 12 && password == repeated else password.isNotEmpty()
            ) { Text(title) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

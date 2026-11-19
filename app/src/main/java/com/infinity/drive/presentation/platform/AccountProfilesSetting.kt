package com.infinity.drive.presentation.platform

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.tgdrive.R
import com.infinity.drive.core.accounts.DeviceAccounts
import com.infinity.drive.presentation.settings.SettingsClickRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AccountProfilesSetting(compact: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: return
    val accounts = remember(context) { DeviceAccounts(context) }
    var profiles by remember { mutableStateOf(accounts.profiles()) }
    if (compact && profiles.size <= 1) return
    var open by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var switching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.device_accounts_title)
    val primaryLabel = stringResource(R.string.device_accounts_primary)
    if (compact) TextButton({ open = true }, modifier = Modifier.fillMaxWidth()) { Text(title) }
    else SettingsClickRow(title, onClick = { open = true },
        subtitle = profiles.firstOrNull { it.id == DeviceAccounts.activeId }?.label?.ifBlank { primaryLabel })

    fun choose(id: String) {
        switching = true
        error = false
        scope.launch {
            try {
                accounts.activate(activity, id)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = true
                switching = false
            }
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!switching) open = false },
            title = { Text(title) },
            text = {
                Column {
                    Text(stringResource(R.string.device_accounts_description))
                    if (error) Text(stringResource(R.string.device_accounts_error))
                    profiles.forEach { profile ->
                        val entry = profile.label.ifBlank { primaryLabel }
                        val selected = profile.id == DeviceAccounts.activeId
                        TextButton({ choose(profile.id) }, enabled = !switching && !selected) {
                            Text(if (selected) stringResource(R.string.device_accounts_current, entry) else entry)
                        }
                    }
                    if (profiles.size < 5 && !compact) TextButton({ add = true }, enabled = !switching) {
                        Text(stringResource(R.string.device_accounts_add))
                    }
                }
            },
            confirmButton = { TextButton({ open = false }, enabled = !switching) { Text(stringResource(android.R.string.ok)) } }
        )
    }
    if (add) {
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text(stringResource(R.string.device_accounts_add)) },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true,
                label = { Text(stringResource(R.string.device_accounts_name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    add = false
                    try {
                        val added = accounts.create(name.trim())
                        profiles = accounts.profiles()
                        name = ""
                        choose(added.id)
                    } catch (_: Exception) { error = true }
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.device_accounts_add)) }
            },
            dismissButton = { TextButton({ add = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

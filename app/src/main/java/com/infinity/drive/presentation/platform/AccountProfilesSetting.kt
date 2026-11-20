package com.infinity.drive.presentation.platform

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tgdrive.R
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.core.accounts.DeviceAccounts
import com.infinity.drive.presentation.components.DriveBottomSheet
import com.infinity.drive.presentation.components.DriveSheetPage
import com.infinity.drive.presentation.components.SheetActionRow
import com.infinity.drive.presentation.components.TelegramProfileAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountProfilesSetting(
    compact: Boolean,
    profileName: String? = null,
    username: String? = null,
    photoPath: String? = null
) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: return
    val accounts = remember(context) { DeviceAccounts(context) }
    var profiles by remember(accounts) { mutableStateOf(accounts.profiles()) }
    if (compact && profiles.size <= 1) return
    var open by rememberSaveable { mutableStateOf(false) }
    var add by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var switching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.device_accounts_title)
    val primaryLabel = stringResource(R.string.device_accounts_primary)
    if (compact) {
        TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(title) }
    } else {
        IconButton(onClick = { open = true }) {
            Icon(DriveIcons.AccountSwitcher, contentDescription = stringResource(R.string.device_accounts_switch))
        }
    }

    fun choose(id: String) {
        if (switching) return
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
        val activeId = DeviceAccounts.activeId
        val activeName = profileName?.takeIf(String::isNotBlank)
        val handle = username?.takeIf(String::isNotBlank)?.let { "@$it" }
        val activeDescription = if (handle == null) stringResource(R.string.device_accounts_active)
        else stringResource(R.string.device_accounts_current, handle)
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { target -> target != SheetValue.Hidden || !switching }
        )
        DriveBottomSheet(
            onDismissRequest = {
                if (!switching) {
                    open = false
                    add = false
                    name = ""
                    error = false
                }
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DriveSheetPage(add) { adding ->
                    if (adding) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val addLabel = stringResource(R.string.device_accounts_add)
                            Text(addLabel, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                name, { name = it.take(40) }, Modifier.fillMaxWidth(),
                                singleLine = true, enabled = !switching,
                                label = { Text(stringResource(R.string.device_accounts_name)) }
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                TextButton(enabled = !switching, onClick = {
                                    add = false
                                    name = ""
                                    error = false
                                }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                                Button(onClick = {
                                    error = false
                                    try {
                                        val added = accounts.create(name.trim())
                                        profiles = accounts.profiles()
                                        name = ""
                                        add = false
                                        choose(added.id)
                                    } catch (_: Exception) {
                                        error = true
                                    }
                                }, enabled = name.isNotBlank() && !switching) { Text(addLabel) }
                            }
                        }
                    } else Column(Modifier.fillMaxWidth()) {
                        Text(
                            title, Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Column(Modifier.selectableGroup()) {
                            profiles.sortedBy { it.id != activeId }.forEach { profile ->
                                val selected = profile.id == activeId
                                val savedLabel = profile.label.ifBlank { primaryLabel }
                                val displayName = if (selected) activeName ?: savedLabel else savedLabel
                                ListItem(
                                    headlineContent = { Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = if (selected) {
                                        { Text(activeDescription, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    } else null,
                                    leadingContent = { TelegramProfileAvatar(photoPath.takeIf { selected }, Modifier.size(40.dp)) },
                                    trailingContent = if (selected) {
                                        { Icon(DriveIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    modifier = Modifier.selectable(
                                        selected = selected,
                                        enabled = !switching && !selected,
                                        role = Role.RadioButton,
                                        onClick = { choose(profile.id) }
                                    )
                                )
                            }
                        }
                        if (profiles.size < 5 && !compact) {
                            SheetActionRow(stringResource(R.string.device_accounts_add), DriveIcons.PersonAdd, enabled = !switching) {
                                add = true
                            }
                        }
                        Text(
                            stringResource(R.string.device_accounts_description), Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (switching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                if (error) {
                    Text(stringResource(R.string.device_accounts_error), Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

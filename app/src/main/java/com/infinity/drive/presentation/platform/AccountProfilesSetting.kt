package com.infinity.drive.presentation.platform

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
    photoPath: String? = null,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: return
    val accounts = remember(context) { DeviceAccounts(context) }
    var profiles by remember(accounts) { mutableStateOf(accounts.profiles()) }
    if (compact && profiles.size <= 1) return
    var open by rememberSaveable { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.device_accounts_title)
    val activeId = DeviceAccounts.activeId

    fun show() {
        profiles = accounts.profiles()
        error = false
        open = true
    }

    if (compact) {
        TextButton(onClick = ::show, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(title) }
    } else {
        IconButton(onClick = ::show, enabled = enabled) {
            Icon(DriveIcons.AccountSwitcher, contentDescription = stringResource(R.string.device_accounts_switch))
        }
    }

    fun switchAccount(action: suspend () -> Unit) {
        if (switching || !enabled) return
        switching = true
        error = false
        scope.launch {
            try {
                action()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                profiles = accounts.profiles()
                error = true
                switching = false
            }
        }
    }

    if (!open) return
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { target -> target != SheetValue.Hidden || !switching }
    )
    DriveBottomSheet(onDismissRequest = { if (!switching) open = false }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title, Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge
            )
            Column(Modifier.selectableGroup()) {
                profiles.sortedBy { it.id != activeId }.forEach { profile ->
                    val selected = profile.id == activeId
                    val handle = (username?.takeIf { selected && it.isNotBlank() } ?: profile.username)?.let { "@$it" }
                    val name = profileName?.takeIf { selected && it.isNotBlank() }
                        ?: profile.label.takeIf(String::isNotBlank)
                        ?: handle ?: stringResource(if (profile.id == DeviceAccounts.PRIMARY) {
                            R.string.device_accounts_primary
                        } else R.string.device_accounts_pending)
                    val detail = when {
                        selected && handle != null && handle != name -> stringResource(R.string.device_accounts_current, handle)
                        selected -> stringResource(R.string.device_accounts_active)
                        else -> handle?.takeUnless { it == name }
                    }
                    ListItem(
                        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = detail?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        leadingContent = { TelegramProfileAvatar(photoPath.takeIf { selected }, Modifier.size(40.dp)) },
                        trailingContent = if (selected) {
                            { Icon(DriveIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        modifier = Modifier.selectable(
                            selected = selected, enabled = enabled && !switching && !selected, role = Role.RadioButton,
                            onClick = { switchAccount { accounts.activate(activity, profile.id) } }
                        )
                    )
                }
            }
            if (!compact && profiles.size < DeviceAccounts.MAX_PROFILES) {
                SheetActionRow(
                    stringResource(R.string.device_accounts_add), DriveIcons.PersonAdd, enabled = enabled && !switching,
                    detail = stringResource(R.string.device_accounts_add_detail)
                ) { switchAccount { accounts.addAndActivate(activity) } }
            }
            if (switching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            if (error) {
                Text(stringResource(R.string.device_accounts_error), Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

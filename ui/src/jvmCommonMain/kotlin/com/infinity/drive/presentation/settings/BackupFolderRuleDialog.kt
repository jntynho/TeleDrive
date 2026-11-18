package com.infinity.drive.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.infinity.drive.domain.model.BackupFolderRule
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.backup_rule_maximum
import com.infinity.drive.resources.backup_rule_maximum_help
import com.infinity.drive.resources.backup_rule_media_only
import com.infinity.drive.resources.backup_rule_nested
import com.infinity.drive.resources.backup_rule_title
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.common_save
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BackupFolderRuleDialog(
    initial: BackupFolderRule,
    onSave: (BackupFolderRule) -> Unit,
    onDismiss: () -> Unit
) {
    var subfolders by remember(initial) { mutableStateOf(initial.includeSubfolders) }
    var mediaOnly by remember(initial) { mutableStateOf(initial.mediaOnly) }
    var maximum by remember(initial) { mutableStateOf(initial.maximumSizeMb?.toString().orEmpty()) }
    val parsed = maximum.toIntOrNull()
    val valid = maximum.isBlank() || parsed != null && parsed >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.backup_rule_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(initial.path)
                RuleToggle(stringResource(Res.string.backup_rule_nested), subfolders) { subfolders = it }
                RuleToggle(stringResource(Res.string.backup_rule_media_only), mediaOnly) { mediaOnly = it }
                OutlinedTextField(
                    value = maximum,
                    onValueChange = { maximum = it },
                    label = { Text(stringResource(Res.string.backup_rule_maximum)) },
                    supportingText = { Text(stringResource(Res.string.backup_rule_maximum_help)) },
                    isError = !valid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(includeSubfolders = subfolders, mediaOnly = mediaOnly, maximumSizeMb = parsed))
            }) { Text(stringResource(Res.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

@Composable
private fun RuleToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = change),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

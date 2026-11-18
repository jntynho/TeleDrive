package com.infinity.drive.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.domain.model.SmartFolder
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.common_save
import com.infinity.drive.resources.smart_folder_create
import com.infinity.drive.resources.smart_folder_empty
import com.infinity.drive.resources.smart_folder_name
import com.infinity.drive.resources.smart_folder_remove
import com.infinity.drive.resources.smart_folder_same_name
import com.infinity.drive.resources.smart_folders_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SmartFoldersDialog(
    folders: List<SmartFolder>,
    canSave: Boolean,
    open: (SmartFolder) -> Unit,
    remove: (SmartFolder) -> Unit,
    save: suspend (String) -> Boolean,
    close: () -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) close() },
        title = { Text(stringResource(if (adding) Res.string.smart_folder_create else Res.string.smart_folders_title)) },
        text = {
            if (adding) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = false },
                        label = { Text(stringResource(Res.string.smart_folder_name)) },
                        singleLine = true,
                        isError = error,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error) Text(stringResource(Res.string.smart_folder_same_name))
                }
            } else if (folders.isEmpty()) {
                Text(stringResource(Res.string.smart_folder_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            supportingContent = folder.query.takeIf { it.isNotBlank() }?.let { query ->
                                { Text(query) }
                            },
                            trailingContent = {
                                IconButton(onClick = { remove(folder) }) {
                                    Icon(DriveIcons.Delete, stringResource(Res.string.smart_folder_remove, folder.name))
                                }
                            },
                            modifier = Modifier.clickable { open(folder) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (adding) {
                TextButton(enabled = name.isNotBlank() && name.trim().length <= 60 && !saving, onClick = {
                    scope.launch {
                        saving = true
                        try {
                            if (save(name)) {
                                name = ""
                                adding = false
                            } else error = true
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(stringResource(Res.string.common_save)) }
            } else if (canSave) {
                TextButton(onClick = { adding = true }) { Text(stringResource(Res.string.smart_folder_create)) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (adding) { adding = false; error = false } else close()
            }, enabled = !saving) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

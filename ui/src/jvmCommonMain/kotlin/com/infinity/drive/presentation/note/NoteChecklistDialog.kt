package com.infinity.drive.presentation.note

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_close
import com.infinity.drive.resources.note_tasks_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NoteChecklistDialog(tasks: List<NoteExtras.Task>, toggle: (NoteExtras.Task) -> Unit, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(Res.string.note_tasks_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(tasks, key = { it.line }) { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = task.checked, role = Role.Checkbox, onValueChange = { toggle(task) }
                        ).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = task.checked, onCheckedChange = null)
                        Text(task.name, Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = close) { Text(stringResource(Res.string.common_close)) }
        }
    )
}

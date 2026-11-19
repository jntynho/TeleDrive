package com.infinity.drive.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.data.repository.TelegramFileVersions
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.platform.LocalMultiFilePicker
import com.infinity.drive.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@Composable
internal fun FileVersionsDialog(file: DriveFile, onClose: () -> Unit) {
    val versions = koinInject<TelegramFileVersions>()
    val picker = LocalMultiFilePicker.current
    val scope = rememberCoroutineScope()
    var rows by remember(file.id) { mutableStateOf<List<TelegramFileVersions.Revision>?>(null) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }
    var confirm by remember(file.id) { mutableStateOf<TelegramFileVersions.Revision?>(null) }
    var saving by remember(file.id) { mutableStateOf(false) }
    var retry by remember(file.id) { mutableIntStateOf(0) }
    val failure = stringResource(Res.string.versions_error)
    LaunchedEffect(file.id, retry) {
        error = null
        try {
            rows = versions.list(file.id)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            error = failure
        }
    }

    fun change(work: suspend () -> Unit) {
        saving = true
        error = null
        scope.launch {
            try {
                work()
                onClose()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = failure
            } finally {
                saving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(stringResource(Res.string.versions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name)
                if (saving || rows == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let {
                    Text(it)
                    TextButton({ retry++ }, enabled = !saving) { Text(stringResource(Res.string.chat_import_retry)) }
                }
                if (rows?.isEmpty() == true) Text(stringResource(Res.string.versions_empty))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(rows.orEmpty(), key = { it.id }) { entry ->
                        val date = DateFormat.getDateTimeInstance().format(Date(entry.modifiedAt))
                        ListItem(
                            headlineContent = { Text(date) },
                            supportingContent = { Text(Formatters.bytes(entry.size)) },
                            trailingContent = {
                                TextButton({ confirm = entry }, enabled = !saving) {
                                    Text(stringResource(Res.string.versions_restore))
                                }
                            }
                        )
                    }
                }
                TextButton(
                    onClick = { picker.pick { selected -> selected.singleOrNull()?.let { change { versions.replace(file.id, it) } } } },
                    enabled = !saving
                ) { Text(stringResource(Res.string.versions_replace)) }
            }
        },
        confirmButton = { TextButton(onClose, enabled = !saving) { Text(stringResource(Res.string.versions_done)) } }
    )
    confirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(Res.string.versions_restore)) },
            text = { Text(stringResource(Res.string.versions_restore_message)) },
            confirmButton = {
                TextButton({
                    confirm = null
                    change { versions.restore(file.id, entry.id) }
                }) { Text(stringResource(Res.string.versions_restore)) }
            },
            dismissButton = { TextButton({ confirm = null }) { Text(stringResource(Res.string.common_cancel)) } }
        )
    }
}

package com.infinity.drive.presentation.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.domain.model.DuplicateFile
import com.infinity.drive.domain.model.DuplicateGroup
import com.infinity.drive.presentation.common.Formatters
import com.infinity.drive.presentation.components.DriveBottomSheet
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.files_duplicates_action
import com.infinity.drive.resources.files_duplicates_drive_root
import com.infinity.drive.resources.files_duplicates_empty
import com.infinity.drive.resources.files_duplicates_group
import com.infinity.drive.resources.files_duplicates_method
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuplicatesSheet(
    groups: List<DuplicateGroup>?,
    onDismiss: () -> Unit,
    onOpen: (DuplicateFile) -> Unit
) {
    DriveBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(Res.string.files_duplicates_action),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Text(
            stringResource(Res.string.files_duplicates_method),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        when {
            groups == null -> LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
            )
            groups.isEmpty() -> Text(
                stringResource(Res.string.files_duplicates_empty),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 500.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                for (group in groups) {
                    item(key = "group-${group.identity}-${group.sizeBytes}") {
                        Text(
                            stringResource(
                                Res.string.files_duplicates_group,
                                group.copies.size,
                                Formatters.bytes(group.sizeBytes)
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    items(group.copies, key = { "file-${it.id}" }) { copy ->
                        ListItem(
                            headlineContent = {
                                Text(copy.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(copy.folderName ?: stringResource(Res.string.files_duplicates_drive_root))
                            },
                            modifier = Modifier.clickable { onOpen(copy) }
                        )
                    }
                }
            }
        }
    }
}

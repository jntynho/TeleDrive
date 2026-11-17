// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.components

import app.tgdrive.ui.LibraryLayout
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.skeleton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_content_description_favorite
import com.infinity.drive.resources.common_content_description_selected
import com.infinity.drive.resources.common_file_size_and_date
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.common.Formatters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: DriveFile,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    var menu by remember(file.id) { mutableStateOf(false) }
    if(menu)FileActionsSheet(file,onDismiss={menu=false},onOpen=onClick)
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    Column(modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .combinedClickable(enabled = !loading, onClick = onClick, onLongClick = onLongClick)
            .heightIn(min=LibraryLayout.HomeCover+LibraryLayout.HomeRowPadding*2)
            .padding(vertical = LibraryLayout.HomeRowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FileThumbnail(
                file = file,
                modifier = Modifier
                    .size(LibraryLayout.HomeCover)
                    .clip(RoundedCornerShape(12.dp)).skeleton(loading)
            )
            if (selected) {
                Icon(
                    imageVector = DriveIcons.CheckCircle,
                    contentDescription = stringResource(Res.string.common_content_description_selected),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(LibraryLayout.HomeRowGap))
        Column(modifier = Modifier.weight(1f),verticalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if(selected)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).skeleton(loading)
                )
                if (file.isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = DriveIcons.StarFilled,
                        contentDescription = stringResource(Res.string.common_content_description_favorite),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
                Text(
                    text = stringResource(
                        Res.string.common_file_size_and_date,
                        Formatters.bytes(file.sizeBytes),
                        Formatters.date(file.modifiedAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.skeleton(loading)
                )
        }
        Spacer(Modifier.width(8.dp))
        if(selectionMode)BackupStateBadge(file = file, modifier = Modifier.skeleton(loading))
        else IconButton(onClick={menu=true},enabled=!loading) {Icon(DriveIcons.MoreVert,"Options for ${file.name}",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    HorizontalDivider(Modifier.padding(start=LibraryLayout.HomeCover+LibraryLayout.HomeRowGap),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
    }
}

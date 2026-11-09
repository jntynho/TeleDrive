// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_content_description_favorite
import com.infinity.drive.domain.model.DriveFolder

@Composable
fun FolderRow(
    folder: DriveFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    Column(modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min=if(LocalCompactLayout.current)56.dp else 72.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if(LocalCompactLayout.current)38.dp else 48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center) {
        Icon(
            imageVector = DriveIcons.Folder,
            modifier = Modifier.size(28.dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        }
        Spacer(Modifier.width(if(LocalCompactLayout.current)10.dp else 14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = folder.contentsLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (folder.isFavorite) {
            Icon(
                imageVector = DriveIcons.Star,
                contentDescription = stringResource(Res.string.common_content_description_favorite),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        androidx.compose.material3.IconButton(onClick){Icon(DriveIcons.ChevronRight,"Open ${folder.name}",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    HorizontalDivider(Modifier.padding(start=if(LocalCompactLayout.current)52.dp else 66.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
    }
}
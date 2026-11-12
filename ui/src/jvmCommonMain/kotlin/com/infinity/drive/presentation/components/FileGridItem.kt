// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_content_description_selected
import com.infinity.drive.domain.model.BackupState
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.common.Formatters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: DriveFile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    galleryTile:Boolean=false,
    selectionMode:Boolean=selected,
    enabled:Boolean=true
) {
    val checkAlpha by animateFloatAsState(if(selected)1f else 0f,tween(180),label="selectionBadge")
    val selectionScale by animateFloatAsState(if(selected).94f else 1f,tween(200),label="selectionScale")
    Column(
        modifier = modifier
            .clip(if(galleryTile)androidx.compose.ui.graphics.RectangleShape else MaterialTheme.shapes.large)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .semantics { this.selected=selected;if(selectionMode)toggleableState=if(selected)androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }
            .combinedClickable(enabled=enabled,role=if(selectionMode)Role.Checkbox else Role.Button,onClick = onClick, onLongClick = onLongClick)
    ) {
        Box {
            FileThumbnail(
                file = file,
                galleryTile=galleryTile,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if(galleryTile)app.tgdrive.ui.LibraryLayout.GalleryAspect else 1f).graphicsLayer{scaleX=selectionScale;scaleY=selectionScale}
            )
            if (selected || checkAlpha>0f) {
                Icon(
                    imageVector = DriveIcons.CheckCircle,
                    contentDescription = stringResource(Res.string.common_content_description_selected),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp).graphicsLayer{alpha=checkAlpha},
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (!galleryTile && file.backupState != BackupState.BACKED_UP) {
                BackupStateBadge(
                    file = file,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .alpha(BADGE_ALPHA)
                )
            }
        }
        if (!compact) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = Formatters.bytes(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val BADGE_ALPHA = 0.6f

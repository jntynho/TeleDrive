package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The same continuous panel, 52dp cover and inset divider used by Activity. */
@Composable
fun ActivityLibraryRow(
    title: String, subtitle: String, first: Boolean, last: Boolean,
    selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    leading: @Composable () -> Unit, modifier: Modifier = Modifier,
    indent: Int = 0, actions: @Composable RowScope.() -> Unit = {}
) {
    val top = if (first) 24.dp else 0.dp
    val bottom = if (last) 24.dp else 0.dp
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().semantics { this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp + (indent.coerceIn(0, 3) * 12).dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center) {
                leading()
                if (selected) Icon(DriveIcons.CheckCircle, "Selected", Modifier.size(20.dp).align(Alignment.BottomEnd),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 78.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
    }
}

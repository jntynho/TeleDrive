package com.infinity.drive.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One modal surface; each purpose keeps its own scrolling and selection semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surfaceContainerLow
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = surface,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = .48f),
        contentWindowInsets = contentWindowInsets,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 36.dp, height = 4.dp,
                color = colors.onSurfaceVariant.copy(alpha = .35f)
            )
        }
    ) {
        val scope = this
        // Default ListItems must blend into the modal rather than painting rectangular patches.
        MaterialTheme(colorScheme = colors.copy(surface = surface, surfaceContainer = surface,
            surfaceContainerLow = surface, surfaceContainerHigh = surface)) {
            content(scope)
        }
    }
}

@Composable
fun SheetActionRow(label: String, icon: ImageVector, enabled: Boolean = true, detail:String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if(enabled)1f else .38f)
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
            if(detail!=null)Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.platform.LocalPlatformCapabilities
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_refresh
import org.jetbrains.compose.resources.stringResource

/** Stands in for the pull gesture on platforms that do not have one. */
@Composable
fun RefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    if (LocalPlatformCapabilities.current.supportsPullToRefresh) return
    IconButton(onClick = onRefresh, enabled = !refreshing) {
        if (refreshing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                DriveIcons.Refresh,
                contentDescription = stringResource(Res.string.common_refresh)
            )
        }
    }
}

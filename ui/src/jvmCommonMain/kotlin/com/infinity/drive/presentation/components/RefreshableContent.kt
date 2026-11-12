package com.infinity.drive.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.infinity.drive.presentation.platform.LocalPlatformCapabilities

/**
 * Pull to refresh where the platform has touch, a plain container elsewhere:
 * the drag gesture fights mouse wheel scrolling on desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (LocalPlatformCapabilities.current.supportsPullToRefresh) {
        val state=rememberPullToRefreshState()
        Box(modifier.pullToRefresh(isRefreshing=isRefreshing,state=state,enabled=enabled,onRefresh=onRefresh)) {
            androidx.compose.runtime.CompositionLocalProvider(app.tgdrive.ui.LocalElasticTopReserved provides enabled) { content() }
            PullToRefreshDefaults.Indicator(state=state,isRefreshing=isRefreshing,modifier=Modifier.align(Alignment.TopCenter))
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

package com.infinity.drive.presentation.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Only actual finger scrolling can change visibility; header layout changes cannot feed back. */
class FilterScrollThreshold(private val threshold: Float, initiallyCollapsed: Boolean = false) {
    var collapsed = initiallyCollapsed
        private set
    private var travel = 0f
    fun consume(deltaY: Float, atStart: Boolean): Boolean {
        if (atStart && deltaY > 0) { collapsed = false; travel = 0f; return collapsed }
        if (deltaY == 0f) return collapsed
        if (travel * deltaY < 0) travel = 0f
        travel += deltaY
        if (travel <= -threshold) { collapsed = true; travel = 0f }
        else if (travel >= threshold) { collapsed = false; travel = 0f }
        return collapsed
    }
}

data class CollapsingFilters(val visible: Boolean, val scrollModifier: Modifier)

@Composable
fun rememberCollapsingFilters(resetKey: Any?, enabled: Boolean = true, canScrollBackward: () -> Boolean): CollapsingFilters {
    var collapsed by rememberSaveable(resetKey) { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    val tracker = remember(resetKey, threshold) { FilterScrollThreshold(threshold, collapsed) }
    val canGoBack by rememberUpdatedState(canScrollBackward)
    val active by rememberUpdatedState(enabled)
    val navigating by rememberUpdatedState(app.tgdrive.ui.LocalNavigationInProgress.current)
    val connection = remember(tracker) { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && !navigating && active) {
                val atStart = !canGoBack()
                val delta = if (atStart && available.y > 0) available.y else consumed.y
                collapsed = tracker.consume(delta, atStart)
            }
            return Offset.Zero
        }
    } }
    return CollapsingFilters(!collapsed, Modifier.nestedScroll(connection))
}

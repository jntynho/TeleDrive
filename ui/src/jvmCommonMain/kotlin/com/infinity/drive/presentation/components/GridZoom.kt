package com.infinity.drive.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import com.infinity.drive.domain.model.ViewMode
import kotlin.math.*

const val MIN_GRID_COLUMNS = 2
const val MAX_GRID_COLUMNS = 6

data class GridZoomLevel(val viewMode: ViewMode, val gridSize: Int)
fun GridZoomLevel.zoomedIn(): GridZoomLevel = if (viewMode == ViewMode.LIST) GridZoomLevel(ViewMode.GRID, MAX_GRID_COLUMNS)
    else copy(gridSize = (gridSize - 1).coerceAtLeast(MIN_GRID_COLUMNS))
fun GridZoomLevel.zoomedOut(): GridZoomLevel = when {
    viewMode == ViewMode.LIST -> this
    gridSize >= MAX_GRID_COLUMNS -> GridZoomLevel(ViewMode.LIST, gridSize)
    else -> copy(gridSize = gridSize + 1)
}

/** Hysteresis keeps a deliberate step selected when the fingers relax just before release. */
class GridPinchSession(private val columns: Int? = null) {
    var zoom = 1f; private set
    var steps = 0; private set
    private fun threshold(direction: Int): Float {
        val n = columns ?: return 1.22f
        val target = (n - direction).coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
        return if (target == n) Float.POSITIVE_INFINITY else sqrt(max(n.toFloat() / target, target.toFloat() / n))
    }
    fun add(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        zoom = (zoom * factor).coerceIn(.25f, 4f)
        if (steps > 0 && zoom < 1f + (threshold(1) - 1f) * .55f) steps = 0
        if (steps < 0 && zoom > 1f / (1f + (threshold(-1) - 1f) * .55f)) steps = 0
        if (zoom >= threshold(1)) steps = 1
        else if (zoom <= 1f / threshold(-1)) steps = -1
    }
}

/** Recognition only: cell layout owns drawing, hit targets and settling. Never scales the viewport. */
fun Modifier.pinchZoom(
    onZoomIn: () -> Unit, onZoomOut: () -> Unit,
    onGestureStarted: (Offset) -> Unit = {}, onGestureFinished: (Boolean) -> Unit = {},
    densityKey: Any? = null, columns: Int? = null, enabled: Boolean = true,
    onGestureProgress: (Float, Offset) -> Unit = { _, _ -> },
    canZoomIn: Boolean = true, canZoomOut: Boolean = true,
    commitAllowed:()->Boolean={true}
): Modifier = composed {
    val inward by rememberUpdatedState(onZoomIn)
    val outward by rememberUpdatedState(onZoomOut)
    val started by rememberUpdatedState(onGestureStarted)
    val finished by rememberUpdatedState(onGestureFinished)
    val progress by rememberUpdatedState(onGestureProgress)
    val allowIn by rememberUpdatedState(canZoomIn)
    val allowOut by rememberUpdatedState(canZoomOut)
    val initialColumns by rememberUpdatedState(columns)
    val mayCommit by rememberUpdatedState(commitAllowed)
    // The caller's context, rather than the committed column count, cancels obsolete gestures.
    this.pointerInput(enabled, densityKey) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            var session = GridPinchSession(initialColumns)
            var firstSpan = 0f
            var origin = Offset.Zero
            var claimed = false
            var ended = false
            fun finish(commit: Boolean) {
                if (ended || !claimed) return
                ended = true
                val step = session.steps
                val changed = commit && mayCommit() && ((step > 0 && allowIn) || (step < 0 && allowOut))
                if (changed) { if (step > 0) inward() else outward() }
                finished(changed)
            }
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val count = event.changes.count { it.pressed }
                    if (!ended && count == 2) {
                        // Include newly pressed pointers when establishing the span. Compose's
                        // transform helpers intentionally exclude them from their first sample.
                        val pointers=event.changes.filter{it.pressed}
                        val center=(pointers[0].position+pointers[1].position)/2f
                        val span=(pointers[0].position-pointers[1].position).getDistance()/2f
                        if (firstSpan == 0f && span > 0f) { firstSpan = span; origin = center }
                        session.add(event.calculateZoom())
                        if (!claimed && firstSpan > 0 && abs(span - firstSpan) >= viewConfiguration.touchSlop * .25f && abs(ln(session.zoom)) >= .045f) {
                            claimed = true
                            started(origin)
                        }
                        if (claimed) {
                            if (center.x.isFinite() && center.y.isFinite()) progress(session.zoom, center)
                            event.changes.forEach { it.consume() }
                        }
                    } else if (count != 2) {
                        // Commit as soon as the pair ends. A remaining finger is not held hostage.
                        if (claimed && !ended) { event.changes.forEach { it.consume() }; finish(count<2) }
                        if(!claimed)session=GridPinchSession(initialColumns)
                        firstSpan = 0f
                    }
                } while (event.changes.any { it.pressed })
            } finally { finish(false) }
        }
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll || !event.keyboardModifiers.isCtrlPressed) continue
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta < 0 && allowIn) inward() else if (delta > 0 && allowOut) outward()
                event.changes.forEach { it.consume() }
            }
        }
    }
}

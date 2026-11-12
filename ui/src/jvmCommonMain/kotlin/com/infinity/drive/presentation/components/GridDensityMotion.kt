package com.infinity.drive.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.*

data class DensityItem(val key: String, val header: Boolean = false)
data class DensityAnchor(val key: String, val fraction: Float, val viewportY: Float)

fun anchoredScrollOffset(anchor: DensityAnchor, newHeight: Float, beforePadding: Float = 0f): Int =
    (beforePadding + anchor.fraction * newHeight - anchor.viewportY).roundToInt()

/** One live grid: still during the gesture, fade out, commit/anchor, then fade in. */
@Stable
class GridDensityMotion internal constructor(private val scope: CoroutineScope, private val grid: LazyGridState) {
    var busy by mutableStateOf(false); private set
    var dragging by mutableStateOf(false); private set
    var transitioning by mutableStateOf(false); private set
    var opacity by mutableFloatStateOf(1f); private set
    val acceptsInput by derivedStateOf { opacity > .05f }
    private var job: Job? = null
    private var generation = 0L
    private var startColumns = 3
    private var anchor: DensityAnchor? = null
    private var items = emptyList<DensityItem>()
    private var mediaKeys = emptySet<String>()
    private var width = 0f
    private var gap = 0f

    fun begin(columns: Int, content: List<DensityItem>, point: Offset, spacing: Float = 0f, inset: Float = 0f) {
        if (transitioning) return
        generation++; job?.cancel()
        val info = grid.layoutInfo
        mediaKeys=content.filterNot{it.header}.mapTo(HashSet()){it.key}
        val selected = info.visibleItemsInfo.filter { it.key.toString() in mediaKeys }
            .minByOrNull { row ->
                val dx = row.offset.x + row.size.width / 2f - point.x
                val dy = row.offset.y + row.size.height / 2f - point.y
                dx * dx + dy * dy
            } ?: run { reset(); return }
        startColumns = columns
        items = content
        width = (info.viewportSize.width - inset * 2).coerceAtLeast(1f)
        gap = spacing
        anchor = DensityAnchor(selected.key.toString(),
            ((point.y - selected.offset.y) / selected.size.height).coerceIn(0f, 1f), point.y)
        busy = true; dragging = true
        scope.launch { grid.stopScroll() }
    }

    /** Delay the actual column change until the old layout is fully transparent. */
    fun release(step: Int, columns: () -> Int, applyDensity: () -> Unit) {
        if (!dragging) return
        val wanted = (startColumns - step).coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
        if (wanted == startColumns || columns() != startColumns) { cancel(); return }
        dragging = false; transitioning = true
        val ticket = ++generation
        job?.cancel()
        job = scope.launch {
            try {
                val reducedMotion = scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
                if (!reducedMotion) {
                    Animatable(opacity).animateTo(0f, tween(65, easing = LinearEasing)) { opacity = value }
                }
                applyDensity()
                val focus = anchor
                val ready = withTimeoutOrNull(700) {
                    snapshotFlow { columns() }.first { it == wanted }
                    val expectedWidth = (width - gap * (wanted - 1)) / wanted
                    // Scroll offsets belong to the NEW rows. Never normalize them against the old columns.
                    val measured = snapshotFlow { grid.layoutInfo }.first { layout ->
                        layout.visibleItemsInfo.any { row ->
                            row.key.toString() in mediaKeys && abs(row.size.width - expectedWidth) < 2f
                        }
                    }
                    if (focus != null) {
                        val index = items.indexOfFirst { it.key == focus.key }
                        val cell = measured.visibleItemsInfo.first { row ->
                            row.key.toString() in mediaKeys && abs(row.size.width - expectedWidth) < 2f
                        }
                        if (index >= 0) grid.scrollToItem(index,
                            anchoredScrollOffset(focus, cell.size.height.toFloat(), measured.beforeContentPadding.toFloat()))
                        snapshotFlow { grid.layoutInfo }.first { layout ->
                            layout.visibleItemsInfo.any { it.key.toString() == focus.key && abs(it.size.width - expectedWidth) < 2f }
                        }
                    }
                }
                if (ready == null) return@launch
                if (!reducedMotion) {
                    Animatable(0f).animateTo(1f, tween(135, easing = FastOutSlowInEasing)) { opacity = value }
                }
            } finally { if (ticket == generation) reset() }
        }
    }

    /** Alpha only; no captured screen, per-cell transforms, extra photos or header resizing. */
    fun fade(): Modifier = Modifier.graphicsLayer { alpha = opacity }
    fun cancel() { generation++; job?.cancel(); reset() }
    private fun reset() {
        busy = false; dragging = false; transitioning = false; opacity = 1f
        anchor = null; items = emptyList(); mediaKeys = emptySet()
    }
}

@Composable
fun rememberGridDensityMotion(state: LazyGridState, context: Any?): GridDensityMotion {
    val scope = rememberCoroutineScope()
    val motion = remember(state, context) { GridDensityMotion(scope,state) }
    LaunchedEffect(motion,state){
        snapshotFlow{state.layoutInfo.viewportSize}.distinctUntilChanged().drop(1).collect{if(motion.busy)motion.cancel()}
    }
    val navigating=app.tgdrive.ui.LocalNavigationInProgress.current
    LaunchedEffect(motion,navigating){if(navigating)motion.cancel()}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(motion,owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP)motion.cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);motion.cancel() }
    }
    return motion
}

fun Modifier.densityAccessibility(columns: Int, onIn: () -> Unit, onOut: () -> Unit): Modifier = semantics {
    customActions = buildList {
        if (columns > MIN_GRID_COLUMNS) add(CustomAccessibilityAction("Larger thumbnails") { onIn(); true })
        if (columns < MAX_GRID_COLUMNS) add(CustomAccessibilityAction("Smaller thumbnails") { onOut(); true })
    }
}

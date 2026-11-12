package com.infinity.drive.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
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

/** Header rows have their own fixed height; only media cells change size. */
data class DensityItem(val key: String, val header: Boolean = false)
data class DensityAnchor(val key: String, val fraction: Float, val viewportY: Float)

/** Pure, bounded geometry: no image loading, re-composition or database work during finger movement. */
fun densityGeometry(items: List<DensityItem>, columns: Int, width: Float, gap: Float,
    aspect: Float, headerHeight: Float, extraCellHeight: Float = 0f): Map<String, Rect> {
    if (width <= 0 || columns !in MIN_GRID_COLUMNS..MAX_GRID_COLUMNS) return emptyMap()
    val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(1f)
    val cellHeight = cellWidth / aspect + extraCellHeight
    var y = 0f; var column = 0
    return buildMap {
        items.forEach { item ->
            if (item.header) {
                if (column != 0) { y += cellHeight + gap; column = 0 }
                put(item.key, Rect(0f, y, width, y + headerHeight)); y += headerHeight + gap
            } else {
                val x = column * (cellWidth + gap)
                put(item.key, Rect(x, y, x + cellWidth, y + cellHeight))
                column++
                if (column == columns) { column = 0; y += cellHeight + gap }
            }
        }
    }
}
private fun blend(a: Rect, b: Rect, fraction: Float) = Rect(
    a.left + (b.left - a.left) * fraction, a.top + (b.top - a.top) * fraction,
    a.right + (b.right - a.right) * fraction, a.bottom + (b.bottom - a.bottom) * fraction)
fun anchoredScrollOffset(anchor: DensityAnchor, newHeight: Float, beforePadding:Float=0f): Int =
    (beforePadding + anchor.fraction * newHeight - anchor.viewportY).roundToInt()

/** One controller per gallery context. The live cells are also the live, transformed hit targets. */
@Stable
class GridDensityMotion internal constructor(private val scope: CoroutineScope, private val grid: LazyGridState) {
    var busy by mutableStateOf(false); private set
    var dragging by mutableStateOf(false); private set
    private var starts by mutableStateOf<Map<String, Rect>>(emptyMap())
    private var destinations by mutableStateOf<Map<String, Rect>>(emptyMap())
    private var amount by mutableFloatStateOf(1f)
    private var job: Job? = null
    private var generation=0L
    private var startColumns = 3
    private var targetColumns = 3
    private var anchor: DensityAnchor? = null
    private var baseRects = emptyMap<String, Rect>()
    private var items = emptyList<DensityItem>()
    private var width = 0f; private var gap = 0f; private var aspect = .8f
    private var headerHeight = 0f; private var extraHeight = 0f; private var left = 0f

    fun begin(state: LazyGridState, columns: Int, content: List<DensityItem>, point: Offset, spacing: Float, ratio: Float, inset:Float=0f, defaultHeaderHeight:Float=42f) {
        generation++;job?.cancel()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        val cells = visible.filter { row -> content.getOrNull(row.index)?.header == false }
        val selected = cells.minByOrNull { row ->
            val dx = row.offset.x + row.size.width / 2f - point.x
            val dy = row.offset.y + row.size.height / 2f - point.y
            dx * dx + dy * dy
        } ?: run { reset();return }
        val existing = visible.associate { row ->
            val actual = Rect(row.offset.x.toFloat(), row.offset.y.toFloat(), (row.offset.x + row.size.width).toFloat(), (row.offset.y + row.size.height).toFloat())
            row.key.toString() to displayed(row.key.toString(), actual)
        }
        startColumns = columns; targetColumns = columns; items = content
        left = inset
        width = (info.viewportSize.width - inset * 2).coerceAtLeast(1f)
        gap = spacing; aspect = ratio
        headerHeight = visible.firstOrNull { row -> content.getOrNull(row.index)?.header == true }?.size?.height?.toFloat() ?: defaultHeaderHeight
        extraHeight = (selected.size.height - selected.size.width / ratio).coerceAtLeast(0f)
        val key = selected.key.toString()
        val rect = existing.getValue(key)
        anchor = DensityAnchor(key, ((point.y - rect.top) / rect.height).coerceIn(0f, 1f), point.y)
        val oldGeometry=densityGeometry(content,columns,width,gap,aspect,headerHeight,extraHeight)
        val oldAnchor=oldGeometry[key]
        val shift=if(oldAnchor!=null)rect.top-oldAnchor.top else 0f
        baseRects=oldGeometry.mapValues{(_,r)->r.translate(Offset(left,shift))}+existing
        starts=baseRects;destinations=baseRects;amount=0f
        busy = true; dragging = true
        scope.launch { state.stopScroll() }
    }

    fun preview(scale: Float, point: Offset) {
        if (!dragging || !scale.isFinite()) return
        val desired = (startColumns + if (scale < 1f) 1 else -1).coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
        if (desired == startColumns) { destinations = baseRects; amount = 0f; return }
        if (desired != targetColumns) {
            targetColumns = desired
            val geometry = densityGeometry(items, desired, width, gap, aspect, headerHeight, extraHeight)
            val focus = anchor ?: return
            val target = geometry[focus.key] ?: return
            val dy = focus.viewportY - target.top - focus.fraction * target.height
            destinations = geometry.mapValues { (_, r) -> r.translate(Offset(left, dy)) }
        }
        val oldWidth = (width - gap * (startColumns - 1)) / startColumns
        val newWidth = (width - gap * (desired - 1)) / desired
        amount = ((scale - 1f) / (newWidth / oldWidth - 1f)).coerceIn(0f, 1f)
    }

    /** Called after the synchronous UI column update. No fixed delay or captured old screen. */
    fun finish(state: LazyGridState, committed: Boolean, columns: () -> Int) {
        if (!dragging) return
        if(!committed&&amount<.001f){cancel();return}
        dragging = false
        val from = baseRects.mapValues { (key, rect) -> displayed(key, rect) }
        val ticket=++generation
        job?.cancel()
        job = scope.launch {
            try {
                val focus = anchor
                if (committed && focus != null && targetColumns != startColumns) {
                    val wanted = targetColumns
                    // Await layout readiness; a timeout rolls back visuals instead of trapping input.
                    val ready = withTimeoutOrNull(700) {
                        snapshotFlow { columns() }.first { it == wanted }
                        val expectedWidth=(width-gap*(wanted-1))/wanted
                        // Measure the NEW columns before scrolling. Applying the offset to the old
                        // row layout normalizes it against the wrong row and moves the focal image.
                        snapshotFlow { state.layoutInfo }.first { layout ->
                            layout.visibleItemsInfo.any{row->items.getOrNull(row.index)?.header==false&&abs(row.size.width-expectedWidth)<2f}
                        }
                        val index=items.indexOfFirst{it.key==focus.key}
                        if(index>=0){
                            val cellHeight=expectedWidth/aspect+extraHeight
                            state.scrollToItem(index,anchoredScrollOffset(focus,cellHeight,state.layoutInfo.beforeContentPadding.toFloat()))
                        }
                        snapshotFlow{state.layoutInfo}.first{layout->layout.visibleItemsInfo.any{it.key.toString()==focus.key&&abs(it.size.width-expectedWidth)<2f}}

                    }
                    if (ready == null) { reset(); return@launch }
                }
                starts = from
                val finalGeometry=densityGeometry(items,columns(),width,gap,aspect,headerHeight,extraHeight)
                val measured=state.layoutInfo.visibleItemsInfo.associate { row -> row.key.toString() to Rect(row.offset.x.toFloat(),row.offset.y.toFloat(),(row.offset.x+row.size.width).toFloat(),(row.offset.y+row.size.height).toFloat()) }
                val reference=anchor?.key
                val finalShift=if(reference!=null&&measured[reference]!=null&&finalGeometry[reference]!=null)
                    measured.getValue(reference).top-finalGeometry.getValue(reference).top else 0f
                destinations=finalGeometry.mapValues{(_,r)->r.translate(Offset(left,finalShift))}+measured
                amount = 0f
                Animatable(0f).animateTo(1f, tween(280, easing = FastOutSlowInEasing)) { amount = value }
            } finally { if(ticket==generation)reset() }
        }
    }

    private fun displayed(key: String, actual: Rect): Rect {
        val start = starts[key] ?: return actual
        return blend(start, destinations[key] ?: actual, amount)
    }
    /** Additional cells entering the viewport during the live gesture; never duplicate visible keys. */
    fun enteringCells():List<Pair<String,Rect>> {
        if(!busy||destinations.isEmpty())return emptyList()
        val visible=grid.layoutInfo.visibleItemsInfo.map{it.key.toString()}.toSet()
        val height=grid.layoutInfo.viewportSize.height.toFloat()
        return starts.mapNotNull{(key,r)->
            val shown=displayed(key,r)
            if(key in visible||shown.bottom<=0||shown.top>=height)null else key to shown
        }
    }

    fun cell(key: String, header: Boolean = false): Modifier = Modifier.graphicsLayer {
        translationX=0f;translationY=0f;scaleX=1f;scaleY=1f
        if (!busy) return@graphicsLayer
        // Actual coordinates are supplied by the grid after measurement, never by a stale screen capture.
        val row = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key.toString() == key } ?: return@graphicsLayer
        val rect = Rect(row.offset.x.toFloat(),row.offset.y.toFloat(),(row.offset.x+row.size.width).toFloat(),(row.offset.y+row.size.height).toFloat())
        val shown = displayed(key, rect)
        transformOrigin = TransformOrigin(0f, 0f)
        translationX = shown.left - rect.left; translationY = shown.top - rect.top
        scaleX = if(header)1f else shown.width / size.width.coerceAtLeast(1f)
        scaleY = if(header)1f else shown.height / size.height.coerceAtLeast(1f)
    }
    fun cancel() { generation++;job?.cancel(); reset() }
    private fun reset() { busy = false; dragging = false; starts = emptyMap(); destinations = emptyMap(); baseRects = emptyMap(); anchor = null; amount = 1f }
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

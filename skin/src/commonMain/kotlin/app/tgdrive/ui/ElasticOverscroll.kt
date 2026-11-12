package app.tgdrive.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*

/** The refresh/sheet host owns an outward pull at its top edge. */
val LocalElasticTopReserved = staticCompositionLocalOf { false }

fun edgeApproach(distance: Float, zone: Float): Float {
    if (!distance.isFinite() || zone <= 0f) return 0f
    val t = (1f - distance.coerceAtLeast(0f) / zone).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun elasticDisplacement(raw: Float, viewport: Float, maxPixels: Float): Float {
    val limit = min(viewport * .22f, maxPixels).coerceAtLeast(1f)
    val damped = raw * .65f
    return damped / (1f + abs(damped) / limit)
}

/** Only movement towards an edge starts anticipation. Held pulls are handled separately. */
fun elasticEdgeProximity(start: Float, end: Float, zone: Float, direction: Float): Float = when {
    direction > .5f -> edgeApproach(start, zone)
    direction < -.5f -> -edgeApproach(end, zone)
    else -> 0f
}

/** Resistance stores visible pixels, so reversing never has to repay an invisible raw drag. */
fun advanceElasticPull(position: Float, delta: Float, limit: Float): Float {
    if (!position.isFinite() || !delta.isFinite() || !limit.isFinite() || limit <= 0f) return 0f
    if (abs(delta) < .5f) return position.coerceIn(-limit, limit)
    if (position != 0f && sign(position) != sign(delta)) {
        return if (position > 0f) (position + delta).coerceAtLeast(0f) else (position + delta).coerceAtMost(0f)
    }
    val current = abs(position).coerceAtMost(limit)
    return sign(delta) * (limit - (limit - current) * exp(-abs(delta) * .65f / limit))
}

private enum class ElasticPhase { REST, PRESS, DRAG, FLING, RETURN }

/** Render-only observer. It neither consumes nested scroll nor participates in native drag capture. */
private class ElasticMotion(private val scope: CoroutineScope) {
    var position by mutableStateOf(Offset.Zero); private set
    private var size = IntSize(1, 1)
    private var density = 1f
    private var enabled = true
    private var touching = false
    private var multitouch = false
    private var phase = ElasticPhase.REST
    private var returning: Job? = null
    private var release: Job? = null
    private var flingWatch: Job? = null
    private var flingSample = 0L
    private val animation = Animatable(Offset.Zero, Offset.VectorConverter)
    private var resumeVelocity = Offset.Zero
    private fun limit(axis: Int) = min(axis * .22f, 96f * density).coerceAtLeast(1f)

    fun resize(next: IntSize, scale: Float) {
        if (next == size && scale == density) return
        val oldX = limit(size.width); val oldY = limit(size.height)
        val hadSize = size.width > 1 && size.height > 1
        size = next; density = scale
        if (hadSize && position != Offset.Zero) {
            val x = limit(size.width) / oldX; val y = limit(size.height) / oldY
            stopReturn()
            position = Offset(position.x * x, position.y * y)
            resumeVelocity = Offset(resumeVelocity.x * x, resumeVelocity.y * y)
            if (!touching) settle()
        }
    }
    fun enable(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) { release?.cancel(); settle() }
    }
    private fun stopReturn() {
        resumeVelocity = if (returning?.isActive == true) animation.velocity else Offset.Zero
        returning?.cancel(); returning = null
    }
    fun down() {
        release?.cancel(); flingWatch?.cancel(); stopReturn(); touching = true; multitouch = false
        phase = ElasticPhase.PRESS
    }
    fun multiplePointers() {
        if (multitouch) return
        multitouch = true; release?.cancel(); settle()
    }
    fun up() {
        touching = false; multitouch = false
        // Native onPreFling gets ownership before a finger release starts a return.
        release?.cancel()
        release = scope.launch {
            withFrameNanos { }; withFrameNanos { }
            if (phase != ElasticPhase.FLING) settle()
        }
    }
    fun cancelGesture() { flingWatch?.cancel(); phase = ElasticPhase.PRESS; up() }
    fun preFling() {
        release?.cancel(); stopReturn(); phase = ElasticPhase.FLING
        flingWatch?.cancel()
        // Native cancellation can omit onPostFling. No scroll frames means visual ownership expires.
        flingWatch = scope.launch {
            while (phase == ElasticPhase.FLING) {
                val sample = flingSample
                repeat(3) { withFrameNanos { } }
                if (sample == flingSample && !touching) { settle(); break }
            }
        }
    }
    fun postFling() { flingWatch?.cancel(); if (!touching) settle() }
    fun reset() {
        release?.cancel(); flingWatch?.cancel(); returning?.cancel(); returning = null
        position = Offset.Zero; resumeVelocity = Offset.Zero
        touching = false; multitouch = false; phase = ElasticPhase.REST
    }
    fun move(consumed: Offset, available: Offset, source: NestedScrollSource, near: Offset, topReserved: Boolean) {
        if (!enabled || multitouch) return
        val fling = phase == ElasticPhase.FLING
        if (!touching && !fling) return // keyboard, accessibility and programmatic scrolling are unchanged
        if (source != NestedScrollSource.UserInput && !fling) return
        if (fling) flingSample++
        if (!fling && phase != ElasticPhase.DRAG) { stopReturn(); phase = ElasticPhase.DRAG }
        val delta = consumed + available
        fun axis(current: Float, movement: Float, unused: Float, proximity: Float, bound: Float, reserve: Boolean): Float {
            if (reserve && movement > 0f) return current.coerceAtMost(0f)
            var next = current
            if (current != 0f && movement * current < 0f) next = advanceElasticPull(current, movement, bound)
            if (abs(unused) >= .5f && (next == 0f || next * unused > 0f)) {
                next = advanceElasticPull(next, unused * if (fling) .3f else 1f, bound)
            } else if (!fling && abs(proximity) > 0f && abs(next) < 6f * density) {
                next = proximity * 6f * density
            }
            return next.coerceIn(-bound, bound)
        }
        position = Offset(axis(position.x, delta.x, available.x, near.x, limit(size.width), false),
            axis(position.y, delta.y, available.y, near.y, limit(size.height), topReserved))
    }
    fun settle() {
        if (returning?.isActive == true) return
        if (position.getDistance() < .05f) { position = Offset.Zero; phase = ElasticPhase.REST; return }
        phase = ElasticPhase.RETURN
        val start = position; val velocity = resumeVelocity
        returning = scope.launch {
            animation.snapTo(start)
            animation.animateTo(Offset.Zero, spring(dampingRatio = 1f, stiffness = 190f), initialVelocity = velocity) {
                position = value
            }
            position = Offset.Zero; resumeVelocity = Offset.Zero
            phase = ElasticPhase.REST
        }
    }
}

internal fun Modifier.elasticMotion(enabled: Boolean, horizontal: Boolean, identity: Any, proximity: (Offset) -> Offset): Modifier = composed {
    val scope = rememberCoroutineScope()
    val motion = remember(identity) { ElasticMotion(scope) }
    val near by rememberUpdatedState(proximity)
    val topReserved by rememberUpdatedState(LocalElasticTopReserved.current)
    val density = LocalDensity.current.density
    val navigating = LocalNavigationInProgress.current
    val owner = LocalLifecycleOwner.current
    SideEffect { motion.enable(enabled && !navigating) }
    DisposableEffect(owner, motion) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) motion.reset() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); motion.reset() }
    }
    val connection = remember(motion, horizontal) { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val delta = consumed + available
            val axisConsumed = if (horizontal) Offset(consumed.x, 0f) else Offset(0f, consumed.y)
            val axisAvailable = if (horizontal) Offset(available.x, 0f) else Offset(0f, available.y)
            motion.move(axisConsumed, axisAvailable, source, near(delta), !horizontal && topReserved)
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity { motion.preFling(); return Velocity.Zero }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity { motion.postFling(); return Velocity.Zero }
    } }
    this.nestedScroll(connection).onSizeChanged { motion.resize(it, density) }
        .then(ElasticPointerElement(motion)).graphicsLayer {
            val p = motion.position
            translationX = p.x; translationY = p.y
            scaleX = 1f + (abs(p.x) / size.width.coerceAtLeast(1f) * .42f).coerceAtMost(.055f)
            scaleY = 1f + (abs(p.y) / size.height.coerceAtLeast(1f) * .42f).coerceAtMost(.055f)
            transformOrigin = TransformOrigin(if (p.x >= 0f) 0f else 1f, if (p.y >= 0f) 0f else 1f)
            clip = true
        }
}

/** Observes DOWN/UP/CANCEL without consuming events or moving the input coordinate system. */
private data class ElasticPointerElement(val motion: ElasticMotion) : ModifierNodeElement<ElasticPointerNode>() {
    override fun create() = ElasticPointerNode(motion)
    override fun update(node: ElasticPointerNode) { node.motion = motion }
    override fun InspectorInfo.inspectableProperties() { name = "elasticPointerObserver" }
}
private class ElasticPointerNode(var motion: ElasticMotion) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        if (pointerEvent.changes.any { it.pressed && !it.previousPressed } && pointerEvent.changes.none { it.previousPressed }) motion.down()
        if (pointerEvent.changes.count { it.pressed } > 1) motion.multiplePointers()
        if (pointerEvent.changes.none { it.pressed } && pointerEvent.changes.any { it.previousPressed }) motion.up()
    }
    override fun onCancelPointerInput() { if (isAttached) motion.cancelGesture() }
    override fun onDetach() { motion.reset() }
}

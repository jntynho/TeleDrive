// Adapted from only_player d2b5dd9; provenance and licenses: third_party/only_player/SOURCE.md.
package com.infinity.drive.presentation.navigation.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.navigation.liquid.liquid.lens
import com.infinity.drive.presentation.platform.LiquidNavigationItem
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

@Composable
internal fun FloatingBottomBar(
    items: List<LiquidNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    glass: Boolean,
    showLabels: Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val minTabHeight = 56.dp + 24.dp * if (showLabels) (fontScale - 1f).coerceAtLeast(0f) else 0f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val action by rememberUpdatedState(onSelected)
    val selection = selectedIndex.coerceIn(items.indices)
    var dragPosition by remember(rtl, selection, items.size) { mutableStateOf<Float?>(null) }
    val position by animateFloatAsState(
        targetValue = dragPosition ?: selection.toFloat(),
        animationSpec = spring(stiffness = 550f),
        label = "navigationSelection"
    )
    val surface = if (glass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                blur(10.dp.toPx(), 10.dp.toPx())
                lens(refractionHeight = 12.dp.toPx(), refractionAmount = 10.dp.toPx())
            },
            onDrawSurface = { drawRect(colors.surfaceContainerHigh.copy(alpha = .86f)) }
        )
    } else {
        Modifier.background(colors.surfaceContainerHigh, CircleShape)
    }

    BoxWithConstraints(
        modifier
            .shadow(6.dp, CircleShape, clip = false)
            .then(surface)
            .selectableGroup()
            .pointerInput(rtl, selection, items.size) {
                fun tabAt(x: Float): Float {
                    val width = size.width - 8.dp.toPx()
                    if (width <= 0f) return selection.toFloat()
                    val centered = ((if (rtl) size.width - x else x) - 4.dp.toPx()) / width * items.size - .5f
                    return centered.coerceIn(0f, items.lastIndex.toFloat())
                }
                detectHorizontalDragGestures(
                    onDragStart = { dragPosition = tabAt(it.x) },
                    onDragCancel = { dragPosition = null },
                    onDragEnd = {
                        val target = dragPosition?.roundToInt()
                        dragPosition = null
                        if (target != null && target != selection) action(target)
                    }
                ) { change, _ ->
                    dragPosition = tabAt(change.position.x)
                }
            }
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / items.size
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.offset { IntOffset((tabWidth.toPx() * position).roundToInt(), 0) }
                    .width(tabWidth).fillMaxHeight()
                    .background(colors.primaryContainer, CircleShape)
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, item ->
                val selected = index == selection
                val tint = if (selected) colors.onPrimaryContainer else colors.onSurface
                Column(
                    Modifier.weight(1f).heightIn(min = minTabHeight).testTag(item.tag)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { action(index) })
                        .then(if (showLabels) Modifier else Modifier.semantics { contentDescription = item.label })
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, null, Modifier.size(24.dp), tint = tint)
                    if (showLabels) {
                        Text(item.label, color = tint, style = MaterialTheme.typography.labelMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

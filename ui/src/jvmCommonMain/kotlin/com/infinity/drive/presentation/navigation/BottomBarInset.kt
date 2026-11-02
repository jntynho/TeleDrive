// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height the bottom navigation bar overlays on top of the current screen.
 * Screens add it to their scroll content padding instead of being resized, so
 * showing or hiding the bar never changes the viewport or the scroll position.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Height the bar would occupy in this layout, whether or not the current
 * screen sits under it. Layouts that navigate with a rail leave it at zero.
 * Screens resolve their own inset from this once, so a route change never
 * retimes the padding of a screen that is still on its way out.
 */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

/**
 * Native Material navigation bar above the system navigation inset.
 * Screens already account for the system inset through Scaffold.
 */
val BottomBarHeight: Dp = 80.dp

/**
 * Extra lift for a floating action button. Scaffold already parks it above the
 * system inset with its own margin, so it needs less than the full bar height.
 */
val FabBottomBarInset: Dp = BottomBarHeight - 20.dp

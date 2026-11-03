// Modified for TG Drive; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute

/** A matched page push: no opacity stagger, scale, spring or layout animation. */
object NavigationTransitions {
    const val DurationMillis = 380
    fun enter(forward: Boolean = true): EnterTransition = slideInHorizontally(tween(DurationMillis, easing = FastOutSlowInEasing)) { if (forward) it else -it }
    fun exit(forward: Boolean = true): ExitTransition = slideOutHorizontally(tween(DurationMillis, easing = FastOutSlowInEasing)) { if (forward) -it else it }
    fun AnimatedContentTransitionScope<NavBackStackEntry>.forward(default: Boolean): Boolean {
        val from = TopLevelDestination.entries.indexOfFirst { initialState.destination.hasRoute(it.route::class) }
        val to = TopLevelDestination.entries.indexOfFirst { targetState.destination.hasRoute(it.route::class) }
        return if (from >= 0 && to >= 0 && from != to) to > from else default
    }
}

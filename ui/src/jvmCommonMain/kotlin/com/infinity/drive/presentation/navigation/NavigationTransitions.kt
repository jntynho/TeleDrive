// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

object NavigationTransitions {

    const val DurationMillis = 280

    /** One shared opacity curve: no translation, scaling, stagger or spring overshoot. */
    fun enter(): EnterTransition = fadeIn(tween(DurationMillis, easing = FastOutSlowInEasing))
    fun exit(): ExitTransition = fadeOut(tween(DurationMillis, easing = FastOutSlowInEasing))

}

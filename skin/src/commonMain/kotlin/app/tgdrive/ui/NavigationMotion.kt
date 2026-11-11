package app.tgdrive.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Scroll effects and expensive secondary motion yield while a page is entering or leaving. */
val LocalNavigationInProgress=staticCompositionLocalOf{false}

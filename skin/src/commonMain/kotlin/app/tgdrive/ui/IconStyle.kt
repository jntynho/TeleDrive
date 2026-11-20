package app.tgdrive.ui

import androidx.compose.runtime.compositionLocalOf

/** Visual style of the Iconsax glyphs in the current composition. */
enum class IconStyle { Outline, Broken }

val LocalIconStyle = compositionLocalOf { IconStyle.Outline }

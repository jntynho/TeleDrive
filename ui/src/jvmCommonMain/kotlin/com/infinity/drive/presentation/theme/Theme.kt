// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.tgdrive.ui.DriveTheme
import app.tgdrive.ui.DriveColors
import com.infinity.drive.domain.model.AccentColour
import androidx.compose.ui.graphics.lerp

@Composable
fun DriveInfinityTheme(darkTheme:Boolean=true,dynamicColor:Boolean=false,amoled:Boolean=false,accentColour:AccentColour=AccentColour.BLUE,content:@Composable ()->Unit) {
    val fallback=if(darkTheme)DriveColors else lightColorScheme(primary=Color(0xFF0068D9),primaryContainer=Color(0xFFD9EAFF),onPrimaryContainer=Color(0xFF071D36),secondaryContainer=Color(0xFFE6EDF7),onSecondaryContainer=Color(0xFF14263A),onPrimary=Color.White,background=Color(0xFFF5F5F7),surface=Color.White,surfaceContainer=Color.White,surfaceContainerLow=Color(0xFFF0F1F4),surfaceContainerHigh=Color(0xFFE7E9EF),surfaceContainerHighest=Color(0xFFDDE1E9),surfaceVariant=Color(0xFFE9EAED),onSurface=Color(0xFF191A1C),onSurfaceVariant=Color(0xFF5B5D63),outlineVariant=Color(0xFFE0E1E4),surfaceTint=Color.Transparent)
    val dynamic=if(dynamicColor)platformColorScheme(darkTheme,true)else null
    val base=dynamic?:accentedScheme(fallback,accentColour,darkTheme)
    val colors=if(amoled)base.copy(background=Color.Black,surface=Color(0xFF171819),surfaceContainer=Color(0xFF171819),surfaceContainerLow=Color(0xFF141414),surfaceContainerHigh=Color(0xFF242424),surfaceContainerHighest=Color(0xFF303030))else base
    PlatformThemeSideEffects(darkTheme)
    DriveTheme(colors,content)
}

/** Accent roles change together; backgrounds and AMOLED black remain neutral. */
internal fun accentedScheme(base:ColorScheme,accent:AccentColour,dark:Boolean):ColorScheme {
    if(accent==AccentColour.BLUE)return base
    val primary=Color(if(dark)accent.dark else accent.light)
    val container=lerp(base.surface,primary,if(dark).23f else .13f)
    val ink=if(dark)Color(0xFF101216)else Color.White
    return base.copy(primary=primary,onPrimary=ink,primaryContainer=container,onPrimaryContainer=if(dark)primary else Color(0xFF17191D),
        secondary=primary,onSecondary=ink,secondaryContainer=container,onSecondaryContainer=base.onSurface,
        tertiary=primary,onTertiary=ink,tertiaryContainer=container,onTertiaryContainer=base.onSurface,inversePrimary=Color(if(dark)accent.light else accent.dark))
}

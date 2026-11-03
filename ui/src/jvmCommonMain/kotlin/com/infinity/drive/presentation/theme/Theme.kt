// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.tgdrive.ui.DriveTheme
import app.tgdrive.ui.DriveColors

@Composable
fun DriveInfinityTheme(darkTheme:Boolean=true,dynamicColor:Boolean=false,amoled:Boolean=false,content:@Composable ()->Unit) {
    val fallback=if(darkTheme)DriveColors else lightColorScheme(primary=Color(0xFF0068D9),primaryContainer=Color(0xFFD9EAFF),onPrimaryContainer=Color(0xFF071D36),secondaryContainer=Color(0xFFE6EDF7),onSecondaryContainer=Color(0xFF14263A),onPrimary=Color.White,background=Color(0xFFF5F5F7),surface=Color.White,surfaceContainer=Color.White,surfaceContainerLow=Color(0xFFF0F1F4),surfaceContainerHigh=Color(0xFFE7E9EF),surfaceContainerHighest=Color(0xFFDDE1E9),surfaceVariant=Color(0xFFE9EAED),onSurface=Color(0xFF191A1C),onSurfaceVariant=Color(0xFF5B5D63),outlineVariant=Color(0xFFE0E1E4),surfaceTint=Color.Transparent)
    val colors=when {
        amoled->DriveColors.copy(background=Color.Black,surface=Color(0xFF171819),surfaceContainer=Color(0xFF171819),surfaceContainerLow=Color(0xFF141414),surfaceContainerHigh=Color(0xFF242424),surfaceContainerHighest=Color(0xFF303030))
        dynamicColor->platformColorScheme(darkTheme,true)?:fallback
        darkTheme->DriveColors
        else->fallback
    }
    PlatformThemeSideEffects(darkTheme)
    DriveTheme(colors,content)
}

// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.tgdrive.ui.DriveTheme
import app.tgdrive.ui.DriveColors

@Composable
fun DriveInfinityTheme(darkTheme:Boolean=true,dynamicColor:Boolean=false,amoled:Boolean=false,content:@Composable ()->Unit) {
    val colors=when {
        amoled->DriveColors.copy(background=Color.Black,surface=Color(0xFF171819),surfaceContainer=Color(0xFF171819))
        dynamicColor->platformColorScheme(darkTheme,true)?:DriveColors
        darkTheme->DriveColors
        else->lightColorScheme(primary=Color(0xFF0068D9),onPrimary=Color.White,background=Color(0xFFF5F5F7),surface=Color.White,surfaceContainer=Color.White,surfaceVariant=Color(0xFFE9EAED),onSurface=Color(0xFF191A1C),onSurfaceVariant=Color(0xFF5B5D63),outlineVariant=Color(0xFFE0E1E4),surfaceTint=Color.Transparent)
    }
    PlatformThemeSideEffects(darkTheme)
    DriveTheme(colors,content)
}

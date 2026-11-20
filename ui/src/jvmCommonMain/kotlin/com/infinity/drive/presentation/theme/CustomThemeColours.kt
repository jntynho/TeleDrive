package com.infinity.drive.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.infinity.drive.domain.model.AccentColour
import com.infinity.drive.domain.model.CustomTheme
import com.infinity.drive.domain.model.ThemePalette

internal fun AccentColour.editorDefinition(): CustomTheme = themeDefinition() ?: captureTheme(
    id = name,
    name = chooserLabel,
    light = accentedScheme(defaultColorScheme(false), this, false),
    dark = accentedScheme(defaultColorScheme(true), this, true)
)

internal fun captureTheme(id: String, name: String, light: ColorScheme, dark: ColorScheme): CustomTheme {
    fun capture(scheme: ColorScheme) = ThemePalette(
        primary = scheme.primary.toStoredColour(),
        secondary = scheme.secondary.toStoredColour(),
        tertiary = scheme.tertiary.toStoredColour(),
        background = scheme.background.toStoredColour(),
        overrides = ThemeColourRole.entries.associate { it.name to it.read(scheme).toStoredColour() },
        inheritedOverrides = ThemeColourRole.entries.mapTo(mutableSetOf()) { it.name }
    )
    return CustomTheme(id, name, capture(light), capture(dark))
}

internal fun Color.toStoredColour(): Long = toArgb().toLong() and 0xFFFFFFFFL

internal fun CustomTheme.toColorScheme(darkMode: Boolean, amoled: Boolean = false): ColorScheme {
    val palette = if (darkMode) dark else light
    val inversePalette = if (darkMode) light else dark
    val default = defaultColorScheme(darkMode)
    val background = Color(palette.background).compositeOver(default.background)
    val edge = if (darkMode) Color.White else Color.Black
    val surface = lerp(background, edge, if (darkMode) 0.055f else 0.025f)
    val surfaceVariant = lerp(background, Color(palette.secondary), 0.14f)
    val highSurface = lerp(background, edge, 0.09f)
    val primary = legibleAccent(Color(palette.primary), highSurface)
    val secondary = legibleAccent(Color(palette.secondary), highSurface)
    val tertiary = legibleAccent(Color(palette.tertiary), highSurface)
    val primaryContainer = lerp(background, primary, 0.20f)
    val secondaryContainer = lerp(background, secondary, 0.20f)
    val tertiaryContainer = lerp(background, tertiary, 0.20f)
    val inverseSurface = Color(inversePalette.background).compositeOver(defaultColorScheme(!darkMode).background)
    val onSurface = contentColour(surface, default.onSurface)
    val onBackground = contentColour(background, default.onBackground)
    val preferredMuted = lerp(onSurface, surface, 0.27f)
    val fixedCanvas = Color(0xFFF7F7F7)
    val primaryFixed = lerp(fixedCanvas, Color(light.primary), 0.24f)
    val primaryFixedDim = lerp(fixedCanvas, Color(light.primary), 0.40f)
    val secondaryFixed = lerp(fixedCanvas, Color(light.secondary), 0.24f)
    val secondaryFixedDim = lerp(fixedCanvas, Color(light.secondary), 0.40f)
    val tertiaryFixed = lerp(fixedCanvas, Color(light.tertiary), 0.24f)
    val tertiaryFixedDim = lerp(fixedCanvas, Color(light.tertiary), 0.40f)
    val scheme = default.copy(
        primary = primary,
        onPrimary = contentColour(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = contentColour(primaryContainer, onSurface),
        secondary = secondary,
        onSecondary = contentColour(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contentColour(secondaryContainer, onSurface),
        tertiary = tertiary,
        onTertiary = contentColour(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contentColour(tertiaryContainer, onSurface),
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = contentColour(surfaceVariant, preferredMuted),
        outline = lerp(surface, onSurface, 0.52f),
        outlineVariant = lerp(surface, onSurface, 0.22f),
        inverseSurface = inverseSurface,
        inverseOnSurface = contentColour(inverseSurface),
        inversePrimary = legibleAccent(Color(inversePalette.primary), inverseSurface),
        surfaceTint = primary,
        surfaceDim = lerp(background, Color.Black, 0.08f),
        surfaceBright = lerp(background, Color.White, 0.18f),
        surfaceContainerLowest = background,
        surfaceContainerLow = lerp(background, edge, 0.035f),
        surfaceContainer = surface,
        surfaceContainerHigh = highSurface,
        surfaceContainerHighest = lerp(background, edge, 0.13f),
        primaryFixed = primaryFixed,
        primaryFixedDim = primaryFixedDim,
        onPrimaryFixed = contentColour(primaryFixed),
        onPrimaryFixedVariant = contentColour(primaryFixedDim),
        secondaryFixed = secondaryFixed,
        secondaryFixedDim = secondaryFixedDim,
        onSecondaryFixed = contentColour(secondaryFixed),
        onSecondaryFixedVariant = contentColour(secondaryFixedDim),
        tertiaryFixed = tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim,
        onTertiaryFixed = contentColour(tertiaryFixed),
        onTertiaryFixedVariant = contentColour(tertiaryFixedDim)
    ).withPaletteOverrides(palette.overrides)
    return if (amoled) scheme.withAmoledBackground() else scheme
}

internal fun ColorScheme.withAmoledBackground(): ColorScheme {
    val card = Color(0xFF171819)
    val darkest = Color(0xFF141414)
    val raised = Color(0xFF242424)
    return copy(
        background = Color.Black,
        onBackground = contentColour(Color.Black, onBackground),
        surface = card,
        onSurface = contentColour(card, onSurface),
        surfaceVariant = raised,
        onSurfaceVariant = contentColour(raised, onSurfaceVariant),
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF303030),
        surfaceContainerLowest = Color.Black,
        surfaceContainer = card,
        surfaceContainerLow = darkest,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = Color(0xFF303030)
    )
}

internal fun contentColour(background: Color, preferred: Color = Color.Unspecified): Color {
    if (preferred != Color.Unspecified && contrast(preferred, background) >= 4.5f) return preferred
    return if (contrast(Color.Black, background) >= 4.5f) Color.Black else Color.White
}

private fun legibleAccent(accent: Color, background: Color): Color {
    if (contrast(accent, background) >= 4.5f) return accent
    val ink = contentColour(background)
    for (step in 1..10) {
        val shade = lerp(accent, ink, step / 10f)
        if (contrast(shade, background) >= 4.5f) return shade
    }
    return ink
}

private fun contrast(first: Color, second: Color): Float {
    val a = first.compositeOver(second).luminance() + 0.05f
    val b = second.luminance() + 0.05f
    return maxOf(a, b) / minOf(a, b)
}

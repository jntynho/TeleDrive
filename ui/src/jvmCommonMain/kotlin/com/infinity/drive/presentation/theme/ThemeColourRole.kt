package com.infinity.drive.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

internal enum class ThemeColourGroup(val label: String) {
    ACTIONS("Actions and selection"),
    SURFACES("Surfaces and text"),
    DETAILS("Borders and feedback"),
    FIXED("Fixed colours")
}

internal enum class ThemeColourRole(
    val label: String,
    val group: ThemeColourGroup,
    val read: (ColorScheme) -> Color
) {
    PRIMARY("Primary action", ThemeColourGroup.ACTIONS, ColorScheme::primary),
    ON_PRIMARY("Primary action text", ThemeColourGroup.ACTIONS, ColorScheme::onPrimary),
    PRIMARY_CONTAINER("Primary container", ThemeColourGroup.ACTIONS, ColorScheme::primaryContainer),
    ON_PRIMARY_CONTAINER("Primary container text", ThemeColourGroup.ACTIONS, ColorScheme::onPrimaryContainer),
    SECONDARY("Secondary action", ThemeColourGroup.ACTIONS, ColorScheme::secondary),
    ON_SECONDARY("Secondary action text", ThemeColourGroup.ACTIONS, ColorScheme::onSecondary),
    SECONDARY_CONTAINER("Secondary container", ThemeColourGroup.ACTIONS, ColorScheme::secondaryContainer),
    ON_SECONDARY_CONTAINER("Secondary container text", ThemeColourGroup.ACTIONS, ColorScheme::onSecondaryContainer),
    TERTIARY("Tertiary action", ThemeColourGroup.ACTIONS, ColorScheme::tertiary),
    ON_TERTIARY("Tertiary action text", ThemeColourGroup.ACTIONS, ColorScheme::onTertiary),
    TERTIARY_CONTAINER("Tertiary container", ThemeColourGroup.ACTIONS, ColorScheme::tertiaryContainer),
    ON_TERTIARY_CONTAINER("Tertiary container text", ThemeColourGroup.ACTIONS, ColorScheme::onTertiaryContainer),
    SURFACE("Surface", ThemeColourGroup.SURFACES, ColorScheme::surface),
    ON_SURFACE("Surface text", ThemeColourGroup.SURFACES, ColorScheme::onSurface),
    ON_BACKGROUND("Background text", ThemeColourGroup.SURFACES, ColorScheme::onBackground),
    SURFACE_VARIANT("Alternate surface", ThemeColourGroup.SURFACES, ColorScheme::surfaceVariant),
    ON_SURFACE_VARIANT("Muted surface text", ThemeColourGroup.SURFACES, ColorScheme::onSurfaceVariant),
    SURFACE_DIM("Dim surface", ThemeColourGroup.SURFACES, ColorScheme::surfaceDim),
    SURFACE_BRIGHT("Bright surface", ThemeColourGroup.SURFACES, ColorScheme::surfaceBright),
    SURFACE_CONTAINER_LOWEST("Lowest container", ThemeColourGroup.SURFACES, ColorScheme::surfaceContainerLowest),
    SURFACE_CONTAINER_LOW("Low container", ThemeColourGroup.SURFACES, ColorScheme::surfaceContainerLow),
    SURFACE_CONTAINER("Card background", ThemeColourGroup.SURFACES, ColorScheme::surfaceContainer),
    SURFACE_CONTAINER_HIGH("High container", ThemeColourGroup.SURFACES, ColorScheme::surfaceContainerHigh),
    SURFACE_CONTAINER_HIGHEST("Highest container", ThemeColourGroup.SURFACES, ColorScheme::surfaceContainerHighest),
    SURFACE_TINT("Surface tint", ThemeColourGroup.SURFACES, ColorScheme::surfaceTint),
    OUTLINE("Border", ThemeColourGroup.DETAILS, ColorScheme::outline),
    OUTLINE_VARIANT("Divider", ThemeColourGroup.DETAILS, ColorScheme::outlineVariant),
    INVERSE_SURFACE("Inverse surface", ThemeColourGroup.DETAILS, ColorScheme::inverseSurface),
    INVERSE_ON_SURFACE("Inverse surface text", ThemeColourGroup.DETAILS, ColorScheme::inverseOnSurface),
    INVERSE_PRIMARY("Inverse accent", ThemeColourGroup.DETAILS, ColorScheme::inversePrimary),
    SCRIM("Overlay", ThemeColourGroup.DETAILS, ColorScheme::scrim),
    ERROR("Error", ThemeColourGroup.DETAILS, ColorScheme::error),
    ON_ERROR("Error text", ThemeColourGroup.DETAILS, ColorScheme::onError),
    ERROR_CONTAINER("Error container", ThemeColourGroup.DETAILS, ColorScheme::errorContainer),
    ON_ERROR_CONTAINER("Error container text", ThemeColourGroup.DETAILS, ColorScheme::onErrorContainer),
    PRIMARY_FIXED("Fixed primary", ThemeColourGroup.FIXED, ColorScheme::primaryFixed),
    PRIMARY_FIXED_DIM("Fixed primary dim", ThemeColourGroup.FIXED, ColorScheme::primaryFixedDim),
    ON_PRIMARY_FIXED("Fixed primary text", ThemeColourGroup.FIXED, ColorScheme::onPrimaryFixed),
    ON_PRIMARY_FIXED_VARIANT("Fixed primary muted text", ThemeColourGroup.FIXED, ColorScheme::onPrimaryFixedVariant),
    SECONDARY_FIXED("Fixed secondary", ThemeColourGroup.FIXED, ColorScheme::secondaryFixed),
    SECONDARY_FIXED_DIM("Fixed secondary dim", ThemeColourGroup.FIXED, ColorScheme::secondaryFixedDim),
    ON_SECONDARY_FIXED("Fixed secondary text", ThemeColourGroup.FIXED, ColorScheme::onSecondaryFixed),
    ON_SECONDARY_FIXED_VARIANT("Fixed secondary muted text", ThemeColourGroup.FIXED, ColorScheme::onSecondaryFixedVariant),
    TERTIARY_FIXED("Fixed tertiary", ThemeColourGroup.FIXED, ColorScheme::tertiaryFixed),
    TERTIARY_FIXED_DIM("Fixed tertiary dim", ThemeColourGroup.FIXED, ColorScheme::tertiaryFixedDim),
    ON_TERTIARY_FIXED("Fixed tertiary text", ThemeColourGroup.FIXED, ColorScheme::onTertiaryFixed),
    ON_TERTIARY_FIXED_VARIANT("Fixed tertiary muted text", ThemeColourGroup.FIXED, ColorScheme::onTertiaryFixedVariant),
}

internal fun ColorScheme.withPaletteOverrides(overrides: Map<String, Long>): ColorScheme {
    if (overrides.isEmpty()) return this
    fun override(role: ThemeColourRole): Color? = overrides[role.name]?.let { Color(it) }
    fun value(role: ThemeColourRole, background: ThemeColourRole? = null): Color {
        val chosen = override(role) ?: role.read(this)
        if (background == null) return chosen
        val backdrop = (override(background) ?: background.read(this)).compositeOver(this.background)
        return contentColour(backdrop, chosen)
    }

    return copy(
        primary = value(ThemeColourRole.PRIMARY),
        onPrimary = value(ThemeColourRole.ON_PRIMARY, ThemeColourRole.PRIMARY),
        primaryContainer = value(ThemeColourRole.PRIMARY_CONTAINER),
        onPrimaryContainer = value(ThemeColourRole.ON_PRIMARY_CONTAINER, ThemeColourRole.PRIMARY_CONTAINER),
        secondary = value(ThemeColourRole.SECONDARY),
        onSecondary = value(ThemeColourRole.ON_SECONDARY, ThemeColourRole.SECONDARY),
        secondaryContainer = value(ThemeColourRole.SECONDARY_CONTAINER),
        onSecondaryContainer = value(ThemeColourRole.ON_SECONDARY_CONTAINER, ThemeColourRole.SECONDARY_CONTAINER),
        tertiary = value(ThemeColourRole.TERTIARY),
        onTertiary = value(ThemeColourRole.ON_TERTIARY, ThemeColourRole.TERTIARY),
        tertiaryContainer = value(ThemeColourRole.TERTIARY_CONTAINER),
        onTertiaryContainer = value(ThemeColourRole.ON_TERTIARY_CONTAINER, ThemeColourRole.TERTIARY_CONTAINER),
        surface = value(ThemeColourRole.SURFACE),
        onSurface = value(ThemeColourRole.ON_SURFACE, ThemeColourRole.SURFACE),
        onBackground = contentColour(background, value(ThemeColourRole.ON_BACKGROUND)),
        surfaceVariant = value(ThemeColourRole.SURFACE_VARIANT),
        onSurfaceVariant = value(ThemeColourRole.ON_SURFACE_VARIANT, ThemeColourRole.SURFACE_VARIANT),
        surfaceDim = value(ThemeColourRole.SURFACE_DIM),
        surfaceBright = value(ThemeColourRole.SURFACE_BRIGHT),
        surfaceContainerLowest = value(ThemeColourRole.SURFACE_CONTAINER_LOWEST),
        surfaceContainerLow = value(ThemeColourRole.SURFACE_CONTAINER_LOW),
        surfaceContainer = value(ThemeColourRole.SURFACE_CONTAINER),
        surfaceContainerHigh = value(ThemeColourRole.SURFACE_CONTAINER_HIGH),
        surfaceContainerHighest = value(ThemeColourRole.SURFACE_CONTAINER_HIGHEST),
        surfaceTint = value(ThemeColourRole.SURFACE_TINT),
        outline = value(ThemeColourRole.OUTLINE),
        outlineVariant = value(ThemeColourRole.OUTLINE_VARIANT),
        inverseSurface = value(ThemeColourRole.INVERSE_SURFACE),
        inverseOnSurface = value(ThemeColourRole.INVERSE_ON_SURFACE, ThemeColourRole.INVERSE_SURFACE),
        inversePrimary = value(ThemeColourRole.INVERSE_PRIMARY),
        scrim = value(ThemeColourRole.SCRIM),
        error = value(ThemeColourRole.ERROR),
        onError = value(ThemeColourRole.ON_ERROR, ThemeColourRole.ERROR),
        errorContainer = value(ThemeColourRole.ERROR_CONTAINER),
        onErrorContainer = value(ThemeColourRole.ON_ERROR_CONTAINER, ThemeColourRole.ERROR_CONTAINER),
        primaryFixed = value(ThemeColourRole.PRIMARY_FIXED),
        primaryFixedDim = value(ThemeColourRole.PRIMARY_FIXED_DIM),
        onPrimaryFixed = value(ThemeColourRole.ON_PRIMARY_FIXED, ThemeColourRole.PRIMARY_FIXED),
        onPrimaryFixedVariant = value(ThemeColourRole.ON_PRIMARY_FIXED_VARIANT, ThemeColourRole.PRIMARY_FIXED_DIM),
        secondaryFixed = value(ThemeColourRole.SECONDARY_FIXED),
        secondaryFixedDim = value(ThemeColourRole.SECONDARY_FIXED_DIM),
        onSecondaryFixed = value(ThemeColourRole.ON_SECONDARY_FIXED, ThemeColourRole.SECONDARY_FIXED),
        onSecondaryFixedVariant = value(ThemeColourRole.ON_SECONDARY_FIXED_VARIANT, ThemeColourRole.SECONDARY_FIXED_DIM),
        tertiaryFixed = value(ThemeColourRole.TERTIARY_FIXED),
        tertiaryFixedDim = value(ThemeColourRole.TERTIARY_FIXED_DIM),
        onTertiaryFixed = value(ThemeColourRole.ON_TERTIARY_FIXED, ThemeColourRole.TERTIARY_FIXED),
        onTertiaryFixedVariant = value(ThemeColourRole.ON_TERTIARY_FIXED_VARIANT, ThemeColourRole.TERTIARY_FIXED_DIM),
    )
}

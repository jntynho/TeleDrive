package com.infinity.drive.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ThemePalette(
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val background: Long,
    val overrides: Map<String, Long> = emptyMap(),
    val inheritedOverrides: Set<String> = emptySet()
)

@Serializable
data class CustomTheme(
    val id: String,
    val name: String,
    val light: ThemePalette,
    val dark: ThemePalette
)

fun UserPreferences.selectAccentColour(colour: AccentColour): UserPreferences =
    copy(accentColour = colour, selectedCustomThemeId = null)

fun UserPreferences.selectCustomTheme(id: String): UserPreferences =
    if (customThemes.any { it.id == id }) copy(selectedCustomThemeId = id) else this

fun UserPreferences.saveCustomTheme(theme: CustomTheme): UserPreferences {
    val name = theme.name.trim()
    if (name.isEmpty() || customThemes.any { it.name.equals(name, ignoreCase = true) && it.id != theme.id }) return this
    val namedTheme = theme.copy(name = name)
    val themes = if (customThemes.any { it.id == theme.id }) {
        customThemes.map { if (it.id == theme.id) namedTheme else it }
    } else customThemes + namedTheme
    return copy(customThemes = themes, selectedCustomThemeId = theme.id)
}

fun UserPreferences.deleteCustomTheme(id: String): UserPreferences = copy(
    customThemes = customThemes.filterNot { it.id == id },
    selectedCustomThemeId = selectedCustomThemeId?.takeUnless { it == id }
)

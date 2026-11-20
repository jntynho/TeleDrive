package com.infinity.drive.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.LibraryFilters
import app.tgdrive.ui.LibraryHeader
import app.tgdrive.ui.elasticVerticalScroll
import com.infinity.drive.domain.model.AccentColour
import com.infinity.drive.domain.model.CustomTheme
import com.infinity.drive.domain.model.ThemePalette
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.presentation.common.AppBackHandler
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.theme.ThemeColourGroup
import com.infinity.drive.presentation.theme.ThemeColourRole
import com.infinity.drive.presentation.theme.captureTheme
import com.infinity.drive.presentation.theme.editorDefinition
import com.infinity.drive.presentation.theme.platformColorScheme
import com.infinity.drive.presentation.theme.toColorScheme
import com.infinity.drive.presentation.theme.toStoredColour
import com.infinity.drive.presentation.theme.withAmoledBackground
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_back
import java.util.UUID
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource

private enum class PreviewTone(val label: String, val dark: Boolean) {
    LIGHT("Light", false), DARK("Dark", true), AMOLED("AMOLED", true)
}

private enum class PaletteColour(val label: String, val resolvedRole: ThemeColourRole?) {
    PRIMARY("Primary", ThemeColourRole.PRIMARY),
    SECONDARY("Secondary", ThemeColourRole.SECONDARY),
    TERTIARY("Tertiary", ThemeColourRole.TERTIARY),
    BACKGROUND("Background", null);

    fun read(palette: ThemePalette): Long = when (this) {
        PRIMARY -> palette.primary
        SECONDARY -> palette.secondary
        TERTIARY -> palette.tertiary
        BACKGROUND -> palette.background
    }

    fun set(palette: ThemePalette, colour: Long): ThemePalette {
        val overrides = (palette.overrides - palette.inheritedOverrides) - listOfNotNull(resolvedRole?.name)
        return when (this) {
            PRIMARY -> palette.copy(primary = colour, overrides = overrides)
            SECONDARY -> palette.copy(secondary = colour, overrides = overrides)
            TERTIARY -> palette.copy(tertiary = colour, overrides = overrides)
            BACKGROUND -> palette.copy(background = colour)
        }.copy(inheritedOverrides = emptySet())
    }
}

private val themeDraftSaver = Saver<CustomTheme, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<CustomTheme>(it) }
)

@Composable
internal fun CustomThemeEditorScreen(
    preferences: UserPreferences,
    themeId: String?,
    onBack: () -> Unit,
    onSave: (CustomTheme) -> Unit
) {
    val focusManager = LocalFocusManager.current

    val lightWallpaper = platformColorScheme(darkTheme = false, dynamicColor = true)
    val darkWallpaper = platformColorScheme(darkTheme = true, dynamicColor = true)
    val dynamic = remember(lightWallpaper, darkWallpaper) {
        if (lightWallpaper != null && darkWallpaper != null) {
            captureTheme("dynamic", "Dynamic", lightWallpaper, darkWallpaper)
        } else null
    }
    val builtIn = remember { AccentColour.presets.map { it.editorDefinition() } }
    val baseThemes = builtIn + listOfNotNull(dynamic) + preferences.customThemes
    val original = preferences.customThemes.firstOrNull { it.id == themeId }
    val source = original
        ?: dynamic?.takeIf { preferences.dynamicColor }
        ?: preferences.selectedCustomTheme
        ?: remember(preferences.accentColour) { preferences.accentColour.editorDefinition() }

    var draft by rememberSaveable(themeId, stateSaver = themeDraftSaver) {
        val initial = original ?: source.copy(
            id = UUID.randomUUID().toString(),
            name = generateSequence(1) { it + 1 }.map { "Custom theme $it" }.first { candidate ->
                preferences.customThemes.none { it.name.equals(candidate, ignoreCase = true) }
            }
        )
        mutableStateOf(initial)
    }
    var sourceLabel by rememberSaveable(themeId) { mutableStateOf(source.name) }
    var tone by rememberSaveable(themeId) { mutableStateOf(PreviewTone.LIGHT) }
    var advanced by rememberSaveable(themeId) { mutableStateOf(false) }
    var coreColour by rememberSaveable(themeId) { mutableStateOf<PaletteColour?>(null) }
    var individualColour by rememberSaveable(themeId) { mutableStateOf<ThemeColourRole?>(null) }
    val scroll = rememberScrollState()
    AppBackHandler {
        when {
            coreColour != null -> coreColour = null
            individualColour != null -> individualColour = null
            else -> onBack()
        }
    }

    val palette = if (tone.dark) draft.dark else draft.light
    val editScheme = remember(draft.light, draft.dark, tone.dark) { draft.toColorScheme(tone.dark) }
    val previewScheme = if (tone == PreviewTone.AMOLED) editScheme.withAmoledBackground() else editScheme
    val trimmedName = draft.name.trim()
    val duplicate = preferences.customThemes.any {
        it.id != draft.id && it.name.equals(trimmedName, ignoreCase = true)
    }
    fun updatePalette(value: ThemePalette) {
        draft = if (tone.dark) draft.copy(dark = value) else draft.copy(light = value)
    }

    coreColour?.let { role ->
        ThemeColourEditor(
            title = role.label,
            initialColour = role.read(palette),
            onDismiss = { coreColour = null },
            onApply = { value ->
                updatePalette(role.set(palette, value))
                coreColour = null
            }
        )
        return
    }
    individualColour?.let { role ->
        ThemeColourEditor(
            title = role.label,
            initialColour = role.read(editScheme).toStoredColour(),
            onDismiss = { individualColour = null },
            onReset = if (role.name in palette.overrides) ({
                updatePalette(palette.copy(
                    overrides = palette.overrides - role.name, inheritedOverrides = palette.inheritedOverrides - role.name
                ))
                individualColour = null
            }) else null,
            onApply = { value ->
                updatePalette(palette.copy(
                    overrides = palette.overrides + (role.name to value), inheritedOverrides = palette.inheritedOverrides - role.name
                ))
                individualColour = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            LibraryHeader(
                title = if (themeId == null) "New custom theme" else "Edit custom theme",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(DriveIcons.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(bottom = LocalBottomBarInset.current),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    Modifier.weight(1f).elasticVerticalScroll(scroll).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        modifier = Modifier.fillMaxWidth().testTag("custom-theme-name"),
                        label = { Text("Theme name") },
                        singleLine = true,
                        isError = duplicate,
                        supportingText = if (duplicate) ({ Text("A theme with this name already exists") }) else null
                    )
                    ThemeSourceSelector(sourceLabel, baseThemes) { base ->
                        sourceLabel = base.name
                        draft = draft.copy(light = base.light, dark = base.dark)
                    }
                    Column {
                        LibraryFilters(PreviewTone.entries.map { it.label }, tone.ordinal, { tone = PreviewTone.entries[it] })
                        Spacer(Modifier.height(10.dp))
                        ThemeLibraryPreview(previewScheme)
                    }
                    Text("Palette", style = MaterialTheme.typography.titleMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaletteColour.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { entry ->
                                    ThemeColourTile(entry.label, entry.read(palette), Modifier.weight(1f)) {
                                        focusManager.clearFocus()
                                        coreColour = entry
                                    }
                                }
                            }
                        }
                    }
                    Column {
                        Surface(
                            onClick = { advanced = !advanced },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(DriveIcons.Tune, null)
                                Text("Individual colours", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Icon(if (advanced) DriveIcons.ExpandLess else DriveIcons.ExpandMore, null)
                            }
                        }
                        if (advanced) {
                            TextButton(
                                enabled = palette.overrides.isNotEmpty(),
                                onClick = { updatePalette(palette.copy(overrides = emptyMap(), inheritedOverrides = emptySet())) }
                            ) { Text("Reset individual colours") }
                            ThemeColourGroup.entries.forEach { group ->
                                Text(
                                    group.label,
                                    Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ThemeColourRole.entries.filter { it.group == group }.forEach { role ->
                                        ThemeColourTile(role.label, role.read(editScheme).toStoredColour()) {
                                            focusManager.clearFocus()
                                            individualColour = role
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    enabled = trimmedName.isNotEmpty() && !duplicate,
                    onClick = { onSave(draft.copy(name = trimmedName)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 48.dp)
                ) { Text("Save theme") }
            }
        }
    }

}

@Composable
private fun ThemeSourceSelector(selectedLabel: String, options: List<CustomTheme>, onSelect: (CustomTheme) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(DriveIcons.Palette, null)
                Column(Modifier.weight(1f)) {
                    Text("Start from theme", style = MaterialTheme.typography.labelMedium)
                    Text(selectedLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(DriveIcons.ChevronDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 380.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = { open = false; onSelect(option) },
                    leadingIcon = { ColourDot(option.light.primary, Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
private fun ThemeColourTile(label: String, value: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColourDot(value, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value.toColourHex(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ColourDot(colour: Long, modifier: Modifier) {
    Box(modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).background(Color(colour), CircleShape))
}

@Composable
private fun ThemeLibraryPreview(colors: ColorScheme) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("custom-theme-preview"),
        shape = MaterialTheme.shapes.medium,
        color = colors.background,
        contentColor = colors.onBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(DriveIcons.CloudDone, null, Modifier.size(20.dp), tint = colors.primary)
                Text("Your library", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Surface(shape = MaterialTheme.shapes.small, color = colors.primary, contentColor = colors.onPrimary) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(DriveIcons.Add, null, Modifier.size(14.dp))
                        Text("Add", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainer, contentColor = colors.onSurface) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(DriveIcons.Folder, null, Modifier.size(22.dp), tint = colors.secondary)
                    Column(Modifier.weight(1f)) {
                        Text("Documents", style = MaterialTheme.typography.labelMedium)
                        Text("3 files", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    Icon(DriveIcons.Star, null, Modifier.size(18.dp), tint = colors.tertiary)
                }
            }
        }
    }
}

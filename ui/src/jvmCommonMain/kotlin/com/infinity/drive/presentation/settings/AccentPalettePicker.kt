package com.infinity.drive.presentation.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.elasticHorizontalScroll
import com.infinity.drive.domain.model.AccentColour
import com.infinity.drive.domain.model.AppTheme
import com.infinity.drive.domain.model.CustomTheme
import com.infinity.drive.domain.model.ThemePalette
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.presentation.theme.contentColour
import com.infinity.drive.presentation.theme.editorDefinition
import com.infinity.drive.presentation.theme.platformColorScheme
import com.infinity.drive.presentation.theme.toColorScheme
import com.infinity.drive.presentation.theme.toStoredColour
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.settings_accent_colour
import org.jetbrains.compose.resources.stringResource

private val accentCellWidth = 64.dp
private val accentCellGap = 2.dp

@Composable
internal fun AccentColourPicker(
    preferences: UserPreferences,
    supportsDynamicColor: Boolean,
    onSelect: (AccentColour) -> Unit,
    onSelectDynamic: () -> Unit,
    onSelectCustom: (String) -> Unit,
    onEditCustom: (String) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onAddCustom: () -> Unit
) {
    val dark = when (preferences.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val useDynamic = supportsDynamicColor && preferences.dynamicColor
    val activeCustom = preferences.selectedCustomTheme.takeUnless { useDynamic }
    val activePreset = preferences.accentColour
    val presets = remember { AccentColour.presets.map { it to it.editorDefinition() } }
    val selectedIndex = when {
        useDynamic -> 1
        activeCustom != null -> presets.size + (if (supportsDynamicColor) 1 else 0) +
            preferences.customThemes.indexOf(activeCustom)
        else -> AccentColour.presets.indexOf(activePreset).coerceAtLeast(0) +
            (if (supportsDynamicColor && activePreset != AccentColour.BLUE) 1 else 0)
    }
    val scrollState = rememberScrollState(
        initial = with(LocalDensity.current) {
            ((accentCellWidth + accentCellGap) * (selectedIndex - 2).coerceAtLeast(0)).roundToPx()
        }
    )
    val colors = MaterialTheme.colorScheme
    var deleting by remember { mutableStateOf<CustomTheme?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_accent_colour), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (useDynamic) "Dynamic" else activeCustom?.name ?: activePreset.chooserLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (activeCustom != null) {
                IconButton(onClick = { onEditCustom(activeCustom.id) }) {
                    Icon(DriveIcons.Edit, contentDescription = "Edit custom theme")
                }
                IconButton(onClick = { deleting = activeCustom }) {
                    Icon(DriveIcons.DeleteOutline, contentDescription = "Delete custom theme")
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                val edgeWidth = minOf(20.dp.toPx(), size.width / 2)
                val isLtr = layoutDirection == LayoutDirection.Ltr
                val fadeLeft = if (isLtr) scrollState.canScrollBackward else scrollState.canScrollForward
                val fadeRight = if (isLtr) scrollState.canScrollForward else scrollState.canScrollBackward
                val background = colors.surfaceContainer
                if (fadeLeft) drawRect(
                    Brush.horizontalGradient(listOf(background, background.copy(alpha = 0f)), endX = edgeWidth),
                    size = Size(edgeWidth, size.height)
                )
                if (fadeRight) drawRect(
                    Brush.horizontalGradient(
                        listOf(background.copy(alpha = 0f), background),
                        startX = size.width - edgeWidth, endX = size.width
                    ),
                    topLeft = Offset(size.width - edgeWidth, 0f),
                    size = Size(edgeWidth, size.height)
                )
            }.elasticHorizontalScroll(scrollState).selectableGroup().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(accentCellGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { (choice, definition) ->
                AccentSample(
                    label = choice.chooserLabel,
                    palette = if (dark) definition.dark else definition.light,
                    selected = !useDynamic && activeCustom == null && activePreset == choice,
                    onSelect = { onSelect(choice) }
                )
                if (choice == AccentColour.BLUE && supportsDynamicColor) {
                    val wallpaper = platformColorScheme(dark, true) ?: colors
                    AccentSample(
                        label = "Dynamic",
                        palette = ThemePalette(
                            wallpaper.primary.toStoredColour(), wallpaper.secondary.toStoredColour(),
                            wallpaper.tertiary.toStoredColour(), wallpaper.background.toStoredColour()
                        ),
                        selected = useDynamic,
                        onSelect = onSelectDynamic
                    )
                }
            }
            preferences.customThemes.forEach { theme ->
                val resolved = remember(theme, dark, preferences.theme) {
                    theme.toColorScheme(dark, amoled = preferences.theme == AppTheme.AMOLED)
                }
                AccentSample(
                    label = theme.name,
                    palette = ThemePalette(
                        resolved.primary.toStoredColour(), resolved.secondary.toStoredColour(),
                        resolved.tertiary.toStoredColour(), resolved.background.toStoredColour()
                    ),
                    selected = activeCustom?.id == theme.id,
                    onSelect = { onSelectCustom(theme.id) }
                )
            }
            Column(
                Modifier.width(accentCellWidth).clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button, onClick = onAddCustom)
                    .semantics { contentDescription = "Add Custom" }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(42.dp)) {
                        drawCircle(
                            color = colors.outline,
                            radius = (size.minDimension - 2.dp.toPx()) / 2,
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
                        )
                    }
                    Icon(DriveIcons.Add, null, Modifier.size(21.dp), tint = colors.onSurface)
                }
                Text(
                    "Add Custom", Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    deleting?.let { theme ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete custom theme?") },
            text = { Text(theme.name) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDeleteCustom(theme.id) }) { Text("Delete theme") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun AccentSample(label: String, palette: ThemePalette, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val primary = Color(palette.primary)
    Box(
        Modifier.size(accentCellWidth, 60.dp).clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = "$label accent" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(48.dp).border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.onSurface else colors.outlineVariant,
                shape = CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(primary),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(40.dp)) {
                    drawArc(Color(palette.secondary), startAngle = 20f, sweepAngle = 86f, useCenter = true)
                    drawArc(Color(palette.tertiary), startAngle = 106f, sweepAngle = 38f, useCenter = true)
                }
                if (selected) {
                    val ink = contentColour(primary)
                    Box(Modifier.size(24.dp).background(primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(DriveIcons.Check, null, Modifier.size(17.dp), tint = ink)
                    }
                }
            }
        }
    }
}

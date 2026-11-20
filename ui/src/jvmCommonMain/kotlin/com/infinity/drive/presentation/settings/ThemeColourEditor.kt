package com.infinity.drive.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.LibraryHeader
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.presentation.theme.toStoredColour
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_back
import com.infinity.drive.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal fun Long.toColourHex(): String = "#" + if ((this ushr 24) and 0xFFL == 0xFFL) {
    (this and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase()
} else {
    (this and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase()
}

internal fun parseThemeColour(hex: String): Long? {
    val digits = hex.trim().removePrefix("#")
    if (digits.length !in listOf(6, 8) || digits.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return null
    val value = digits.toLongOrNull(16) ?: return null
    return if (digits.length == 6) value or 0xFF000000L else value
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeColourEditor(
    title: String,
    initialColour: Long,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
    onApply: (Long) -> Unit
) {
    val initial = Color(initialColour)
    val initialHsv = initial.toHsv()
    var hue by rememberSaveable(title, initialColour) { mutableFloatStateOf(initialHsv.hue) }
    var saturation by rememberSaveable(title, initialColour) { mutableFloatStateOf(initialHsv.saturation) }
    var brightness by rememberSaveable(title, initialColour) { mutableFloatStateOf(initialHsv.brightness) }
    var opacity by rememberSaveable(title, initialColour) { mutableFloatStateOf(initial.alpha) }
    var hex by rememberSaveable(title, initialColour) { mutableStateOf(initialColour.toColourHex()) }
    val adjusted = Color.hsv(hue, saturation, brightness, opacity)
    val parsed = parseThemeColour(hex)

    fun rewriteHex() {
        hex = Color.hsv(hue, saturation, brightness, opacity).toStoredColour().toColourHex()
    }

    fun editHex(text: String) {
        hex = text.take(10)
        val colour = parseThemeColour(hex)?.let { Color(it) } ?: return
        val hsv = colour.toHsv()
        hue = hsv.hue
        saturation = hsv.saturation
        brightness = hsv.brightness
        opacity = colour.alpha
    }

    Scaffold(topBar = {
        LibraryHeader(title = title, navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(DriveIcons.ArrowBack, contentDescription = stringResource(Res.string.common_back))
            }
        })
    }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(bottom = LocalBottomBarInset.current),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) { Box(Modifier.background(adjusted)) }
                    OutlinedTextField(
                        value = hex,
                        onValueChange = ::editHex,
                        label = { Text("Hex color") },
                        singleLine = true,
                        isError = parsed == null,
                        modifier = Modifier.weight(1f).testTag("custom-theme-colour-hex")
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }))
                )
                ColourSlider("Hue", hue.roundToInt().toString() + "°", hue, 0f..360f) {
                    hue = it
                    rewriteHex()
                }
                ColourSlider("Saturation", saturation.percent(), saturation) {
                    saturation = it
                    rewriteHex()
                }
                ColourSlider("Brightness", brightness.percent(), brightness) {
                    brightness = it
                    rewriteHex()
                }
                ColourSlider("Opacity", opacity.percent(), opacity) {
                    opacity = it
                    rewriteHex()
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (onReset != null) {
                        TextButton(onClick = onReset) {
                            Icon(DriveIcons.Refresh, null, Modifier.size(16.dp))
                            Text("Reset", Modifier.padding(start = 6.dp))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
                    Button(onClick = { parsed?.let(onApply) }, enabled = parsed != null) {
                        Text("Apply color")
                    }
                }
            }
        }
    }
}

@Composable
private fun ColourSlider(label: String, display: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(display, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

private data class Hsv(val hue: Float, val saturation: Float, val brightness: Float)

private fun Color.toHsv(): Hsv {
    val maximum = maxOf(red, green, blue)
    val difference = maximum - minOf(red, green, blue)
    val hue = when {
        difference == 0f -> 0f
        maximum == red -> (green - blue) / difference
        maximum == green -> 2 + (blue - red) / difference
        else -> 4 + (red - green) / difference
    }
    val degrees = ((hue * 60 + 360) % 360).coerceIn(0f, 360f)
    return Hsv(degrees, if (maximum == 0f) 0f else (difference / maximum).coerceIn(0f, 1f), maximum)
}

private fun Float.percent(): String = "${(this * 100).roundToInt()}%"

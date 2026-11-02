// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.*
import app.tgdrive.ui.DriveSwitch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.components.GroupedList
import com.infinity.drive.presentation.components.GroupedListScope

val LocalSettingsSearchTarget = staticCompositionLocalOf<String?> { null }

@Composable
private fun Modifier.searchTarget(title:String):Modifier {
    val target=LocalSettingsSearchTarget.current
    val requester=remember{BringIntoViewRequester()}
    LaunchedEffect(title,target){if(title==target){delay(320);requester.bringIntoView()}}
    return this.bringIntoViewRequester(requester).background(if(title==target)MaterialTheme.colorScheme.primary.copy(alpha=.10f)else Color.Transparent)
}


@Composable
fun SettingsGroup(content: GroupedListScope.() -> Unit) {
    GroupedList(horizontalPadding = 16.dp, connected=true, content = content)
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().heightIn(min=56.dp).searchTarget(title)
            .toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        DriveSwitch(checked,null)
    }
}

@Composable
fun SettingsClickRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().heightIn(min=56.dp).searchTarget(title)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(16.dp))
            it()
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector?) {
    icon ?: return
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(ROW_ICON_SIZE)
    )
    Spacer(Modifier.width(16.dp))
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    valueLabel: (Int) -> String = { it.toString() }
) {
    var draft by remember(title){mutableFloatStateOf(value.toFloat())}
    var dragging by remember(title){mutableStateOf(false)}
    LaunchedEffect(value,dragging){if(!dragging)draft=value.toFloat()}
    Column(
        modifier = Modifier
            .fillMaxWidth().searchTarget(title)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueLabel(if(dragging)kotlin.math.round(draft).toInt()else value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        app.tgdrive.ui.SlimSlider(
            value = if(dragging)draft else value.toFloat(),
            onValueChange = { dragging=true;draft=it },
            onValueChangeFinished={val next=kotlin.math.round(draft).toInt().coerceIn(range);dragging=false;if(next!=value)onChange(next)},
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0)
        )
    }
}

private val ROW_ICON_SIZE = 24.dp

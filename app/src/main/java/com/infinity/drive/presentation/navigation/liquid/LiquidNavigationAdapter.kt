package com.infinity.drive.presentation.navigation.liquid

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.infinity.drive.presentation.platform.LiquidNavigation
import com.infinity.drive.presentation.platform.LiquidNavigationItem
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@androidx.annotation.RequiresApi(33)
@Composable
fun rememberLiquidNavigation(): LiquidNavigation {
    val backdrop = rememberLayerBackdrop { drawContent() }
    return remember(backdrop) {
        object : LiquidNavigation {
            override val supportsGlass: Boolean get() = isRuntimeShaderSupported()

            override fun capture(modifier: Modifier, enabled: Boolean): Modifier =
                if (enabled) modifier.layerBackdrop(backdrop) else modifier

            @Composable
            override fun Bar(
                items: List<LiquidNavigationItem>,
                selectedIndex: Int,
                showLabels: Boolean,
                glass: Boolean,
                onSelect: (Int) -> Unit
            ) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingBottomBar(
                        items = items,
                        selectedIndex = selectedIndex,
                        onSelected = onSelect,
                        backdrop = backdrop,
                        glass = glass && supportsGlass,
                        showLabels = showLabels,
                        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().testTag("liquid-navigation")
                    )
                }
            }
        }
    }
}

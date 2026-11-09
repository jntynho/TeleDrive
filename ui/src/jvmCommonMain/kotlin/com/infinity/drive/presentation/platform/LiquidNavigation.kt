package com.infinity.drive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class LiquidNavigationItem(val icon:ImageVector,val label:String,val tag:String)
interface LiquidNavigation {
    val supportsGlass:Boolean
    fun capture(modifier:Modifier,enabled:Boolean):Modifier
    @Composable fun Bar(items:List<LiquidNavigationItem>,selectedIndex:Int,showLabels:Boolean,glass:Boolean,onSelect:(Int)->Unit)
}
val LocalLiquidNavigation=staticCompositionLocalOf<LiquidNavigation?>{null}

package com.infinity.drive.presentation.navigation.liquid

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinity.drive.presentation.platform.LiquidNavigation
import com.infinity.drive.presentation.platform.LiquidNavigationItem
import top.yukonga.miuix.kmp.blur.*

@androidx.annotation.RequiresApi(33)
@Composable
fun rememberLiquidNavigation():LiquidNavigation {
    val backdrop=rememberLayerBackdrop { drawContent() }
    return remember(backdrop){object:LiquidNavigation {
        override val supportsGlass:Boolean get()=isRuntimeShaderSupported()
        override fun capture(modifier:Modifier,enabled:Boolean)=if(enabled)modifier.layerBackdrop(backdrop)else modifier
        @Composable override fun Bar(items:List<LiquidNavigationItem>,selectedIndex:Int,showLabels:Boolean,glass:Boolean,onSelect:(Int)->Unit){
            val index=rememberUpdatedState(selectedIndex)
            val action=rememberUpdatedState(onSelect)
            val readIndex=remember{{index.value}}
            val select=remember{{i:Int->action.value(i)}}
            BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom=12.dp),contentAlignment=Alignment.Center){
                val itemWidth=minOf(76.dp,((maxWidth-32.dp)/items.size.coerceAtLeast(1)).coerceAtLeast(48.dp))
                FloatingBottomBar(selectedIndex=readIndex,onSelected=select,backdrop=backdrop,tabsCount=items.size,isBlurEnabled=glass&&supportsGlass,
                    modifier=Modifier.widthIn(max=maxWidth-16.dp).testTag("liquid-navigation")){
                    items.forEachIndexed{i,item->
                        FloatingBottomBarItem(onClick={select(i)},modifier=Modifier.defaultMinSize(minWidth=itemWidth).testTag(item.tag).semantics{selected=i==selectedIndex}){
                            Icon(item.icon,item.label,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.onSurface)
                            if(showLabels)Text(item.label,color=MaterialTheme.colorScheme.onSurface,fontSize=12.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }}
}

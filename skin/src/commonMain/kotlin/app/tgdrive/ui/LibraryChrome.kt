package app.tgdrive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import app.tgdrive.ui.elasticHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHeader(title:String, navigationIcon:@Composable ()->Unit={}, actions:@Composable RowScope.()->Unit={}) {
    TopAppBar(title={Text(title,style=MaterialTheme.typography.headlineLarge,maxLines=1,overflow=TextOverflow.Ellipsis)},
        navigationIcon=navigationIcon, actions=actions,
        colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background,scrolledContainerColor=MaterialTheme.colorScheme.background))
}

/** Equal-width native tabs, with one quiet sliding indicator and no press ripple. */
@Composable
fun LibraryFilters(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    if(labels.isEmpty())return
    val index=selected.coerceIn(0,labels.lastIndex)
    val position by androidx.compose.animation.core.animateFloatAsState(index.toFloat(),androidx.compose.animation.core.tween(260,easing=androidx.compose.animation.core.FastOutSlowInEasing),label="filterIndicator")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width=maxWidth/labels.size
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { item,label->
                val color by animateColorAsState(if(item==index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,androidx.compose.animation.core.tween(220),label="filterLabel")
                Box(Modifier.weight(1f).heightIn(min=48.dp).selectable(selected=item==index,onClick={if(item!=index)onSelect(item)},role=Role.Tab,interactionSource=remember{androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null).padding(horizontal=4.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                    Text(label,color=color,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset(x=width*position).width(width).padding(horizontal=12.dp).height(2.dp).background(MaterialTheme.colorScheme.primary))
    }
}

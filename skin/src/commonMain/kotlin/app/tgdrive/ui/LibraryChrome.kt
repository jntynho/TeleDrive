package app.tgdrive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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

/** Stable height: changing filters never moves the content while scrolling. */
@Composable
fun LibraryFilters(labels:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
        labels.forEachIndexed { index,label ->
            val active=index==selected
            val color by animateColorAsState(if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,label="filterColor")
            Column(Modifier.width(IntrinsicSize.Max).widthIn(min=48.dp).selectable(active,onClick={onSelect(index)},role=Role.Tab).heightIn(min=48.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.heightIn(min=44.dp).padding(horizontal=2.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                    Text(label,color=color,style=MaterialTheme.typography.titleSmall)
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(if(active)color else Color.Transparent))
            }
        }
    }
}

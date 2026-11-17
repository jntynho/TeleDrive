package app.tgdrive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class LibraryShortcut(val label:String,val icon:ImageVector,val onClick:()->Unit)
data class LibraryOverview(val count:Int,val bytes:Long,val groups:List<Long>,val offlineBytes:Long=0,val lockedVaults:Int=0)

/** Retained for callers that calculate category proportions; these are not storage quotas. */
fun libraryArcShares(groups:List<Long>):List<Float> {
    val total=groups.sumOf{it.coerceAtLeast(0).toDouble()}
    return groups.map{if(total==0.0)0f else (it.coerceAtLeast(0)/total*180).toFloat()}
}

/** All styles share their frame, including Home, previews and the loading skeleton. */
@Composable
fun LibraryDashboard(
    usage: LibraryOverview,
    onAdd: () -> Unit,
    showAddAction: Boolean = true,
    loading: Boolean = false,
    updatedLabel: String = "No uploads yet",
    style:String=LocalDashboardStyle.current,
    dark:Boolean=LocalDashboardDark.current?: (style in setOf("ORBIT","RIBBON"))
) {
    StyledLibraryDashboard(style,usage,loading,updatedLabel,dark)
    if(showAddAction)Button(onAdd,Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=52.dp),shape=CircleShape){
        Icon(DriveIcons.Add,null);Spacer(Modifier.width(8.dp));Text("Add files")
    }
}

/** The same layout is used by Home and its skeleton, including large text wrapping. */
@Composable
fun LibraryBrowse(shortcuts: List<LibraryShortcut>, loading: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Browse your library", Modifier.skeleton(loading), style = MaterialTheme.typography.titleLarge)
        shortcuts.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { shortcut ->
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled = !loading, onClick = shortcut.onClick)
                        .heightIn(min = 64.dp).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(shortcut.icon, null, Modifier.size(24.dp).skeleton(loading),
                            tint = MaterialTheme.colorScheme.onSurface)
                        Text(shortcut.label, Modifier.weight(1f).skeleton(loading), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

internal fun formatLibraryBytes(bytes:Long):String {
    val units=listOf("B","KB","MB","GB","TB")
    var amount=bytes.coerceAtLeast(0).toDouble();var index=0
    while(amount>=1024&&index<units.lastIndex){amount/=1024;index++}
    val rounded=kotlin.math.round(amount*10).toLong()
    return if(index==0)"${bytes.coerceAtLeast(0)} B" else "${rounded/10}.${rounded%10} ${units[index]}"
}

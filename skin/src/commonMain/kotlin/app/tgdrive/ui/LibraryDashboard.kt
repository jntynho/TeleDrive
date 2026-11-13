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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class LibraryShortcut(val label:String,val icon:ImageVector,val onClick:()->Unit)
data class LibraryOverview(val count:Int,val bytes:Long,val groups:List<Long>,val offlineBytes:Long=0,val lockedVaults:Int=0)

/** Retained for callers that calculate category proportions; these are not storage quotas. */
fun libraryArcShares(groups:List<Long>):List<Float> {
    val total=groups.sumOf{it.coerceAtLeast(0).toDouble()}
    return groups.map{if(total==0.0)0f else (it.coerceAtLeast(0)/total*180).toFloat()}
}

private val SandInk = Color(0xFF29251F)

/** A storage summary, with navigation kept in the six destinations below it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryDashboard(
    usage: LibraryOverview,
    onAdd: () -> Unit,
    showAddAction: Boolean = true,
    loading: Boolean = false,
    updatedLabel: String = "No uploads yet",
    style:String=LocalDashboardStyle.current
) {
    if(style in setOf("ORBIT","MOSAIC","RIBBON","AURORA","COMPACT")) {
        AlternativeLibraryDashboard(style,usage,loading,updatedLabel)
        if(showAddAction)Button(onAdd,Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=52.dp),shape=CircleShape){Icon(DriveIcons.Add,null);Spacer(Modifier.width(8.dp));Text("Add files")}
        return
    }
    CompositionLocalProvider(LocalContentColor provides SandInk) {
        Column(
            Modifier.fillMaxWidth().testTag("library-card")
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF2E2CE), Color(0xFFE8D0B6))))
                .drawBehind {
                    // A quiet paper fold stays at the edge, away from the information.
                    val fold = Path().apply {
                        moveTo(size.width * .94f, size.height)
                        quadraticTo(size.width * .89f, size.height * .65f, size.width, size.height * .40f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(fold, Color.White.copy(alpha = .18f))
                }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Your library", Modifier.weight(1f).skeleton(loading), style = MaterialTheme.typography.titleMedium)
                Icon(DriveIcons.Folder, null, Modifier.size(24.dp).skeleton(loading))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (loading) "24.8 GB" else formatLibraryBytes(usage.bytes), Modifier.skeleton(loading),
                    style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Stored in Telegram", Modifier.skeleton(loading), style = MaterialTheme.typography.bodyMedium,
                    color = SandInk.copy(alpha = .75f))
            }
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(SandInk.copy(alpha = .055f)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (loading) "1,284 files" else "${usage.count} ${if (usage.count == 1) "file" else "files"}",
                    Modifier.skeleton(loading), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(DriveIcons.History, null, Modifier.size(16.dp).skeleton(loading))
                    Text(updatedLabel, Modifier.skeleton(loading), style = MaterialTheme.typography.labelMedium,
                        color = SandInk.copy(alpha = .75f))
                }
            }
        }
    }
    if (showAddAction) Button(onAdd, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp), shape = CircleShape) {
        Icon(DriveIcons.Add, null); Spacer(Modifier.width(8.dp)); Text("Add files")
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

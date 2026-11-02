package app.tgdrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class LibraryOverview(val count:Int,val bytes:Long,val groups:List<Long>,val offlineBytes:Long=0,val lockedVaults:Int=0)

private val usageLabels=listOf("Photos","Videos","Audio","Files")
private val usageColors=listOf(Color(0xFF37C8F0),Color(0xFF0087FF),Color(0xFFC49AF5),Color(0xFF969FA8))

/** Each angle is a share of indexed bytes, never an inferred Telegram storage quota. */
fun libraryArcShares(groups:List<Long>):List<Float> {
    val total=groups.sumOf{it.coerceAtLeast(0).toDouble()}
    return groups.map{if(total==0.0)0f else (it.coerceAtLeast(0)/total*180).toFloat()}
}

@Composable
fun LibraryDashboard(usage:LibraryOverview,onAdd:()->Unit,showAddAction:Boolean=true) {
    var details by remember{mutableStateOf(false)}
    val groups=List(4){usage.groups.getOrElse(it){0L}.coerceAtLeast(0)}
    DashboardCard(onClick={details=true},modifier=Modifier.fillMaxWidth().semantics{contentDescription="About library storage"}) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Your library",style=MaterialTheme.typography.titleMedium)
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(formatBytes(usage.bytes),style=MaterialTheme.typography.displaySmall)
                Text("${usage.count} files",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val shares=remember(groups){libraryArcShares(groups)}
            val emptyColor=MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(12.dp).clip(DriveStyle.PillShape).semantics{contentDescription="File sizes by category; not a storage limit"}) {
                if(groups.sum()==0L) drawRect(emptyColor)
                else { var x=0f; shares.forEachIndexed { index,share ->
                    val width=size.width*share/180f
                    if(width>0)drawRect(usageColors[index],Offset(x,0f),androidx.compose.ui.geometry.Size(width,size.height))
                    x+=width
                } }
            }
            BoxWithConstraints {
                val stacked=maxWidth<280.dp||LocalDensity.current.fontScale>1.25f
                if(stacked) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf(0 to 1,2 to 3).forEach{(a,b)->Row(Modifier.fillMaxWidth()){
                        Box(Modifier.weight(1f)){UsageLegend(a,groups[a])};Box(Modifier.weight(1f)){UsageLegend(b,groups[b])}
                    }}
                } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){groups.indices.forEach{UsageLegend(it,groups[it])}}
            }
        }
    }
    if(showAddAction)Button(onAdd,Modifier.fillMaxWidth().heightIn(min=52.dp).padding(top=8.dp),shape=DriveStyle.PillShape){Icon(DriveIcons.Add,null);Spacer(Modifier.width(8.dp));Text("Add files")}
    if(details)AlertDialog(onDismissRequest={details=false},title={Text("Your indexed library")},text={Text("These sizes describe your visible active files, not a Telegram storage limit. Trash, previous versions and locked vaults are excluded.\n\n${formatBytes(usage.offlineBytes)} is stored offline on this device.",Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton({details=false}){Text("Done")}})
}

@Composable private fun UsageLegend(index:Int,bytes:Long) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).background(usageColors[index],CircleShape))
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(usageLabels[index],style=MaterialTheme.typography.labelMedium)
            Text(formatBytes(bytes),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun DashboardCard(onClick:()->Unit,modifier:Modifier,content:@Composable ()->Unit) {
    Card(onClick,modifier,shape=DriveStyle.CardShape,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){content()}
}
private fun formatBytes(bytes:Long):String {
    val units=listOf("B","KB","MB","GB","TB")
    var amount=bytes.coerceAtLeast(0).toDouble();var index=0
    while(amount>=1024&&index<units.lastIndex){amount/=1024;index++}
    val rounded=kotlin.math.round(amount*10).toLong()
    return if(index==0)"${bytes.coerceAtLeast(0)} B" else "${rounded/10}.${rounded%10} ${units[index]}"
}

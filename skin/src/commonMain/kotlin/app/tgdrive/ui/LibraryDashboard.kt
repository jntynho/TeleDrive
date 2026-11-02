package app.tgdrive.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
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

data class LibraryShortcut(val label:String,val icon:ImageVector,val onClick:()->Unit)

data class LibraryOverview(val count:Int,val bytes:Long,val groups:List<Long>,val offlineBytes:Long=0,val lockedVaults:Int=0)

private val usageLabels=listOf("Photos","Videos","Audio","Files")
private val usageColors=listOf(Color(0xFF37C8F0),Color(0xFF0087FF),Color(0xFFC49AF5),Color(0xFF969FA8))

/** Each angle is a share of indexed bytes, never an inferred Telegram storage quota. */
fun libraryArcShares(groups:List<Long>):List<Float> {
    val total=groups.sumOf{it.coerceAtLeast(0).toDouble()}
    return groups.map{if(total==0.0)0f else (it.coerceAtLeast(0)/total*180).toFloat()}
}

@Composable
fun LibraryDashboard(usage:LibraryOverview,onAdd:()->Unit,showAddAction:Boolean=true,
    shortcuts:List<LibraryShortcut> = emptyList(),loading:Boolean=false
) {
    var details by remember{mutableStateOf(false)}
    var collections by rememberSaveable{mutableStateOf(false)}
    val groups=List(4){usage.groups.getOrElse(it){0L}.coerceAtLeast(0)}
    val opacity by animateFloatAsState(if(collections)0f else 1f,tween(260),label="libraryFace")
    Card(modifier=Modifier.fillMaxWidth().testTag("library-card"),shape=DriveStyle.CardShape,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().heightIn(min=if(shortcuts.isEmpty())0.dp else 224.dp)) {
            val front=Modifier.fillMaxWidth().graphicsLayer{alpha=opacity}

                .then(if(collections)Modifier.clearAndSetSemantics{}else Modifier)
            Column(front.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.padding(end=if(shortcuts.isEmpty())0.dp else 40.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("Your library",Modifier.weight(1f).skeleton(loading),style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick={details=true},enabled=!collections&&!loading,modifier=Modifier.size(48.dp)){Icon(DriveIcons.Info,"About library storage",Modifier.size(20.dp).skeleton(loading))}
                }
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(if(loading)"42.8 GB"else formatBytes(usage.bytes),Modifier.skeleton(loading),style=MaterialTheme.typography.displaySmall,maxLines=1)
                    Text(if(loading)"1,284 files"else "${usage.count} files",Modifier.skeleton(loading),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val shares=remember(groups){libraryArcShares(groups)}
                val emptyColor=MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().height(12.dp).clip(DriveStyle.PillShape).skeleton(loading).semantics{contentDescription="File sizes by category; not a storage limit"}) {
                    if(groups.sum()==0L)drawRect(emptyColor)
                    else {var x=0f;shares.forEachIndexed{index,share->
                        val width=size.width*share/180f
                        if(width>0)drawRect(usageColors[index],Offset(x,0f),androidx.compose.ui.geometry.Size(width,size.height))
                        x+=width
                    }}
                }
                BoxWithConstraints {
                    val stacked=maxWidth<280.dp||LocalDensity.current.fontScale>1.25f
                    if(stacked)Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        listOf(0 to 1,2 to 3).forEach{(a,b)->Row(Modifier.fillMaxWidth()){
                            Box(Modifier.weight(1f)){UsageLegend(a,groups[a],loading)}
                            Box(Modifier.weight(1f)){UsageLegend(b,groups[b],loading)}
                        }}
                    }else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){groups.indices.forEach{UsageLegend(it,groups[it],loading)}}
                }
            }
            CollectionsFace(collections,shortcuts,Modifier.matchParentSize())
            if(shortcuts.isNotEmpty())IconButton({collections=!collections},enabled=!loading,modifier=Modifier.align(Alignment.TopEnd).padding(4.dp)){
                Icon(if(collections)DriveIcons.Storage else DriveIcons.GridView,if(collections)"Show library overview"else"Show collections",Modifier.size(22.dp).skeleton(loading))
            }
        }
    }
    if(showAddAction)Button(onAdd,Modifier.fillMaxWidth().heightIn(min=52.dp).padding(top=8.dp),shape=DriveStyle.PillShape){Icon(DriveIcons.Add,null);Spacer(Modifier.width(8.dp));Text("Add files")}
    if(details)AlertDialog(onDismissRequest={details=false},title={Text("Your indexed library")},text={Text("These sizes describe your visible active files, not a Telegram storage limit. Trash, previous versions and locked vaults are excluded.\n\n${formatBytes(usage.offlineBytes)} is stored offline on this device.",Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton({details=false}){Text("Done")}})
}

@Composable private fun UsageLegend(index:Int,bytes:Long,loading:Boolean=false) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(11.dp).skeleton(loading).background(usageColors[index],CircleShape))
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(usageLabels[index],Modifier.skeleton(loading),style=MaterialTheme.typography.labelMedium)
            Text(if(loading)"7.2 GB"else formatBytes(bytes),Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun CollectionsFace(visible:Boolean,shortcuts:List<LibraryShortcut>,modifier:Modifier){
    AnimatedVisibility(visible,modifier=modifier,enter=fadeIn(tween(260)),exit=fadeOut(tween(160))){
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("Your shortcuts",Modifier.padding(end=40.dp),style=MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    shortcuts.forEach{shortcut->
                        Row(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled=visible,onClick=shortcut.onClick).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Icon(shortcut.icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
                            Text(shortcut.label,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                            Icon(DriveIcons.KeyboardArrowRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
    }
}

package app.tgdrive.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class LibraryShortcut(val label:String,val icon:ImageVector,val onClick:()->Unit)
data class LibraryOverview(val count:Int,val bytes:Long,val groups:List<Long>,val offlineBytes:Long=0,val lockedVaults:Int=0)

/** Retained for callers that calculate category proportions; these are not storage quotas. */
fun libraryArcShares(groups:List<Long>):List<Float> {
    val total=groups.sumOf{it.coerceAtLeast(0).toDouble()}
    return groups.map{if(total==0.0)0f else (it.coerceAtLeast(0)/total*180).toFloat()}
}

private val PassInk=Color(0xFF241F2E)
private val PassShape=RoundedCornerShape(topStart=12.dp,topEnd=32.dp,bottomEnd=12.dp,bottomStart=32.dp)

/** Both faces stay measured. Only draw properties animate, so surrounding rows never move. */
@Composable
fun LibraryDashboard(usage:LibraryOverview,onAdd:()->Unit,showAddAction:Boolean=true,
    shortcuts:List<LibraryShortcut> = emptyList(),loading:Boolean=false,onBrowse:(()->Unit)?=null
) {
    var collections by rememberSaveable{mutableStateOf(false)}
    val showingCollections=collections&&shortcuts.isNotEmpty()
    val progress by animateFloatAsState(if(showingCollections)1f else 0f,
        tween(340,easing=FastOutSlowInEasing),label="libraryFace")
    val travel=with(LocalDensity.current){8.dp.toPx()}
    val stackOffset=with(LocalDensity.current){6.dp.toPx()}
    CompositionLocalProvider(LocalContentColor provides PassInk) {
        Box(Modifier.fillMaxWidth().testTag("library-card").padding(bottom=6.dp)) {
            Box(Modifier.matchParentSize().padding(horizontal=6.dp).graphicsLayer{translationY=stackOffset}
                .background(Color(0xFFB7A1D3),PassShape))
            Column(Modifier.fillMaxWidth().clip(PassShape).background(Brush.linearGradient(
                listOf(Color(0xFFFFE5C3),Color(0xFFF0B8D3),Color(0xFFB7ACE8))))
                .drawBehind {
                    val fold=Path().apply {
                        moveTo(size.width*.68f,size.height)
                        lineTo(size.width*.84f,size.height*.36f)
                        lineTo(size.width,size.height*.26f)
                        lineTo(size.width,size.height)
                        close()
                    }
                    drawPath(fold,PassInk.copy(alpha=.035f))
                }.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Text("Your library",Modifier.graphicsLayer{alpha=1-progress}
                            .then(if(showingCollections)Modifier.clearAndSetSemantics{}else Modifier).skeleton(loading),
                            style=MaterialTheme.typography.titleMedium)
                        Text("Your collections",Modifier.graphicsLayer{alpha=progress}
                            .then(if(!showingCollections)Modifier.clearAndSetSemantics{}else Modifier).skeleton(loading),
                            style=MaterialTheme.typography.titleMedium)
                    }
                    if(shortcuts.isNotEmpty()) TextButton(onClick={collections=!collections},enabled=!loading,
                        modifier=Modifier.width(146.dp).heightIn(min=48.dp).semantics {
                            contentDescription=if(showingCollections)"Show library overview"else"Show collections"
                        },colors=ButtonDefaults.textButtonColors(contentColor=PassInk,disabledContentColor=PassInk.copy(alpha=.4f))) {
                        Icon(if(showingCollections)DriveIcons.ArrowBack else DriveIcons.GridView,null,Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if(showingCollections)"Overview"else"Collections",style=MaterialTheme.typography.labelMedium)
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min=132.dp),contentAlignment=Alignment.Center) {
                    Column(Modifier.fillMaxWidth().graphicsLayer{alpha=1-progress;translationX=-travel*progress}
                        .then(if(showingCollections)Modifier.clearAndSetSemantics{}else Modifier)) {
                        val compact=LocalDensity.current.fontScale>1.3f
                        Text(if(loading)"42.8 GB"else formatBytes(usage.bytes),Modifier.skeleton(loading),
                            style=if(compact)MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(if(loading)"1,284 files"else "${usage.count} files",Modifier.skeleton(loading),
                            style=MaterialTheme.typography.bodyMedium,color=PassInk.copy(alpha=.75f))
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color=PassInk.copy(alpha=.2f))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(enabled=!loading&&!showingCollections&&onBrowse!=null){onBrowse?.invoke()}
                            .heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(DriveIcons.CloudQueue,null,Modifier.size(22.dp).skeleton(loading))
                            Text("Explore your files",Modifier.weight(1f).padding(horizontal=10.dp).skeleton(loading),
                                style=MaterialTheme.typography.bodyMedium)
                            Icon(DriveIcons.ChevronRight,null,Modifier.size(22.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().graphicsLayer{alpha=progress;translationX=travel*(1-progress)}
                        .then(if(!showingCollections)Modifier.clearAndSetSemantics{}else Modifier),
                        horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                        shortcuts.forEach { shortcut ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .clickable(enabled=showingCollections&&!loading,onClick=shortcut.onClick)
                                .heightIn(min=112.dp).padding(horizontal=4.dp,vertical=16.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Icon(shortcut.icon,null,Modifier.size(28.dp))
                                Text(shortcut.label,style=MaterialTheme.typography.labelMedium,
                                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
    if(showAddAction)Button(onAdd,Modifier.fillMaxWidth().heightIn(min=52.dp).padding(top=8.dp),shape=CircleShape){
        Icon(DriveIcons.Add,null);Spacer(Modifier.width(8.dp));Text("Add files")
    }
}

/** Category destinations are outside the pass so they stay available on either face. */
@Composable
fun LibraryCategories(groups:List<Long>,onOpen:(Int)->Unit,loading:Boolean=false) {
    val labels=listOf("Photos","Videos","Audio","Files")
    val colors=listOf(Color(0xFF77D2D0),Color(0xFF84C2F4),Color(0xFFD4AFE9),Color(0xFFE7D3A3))
    val icons=listOf(DriveIcons.Image,DriveIcons.PlayCircle,DriveIcons.MusicNotes,DriveIcons.Description)
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Browse your library",Modifier.skeleton(loading),style=MaterialTheme.typography.titleLarge)
        repeat(2){row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                repeat(2){column->
                    val index=row*2+column
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled=!loading){onOpen(index)}.heightIn(min=64.dp).padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(colors[index].copy(alpha=.15f)),contentAlignment=Alignment.Center) {
                            Icon(icons[index],null,Modifier.size(23.dp).skeleton(loading),tint=if(MaterialTheme.colorScheme.background.luminance()>.5f)MaterialTheme.colorScheme.primary else colors[index])
                        }
                        Column(Modifier.weight(1f)) {
                            Text(labels[index],Modifier.skeleton(loading),style=MaterialTheme.typography.labelLarge)
                            Text(if(loading)"18.6 GB"else formatBytes(groups.getOrElse(index){0}),Modifier.skeleton(loading),
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes:Long):String {
    val units=listOf("B","KB","MB","GB","TB")
    var amount=bytes.coerceAtLeast(0).toDouble();var index=0
    while(amount>=1024&&index<units.lastIndex){amount/=1024;index++}
    val rounded=kotlin.math.round(amount*10).toLong()
    return if(index==0)"${bytes.coerceAtLeast(0)} B" else "${rounded/10}.${rounded%10} ${units[index]}"
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.tgdrive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer

val LocalDashboardStyle=staticCompositionLocalOf{"CLASSIC"}
val LocalDashboardDark=staticCompositionLocalOf<Boolean?>{null}
private val categoryNames=listOf("Photos","Videos","Audio","Files")
private data class DashboardPalette(val top:Color,val bottom:Color,val ink:Color)
private fun palette(style:String,dark:Boolean):DashboardPalette {
    val shades=when(style){
        "ORBIT"->if(dark)0xFF1B3048L to 0xFF294D68L else 0xFFDFECF9L to 0xFFC7DEEDL
        "MOSAIC"->if(dark)0xFF183C33L to 0xFF214D41L else 0xFFE0F1E9L to 0xFFC9E7DBL
        "RIBBON"->if(dark)0xFF282339L to 0xFF40334DL else 0xFFF1E6F7L to 0xFFE2D5EFL
        "HORIZON"->if(dark)0xFF233347L to 0xFF344C63L else 0xFFE8EFF8L to 0xFFCEDFEBL
        "STRATA"->if(dark)0xFF273236L to 0xFF354347L else 0xFFE6EEEBL to 0xFFD2E2DDL
        else->if(dark)0xFF37302AL to 0xFF4A3B2FL else 0xFFF2E2CEL to 0xFFE8D0B6L
    }
    return DashboardPalette(Color(shades.first),Color(shades.second),if(dark)Color(0xFFF8F3EE)else Color(0xFF25322F))
}
private fun categoryColours(dark:Boolean)=if(dark)listOf(Color(0xFF7EBCFF),Color(0xFFC4A5F6),Color(0xFFFFC57A),Color(0xFF88D7B1))
    else listOf(Color(0xFF3973AD),Color(0xFF8663AE),Color(0xFFB97632),Color(0xFF368468))

/** Every style uses the measured height of the original Classic layout. */
@Composable
internal fun StyledLibraryDashboard(style:String,usage:LibraryOverview,loading:Boolean,updated:String,dark:Boolean){
    val canonical=when(style){"AURORA"->"HORIZON";"COMPACT"->"STRATA";else->style}
    val palette=palette(canonical,dark)
    val top by animateColorAsState(palette.top,tween(240),label="dashboardTop")
    val bottom by animateColorAsState(palette.bottom,tween(240),label="dashboardBottom")
    val ink by animateColorAsState(palette.ink,tween(240),label="dashboardInk")
    val colours=categoryColours(dark)
    CompositionLocalProvider(LocalContentColor provides ink){
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val frameHeight=originalClassicHeight(maxWidth,usage,loading,updated)
            Column(Modifier.fillMaxWidth().height(frameHeight).testTag("library-card")
                .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(top,bottom)))
                .drawBehind{
                    if(canonical=="CLASSIC"){
                        val fold=Path().apply{moveTo(size.width*.94f,size.height);quadraticTo(size.width*.89f,size.height*.65f,size.width,size.height*.40f);lineTo(size.width,size.height);close()}
                        drawPath(fold,Color.White.copy(alpha=if(dark).04f else .18f))
                    }
                }.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("Your library",Modifier.weight(1f).skeleton(loading),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Icon(DriveIcons.Folder,null,Modifier.size(24.dp).skeleton(loading))
                }
                Column(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.Center){
                    when(canonical){
                        "ORBIT"->OrbitBody(usage,loading,colours)
                        "MOSAIC"->MosaicBody(usage,loading)
                        "RIBBON"->RibbonBody(usage,loading,colours)
                        "HORIZON"->HorizonBody(usage,loading,colours)
                        "STRATA"->StrataBody(usage,loading,colours)
                        else->StorageValue(usage,loading,large=true)
                    }
                }
                DashboardFootnote(usage,loading,updated)
            }
        }
    }
}
@Composable
private fun StorageValue(usage:LibraryOverview,loading:Boolean,large:Boolean=false,caption:Boolean=true,compact:Boolean=false){
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(if(loading)"24.8 GB"else formatLibraryBytes(usage.bytes),Modifier.skeleton(loading),
            style=when{
                LocalDensity.current.fontScale>1.3f||compact->MaterialTheme.typography.headlineSmall
                large->MaterialTheme.typography.displayMedium
                else->MaterialTheme.typography.headlineMedium
            },
            maxLines=1,overflow=TextOverflow.Ellipsis)
        if(caption)Text("Stored in Telegram",Modifier.skeleton(loading),style=MaterialTheme.typography.bodySmall,color=LocalContentColor.current.copy(alpha=.8f),maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
@Composable
private fun DashboardFootnote(usage:LibraryOverview,loading:Boolean,updated:String){
    FlowRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalContentColor.current.copy(alpha=.055f)).padding(horizontal=10.dp,vertical=8.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(if(loading)"1,284 files"else"${usage.count} ${if(usage.count==1)"file"else"files"}",Modifier.skeleton(loading),style=MaterialTheme.typography.labelLarge)
        Text(updated,Modifier.skeleton(loading),style=MaterialTheme.typography.labelMedium,color=LocalContentColor.current.copy(alpha=.8f))
    }
}
@Composable
private fun CategoryLegend(loading:Boolean,colours:List<Color>){
    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        categoryNames.forEachIndexed{index,label->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
            Box(Modifier.size(8.dp).clip(CircleShape).background(colours[index]))
            Text(label,Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall)
        }}
    }
}
@Composable
private fun OrbitBody(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    val ring:@Composable ()->Unit={CategoryRing(usage,loading,colours)}
    if(LocalDensity.current.fontScale>1.3f){
        StorageValue(usage,loading,caption=false);Spacer(Modifier.height(8.dp))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ring();Box(Modifier.weight(1f)){CategoryLegend(loading,colours)}
        }
        return
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.weight(1f)){StorageValue(usage,loading)}
        ring()
    }
    Spacer(Modifier.height(6.dp));CategoryLegend(loading,colours)
}
@Composable
private fun CategoryRing(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    Canvas(Modifier.size(if(LocalDensity.current.fontScale>1.3f)64.dp else 56.dp).skeleton(loading)){
            val stroke=6.dp.toPx();val inset=stroke/2;val bounds=Size(size.width-stroke,size.height-stroke)
            drawArc(colours.first().copy(alpha=.15f),0f,360f,false,Offset(inset,inset),bounds,style=Stroke(stroke))
            var start=-90f
            libraryArcShares(usage.groups).take(4).forEachIndexed{index,halfSweep->
                val sweep=halfSweep*2
                if(sweep>2)drawArc(colours[index],start,sweep-2,false,Offset(inset,inset),bounds,style=Stroke(stroke))
                start+=sweep
            }
    }
}
@Composable
private fun MosaicBody(usage:LibraryOverview,loading:Boolean){
    val large=LocalDensity.current.fontScale>1.3f
    val icons=listOf(DriveIcons.Photo,DriveIcons.PlayArrow,DriveIcons.MusicNote,DriveIcons.Description)
    val tiles:@Composable ()->Unit={
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            repeat(2){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                repeat(2){column->val index=row*2+column
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(LocalContentColor.current.copy(alpha=.065f)).padding(4.dp),
                        horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(icons[index],categoryNames[index],Modifier.size(14.dp).skeleton(loading))
                        Text(formatLibraryBytes(usage.groups.getOrElse(index){0}),Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }}
        }
    }
    if(large){StorageValue(usage,loading,caption=false);Spacer(Modifier.height(8.dp));tiles()}
    else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f)){StorageValue(usage,loading,caption=false,compact=true);Text("Total",style=MaterialTheme.typography.bodySmall)}
        Box(Modifier.weight(1f)){tiles()}
    }
}
@Composable
private fun RibbonBody(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    StorageValue(usage,loading,caption=false);Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(LocalContentColor.current.copy(alpha=.12f)).skeleton(loading),horizontalArrangement=Arrangement.spacedBy(3.dp)){
        usage.groups.take(4).forEachIndexed{index,value->if(value>0)Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(colours[index]))}
    }
    Spacer(Modifier.height(6.dp));CategoryLegend(loading,colours)
}
@Composable
private fun HorizonBody(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.weight(1f)){StorageValue(usage,loading,large=true)}
        if(LocalDensity.current.fontScale<=1.3f){
            Box(Modifier.size(64.dp).skeleton(loading),contentAlignment=Alignment.Center){
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(LocalContentColor.current.copy(alpha=.06f)))
                Icon(DriveIcons.Folder,null,Modifier.size(42.dp))
                Box(Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(colours.first()),contentAlignment=Alignment.Center){
                    Icon(DriveIcons.CloudQueue,null,Modifier.size(18.dp),tint=Color.White)
                }
            }
        }
    }
}
@Composable
private fun StrataBody(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    val large=LocalDensity.current.fontScale>1.3f
    val rows:@Composable ()->Unit={
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)){
            categoryNames.forEachIndexed{index,label->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colours[index].copy(alpha=.10f)).padding(horizontal=5.dp,vertical=1.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    Box(Modifier.size(5.dp).clip(CircleShape).background(colours[index]))
                    Text(label,Modifier.weight(1f).skeleton(loading),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(formatLibraryBytes(usage.groups.getOrElse(index){0}),Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
            }
        }
    }
    if(large){StorageValue(usage,loading,caption=false);Spacer(Modifier.height(8.dp));rows()}
    else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(.9f)){StorageValue(usage,loading,caption=false,compact=true);Text("Total",style=MaterialTheme.typography.bodySmall)}
        Box(Modifier.weight(1.1f)){rows()}
    }
}

/** Measure the original Classic's typography and wrapping, instead of inventing a taller frame. */
@Composable
private fun originalClassicHeight(width:Dp,usage:LibraryOverview,loading:Boolean,updated:String):Dp {
    val density=LocalDensity.current
    val measure=rememberTextMeasurer()
    val type=MaterialTheme.typography
    fun size(text:String,style:androidx.compose.ui.text.TextStyle,available:Dp,lines:Int=Int.MAX_VALUE)=measure.measure(text,style,maxLines=lines,
        constraints=Constraints(maxWidth=with(density){available.coerceAtLeast(1.dp).roundToPx()})).size
    fun height(px:Int)=with(density){px.toDp()}
    val inside=width-40.dp
    val title=maxOf(24.dp,height(size("Your library",type.titleMedium,inside-24.dp).height))
    val number=height(size(if(loading)"24.8 GB"else formatLibraryBytes(usage.bytes),
        if(density.fontScale>1.3f)type.headlineLarge else type.displayMedium,inside,1).height)
    val caption=height(size("Stored in Telegram",type.bodyMedium,inside).height)
    val footerWidth=inside-24.dp
    val count=size(if(loading)"1,284 files"else"${usage.count} ${if(usage.count==1)"file"else"files"}",type.labelLarge,footerWidth)
    val time=size(updated,type.labelMedium,footerWidth-22.dp)
    val timeHeight=maxOf(16.dp,height(time.height))
    val footer=if(height(count.width)+16.dp+22.dp+height(time.width)<=footerWidth)maxOf(height(count.height),timeHeight)
        else height(count.height)+8.dp+timeHeight
    return 40.dp+32.dp+title+number+4.dp+caption+footer+20.dp
}

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

val LocalDashboardStyle=staticCompositionLocalOf{"CLASSIC"}
val LocalDashboardDark=staticCompositionLocalOf<Boolean?>{null}
private val categoryNames=listOf("Photos","Videos","Audio","Files")
private data class DashboardPalette(val top:Color,val bottom:Color,val ink:Color)
private fun palette(style:String,dark:Boolean):DashboardPalette {
    val shades=when(style){
        "ORBIT"->if(dark)0xFF1B3048L to 0xFF294D68L else 0xFFDFECF9L to 0xFFC7DEEDL
        "MOSAIC"->if(dark)0xFF183C33L to 0xFF214D41L else 0xFFE0F1E9L to 0xFFC9E7DBL
        "RIBBON"->if(dark)0xFF282339L to 0xFF40334DL else 0xFFF1E6F7L to 0xFFE2D5EFL
        "HORIZON"->if(dark)0xFF352C31L to 0xFF4A3639L else 0xFFF9EDE3L to 0xFFEFD6C9L
        "STRATA"->if(dark)0xFF273236L to 0xFF354347L else 0xFFE6EEEBL to 0xFFD2E2DDL
        else->if(dark)0xFF37302AL to 0xFF4A3B2FL else 0xFFF2E2CEL to 0xFFE8D0B6L
    }
    return DashboardPalette(Color(shades.first),Color(shades.second),if(dark)Color(0xFFF8F3EE)else Color(0xFF25322F))
}
private fun categoryColours(dark:Boolean)=if(dark)listOf(Color(0xFF7EBCFF),Color(0xFFC4A5F6),Color(0xFFFFC57A),Color(0xFF88D7B1))
    else listOf(Color(0xFF3973AD),Color(0xFF8663AE),Color(0xFFB97632),Color(0xFF368468))

/** Height depends on width and accessibility text size, never the style, loading state or data. */
@Composable
internal fun StyledLibraryDashboard(style:String,usage:LibraryOverview,loading:Boolean,updated:String,dark:Boolean){
    val canonical=when(style){"AURORA"->"HORIZON";"COMPACT"->"STRATA";else->style}
    val palette=palette(canonical,dark)
    val top by animateColorAsState(palette.top,tween(240),label="dashboardTop")
    val bottom by animateColorAsState(palette.bottom,tween(240),label="dashboardBottom")
    val ink by animateColorAsState(palette.ink,tween(240),label="dashboardInk")
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    val colours=categoryColours(dark)
    CompositionLocalProvider(LocalContentColor provides ink){
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val frameHeight=(320+220*(fontScale-1f)).dp+if(maxWidth<280.dp)24.dp else 0.dp
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
private fun StorageValue(usage:LibraryOverview,loading:Boolean,large:Boolean=false){
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(if(loading)"24.8 GB"else formatLibraryBytes(usage.bytes),Modifier.skeleton(loading),
            style=when{
                LocalDensity.current.fontScale>1.3f->MaterialTheme.typography.headlineSmall
                large->MaterialTheme.typography.displayMedium
                else->MaterialTheme.typography.headlineMedium
            },
            maxLines=1,overflow=TextOverflow.Ellipsis)
        Text("Stored in Telegram",Modifier.skeleton(loading),style=MaterialTheme.typography.bodySmall,color=LocalContentColor.current.copy(alpha=.8f),maxLines=1,overflow=TextOverflow.Ellipsis)
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
        StorageValue(usage,loading);Spacer(Modifier.height(12.dp))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ring();Box(Modifier.weight(1f)){CategoryLegend(loading,colours)}
        }
        return
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.weight(1f)){StorageValue(usage,loading)}
        ring()
    }
    Spacer(Modifier.height(12.dp));CategoryLegend(loading,colours)
}
@Composable
private fun CategoryRing(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    Canvas(Modifier.size(72.dp).skeleton(loading)){
            val stroke=8.dp.toPx();val inset=stroke/2;val bounds=Size(size.width-stroke,size.height-stroke)
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
    StorageValue(usage,loading);Spacer(Modifier.height(10.dp))
    repeat(2){row->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            repeat(2){column->val index=row*2+column
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(LocalContentColor.current.copy(alpha=.065f)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                    Text(categoryNames[index],Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(formatLibraryBytes(usage.groups.getOrElse(index){0}),Modifier.skeleton(loading),style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        if(row==0)Spacer(Modifier.height(6.dp))
    }
}
@Composable
private fun RibbonBody(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    StorageValue(usage,loading);Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(LocalContentColor.current.copy(alpha=.12f)).skeleton(loading),horizontalArrangement=Arrangement.spacedBy(3.dp)){
        usage.groups.take(4).forEachIndexed{index,value->if(value>0)Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(colours[index]))}
    }
    Spacer(Modifier.height(12.dp));CategoryLegend(loading,colours)
}
@Composable
private fun HorizonBody(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    val largeText=LocalDensity.current.fontScale>1.3f
    StorageValue(usage,loading);Spacer(Modifier.height(12.dp))
    val values=(0..3).map{usage.groups.getOrElse(it){0}.coerceAtLeast(0)}
    val peak=values.maxOrNull()?.coerceAtLeast(1)?:1
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom){
        values.forEachIndexed{index,bytes->
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(topStart=12.dp,topEnd=12.dp,bottomStart=4.dp,bottomEnd=4.dp))
                    .background(colours[index].copy(alpha=.12f)).skeleton(loading),contentAlignment=Alignment.BottomCenter){
                    Box(Modifier.fillMaxWidth().fillMaxHeight((bytes.toDouble()/peak).toFloat()).background(Brush.verticalGradient(listOf(colours[index].copy(alpha=.45f),colours[index]))))
                }
                if(!largeText)Text(categoryNames[index],Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
    if(largeText){Spacer(Modifier.height(8.dp));CategoryLegend(loading,colours)}
}
@Composable
private fun StrataBody(usage:LibraryOverview,loading:Boolean,colours:List<Color>){
    StorageValue(usage,loading);Spacer(Modifier.height(10.dp))
    categoryNames.forEachIndexed{index,label->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colours[index].copy(alpha=.10f)).padding(horizontal=8.dp,vertical=4.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Box(Modifier.size(7.dp).clip(CircleShape).background(colours[index]))
            Text(label,Modifier.weight(1f).skeleton(loading),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(formatLibraryBytes(usage.groups.getOrElse(index){0}),Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall,maxLines=1)
        }
        if(index<3)Spacer(Modifier.height(4.dp))
    }
}

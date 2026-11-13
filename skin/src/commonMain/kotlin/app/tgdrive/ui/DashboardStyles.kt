@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.tgdrive.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val LocalDashboardStyle=staticCompositionLocalOf{"CLASSIC"}
private val categoryColours=listOf(Color(0xFF7EBCFF),Color(0xFFC4A5F6),Color(0xFFFFC57A),Color(0xFF88D7B1))
private val categoryNames=listOf("Photos","Videos","Audio","Files")

@Composable
internal fun AlternativeLibraryDashboard(style:String,usage:LibraryOverview,loading:Boolean,updated:String){
    when(style){
        "ORBIT"->OrbitDashboard(usage,loading,updated)
        "MOSAIC"->MosaicDashboard(usage,loading,updated)
        "RIBBON"->RibbonDashboard(usage,loading,updated)
        "AURORA"->AuroraDashboard(usage,loading,updated)
        else->CompactDashboard(usage,loading,updated)
    }
}

@Composable
private fun DashboardSurface(colours:List<Color>,ink:Color,content:@Composable ColumnScope.()->Unit){
    CompositionLocalProvider(LocalContentColor provides ink){
        Column(Modifier.fillMaxWidth().testTag("library-card").clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colours)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}
@Composable
private fun DashboardHeading(loading:Boolean){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("Your library",Modifier.weight(1f).skeleton(loading),style=MaterialTheme.typography.titleMedium)
        Icon(DriveIcons.Folder,null,Modifier.size(24.dp).skeleton(loading))
    }
}
@Composable
private fun StorageValue(usage:LibraryOverview,loading:Boolean,large:Boolean=true){
    Text(if(loading)"24.8 GB"else formatLibraryBytes(usage.bytes),Modifier.skeleton(loading),
        style=if(large&&LocalDensity.current.fontScale<1.3f)MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
        maxLines=1,overflow=TextOverflow.Ellipsis)
}
@Composable
private fun DashboardFootnote(usage:LibraryOverview,loading:Boolean,updated:String){
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(if(loading)"1,284 files"else"${usage.count} ${if(usage.count==1)"file"else"files"}",Modifier.skeleton(loading),style=MaterialTheme.typography.labelLarge)
        Text(updated,Modifier.skeleton(loading),style=MaterialTheme.typography.labelMedium,color=LocalContentColor.current.copy(alpha=.75f))
    }
}
@Composable
private fun OrbitDashboard(usage:LibraryOverview,loading:Boolean,updated:String){
    DashboardSurface(listOf(Color(0xFF1B3048),Color(0xFF294D68)),Color(0xFFF3F7FC)){
        DashboardHeading(loading)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                StorageValue(usage,loading);Text("Stored in Telegram",style=MaterialTheme.typography.bodySmall,color=LocalContentColor.current.copy(alpha=.75f))
            }
            Canvas(Modifier.size(76.dp).skeleton(loading)){
                val stroke=8.dp.toPx();val inset=stroke/2
                drawArc(Color.White.copy(alpha=.13f),0f,360f,false,topLeft=androidx.compose.ui.geometry.Offset(inset,inset),size=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),style=Stroke(stroke))
                var start=-90f
                libraryArcShares(usage.groups).take(4).forEachIndexed{index,halfSweep->
                    val sweep=halfSweep*2
                    if(sweep>2)drawArc(categoryColours[index],start,sweep-2,false,topLeft=androidx.compose.ui.geometry.Offset(inset,inset),size=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),style=Stroke(stroke))
                    start+=sweep
                }
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            categoryNames.forEachIndexed{index,label->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColours[index]))
                Text("$label ${formatLibraryBytes(usage.groups.getOrElse(index){0})}",Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall)
            }}
        }
        DashboardFootnote(usage,loading,updated)
    }
}
@Composable
private fun MosaicDashboard(usage:LibraryOverview,loading:Boolean,updated:String){
    DashboardSurface(listOf(Color(0xFFE0F1E9),Color(0xFFC9E7DB)),Color(0xFF12382B)){
        DashboardHeading(loading);StorageValue(usage,loading)
        val icons=listOf(DriveIcons.Photo,DriveIcons.PlayArrow,DriveIcons.MusicNote,DriveIcons.Description)
        val columns=if(LocalDensity.current.fontScale>1.3f)1 else 2
        repeat(4/columns){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            repeat(columns){column->val index=row*columns+column
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha=.45f)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        Icon(icons[index],null,Modifier.size(16.dp).skeleton(loading));Text(categoryNames[index],Modifier.skeleton(loading),style=MaterialTheme.typography.labelMedium)
                    }
                    Text(formatLibraryBytes(usage.groups.getOrElse(index){0}),Modifier.skeleton(loading),style=MaterialTheme.typography.titleSmall)
                }
            }
        }}
        DashboardFootnote(usage,loading,updated)
    }
}
@Composable
private fun RibbonDashboard(usage:LibraryOverview,loading:Boolean,updated:String){
    DashboardSurface(listOf(Color(0xFF282339),Color(0xFF40334D)),Color(0xFFF9F3FF)){
        DashboardHeading(loading);StorageValue(usage,loading)
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.White.copy(alpha=.14f)).skeleton(loading),horizontalArrangement=Arrangement.spacedBy(3.dp)){
            val values=usage.groups.take(4).map{it.coerceAtLeast(0).toFloat()}
            values.forEachIndexed{index,value->if(value>0)Box(Modifier.weight(value).fillMaxHeight().background(categoryColours[index]))}
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            categoryNames.forEachIndexed{index,label->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColours[index]));Text(label,Modifier.skeleton(loading),style=MaterialTheme.typography.labelSmall)
            }}
        }
        DashboardFootnote(usage,loading,updated)
    }
}
@Composable
private fun AuroraDashboard(usage:LibraryOverview,loading:Boolean,updated:String){
    DashboardSurface(listOf(Color(0xFFDCEEFF),Color(0xFFDADFF4),Color(0xFFE9E4F6)),Color(0xFF172A46)){
        DashboardHeading(loading)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha=.35f)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Everything you keep",Modifier.skeleton(loading),style=MaterialTheme.typography.bodyMedium)
            StorageValue(usage,loading)
            Text("Original quality · Your own cloud",Modifier.skeleton(loading),style=MaterialTheme.typography.labelMedium)
        }
        DashboardFootnote(usage,loading,updated)
    }
}
@Composable
private fun CompactDashboard(usage:LibraryOverview,loading:Boolean,updated:String){
    DashboardSurface(listOf(Color(0xFFE6E9ED),Color(0xFFD2DAE2)),Color(0xFF202C38)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha=.5f)),contentAlignment=Alignment.Center){Icon(DriveIcons.Storage,null,Modifier.size(24.dp).skeleton(loading))}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text("Your library",Modifier.skeleton(loading),style=MaterialTheme.typography.labelLarge)
                StorageValue(usage,loading,large=false)
            }
        }
        DashboardFootnote(usage,loading,updated)
    }
}

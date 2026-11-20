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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        "HORIZON"->if(dark)0xFF15233CL to 0xFF294466L else 0xFFE8F2FDL to 0xFFD0E4F2L
        "STRATA"->if(dark)0xFF273236L to 0xFF354347L else 0xFFE6EEEBL to 0xFFD2E2DDL
        "SPECTRUM" -> if (dark) 0xFF321F35L to 0xFF543449L else 0xFFFBE5EEL to 0xFFEFCDE0L
        "LEDGER" -> if (dark) 0xFF212B28L to 0xFF3A473BL else 0xFFEFF1E0L to 0xFFDEE5CDL
        "BEACON" -> if (dark) 0xFF342318L to 0xFF563623L else 0xFFFFE9C5L to 0xFFF3D1A5L
        else->if(dark)0xFF37302AL to 0xFF4A3B2FL else 0xFFF2E2CEL to 0xFFE8D0B6L
    }
    return DashboardPalette(Color(shades.first),Color(shades.second),if(dark)Color(0xFFF8F3EE)else Color(0xFF25322F))
}
private fun categoryColours(dark:Boolean)=if(dark)listOf(Color(0xFF7EBCFF),Color(0xFFC4A5F6),Color(0xFFFFC57A),Color(0xFF88D7B1))
    else listOf(Color(0xFF3973AD),Color(0xFF8663AE),Color(0xFFB97632),Color(0xFF368468))

/** Every style uses the measured height of the original Classic layout. */
@Composable
internal fun StyledLibraryDashboard(
    style: String,
    usage: LibraryOverview,
    loading: Boolean,
    updated: String,
    dark: Boolean,
    modifier: Modifier,
    headerAction: (@Composable () -> Unit)?
) {
    val canonical=when(style){"AURORA"->"HORIZON";"COMPACT"->"STRATA";else->style}
    val palette=palette(canonical,dark)
    val top by animateColorAsState(palette.top,tween(240),label="dashboardTop")
    val bottom by animateColorAsState(palette.bottom,tween(240),label="dashboardBottom")
    val ink by animateColorAsState(palette.ink,tween(240),label="dashboardInk")
    val colours=categoryColours(dark)
    CompositionLocalProvider(LocalContentColor provides ink){
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val cardWidth = maxWidth
            val frameHeight = originalClassicHeight(cardWidth, usage, loading, updated)
            Box(modifier.fillMaxWidth().height(frameHeight).testTag("library-card")
                .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(top,bottom)))
                .drawBehind{
                    when (canonical) {
                        "CLASSIC" -> {
                            val fold=Path().apply{moveTo(size.width*.94f,size.height);quadraticTo(size.width*.89f,size.height*.65f,size.width,size.height*.40f);lineTo(size.width,size.height);close()}
                            drawPath(fold,Color.White.copy(alpha=if(dark).04f else .18f))
                        }
                        "BEACON" -> drawBeacon(dark)
                    }
                }) {
                Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("Your library",Modifier.weight(1f).skeleton(loading),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if (headerAction == null) Icon(DriveIcons.Folder,null,Modifier.size(24.dp).skeleton(loading))
                        else Spacer(Modifier.width(40.dp))
                    }
                    Column(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.Center) {
                        when(canonical) {
                            "ORBIT" -> OrbitBody(usage,loading,colours)
                            "MOSAIC" -> MosaicBody(usage,loading,colours,cardWidth-40.dp)
                            "RIBBON" -> RibbonBody(usage,loading,colours)
                            "HORIZON" -> HorizonBody(usage,loading,dark)
                            "STRATA" -> StrataBody(usage,loading,colours)
                            "SPECTRUM" -> SpectrumBody(usage,loading,colours)
                            "LEDGER" -> LedgerBody(usage,loading)
                            else -> StorageValue(usage,loading,large=true)
                        }
                    }
                    DashboardFootnote(usage,loading,updated)
                }
                if (headerAction != null) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)) { headerAction() }
                }
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
private fun MosaicBody(usage: LibraryOverview, loading: Boolean, colours: List<Color>, width: Dp) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val tiles: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(2) { column ->
                        val index = row * 2 + column
                        val label = categoryNames[index]
                        val amount = formatLibraryBytes(usage.groups.getOrElse(index) { 0L })
                        val colour = colours[index]
                        val description = if (largeText) Modifier.semantics { contentDescription = "$label, $amount" } else Modifier
                        Column(
                            Modifier.weight(1f).then(description).clip(RoundedCornerShape(10.dp))
                                .background(colour.copy(alpha = .14f)).padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(categoryIcon(index), null, Modifier.size(14.dp).skeleton(loading), tint = colour)
                                Text(label, Modifier.skeleton(loading), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            if (!largeText) {
                                Text(amount, Modifier.skeleton(loading), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    if (largeText || width < 245.dp) {
        StorageValue(usage, loading, caption = false, compact = true)
        Spacer(Modifier.height(5.dp))
        tiles()
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(.37f)) { StorageValue(usage, loading, compact = true) }
            Box(Modifier.weight(.63f)) { tiles() }
        }
    }
}

@Composable
private fun categoryIcon(index: Int) = when (index) {
    0 -> DriveIcons.Photo
    1 -> DriveIcons.Videocam
    2 -> DriveIcons.MusicNote
    else -> DriveIcons.Description
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
private fun HorizonBody(usage: LibraryOverview, loading: Boolean, dark: Boolean) {
    val atmosphere = if (dark) Color(0xFF81AFE1) else Color(0xFF76A9D1)
    val hills = if (dark) Color(0xFF92C5CC) else Color(0xFF3E759D)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { StorageValue(usage, loading, large = true) }
        if (LocalDensity.current.fontScale <= 1.3f) {
            Box(
                Modifier.size(width = 98.dp, height = 74.dp).skeleton(loading).clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(atmosphere.copy(alpha = .32f), atmosphere.copy(alpha = .06f)))),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(if (dark) Color(0xFFE9DAF5) else Color(0xFFFFD397), 12.dp.toPx(), Offset(size.width * .76f, size.height * .26f))
                    val ridge = Path().apply {
                        moveTo(0f, size.height * .78f)
                        quadraticTo(size.width * .24f, size.height * .49f, size.width * .52f, size.height * .74f)
                        quadraticTo(size.width * .76f, size.height * .91f, size.width, size.height * .55f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(ridge, hills.copy(alpha = .28f))
                    drawLine(hills.copy(alpha = .32f), Offset(0f, size.height * .82f), Offset(size.width, size.height * .82f), 1.dp.toPx())
                }
                Icon(DriveIcons.CloudQueue, null, Modifier.size(36.dp).offset(x = (-5).dp, y = (-2).dp))
            }
        }
    }
}

@Composable
private fun SpectrumBody(usage: LibraryOverview, loading: Boolean, colours: List<Color>) {
    val chart: @Composable () -> Unit = {
        val sizes = List(categoryNames.size) { usage.groups.getOrElse(it) { 0L }.coerceAtLeast(0L) }
        val maximum = sizes.max().coerceAtLeast(1L).toDouble()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sizes.forEachIndexed { index, amount ->
                val colour = colours[index]
                val fraction = (amount / maximum).toFloat()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(12.dp).height(46.dp).skeleton(loading).clip(CircleShape).background(colour.copy(alpha = .16f))) {
                        if (fraction > 0f) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).align(Alignment.BottomCenter).clip(CircleShape).background(colour))
                        }
                    }
                    Icon(categoryIcon(index), "${categoryNames[index]}, ${formatLibraryBytes(amount)}", Modifier.size(14.dp).skeleton(loading), tint = colour)
                }
            }
        }
    }
    if (LocalDensity.current.fontScale > 1.3f) {
        StorageValue(usage, loading, caption = false, compact = true)
        Spacer(Modifier.height(6.dp))
        chart()
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { StorageValue(usage, loading, compact = true) }
            Box(Modifier.weight(1f)) { chart() }
        }
    }
}

@Composable
private fun LedgerBody(usage: LibraryOverview, loading: Boolean) {
    val ink = LocalContentColor.current
    val amount = if (loading) "3.4 GB" else formatLibraryBytes(usage.offlineBytes)
    if (LocalDensity.current.fontScale > 1.3f) {
        StorageValue(usage, loading, compact = true)
        Spacer(Modifier.height(5.dp))
        HorizontalDivider(color = ink.copy(alpha = .2f))
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("On this device", Modifier.weight(1f).skeleton(loading), style = MaterialTheme.typography.labelSmall)
            Text(amount, Modifier.skeleton(loading), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { StorageValue(usage, loading, compact = true) }
            VerticalDivider(Modifier.height(46.dp), color = ink.copy(alpha = .2f))
            Column(Modifier.weight(.9f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(amount, Modifier.skeleton(loading), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("On this device", Modifier.skeleton(loading), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = .8f))
            }
        }
    }
}

private fun DrawScope.drawBeacon(dark: Boolean) {
    val light = if (dark) Color(0xFFEC9D58) else Color(0xFFC56A33)
    val radius = size.height * .36f
    val source = Offset(size.width * .88f, size.height * .58f)
    drawCircle(light.copy(alpha = .12f), radius, source)
    drawCircle(light.copy(alpha = .18f), radius * .72f, source, style = Stroke(1.dp.toPx()))
    drawCircle(light.copy(alpha = .26f), radius * .43f, source)
    val beams = Path().apply {
        moveTo(source.x, source.y)
        lineTo(size.width * .61f, 0f)
        lineTo(size.width * .74f, 0f)
        close()
        moveTo(source.x, source.y)
        lineTo(size.width, size.height * .23f)
        lineTo(size.width, size.height * .44f)
        close()
    }
    drawPath(beams, light.copy(alpha = .12f))
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

package app.tgdrive.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** One clock per skeleton scene; opacity is read by drawing, not recomposition. */
private val LocalSkeletonPulse=staticCompositionLocalOf<State<Float>>{mutableStateOf(.12f)}
@Composable
fun SkeletonScene(modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    val clock=rememberInfiniteTransition(label="skeletonPulse")
    val opacity=clock.animateFloat(.07f,.14f,infiniteRepeatable(tween(1100,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="skeletonOpacity")
    CompositionLocalProvider(LocalSkeletonPulse provides opacity){
        Box(modifier.clearAndSetSemantics{contentDescription="Loading content"}){content()}
    }
}
@Composable
fun Modifier.skeleton(loading:Boolean=true,radius:Dp=6.dp):Modifier {
    if(!loading)return this
    val opacity=LocalSkeletonPulse.current
    val color=MaterialTheme.colorScheme.onSurface
    return clearAndSetSemantics{}.drawWithContent{
        drawRoundRect(color.copy(alpha=opacity.value),cornerRadius=CornerRadius(radius.toPx()))
    }
}
@Composable
fun SkeletonBlock(modifier:Modifier=Modifier,radius:Dp=6.dp){Box(modifier.skeleton(radius=radius))}

/** Shared by real rows and skeletons, so thumbnail sizes and spacing stay identical. */
object LibraryLayout {
    val PageInset=16.dp
    val HomeCover=52.dp
    val HomeRowPadding=4.dp
    val HomeRowGap=12.dp
    val AudioCover=54.dp
    val AudioRowPadding=10.dp
    val AudioRowGap=12.dp
    val CoverCorner=12.dp
    val GalleryGutter=2.dp
    const val GalleryAspect=4f/5f
    val GalleryTop=12.dp
}

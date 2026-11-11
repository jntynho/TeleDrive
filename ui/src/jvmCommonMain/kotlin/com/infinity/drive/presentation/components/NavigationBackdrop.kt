package com.infinity.drive.presentation.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

val LocalNavigationScene=staticCompositionLocalOf<GraphicsLayer?>{null}
@Composable
fun Modifier.recordNavigationScene(scene:GraphicsLayer,enabled:Boolean):Modifier {
    val navigating=app.tgdrive.ui.LocalNavigationInProgress.current
    return drawWithContent{
        if(enabled){
            if(!navigating||scene.size.width==0)scene.record{this@drawWithContent.drawContent()}
            if(navigating)drawContent()else drawLayer(scene)
        }else drawContent()
    }
}
/** Blur the captured library only. Navigation icons/text remain crisp and fully interactive. */
@Composable
fun Modifier.navigationBackdrop(enabled:Boolean,cornerRadius:androidx.compose.ui.unit.Dp=28.dp):Modifier {
    val scene=LocalNavigationScene.current
    val backdrop=rememberGraphicsLayer()
    var position by remember{mutableStateOf(Offset.Zero)}
    if(!enabled||scene==null)return this
    return onGloballyPositioned{position=it.positionInRoot()}.drawWithCache{
        val mask=androidx.compose.ui.graphics.Path().apply{addRoundRect(androidx.compose.ui.geometry.RoundRect(0f,0f,size.width,size.height,androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())))}
        onDrawBehind{
            backdrop.record{
                translate(-position.x,-position.y){drawLayer(scene)}
            }
            backdrop.renderEffect=BlurEffect(18.dp.toPx(),18.dp.toPx())
            clipPath(mask){drawLayer(backdrop)}
        }
    }
}

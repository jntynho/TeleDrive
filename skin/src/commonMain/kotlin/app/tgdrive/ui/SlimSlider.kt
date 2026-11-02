package app.tgdrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Native slider semantics/gestures with a quiet continuous track and a round thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,
    valueRange:ClosedFloatingPointRange<Float> = 0f..1f,steps:Int=0,enabled:Boolean=true,
    onValueChangeFinished:(()->Unit)?=null,trackHeight:Dp=4.dp,thumbSize:Dp=16.dp,
    activeColor:Color=MaterialTheme.colorScheme.primary,
    inactiveColor:Color=MaterialTheme.colorScheme.outlineVariant
) {
    Slider(value.coerceIn(valueRange),onValueChange,modifier.heightIn(min=48.dp),enabled=enabled,
        valueRange=valueRange,steps=steps,onValueChangeFinished=onValueChangeFinished,
        thumb={Box(Modifier.size(thumbSize).background(if(enabled)activeColor else inactiveColor,CircleShape))},
        track={state->
            val fraction=((state.value-valueRange.start)/(valueRange.endInclusive-valueRange.start).coerceAtLeast(.001f)).coerceIn(0f,1f)
            Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                val radius=CornerRadius(size.height/2)
                drawRoundRect(inactiveColor,cornerRadius=radius)
                if(fraction>0f)drawRoundRect(if(enabled)activeColor else inactiveColor,size=Size(size.width*fraction,size.height),cornerRadius=radius)
            }
        })
}

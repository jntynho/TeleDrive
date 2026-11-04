package app.tgdrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.StrokeCap
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Slider(value.coerceIn(valueRange),onValueChange,modifier.heightIn(min=48.dp),enabled=enabled,
        valueRange=valueRange,steps=steps,onValueChangeFinished=onValueChangeFinished,
        // Reserve the native thumb's horizontal travel, but draw both parts on one canvas.
        // A custom thumb otherwise inherits Slider's minimum height and becomes an oval.
        thumb={Spacer(Modifier.width(thumbSize).height(0.dp))},
        track={state->
            val fraction=((state.value-valueRange.start)/(valueRange.endInclusive-valueRange.start).coerceAtLeast(.001f)).coerceIn(0f,1f)
            Canvas(Modifier.fillMaxWidth().height(maxOf(trackHeight, thumbSize))) {
                val y=size.height/2f
                val x=size.width*(if(rtl)1f-fraction else fraction)
                val start=if(rtl)size.width else 0f
                val color=if(enabled)activeColor else inactiveColor
                drawLine(inactiveColor,Offset(0f,y),Offset(size.width,y),trackHeight.toPx(),StrokeCap.Round)
                if(fraction>0f)drawLine(color,Offset(start,y),Offset(x,y),trackHeight.toPx(),StrokeCap.Round)
                if(thumbSize>0.dp)drawCircle(color,thumbSize.toPx()/2f,Offset(x,y))
            }
        })
}

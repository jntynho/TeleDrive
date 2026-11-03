package app.tgdrive.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.min

/** Smooth, zero-slope entry into the last 48dp; no change to actual scroll consumption. */
fun edgeApproach(distance:Float,zone:Float):Float {
    if(!distance.isFinite()||zone<=0f)return 0f
    val t=(1f-distance.coerceAtLeast(0f)/zone).coerceIn(0f,1f)
    return t*t*(3f-2f*t)
}

fun elasticDisplacement(raw:Float,viewport:Float,maxPixels:Float):Float {
    val limit=min(viewport*.18f,maxPixels).coerceAtLeast(1f)
    val damped=raw*.65f
    return damped/(1f+abs(damped)/limit)
}

object ElasticOverscrollFactory:OverscrollFactory {
    override fun createOverscrollEffect():OverscrollEffect=ElasticOverscrollEffect()
    override fun equals(other:Any?)=this===other
    override fun hashCode()=37
}

/** Each native scrollable owns exactly one instance. The callback returns signed proximity. */
internal class ElasticOverscrollEffect(private val proximity:(Offset)->Offset={Offset.Zero}):OverscrollEffect {
    private var offset by mutableStateOf(Offset.Zero)
    private var approach by mutableStateOf(Offset.Zero)
    private var raw=Offset.Zero
    private var width=1f
    private var height=1f
    private var maxPixels=72f
    private var settling:Job?=null
    private var flinging=false
    private val layer=object:Modifier.Node(),LayoutModifierNode {
        override fun onDetach(){settling?.cancel();raw=Offset.Zero;offset=Offset.Zero;approach=Offset.Zero}
        override fun MeasureScope.measure(measurable:Measurable,constraints:Constraints):MeasureResult {
            val child=measurable.measure(constraints)
            width=child.width.toFloat().coerceAtLeast(1f);height=child.height.toFloat().coerceAtLeast(1f)
            maxPixels=72.dp.toPx()
            return layout(child.width,child.height){child.placeWithLayer(0,0){
                translationX=offset.x;translationY=offset.y
                scaleX=1f+(abs(offset.x)/width*.3f+abs(approach.x)*.009f).coerceAtMost(.04f)
                scaleY=1f+(abs(offset.y)/height*.3f+abs(approach.y)*.009f).coerceAtMost(.04f)
                transformOrigin=TransformOrigin(if(offset.x+approach.x>=0)0f else 1f,if(offset.y+approach.y>=0)0f else 1f)
            }}
        }
        fun settle(leftover:Velocity) {
            settling?.cancel()
            if(!isInProgress){raw=Offset.Zero;return}
            val start=offset;val pre=approach
            settling=coroutineScope.launch {
                val animation=Animatable(1f)
                animation.animateTo(0f,spring(dampingRatio=1f,stiffness=190f),
                    initialVelocity=(abs(leftover.x)/width+abs(leftover.y)/height).coerceAtMost(2f)) {
                    val fraction=value.coerceAtLeast(0f)
                    offset=start*fraction;approach=pre*fraction
                }
                raw=Offset.Zero;offset=Offset.Zero;approach=Offset.Zero
            }
        }
    }
    override val node:DelegatableNode get()=layer
    override val isInProgress:Boolean get()=offset.getDistance()>.1f||approach.getDistance()>.001f
    private fun update(){offset=Offset(elasticDisplacement(raw.x,width,maxPixels),elasticDisplacement(raw.y,height,maxPixels))}
    private fun restore(value:Float,size:Float):Float {
        val limit=min(size*.18f,maxPixels).coerceAtLeast(1f)
        return value/(1f-(abs(value)/limit).coerceAtMost(.99f))/.65f
    }
    private fun unwind(delta:Float,current:Float):Float=
        if(current!=0f&&sign(delta)!=sign(current))sign(delta)*min(abs(delta),abs(current))else 0f
    override fun applyToScroll(delta:Offset,source:NestedScrollSource,performScroll:(Offset)->Offset):Offset {
        if(source!=NestedScrollSource.UserInput){
            val consumed=performScroll(delta)
            if(flinging){val remaining=delta-consumed;raw+=remaining*.35f;approach=proximity(delta)*.35f;update()}
            return consumed
        }
        if(settling?.isActive==true){
            settling?.cancel();raw=Offset(restore(offset.x,width),restore(offset.y,height))
        }
        val released=Offset(unwind(delta.x,raw.x),unwind(delta.y,raw.y))
        raw+=released
        val consumed=performScroll(delta-released)
        val remaining=delta-released-consumed
        raw=Offset((raw.x+remaining.x).coerceIn(-width*2,width*2),(raw.y+remaining.y).coerceIn(-height*2,height*2))
        // Layout changes, scrollTo and selection auto-scroll never drive anticipatory motion.
        approach=proximity(delta)
        update()
        return released+consumed+remaining
    }
    override suspend fun applyToFling(velocity:Velocity,performFling:suspend(Velocity)->Velocity){
        flinging=true
        try { val consumed=performFling(velocity);layer.settle(velocity-consumed) }
        finally { flinging=false }
    }
}

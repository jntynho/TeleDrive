package app.tgdrive.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Boundary spring, inspired by the Miuix overscroll used by Only Player.
 * Uses the Compose scroll contract, so nested scrolling/pull-to-refresh gets first refusal.
 * No pointer interception, polling, extra UI dependency or layout remeasurement per frame.
 */
object ElasticOverscrollFactory:OverscrollFactory {
    override fun createOverscrollEffect():OverscrollEffect=ElasticOverscrollEffect()
    override fun equals(other:Any?)=this===other
    override fun hashCode()=37
}

internal class ElasticOverscrollEffect:OverscrollEffect {
    private var offset by mutableStateOf(Offset.Zero)
    private var raw=Offset.Zero
    private var width=1f
    private var height=1f
    private var scope:CoroutineScope?=null
    private var settling:Job?=null
    override val isInProgress:Boolean get()=abs(offset.x)>.5f||abs(offset.y)>.5f
    override val node:DelegatableNode=object:Modifier.Node(),LayoutModifierNode {
        override fun onAttach(){scope=coroutineScope}
        override fun onDetach(){settling?.cancel();scope=null;raw=Offset.Zero;offset=Offset.Zero}
        override fun MeasureScope.measure(measurable:Measurable,constraints:Constraints):MeasureResult {
            val child=measurable.measure(constraints)
            width=child.width.toFloat().coerceAtLeast(1f);height=child.height.toFloat().coerceAtLeast(1f)
            return layout(child.width,child.height){child.placeWithLayer(0,0){translationX=offset.x;translationY=offset.y}}
        }
    }
    private fun resist(distance:Float,size:Float):Float {
        val limit=(size*.20f).coerceAtLeast(1f)
        val damped=distance*.62f
        return damped/(1f+abs(damped)/limit)
    }
    private fun restore(distance:Float,size:Float):Float {
        val limit=(size*.20f).coerceAtLeast(1f)
        return (distance/(1f-abs(distance).coerceAtMost(limit*.99f)/limit)/.62f).coerceIn(-size*2,size*2)
    }
    private fun update(){offset=Offset(resist(raw.x,width),resist(raw.y,height))}
    private fun unwind(delta:Float,current:Float):Float =
        if(current!=0f&&sign(delta)!=sign(current))sign(delta)*minOf(abs(delta),abs(current))else 0f
    override fun applyToScroll(delta:Offset,source:NestedScrollSource,performScroll:(Offset)->Offset):Offset {
        if(source!=NestedScrollSource.UserInput)return performScroll(delta)
        if(settling?.isActive==true){raw=Offset(restore(offset.x,width),restore(offset.y,height));settling?.cancel()}
        val released=Offset(unwind(delta.x,raw.x),unwind(delta.y,raw.y))
        raw+=released
        val consumed=performScroll(delta-released)
        val remaining=delta-released-consumed
        fun releaseConsumed(current:Float,used:Float)=if(sign(current)==sign(used))current-sign(current)*minOf(abs(current),abs(used))else current
        raw=Offset(releaseConsumed(raw.x,consumed.x),releaseConsumed(raw.y,consumed.y))
        raw=Offset((raw.x+remaining.x).coerceIn(-width*2,width*2),(raw.y+remaining.y).coerceIn(-height*2,height*2))
        update()
        return released+consumed+remaining
    }
    override suspend fun applyToFling(velocity:Velocity,performFling:suspend(Velocity)->Velocity){
        performFling(velocity)
        settling?.cancel()
        if(!isInProgress){raw=Offset.Zero;offset=Offset.Zero;return}
        settling=scope?.launch {
            val animation=Animatable(offset,Offset.VectorConverter)
            animation.animateTo(Offset.Zero,spring(dampingRatio=1f,stiffness=220f)){offset=value}
            raw=Offset.Zero;offset=Offset.Zero
        }
    }
}

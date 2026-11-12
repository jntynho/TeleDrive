package app.tgdrive.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private fun listMotion(state: LazyListState, horizontal: Boolean, reverse: Boolean, enabled: Boolean): Modifier = Modifier.composed {
    val zone = with(LocalDensity.current) { 48.dp.toPx() }
    val rtl = horizontal && LocalLayoutDirection.current == LayoutDirection.Rtl
    Modifier.elasticMotion(enabled, horizontal, state) { delta ->
        val layout = state.layoutInfo
        val last = layout.visibleItemsInfo.lastOrNull()
        // firstVisibleItemScrollOffset already accounts for beforeContentPadding.
        val start = if (!state.canScrollBackward) 0f else if (state.firstVisibleItemIndex == 0)
            state.firstVisibleItemScrollOffset.toFloat() else Float.POSITIVE_INFINITY
        val end = if (!state.canScrollForward) 0f else if (last?.index == layout.totalItemsCount - 1)
            (last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset).toFloat().coerceAtLeast(0f) else Float.POSITIVE_INFINITY
        val input = if (horizontal) delta.x else delta.y
        val sign = if (reverse != rtl) -1 else 1
        val amount = elasticEdgeProximity(start, end, zone, input * sign) * sign
        if (horizontal) Offset(amount, 0f) else Offset(0f, amount)
    }
}

@Composable
fun ElasticLazyColumn(modifier:Modifier=Modifier,state:LazyListState=rememberLazyListState(),
    contentPadding:PaddingValues=PaddingValues(0.dp),reverseLayout:Boolean=false,
    verticalArrangement:Arrangement.Vertical=if(!reverseLayout)Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment:Alignment.Horizontal=Alignment.Start,flingBehavior:FlingBehavior=ScrollableDefaults.flingBehavior(),
    userScrollEnabled:Boolean=true,elasticEnabled:Boolean=true,content:LazyListScope.()->Unit) {
    androidx.compose.foundation.lazy.LazyColumn(modifier=modifier.then(listMotion(state,false,reverseLayout,elasticEnabled&&userScrollEnabled)),state=state,contentPadding=contentPadding,reverseLayout=reverseLayout,
        verticalArrangement=verticalArrangement,horizontalAlignment=horizontalAlignment,flingBehavior=flingBehavior,
        userScrollEnabled=userScrollEnabled,overscrollEffect=null,content=content)
}

@Composable
fun ElasticLazyRow(modifier:Modifier=Modifier,state:LazyListState=rememberLazyListState(),
    contentPadding:PaddingValues=PaddingValues(0.dp),reverseLayout:Boolean=false,
    horizontalArrangement:Arrangement.Horizontal=if(!reverseLayout)Arrangement.Start else Arrangement.End,
    verticalAlignment:Alignment.Vertical=Alignment.Top,flingBehavior:FlingBehavior=ScrollableDefaults.flingBehavior(),
    userScrollEnabled:Boolean=true,elasticEnabled:Boolean=true,content:LazyListScope.()->Unit) {
    androidx.compose.foundation.lazy.LazyRow(modifier=modifier.then(listMotion(state,true,reverseLayout,elasticEnabled&&userScrollEnabled)),state=state,contentPadding=contentPadding,reverseLayout=reverseLayout,
        horizontalArrangement=horizontalArrangement,verticalAlignment=verticalAlignment,flingBehavior=flingBehavior,
        userScrollEnabled=userScrollEnabled,overscrollEffect=null,content=content)
}

@Composable
fun ElasticLazyVerticalGrid(columns:GridCells,modifier:Modifier=Modifier,state:LazyGridState=rememberLazyGridState(),
    contentPadding:PaddingValues=PaddingValues(0.dp),reverseLayout:Boolean=false,
    verticalArrangement:Arrangement.Vertical=if(!reverseLayout)Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement:Arrangement.Horizontal=Arrangement.Start,flingBehavior:FlingBehavior=ScrollableDefaults.flingBehavior(),
    userScrollEnabled:Boolean=true,elasticEnabled:Boolean=true,content:LazyGridScope.()->Unit) {
    val zone=with(LocalDensity.current){48.dp.toPx()}
    val motion=Modifier.elasticMotion(elasticEnabled&&userScrollEnabled,false,state){delta ->
        val layout=state.layoutInfo
        val last=layout.visibleItemsInfo.firstOrNull{it.index==layout.totalItemsCount-1}
        val start=if(!state.canScrollBackward)0f else if(state.firstVisibleItemIndex==0)state.firstVisibleItemScrollOffset.toFloat()else Float.POSITIVE_INFINITY
        // The last row may contain cells of different heights: its lowest edge is the boundary.
        val lastRowBottom=last?.let{cell->layout.visibleItemsInfo.filter{it.row==cell.row}.maxOfOrNull{it.offset.y+it.size.height}}
        val end=if(!state.canScrollForward)0f else lastRowBottom?.let{(it+layout.afterContentPadding-layout.viewportEndOffset).toFloat().coerceAtLeast(0f)}?:Float.POSITIVE_INFINITY
        val sign=if(reverseLayout)-1 else 1
        Offset(0f,elasticEdgeProximity(start,end,zone,delta.y*sign)*sign)
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns=columns,modifier=modifier.then(motion),state=state,contentPadding=contentPadding,
        reverseLayout=reverseLayout,verticalArrangement=verticalArrangement,horizontalArrangement=horizontalArrangement,
        flingBehavior=flingBehavior,userScrollEnabled=userScrollEnabled,overscrollEffect=null,content=content)
}

fun Modifier.elasticVerticalScroll(state:ScrollState,enabled:Boolean=true,flingBehavior:FlingBehavior?=null,reverseScrolling:Boolean=false):Modifier=composed {
    this.then(scrollMotion(state,false,reverseScrolling,enabled)).verticalScroll(state=state,overscrollEffect=null,enabled=enabled,flingBehavior=flingBehavior,reverseScrolling=reverseScrolling)
}
fun Modifier.elasticHorizontalScroll(state:ScrollState,enabled:Boolean=true,flingBehavior:FlingBehavior?=null,reverseScrolling:Boolean=false):Modifier=composed {
    this.then(scrollMotion(state,true,reverseScrolling,enabled)).horizontalScroll(state=state,overscrollEffect=null,enabled=enabled,flingBehavior=flingBehavior,reverseScrolling=reverseScrolling)
}
private fun scrollMotion(state:ScrollState,horizontal:Boolean,reverse:Boolean,enabled:Boolean):Modifier=Modifier.composed {
    val zone=with(LocalDensity.current){48.dp.toPx()}
    val rtl=horizontal&&LocalLayoutDirection.current==LayoutDirection.Rtl
    Modifier.elasticMotion(enabled,horizontal,state){delta ->
        val sign=if(reverse!=rtl)-1 else 1
        val amount=if(state.maxValue==Int.MAX_VALUE)0f else elasticEdgeProximity(state.value.toFloat(),
            (state.maxValue-state.value).toFloat(),zone,(if(horizontal)delta.x else delta.y)*sign)*sign
        if(horizontal)Offset(amount,0f)else Offset(0f,amount)
    }
}

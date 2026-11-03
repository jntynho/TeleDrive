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
import androidx.compose.ui.unit.dp
import kotlin.math.sign

private fun edge(delta:Float,start:Float,end:Float,zone:Float):Float=
    if(delta==0f)0f else sign(delta)*edgeApproach(if(delta>0)start else end,zone)

@Composable
private fun listEffect(state:LazyListState,horizontal:Boolean,reverse:Boolean):OverscrollEffect {
    val zone=with(LocalDensity.current){48.dp.toPx()}
    return remember(state,zone,horizontal,reverse){ElasticOverscrollEffect { delta ->
        val layout=state.layoutInfo
        val first=layout.visibleItemsInfo.firstOrNull()
        val last=layout.visibleItemsInfo.lastOrNull()
        val start=if(!state.canScrollBackward)0f else if(first?.index==0)(layout.viewportStartOffset-first.offset).toFloat().coerceAtLeast(0f)else Float.POSITIVE_INFINITY
        val end=if(!state.canScrollForward)0f else if(last?.index==layout.totalItemsCount-1)(last.offset+last.size+layout.afterContentPadding-layout.viewportEndOffset).toFloat().coerceAtLeast(0f)else Float.POSITIVE_INFINITY
        val input=if(horizontal)delta.x else delta.y
        val amount=edge(if(reverse)-input else input,start,end,zone)*(if(reverse)-1 else 1)
        if(horizontal)Offset(amount,0f)else Offset(0f,amount)
    }}
}

@Composable
fun ElasticLazyColumn(modifier:Modifier=Modifier,state:LazyListState=rememberLazyListState(),
    contentPadding:PaddingValues=PaddingValues(0.dp),reverseLayout:Boolean=false,
    verticalArrangement:Arrangement.Vertical=if(!reverseLayout)Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment:Alignment.Horizontal=Alignment.Start,flingBehavior:FlingBehavior=ScrollableDefaults.flingBehavior(),
    userScrollEnabled:Boolean=true,content:LazyListScope.()->Unit) {
    androidx.compose.foundation.lazy.LazyColumn(modifier=modifier,state=state,contentPadding=contentPadding,reverseLayout=reverseLayout,
        verticalArrangement=verticalArrangement,horizontalAlignment=horizontalAlignment,flingBehavior=flingBehavior,
        userScrollEnabled=userScrollEnabled,overscrollEffect=listEffect(state,false,reverseLayout),content=content)
}

@Composable
fun ElasticLazyRow(modifier:Modifier=Modifier,state:LazyListState=rememberLazyListState(),
    contentPadding:PaddingValues=PaddingValues(0.dp),reverseLayout:Boolean=false,
    horizontalArrangement:Arrangement.Horizontal=if(!reverseLayout)Arrangement.Start else Arrangement.End,
    verticalAlignment:Alignment.Vertical=Alignment.Top,flingBehavior:FlingBehavior=ScrollableDefaults.flingBehavior(),
    userScrollEnabled:Boolean=true,content:LazyListScope.()->Unit) {
    androidx.compose.foundation.lazy.LazyRow(modifier=modifier,state=state,contentPadding=contentPadding,reverseLayout=reverseLayout,
        horizontalArrangement=horizontalArrangement,verticalAlignment=verticalAlignment,flingBehavior=flingBehavior,
        userScrollEnabled=userScrollEnabled,overscrollEffect=listEffect(state,true,reverseLayout),content=content)
}

@Composable
fun ElasticLazyVerticalGrid(columns:GridCells,modifier:Modifier=Modifier,state:LazyGridState=rememberLazyGridState(),
    contentPadding:PaddingValues=PaddingValues(0.dp),reverseLayout:Boolean=false,
    verticalArrangement:Arrangement.Vertical=if(!reverseLayout)Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement:Arrangement.Horizontal=Arrangement.Start,flingBehavior:FlingBehavior=ScrollableDefaults.flingBehavior(),
    userScrollEnabled:Boolean=true,content:LazyGridScope.()->Unit) {
    val zone=with(LocalDensity.current){48.dp.toPx()}
    val effect=remember(state,zone,reverseLayout){ElasticOverscrollEffect { delta ->
        val layout=state.layoutInfo
        val first=layout.visibleItemsInfo.firstOrNull{it.index==0}
        val last=layout.visibleItemsInfo.firstOrNull{it.index==layout.totalItemsCount-1}
        val start=if(!state.canScrollBackward)0f else first?.let{(layout.viewportStartOffset-it.offset.y).toFloat().coerceAtLeast(0f)}?:Float.POSITIVE_INFINITY
        val end=if(!state.canScrollForward)0f else last?.let{(it.offset.y+it.size.height+layout.afterContentPadding-layout.viewportEndOffset).toFloat().coerceAtLeast(0f)}?:Float.POSITIVE_INFINITY
        Offset(0f,edge(if(reverseLayout)-delta.y else delta.y,start,end,zone)*(if(reverseLayout)-1 else 1))
    }}
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns=columns,modifier=modifier,state=state,contentPadding=contentPadding,
        reverseLayout=reverseLayout,verticalArrangement=verticalArrangement,horizontalArrangement=horizontalArrangement,
        flingBehavior=flingBehavior,userScrollEnabled=userScrollEnabled,overscrollEffect=effect,content=content)
}

fun Modifier.elasticVerticalScroll(state:ScrollState,enabled:Boolean=true,flingBehavior:FlingBehavior?=null,reverseScrolling:Boolean=false):Modifier=composed {
    val effect=scrollEffect(state,false,reverseScrolling)
    this.verticalScroll(state=state,overscrollEffect=effect,enabled=enabled,flingBehavior=flingBehavior,reverseScrolling=reverseScrolling)
}
fun Modifier.elasticHorizontalScroll(state:ScrollState,enabled:Boolean=true,flingBehavior:FlingBehavior?=null,reverseScrolling:Boolean=false):Modifier=composed {
    val effect=scrollEffect(state,true,reverseScrolling)
    this.horizontalScroll(state=state,overscrollEffect=effect,enabled=enabled,flingBehavior=flingBehavior,reverseScrolling=reverseScrolling)
}
@Composable
private fun scrollEffect(state:ScrollState,horizontal:Boolean,reverse:Boolean):OverscrollEffect {
    val zone=with(LocalDensity.current){48.dp.toPx()}
    return remember(state,zone,horizontal,reverse){ElasticOverscrollEffect { delta ->
        val amount=if(state.maxValue==Int.MAX_VALUE)0f else edge(
            (if(horizontal)delta.x else delta.y)*(if(reverse)-1 else 1),state.value.toFloat(),
            (state.maxValue-state.value).toFloat(),zone)*(if(reverse)-1 else 1)
        if(horizontal)Offset(amount,0f)else Offset(0f,amount)
    }}
}

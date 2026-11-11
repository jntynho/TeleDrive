package com.infinity.drive.presentation.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.toRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.WeakHashMap

private data class NavigationIntent(val serial:Long,val origin:String?, val route:Route?, val topLevel:Boolean, val run:()->Unit)
private class NavigationQueue {
    val requests=Channel<NavigationIntent>(Channel.CONFLATED)
    var serial=0L
}
private val queues=WeakHashMap<NavHostController,NavigationQueue>()

private fun NavBackStackEntry.matches(route:Route):Boolean =
    destination.hasRoute(route::class) && runCatching { toRoute<Route>(route::class)==route }.getOrDefault(false)

/** A single latest-intent queue waits for the real entry, rather than dropping taps at STARTED. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun NavigationCoordinator(controller:NavHostController) {
    val queue=remember(controller){NavigationQueue()}
    val requests=queue.requests
    DisposableEffect(controller,requests){
        queues[controller]=queue
        onDispose{queues.remove(controller);requests.close()}
    }
    LaunchedEffect(controller,requests){
        requests.receiveAsFlow().collectLatest { intent ->
            val entry=controller.currentBackStackEntryFlow.flatMapLatest { current ->
                current.lifecycle.currentStateFlow.map { current to it }
            }.first { (_,state)->state.isAtLeast(Lifecycle.State.RESUMED) }.first
            if(controller.currentBackStackEntry!==entry||intent.serial!=queue.serial)return@collectLatest
            if(!intent.topLevel && intent.origin!=entry.id)return@collectLatest
            if(intent.route!=null && entry.matches(intent.route))return@collectLatest
            intent.run()
        }
    }
}

private fun NavHostController.submit(route:Route?,topLevel:Boolean=false,action:()->Unit){
    val entry=currentBackStackEntry?:return
    val queue=queues[this]
    val serial=queue?.let{++it.serial}?:0L
    if(route!=null&&entry.matches(route))return
    if(entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))action()
    else queue?.requests?.trySend(NavigationIntent(serial,entry.id,route,topLevel,action))
}

fun NavHostController.navigateOnce(route:Route)=submit(route){navigate(route)}
fun NavHostController.navigateOnce(route:Route,builder:NavOptionsBuilder.()->Unit)=submit(route){navigate(route,builder)}
fun NavHostController.navigateTopLevelOnce(route:Route,builder:NavOptionsBuilder.()->Unit)=submit(route,true){navigate(route,builder)}
fun NavHostController.popBackStackOnce()=submit(null){popBackStack()}
